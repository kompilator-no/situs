package no.testframework.framework.core

class TestOrchestrator {
    fun run(steps: List<TestStep>, context: MutableMap<String, Any?> = mutableMapOf()) {
        steps.forEach { step -> step.execute(context) }
    }
}
