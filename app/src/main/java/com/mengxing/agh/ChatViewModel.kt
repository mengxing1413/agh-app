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

    data class UserBubble(
        override val key: String,
        val text: String,
        val time: String = ""
    ) : ChatItem

    data class AgentBubble(
        override val key: String,
        val text: String,
        val done: Boolean,
        val time: String = "",
        val model: String? = null,
        val elapsedMs: Long? = null
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
    val currentSessionId: String? = null,     // 当前正在对话的会话（列表里标"当前"用）
    val sessions: List<AcpClient.SessionInfo> = emptyList(),
    val sessionsLoading: Boolean = false,
    val sessionsError: String? = null,
    val sessionsOpen: Boolean = false,
    // —— 会话管理（A 组）——
    val sessionsCursor: String = "",          // 分页游标（服务端 next）
    val sessionsHasMore: Boolean = false,
    val sessionsLoadingMore: Boolean = false,
    val sessionsQuery: String = "",           // 本地搜索词（服务端 q 实测不工作）
    val sessionsShowArchived: Boolean = false, // 是否显示已归档
    val sessionsNotice: String? = null        // 操作结果提示
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
    private var lastAgentKey: String? = null         // 本轮最近一条 agent 气泡（补耗时用）
    private var sendStartMs = 0L                     // 本轮 prompt 发出时刻
    private var everConnected = false                // 是否已成功连接过（区分首连与重连）

    init {
        acp.setListener(this)
        acp.connect(WS_URL, AUTH_USER, AUTH_PASS)
    }

    // ==================== 对外操作 ====================

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        // 收尾上一轮还没完成的"正在思考"占位（连发两条场景），避免它永远转圈
        streamingAgentKey?.let { k ->
            _state.update { s ->
                s.copy(items = s.items.map { item ->
                    if (item is ChatItem.AgentBubble && item.key == k && !item.done)
                        item.copy(text = item.text.ifEmpty { "（已跳过等待）" }, done = true)
                    else item
                })
            }
        }
        historyMode = false
        userEchoKey = null
        streamingAgentKey = null
        lastAgentText = ""
        lastAgentKey = null
        sendStartMs = System.currentTimeMillis()
        val key = "u" + ids.incrementAndGet()
        _state.update { it.copy(items = it.items + ChatItem.UserBubble(key, t, clock())) }
        // 立即放一个"正在思考"占位气泡：AI 首字之前也有反馈，不再干等
        val akey = "a" + ids.incrementAndGet()
        streamingAgentKey = akey
        lastAgentKey = akey
        _state.update { it.copy(items = it.items + ChatItem.AgentBubble(akey, "", false, clock())) }
        acp.prompt(t)
    }

    fun openSession(sid: String, cwd: String) {
        lastAgentKey = null
        historyMode = true
        streamingAgentKey = null
        userEchoKey = null
        lastAgentText = ""
        _state.update {
            it.copy(items = emptyList(), historyLoaded = false, currentSessionId = sid,
                sessionsOpen = false, statusText = "正在加载历史…")
        }
        acp.loadSession(sid, cwd)
    }

    /**
     * 新建会话：先在服务端建好（拿到新 sessionId）再清屏，失败则保持原会话不动。
     * 顺序反了会出现"屏已清空、消息却打进旧会话"的错位。
     */
    fun newSession() {
        historyMode = false
        userEchoKey = null
        streamingAgentKey = null
        lastAgentText = ""
        lastAgentKey = null
        sendStartMs = 0L
        _state.update {
            it.copy(sessionsOpen = false, statusText = "正在新建会话…", inputEnabled = false)
        }
        acp.newSession(".", object : AcpClient.RespCb {
            override fun onResult(result: org.json.JSONObject) {
                _state.update {
                    it.copy(items = emptyList(), historyLoaded = false,
                        statusText = "新会话", inputEnabled = true,
                        currentSessionId = acp.getSessionId())
                }
            }

            override fun onError(msg: String?) {
                _state.update {
                    it.copy(statusText = "新会话创建失败：" + (msg ?: ""),
                        inputEnabled = acp.getSessionId() != null)
                }
            }
        })
    }

    fun openSessions() {
        _state.update { it.copy(sessionsOpen = true) }
        refreshSessions()
    }

    fun closeSessions() {
        _state.update { it.copy(sessionsOpen = false, sessionsNotice = null) }
    }

    fun refreshSessions() {
        _state.update {
            it.copy(sessionsLoading = true, sessionsError = null,
                sessionsCursor = "", sessionsHasMore = false, sessionsLoadingMore = false)
        }
        acp.listSessions(null, object : AcpClient.SessionsCb {
            override fun onResult(sessions: MutableList<AcpClient.SessionInfo>, next: String?) {
                _state.update {
                    it.copy(sessions = sessions.toList(), sessionsLoading = false,
                        sessionsCursor = next ?: "", sessionsHasMore = !next.isNullOrEmpty())
                }
            }

            override fun onError(msg: String?) {
                _state.update { it.copy(sessionsLoading = false, sessionsError = msg ?: "加载失败") }
            }
        })
    }

    /** 拉取下一页（分页）。 */
    fun loadMoreSessions() {
        val s = _state.value
        if (s.sessionsCursor.isEmpty() || s.sessionsLoadingMore) return
        _state.update { it.copy(sessionsLoadingMore = true) }
        acp.listSessions(s.sessionsCursor, object : AcpClient.SessionsCb {
            override fun onResult(sessions: MutableList<AcpClient.SessionInfo>, next: String?) {
                _state.update {
                    it.copy(sessions = it.sessions + sessions, sessionsLoadingMore = false,
                        sessionsCursor = next ?: "", sessionsHasMore = !next.isNullOrEmpty())
                }
            }

            override fun onError(msg: String?) {
                _state.update { it.copy(sessionsLoadingMore = false, sessionsNotice = msg ?: "加载失败") }
            }
        })
    }

    fun setSessionsQuery(q: String) { _state.update { it.copy(sessionsQuery = q) } }

    fun toggleSessionsArchived() {
        _state.update { it.copy(sessionsShowArchived = !it.sessionsShowArchived) }
    }

    fun clearSessionsNotice() { _state.update { it.copy(sessionsNotice = null) } }

    /** 重命名会话（成功后刷新列表）。 */
    fun renameSession(sid: String, title: String) {
        val t = title.trim().take(256)   // 服务端 session/rename 上限 256
        if (t.isEmpty()) { _state.update { it.copy(sessionsNotice = "标题不能为空") }; return }
        acp.renameSession(sid, t, object : AcpClient.RespCb {
            override fun onResult(result: org.json.JSONObject) { refreshSessions() }
            override fun onError(msg: String?) {
                _state.update { it.copy(sessionsNotice = msg ?: "重命名失败") }
            }
        })
    }

    /** 归档 / 取消归档。 */
    fun archiveSession(sid: String, archived: Boolean) {
        acp.archiveSession(sid, archived, object : AcpClient.RespCb {
            override fun onResult(result: org.json.JSONObject) {
                _state.update { it.copy(sessionsNotice = if (archived) "已归档" else "已取消归档") }
                refreshSessions()
            }

            override fun onError(msg: String?) {
                _state.update { it.copy(sessionsNotice = msg ?: "操作失败") }
            }
        })
    }

    /** 分叉：从会话末尾（lastSeq）分出一个新会话。 */
    fun forkSession(s: AcpClient.SessionInfo) {
        if (s.lastSeq < 1) {
            _state.update { it.copy(sessionsNotice = "会话还没有内容，无法分叉") }
            return
        }
        acp.forkSession(s.sessionId, s.lastSeq, object : AcpClient.RespCb {
            override fun onResult(result: org.json.JSONObject) {
                _state.update { it.copy(sessionsNotice = "已分叉出新会话") }
                refreshSessions()
            }

            override fun onError(msg: String?) {
                _state.update { it.copy(sessionsNotice = msg ?: "分叉失败") }
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

    private fun clock(): String =
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(System.currentTimeMillis()))

    override fun onCleared() {
        acp.close()
        super.onCleared()
    }

    // ==================== AcpClient.Listener ====================

    override fun onConnected() {
        // 重连（之前连过且已有会话）时不再新建会话——否则会静默把当前会话换掉，
        // 造成"屏上是旧对话、消息却打进新会话"的错位，还会在服务端堆一串空会话。
        val reconnected = everConnected && acp.getSessionId() != null
        everConnected = true
        if (reconnected) {
            _state.update {
                it.copy(conn = ConnState.CONNECTED, statusText = "已重新连接", inputEnabled = true)
            }
            return
        }
        _state.update {
            it.copy(conn = ConnState.CONNECTED, statusText = "已连接", inputEnabled = false)
        }
        // 首次连接：建一个全新会话，成功后再开输入
        acp.newSession(".", object : AcpClient.RespCb {
            override fun onResult(result: org.json.JSONObject) {
                _state.update {
                    it.copy(items = emptyList(), statusText = "新会话", inputEnabled = true,
                        currentSessionId = acp.getSessionId())
                }
            }

            override fun onError(msg: String?) {
                _state.update {
                    it.copy(statusText = "会话初始化失败：" + (msg ?: ""),
                        inputEnabled = acp.getSessionId() != null)
                }
            }
        })
    }

    override fun onMessageChunk(text: String) {
        userEchoKey = null          // 进入 agent 输出，结束历史用户聚合
        lastAgentText += text
        val k = streamingAgentKey
        if (k == null) {
            val nk = "a" + ids.incrementAndGet()
            streamingAgentKey = nk
            lastAgentKey = nk
            _state.update { it.copy(items = it.items + ChatItem.AgentBubble(nk, text, false, clock())) }
        } else {
            lastAgentKey = k
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
            lastAgentKey = k
            _state.update { s ->
                s.copy(items = s.items.map { item ->
                    if (item is ChatItem.AgentBubble && item.key == k) item.copy(text = text, done = true)
                    else item
                })
            }
            streamingAgentKey = null
        } else {
            val nk = "a" + ids.incrementAndGet()
            lastAgentKey = nk
            _state.update { it.copy(items = it.items + ChatItem.AgentBubble(nk, text, true, clock())) }
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
        _state.update {
            it.copy(historyLoaded = true, statusText = "已连接 · 历史会话",
                currentSessionId = sessionId)
        }
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
        val k = streamingAgentKey ?: lastAgentKey
        val elapsed = if (sendStartMs > 0L) System.currentTimeMillis() - sendStartMs else 0L
        val model = acp.modelName
        if (k != null) {
            _state.update { s ->
                s.copy(items = s.items.map { item ->
                    if (item is ChatItem.AgentBubble && item.key == k)
                        item.copy(
                            done = true,
                            text = if (item.text.isEmpty()) "（本轮没有文本回复）" else item.text,
                            model = item.model ?: model,
                            elapsedMs = if (elapsed > 0L) elapsed else item.elapsedMs
                        )
                    else item
                })
            }
        }
        streamingAgentKey = null
        sendStartMs = 0L
    }

    override fun onError(msg: String) {
        // 若当前有"正在思考"占位气泡，就地把它改成错误提示（否则它会一直转圈，
        // 且后续文本还会被追加进这条错误气泡）
        val k = streamingAgentKey
        if (k != null) {
            streamingAgentKey = null
            sendStartMs = 0L
            _state.update { s ->
                s.copy(items = s.items.map { item ->
                    if (item is ChatItem.AgentBubble && item.key == k)
                        item.copy(text = "❌ " + msg, done = true)
                    else item
                })
            }
            return
        }
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
