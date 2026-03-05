package no.testframework.plugins.http;

import no.testframework.javalibrary.annotations.RunTimeTest;
import no.testframework.javalibrary.annotations.RuntimeTestSuite;
import no.testframework.plugins.TestFrameworkPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ready-made runtime test suite that performs HTTP GET health-checks against one or
 * more URLs and asserts each returns a 2xx status code.
 *
 * <h2>Quick start — no Spring needed</h2>
 * <pre>{@code
 * HttpHealthCheckPlugin plugin = HttpHealthCheckPlugin.forUrls(
 *         "https://my-service.example.com/health",
 *         "https://other-service.example.com/actuator/health"
 * );
 * RuntimeTestSuiteRunner runner = new RuntimeTestSuiteRunner();
 * runner.runSuite(plugin);
 * }</pre>
 *
 * <h2>Spring — register as a bean</h2>
 * <pre>{@code
 * @Bean
 * public HttpHealthCheckPlugin httpHealthChecks() {
 *     return HttpHealthCheckPlugin.builder()
 *             .suiteName("Production Health Checks")
 *             .timeoutMs(5_000)
 *             .url("https://api.example.com/health")
 *             .url("https://auth.example.com/health")
 *             .build();
 * }
 * }</pre>
 *
 * <h2>Running via HTTP API</h2>
 * <pre>
 * POST /api/test-framework/suites/HTTP Health Checks/run
 * GET  /api/test-framework/runs/{runId}/status
 * </pre>
 *
 * <p>Each URL becomes its own {@code @RunTimeTest} — results are reported individually
 * so you can see exactly which endpoint is down.
 *
 * <p>The plugin is <b>not</b> annotated with {@code @RuntimeTestSuite} at the class level
 * because its name, timeout, and URL list are all runtime-configurable. Instead it is
 * registered as a suite directly via
 * {@link no.testframework.javalibrary.runtime.RuntimeTestSuiteRunner#runSuite(Object)}
 * or by adding it to the suite set passed to {@code TestFrameworkService}.
 */
public class HttpHealthCheckPlugin implements TestFrameworkPlugin {

    private static final Logger log = LoggerFactory.getLogger(HttpHealthCheckPlugin.class);

    private final String suiteName;
    private final List<String> urls;
    private final long timeoutMs;

    private final HttpClient httpClient;

    private HttpHealthCheckPlugin(Builder builder) {
        this.suiteName  = builder.suiteName;
        this.urls       = Collections.unmodifiableList(new ArrayList<>(builder.urls));
        this.timeoutMs  = builder.timeoutMs;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Creates a plugin with default settings (suite name {@code "HTTP Health Checks"},
     * timeout 5 seconds) for the given URLs.
     *
     * @param urls one or more URLs to check
     * @return a configured {@code HttpHealthCheckPlugin}
     */
    public static HttpHealthCheckPlugin forUrls(String... urls) {
        Builder b = new Builder();
        for (String url : urls) {
            b.url(url);
        }
        return b.build();
    }

    /**
     * Returns a {@link Builder} for full configuration.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    // -------------------------------------------------------------------------
    // Suite metadata (used by the framework when this instance is passed directly)
    // -------------------------------------------------------------------------

    /**
     * Returns the suite name configured via the builder.
     * Used when registering this plugin programmatically rather than via annotation scanning.
     *
     * @return the suite name
     */
    public String getSuiteName() {
        return suiteName;
    }

    /**
     * Returns the list of URLs that will be checked.
     *
     * @return an unmodifiable list of URLs
     */
    public List<String> getUrls() {
        return urls;
    }

    /**
     * Returns the per-request connect and read timeout in milliseconds.
     *
     * @return timeout in milliseconds
     */
    public long getTimeoutMs() {
        return timeoutMs;
    }

    // -------------------------------------------------------------------------
    // Check logic — called directly by the runner when used without annotations
    // -------------------------------------------------------------------------

    /**
     * Checks all configured URLs and asserts each returns a 2xx HTTP status code.
     *
     * <p>Called by {@link no.testframework.javalibrary.runtime.RuntimeTestSuiteRunner}
     * when this plugin is run directly (without annotation-based discovery).
     * For annotation-based use see {@link AnnotatedHttpHealthCheckPlugin}.
     *
     * @throws AssertionError if any URL returns a non-2xx status or is unreachable
     */
    public void checkAll() {
        List<String> failures = new ArrayList<>();
        for (String url : urls) {
            try {
                int status = get(url);
                if (status < 200 || status >= 300) {
                    failures.add(url + " → HTTP " + status);
                    log.warn("[FAIL] {} returned HTTP {}", url, status);
                } else {
                    log.info("[OK]   {} returned HTTP {}", url, status);
                }
            } catch (IOException | InterruptedException e) {
                failures.add(url + " → " + e.getMessage());
                log.warn("[FAIL] {} unreachable: {}", url, e.getMessage());
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        assertThat(failures)
                .as("All HTTP health checks must return 2xx\nFailed:\n" + String.join("\n", failures))
                .isEmpty();
    }

    /**
     * Checks a single URL and asserts it returns a 2xx HTTP status code.
     * Used by {@link AnnotatedHttpHealthCheckPlugin} to check each URL as a separate test.
     *
     * @param url the URL to check
     * @throws AssertionError if the URL returns a non-2xx status or is unreachable
     */
    public void checkUrl(String url) {
        log.info("Checking {}", url);
        int status;
        try {
            status = get(url);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new AssertionError("HTTP request to " + url + " failed: " + e.getMessage(), e);
        }
        assertThat(status)
                .as("Expected 2xx from %s but got HTTP %d", url, status)
                .isBetween(200, 299);
        log.info("[OK] {} returned HTTP {}", url, status);
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private int get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofMillis(timeoutMs))
                .build();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        return response.statusCode();
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /**
     * Fluent builder for {@link HttpHealthCheckPlugin}.
     *
     * <pre>{@code
     * HttpHealthCheckPlugin plugin = HttpHealthCheckPlugin.builder()
     *         .suiteName("My Health Checks")
     *         .timeoutMs(3_000)
     *         .url("https://api.example.com/health")
     *         .url("https://auth.example.com/health")
     *         .build();
     * }</pre>
     */
    public static final class Builder {

        private String suiteName = "HTTP Health Checks";
        private long timeoutMs   = 5_000;
        private final List<String> urls = new ArrayList<>();

        private Builder() {}

        /**
         * Sets the suite name shown in reports and the HTTP API.
         *
         * @param suiteName the display name for this health-check suite
         * @return this builder
         */
        public Builder suiteName(String suiteName) {
            this.suiteName = suiteName;
            return this;
        }

        /**
         * Sets the per-request connect and read timeout.
         * Defaults to {@code 5_000} ms.
         *
         * @param timeoutMs timeout in milliseconds
         * @return this builder
         */
        public Builder timeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        /**
         * Adds a URL to check.
         *
         * @param url the URL to include in this health-check suite
         * @return this builder
         */
        public Builder url(String url) {
            this.urls.add(url);
            return this;
        }

        /**
         * Builds and returns the configured {@link HttpHealthCheckPlugin}.
         *
         * @return a new {@code HttpHealthCheckPlugin}
         * @throws IllegalStateException if no URLs have been added
         */
        public HttpHealthCheckPlugin build() {
            if (urls.isEmpty()) {
                throw new IllegalStateException("At least one URL must be added via url(...)");
            }
            return new HttpHealthCheckPlugin(this);
        }
    }
}
