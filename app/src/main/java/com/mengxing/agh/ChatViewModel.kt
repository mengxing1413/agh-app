package com.mengxing.agh

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/** 消息流里的一个条目。 */
sealed interface ChatItem {
    val key: String

    data class UserBubble(override val key: String, val text: String) : ChatItem

    data class AgentBubble(
        override val key: String,
        val text: String,
        val done: Boolean
    ) : ChatItem

    data class ToolCard(
        override val key: String,
        val toolCallId: String,
        val title: String,
        val kind: String,
        val status: String,
        val rawInput: String,
        val rawOutput: String
    ) : ChatItem

    data class PermissionCard(
        override val key: String,
        val reqId: Any?,
        val title: String,
        val options: List<AcpClient.PermOption>,
        val resolvedNote: String?
    ) : ChatItem
}

enum class ConnState { CONNECTING, CONNECTED, RECONNECTING, CLOSED }

data class ChatUiState(
    val items: List<ChatItem> = emptyList(),
    val conn: ConnState = ConnState.CONNECTING,
    val statusText: String = "连接中…",
    val historyLoaded: Boolean = false,
    val inputEnabled: Boolean = false,
    val sessions: List<AcpClient.SessionInfo> = emptyList(),
    val sessionsLoading: Boolean = false,
    val sessionsError: String? = null,
    val sessionsOpen: Boolean = false
)

/**
 * 聊天状态与逻辑中枢：桥接 AcpClient（Java 通信层），向 Compose UI 暴露不可变状态。
 *
 * 修复：用户消息重复显示——实时发送时本地已立即上屏，服务端回显的 user_message_chunk
 * 只在【历史加载回放】模式（historyMode=true）下才接收，二者不再打架。
 */
class ChatViewModel : ViewModel(), AcpClient.Listener {

    companion object {
        const val WS_URL =
            "wss://i-69df90b938a3bc3d5392c8bb-1.tail6125f3.ts.net:8443/__agh_ws"
        const val AUTH_USER = "agh"
        const val AUTH_PASS = "11ad3c6798ca7e19"
    }

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val acp = AcpClient()
    private val ids = AtomicLong(0)

    private var streamingAgentKey: String? = null   // 正在流式输出的 agent 气泡
    private var userEchoKey: String? = null          // 历史回放中聚合的用户气泡
    private var historyMode = false                  // 是否处于历史加载回放
    private var lastAgentText = ""

    init {
        acp.setListener(this)
        acp.connect(WS_URL, AUTH_USER, AUTH_PASS)
    }

