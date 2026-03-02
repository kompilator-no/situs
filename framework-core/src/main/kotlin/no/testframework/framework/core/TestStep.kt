package no.testframework.framework.core

interface TestStep {
    fun execute(context: MutableMap<String, Any?>)
}
