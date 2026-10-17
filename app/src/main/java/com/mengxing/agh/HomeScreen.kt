package com.mengxing.agh

import androidx.compose.foundation.background
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mengxing.agh.ui.theme.YunoAppTheme
import com.mengxing.agh.ui.theme.YunoGreen

/** 一个 Agent 后端的展示信息。 */
data class AgentProfile(
    val id: String,
    val name: String,
    val type: String,        // "AGH" / "Nanobot" / "Hermes" / "Pi" / "OpenClaw"
    val status: String,      // "online" / "connecting" / "offline"
    val model: String,       // 当前模型，如 "agnes-3.0-flash"
    val lastActive: String,  // 相对时间："22:50" / "昨天" / "3 天前"
    val emoji: String        // 头像用 emoji，如 "🐹" "🐱"
)

/** 一条最近会话的展示信息。 */
data class RecentSession(
    val id: String,
    val agentId: String,
    val agentEmoji: String,
    val title: String,
    val time: String,
    val messageCount: Int
)

/** 渐变卡片上的深绿端点色（豆沙绿向深绿过渡）。 */
private val BrandGreenDeep = Color(0xFF5C7A49)

/** 状态点颜色：在线。 */
private val StatusOnline = Color(0xFF4CAF50)

/** 状态点颜色：连接中。 */
private val StatusConnecting = Color(0xFFFFB300)

/** 竖卡固定宽度。 */
private val AgentCardWidth = 130.dp

/** 竖卡固定高度（保证横滑区整齐对齐）。 */
private val AgentCardHeight = 140.dp

/**
 * 多 Agent 后端首页。
 *
 * 展示当前 Agent 大卡片、可横滑切换的 Agent 列表、最近会话与底部操作栏。
 *
 * @param agents 全部 Agent 后端
 * @param recent 最近会话列表
 * @param currentAgentId 当前（最近使用）的 Agent id，用于大卡片与横滑区高亮
 * @param onAgentClick 点击横滑区某个 Agent 卡
 * @param onAddAgent 点击「添加 Agent」卡
 * @param onSessionClick 点击某条最近会话；「继续上次对话」会优先复用当前 Agent 的最近一条会话
 * @param onNewSession 点击底部「新建会话」；当前 Agent 没有历史会话时「继续上次对话」也走这里
 * @param onSettings 点击右上角或底部的设置图标
 */
