package com.mengxing.agh

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mengxing.agh.ui.MarkdownText
import com.mengxing.agh.ui.theme.YunoAppTheme
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mengxing.agh.data.AppConfig
import com.mengxing.agh.data.ModelFavs

/** YunoAgent 原生对话页（Compose 版）。 */
class ChatActivity : ComponentActivity() {

    /** 主题配置快照（配色/皮肤/字号）。从设置页回来若变了 → 自动重建换肤，不用退出 App。 */
    private var uiSnap: String? = null

    /** 主题配置 + 常用模型勾选 的快照：从设置页回来若变了就自动重建（换肤 / 更新下拉列表）。 */
    private fun uiConfig(): String {
        val f = ModelFavs.load(this)
        return AppConfig.theme(this) + "|" + AppConfig.skin(this) + "|" +
            AppConfig.fontScale(this) + "|" + f.size + "#" + f.hashCode()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uiSnap = uiConfig()
        setContent {
            YunoAppTheme {
                val vm: ChatViewModel = viewModel()
                ChatScreen(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val now = uiConfig()
        if (uiSnap != null && uiSnap != now) {
            uiSnap = now
            recreate()   // 配置变了：重建本页换肤（会话由「锁定最后会话」逻辑自动恢复）
            return
        }
        uiSnap = now
    }
}

@Composable
fun ChatScreen(vm: ChatViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var usageOpen by remember { mutableStateOf(false) }
    // E 组：待发附件（选好先显示在输入框上方，可移除）
    var pendingAtt by remember { mutableStateOf<List<PendingAtt>>(emptyList()) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) pendingAtt = pendingAtt + loadAtt(context, uri, true)
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) pendingAtt = pendingAtt + loadAtt(context, uri, false)
    }

    LaunchedEffect(state.items) {
        if (state.items.isNotEmpty()) {
            listState.animateScrollToItem(state.items.size - 1)
        }
    }

    // 模型/权限切换成败提示
    LaunchedEffect(state.settingsNotice) {
        state.settingsNotice?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            vm.clearSettingsNotice()
        }
    }

    // 工作区切换提示
    LaunchedEffect(state.workspaceNotice) {
        state.workspaceNotice?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            vm.clearWorkspaceNotice()
        }
    }

    // 从设置页返回时，若切换了连接配置则自动重连
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) vm.reloadConfigIfChanged()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                YunoTopBar(
                    state = state,
                    onNewSession = { vm.newSession() },
                    onSessions = { vm.openSessions() },
                    onWorkbench = { context.startActivity(Intent(context, MainActivity::class.java)) },
                    onSettings = { context.startActivity(Intent(context, SettingsActivity::class.java)) }
                )
                WorkspaceBar(state.activeCwd) { vm.openWorkspaces() }
            }
        },
        bottomBar = {
            Column {
                StatusStrip(
                    state = state,
                    onRequestModels = { vm.refreshModels() },
                    onPickModel = { m -> vm.selectModel(m) },
                    onOpenUsage = { vm.refreshUsage(); usageOpen = true },
                    onPickYolo = { en -> vm.setYolo(en) }
                )
                InputBar(
                    value = input,
                    onValueChange = { input = it },
                    enabled = state.inputEnabled,
                    pendingAtt = pendingAtt,
                    onAttachImage = { pickImage.launch("image/*") },
                    onAttachFile = { pickFile.launch("*/*") },
                    onRemoveAtt = { i -> pendingAtt = pendingAtt.filterIndexed { idx, _ -> idx != i } },
                    onSend = {
                        val blocks = pendingAtt.map { it.toBlock() }
                        val labels = pendingAtt.filter { !it.isImage }.map { it.label }
                        val images = pendingAtt.filter { it.isImage }.map { it.base64 }
                        vm.send(input, labels, images, blocks)
                        input = ""
                        pendingAtt = emptyList()
                    }
                )
            }
        }
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            if (state.items.isEmpty()) {
                EmptyState(onPick = { vm.send(it) })
            } else {
                MessageList(
                    state = state,
                    listState = listState,
                    onRespond = { reqId, oid, note -> vm.respondPermission(reqId, oid, note) }
                )
            }
        }
    }

    if (state.sessionsOpen) {
        SessionSheet(
            state = state,
            onPick = { s -> vm.openSession(s.sessionId, s.cwd) },
            onNewSession = { vm.newSession() },
            onDismiss = { vm.closeSessions() },
            onRetry = { vm.refreshSessions() },
            onQuery = { q -> vm.setSessionsQuery(q) },
            onToggleArchived = { vm.toggleSessionsArchived() },
            onLoadMore = { vm.loadMoreSessions() },
            onRename = { sid, title -> vm.renameSession(sid, title) },
            onArchive = { sid, archived -> vm.archiveSession(sid, archived) },
            onDelete = { s -> vm.deleteSession(s) },
            onFork = { s -> vm.forkSession(s) },
            onClearNotice = { vm.clearSessionsNotice() }
        )
    }

    if (usageOpen) {
        UsageSheet(
            state = state,
            onRefresh = { vm.refreshUsage() },
            onDismiss = { usageOpen = false }
        )
    }

    if (state.workspacesOpen) {
        WorkspaceSheet(
            state = state,
            onPick = { w -> vm.selectWorkspace(w) },
            onRetry = { vm.refreshWorkspaces() },
            onAddWorkspace = { p -> vm.addWorkspace(p) },
            onDismiss = { vm.closeWorkspaces() }
        )
    }
}

