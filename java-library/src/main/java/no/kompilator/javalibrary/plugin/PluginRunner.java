package no.kompilator.javalibrary.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;

import java.util.Collection;

/**
 * Spring {@link ApplicationRunner} that discovers all {@link TestFrameworkPlugin} beans
 * in the application context and calls {@link TestFrameworkPlugin#onStartup()} on each
 * one that has opted in via {@link TestFrameworkPlugin#runOnStartup()}.
 *
 * <p>Registered automatically by {@link no.kompilator.javalibrary.spring.RuntimeTestAutoConfiguration}.
 * No manual wiring needed — simply declare a {@link TestFrameworkPlugin} bean and it will
 * be picked up automatically.
 *
 * <h2>Execution order</h2>
 * <p>Plugins run in the order Spring returns them from the context. Use
 * {@code @Order} on your plugin bean to control relative ordering.
 *
 * <h2>Error handling</h2>
 * <p>If one plugin throws during {@link TestFrameworkPlugin#onStartup()}, the exception
 * is logged and the runner continues to the next plugin — a single failing plugin
 * does not prevent others from running.
 *
 * @see TestFrameworkPlugin
 */
public class PluginRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PluginRunner.class);

    private final ApplicationContext applicationContext;

    /**
     * @param applicationContext the Spring context used to discover plugin beans
     */
    public PluginRunner(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Discovers all {@link TestFrameworkPlugin} beans and calls
     * {@link TestFrameworkPlugin#onStartup()} on those with {@code runOnStartup() == true}.
     *
     * @param args application arguments (not used)
     */
    @Override
    public void run(ApplicationArguments args) {
        Collection<TestFrameworkPlugin> plugins =
                applicationContext.getBeansOfType(TestFrameworkPlugin.class).values();

        if (plugins.isEmpty()) {
            log.debug("PluginRunner: no TestFrameworkPlugin beans found in context");
            return;
        }

        log.info("PluginRunner: found {} plugin(s)", plugins.size());

        for (TestFrameworkPlugin plugin : plugins) {
            if (!plugin.runOnStartup()) {
                log.debug("PluginRunner: skipping '{}' (runOnStartup=false)", plugin.pluginName());
                continue;
            }
            log.info("PluginRunner: running '{}'", plugin.pluginName());
            try {
                plugin.onStartup();
                log.info("PluginRunner: '{}' completed successfully", plugin.pluginName());
            } catch (Exception e) {
                log.error("PluginRunner: '{}' threw an exception during onStartup — continuing with remaining plugins",
                        plugin.pluginName(), e);
            }
        }
    }
}
