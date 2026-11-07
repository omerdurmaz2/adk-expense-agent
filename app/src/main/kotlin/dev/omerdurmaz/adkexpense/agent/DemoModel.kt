package dev.omerdurmaz.adkexpense.agent

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import dev.omerdurmaz.adkexpense.data.Category
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * An offline stand-in for Gemini so the sample runs with no keys. It is not AI: it spots an
 * amount and a few keywords, then calls the same tools a real model would, so the tools, the
 * confirmation card, the session store and the streaming UI all run for real.
 *
 * Try "Add yesterday's taxi 18.40" or "How much did I spend on groceries?".
 */
class DemoModel(private val today: () -> LocalDate = LocalDate::now) : Model {
    override val name = "demo-scripted"

    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
        val last = request.contents.lastOrNull()?.parts.orEmpty()
        val toolResult = last.firstNotNullOfOrNull { it.functionResponse }?.response
        if (toolResult == null) {
            emit(LlmResponse(content = plan(last.mapNotNull { it.text }.joinToString(" "))))
            return@flow
        }
        val text = describe(toolResult, lastCallArgs(request))
        if (stream) {
            for (word in text.split(" ")) {
                delay(40) // makes the partial events visible in the UI
                emit(LlmResponse(content = say("$word "), partial = true))
            }
        }
        emit(LlmResponse(content = say(text)))
    }

    private fun plan(userText: String): Content {
        val words = userText.lowercase().split(Regex("""[^\p{L}\d]+""")).toSet()
        val amount = AMOUNT.find(userText)?.value?.replace(',', '.')?.toDouble()
        val keyword = KEYWORDS.firstOrNull { (word, _) -> word in words }
        val wantsWrite = WRITE_WORDS.any { it in words }
        return when {
            wantsWrite && amount != null -> {
                val yesterday = "yesterday" in words || "dün" in words
                callTool(
                    "add_expense",
                    mapOf(
                        "amount" to amount,
                        "category" to (keyword?.second ?: Category.OTHER).name,
                        "date" to (if (yesterday) today().minusDays(1) else today()).toString(),
                        "note" to (keyword?.first?.replaceFirstChar(Char::uppercase) ?: "Expense"),
                    ),
                )
            }
            "add" in words || "ekle" in words ->
                say("How much was it? For example: \"Add yesterday's taxi 18.40\".")
            else -> {
                val days = if ("week" in words || "hafta" in words) 6L else 29L
                callTool(
                    "search_expenses",
                    buildMap {
                        put("from", today().minusDays(days).toString())
                        put("to", today().toString())
                        keyword?.let { put("category", it.second.name) }
                    },
                )
            }
        }
    }

    // ADK wraps whatever a tool returns as {"result": ...}; callbacks and ADK's own errors
    // (unknown tool, bad enum, rejected confirmation) arrive unwrapped as {"error": ...}.
    private fun describe(response: Map<String, Any?>, args: Map<String, Any?>): String {
        val result = response["result"] as? Map<*, *> ?: response
        return when {
            "saved" in result -> "Saved: ${result["saved"]}."
            "total" in result -> {
                val what = args["category"]?.toString() ?: "everything"
                val count = result["count"]
                val latest = (result["expenses"] as? List<*>)?.firstOrNull()
                "From ${args["from"]} to ${args["to"]} you spent ${result["total"]} on $what " +
                    "($count ${if (count == 1) "expense" else "expenses"})." +
                    (latest?.let { " Latest: $it." } ?: "")
            }
            "error" in result -> "That didn't work: ${result["error"]}."
            else -> "Done."
        }
    }

    private fun lastCallArgs(request: LlmRequest): Map<String, Any?> = request.contents
        .flatMap { it.parts }
        .lastOrNull { it.functionCall != null }
        ?.functionCall?.args.orEmpty()

    private fun say(text: String) = Content.fromText(Role.MODEL, text)

    private fun callTool(name: String, args: Map<String, Any?>) = Content(
        role = Role.MODEL,
        parts = listOf(Part(functionCall = FunctionCall(name = name, args = args))),
    )

    private companion object {
        val AMOUNT = Regex("""\d+(?:[.,]\d{1,2})?""")
        val WRITE_WORDS = setOf("add", "spent", "paid", "record", "ekle", "harcadım", "ödedim")
        val KEYWORDS = listOf(
            "taxi" to Category.TRANSPORT,
            "taksi" to Category.TRANSPORT,
            "metro" to Category.TRANSPORT,
            "bus" to Category.TRANSPORT,
            "groceries" to Category.GROCERIES,
            "migros" to Category.GROCERIES,
            "market" to Category.GROCERIES,
            "lunch" to Category.EATING_OUT,
            "dinner" to Category.EATING_OUT,
            "coffee" to Category.EATING_OUT,
            "pizza" to Category.EATING_OUT,
            "restaurant" to Category.EATING_OUT,
            "rent" to Category.RENT,
            "kira" to Category.RENT,
        )
    }
}
