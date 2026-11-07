package dev.omerdurmaz.adkexpense.agent

import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.agents.RunConfig
import com.google.adk.kt.agents.StreamingMode
import com.google.adk.kt.callbacks.BeforeToolCallback
import com.google.adk.kt.callbacks.CallbackChoice
import com.google.adk.kt.callbacks.OnToolErrorCallback
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import java.time.LocalDate

const val AGENT_NAME = "finance_helper"
private val ALLOWED_TOOLS = setOf("search_expenses", "add_expense")

val AGENT_RUN_CONFIG = RunConfig(streamingMode = StreamingMode.SSE, maxLlmCalls = 8)

private const val PROMPT = """
You help the user understand and record their spending in this app.
- Use search_expenses for facts and totals. Never invent amounts.
- Use add_expense only when the user clearly asks to record something.
- Ask a short question if the amount, category or date is unclear.
- Answer in the user's language, in at most three sentences.
"""

fun financeAgent(model: Model, tools: FinanceTools): LlmAgent = LlmAgent(
    name = AGENT_NAME,
    model = model,
    instruction = Instruction { _ ->
        Content(parts = listOf(Part(text = PROMPT + "Today is ${LocalDate.now()}.")))
    },
    tools = tools.generatedTools(),
    maxSteps = 6,
    beforeToolCallbacks = listOf(
        BeforeToolCallback { _, tool, args ->
            if (tool.name in ALLOWED_TOOLS) {
                CallbackChoice.Continue(args)
            } else {
                CallbackChoice.Break(mapOf("error" to "tool not available"))
            }
        },
    ),
    onToolErrorCallbacks = listOf(
        OnToolErrorCallback { _, tool, _, _ ->
            val message = "${tool.name} failed; check the arguments"
            CallbackChoice.Break(mapOf("error" to message))
        },
    ),
)
