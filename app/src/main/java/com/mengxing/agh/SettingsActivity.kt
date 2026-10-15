package com.mengxing.agh

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mengxing.agh.ui.theme.YunoAppTheme

/** YunoAgent 原生设置页（Compose 版）。 */
class SettingsActivity : ComponentActivity() {

    companion object {
        const val PREFS = "yuno_prefs"
        const val KEY_THEME = "theme"
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YunoAppTheme {
                SettingsScreen(onClose = { finish() })
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun SettingsScreen(onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences(SettingsActivity.PREFS, Context.MODE_PRIVATE) }
    var theme by remember { mutableStateOf(prefs.getString(SettingsActivity.KEY_THEME, SettingsActivity.THEME_SYSTEM) ?: SettingsActivity.THEME_SYSTEM) }
    // 版本号动态读取，避免与 build.gradle 脱节
    val appVer = remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
        } catch (e: Exception) { "?" }
    }

    fun setTheme(t: String) {
        theme = t
        prefs.edit().putString(SettingsActivity.KEY_THEME, t).apply()
        (ctx as? ComponentActivity)?.recreate()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 32.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "设置",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = cs.onBackground
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, "关闭", tint = cs.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EntryCard("模型与账户", Modifier.weight(1f))
            EntryCard("技能", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EntryCard("插件管理", Modifier.weight(1f))
            EntryCard("MCP", Modifier.weight(1f))
        }

        GroupTitle("配色")
        OptionRow("跟随系统", "随手机深色模式自动切换（推荐）", theme == "system") { setTheme("system") }
        OptionRow("浅色", "固定浅色，不随系统变化", theme == "light") { setTheme("light") }
        OptionRow("品牌深绿", "固定深绿，不随系统变化", theme == "dark") { setTheme("dark") }

        GroupTitle("皮肤")
        OptionRow("默认", "标准视觉", true) {}

        GroupTitle("字号")
        OptionRow("标准", "", true) {}

        GroupTitle("关于")
        OptionRow("YunoAgent", "v$appVer · 你的随身 AGH", false) {}
    }
}

@Composable
private fun EntryCard(title: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cs.outline),
        modifier = modifier
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface
            )
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 22.dp, bottom = 8.dp, start = 4.dp)
    )
}

@Composable
private fun OptionRow(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = if (selected) cs.primaryContainer else cs.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) cs.primary else cs.outline
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp)).clickable { onClick() }
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurface,
                    fontWeight = FontWeight.Medium
                )
                if (desc.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            if (selected) {
                Icon(Icons.Filled.Check, null, tint = cs.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
