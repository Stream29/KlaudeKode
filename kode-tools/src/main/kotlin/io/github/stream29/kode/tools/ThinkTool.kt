package io.github.stream29.kode.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import io.github.stream29.kode.ui.core.MessageHandler
import kotlinx.serialization.Serializable

/**
 * Think tool for structured thinking and planning.
 * Based on kimi-cli's think tool - allows the agent to pause and think through problems.
 */
@Suppress("unused")
@LLMDescription(
    "A tool for structured thinking and problem-solving. " +
    "Use this when you need to think through a complex problem, break down a task, " +
    "or plan your approach before taking action. This tool does not perform any action " +
    "but gives you space to reason through the problem and share your thinking."
)
public class ThinkTool public constructor(
    private val messageHandler: MessageHandler,
    private val logger: (String) -> Unit = { println(it) },
) : ToolSet {

    @Tool
    @LLMDescription(
        "Use this tool to think through a problem or plan your approach. " +
        "This is a 'thinking pause' - use it when you need to reason about something " +
        "before taking action. Good for: breaking down complex tasks, analyzing trade-offs, " +
        "planning multi-step approaches, or verifying your understanding of requirements."
    )
    public fun think(
        @LLMDescription("Your thoughts, reasoning, or plan. Explain what you're thinking about and why.")
        thought: String,
        @LLMDescription("Optional: What you plan to do next based on this thinking")
        nextAction: String? = null,
    ): ThinkResult {
        logger("💭 Agent thinking: ${thought.take(100)}${if (thought.length > 100) "..." else ""}")

        messageHandler.addMessageToUser("💭 Thinking: $thought")
        nextAction?.let { messageHandler.addMessageToUser("➡️ Next: $it") }

        return ThinkResult(
            success = true,
            thought = thought,
            nextAction = nextAction,
            message = "Thought recorded successfully",
        )
    }

    @Tool
    @LLMDescription(
        "Analyze a problem by breaking it down into components. " +
        "Use this for complex tasks to identify sub-problems, constraints, and approaches."
    )
    public fun analyzeProblem(
        @LLMDescription("A brief description of the problem or task")
        problem: String,
        @LLMDescription("Key components or aspects of the problem")
        components: List<String>,
        @LLMDescription("Potential challenges or constraints")
        challenges: List<String>? = null,
        @LLMDescription("Possible approaches to solve the problem")
        approaches: List<String>? = null,
    ): AnalysisResult {
        logger("🔍 Analyzing problem: $problem")
        messageHandler.addMessageToUser("🔍 Analyzing: $problem")

        val analysis = ProblemAnalysis(
            problem = problem,
            components = components,
            challenges = challenges.orEmpty(),
            approaches = approaches.orEmpty(),
        )

        return AnalysisResult(
            success = true,
            analysis = analysis,
            message = "Problem analysis complete",
        )
    }

    @Tool
    @LLMDescription(
        "Create a step-by-step plan for completing a task. " +
        "Use this to organize your work into clear, actionable steps."
    )
    public fun createPlan(
        @LLMDescription("The overall goal or task")
        goal: String,
        @LLMDescription("The specific steps to achieve the goal")
        steps: List<String>,
        @LLMDescription("Optional estimated time or complexity for each step")
        stepComplexity: List<String>? = null,
    ): PlanResult {
        logger("📋 Creating plan for: $goal")
        messageHandler.addMessageToUser("📋 Planning: $goal")

        val planSteps = steps.mapIndexed { index, step ->
            PlanStep(
                number = index + 1,
                description = step,
                complexity = stepComplexity?.getOrNull(index) ?: DEFAULT_STEP_COMPLEXITY,
            )
        }

        val plan = ExecutionPlan(
            goal = goal,
            steps = planSteps,
            totalSteps = steps.size,
        )

        return PlanResult(
            success = true,
            plan = plan,
            message = "Created plan with ${steps.size} steps for: $goal",
        )
    }

    private companion object {
        const val DEFAULT_STEP_COMPLEXITY: String = "medium"
    }
}

/**
 * Result of a think operation
 */
@Serializable
public data class ThinkResult(
    val success: Boolean,
    val thought: String,
    val nextAction: String?,
    val message: String,
) {
    override fun toString(): String = buildString {
        appendLine("Thought:")
        appendLine(thought)
        nextAction?.let {
            appendLine()
            appendLine("Next Action:")
            appendLine(it)
        }
    }
}

/**
 * Problem analysis structure
 */
@Serializable
public data class ProblemAnalysis(
    val problem: String,
    val components: List<String>,
    val challenges: List<String>,
    val approaches: List<String>,
)

/**
 * Analysis result
 */
@Serializable
public data class AnalysisResult(
    val success: Boolean,
    val analysis: ProblemAnalysis,
    val message: String,
) {
    override fun toString(): String = buildString {
        appendLine("Problem: ${analysis.problem}")
        appendLine()
        appendLine("Components:")
        analysis.components.forEach { appendLine("  - $it") }
        if (analysis.challenges.isNotEmpty()) {
            appendLine()
            appendLine("Challenges:")
            analysis.challenges.forEach { appendLine("  - $it") }
        }
        if (analysis.approaches.isNotEmpty()) {
            appendLine()
            appendLine("Approaches:")
            analysis.approaches.forEach { appendLine("  - $it") }
        }
    }
}

/**
 * A single step in a plan
 */
@Serializable
public data class PlanStep(
    val number: Int,
    val description: String,
    val complexity: String,
)

/**
 * Execution plan
 */
@Serializable
public data class ExecutionPlan(
    val goal: String,
    val steps: List<PlanStep>,
    val totalSteps: Int,
)

/**
 * Plan result
 */
@Serializable
public data class PlanResult(
    val success: Boolean,
    val plan: ExecutionPlan,
    val message: String,
) {
    override fun toString(): String = buildString {
        appendLine("Goal: ${plan.goal}")
        appendLine()
        appendLine("Steps (${plan.totalSteps}):")
        plan.steps.forEach { step ->
            appendLine("${step.number}. ${step.description} (${step.complexity})")
        }
    }
}
