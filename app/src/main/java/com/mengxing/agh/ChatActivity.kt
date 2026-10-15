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
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mengxing.agh.ui.MarkdownText
import com.mengxing.agh.ui.theme.YunoAppTheme

/** YunoAgent 原生对话页（Compose 版）。 */
class ChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YunoAppTheme {
                val vm: ChatViewModel = viewModel()
                ChatScreen(vm)
            }
        }
    }
}

@Composable
fun ChatScreen(vm: ChatViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(state.items) {
        if (state.items.isNotEmpty()) {
            listState.animateScrollToItem(state.items.size - 1)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            YunoTopBar(
                state = state,
                onNewSession = { vm.newSession() },
                onSessions = { vm.openSessions() },
                onWorkbench = { context.startActivity(Intent(context, MainActivity::class.java)) },
                onSettings = { context.startActivity(Intent(context, SettingsActivity::class.java)) }
            )
        },
        bottomBar = {
            InputBar(
                value = input,
                onValueChange = { input = it },
                enabled = state.inputEnabled,
                onSend = { vm.send(input); input = "" }
            )
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
            onFork = { s -> vm.forkSession(s) },
            onClearNotice = { vm.clearSessionsNotice() }
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
            Text(
                item.text,
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = cs.onPrimary,
                style = MaterialTheme.typography.bodyLarge
            )
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
    onSend: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val canSend = enabled && value.isNotBlank()
    Surface(color = cs.background) {
        Row(
            Modifier.fillMaxWidth().imePadding()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cs.surface,
                border = BorderStroke(1.dp, cs.outline),
                modifier = Modifier.weight(1f)
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
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
        containerColor = cs.surface
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
                        if (state.sessionsShowArchived) "隐藏归档" else "显示归档",
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
                        state.sessions.isNotEmpty() -> "没有非归档会话，可点「显示归档」查看"
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
                            onPick = { onPick(s) },
                            onRename = { renameText = s.title; renaming = s },
                            onArchiveToggle = { onArchive(s.sessionId, !s.archived) },
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
    onPick: () -> Unit,
    onRename: () -> Unit,
    onArchiveToggle: () -> Unit,
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
                    append(formatTime(s.createdAt))
                    if (s.lastSeq > 0) append("  ·  ").append(s.lastSeq).append(" 条消息")
                    if (s.archived) append("  ·  已归档")
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
                DropdownMenuItem(
                    text = { Text(if (s.archived) "取消归档" else "归档") },
                    onClick = { menu = false; onArchiveToggle() }
                )
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

private fun formatTime(iso: String): String {
    if (iso.isEmpty()) return ""
    return try {
        if (iso.matches(Regex("\\d+"))) {
            val ms = iso.toLong()
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(ms))
        } else {
            var s = iso.replace("T", " ").replace("Z", "")
            if (s.length >= 16) s = s.substring(0, 16)
            s
        }
    } catch (e: Exception) {
        iso
    }
}