// ==================== 顶栏 ====================

@Composable
private fun YunoTopBar(
    state: ChatUiState,
    onNewSession: () -> Unit,
    onSessions: () -> Unit,
    onWorkbench: () -> Unit,
    onSettings: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.background) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(cs.primary),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_hamster),
                    contentDescription = "YunoAgent",
                    modifier = Modifier.size(38.dp).clip(CircleShape)
                )
            }

            Spacer(Modifier.width(11.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    "YunoAgent",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = cs.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(connDot(state.conn)))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        state.statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onNewSession) {
                Icon(Icons.Outlined.AddComment, "新会话", tint = cs.onBackground)
            }
            IconButton(onClick = onSessions) {
                Icon(Icons.Outlined.History, "历史会话", tint = cs.onBackground)
            }
            IconButton(onClick = onWorkbench) {
                Icon(Icons.Outlined.DesktopWindows, "工作台", tint = cs.onBackground)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, "设置", tint = cs.onBackground)
            }
        }
    }
}

@Composable
private fun connDot(conn: ConnState): Color = when (conn) {
    ConnState.CONNECTED -> MaterialTheme.colorScheme.primary
    ConnState.CONNECTING, ConnState.RECONNECTING -> Color(0xFFE0A030)
    ConnState.CLOSED -> MaterialTheme.colorScheme.error
}

// ==================== 空状态 ====================

@Composable
private fun EmptyState(onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_hamster),
            contentDescription = "YunoAgent",
            modifier = Modifier.size(96.dp).clip(CircleShape)
        )

        Spacer(Modifier.height(20.dp))
        Text(
            "你好，我是 YunoAgent",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = cs.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text("你的随身 AGH 助手", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(26.dp))

        listOf("写一篇公众号文章", "总结这份报告", "查一下今天的新闻").forEach { s ->
            Surface(
                color = cs.surface,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, cs.outline),
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(50)).clickable { onPick(s) }
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = cs.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(s, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                }
            }
        }
    }
}

// ==================== 消息列表 ====================

@Composable
private fun MessageList(
    state: ChatUiState,
    listState: LazyListState,
    onRespond: (Any?, String, String) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(state.items, key = { it.key }) { item ->
            when (item) {
                is ChatItem.UserBubble -> UserBubbleRow(item)
                is ChatItem.AgentBubble -> AgentBubbleRow(item, state.sessionModel)
                is ChatItem.ToolCard -> ToolCardView(item)
                is ChatItem.PermissionCard ->
                    PermissionCardView(item) { oid, note -> onRespond(item.reqId, oid, note) }
            }
        }
    }
}

