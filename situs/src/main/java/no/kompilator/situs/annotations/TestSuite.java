package no.kompilator.situs.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a <b>system integration test suite</b> that can be discovered and executed
 * by the framework at application runtime (not just at build time).
 *
 * <p>Annotated classes are discovered automatically by
 * {@link no.kompilator.situs.runtime.ClasspathScanner#findAllTestSuites()}
 * (full classpath scan) or
 * {@link no.kompilator.situs.runtime.ClasspathScanner#findTestSuites(String)}
 * (package-scoped scan), and then registered with
 * {@link no.kompilator.situs.runtime.TestSuiteRegistry}.
 * The framework scans the class for methods annotated with {@link Test}.
 *
 * <p>Example:
 * <pre>{@code
 * @TestSuite(name = "PaymentSuite", description = "Tests the payment flow", parallel = true)
 * public class PaymentTestSuite {
 *
 *     @BeforeEach
 *     public void setUp() { ... }
 *
 *     @Test(name = "successfulPayment", timeoutMs = 5_000)
 *     public void testSuccessfulPayment() { ... }
 * }
 * }</pre>
 *
 * @see Test
 * @see no.kompilator.situs.runtime.TestSuiteRegistry
 * @see no.kompilator.situs.runtime.ClasspathScanner
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TestSuite {

    /**
     * Display name for this suite.
     * Defaults to the simple class name if left blank.
     */
    String name() default "";

    /** Optional human-readable description shown in reports and API responses. */
    String description() default "";

    /**
     * When {@code true} all tests in this suite are executed in parallel.
     * When {@code false} (default) tests run sequentially in declaration order.
     *
     * <p>In parallel mode each test gets its own object instance, so test methods
     * must not rely on shared mutable fields unless those fields are thread-safe.
     */
    boolean parallel() default false;
}