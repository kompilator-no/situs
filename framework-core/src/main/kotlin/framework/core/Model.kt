package framework.core

import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

enum class ExecutionMode {
    SEQUENTIAL,
    PARALLEL,
}

class StepContext internal constructor(
    private val state: MutableMap<ContextKey<*>, Any?> = ConcurrentHashMap(),
) {
    fun <T : Any> put(key: ContextKey<T>, value: T) {
        state[key] = value
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(key: ContextKey<T>): T? = state[key] as T?

    fun <T : Any> require(key: ContextKey<T>): T =
        get(key) ?: error("Missing context value for key '${key.name}'")

    fun contains(key: ContextKey<*>): Boolean = state.containsKey(key)

    fun snapshot(): Map<ContextKey<*>, Any?> = state.toMap()

    companion object {
        fun empty(): StepContext = StepContext()
    }
}

data class ContextKey<T : Any>(
    val name: String,
    val type: KClass<T>,
)

inline fun <reified T : Any> contextKey(name: String): ContextKey<T> = ContextKey(name, T::class)

data class AssertionResult(
    val name: String,
    val success: Boolean,
    val message: String? = null,
)

data class StepResult(
    val status: Status,
    val assertions: List<AssertionResult> = emptyList(),
    val metadata: Map<String, Any?> = emptyMap(),
    val error: Throwable? = null,
) {
    enum class Status {
        PASSED,
        FAILED,
        SKIPPED,
    }

    companion object {
        fun passed(
            assertions: List<AssertionResult> = emptyList(),
            metadata: Map<String, Any?> = emptyMap(),
        ): StepResult = StepResult(Status.PASSED, assertions, metadata)

        fun failed(
            assertions: List<AssertionResult> = emptyList(),
            error: Throwable? = null,
            metadata: Map<String, Any?> = emptyMap(),
        ): StepResult = StepResult(Status.FAILED, assertions, metadata, error)

        fun skipped(reason: String? = null): StepResult =
            StepResult(
                status = Status.SKIPPED,
                assertions = emptyList(),
                metadata = reason?.let { mapOf("reason" to it) } ?: emptyMap(),
            )
    }
}

fun interface StepAction {
    fun run(context: StepContext): StepResult
}

data class TestStep(
    val id: String,
    val name: String,
    val dependencies: Set<String> = emptySet(),
    val action: StepAction,
)

data class ParallelBlock(
    val name: String,
    val branches: List<ExecutionBlock>,
)

sealed interface ExecutionBlock {
    val mode: ExecutionMode

    data class StepBlock(val step: TestStep) : ExecutionBlock {
        override val mode: ExecutionMode = ExecutionMode.SEQUENTIAL
    }

    data class Sequence(
        val name: String,
        val blocks: List<ExecutionBlock>,
    ) : ExecutionBlock {
        override val mode: ExecutionMode = ExecutionMode.SEQUENTIAL
    }

    data class Parallel(val block: ParallelBlock) : ExecutionBlock {
        override val mode: ExecutionMode = ExecutionMode.PARALLEL
    }
}

data class TestCase(
    val id: String,
    val name: String,
    val blocks: List<ExecutionBlock>,
)

data class TestSuite(
    val id: String,
    val name: String,
    val cases: List<TestCase>,
)