@Composable
private fun UserBubbleRow(item: ChatItem.UserBubble) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Surface(
            color = cs.primary,
            shape = RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                // E 组：附件（图片缩略图 + 文件名）
                if (item.attImages.isNotEmpty() || item.attLabels.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.attImages.forEach { b64 ->
                            val bmp = remember(b64) { decodeB64Image(b64) }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "图片",
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            }
                        }
                        item.attLabels.forEach { label ->
                            Text(
                                "📎 $label",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (item.text.isNotEmpty()) Spacer(Modifier.height(6.dp))
                }
                if (item.text.isNotEmpty()) {
                    Text(
                        item.text,
                        color = cs.onPrimary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
        if (item.time.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                item.time,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
    }
}

@Composable
private fun AgentBubbleRow(item: ChatItem.AgentBubble, fallbackModel: String?) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Image(
            painter = painterResource(R.drawable.ic_hamster),
            contentDescription = "YunoAgent",
            modifier = Modifier.size(30.dp).clip(CircleShape)
        )

        Spacer(Modifier.width(8.dp))

        Column(Modifier.widthIn(max = 300.dp)) {
            Surface(
                color = cs.surface,
                shape = RoundedCornerShape(6.dp, 18.dp, 18.dp, 18.dp),
                border = BorderStroke(1.dp, cs.outlineVariant)
            ) {
                if (item.text.isEmpty() && !item.done) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 2.dp,
                            color = cs.primary
                        )
                        Spacer(Modifier.width(9.dp))
                        Text("正在思考…", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                } else {
                    // done 但无文本（异常流）兜底，避免"空气泡还在转圈"的观感
                    val shown = if (item.text.isEmpty()) "（无内容）" else item.text
                    MarkdownText(shown, cs.onSurface, Modifier.padding(horizontal = 14.dp, vertical = 11.dp))
                }
            }
            val meta = agentMeta(item, fallbackModel)
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

private fun agentMeta(item: ChatItem.AgentBubble, fallbackModel: String?): String {
    val parts = mutableListOf<String>()
    if (item.time.isNotEmpty()) parts.add(item.time)
    val model = if (!item.model.isNullOrEmpty()) item.model else fallbackModel
    if (!model.isNullOrEmpty()) parts.add(model)
    if (item.done) {
        item.elapsedMs?.let {
            parts.add("用时 " + java.lang.String.format(java.util.Locale.US, "%.1f", it / 1000.0) + " 秒")
        }
    } else {
        parts.add("生成中…")
    }
    return parts.joinToString("  ·  ")
}

// ==================== 工具卡 / 审批卡 ====================

@Composable
private fun ToolCardView(item: ChatItem.ToolCard) {
    val cs = MaterialTheme.colorScheme
    var expanded by remember(item.key) { mutableStateOf(false) }
    val hasDetail = item.rawInput.isNotEmpty() || item.rawOutput.isNotEmpty()

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Spacer(Modifier.width(38.dp))
        Surface(
            color = cs.surface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, cs.outline),
            modifier = Modifier.widthIn(max = 300.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(enabled = hasDetail) { expanded = !expanded }
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(toolKindIcon(item.kind), fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(statusIcon(item.status), color = statusColor(item.status), fontSize = 13.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        item.title.ifEmpty { "工具调用" },
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (hasDetail) {
                        Icon(
                            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (expanded) {
                    Spacer(Modifier.height(8.dp))
                    val sb = StringBuilder()
                    sb.append("状态：").append(item.status.ifEmpty { "—" })
                    if (item.kind.isNotEmpty()) sb.append("  ·  类型：").append(item.kind)
                    if (item.rawInput.isNotEmpty()) sb.append("\n\n参数：\n").append(item.rawInput)
                    if (item.rawOutput.isNotEmpty()) sb.append("\n\n结果：\n").append(item.rawOutput)
                    Text(
                        sb.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionCardView(item: ChatItem.PermissionCard, onRespond: (String, String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val warn = Color(0xFFE0A030)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Spacer(Modifier.width(38.dp))
        Surface(
            color = cs.surface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, warn),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "⚠️ 需要授权：" + item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface
                )
                Spacer(Modifier.height(10.dp))
                when {
                    item.resolvedNote != null -> Text(
                        "✓ " + item.resolvedNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                    item.options.isEmpty() -> TextButton(onClick = { onRespond("", "已取消") }) { Text("取消") }
                    else -> Row {
                        item.options.forEach { o ->
                            val label = optionLabel(o)
                            val allow = o.kind != null && o.kind.startsWith("allow")
                            Surface(
                                color = if (allow) cs.primary else cs.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.padding(end = 8.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onRespond(o.optionId, "已选择：" + label) }
                            ) {
                                Text(
                                    label,
                                    Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                    color = if (allow) cs.onPrimary else cs.onSurface,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== 输入栏 ====================

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    pendingAtt: List<PendingAtt> = emptyList(),
    onAttachImage: () -> Unit = {},
    onAttachFile: () -> Unit = {},
    onRemoveAtt: (Int) -> Unit = {},
    onSend: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val canSend = enabled && (value.isNotBlank() || pendingAtt.isNotEmpty())
    var attachMenu by remember { mutableStateOf(false) }

    Surface(color = cs.background) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            // 待发附件预览条（点一下可移除）
            if (pendingAtt.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pendingAtt.forEachIndexed { i, att -> AttachmentChip(att) { onRemoveAtt(i) } }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // 附件按钮：发图片 / 发文件
                Box {
                    IconButton(onClick = { attachMenu = true }) {
                        Icon(Icons.Outlined.AttachFile, "添加附件", tint = cs.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                        DropdownMenuItem(text = { Text("📷 发图片") },
                            onClick = { attachMenu = false; onAttachImage() })
                        DropdownMenuItem(text = { Text("📎 发文件") },
                            onClick = { attachMenu = false; onAttachFile() })
                    }
                }

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = cs.surface,
                    border = BorderStroke(1.dp, cs.outline),
                    modifier = Modifier.weight(1f)
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        // 输入框始终可打字：会话未就绪只禁用发送按钮，
                        // 否则状态没同步好就会出现"打不了字"的失灵。
                        enabled = true,
                        textStyle = TextStyle(color = cs.onSurface, fontSize = 15.sp),
                        cursorBrush = SolidColor(cs.primary),
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 11.dp),
                        decorationBox = { inner ->
                            Box {
                                if (value.isEmpty()) {
                                    Text(
                                        "给 YunoAgent 发消息…",
                                        color = cs.onSurfaceVariant,
                                        fontSize = 15.sp
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }

                Spacer(Modifier.width(8.dp))

                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(if (canSend) cs.primary else cs.surfaceVariant)
                        .clickable(enabled = canSend) { onSend() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        "发送",
                        tint = if (canSend) cs.onPrimary else cs.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/** 待发附件小标签（点一下移除）。 */
@Composable
private fun AttachmentChip(att: PendingAtt, onRemove: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = cs.surfaceVariant,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onRemove() }
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (att.isImage) "📷" else "📎", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(4.dp))
            Text(
                att.label,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 130.dp)
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.Close, "移除", modifier = Modifier.size(13.dp), tint = cs.onSurfaceVariant)
        }
    }
}

// ==================== 状态条（B 组：模型 / 用量 / 权限）====================

@Composable
private fun StatusStrip(
    state: ChatUiState,
    onRequestModels: () -> Unit,
    onPickModel: (AcpClient.ModelInfo) -> Unit,
    onOpenUsage: () -> Unit,
    onPickYolo: (Boolean) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.background) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModelChip(state, onRequestModels, onPickModel)
            Spacer(Modifier.width(8.dp))
            UsageChip(state.usage, onOpenUsage)
            Spacer(Modifier.weight(1f))
            PermChip(state.yolo, onPickYolo)
        }
    }
}

@Composable
private fun ChipSurface(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.surface,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, cs.outline),
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onClick() }
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun ModelChip(
    state: ChatUiState,
    onRequestModels: () -> Unit,
    onPickModel: (AcpClient.ModelInfo) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val current = state.sessionModel ?: state.usage?.modelId
    val appCtx = LocalContext.current
    // 小下拉只列「常用」（模型管理页勾选推送的）+ 当前会话模型保底置顶（没勾也能切回去）
    val favIds = remember(open) { ModelFavs.load(appCtx) }
    val visible = remember(state.models, favIds, current) {
        val favs = state.models.filter { m -> favIds.contains(m.id.lowercase()) }
        val cur = state.models.firstOrNull { m -> m.id == current }
        val head = if (cur != null && favs.none { f -> f.id == cur.id }) listOf(cur) else emptyList()
        head + favs
    }
    Box {
        ChipSurface(onClick = { onRequestModels(); open = true }) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = cs.primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                current ?: "选择模型",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (visible.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("未勾选常用模型 → 去 设置 · 模型管理 勾选", style = MaterialTheme.typography.bodySmall) },
                    onClick = { open = false }
                )
            } else {
                visible.forEach { m ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    (if (m.id == current) "✓ " else "   ") + m.id,
                                    color = if (m.id == current) cs.primary else cs.onSurface
                                )
                                if (m.contextWindow > 0) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        fmtK(m.contextWindow),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = cs.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        onClick = { open = false; onPickModel(m) }
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageChip(u: AcpClient.UsageInfo?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ChipSurface(onClick = onClick) {
        Icon(Icons.Outlined.DataUsage, null, tint = cs.primary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            if (u == null || u.ctxWindow <= 0) "用量"
            else fmtK(u.ctxTokens) + " / " + fmtK(u.ctxWindow) + " · " + pct(u.ctxTokens, u.ctxWindow),
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurface
        )
    }
}

@Composable
private fun PermChip(yolo: Boolean, onPick: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val warn = Color(0xFFE0A030)
    var open by remember { mutableStateOf(false) }
    Box {
        ChipSurface(onClick = { open = true }) {
            Icon(Icons.Outlined.Security, null, tint = if (yolo) warn else cs.primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                if (yolo) "完全权限" else "工作区内",
                style = MaterialTheme.typography.labelSmall,
                color = if (yolo) warn else cs.onSurface
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text((if (!yolo) "✓ " else "   ") + "工作区内修改（默认）") },
                onClick = { open = false; onPick(false) }
            )
            DropdownMenuItem(
                text = { Text((if (yolo) "✓ " else "   ") + "完全权限（免逐次审批）") },
                onClick = { open = false; onPick(true) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsageSheet(
    state: ChatUiState,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val u = state.usage
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = cs.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "会话 Token 统计",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onRefresh) { Text("刷新") }
            }
            Spacer(Modifier.height(6.dp))

            if (u == null) {
                Text("暂无用量数据：先在会话里发一条消息，或点「刷新」。", color = cs.onSurfaceVariant)
                return@Column
            }

            // 上下文占用进度条
            val frac = if (u.ctxWindow > 0) (u.ctxTokens.toFloat() / u.ctxWindow).coerceIn(0f, 1f) else 0f
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("上下文", style = MaterialTheme.typography.bodyMedium, color = cs.onSurface, modifier = Modifier.weight(1f))
                Text(
                    fmtK(u.ctxTokens) + " / " + fmtK(u.ctxWindow) + "  ·  " + pct(u.ctxTokens, u.ctxWindow),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(cs.surfaceVariant)) {
                if (frac > 0f) {
                    Box(Modifier.fillMaxWidth(frac).height(8.dp).clip(RoundedCornerShape(4.dp)).background(cs.primary))
                }
            }
            Spacer(Modifier.height(16.dp))

            UsageRow("累计输入", u.input)
            UsageRow("累计输出", u.output)
            UsageRow("总计", u.input + u.output)
            UsageRow("缓存读取", u.cacheRead)
            UsageRow("缓存写入", u.cacheWrite)
            UsageRow("推理", u.reasoning)
            Spacer(Modifier.height(6.dp))
            UsageText("自动压缩", if (u.autoCompact) "已开启" else "已关闭")
            UsageText("完全权限", if (u.yolo) "已开启" else "已关闭")
            // 预算状态（session.budget；服务端未返回则不显示）
            if (state.budgetUsed >= 0 && state.budgetCap >= 0)
                UsageText("预算", "${state.budgetUsed} / ${state.budgetCap} 点")
            if (!u.modelId.isNullOrEmpty()) UsageText("当前模型", u.modelId)
            if (u.costUsdMicros > 0) {
                UsageText("费用", "≈ $" + java.lang.String.format(java.util.Locale.US, "%.4f", u.costUsdMicros / 1000000.0))
            }
        }
    }
}

@Composable
private fun UsageRow(label: String, value: Int) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(fmtNum(value) + "  (" + fmtK(value) + ")", style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
    }
}

@Composable
private fun UsageText(label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
    }
}

/** 9216 → 9.2K；200000 → 200K；<1000 原样。 */
private fun fmtK(n: Int): String = when {
    n < 1000 -> n.toString()
    n < 1000000 -> java.lang.String.format(java.util.Locale.US, "%.1fK", n / 1000.0).replace(".0K", "K")
    else -> java.lang.String.format(java.util.Locale.US, "%.1fM", n / 1000000.0)
}

private fun fmtNum(n: Int): String =
    java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(n)

private fun pct(a: Int, b: Int): String =
    if (b <= 0) "—" else java.lang.String.format(java.util.Locale.US, "%.1f%%", a * 100.0 / b)

// ==================== 历史会话抽屉 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionSheet(
    state: ChatUiState,
    onPick: (AcpClient.SessionInfo) -> Unit,
    onNewSession: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onQuery: (String) -> Unit,
    onToggleArchived: () -> Unit,
    onLoadMore: () -> Unit,
    onRename: (String, String) -> Unit,
    onArchive: (String, Boolean) -> Unit,
    onDelete: (AcpClient.SessionInfo) -> Unit,
    onFork: (AcpClient.SessionInfo) -> Unit,
    onClearNotice: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var renaming by remember { mutableStateOf<AcpClient.SessionInfo?>(null) }
    var renameText by remember { mutableStateOf("") }

    // 本地过滤：搜索(title/sessionId) + 归档开关。服务端 q 实测不返回结果，故在 App 内做。
    val shown = remember(state.sessions, state.sessionsQuery, state.sessionsShowArchived) {
        state.sessions.filter { s ->
            (state.sessionsShowArchived || !s.archived) && (
                state.sessionsQuery.isBlank() ||
                    s.title.contains(state.sessionsQuery, ignoreCase = true) ||
                    s.sessionId.contains(state.sessionsQuery, ignoreCase = true)
                )
        }
    }

    // 操作结果提示 3 秒后自动消失
    val notice = state.sessionsNotice
    LaunchedEffect(notice) {
        if (notice != null) {
            kotlinx.coroutines.delay(3000)
            onClearNotice()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cs.surfaceContainer
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)
        ) {
            Text(
                "历史会话",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface
            )
            Spacer(Modifier.height(12.dp))

            Surface(
                color = cs.primary,
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50))
                    .clickable { onNewSession() }
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Outlined.AddComment, null, tint = cs.onPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("新建会话", style = MaterialTheme.typography.bodyMedium, color = cs.onPrimary)
                }
            }
            Spacer(Modifier.height(12.dp))

            // 搜索 + 归档开关
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = cs.surfaceVariant,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f)
                ) {
                    BasicTextField(
                        value = state.sessionsQuery,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface),
                        cursorBrush = SolidColor(cs.primary),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        decorationBox = { inner ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Search, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Box(Modifier.weight(1f)) {
                                    if (state.sessionsQuery.isEmpty()) {
                                        Text("搜索会话…", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    inner()
                                }
                            }
                        }
                    )
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onToggleArchived) {
                    Text(
                        if (state.sessionsShowArchived) "隐藏已删除" else "显示已删除",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.sessionsShowArchived) cs.primary else cs.onSurfaceVariant
                    )
                }
            }

            notice?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = cs.primary, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(6.dp))

            when {
                state.sessionsLoading -> Box(
                    Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = cs.primary) }

                state.sessionsError != null -> Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.sessionsError, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onRetry) { Text("重试") }
                }

                shown.isEmpty() -> Text(
                    when {
                        state.sessions.isNotEmpty() && state.sessionsQuery.isNotBlank() -> "没有匹配的会话"
                        state.sessions.isNotEmpty() -> "没有会话，可点「显示已删除」查看"
                        else -> "暂无历史会话"
                    },
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(shown, key = { it.sessionId }) { s ->
                        SessionRow(
                            s = s,
                            isCurrent = s.sessionId == state.currentSessionId,
                            stat = state.sessionStats[s.sessionId],
                            onPick = { onPick(s) },
                            onRename = { renameText = s.title; renaming = s },
                            onArchiveToggle = { onArchive(s.sessionId, !s.archived) },
                            onDelete = { onDelete(s) },
                            onFork = { onFork(s) }
                        )
                    }
                    if (state.sessionsHasMore || state.sessionsLoadingMore) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (state.sessionsLoadingMore) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = cs.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                TextButton(onClick = onLoadMore) { Text("加载更多") }
                            }
                        }
                    }
                }
            }
        }
    }

    // 重命名对话框
    renaming?.let { target ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("重命名会话") },
            text = {
                Surface(color = cs.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
                    BasicTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                        cursorBrush = SolidColor(cs.primary),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(target.sessionId, renameText)
                    renaming = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun SessionRow(
    s: AcpClient.SessionInfo,
    isCurrent: Boolean,
    stat: Pair<Long, Int>?,      // E 组本地增强：(最后活跃ms, 真实消息条数)，无记录为 null
    onPick: () -> Unit,
    onRename: () -> Unit,
    onArchiveToggle: () -> Unit,
    onDelete: () -> Unit,
    onFork: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .clickable { onPick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(cs.surfaceVariant),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.History, null, tint = cs.primary, modifier = Modifier.size(18.dp)) }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                s.title.ifEmpty { "(无标题)" },
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    if (isCurrent) append("当前会话").append("  ·  ")
                    // E 组：优先显示「最后活跃时间」（本地记录），没记录退回创建时间
                    if (stat != null && stat.first > 0) append(formatTime(stat.first.toString()))
                    else append(formatTime(s.createdAt))
                    // 真实对话条数（本地统计），没统计到退回事件数并标注为「记录」
                    if (stat != null && stat.second > 0) append("  ·  ").append(stat.second).append(" 条对话")
                    else if (s.lastSeq > 0) append("  ·  ").append(s.lastSeq).append(" 条记录")
                    if (s.archived) append("  ·  已删除")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isCurrent || s.archived) cs.primary else cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Outlined.MoreVert, "更多操作", tint = cs.onSurfaceVariant)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("重命名") }, onClick = { menu = false; onRename() })
                if (s.archived) {
                    DropdownMenuItem(text = { Text("恢复") }, onClick = { menu = false; onArchiveToggle() })
                } else {
                    DropdownMenuItem(
                        text = { Text("删除", color = cs.error) },
                        onClick = { menu = false; onDelete() }
                    )
                }
                DropdownMenuItem(text = { Text("分叉到新会话") }, onClick = { menu = false; onFork() })
            }
        }
    }
}

