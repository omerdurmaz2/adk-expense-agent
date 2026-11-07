package dev.omerdurmaz.adkexpense.koog

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.serialization.typeToken
import dev.omerdurmaz.adkexpense.data.Category
import dev.omerdurmaz.adkexpense.data.ExpenseRepository
import java.math.RoundingMode
import java.time.LocalDate
import kotlinx.serialization.Serializable

// The same add_expense tool written for JetBrains Koog 1.3.0, for comparison with
// FinanceTools. It lives in src/test so Koog never ships in the APK.

class AddExpenseTool(
    private val repo: ExpenseRepository,
    private val confirm: suspend (Args) -> Boolean, // shows the card, waits for a tap
) : Tool<AddExpenseTool.Args, String>(
    argsType = typeToken<Args>(),
    resultType = typeToken<String>(),
    name = "add_expense",
    description = "Saves one expense after the user confirms it.",
) {
    @Serializable
    data class Args(
        @property:LLMDescription("Amount, e.g. 12.50") val amount: Double,
        @property:LLMDescription("Expense category") val category: Category,
        @property:LLMDescription("Day of the expense, YYYY-MM-DD") val date: String,
        @property:LLMDescription("Short note") val note: String,
    )

    override suspend fun execute(args: Args): String {
        val day = runCatching { LocalDate.parse(args.date) }.getOrNull()
            ?: return "error: date must be YYYY-MM-DD"
        if (!confirm(args)) return "The user rejected this expense."
        val cents = args.amount.toBigDecimal()
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
        return "saved #" + repo.add(cents, args.category, day, args.note.take(80)).id
    }
}

fun koogFinanceAgent(executor: PromptExecutor, model: LLModel, tool: AddExpenseTool) =
    AIAgent(
        promptExecutor = executor,
        llmModel = model,
        systemPrompt = "You help the user record spending with add_expense.",
        toolRegistry = ToolRegistry { tool(tool) },
        maxIterations = 10,
    )
