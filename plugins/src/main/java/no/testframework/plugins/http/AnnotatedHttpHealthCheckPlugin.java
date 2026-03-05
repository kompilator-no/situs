package no.testframework.plugins.http;

import no.testframework.javalibrary.annotations.BeforeAll;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Annotation-driven HTTP health-check plugin that checks a single URL per suite run.
 *
 * <p>This class is annotated with {@code @RuntimeTestSuite} so it is discovered
 * automatically by the framework's classpath scanner. Subclass it to check a specific
 * URL with zero boilerplate:
 *
 * <pre>{@code
 * @Component  // optional — add if you want Spring DI
 * public class ApiHealthCheck extends AnnotatedHttpHealthCheckPlugin {
 *
 *     public ApiHealthCheck() {
 *         super("https://api.example.com/health", 5_000);
 *     }
 * }
 * }</pre>
 *
 * <p>The suite and test names are derived from the class name by default but can be
 * overridden via the constructor:
 *
 * <pre>{@code
 * public class ApiHealthCheck extends AnnotatedHttpHealthCheckPlugin {
 *     public ApiHealthCheck() {
 *         super("API Health Check",          // suite name
 *               "https://api.example.com/health",
 *               5_000);
 *     }
 * }
 * }</pre>
 *
 * <h2>Running via HTTP API</h2>
 * <pre>
 * POST /api/test-framework/suites/ApiHealthCheck/run
 * GET  /api/test-framework/runs/{runId}/status
 * </pre>
 *
 * @see HttpHealthCheckPlugin for programmatic (builder-based) usage
 */
@RuntimeTestSuite(
        name = "HTTP Health Check",
        description = "Checks that the configured URL returns a 2xx HTTP response"
)
public class AnnotatedHttpHealthCheckPlugin implements TestFrameworkPlugin {

    private static final Logger log = LoggerFactory.getLogger(AnnotatedHttpHealthCheckPlugin.class);

    private final String url;
    private final long timeoutMs;
    private HttpClient httpClient;

    /**
     * Creates a health-check suite for the given URL with a 5-second timeout.
     *
     * @param url the URL to check
     */
    protected AnnotatedHttpHealthCheckPlugin(String url) {
        this(url, 5_000);
    }

    /**
     * Creates a health-check suite for the given URL and timeout.
     *
     * @param url       the URL to check
     * @param timeoutMs connect and read timeout in milliseconds
     */
    protected AnnotatedHttpHealthCheckPlugin(String url, long timeoutMs) {
        this.url       = url;
        this.timeoutMs = timeoutMs;
    }

    /**
     * Initialises the {@link HttpClient} before any test runs.
     * Called automatically by the framework via {@code @BeforeAll}.
     */
    @BeforeAll
    public void initClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        log.debug("HttpClient initialised (timeout: {} ms)", timeoutMs);
    }

    /**
     * Sends a GET request to the configured URL and asserts a 2xx response.
     *
     * @throws AssertionError if the response status is not 2xx or the request fails
     */
    @RunTimeTest(name = "httpGet2xx", description = "GET the configured URL and assert a 2xx response")
    public void httpGet2xx() throws IOException, InterruptedException {
        log.info("Checking {}", url);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofMillis(timeoutMs))
                .build();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        int status = response.statusCode();
        log.info("Response: HTTP {}", status);
        assertThat(status)
                .as("Expected 2xx from %s but got HTTP %d", url, status)
                .isBetween(200, 299);
    }
}
