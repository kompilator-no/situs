package framework.core

data class SuiteValidationIssue(
    val code: String,
    val message: String,
    val path: String,
)

data class SuiteValidationReport(
    val issues: List<SuiteValidationIssue>,
) {
    val isValid: Boolean = issues.isEmpty()
}

fun TestSuite.validate(): SuiteValidationReport {
    val issues = mutableListOf<SuiteValidationIssue>()

    val duplicateCaseIds = cases.groupBy { it.id }.filterValues { it.size > 1 }.keys
    duplicateCaseIds.forEach { caseId ->
        issues += SuiteValidationIssue(
            code = "DUPLICATE_CASE_ID",
            message = "Case id '$caseId' is used more than once in suite '$id'.",
            path = "suite:$id/case:$caseId",
        )
    }

    cases.forEach { testCase ->
        val steps = collectSteps(testCase.blocks)
        val stepById = steps.groupBy { it.id }

        stepById
            .filterValues { it.size > 1 }
            .keys
            .forEach { stepId ->
                issues += SuiteValidationIssue(
                    code = "DUPLICATE_STEP_ID",
                    message = "Step id '$stepId' is used more than once in case '${testCase.id}'.",
                    path = "suite:$id/case:${testCase.id}/step:$stepId",
                )
            }

        val knownStepIds = stepById.keys

        steps.forEach { step ->
            step.dependencies.forEach { dependency ->
                if (dependency == step.id) {
                    issues += SuiteValidationIssue(
                        code = "SELF_DEPENDENCY",
                        message = "Step '${step.id}' depends on itself in case '${testCase.id}'.",
                        path = "suite:$id/case:${testCase.id}/step:${step.id}",
                    )
                }

                if (dependency !in knownStepIds) {
                    issues += SuiteValidationIssue(
                        code = "UNKNOWN_DEPENDENCY",
                        message = "Step '${step.id}' depends on unknown step '$dependency' in case '${testCase.id}'.",
                        path = "suite:$id/case:${testCase.id}/step:${step.id}",
                    )
                }
            }
        }

        val graph = steps.associate { it.id to it.dependencies.filter { dep -> dep in knownStepIds }.toSet() }
        findDependencyCycles(graph).forEach { cycle ->
            issues += SuiteValidationIssue(
                code = "CYCLIC_DEPENDENCY",
                message = "Dependency cycle detected in case '${testCase.id}': ${cycle.joinToString(" -> ")}",
                path = "suite:$id/case:${testCase.id}",
            )
        }
    }

    return SuiteValidationReport(issues)
}

fun TestSuite.requireValid() {
    val report = validate()
    if (report.isValid) {
        return
    }

    val reason = report.issues.joinToString(separator = "\n") { "[${it.code}] ${it.path}: ${it.message}" }
    throw IllegalArgumentException("Invalid test suite '$id':\n$reason")
}

private fun collectSteps(blocks: List<ExecutionBlock>): List<TestStep> =
    blocks.flatMap { block ->
        when (block) {
            is ExecutionBlock.StepBlock -> listOf(block.step)
            is ExecutionBlock.Sequence -> collectSteps(block.blocks)
            is ExecutionBlock.Parallel -> collectSteps(block.block.branches)
        }
    }

private fun findDependencyCycles(graph: Map<String, Set<String>>): Set<List<String>> {
    val cycles = linkedSetOf<List<String>>()
    val visiting = mutableSetOf<String>()
    val visited = mutableSetOf<String>()
    val path = ArrayDeque<String>()

    fun dfs(node: String) {
        if (node in visiting) {
            val cycleStart = path.indexOf(node)
            if (cycleStart >= 0) {
                val cycle = path.drop(cycleStart) + node
                cycles += normalizeCycle(cycle)
            }
            return
        }

        if (node in visited) {
            return
        }

        visiting += node
        path += node

        graph[node].orEmpty().forEach { dependency ->
            dfs(dependency)
        }

        path.removeLast()
        visiting.remove(node)
        visited += node
    }

    graph.keys.forEach(::dfs)
    return cycles
}

private fun normalizeCycle(cycle: List<String>): List<String> {
    if (cycle.size <= 1) {
        return cycle
    }

    val withoutClosingNode = cycle.dropLast(1)
    val minIndex = withoutClosingNode.indices.minBy { withoutClosingNode[it] }
    val rotated = withoutClosingNode.drop(minIndex) + withoutClosingNode.take(minIndex)
    return rotated + rotated.first()
}