@Composable
fun HomeScreen(
    agents: List<AgentProfile>,
    recent: List<RecentSession>,
    currentAgentId: String,
    onAgentClick: (AgentProfile) -> Unit,
    onAddAgent: () -> Unit,
    onSessionClick: (RecentSession) -> Unit,
    onNewSession: () -> Unit,
    onSettings: () -> Unit
) {
    val currentAgent = agents.firstOrNull { it.id == currentAgentId } ?: agents.firstOrNull()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            HomeBottomBar(onNewSession = onNewSession, onSettings = onSettings)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. 顶部标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "我的 Agent",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "设置",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // 2. 当前 Agent 大卡片
            if (currentAgent != null) {
                CurrentAgentCard(
                    agent = currentAgent,
                    onContinue = {
                        val lastOfAgent = recent.firstOrNull { it.agentId == currentAgent.id }
                        if (lastOfAgent != null) onSessionClick(lastOfAgent) else onNewSession()
                    }
                )
            }

            // 3. 「我的 Agent」横滑区
            Text(
                text = "我的 Agent",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(agents, key = { it.id }) { agent ->
                    AgentMiniCard(
                        agent = agent,
                        selected = agent.id == currentAgentId,
                        onClick = { onAgentClick(agent) }
                    )
                }
                item(key = "add-agent") {
                    AddAgentCard(onClick = onAddAgent)
                }
            }

            // 4. 「最近会话」区
            Text(
                text = "最近会话",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            RecentSessionList(recent = recent, onSessionClick = onSessionClick)

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/** 当前 Agent 大卡片：豆沙绿渐变、白字、右下角「继续上次对话」。 */
@Composable
private fun CurrentAgentCard(
    agent: AgentProfile,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(YunoGreen, BrandGreenDeep)
                )
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // emoji 头像
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = agent.emoji, fontSize = 24.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = agent.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TypeChip(
                        text = agent.type,
                        container = Color.White.copy(alpha = 0.22f),
                        content = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = agent.model,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 状态点 + 状态文字
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(status = agent.status)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusLabel(agent.status),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
            Text(
                text = " · 最近活跃 ${agent.lastActive}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
        }

        // 右下角「继续上次对话」
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = onContinue,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF3F5A33)
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "继续上次对话",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** 横滑区里的单个 Agent 竖卡；选中项带豆沙绿描边。 */
@Composable
private fun AgentMiniCard(
    agent: AgentProfile,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(AgentCardWidth)
            .height(AgentCardHeight)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(2.dp, YunoGreen)
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(YunoGreen.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = agent.emoji, fontSize = 20.sp)
            }
            Text(
                text = agent.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            TypeChip(
                text = agent.type,
                container = YunoGreen.copy(alpha = 0.12f),
                content = YunoGreen
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StatusDot(status = agent.status, size = 7.dp)
                Text(
                    text = agent.lastActive,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 横滑区末尾的「添加 Agent」虚线卡。 */
@Composable
private fun AddAgentCard(onClick: () -> Unit) {
    val borderColor = MaterialTheme.colorScheme.outline
    Column(
        modifier = Modifier
            .width(AgentCardWidth)
            .height(AgentCardHeight)
            .dashedBorder(
                color = borderColor,
                strokeWidth = 2.dp,
                corner = 16.dp
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "+",
            fontSize = 28.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "添加 Agent",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

/** 「最近会话」列表：用普通 Column 循环渲染，避免嵌套滚动冲突。 */
@Composable
private fun RecentSessionList(
    recent: List<RecentSession>,
    onSessionClick: (RecentSession) -> Unit
) {
    // 默认最多展示 5 条，点「查看全部」展开/收起
    var showAll by remember { mutableStateOf(false) }
    val visible = if (showAll) recent else recent.take(5)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        visible.forEach { session ->
            RecentRow(session = session, onClick = { onSessionClick(session) })
        }
        if (recent.size > 5) {
            TextButton(
                onClick = { showAll = !showAll },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (showAll) "收起 ↑" else "查看全部 →",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** 单行最近会话：emoji 图标 + 标题（最多两行）+ 消息条数徽标 + 右侧时间。 */
@Composable
private fun RecentRow(
    session: RecentSession,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    MaterialTheme.colorScheme.secondaryContainer,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = session.agentEmoji, fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        RoundedCornerShape(50.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${session.messageCount} 条消息",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = session.time,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.End
        )
    }
}

/** 底部固定操作栏：全宽「新建会话」主按钮 + 设置图标。 */
@Composable
private fun HomeBottomBar(
    onNewSession: () -> Unit,
    onSettings: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onNewSession,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = YunoGreen,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "新建会话",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = onSettings,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** 小圆点状态指示。 */
@Composable
private fun StatusDot(status: String, size: Dp = 8.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(statusColor(status), CircleShape)
    )
}

/** 状态点颜色：online=绿 / connecting=琥珀 / offline=outline。 */
@Composable
private fun statusColor(status: String): Color = when (status) {
    "online" -> StatusOnline
    "connecting" -> StatusConnecting
    else -> MaterialTheme.colorScheme.outline
}

/** 状态文字：在线 / 连接中 / 未连接 / 离线。 */
private fun statusLabel(status: String): String = when (status) {
    "online" -> "在线"
    "connecting" -> "连接中"
    "idle" -> "未连接"
    else -> "离线"
}

/** 类型小标签（胶囊形）。 */
@Composable
private fun TypeChip(
    text: String,
    container: Color,
    content: Color
) {
    Box(
        modifier = Modifier
            .background(container, RoundedCornerShape(50.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = content,
            maxLines = 1
        )
    }
}

/** 自绘虚线圆角边框。 */
private fun Modifier.dashedBorder(
    color: Color,
    strokeWidth: Dp = 2.dp,
    corner: Dp = 16.dp,
    dash: Dp = 10.dp,
    gap: Dp = 6.dp
): Modifier = this.drawBehind {
    drawRoundRect(
        brush = SolidColor(color),
        cornerRadius = CornerRadius(corner.toPx(), corner.toPx()),
        style = Stroke(
            width = strokeWidth.toPx(),
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(dash.toPx(), gap.toPx()),
                0f
            )
        )
    )
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    YunoAppTheme {
        HomeScreen(
            agents = listOf(
                AgentProfile(
                    id = "agh-1",
                    name = "小仓",
                    type = "AGH",
                    status = "online",
                    model = "agnes-3.0-flash",
                    lastActive = "22:50",
                    emoji = "🐹"
                ),
                AgentProfile(
                    id = "nano-1",
                    name = "喵仔",
                    type = "Nanobot",
                    status = "connecting",
                    model = "claude-sonnet-4",
                    lastActive = "昨天",
                    emoji = "🐱"
                ),
                AgentProfile(
                    id = "hermes-1",
                    name = "信使",
                    type = "Hermes",
                    status = "offline",
                    model = "gpt-4o-mini",
                    lastActive = "3 天前",
                    emoji = "🦉"
                )
            ),
            recent = listOf(
                RecentSession(
                    id = "s-1",
                    agentId = "agh-1",
                    agentEmoji = "🐹",
                    title = "帮我梳理 Compose 状态管理的几种方案",
                    time = "22:50",
                    messageCount = 18
                ),
                RecentSession(
                    id = "s-2",
                    agentId = "nano-1",
                    agentEmoji = "🐱",
                    title = "Android 云编译流水线排查记录",
                    time = "昨天",
                    messageCount = 6
                ),
                RecentSession(
                    id = "s-3",
                    agentId = "hermes-1",
                    agentEmoji = "🦉",
                    title = "周报素材整理与摘要生成",
                    time = "3 天前",
                    messageCount = 32
                )
            ),
            currentAgentId = "agh-1",
            onAgentClick = {},
            onAddAgent = {},
            onSessionClick = {},
            onNewSession = {},
            onSettings = {}
        )
    }
}
