package framework.core

import framework.core.dsl.dependsOn
import framework.core.dsl.testSuite
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ValidationTest {
    @Test
    fun `validate reports no issues for a valid suite`() {
        val suite =
            testSuite(id = "suite") {
                case(id = "case-1") {
                    step(id = "prepare") { StepResult.passed() }
                    step(id = "execute", dependsOn = dependsOn("prepare")) { StepResult.passed() }
                }
            }

        val report = suite.validate()

        assertTrue(report.isValid)
        assertTrue(report.issues.isEmpty())
    }

    @Test
    fun `validate reports duplicates and unknown dependencies`() {
        val suite =
            testSuite(id = "suite") {
                case(id = "dup-case") {
                    step(id = "same") { StepResult.passed() }
                    parallel(name = "fanout") {
                        branch("b1") {
                            step(id = "same") { StepResult.passed() }
                        }
                    }
                    step(id = "bad-deps", dependsOn = dependsOn("missing", "bad-deps")) { StepResult.passed() }
                }
                case(id = "dup-case") {
                    step(id = "other") { StepResult.passed() }
                }
            }

        val issues = suite.validate().issues

        assertEquals(5, issues.size)
        assertTrue(issues.any { it.code == "DUPLICATE_CASE_ID" })
        assertTrue(issues.any { it.code == "DUPLICATE_STEP_ID" })
        assertTrue(issues.any { it.code == "UNKNOWN_DEPENDENCY" })
        assertTrue(issues.any { it.code == "SELF_DEPENDENCY" })
        assertTrue(issues.any { it.code == "CYCLIC_DEPENDENCY" })
    }

    @Test
    fun `validate reports cyclic dependencies`() {
        val suite =
            testSuite(id = "suite") {
                case(id = "cycle") {
                    step(id = "a", dependsOn = dependsOn("b")) { StepResult.passed() }
                    step(id = "b", dependsOn = dependsOn("c")) { StepResult.passed() }
                    step(id = "c", dependsOn = dependsOn("a")) { StepResult.passed() }
                }
            }

        val issues = suite.validate().issues

        assertEquals(1, issues.size)
        assertEquals("CYCLIC_DEPENDENCY", issues.first().code)
    }

    @Test
    fun `requireValid throws for invalid suite`() {
        val suite =
            testSuite(id = "suite") {
                case(id = "case") {
                    step(id = "a", dependsOn = dependsOn("missing")) { StepResult.passed() }
                }
            }

        assertFailsWith<IllegalArgumentException> {
            suite.requireValid()
        }
    }
}
