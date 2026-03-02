# framework-core DSL

`framework-core` definerer sentrale modeller (`TestSuite`, `TestCase`, `TestStep`) og et Kotlin DSL-lag for å beskrive flyt i tester.

## Eksempel

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

        // kjøres først etter at alle parallel branches er ferdige (barrier/join)
        step(id = "final-assert") {
            StepResult.passed(
                assertions = listOf(AssertionResult("all checks done", success = true)),
            )
        }
    }
}
```

## Designnotater

- **Tydelig rekkefølge:** `TestCase.blocks` opprettholder deklarert rekkefølge.
- **Parallelle blokker med barrier:** `ExecutionBlock.Parallel` kapsler branches; runtime kan vente på alle branches før neste blokk.
- **Avhengigheter:** `TestStep.dependencies` + delt typed state i `StepContext` gjør det enkelt å bruke output fra ett steg i neste.
- **Bibliotek-vennlig:** DSL-et bygger kun immutable modeller, slik at team kan bruke egne executors/runtimes.

## Transport contract and compatibility

`framework-core` now exposes a shared transport contract in `no.testframework.framework.core.transport` with:

- canonical request/response/error models
- protocol metadata (`protocolVersion` + capability flags)
- compatibility guard (`TransportCompatibility.requireCompatible`) used by the runner at startup

Transport modules consume shared contract tests via `TransportContractSuite` from `framework-core` test fixtures.