    // ==================== 对外操作 ====================

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        historyMode = false
        userEchoKey = null
        streamingAgentKey = null
        lastAgentText = ""
        val key = "u" + ids.incrementAndGet()
        _state.update { it.copy(items = it.items + ChatItem.UserBubble(key, t)) }
        acp.prompt(t)
    }

    fun openSession(sid: String, cwd: String) {
        historyMode = true
        streamingAgentKey = null
        userEchoKey = null
        lastAgentText = ""
        _state.update {
            it.copy(items = emptyList(), historyLoaded = false,
                sessionsOpen = false, statusText = "正在加载历史…")
        }
        acp.loadSession(sid, cwd)
    }

    fun openSessions() {
        _state.update { it.copy(sessionsOpen = true) }
        refreshSessions()
    }

    fun closeSessions() {
        _state.update { it.copy(sessionsOpen = false) }
    }

    fun refreshSessions() {
        _state.update { it.copy(sessionsLoading = true, sessionsError = null) }
        acp.listSessions(object : AcpClient.SessionsCb {
            override fun onResult(sessions: MutableList<AcpClient.SessionInfo>, next: String?) {
                _state.update { it.copy(sessions = sessions.toList(), sessionsLoading = false) }
            }

            override fun onError(msg: String?) {
                _state.update { it.copy(sessionsLoading = false, sessionsError = msg ?: "加载失败") }
            }
        })
    }

    fun respondPermission(reqId: Any?, optionId: String, note: String) {
        acp.respondPermission(reqId, optionId)
        _state.update { s ->
            s.copy(items = s.items.map {
                if (it is ChatItem.PermissionCard && it.reqId == reqId) it.copy(resolvedNote = note) else it
            })
        }
    }

    override fun onCleared() {
        acp.close()
        super.onCleared()
    }

    // ==================== AcpClient.Listener ====================

    override fun onConnected() {
        _state.update {
            it.copy(conn = ConnState.CONNECTED, statusText = "已连接", inputEnabled = true)
        }
        acp.newSession(".")
    }

    override fun onMessageChunk(text: String) {
        userEchoKey = null          // 进入 agent 输出，结束历史用户聚合
        lastAgentText += text
        val k = streamingAgentKey
        if (k == null) {
            val nk = "a" + ids.incrementAndGet()
            streamingAgentKey = nk
            _state.update { it.copy(items = it.items + ChatItem.AgentBubble(nk, text, false)) }
        } else {
            _state.update { s ->
                s.copy(items = s.items.map { item ->
                    if (item is ChatItem.AgentBubble && item.key == k) item.copy(text = item.text + text)
                    else item
                })
            }
        }
    }

    override fun onAgentMessage(text: String) {
        val k = streamingAgentKey
        if (k != null) {
            _state.update { s ->
                s.copy(items = s.items.map { item ->
                    if (item is ChatItem.AgentBubble && item.key == k) item.copy(text = text, done = true)
                    else item
                })
            }
            streamingAgentKey = null
        } else {
            val nk = "a" + ids.incrementAndGet()
            _state.update { it.copy(items = it.items + ChatItem.AgentBubble(nk, text, true)) }
        }
        if (lastAgentText.isEmpty()) lastAgentText = text
    }

    /** ★ 修复点：只有历史回放才接收用户回显，实时发送不再重复上屏。 */
    override fun onUserMessage(text: String) {
        if (!historyMode) return
        val k = userEchoKey
        if (k == null) {
            val nk = "h" + ids.incrementAndGet()
            userEchoKey = nk
            _state.update { it.copy(items = it.items + ChatItem.UserBubble(nk, text)) }
        } else {
            _state.update { s ->
                s.copy(items = s.items.map { item ->
                    if (item is ChatItem.UserBubble && item.key == k) item.copy(text = item.text + text)
                    else item
                })
            }
        }
    }

    override fun onSessionLoaded(sessionId: String) {
        historyMode = false
        _state.update { it.copy(historyLoaded = true, statusText = "已连接 · 历史会话") }
    }

    override fun onToolCall(
        toolCallId: String, title: String, kind: String, status: String,
        rawInput: String, rawOutput: String
    ) {
        upsertTool(toolCallId, title, kind, status, rawInput, rawOutput)
    }

    override fun onToolCallUpdate(
        toolCallId: String, title: String, kind: String, status: String,
        rawInput: String, rawOutput: String
    ) {
        upsertTool(toolCallId, title, kind, status, rawInput, rawOutput)
    }

    override fun onPermissionRequest(
        reqId: Any?, title: String, options: MutableList<AcpClient.PermOption>
    ) {
        val key = "perm:" + ids.incrementAndGet()
        _state.update {
            it.copy(items = it.items + ChatItem.PermissionCard(key, reqId, title, options.toList(), null))
        }
    }

    override fun onPromptDone(stopReason: String) {
        val k = streamingAgentKey ?: return
        _state.update { s ->
            s.copy(items = s.items.map { item ->
                if (item is ChatItem.AgentBubble && item.key == k) item.copy(done = true) else item
            })
        }
        streamingAgentKey = null
    }

    override fun onError(msg: String) {
        val key = "e" + ids.incrementAndGet()
        _state.update { it.copy(items = it.items + ChatItem.AgentBubble(key, "❌ " + msg, true)) }
    }

    override fun onClosed() {
        _state.update { it.copy(conn = ConnState.CLOSED, statusText = "已断开", inputEnabled = false) }
    }

    override fun onReconnecting(msg: String) {
        _state.update { it.copy(conn = ConnState.RECONNECTING, statusText = msg) }
    }

    // ==================== 内部工具 ====================

    private fun upsertTool(
        toolCallId: String, title: String, kind: String, status: String,
        rawInput: String, rawOutput: String
    ) {
        val key = if (toolCallId.isEmpty()) "t:" + title else "tool:" + toolCallId
        _state.update { s ->
            val idx = s.items.indexOfFirst { it.key == key }
            if (idx < 0) {
                s.copy(items = s.items + ChatItem.ToolCard(
                    key, toolCallId, title, kind, status, rawInput, rawOutput))
            } else {
                val cur = s.items[idx] as ChatItem.ToolCard
                val merged = cur.copy(
                    title = title.ifEmpty { cur.title },
                    kind = kind.ifEmpty { cur.kind },
                    status = status.ifEmpty { cur.status },
                    rawInput = rawInput.ifEmpty { cur.rawInput },
                    rawOutput = rawOutput.ifEmpty { cur.rawOutput }
                )
                s.copy(items = s.items.toMutableList().also { it[idx] = merged })
            }
        }
    }
}
