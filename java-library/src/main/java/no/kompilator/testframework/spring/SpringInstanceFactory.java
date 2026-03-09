package no.kompilator.testframework.spring;

import no.kompilator.testframework.runtime.InstanceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

/**
 * Spring-aware {@link InstanceFactory} that resolves {@code @TestSuite} class
 * instances from the Spring {@link ApplicationContext}.
 *
 * <p>If the suite class is registered as a Spring bean (e.g. annotated with
 * {@code @Component} or declared via {@code @Bean}), it is retrieved from the context
 * so that all its constructor and field dependencies are injected automatically.
 *
 * <p>If the suite class is <em>not</em> a Spring bean, the factory falls back to
 * plain reflection instantiation ({@code new SuiteClass()}) so non-bean suites
 * continue to work without any changes.
 *
 * <p>Activated automatically when the framework is wired via
 * {@link RuntimeTestAutoConfiguration} or {@link EnableRuntimeTests}.
 *
 * <h2>Example — injecting a Spring bean into a suite</h2>
 * <pre>{@code
 * @Component
 * @TestSuite(name = "Payment Suite")
 * public class PaymentTestSuite {
 *
 *     private final PaymentService paymentService;
 *
 *     public PaymentTestSuite(PaymentService paymentService) {
 *         this.paymentService = paymentService;
 *     }
 *
 *     @Test(name = "process payment")
 *     public void processPayment() {
 *         assertThat(paymentService.process(100)).isTrue();
 *     }
 * }
 * }</pre>
 *
 * @see InstanceFactory
 * @see no.kompilator.testframework.service.TestFrameworkService
 */
public class SpringInstanceFactory implements InstanceFactory {

    private static final Logger log = LoggerFactory.getLogger(SpringInstanceFactory.class);

    private final ApplicationContext applicationContext;

    /**
     * @param applicationContext the Spring application context to resolve beans from
     */
    public SpringInstanceFactory(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Returns a suite instance from the Spring context if the class is a registered bean,
     * otherwise falls back to reflection instantiation.
     *
     * @param suiteClass the suite class to resolve
     * @return a fully dependency-injected instance if it is a Spring bean,
     *         or a plain reflectively-created instance otherwise
     */
    @Override
    public Object createInstance(Class<?> suiteClass) {
        if (applicationContext.getBeanNamesForType(suiteClass).length > 0) {
            log.debug("Resolving suite instance from Spring context: {}", suiteClass.getSimpleName());
            return applicationContext.getBean(suiteClass);
        }
        log.debug("Suite class not a Spring bean, falling back to reflection: {}", suiteClass.getSimpleName());
        return InstanceFactory.reflective().createInstance(suiteClass);
    }
}