// ==================== 工具函数 ====================

private fun statusIcon(status: String): String = when (status) {
    "completed" -> "✓"
    "failed" -> "✗"
    "in_progress", "pending" -> "⏳"
    else -> "•"
}

@Composable
private fun statusColor(status: String): Color = when (status) {
    "completed" -> MaterialTheme.colorScheme.primary
    "failed" -> MaterialTheme.colorScheme.error
    "in_progress", "pending" -> Color(0xFFE0A030)
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun toolKindIcon(kind: String): String = when (kind) {
    "read" -> "📖"
    "edit" -> "✏️"
    "delete" -> "🗑"
    "move" -> "📦"
    "search" -> "🔍"
    "execute" -> "▶️"
    "think" -> "💭"
    "fetch" -> "🌐"
    else -> "🔧"
}

private fun optionLabel(o: AcpClient.PermOption): String {
    if (!o.name.isNullOrEmpty()) return o.name
    return when (o.kind) {
        "allow_once" -> "本次允许"
        "allow_always" -> "总是允许"
        "reject_once" -> "拒绝"
        "reject_always" -> "总是拒绝"
        else -> if (o.optionId.isEmpty()) "选择" else o.optionId
    }
}

/**
 * 时间显示：AGH 返回的 createdAt 是 ISO 8601 的 **UTC**（如 2026-10-15T13:43:47.593Z），
 * 必须先按 UTC 解析再转成手机本地时区输出，否则会显示成 8 小时前。
 */
private fun formatTime(iso: String): String {
    if (iso.isEmpty()) return ""
    return try {
        if (iso.matches(Regex("\\d+"))) {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(iso.toLong()))
        } else {
            // ISO 8601（可带毫秒/时区后缀）→ 本地时区
            val norm = iso.replace("Z", "+0000").replace(Regex("(\\.\\d{3})\\d*"), "$1")
            val parse = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", java.util.Locale.US)
                .parse(norm)
                ?: java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.US)
                    .parse(norm.replace(Regex("\\.\\d{3}\\+"), "+"))
                ?: return iso
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(parse)
        }
    } catch (e: Exception) {
        iso
    }
}

