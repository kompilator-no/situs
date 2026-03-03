package no.testframework.javalibrary.suite;

import no.testframework.javalibrary.runtime.TestExecutionContext;
import no.testframework.javalibrary.runtime.TestStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SuiteApiTest {

    @Test
    void actionStepBuilderAndInterfaceImplementationAreSupported() {
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicBoolean finished = new AtomicBoolean(false);

        ActionStep built = ActionStep.builder(context -> context.put("a", "b"))
                .name("built action")
                .description("builder")
                .delay(Delay.from(1, TimeUnit.MILLISECONDS))
                .timeout(Timeout.from(1, TimeUnit.SECONDS))
                .onStarted(() -> started.set(true))
                .onFinished(() -> finished.set(true))
                .build();

        ActionStep implemented = new ActionStep() {
            @Override
            public String name() {
                return "implemented action";
            }

            @Override
            public Action action() {
                return context -> context.put("c", "d");
            }
        };

        TestCase testCase = new TestCase() {
            @Override
            public String name() {
                return "ActionStep test case";
            }

            @Override
            public List<Step> steps() {
                return List.of(built, implemented);
            }
        };

        TestSuite suite = new TestSuite() {
            @Override
            public String name() {
                return "ActionStep suite";
            }

            @Override
            public List<TestCase> testCases() {
                return List.of(testCase);
            }
        };

        SuiteResult result = SuiteApi.create().runSuite(suite);

        assertEquals(TestStatus.PASSED, result.status());
        assertEquals("b", result.contextSnapshot().get("a"));
        assertEquals("d", result.contextSnapshot().get("c"));
        assertEquals(true, started.get());
        assertEquals(true, finished.get());
    }




    @Test
    void genericActionAndGenericValidatorAreSupported() {
        GenericAction genericAction = GenericAction.builder(() -> java.util.Optional.of(Report.from("ok")))
                .iterations(Iterations.from(2))
                .build();

        GenericValidator genericValidator = GenericValidator.builder(() -> Assertion.success())
                .attempts(Attempts.from(2, Delay.from(1, TimeUnit.MILLISECONDS)))
                .build();

        TestCase testCase = new TestCase() {
            @Override
            public String name() {
                return "Generic test case";
            }

            @Override
            public List<Step> steps() {
                return List.of(
                        ActionStep.from(genericAction),
                        ValidatorStep.from(genericValidator)
                );
            }
        };

        TestSuite suite = new TestSuite() {
            @Override
            public String name() {
                return "Generic suite";
            }

            @Override
            public List<TestCase> testCases() {
                return List.of(testCase);
            }
        };

        SuiteResult result = SuiteApi.create().runSuite(suite);
        assertEquals(TestStatus.PASSED, result.status());
    }

    @Test
    void validatorStepBuilderAndInterfaceImplementationAreSupported() {
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicBoolean finished = new AtomicBoolean(false);

        ValidatorStep built = ValidatorStep.builder(context -> "ok".equals(context.get("v1", String.class)))
                .name("built validator")
                .description("builder")
                .delay(Delay.from(1, TimeUnit.MILLISECONDS))
                .timeout(Timeout.from(1, TimeUnit.SECONDS))
                .onStarted(() -> started.set(true))
                .onFinished(() -> finished.set(true))
                .build();

        ValidatorStep implemented = new ValidatorStep() {
            @Override
            public String name() {
                return "implemented validator";
            }

            @Override
            public Validator validator() {
                return context -> "done".equals(context.get("v2", String.class));
            }
        };

        TestCase testCase = new TestCase() {
            @Override
            public String name() {
                return "ValidatorStep test case";
            }

            @Override
            public List<Step> steps() {
                return List.of(
                        ActionStep.builder(context -> context.put("v1", "ok")).build(),
                        ActionStep.builder(context -> context.put("v2", "done")).build(),
                        built,
                        implemented
                );
            }
        };

        TestSuite suite = new TestSuite() {
            @Override
            public String name() {
                return "ValidatorStep suite";
            }

            @Override
            public List<TestCase> testCases() {
                return List.of(testCase);
            }
        };

        SuiteResult result = SuiteApi.create().runSuite(suite);

        assertEquals(TestStatus.PASSED, result.status());
        assertEquals(true, started.get());
        assertEquals(true, finished.get());
    }

    @Test
    void runsAdvancedStyleSuiteAndTestCases() {
        SuiteApi api = SuiteApi.create();

        SuiteResult result = api.runSuite(new AdvancedTestSuite());

        assertEquals(TestStatus.PASSED, result.status());
        assertEquals(2, result.testCaseResults().size());
        assertEquals("endpoint 1 response", result.contextSnapshot().get("httpPayload"));
        assertEquals("processed", result.contextSnapshot().get("kafkaState"));
    }

    private static final class AdvancedTestSuite implements TestSuite {

        @Override
        public String name() {
            return "Test Suite Name";
        }

        @Override
        public String description() {
            return "Test Suite Description";
        }

        @Override
        public ExecutionStrategy executionStrategy() {
            return ExecutionStrategy.SEQUENTIAL;
        }

        @Override
        public List<TestCase> testCases() {
            return List.of(
                    new MyHttpTestCase(),
                    new MyKafkaTestCase()
            );
        }
    }

    private static final class MyHttpTestCase implements TestCase {

        @Override
        public String name() {
            return "HTTP Test Case";
        }

        @Override
        public String description() {
            return "Validate HTTP endpoint metadata and payload";
        }

        @Override
        public HttpEndpoints httpEndpoints() {
            return HttpEndpoints.from(
                    8080,
                    List.of(
                            HttpEndpoint.from(
                                    "/endpoint1/",
                                    Map.of(
                                            "Content-Type", "text/html",
                                            "Accept-Language", "en-US"
                                    ),
                                    () -> new HttpBody("endpoint 1 response")
                            )
                    )
            );
        }

        @Override
        public List<Step> steps() {
            return List.of(
                    new Step() {
                        @Override
                        public String name() {
                            return "Store endpoint response";
                        }

                        @Override
                        public void execute(TestExecutionContext context) {
                            HttpBody body = httpEndpoints().endpoints().getFirst().bodySupplier().get();
                            context.put("httpPayload", body.value());
                        }
                    },
                    new Step() {
                        @Override
                        public String name() {
                            return "Validate endpoint response";
                        }

                        @Override
                        public void execute(TestExecutionContext context) {
                            String payload = context.get("httpPayload", String.class);
                            if (!"endpoint 1 response".equals(payload)) {
                                throw new IllegalStateException("Unexpected payload");
                            }
                        }
                    }
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
            return "Simulate message workflow";
        }

        @Override
        public List<Step> steps() {
            return List.of(
                    new Step() {
                        @Override
                        public String name() {
                            return "Produce event";
                        }

                        @Override
                        public void execute(TestExecutionContext context) {
                            context.put("kafkaState", "produced");
                        }
                    },
                    new Step() {
                        @Override
                        public String name() {
                            return "Consume and process event";
                        }

                        @Override
                        public void execute(TestExecutionContext context) {
                            if (!"produced".equals(context.get("kafkaState", String.class))) {
                                throw new IllegalStateException("Event not produced");
                            }
                            context.put("kafkaState", "processed");
                        }
                    }
            );
        }
    }
}
