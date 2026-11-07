package dev.omerdurmaz.adkexpense.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.adk.kt.agents.LlmCallsLimitExceededException
import com.google.adk.kt.events.Event
import com.google.adk.kt.events.ToolConfirmation
import com.google.adk.kt.runners.Runner
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import dev.omerdurmaz.adkexpense.agent.AGENT_NAME
import dev.omerdurmaz.adkexpense.agent.AGENT_RUN_CONFIG
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class ChatLine(val fromUser: Boolean, val text: String)

data class PendingWrite(
    val confirmCallIds: List<String>,
    val drafts: List<Map<String, Any?>>,
)

data class ChatUiState(
    val lines: List<ChatLine> = emptyList(),
    val streaming: String = "",
    val pending: PendingWrite? = null,
    val busy: Boolean = false,
    val failed: Boolean = false,
)

class FinanceChatViewModel(
    private val runner: Runner,
    private val userId: String,
    private val sessionId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    private val seenCalls = mutableMapOf<String, FunctionCall>()

    init {
        // Rebuild the transcript from the session store after process death.
        viewModelScope.launch {
            val key = SessionKey(runner.appName, userId, sessionId)
            val saved = runner.sessionService.getSession(key)
            val lines = saved?.events.orEmpty()
                .filter { it.contentText().isNotBlank() }
                .map { ChatLine(it.author == Role.USER, it.contentText()) }
            _state.update { it.copy(lines = lines + it.lines) }
        }
    }

    fun send(text: String) {
        _state.update { it.copy(lines = it.lines + ChatLine(true, text)) }
        launchRun(listOf(Content.fromText(Role.USER, text)))
    }

    fun answer(approved: Boolean) {
        val pending = _state.value.pending ?: return
        _state.update { it.copy(pending = null) }
        // One FunctionResponse per paused call, sent one after another.
        val replies = pending.confirmCallIds.map { id ->
            val response = FunctionResponse(
                name = FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME,
                id = id,
                response = mapOf(ToolConfirmation.CONFIRMED_KEY to approved),
            )
            Content(role = Role.USER, parts = listOf(Part(functionResponse = response)))
        }
        launchRun(replies)
    }

    private fun launchRun(messages: List<Content>) = viewModelScope.launch {
        _state.update { it.copy(busy = true, failed = false) }
        try {
            withTimeout(60.seconds) {
                for (message in messages) {
                    runner.runAsync(
                        userId,
                        sessionId,
                        newMessage = message,
                        runConfig = AGENT_RUN_CONFIG,
                    ).collect(::onEvent)
                }
            }
        } catch (e: TimeoutCancellationException) {
            _state.update { it.copy(failed = true) }
        } catch (e: LlmCallsLimitExceededException) {
            _state.update { it.copy(failed = true) }
        } catch (e: CancellationException) {
            throw e // the screen went away; don't swallow cancellation
        } catch (e: Exception) {
            // Network, quota or App Check errors from a cloud model end up here.
            _state.update { it.copy(failed = true) }
        } finally {
            _state.update { it.copy(busy = false, streaming = "") }
        }
    }

    private fun onEvent(event: Event) {
        if (event.author != AGENT_NAME) return
        if (event.partial) {
            _state.update { it.copy(streaming = it.streaming + event.contentText()) }
            return
        }
        val text = event.contentText()
        if (text.isNotBlank()) {
            _state.update {
                it.copy(lines = it.lines + ChatLine(false, text), streaming = "")
            }
        }
        event.functionCalls().forEach { call -> call.id?.let { seenCalls[it] = call } }
        val asks = event.functionCalls()
            .filter { it.name == FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME }
            .mapNotNull { it.id }
        if (asks.isNotEmpty()) {
            val drafts = event.actions.requestedToolConfirmations.keys
                .mapNotNull { seenCalls[it]?.args }
            _state.update { it.copy(pending = PendingWrite(asks, drafts)) }
        }
    }
}
