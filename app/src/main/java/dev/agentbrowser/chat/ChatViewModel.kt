package dev.agentbrowser.chat

import dev.agentbrowser.agent.AgentEvent
import dev.agentbrowser.agent.AgentLoop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ChatItem {
    data class User(val text: String) : ChatItem
    data class Assistant(val text: String) : ChatItem
    data class Action(val text: String) : ChatItem
    data class Notice(val text: String, val openSettings: Boolean = false) : ChatItem
}

/** Turns [AgentLoop] events into what the chat panel shows. Lives as long as [scope]. */
class ChatViewModel(private val loop: AgentLoop, private val scope: CoroutineScope) {
    private val _items = MutableStateFlow<List<ChatItem>>(emptyList())
    private val _running = MutableStateFlow(false)
    private val _pendingConfirm = MutableStateFlow<String?>(null)
    val items: StateFlow<List<ChatItem>> = _items
    val running: StateFlow<Boolean> = _running
    /** Description of the action waiting for the user's approval, or null. */
    val pendingConfirm: StateFlow<String?> = _pendingConfirm

    private var job: Job? = null

    init {
        scope.launch { loop.events.collect(::onEvent) }
    }

    private fun add(item: ChatItem) {
        _items.value = _items.value + item
    }

    private fun onEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.AssistantText -> add(ChatItem.Assistant(event.text))
            is AgentEvent.Action -> add(ChatItem.Action(event.description))
            is AgentEvent.ConfirmRequest -> _pendingConfirm.value = event.description
            is AgentEvent.Failed -> add(ChatItem.Notice(event.message, openSettings = event.isAuth))
            AgentEvent.StepLimit -> add(ChatItem.Notice("한 번에 할 수 있는 동작 수를 넘어 멈췄어요."))
            AgentEvent.Done -> Unit
        }
    }

    fun send(text: String) {
        if (_running.value || text.isBlank()) return
        add(ChatItem.User(text.trim()))
        _running.value = true
        job = scope.launch {
            try {
                loop.run(text.trim())
            } finally {
                _running.value = false
                _pendingConfirm.value = null
            }
        }
    }

    fun stop() {
        if (!_running.value) return
        job?.cancel()
        _running.value = false
        _pendingConfirm.value = null
        add(ChatItem.Notice("중지했어요."))
    }

    fun answerConfirm(approved: Boolean) {
        _pendingConfirm.value = null
        loop.confirm(approved)
    }

    fun newChat() {
        if (_running.value) {
            job?.cancel()
            _running.value = false
            _pendingConfirm.value = null
        }
        loop.reset()
        _items.value = emptyList()
    }
}
