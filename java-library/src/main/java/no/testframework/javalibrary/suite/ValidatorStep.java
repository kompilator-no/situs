package no.testframework.javalibrary.suite;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

public interface ValidatorStep extends Step {
    Validator validator();

    @Override
    default String name() {
        return "Validator step";
    }

    @Override
    default String description() {
        return "";
    }

    default Delay delay() {
        return Delay.none();
    }

    default Timeout timeout() {
        return Timeout.defaultTimeout();
    }

    @Override
    default void execute(TestExecutionContext context) {
        Objects.requireNonNull(context, "context cannot be null");
        Validator definedValidator = Objects.requireNonNull(validator(), "validator cannot be null");

        try {
            delay().sleepIfNeeded();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Validator step interrupted during delay", exception);
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> future = executor.submit(() -> definedValidator.validate(context));
            Boolean valid = future.get(timeout().value(), timeout().unit());
            if (!Boolean.TRUE.equals(valid)) {
                throw new IllegalStateException("Validator step returned false");
            }
        } catch (TimeoutException exception) {
            throw new IllegalStateException("Validator step timed out", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Validator step interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Validator step failed", cause);
        } finally {
            executor.shutdownNow();
        }
    }

    static Builder builder(Validator validator) {
        return new Builder(validator);
    }

    static ValidatorStep from(GenericValidator genericValidator) {
        Objects.requireNonNull(genericValidator, "genericValidator cannot be null");
        Registry<String, Object> registry = new InMemoryRegistry();
        return builder(context -> {
            Attempts attempts = genericValidator.attempts();
            for (int attempt = 1; attempt <= attempts.value(); attempt++) {
                ValidatorContext validatorContext = new ValidatorContext(registry, attempt);
                try {
                    genericValidator.onStarted(validatorContext);
                    Assertion assertion = genericValidator.callback();
                    genericValidator.onFinished();
                    if (assertion.passed()) {
                        return true;
                    }
                    if (attempt < attempts.value()) {
                        attempts.delay().sleepIfNeeded();
                    }
                } catch (RuntimeException exception) {
                    genericValidator.onException(exception);
                    throw exception;
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Validator attempts interrupted", exception);
                }
            }
            return false;
        }).name("Generic validator step").build();
    }

    final class Builder {
        private final Validator validator;
        private String name = "Validator step";
        private String description = "";
        private Delay delay = Delay.none();
        private Timeout timeout = Timeout.defaultTimeout();
        private Runnable onStarted = () -> {};
        private Runnable onFinished = () -> {};

        private Builder(Validator validator) {
            this.validator = Objects.requireNonNull(validator, "validator cannot be null");
        }

        public Builder name(String name) {
            this.name = ActionStep.requireNonBlank(name, "name");
            return this;
        }

        public Builder description(String description) {
            this.description = description == null ? "" : description;
            return this;
        }

        public Builder delay(Delay delay) {
            this.delay = Objects.requireNonNull(delay, "delay cannot be null");
            return this;
        }

        public Builder timeout(Timeout timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout cannot be null");
            return this;
        }

        public Builder onStarted(Runnable onStarted) {
            this.onStarted = Objects.requireNonNull(onStarted, "onStarted cannot be null");
            return this;
        }

        public Builder onFinished(Runnable onFinished) {
            this.onFinished = Objects.requireNonNull(onFinished, "onFinished cannot be null");
            return this;
        }

        public ValidatorStep build() {
            return new BuiltValidatorStep(name, description, delay, timeout, validator, onStarted, onFinished);
        }
    }

    record BuiltValidatorStep(
            String name,
            String description,
            Delay delay,
            Timeout timeout,
            Validator validator,
            Runnable startedHook,
            Runnable finishedHook
    ) implements ValidatorStep {
        public BuiltValidatorStep {
            name = ActionStep.requireNonBlank(name, "name");
            description = description == null ? "" : description;
            delay = Objects.requireNonNull(delay, "delay cannot be null");
            timeout = Objects.requireNonNull(timeout, "timeout cannot be null");
            validator = Objects.requireNonNull(validator, "validator cannot be null");
            startedHook = Objects.requireNonNull(startedHook, "startedHook cannot be null");
            finishedHook = Objects.requireNonNull(finishedHook, "finishedHook cannot be null");
        }

        @Override
        public void onStarted() {
            startedHook.run();
        }

        @Override
        public void onFinished() {
            finishedHook.run();
        }
    }
}
