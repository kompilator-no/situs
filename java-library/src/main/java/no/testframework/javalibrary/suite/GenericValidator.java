package no.testframework.javalibrary.suite;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface GenericValidator {
    default void onStarted(ValidatorContext context) {
    }

    default Attempts attempts() {
        return Attempts.from(1);
    }

    Assertion callback();

    default void onFinished() {
    }

    default void onException(Exception exception) {
    }

    static Builder builder(Supplier<Assertion> callback) {
        return new Builder(callback);
    }

    final class Builder {
        private final Supplier<Assertion> callbackSupplier;
        private Attempts attempts = Attempts.from(1);
        private Consumer<ValidatorContext> startedHook = ignored -> {};
        private Runnable finishedHook = () -> {};
        private Consumer<Exception> exceptionHook = ignored -> {};

        private Builder(Supplier<Assertion> callbackSupplier) {
            this.callbackSupplier = Objects.requireNonNull(callbackSupplier, "callback cannot be null");
        }

        public Builder attempts(Attempts attempts) {
            this.attempts = Objects.requireNonNull(attempts, "attempts cannot be null");
            return this;
        }

        public Builder onStarted(Consumer<ValidatorContext> onStarted) {
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

        public GenericValidator build() {
            return new BuiltGenericValidator(callbackSupplier, attempts, startedHook, finishedHook, exceptionHook);
        }
    }

    final class BuiltGenericValidator implements GenericValidator {
        private final Supplier<Assertion> callbackSupplier;
        private final Attempts attempts;
        private final Consumer<ValidatorContext> startedHook;
        private final Runnable finishedHook;
        private final Consumer<Exception> exceptionHook;

        private BuiltGenericValidator(
                Supplier<Assertion> callbackSupplier,
                Attempts attempts,
                Consumer<ValidatorContext> startedHook,
                Runnable finishedHook,
                Consumer<Exception> exceptionHook
        ) {
            this.callbackSupplier = Objects.requireNonNull(callbackSupplier, "callback cannot be null");
            this.attempts = Objects.requireNonNull(attempts, "attempts cannot be null");
            this.startedHook = Objects.requireNonNull(startedHook, "startedHook cannot be null");
            this.finishedHook = Objects.requireNonNull(finishedHook, "finishedHook cannot be null");
            this.exceptionHook = Objects.requireNonNull(exceptionHook, "exceptionHook cannot be null");
        }

        @Override
        public void onStarted(ValidatorContext context) {
            startedHook.accept(context);
        }

        @Override
        public Attempts attempts() {
            return attempts;
        }

        @Override
        public Assertion callback() {
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
