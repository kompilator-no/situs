package no.testframework.plugins.http;

import com.sun.net.httpserver.HttpServer;
import no.testframework.javalibrary.domain.TestCaseExecutionResult;
import no.testframework.javalibrary.runtime.TestRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link HttpHealthCheckPlugin}.
 *
 * <p>Spins up an embedded {@link HttpServer} on a random port so tests are
 * fully self-contained and require no external network access.
 */
class HttpHealthCheckPluginTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        baseUrl = "http://localhost:" + port;

        server.createContext("/ok",      exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/created", exchange -> {
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        server.createContext("/error",   exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.createContext("/notfound", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    @Test
    void builderRequiresAtLeastOneUrl() {
        assertThatThrownBy(() -> HttpHealthCheckPlugin.builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("At least one URL");
    }

    @Test
    void builderDefaultSuiteName() {
        HttpHealthCheckPlugin plugin = HttpHealthCheckPlugin.forUrls(baseUrl + "/ok");
        assertThat(plugin.getSuiteName()).isEqualTo("HTTP Health Checks");
    }

    @Test
    void builderCustomSuiteName() {
        HttpHealthCheckPlugin plugin = HttpHealthCheckPlugin.builder()
                .suiteName("My Checks")
                .url(baseUrl + "/ok")
                .build();
        assertThat(plugin.getSuiteName()).isEqualTo("My Checks");
    }

    @Test
    void builderUrlsArePreserved() {
        HttpHealthCheckPlugin plugin = HttpHealthCheckPlugin.builder()
                .url(baseUrl + "/ok")
                .url(baseUrl + "/created")
                .build();
        assertThat(plugin.getUrls()).containsExactly(baseUrl + "/ok", baseUrl + "/created");
    }

    @Test
    void forUrlsFactoryCreatesPlugin() {
        HttpHealthCheckPlugin plugin = HttpHealthCheckPlugin.forUrls(baseUrl + "/ok");
        assertThat(plugin.getUrls()).hasSize(1);
    }

    // -------------------------------------------------------------------------
    // checkAll
    // -------------------------------------------------------------------------

    @Test
    void checkAllPassesFor200() {
        HttpHealthCheckPlugin plugin = HttpHealthCheckPlugin.forUrls(baseUrl + "/ok");
        // should not throw
        plugin.checkAll();
    }

    @Test
    void checkAllPassesFor201() {
        HttpHealthCheckPlugin plugin = HttpHealthCheckPlugin.forUrls(baseUrl + "/created");
        plugin.checkAll();
    }

    @Test
    void checkAllFailsFor500() {
        HttpHealthCheckPlugin plugin = HttpHealthCheckPlugin.forUrls(baseUrl + "/error");
        assertThatThrownBy(plugin::checkAll)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void checkAllFailsFor404() {
        HttpHealthCheckPlugin plugin = HttpHealthCheckPlugin.forUrls(baseUrl + "/notfound");
        assertThatThrownBy(plugin::checkAll)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("HTTP 404");
    }

    @Test
    void checkAllReportsAllFailuresInOneAssertion() {
        HttpHealthCheckPlugin plugin = HttpHealthCheckPlugin.builder()
                .url(baseUrl + "/error")
                .url(baseUrl + "/notfound")
                .build();
        assertThatThrownBy(plugin::checkAll)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("HTTP 500")
                .hasMessageContaining("HTTP 404");
    }

    @Test
    void checkAllWithMixedUrlsPassesWhenAll2xx() {
        HttpHealthCheckPlugin plugin = HttpHealthCheckPlugin.builder()
                .url(baseUrl + "/ok")
                .url(baseUrl + "/created")
                .build();
        plugin.checkAll();  // should not throw
    }

    // -------------------------------------------------------------------------
    // checkUrl
    // -------------------------------------------------------------------------

    @Test
    void checkUrlPassesFor200() {
        HttpHealthCheckPlugin plugin = HttpHealthCheckPlugin.forUrls(baseUrl + "/ok");
        plugin.checkUrl(baseUrl + "/ok");  // should not throw
    }

    @Test
    void checkUrlFailsFor500() {
        HttpHealthCheckPlugin plugin = HttpHealthCheckPlugin.forUrls(baseUrl + "/ok");
        assertThatThrownBy(() -> plugin.checkUrl(baseUrl + "/error"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("500");
    }

    // -------------------------------------------------------------------------
    // Via TestRunner (integration)
    // -------------------------------------------------------------------------

    @Test
    void annotatedPluginRunsViaTestRunner() throws IOException {
        // Anonymous subclass pointing to the local test server
        class LocalHealthCheck extends AnnotatedHttpHealthCheckPlugin {
            LocalHealthCheck() { super(baseUrl + "/ok"); }
        }

        TestRunner runner = new TestRunner();
        List<TestCaseExecutionResult> results = runner.runTests(LocalHealthCheck.class);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isPassed()).isTrue();
        assertThat(results.get(0).getName()).isEqualTo("httpGet2xx");
    }

    @Test
    void annotatedPluginFailsFor500ViaTestRunner() throws IOException {
        class FailingHealthCheck extends AnnotatedHttpHealthCheckPlugin {
            FailingHealthCheck() { super(baseUrl + "/error"); }
        }

        TestRunner runner = new TestRunner();
        List<TestCaseExecutionResult> results = runner.runTests(FailingHealthCheck.class);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isPassed()).isFalse();
        assertThat(results.get(0).getErrorMessage()).contains("500");
    }
}