// ==================== 附件（E 组）====================
// AGH 的 ContentBlock 支持 image/file，都是 base64 + mimeType：
//   {"type":"image","data":"<base64>","mimeType":"image/jpeg"}
//   {"type":"file","data":"<base64>","mimeType":"application/pdf","name":"报告.pdf"}

/** 待发附件。 */
private data class PendingAtt(
    val label: String,
    val mimeType: String,
    val base64: String,
    val isImage: Boolean
) {
    /** 组装成 AGH 的 ContentBlock。 */
    fun toBlock(): org.json.JSONObject = org.json.JSONObject().apply {
        put("type", if (isImage) "image" else "file")
        put("data", base64)
        put("mimeType", mimeType)
        if (!isImage) put("name", label)   // 文件块必填文件名；图片块不带
    }
}

/** 从 URI 读成待发附件（base64）。图片先压缩：原图 base64 可达 4~10MB，会把 WS 连接撑断。 */
private fun loadAtt(ctx: android.content.Context, uri: android.net.Uri, isImage: Boolean): PendingAtt {
    val cr = ctx.contentResolver
    val name = queryDisplayName(cr, uri) ?: (if (isImage) "图片" else "文件")
    if (isImage) {
        val packed = compressImage(cr, uri)      // 最长边 1568px + JPEG 80 → 典型 200~500KB
        if (packed != null) return PendingAtt(name, "image/jpeg", packed, true)
    }
    val mime = cr.getType(uri) ?: (if (isImage) "image/jpeg" else "application/octet-stream")
    val bytes = try {
        cr.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
    } catch (e: Exception) {
        ByteArray(0)
    }
    val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    return PendingAtt(name, mime, b64, isImage)
}

