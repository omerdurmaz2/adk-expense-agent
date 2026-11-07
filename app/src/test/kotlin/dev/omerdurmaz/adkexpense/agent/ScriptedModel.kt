package dev.omerdurmaz.adkexpense.agent

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Replays canned model turns in order and records every request. */
class ScriptedModel(private vararg val turns: Content) : Model {
    override val name = "scripted"
    val requests = mutableListOf<LlmRequest>()

    override fun generateContent(
        request: LlmRequest,
        stream: Boolean,
    ): Flow<LlmResponse> {
        requests += request
        val turn = turns.getOrElse(requests.size - 1) { turns.last() }
        return flowOf(LlmResponse(content = turn))
    }
}

fun say(text: String) = Content.fromText(Role.MODEL, text)

fun callTool(name: String, args: Map<String, Any?>) = Content(
    role = Role.MODEL,
    parts = listOf(Part(functionCall = FunctionCall(name = name, args = args))),
)

/** The tool result the model saw last in this request, e.g. {"error": "..."}. */
fun LlmRequest.lastToolResult(): Map<String, Any?>? =
    contents.lastOrNull()?.parts?.firstNotNullOfOrNull { it.functionResponse }?.response
