package com.mengxing.agh

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mengxing.agh.data.AppConfig
import com.mengxing.agh.ui.theme.YunoAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
                var statuses by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

                // 后台探活：端口通即在线（不跑后端协议，通用且快）
                LaunchedEffect(profiles) {
                    if (profiles.isEmpty()) return@LaunchedEffect
                    statuses = emptyMap()
                    statuses = withContext(Dispatchers.IO) {
                        profiles.associate { p -> p.id to if (tcpProbe(p.url)) "online" else "offline" }
                    }
                }

                // 从设置页/对话页返回时必须重新读一遍配置——
                // 否则在设置里新增的 Agent 不会出现在首页（只在启动时加载了一次）。
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            profiles = AppConfig.loadProfiles(ctx)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                val agents = profiles.map { p ->
                    AgentProfile(
                        id = p.id,
                        name = p.name.ifBlank { p.url.substringAfterLast('/').ifBlank { p.url } },
                        type = p.type,
                        status = statuses[p.id] ?: "connecting",   // 真实探活结果（探测中显示"连接中"）
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