/**
 * 压缩到最长边 1568px 的 JPEG(80) 并返回 base64；失败返回 null（走原图兜底）。
 * 背景：AGH 的 WS 对单条消息有体积上限，未压缩的照片 base64 会直接把连接撑断（发送失败：连接中断）。
 */
private fun compressImage(cr: android.content.ContentResolver, uri: android.net.Uri): String? {
    return try {
        // 1) 探边界拿原始尺寸（不分配像素内存）
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        val w = bounds.outWidth; val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null
        // 2) 采样取整（inSampleSize 只能是 2 的幂）
        var sample = 1
        while (maxOf(w, h) / sample > 1568 * 2) sample *= 2
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = cr.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        // 3) 精缩到最长边 1568
        val longest = maxOf(bmp.width, bmp.height)
        val out = if (longest > 1568) {
            val f = 1568f / longest
            val nw = (bmp.width * f).toInt().coerceAtLeast(1)
            val nh = (bmp.height * f).toInt().coerceAtLeast(1)
            val s = android.graphics.Bitmap.createScaledBitmap(bmp, nw, nh, true)
            if (s !== bmp) bmp.recycle()
            s
        } else bmp
        val baos = java.io.ByteArrayOutputStream()
        out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, baos)
        if (out !== bmp) out.recycle() else bmp.recycle()
        android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }
}

private fun queryDisplayName(cr: android.content.ContentResolver, uri: android.net.Uri): String? {
    return try {
        cr.query(uri, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) null
            else {
                val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (i < 0) null else c.getString(i)
            }
        }
    } catch (e: Exception) {
        null
    }
}

/** 把 base64 解成可显示的位图（图片缩略图用）。 */
private fun decodeB64Image(b64: String): android.graphics.Bitmap? = try {
    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
} catch (e: Exception) {
    null
}
