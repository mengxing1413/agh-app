package com.mengxing.agh

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mengxing.agh.data.AppConfig
import com.mengxing.agh.ui.theme.YunoAppTheme

/**
 * 多 Agent 后端首页（App 主入口）。
 *
 * 展示所有已配置的 Agent 后端卡片 + 最近会话，点卡片进入对应的对话页。
 * 连接配置复用 AppConfig.ServerProfile（不另建存储，避免两套配置打架）。
 */
class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YunoAppTheme {
                val ctx = this
                var profiles by remember { mutableStateOf(AppConfig.loadProfiles(ctx)) }

                val agents = profiles.map { p ->
                    AgentProfile(
                        id = p.id,
                        name = p.name.ifBlank { p.url.substringAfterLast('/').ifBlank { p.url } },
                        type = p.type,
                        status = "offline",   // 连接状态由对话页刷新；首页先展示静态
                        model = "",
                        lastActive = "",
                        emoji = p.emoji
                    )
                }
                val currentId = remember(profiles) {
                    runCatching { AppConfig.activeProfile(ctx).id }.getOrDefault(agents.firstOrNull()?.id ?: "")
                }

                HomeScreen(
                    agents = agents,
                    recent = emptyList(),   // 跨 Agent 会话聚合后续接入，先占位
                    currentAgentId = currentId,
                    onAgentClick = { a ->
                        AppConfig.setActiveId(ctx, a.id)
                        startActivity(Intent(ctx, ChatActivity::class.java))
                    },
                    onAddAgent = { startActivity(Intent(ctx, SettingsActivity::class.java)) },
                    onSessionClick = { s ->
                        AppConfig.setActiveId(ctx, s.agentId)
                        startActivity(Intent(ctx, ChatActivity::class.java))
                    },
                    onNewSession = { startActivity(Intent(ctx, ChatActivity::class.java)) },
                    onSettings = { startActivity(Intent(ctx, SettingsActivity::class.java)) }
                )
            }
        }
    }
}
