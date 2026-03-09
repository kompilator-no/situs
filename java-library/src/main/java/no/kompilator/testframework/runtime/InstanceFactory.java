package no.kompilator.testframework.runtime;

/**
 * Strategy interface for creating instances of {@code @TestSuite} classes.
 *
 * <p>The default implementation simply calls the no-arg constructor via reflection.
 * Alternative implementations can supply instances from a dependency-injection container
 * (e.g. a Spring {@code ApplicationContext}) so that test suite classes can receive
 * injected beans.
 *
 * <p>The factory is called:
 * <ul>
 *   <li>Once for the shared {@code @BeforeAll} / {@code @AfterAll} instance.</li>
 *   <li>Once per test in sequential mode (fresh instance per test).</li>
 *   <li>Once per test in parallel mode (fresh instance per thread).</li>
 * </ul>
 *
 * @see TestRunner
 * @see <a href="../spring/SpringInstanceFactory.html">SpringInstanceFactory</a>
 */
@FunctionalInterface
public interface InstanceFactory {

    /**
     * Returns an instance of {@code suiteClass} to use for a single test run.
     *
     * @param suiteClass the suite class to instantiate
     * @return a ready-to-use instance — never {@code null}
     * @throws RuntimeException if the instance cannot be created
     */
    Object createInstance(Class<?> suiteClass);

    /**
     * Default implementation — creates instances via the no-arg constructor.
     * Uses {@link java.lang.reflect.Constructor#setAccessible(boolean) setAccessible(true)}
     * so that non-public inner classes (e.g. test fixtures) can be instantiated.
     * Used when no DI container is available.
     */
    static InstanceFactory reflective() {
        return suiteClass -> {
            try {
                var constructor = suiteClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor.newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Failed to instantiate " + suiteClass.getName(), e);
            }
        };
    }
}
