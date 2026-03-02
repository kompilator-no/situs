# framework-core DSL

`framework-core` defines core models (`TestSuite`, `TestCase`, `TestStep`) and a Kotlin DSL layer for describing test flow.

## Example

```kotlin
import framework.core.AssertionResult
import framework.core.StepResult
import framework.core.contextKey
import framework.core.dsl.dependsOn
import framework.core.dsl.testSuite

val kafkaPayload = contextKey<String>("kafka.payload")

val suite = testSuite(id = "orders") {
    case(id = "create-order") {
        step(id = "produce-kafka") {
            put(kafkaPayload, "order-123")
            StepResult.passed(
                assertions = listOf(AssertionResult("kafka publish", success = true)),
            )
        }

        parallel(name = "fanout") {
            branch("http-verify") {
                step(id = "call-http", dependsOn = dependsOn("produce-kafka")) {
                    val payload = require(kafkaPayload)
                    StepResult.passed(metadata = mapOf("payload" to payload))
                }
            }

            branch("audit") {
                step(id = "write-audit") { StepResult.passed() }
            }
        }

        // runs only after all parallel branches are complete (barrier/join)
        step(id = "final-assert") {
            StepResult.passed(
                assertions = listOf(AssertionResult("all checks done", success = true)),
            )
        }
    }
}
```

## Design notes

- **Clear ordering:** `TestCase.blocks` preserves declared execution order.
- **Parallel blocks with a barrier:** `ExecutionBlock.Parallel` encapsulates branches; the runtime can wait for all branches before the next block.
- **Dependencies:** `TestStep.dependencies` + shared typed state in `StepContext` make it easy to use output from one step in the next.
- **Library-friendly:** The DSL builds immutable models only, so teams can use their own executors/runtimes.

## Transport contract and compatibility

`framework-core` now exposes a shared transport contract in `no.testframework.framework.core.transport` with:

- canonical request/response/error models
- protocol metadata (`protocolVersion` + capability flags)
- compatibility guard (`TransportCompatibility.requireCompatible`) used by the runner at startup

Transport modules consume shared contract tests via `TransportContractSuite` from `framework-core` test fixtures.
