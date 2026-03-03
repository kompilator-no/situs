package no.testframework.javalibrary.suite;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface GenericAction {
    default void onStarted(ActionContext context) {
    }

    default Iterations iterations() {
        return Iterations.from(1);
    }

    Optional<Report> callback();

    default void onFinished() {
    }

    default void onException(Exception exception) {
    }

    static Builder builder(Supplier<Optional<Report>> callback) {
        return new Builder(callback);
    }

    final class Builder {
        private final Supplier<Optional<Report>> callbackSupplier;
        private Iterations iterations = Iterations.from(1);
        private Consumer<ActionContext> startedHook = ignored -> {};
        private Runnable finishedHook = () -> {};
        private Consumer<Exception> exceptionHook = ignored -> {};

        private Builder(Supplier<Optional<Report>> callbackSupplier) {
            this.callbackSupplier = Objects.requireNonNull(callbackSupplier, "callback cannot be null");
        }

        public Builder iterations(Iterations iterations) {
            this.iterations = Objects.requireNonNull(iterations, "iterations cannot be null");
            return this;
        }

        public Builder onStarted(Consumer<ActionContext> onStarted) {
            this.startedHook = Objects.requireNonNull(onStarted, "onStarted cannot be null");
            return this;
        }

        public Builder onFinished(Runnable onFinished) {
            this.finishedHook = Objects.requireNonNull(onFinished, "onFinished cannot be null");
            return this;
        }

        public Builder onException(Consumer<Exception> onException) {
            this.exceptionHook = Objects.requireNonNull(onException, "onException cannot be null");
            return this;
        }

        public GenericAction build() {
            return new BuiltGenericAction(callbackSupplier, iterations, startedHook, finishedHook, exceptionHook);
        }
    }

    final class BuiltGenericAction implements GenericAction {
        private final Supplier<Optional<Report>> callbackSupplier;
        private final Iterations iterations;
        private final Consumer<ActionContext> startedHook;
        private final Runnable finishedHook;
        private final Consumer<Exception> exceptionHook;

        private BuiltGenericAction(
                Supplier<Optional<Report>> callbackSupplier,
                Iterations iterations,
                Consumer<ActionContext> startedHook,
                Runnable finishedHook,
                Consumer<Exception> exceptionHook
        ) {
            this.callbackSupplier = Objects.requireNonNull(callbackSupplier, "callback cannot be null");
            this.iterations = Objects.requireNonNull(iterations, "iterations cannot be null");
            this.startedHook = Objects.requireNonNull(startedHook, "startedHook cannot be null");
            this.finishedHook = Objects.requireNonNull(finishedHook, "finishedHook cannot be null");
            this.exceptionHook = Objects.requireNonNull(exceptionHook, "exceptionHook cannot be null");
        }

        @Override
        public void onStarted(ActionContext context) {
            startedHook.accept(context);
        }

        @Override
        public Iterations iterations() {
            return iterations;
        }

        @Override
        public Optional<Report> callback() {
            return callbackSupplier.get();
        }

        @Override
        public void onFinished() {
            finishedHook.run();
        }

        @Override
        public void onException(Exception exception) {
            exceptionHook.accept(exception);
        }
    }
}
