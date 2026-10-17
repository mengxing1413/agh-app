package com.mengxing.agh

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong
import com.mengxing.agh.data.AppConfig

/** 消息流里的一个条目。 */
sealed interface ChatItem {
    val key: String

    data class UserBubble(
        override val key: String,
        val text: String,
        val time: String = "",
        val attLabels: List<String> = emptyList(),   // E 组：附件标签（文件名等）
        val attImages: List<String> = emptyList()    // E 组：图片 base64（渲染缩略图）
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
    val sessionModel: String? = null,         // 当前会话模型（气泡 meta 兜底显示）
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
    val sessionsNotice: String? = null,       // 操作结果提示
    val sessionStats: Map<String, Pair<Long, Int>> = emptyMap(), // E组本地增强：(最后活跃ms, 真实消息条数)
    // —— 状态条（B 组）：模型 / 用量 / 权限 ——
    val models: List<AcpClient.ModelInfo> = emptyList(),   // 可选模型（apis.list）
    val usage: AcpClient.UsageInfo? = null,                // 当前会话用量快照
    val budgetUsed: Int = -1,                              // 预算：已用点数（-1 = 未取到）
    val budgetCap: Int = -1,                               // 预算：上限点数
    val yolo: Boolean = false,                             // 完全权限（免逐次审批）
    val settingsNotice: String? = null,                    // 模型/权限切换失败提示
    // —— 工作区（C 组）——
    val workspaces: List<AcpClient.Workspace> = emptyList(),
    val workspacesOpen: Boolean = false,
    val activeCwd: String = "",
    val workspaceNotice: String? = null
)

/**
 * 聊天状态与逻辑中枢：桥接 AcpClient（Java 通信层），向 Compose UI 暴露不可变状态。
 *
 * 修复：用户消息重复显示——实时发送时本地已立即上屏，服务端回显的 user_message_chunk
 * 只在【历史加载回放】模式（historyMode=true）下才接收，二者不再打架。
 */
class ChatViewModel(app: Application) : AndroidViewModel(app), AcpClient.Listener {

    companion object {
        private const val PREFS = "yuno_agh"
        private const val KEY_SESSION_ID = "last_session_id"
        private const val KEY_SESSION_CWD = "last_session_cwd"
    }

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val acp = AcpClient()
    private var nano: NanobotClient? = null      // Nanobot 后端（陆吾）
    private var isNanobot = false
    private val ids = AtomicLong(0)
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private var streamingAgentKey: String? = null   // 正在流式输出的 agent 气泡
    private var userEchoKey: String? = null          // 历史回放中聚合的用户气泡
    private var userEchoTurnId: String? = null       // 该用户气泡所属 turn（同 turn 的分片才聚合）
    private var historyMode = false                  // 是否处于历史加载回放
    private var lastAgentText = ""
    private var lastAgentKey: String? = null         // 本轮最近一条 agent 气泡（补耗时用）
    private var sendStartMs = 0L                     // 本轮 prompt 发出时刻
    private var everConnected = false                // 是否已成功连接过（区分首连与重连）
    private var connConfigId: String? = null         // 当前连接用的配置 id（检测设置页变更）
    private var selectedCwd: String? = null          // 用户选定的工作区（新会话 cwd）

    // —— 会话本地增强（E 组）———————————————————————————
    // AGH 协议只给「创建时间 + 事件数」，不给「最后活跃时间 + 真实对话条数」，
    // 但网页版靠服务端排序能定位到"最后对话"。这里在手机本地补记这两个口径：
    // 发消息时记一笔，列表显示就越用越准；没记录的老会话走创建时间/事件数兜底。
    private val statsPrefs = app.getSharedPreferences("yuno_session_stats", Context.MODE_PRIVATE)

    /** 发消息时记一笔：更新最后活跃时间 + 真实消息数 +1。 */
    private fun bumpSessionStat(sid: String?) {
        if (sid.isNullOrEmpty()) return
        val raw = statsPrefs.getString(sid, null)
        val old = raw?.split("|") ?: emptyList()
        val count = (old.getOrNull(1)?.toIntOrNull() ?: 0) + 1
        statsPrefs.edit()
            .putString(sid, System.currentTimeMillis().toString() + "|" + count)
            .apply()
    }

