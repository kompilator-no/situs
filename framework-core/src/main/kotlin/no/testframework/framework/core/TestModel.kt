package no.testframework.framework.core

import kotlinx.serialization.Serializable

@Serializable
data class TestModel(
    val id: String,
    val name: String,
    val steps: List<String>
)
