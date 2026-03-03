package no.testframework.sampleapp;

import no.testframework.javalibrary.suite.Action;
import no.testframework.javalibrary.suite.ActionStep;
import no.testframework.javalibrary.suite.Delay;
import no.testframework.javalibrary.suite.ExecutionStrategy;
import no.testframework.javalibrary.suite.Report;
import no.testframework.javalibrary.suite.Iterations;
import no.testframework.javalibrary.suite.GenericValidator;
import no.testframework.javalibrary.suite.GenericAction;
import no.testframework.javalibrary.suite.Attempts;
import no.testframework.javalibrary.suite.Assertion;
import no.testframework.javalibrary.suite.HttpBody;
import no.testframework.javalibrary.suite.HttpEndpoint;
import no.testframework.javalibrary.suite.HttpEndpoints;
import no.testframework.javalibrary.suite.Step;
import no.testframework.javalibrary.suite.StepExecutionCondition;
import no.testframework.javalibrary.suite.TestCase;
import no.testframework.javalibrary.suite.TestSuite;
import no.testframework.javalibrary.suite.Timeout;
import no.testframework.javalibrary.suite.Validator;
import no.testframework.javalibrary.suite.ValidatorStep;
import no.testframework.javalibrary.runtime.TestExecutionContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class SampleSuiteDefinition implements TestSuite {

    @Override
    public String name() {
        return "Sample OO Suite";
    }

    @Override
    public String description() {
        return "Object-oriented sample suite for sample-app";
    }

    @Override
    public ExecutionStrategy executionStrategy() {
        return ExecutionStrategy.SEQUENTIAL;
    }

    @Override
    public List<TestCase> testCases() {
        return List.of(new MyHttpTestCase(), new MyKafkaTestCase(), new MyGenericTestCase());
    }

    private static final class MyHttpTestCase implements TestCase {
        @Override
        public String name() {
            return "HTTP Test Case";
        }

        @Override
        public String description() {
            return "Uses HttpEndpoints metadata and validates payload in context";
        }

        @Override
        public ExecutionStrategy executionStrategy() {
            return ExecutionStrategy.SEQUENTIAL;
        }

        @Override
        public StepExecutionCondition stepExecutionCondition() {
            return StepExecutionCondition.ON_SUCCESS;
        }

        @Override
        public HttpEndpoints httpEndpoints() {
            return HttpEndpoints.from(
                    8080,
                    List.of(
                            HttpEndpoint.from(
                                    "/endpoint1/",
                                    Map.of("Content-Type", "text/html", "Accept-Language", "en-US"),
                                    () -> new HttpBody("endpoint 1 response")
                            )
                    )
            );
        }

        @Override
        public List<Step> steps() {
            return List.of(
                    ActionStep.builder((Action) context ->
                                    context.put("sampleHttpBody", httpEndpoints().endpoints().getFirst().bodySupplier().get().value()))
                            .name("Store endpoint body")
                            .description("ActionStep builder example")
                            .delay(Delay.from(5, TimeUnit.MILLISECONDS))
                            .timeout(Timeout.from(1, TimeUnit.SECONDS))
                            .build(),
                    ValidatorStep.builder((Validator) context ->
                                    "endpoint 1 response".equals(context.get("sampleHttpBody", String.class)))
                            .name("Validate endpoint body")
                            .description("ValidatorStep builder example")
                            .delay(Delay.from(5, TimeUnit.MILLISECONDS))
                            .timeout(Timeout.from(1, TimeUnit.SECONDS))
                            .build()
            );
        }
    }


    private static final class MyGenericTestCase implements TestCase {
        @Override
        public String name() {
            return "Generic Test Case";
        }

        @Override
        public String description() {
            return "Demonstrates GenericAction and GenericValidator";
        }

        @Override
        public List<Step> steps() {
            GenericAction genericAction = GenericAction.builder(() -> Optional.of(Report.from("generic-ok")))
                    .iterations(Iterations.from(1))
                    .build();

            GenericValidator genericValidator = GenericValidator.builder(() -> Assertion.success())
                    .attempts(Attempts.from(1))
                    .build();

            return List.of(
                    ActionStep.from(genericAction),
                    ValidatorStep.from(genericValidator)
            );
        }
    }

    private static final class MyKafkaTestCase implements TestCase {
        @Override
        public String name() {
            return "Kafka Test Case";
        }

        @Override
        public String description() {
            return "Simulates producer/consumer state transitions";
        }

        @Override
        public List<Step> steps() {
            return List.of(
                    new ProduceEventActionStep(),
                    new ConsumeEventValidatorStep()
            );
        }


        private static final class ConsumeEventValidatorStep implements ValidatorStep {
            @Override
            public String name() {
                return "Consume event";
            }

            @Override
            public String description() {
                return "ValidatorStep interface implementation example";
            }

            @Override
            public Delay delay() {
                return Delay.from(5, TimeUnit.MILLISECONDS);
            }

            @Override
            public Timeout timeout() {
                return Timeout.from(1, TimeUnit.SECONDS);
            }

            @Override
            public Validator validator() {
                return context -> {
                    boolean produced = "produced".equals(context.get("sampleKafkaState", String.class));
                    if (produced) {
                        context.put("sampleKafkaState", "processed");
                    }
                    return produced;
                };
            }
        }

        private static final class ProduceEventActionStep implements ActionStep {
            @Override
            public String name() {
                return "Produce event";
            }

            @Override
            public String description() {
                return "ActionStep interface implementation example";
            }

            @Override
            public Delay delay() {
                return Delay.from(5, TimeUnit.MILLISECONDS);
            }

            @Override
            public Timeout timeout() {
                return Timeout.from(1, TimeUnit.SECONDS);
            }

            @Override
            public Action action() {
                return context -> context.put("sampleKafkaState", "produced");
            }
        }
    }
}
