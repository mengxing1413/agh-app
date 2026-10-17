package com.mengxing.agh

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 可连接的后端平台（不同类型协议与地址规则都不同）。 */
data class Platform(
    val type: String,        // AGH / Nanobot / Hermes / Pi / OpenClaw
    val title: String,       // 展示名（AGH 用全称 Agnes Harness）
    val emoji: String,
    val hint: String,        // 地址格式提示
    val needUser: Boolean = false,
    val passLabel: String = "密码"
)

val PLATFORMS = listOf(
    Platform("AGH", "Agnes Harness", "🐹", "wss://主机:端口/__agh_ws", needUser = true, passLabel = "密码"),
    Platform("Nanobot", "Nanobot", "🐱", "ws://主机:端口/", passLabel = "Token"),
    Platform("Hermes", "Hermes", "👩", "ws://主机:端口", needUser = true, passLabel = "密码"),
    Platform("Pi", "Pi", "🟨", "ws://主机:端口", needUser = true, passLabel = "密码"),
    Platform("OpenClaw", "OpenClaw", "🦀", "ws://主机:端口", needUser = true, passLabel = "Token")
)

/** 新增连接第一步：选后端平台（对齐 Clawket 的交互，避免选错模板填错路径）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformPickerScreen(onBack: () -> Unit, onPick: (Platform) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("连接你的 Agent") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            Text("选择后端", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "选好后自动带出对应的地址格式，只需填主机和凭据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            PLATFORMS.chunked(2).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { p ->
                        Card(
                            modifier = Modifier.weight(1f).clickable { onPick(p) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(p.emoji, fontSize = 26.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    p.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    p.hint,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
