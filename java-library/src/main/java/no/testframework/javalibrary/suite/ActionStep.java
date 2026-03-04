package no.testframework.javalibrary.suite;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

public interface ActionStep extends Step {
    Action action();

    @Override
    default String name() {
        return "Action step";
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
        Action definedAction = Objects.requireNonNull(action(), "action cannot be null");

        try {
            delay().sleepIfNeeded();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Action step interrupted during delay", exception);
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = executor.submit(() -> definedAction.execute(context));
            future.get(timeout().value(), timeout().unit());
        } catch (TimeoutException exception) {
            throw new IllegalStateException("Action step timed out", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Action step interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Action step failed", cause);
        } finally {
            executor.shutdownNow();
        }
    }

    static Builder builder(Action action) {
        return new Builder(action);
    }

    static ActionStep from(GenericAction genericAction) {
        Objects.requireNonNull(genericAction, "genericAction cannot be null");
        Registry<String, Object> registry = new InMemoryRegistry();
        return builder(context -> {
            Iterations iterations = genericAction.iterations();
            for (int i = 1; i <= iterations.value(); i++) {
                ActionContext actionContext = new ActionContext(registry, i);
                try {
                    genericAction.onStarted(actionContext);
                    genericAction.callback().ifPresent(report -> {
                        if (!report.message().isBlank()) {
                            registry.register("lastActionReport", report.message());
                        }
                    });
                    genericAction.onFinished();
                } catch (RuntimeException exception) {
                    genericAction.onException(exception);
                    throw exception;
                }
            }
        }).name("Generic action step").build();
    }

    static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return value;
    }

    final class Builder {
        private final Action action;
        private String name = "Action step";
        private String description = "";
        private Delay delay = Delay.none();
        private Timeout timeout = Timeout.defaultTimeout();
        private Runnable onStarted = () -> {};
        private Runnable onFinished = () -> {};

        private Builder(Action action) {
            this.action = Objects.requireNonNull(action, "action cannot be null");
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

        public ActionStep build() {
            return new BuiltActionStep(name, description, delay, timeout, action, onStarted, onFinished);
        }

    }

    record BuiltActionStep(
            String name,
            String description,
            Delay delay,
            Timeout timeout,
            Action action,
            Runnable startedHook,
            Runnable finishedHook
    ) implements ActionStep {
        public BuiltActionStep {
            name = ActionStep.requireNonBlank(name, "name");
            description = description == null ? "" : description;
            delay = Objects.requireNonNull(delay, "delay cannot be null");
            timeout = Objects.requireNonNull(timeout, "timeout cannot be null");
            action = Objects.requireNonNull(action, "action cannot be null");
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
