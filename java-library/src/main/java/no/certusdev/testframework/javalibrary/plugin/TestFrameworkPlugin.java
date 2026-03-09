package no.certusdev.testframework.javalibrary.plugin;

/**
 * Marker and lifecycle interface for all test framework plugins.
 *
 * <p>Declare your plugin as a Spring {@code @Bean} (or {@code @Component}) and
 * implement this interface. The library's {@link PluginRunner} will detect it
 * automatically via the Spring {@code ApplicationContext} and call
 * {@link #onStartup()} once the context is fully initialised — no extra wiring needed.
 *
 * <h2>Minimal plugin</h2>
 * <pre>{@code
 * @Component
 * public class MyPlugin implements TestFrameworkPlugin {
 *
 *     @Override
 *     public String pluginName() {
 *         return "My Plugin";
 *     }
 *
 *     @Override
 *     public void onStartup() {
 *         // runs automatically after the Spring context starts
 *     }
 * }
 * }</pre>
 *
 * <h2>Opt-out of auto-run</h2>
 * <p>Override {@link #runOnStartup()} and return {@code false} if you want the
 * plugin bean to exist in the context but not run automatically:
 * <pre>{@code
 * @Override
 * public boolean runOnStartup() { return false; }
 * }</pre>
 *
 * @see PluginRunner
 */
public interface TestFrameworkPlugin {

    /**
     * Returns a human-readable name for this plugin, used in log output.
     *
     * @return the plugin name
     */
    String pluginName();

    /**
     * Called once by {@link PluginRunner} after the Spring application context is
     * fully started, if {@link #runOnStartup()} returns {@code true}.
     *
     * <p>Default implementation is a no-op — override to perform work on startup.
     */
    default void onStartup() {
        // no-op by default
    }

    /**
     * Controls whether {@link PluginRunner} calls {@link #onStartup()} automatically.
     *
     * <p>Returns {@code false} by default — plugins do not run on startup unless
     * they explicitly opt in by overriding this method.
     *
     * @return {@code true} to run automatically on startup, {@code false} to skip
     */
    default boolean runOnStartup() {
        return false;
    }
}
