package framework.core.dsl

import framework.core.ExecutionBlock
import framework.core.ParallelBlock
import framework.core.StepAction
import framework.core.StepContext
import framework.core.StepResult
import framework.core.TestCase
import framework.core.TestStep
import framework.core.TestSuite

fun testSuite(
    id: String,
    name: String = id,
    block: TestSuiteBuilder.() -> Unit,
): TestSuite = TestSuiteBuilder(id, name).apply(block).build()

class TestSuiteBuilder internal constructor(
    private val id: String,
    private val name: String,
) {
    private val cases = mutableListOf<TestCase>()

    fun case(
        id: String,
        name: String = id,
        block: TestCaseBuilder.() -> Unit,
    ) {
        cases += TestCaseBuilder(id, name).apply(block).build()
    }

    fun build(): TestSuite = TestSuite(id = id, name = name, cases = cases.toList())
}

class TestCaseBuilder internal constructor(
    private val id: String,
    private val name: String,
) {
    private val blocks = mutableListOf<ExecutionBlock>()

    fun step(
        id: String,
        name: String = id,
        dependsOn: Set<String> = emptySet(),
        action: StepContext.() -> StepResult,
    ) {
        blocks += ExecutionBlock.StepBlock(
            step = TestStep(
                id = id,
                name = name,
                dependencies = dependsOn,
                action = StepAction { context -> action(context) },
            ),
        )
    }

    fun parallel(
        name: String,
        block: ParallelBlockBuilder.() -> Unit,
    ) {
        val parallelBlock = ParallelBlockBuilder(name).apply(block).build()
        blocks += ExecutionBlock.Parallel(parallelBlock)
    }

    fun build(): TestCase = TestCase(id = id, name = name, blocks = blocks.toList())
}

class ParallelBlockBuilder internal constructor(
    private val name: String,
) {
    private val branches = mutableListOf<ExecutionBlock>()

    /**
     * Defines one branch in a parallel section.
     * Branch internals are sequential unless nested parallel blocks are added.
     */
    fun branch(
        label: String,
        block: TestCaseBuilder.() -> Unit,
    ) {
        val branchBlocks = TestCaseBuilder(id = label, name = label).apply(block).build().blocks
        branches += ExecutionBlock.Sequence(name = label, blocks = branchBlocks)
    }

    fun build(): ParallelBlock = ParallelBlock(name = name, branches = branches.toList())
}

fun dependsOn(vararg stepIds: String): Set<String> = stepIds.toSet()
