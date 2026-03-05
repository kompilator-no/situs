package no.testframework.plugins;

/**
 * Marker interface for all test framework plugins.
 *
 * <p>A plugin is a pre-built, configurable {@code @RuntimeTestSuite} that can be
 * dropped into any application with minimal setup. Plugins live in the
 * {@code no.testframework.plugins} module and depend only on the core
 * {@code java-library} (no Spring required unless the plugin explicitly uses it).
 *
 * <p>Each concrete plugin implements this interface and is also annotated with
 * {@code @RuntimeTestSuite} so the framework's classpath scanner picks it up
 * automatically.
 *
 * <h2>Implementing a plugin</h2>
 * <pre>{@code
 * @RuntimeTestSuite(name = "My Plugin Suite")
 * public class MyPlugin implements TestFrameworkPlugin {
 *
 *     @RunTimeTest(name = "myCheck")
 *     public void myCheck() {
 *         // assertion logic here
 *     }
 * }
 * }</pre>
 *
 * @see no.testframework.plugins.http.HttpHealthCheckPlugin
 */
public interface TestFrameworkPlugin {
    // Marker — no methods required.
    // Plugins are discovered via @RuntimeTestSuite, not by type.
}