    /** 读会话统计：(最后活跃时间 ms, 真实消息条数)；没记录返回 null。 */
    private fun sessionStat(sid: String): Pair<Long, Int>? {
        val raw = statsPrefs.getString(sid, null) ?: return null
        val p = raw.split("|")
        val ts = p.getOrNull(0)?.toLongOrNull() ?: return null
        return ts to (p.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    /** 批量读出列表里这些会话的统计，供 UI 显示。 */
    private fun loadStats(ids: List<String>): Map<String, Pair<Long, Int>> =
        ids.mapNotNull { id -> sessionStat(id)?.let { id to it } }.toMap()

    init {
        acp.setListener(this)
        val p = AppConfig.activeProfile(app)
        connConfigId = p.id
        // 后端类型分流：Nanobot 的协议与 AGH/ACP 完全不同，走独立适配器。
        // 判定：type=Nanobot，或地址里带 /luwu/ 、:8765（陆吾的 nanobot 端口）。
        isNanobot = p.type.equals("Nanobot", true) ||
            p.name.contains("nanobot", true) || p.name.contains("陆吾") ||
            p.url.contains("/luwu/", true) || p.url.contains(":8765") || p.url.contains("47653")
        if (isNanobot) {
            val n = NanobotClient(p.url, p.pass)   // Nanobot 的 token 填在密码框
            n.setListener(object : NanobotClient.Listener {
                override fun onConnected(chatId: String) {
                    _state.update {
                        it.copy(statusText = "已连接 · 陆吾", inputEnabled = true, historyLoaded = true)
                    }
                }
                override fun onReply(text: String, done: Boolean) {
                    if (text.isNotEmpty()) onMessageChunk(text)
                    if (done) onPromptDone("end_turn")
                }
                override fun onError(msg: String) { this@ChatViewModel.onError(msg) }
            })
            nano = n
            n.connect()
        } else {
            acp.connect(p.url, p.user, p.pass)
        }
    }

    // ==================== 对外操作 ====================

    fun send(
        text: String,
        attLabels: List<String> = emptyList(),
        attImages: List<String> = emptyList(),
        attachments: List<org.json.JSONObject>? = null
    ) {
        historyMode = false   // 用户主动发送 = 退出历史回放模式（否则服务端回显会造成重复上屏）
        val t = text.trim()
        if (t.isEmpty() && attachments.isNullOrEmpty()) return
        if (isNanobot) {
            // Nanobot 后端：本地立即上屏 + 纯文本发送（协议只吃文本，附件暂不支持）
            val nk = "u" + ids.incrementAndGet()
            _state.update {
                it.copy(items = it.items + ChatItem.UserBubble(nk, t, time = clock(), attLabels = attLabels, attImages = attImages))
            }
            sendStartMs = System.currentTimeMillis()
            streamingAgentKey = null
            lastAgentText = ""
            nano?.send(t)
            return
        }
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
        _state.update { it.copy(items = it.items + ChatItem.UserBubble(key, t, clock(), attLabels, attImages)) }
        // 立即放一个"正在思考"占位气泡：AI 首字之前也有反馈，不再干等
        val akey = "a" + ids.incrementAndGet()
        streamingAgentKey = akey
        lastAgentKey = akey
        _state.update { it.copy(items = it.items + ChatItem.AgentBubble(akey, "", false, clock())) }
        bumpSessionStat(acp.getSessionId())   // E 组：记一笔最后活跃时间 + 真实消息数
        acp.prompt(t, attachments)
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
        startNewSession()
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
                val visible = sessions.filter { it.lastSeq > 0 }   // 过滤空会话（无消息）
                val stats = loadStats(visible.map { it.sessionId })
                _state.update {
                    it.copy(sessions = visible, sessionStats = stats, sessionsLoading = false,
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
                val visible = sessions.filter { it.lastSeq > 0 }
                val fresh = loadStats(visible.map { s -> s.sessionId })
                _state.update {
                    it.copy(sessions = it.sessions + visible, sessionStats = it.sessionStats + fresh,
                        sessionsLoadingMore = false,
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

    /** 删除会话：AGH 仅支持「归档」，故删除 = 归档并从列表移出；可在「显示已删除」中恢复。 */
    fun deleteSession(s: AcpClient.SessionInfo) {
        acp.archiveSession(s.sessionId, true, object : AcpClient.RespCb {
            override fun onResult(result: org.json.JSONObject) {
                _state.update {
                    it.copy(
                        sessions = it.sessions.filter { x -> x.sessionId != s.sessionId },
                        sessionsNotice = "已删除"
                    )
                }
            }

            override fun onError(msg: String?) {
                _state.update { it.copy(sessionsNotice = msg ?: "删除失败") }
            }
        })
    }

    // ==================== 工作区（C 组）====================

    fun openWorkspaces() {
        _state.update { it.copy(workspacesOpen = true) }
        refreshWorkspaces()
    }

    fun closeWorkspaces() { _state.update { it.copy(workspacesOpen = false) } }

    /** D16：添加工作区（workspace.add，向 AGH 注册新目录）。 */
    fun addWorkspace(path: String) {
        acp.addWorkspace(path, object : AcpClient.RespCb {
            override fun onResult(result: org.json.JSONObject) {
                _state.update { it.copy(workspaceNotice = "已添加工作区 ✓") }
                refreshWorkspaces()
            }

            override fun onError(msg: String?) {
                _state.update { it.copy(workspaceNotice = "添加失败：" + (msg ?: "")) }
            }
        })
    }

    fun refreshWorkspaces() {
        acp.listWorkspaces(object : AcpClient.WorkspacesCb {
            override fun onResult(workspaces: MutableList<AcpClient.Workspace>) {
                _state.update { st ->
                    st.copy(
                        workspaces = workspaces.toList(),
                        activeCwd = st.activeCwd.ifEmpty {
                            selectedCwd ?: workspaces.firstOrNull()?.path ?: ""
                        }
                    )
                }
            }

            override fun onError(msg: String?) {
                _state.update { it.copy(workspaceNotice = msg ?: "工作区加载失败") }
            }
        })
    }

    fun selectWorkspace(w: AcpClient.Workspace) {
        selectedCwd = w.path
        acp.setPreferredCwd(w.path)
        _state.update {
            it.copy(activeCwd = w.path, workspacesOpen = false,
                workspaceNotice = "已切到工作区：" + w.name)
        }
        newSession()   // 切到该目录开新会话，后续对话都落在它下面
    }

    fun clearWorkspaceNotice() { _state.update { it.copy(workspaceNotice = null) } }

    /** 从设置页返回：若当前连接配置已变更，则断开重连。 */
    fun reloadConfigIfChanged() {
        val p = AppConfig.activeProfile(getApplication())
        if (p.id == connConfigId) return
        connConfigId = p.id
        everConnected = false
        _state.update {
            it.copy(items = emptyList(), historyLoaded = false, currentSessionId = null,
                sessionModel = null, usage = null, statusText = "正在连接…",
                conn = ConnState.CONNECTING, inputEnabled = false)
        }
        acp.close()
        acp.connect(p.url, p.user, p.pass)
    }

    // ==================== 状态条：模型 / 用量 / 权限（B 组）====================

    /** 拉取可选模型列表（静默失败，不打扰对话）。 */
    fun refreshModels() {
        acp.fetchModels(object : AcpClient.ModelsCb {
            override fun onResult(models: MutableList<AcpClient.ModelInfo>) {
                _state.update { st ->
                    val list = models.toList()
                    // 目录到达后回修已持有的用量窗口（usage 可能先于 models 到、或带旧窗口）
                    val old = st.usage
                    val fixed = if (old != null) {
                        val mid = if (!old.modelId.isNullOrEmpty()) old.modelId else st.sessionModel
                        val w = list.firstOrNull { m -> m.id == mid }?.contextWindow ?: 0
                        if (w > 0) old.withWindow(w) else old
                    } else null
                    st.copy(models = list, usage = fixed ?: old)
                }
            }
            override fun onError(msg: String?) { /* 静默 */ }
        })
    }

    /** 刷新当前会话用量 + 完全权限状态（静默失败）。 */
    fun refreshUsage() {
        acp.fetchUsage(object : AcpClient.UsageCb {
            override fun onResult(u: AcpClient.UsageInfo) {
                _state.update { st ->
                    // 服务端 usage.context.window 可能是过时值（改模型规格前建的会话仍报 200K），
                    // 展示以模型目录里的权威 contextWindow 为准（如 agnes-3.0-flash=512000）。
                    val mid = if (!u.modelId.isNullOrEmpty()) u.modelId else st.sessionModel
                    val w = st.models.firstOrNull { m -> m.id == mid }?.contextWindow ?: 0
                    val fixed = if (w > 0) u.withWindow(w) else u
                    st.copy(usage = fixed, yolo = fixed.yolo,
                        sessionModel = if (!fixed.modelId.isNullOrEmpty()) fixed.modelId else st.sessionModel)
                }
            }
            override fun onError(msg: String?) { /* 静默 */ }
        })
        // 顺带拉「预算状态」（session.budget）：取不到就保持 -1，不显示
        val sid = _state.value.currentSessionId ?: ""
        if (sid.isNotEmpty()) {
            acp.fetchBudget(sid, object : AcpClient.RespCb {
                override fun onResult(result: org.json.JSONObject) {
                    val st = result.optJSONObject("state") ?: return
                    _state.update {
                        it.copy(budgetUsed = st.optInt("creditsUsed", -1),
                            budgetCap = st.optInt("creditsCap", -1))
                    }
                }
                override fun onError(msg: String?) { /* 静默 */ }
            })
        }
    }

    /** 切换会话主模型（成功后刷新用量里回显的模型名）。 */
    fun selectModel(m: AcpClient.ModelInfo) {
        acp.setModel(m.route, m.id, object : AcpClient.RespCb {
            override fun onResult(result: org.json.JSONObject) {
                _state.update { it.copy(sessionModel = m.id, settingsNotice = "已切换模型：" + m.id) }
                refreshUsage()
            }
            override fun onError(msg: String?) {
                _state.update { it.copy(settingsNotice = "切换模型失败：" + (msg ?: "")) }
            }
        })
    }

    /** 切换「完全权限」开关。 */
    fun setYolo(enabled: Boolean) {
        acp.setYolo(enabled, object : AcpClient.RespCb {
            override fun onResult(result: org.json.JSONObject) {
                _state.update { it.copy(yolo = enabled) }
                refreshUsage()
            }
            override fun onError(msg: String?) {
                _state.update { it.copy(settingsNotice = "权限切换失败：" + (msg ?: "")) }
            }
        })
    }

    fun clearSettingsNotice() { _state.update { it.copy(settingsNotice = null) } }

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
        // 先取 AGH profile 的默认模型，让气泡下的"模型"信息即时可用（拿不到则留空，不显示）
        acp.fetchProfileDefaultModel { m ->
            _state.update { st -> if (st.sessionModel == null) st.copy(sessionModel = m) else st }
        }
        refreshModels()   // 状态条的模型胶囊需要可选模型列表
        refreshWorkspaces()   // 工作区列表（顶部目录栏）
        // 会话锁定（用户规则）：永远接上「最后对话的会话」，绝不自动开新会话；
        // 手动新建只走 ➕ 按钮。仅当服务端一个会话都没有时才新建。
        val savedId = prefs.getString(KEY_SESSION_ID, null)
        val savedCwd = prefs.getString(KEY_SESSION_CWD, null)
        if (!savedId.isNullOrEmpty()) {
            historyMode = true   // 回放历史时才接收用户消息回显（与点开历史会话一致）
            _state.update { it.copy(statusText = "正在恢复上次会话…") }
            acp.loadSession(savedId, savedCwd ?: "", object : AcpClient.RespCb {
                override fun onResult(result: org.json.JSONObject) {
                    // 状态由 onSessionLoaded 落定，这里只兜底模型
                    _state.update { st -> st.copy(sessionModel = acp.getModelName() ?: st.sessionModel) }
                }

                override fun onError(msg: String?) {
                    // 上次会话已删除/失效 → 退到服务端最近的会话，绝不凭空新建
                    _state.update { it.copy(statusText = "上次会话已失效，接上最近的会话…") }
                    restoreLatestSession()
                }
            }, true)
        } else {
            restoreLatestSession()
        }
    }

    /**
     * 退化兜底：接上服务端「最近的会话」继续用；只有完全没有会话时才新建一个。
     * 这保证了"打开就是上次那个对话"，不会自己凭空开新对话。
     */
    private fun restoreLatestSession() {
        acp.listSessions(null, object : AcpClient.SessionsCb {
            override fun onResult(sessions: MutableList<AcpClient.SessionInfo>, next: String?) {
                // 未删除、且有过实际内容的会话里，取最近活跃的那个（服务端按最近优先返回）
                val target = sessions.firstOrNull { !it.archived && it.lastSeq > 0 }
                if (target == null) {
                    // 全新环境：一个会话都没有 → 这才是新建的唯一时机
                    startNewSession()
                    return
                }
                historyMode = true
                _state.update { it.copy(statusText = "正在打开上次会话…") }
                acp.loadSession(target.sessionId, target.cwd ?: "", object : AcpClient.RespCb {
                    override fun onResult(result: org.json.JSONObject) {
                        persistSession()
                        _state.update { st -> st.copy(sessionModel = acp.getModelName() ?: st.sessionModel) }
                    }

                    override fun onError(msg: String?) {
                        _state.update { it.copy(statusText = "会话打开失败：" + (msg ?: "")) }
                    }
                }, true)
            }

            override fun onError(msg: String?) {
                // 连列表都拿不到 → 退回本地记录，仍然失败就只能新建
                startNewSession()
            }
        })
    }

    /** 新建会话（启动兜底与用户点 ➕ 共用）：先建好拿到新 id 再清屏。 */
    private fun startNewSession() {
        acp.newSession(".", object : AcpClient.RespCb {
            override fun onResult(result: org.json.JSONObject) {
                persistSession()
                _state.update {
                    it.copy(items = emptyList(), historyLoaded = false,
                        statusText = "新会话", inputEnabled = true,
                        currentSessionId = acp.getSessionId())
                }
                refreshUsage()
            }

            override fun onError(msg: String?) {
                _state.update {
                    it.copy(statusText = "会话初始化失败：" + (msg ?: ""),
                        inputEnabled = acp.getSessionId() != null)
                }
            }
        })
    }

    /** 记住当前会话 id/cwd，供下次启动恢复（实现"主对话"记忆）。 */
    private fun persistSession() {
        val sid = acp.getSessionId() ?: return
        prefs.edit()
            .putString(KEY_SESSION_ID, sid)
            .putString(KEY_SESSION_CWD, acp.getDefaultCwd() ?: "")
            .apply()
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
    override fun onUserMessage(text: String, images: List<String>, turnId: String) {
        if (!historyMode) return
        // 新的用户消息 = 新回合开始：先把上一条 agent 回复收尾，
        // 否则历史回放里没有 onPromptDone，所有回复会被挤进同一个气泡。
        streamingAgentKey?.let { k ->
            _state.update { s ->
                s.copy(items = s.items.map { item ->
                    if (item is ChatItem.AgentBubble && item.key == k)
                        item.copy(done = true,
                            text = if (item.text.isEmpty()) "（本轮没有文本回复）" else item.text,
                            model = item.model ?: acp.modelName)
                    else item
                })
            }
        }
        streamingAgentKey = null
        // 同一条消息的分片共享 turnId，只有同 turn 才合并到一个气泡
        val sameTurn = userEchoKey != null && (turnId.isEmpty() || turnId == userEchoTurnId)
        if (!sameTurn) {
            val nk = "h" + ids.incrementAndGet()
            userEchoKey = nk
            userEchoTurnId = turnId
            _state.update { it.copy(items = it.items + ChatItem.UserBubble(nk, text, time = clock(), attImages = images)) }
        } else {
            _state.update { s ->
                s.copy(items = s.items.map { item ->
                    if (item is ChatItem.UserBubble && item.key == userEchoKey)
                        item.copy(text = item.text + text, attImages = item.attImages + images)
                    else item
                })
            }
        }
    }

    override fun onSessionLoaded(sessionId: String) {
        // 这里【不能】关 historyMode：session/load 的响应先返回，服务端的重放消息还在陆续到达
        // （实测 LOAD_RESULT 出现在重放流中间）。在这里关掉会导致后续历史消息（含图片、用户消息）
        // 全被丢弃、agent 回复也收不了口。改为在用户主动发送时才退出历史模式（见 send()）。
        persistSession()
        _state.update {
            // inputEnabled 必须在这里放行：首连时是 false，若不放行，
            // 走"恢复会话/打开历史会话"路径后输入框会一直禁用（表现为"打不了字"）。
            it.copy(historyLoaded = true, statusText = "已连接 · 历史会话",
                inputEnabled = true,
                currentSessionId = sessionId,
                sessionModel = acp.getModelName() ?: it.sessionModel)
        }
        refreshUsage()
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
        refreshUsage()   // 每轮结束后刷新上下文用量
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
