package com.mengxing.agh

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.mengxing.agh.data.AppConfig
import com.mengxing.agh.data.ModelCatalog
import com.mengxing.agh.data.ModelFavs
import com.mengxing.agh.data.ServerProfile
import com.mengxing.agh.ui.theme.YunoAppTheme
import org.json.JSONObject
import java.util.UUID

/** YunoAgent 原生设置页：连接管理 + 外观（配色/皮肤/字号）+ 关于。 */
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
                SettingsApp(onClose = { finish() })
            }
        }
    }
}

@Composable
private fun SettingsApp(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf("main") }        // main / connections / platform / edit
    var editId by remember { mutableStateOf<String?>(null) } // 正在编辑的连接 id（null=新增）
    var presetPlatform by remember { mutableStateOf<Platform?>(null) }  // 新增时选定的平台
    var profiles by remember { mutableStateOf(AppConfig.loadProfiles(ctx)) }
    var activeId by remember { mutableStateOf(AppConfig.activeId(ctx)) }

    fun reload() {
        profiles = AppConfig.loadProfiles(ctx)
        activeId = AppConfig.activeId(ctx)
    }

    when (screen) {
        "connections" -> ConnectionsScreen(
            ctx = ctx,
            profiles = profiles,
            activeId = activeId,
            onBack = { screen = "main" },
            onSelect = { id -> AppConfig.setActiveId(ctx, id); reload() },
            onAdd = { editId = null; presetPlatform = null; screen = "platform" },
            onEdit = { id -> editId = id; presetPlatform = null; screen = "edit" },
            onDelete = { id -> AppConfig.deleteProfile(ctx, id); reload() },
            onTest = { p, cb -> testConnection(p, cb) }
        )

        "platform" -> PlatformPickerScreen(
            onBack = { screen = "connections" },
            onPick = { p -> presetPlatform = p; editId = null; screen = "edit" }
        )

        "edit" -> EditConnectionScreen(
            ctx = ctx,
            original = profiles.firstOrNull { it.id == editId },
            preset = presetPlatform,
            onBack = { screen = "connections" },
            onSave = { p ->
                AppConfig.upsertProfile(ctx, p)
                if (editId == null) AppConfig.setActiveId(ctx, p.id)   // 新增即设为当前
                reload(); screen = "connections"
            }
        )

        "models" -> ModelsScreen(ctx = ctx, onBack = { screen = "main" })

        "manage" -> ManageScreen(ctx = ctx, onBack = { screen = "main" })

        else -> MainSettings(
            ctx = ctx,
            active = AppConfig.activeProfile(ctx),
            onClose = onClose,
            onManageConnections = { screen = "connections" },
            onManageModels = { screen = "models" },
            onManageAGH = { screen = "manage" }
        )
    }
}

// ==================== 主设置页 ====================

@Composable
private fun MainSettings(
    ctx: Context,
    active: ServerProfile,
    onClose: () -> Unit,
    onManageConnections: () -> Unit,
    onManageModels: () -> Unit,
    onManageAGH: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var theme by remember { mutableStateOf(AppConfig.theme(ctx)) }
    var skin by remember { mutableStateOf(AppConfig.skin(ctx)) }
    var font by remember { mutableStateOf(AppConfig.fontScale(ctx)) }
    var glassOn by remember { mutableStateOf(AppConfig.glass(ctx)) }

    // 版本号动态读取，避免与 build.gradle 脱节
    val appVer = remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
        } catch (e: Exception) { "?" }
    }

    fun recreate() { (ctx as? ComponentActivity)?.recreate() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 32.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("设置", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = cs.onBackground)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, "关闭", tint = cs.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(18.dp))

        GroupTitle("连接")
        Surface(
            color = cs.surface, shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, cs.outline),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .clickable { onManageConnections() }
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(cs.primaryContainer),
                    contentAlignment = Alignment.Center) {
                    Text("AGH", color = cs.onPrimaryContainer,
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(active.name.ifEmpty { "未命名连接" },
                        style = MaterialTheme.typography.bodyLarge, color = cs.onSurface,
                        fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text(hostOf(active.url), style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant)
                }
                Text("管理 ›", style = MaterialTheme.typography.labelMedium, color = cs.primary)
            }
        }

        GroupTitle("模型")
        Surface(
            color = cs.surface, shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, cs.outline),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .clickable { onManageModels() }
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(cs.secondaryContainer),
                    contentAlignment = Alignment.Center) {
                    Text("✦", color = cs.onSecondaryContainer,
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("模型配置",
                        style = MaterialTheme.typography.bodyLarge, color = cs.onSurface,
                        fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text("添加 / 切换模型，自动匹配上下文窗口",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Text("管理 ›", style = MaterialTheme.typography.labelMedium, color = cs.primary)
            }
        }

        GroupTitle("AGH 管理")
        Surface(
            color = cs.surface, shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, cs.outline),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .clickable { onManageAGH() }
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(cs.secondaryContainer),
                    contentAlignment = Alignment.Center) {
                    Text("⚙", color = cs.onSecondaryContainer,
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("技能 / MCP / 包管理",
                        style = MaterialTheme.typography.bodyLarge, color = cs.onSurface,
                        fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text("查看并管理 AGH 的包、MCP 服务、技能、资源",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Text("进入 ›", style = MaterialTheme.typography.labelMedium, color = cs.primary)
            }
        }

        GroupTitle("配色")
        OptionRow("跟随系统", "随手机深色模式自动切换（推荐）", theme == "system") {
            theme = "system"; AppConfig.setString(ctx, AppConfig.KEY_THEME, theme); recreate()
        }
        OptionRow("浅色", "固定浅色，不随系统变化", theme == "light") {
            theme = "light"; AppConfig.setString(ctx, AppConfig.KEY_THEME, theme); recreate()
        }
        OptionRow("深色", "固定深色，不随系统变化", theme == "dark") {
            theme = "dark"; AppConfig.setString(ctx, AppConfig.KEY_THEME, theme); recreate()
        }

        GroupTitle("皮肤")
        SkinRow(skin) { k -> skin = k; AppConfig.setString(ctx, AppConfig.KEY_SKIN, k); recreate() }

        Spacer(Modifier.height(8.dp))
        OptionSwitch("玻璃质感",
            "近不透明磨砂面板 + 按主题色铺柔光渐变壁纸（可与任意主题色组合）", glassOn) { on ->
            glassOn = on; AppConfig.setGlass(ctx, on); recreate()
        }

        GroupTitle("字号")
        OptionRow("小", "更紧凑，一屏看得更多", font == "small") {
            font = "small"; AppConfig.setString(ctx, AppConfig.KEY_FONT, font); recreate()
        }
        OptionRow("标准", "默认字号", font == "normal") {
            font = "normal"; AppConfig.setString(ctx, AppConfig.KEY_FONT, font); recreate()
        }
        OptionRow("大", "看得更清楚", font == "large") {
            font = "large"; AppConfig.setString(ctx, AppConfig.KEY_FONT, font); recreate()
        }
        OptionRow("超大", "适合视力不佳", font == "xlarge") {
            font = "xlarge"; AppConfig.setString(ctx, AppConfig.KEY_FONT, font); recreate()
        }

        GroupTitle("关于")
        OptionRow("YunoAgent", "v$appVer · 你的随身 AGH", false) {}
    }
}

private fun hostOf(url: String): String {
    return url.removePrefix("wss://").removePrefix("ws://")
        .substringBefore("/").ifEmpty { url }
}

@Composable
private fun SkinRow(selected: String, onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    // 16 款主题色（整合：原基础 5 款 + 社区/补充 11 款），每行 4 款；明暗双套，可与「玻璃质感」任意组合
    val all = listOf(
        Triple("green", "豆沙绿", YunoSkinDot(Color(0xFF7C9F69))),
        Triple("blue", "海洋蓝", YunoSkinDot(Color(0xFF4A82C4))),
        Triple("orange", "暖橙", YunoSkinDot(Color(0xFFE08A3C))),
        Triple("violet", "紫罗兰", YunoSkinDot(Color(0xFF8E6BC8))),
        Triple("slate", "石墨灰", YunoSkinDot(Color(0xFF6B7280))),
        Triple("nord", "北境极光", YunoSkinDot(Color(0xFF81A1C1))),
        Triple("dracula", "德古拉", YunoSkinDot(Color(0xFFBD93F9))),
        Triple("tokyo", "东京夜", YunoSkinDot(Color(0xFF7AA2F7))),
        Triple("catppuccin", "猫布奇诺", YunoSkinDot(Color(0xFF89B4FA))),
        Triple("everforest", "永恒森林", YunoSkinDot(Color(0xFF7FBBB3))),
        Triple("rosepine", "玫瑰松", YunoSkinDot(Color(0xFFEB6F92))),
        Triple("gruvbox", "复古木", YunoSkinDot(Color(0xFFD79921))),
        Triple("kanagawa", "神奈川", YunoSkinDot(Color(0xFF7E9CD8))),
        Triple("solarized", "日光", YunoSkinDot(Color(0xFF268BD2))),
        Triple("nightowl", "夜枭", YunoSkinDot(Color(0xFF82AAFF))),
        Triple("onedark", "单色", YunoSkinDot(Color(0xFF61AFEF)))
    )
    all.chunked(4).forEach { row ->
        SkinRowLine(row, selected, onPick)
        Spacer(Modifier.height(4.dp))
    }
    Spacer(Modifier.height(2.dp))
    Text("16 款主题色 · 明暗双套；每款都能叠加下面的「玻璃质感」",
        style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
}

@Composable
private fun SkinRowLine(
    skins: List<Triple<String, String, YunoSkinDot>>,
    selected: String,
    onPick: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for ((k, label, dot) in skins) {
            val sel = k == selected
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                    .background(if (sel) cs.primaryContainer else cs.surface)
                    .clickable { onPick(k) }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(dot.color))
                Spacer(Modifier.height(6.dp))
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = if (sel) cs.onPrimaryContainer else cs.onSurfaceVariant)
            }
        }
    }
}

private class YunoSkinDot(val color: Color)

@Composable
private fun GroupTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 22.dp, bottom = 8.dp, start = 4.dp))
}

@Composable
private fun OptionRow(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = if (selected) cs.primaryContainer else cs.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) cs.primary else cs.outline),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp)).clickable { onClick() }
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurface, fontWeight = FontWeight.Medium)
                if (desc.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            if (selected) Icon(Icons.Filled.Check, null, tint = cs.primary, modifier = Modifier.size(20.dp))
        }
    }
}

/** 开关型选项（用于「玻璃质感」这类叠加开关）。 */
@Composable
private fun OptionSwitch(title: String, desc: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (checked) cs.primary else cs.outline),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp)).clickable { onToggle(!checked) }
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurface, fontWeight = FontWeight.Medium)
                if (desc.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            Switch(checked = checked, onCheckedChange = { onToggle(it) })
        }
    }
}

// ==================== 连接管理 ====================

@Composable
private fun ConnectionsScreen(
    ctx: Context,
    profiles: List<ServerProfile>,
    activeId: String,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onTest: (ServerProfile, (Boolean, String) -> Unit) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var testingId by remember { mutableStateOf<String?>(null) }
    var testMsg by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 32.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回", tint = cs.onBackground) }
            Text("连接", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, color = cs.onBackground)
            Spacer(Modifier.weight(1f))
            Text("新增", style = MaterialTheme.typography.labelLarge, color = cs.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onAdd() }
                    .padding(horizontal = 8.dp, vertical = 6.dp))
        }

        Spacer(Modifier.height(8.dp))

        for (p in profiles) {
            val isActive = p.id == activeId
            Surface(
                color = cs.surface, shape = RoundedCornerShape(14.dp),
                border = BorderStroke(if (isActive) 1.5.dp else 1.dp,
                    if (isActive) cs.primary else cs.outline),
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(14.dp)).clickable { onSelect(p.id) }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(18.dp).clip(CircleShape)
                            .background(if (isActive) cs.primary else cs.outlineVariant),
                            contentAlignment = Alignment.Center) {
                            if (isActive) Icon(Icons.Filled.Check, null,
                                tint = cs.onPrimary, modifier = Modifier.size(13.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(p.name.ifEmpty { "未命名连接" },
                            style = MaterialTheme.typography.bodyLarge, color = cs.onSurface,
                            fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onEdit(p.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Edit, "编辑", tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onDelete(p.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Delete, "删除", tint = cs.error, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(p.url, style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant, maxLines = 2)

                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = {
                            testingId = p.id; testMsg = null
                            onTest(p) { ok, msg ->
                                testingId = null
                                testMsg = (if (ok) "✓ " else "✗ ") + p.name + "：" + msg
                            }
                        }, enabled = testingId != p.id) {
                            if (testingId == p.id) {
                                CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("测试中…")
                            } else Text("测试连接")
                        }
                    }
                }
            }
        }

        testMsg?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

// ==================== 编辑连接 ====================

@Composable
private fun EditConnectionScreen(
    ctx: Context,
    original: ServerProfile?,
    preset: Platform? = null,
    onBack: () -> Unit,
    onSave: (ServerProfile) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val isNew = original == null
    var name by remember { mutableStateOf(original?.name ?: (preset?.title ?: "")) }
    var url by remember { mutableStateOf(original?.url ?: "") }
    var user by remember { mutableStateOf(original?.user ?: "") }
    var pass by remember { mutableStateOf(original?.pass ?: "") }
    // 后端类型：nanobot 的地址与凭据规则都不同（不补 /__agh_ws、密码框填 token）
    val nanoNow = preset?.type == "Nanobot" || original?.type == "Nanobot" || looksNano(name, url)
    var testing by remember { mutableStateOf(false) }
    var testMsg by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val raw = result.contents
        if (raw.isNullOrEmpty()) return@rememberLauncherForActivityResult
        val fill = parseQrContent(raw)
        if (fill.url == null && fill.name == null && fill.user == null && fill.pass == null) {
            err = "无法识别的二维码内容"
        } else {
            err = null
            fill.name?.let { name = it }
            fill.url?.let { url = it }
            fill.user?.let { user = it }
            fill.pass?.let { pass = it }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 32.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回", tint = cs.onBackground) }
            Text(if (isNew) "新增连接" else "编辑连接",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = cs.onBackground)
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(value = name, onValueChange = { name = it },
            label = { Text("名称") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))

        OutlinedTextField(value = url, onValueChange = { url = it },
            label = { Text("服务器地址") },
            placeholder = { Text(preset?.hint ?: "wss://主机:端口/__agh_ws") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))

        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = user, onValueChange = { user = it },
                label = { Text("用户名") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = pass, onValueChange = { pass = it },
                label = { Text(preset?.passLabel ?: "密码") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    val opts = ScanOptions()
                    opts.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    opts.setPrompt("对准二维码")
                    opts.setBeepEnabled(false)
                    opts.setOrientationLocked(false)
                    scanner.launch(opts)
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.QrCodeScanner, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("扫码填入")
            }
            OutlinedButton(
                onClick = {
                    val p = ServerProfile(
                        original?.id ?: UUID.randomUUID().toString(),
                        name.ifEmpty { "未命名" }, normalizeWs(url, nanoNow), user, pass,
                        type = original?.type ?: (preset?.type ?: "AGH"),
                        emoji = original?.emoji ?: (preset?.emoji ?: "🤖"))
                    testing = true; testMsg = null
                    testConnection(p) { ok, msg -> testing = false; testMsg = (if (ok) "✓ " else "✗ ") + msg }
                },
                enabled = !testing && url.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                if (testing) {
                    CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp)); Text("测试中…")
                } else Text("测试连接")
            }
        }

        testMsg?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        err?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = cs.error)
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                if (url.isBlank()) { err = "请填写服务器地址"; return@Button }
                val p = ServerProfile(
                    original?.id ?: UUID.randomUUID().toString(),
                    name.ifEmpty { "未命名" }, normalizeWs(url, nanoNow), user, pass,
                    type = original?.type ?: (preset?.type ?: "AGH"),
                    emoji = original?.emoji ?: (preset?.emoji ?: "🤖"))
                onSave(p)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("保存") }
    }
}

// ==================== 工具函数 ====================

private data class QrFill(val name: String?, val url: String?, val user: String?, val pass: String?)

/** 解析二维码内容：支持 JSON / agnese-url / 普通地址。 */
private fun parseQrContent(raw: String): QrFill {
    val t = raw.trim()
    if (t.isEmpty()) return QrFill(null, null, null, null)

    // 1) JSON：{"name":..,"url":..,"user":..,"pass":..}
    if (t.startsWith("{")) {
        try {
            val o = JSONObject(t)
            val u = pick(o, "url", "ws", "wss", "server", "endpoint")
            return QrFill(
                pick(o, "name", "title"),
                u?.let { normalizeWs(it) },
                pick(o, "user", "username", "account"),
                pick(o, "pass", "password", "pwd", "token")
            )
        } catch (_: Exception) { }
    }

    // 2) agh://user:pass@host:port  或  agh://host:port?user=..&pass=..
    if (t.startsWith("agh://") || t.startsWith("agnes://")) {
        val rest = t.substringAfter("://")
        var user: String? = null
        var pass: String? = null
        var host = rest
        if (rest.contains("@")) {
            val cred = rest.substringBefore("@")
            host = rest.substringAfter("@")
            user = cred.substringBefore(":").ifEmpty { null }
            pass = cred.substringAfter(":", "").ifEmpty { null }
        }
        var query = ""
        if (host.contains("?")) { query = host.substringAfter("?"); host = host.substringBefore("?") }
        if (query.isNotEmpty()) {
            for (kv in query.split("&")) {
                val k = kv.substringBefore("="); val v = kv.substringAfter("=", "")
                when (k) {
                    "user", "username" -> user = v.ifEmpty { user }
                    "pass", "password" -> pass = v.ifEmpty { pass }
                }
            }
        }
        return QrFill(null, normalizeWs(host), user, pass)
    }

    // 3) 普通地址
    if (t.startsWith("ws://") || t.startsWith("wss://") ||
        t.startsWith("http://") || t.startsWith("https://")) {
        return QrFill(null, normalizeWs(t), null, null)
    }

    return QrFill(null, null, null, null)
}

private fun pick(o: JSONObject, vararg keys: String): String? {
    for (k in keys) {
        val v = o.optString(k, "")
        if (v.isNotEmpty()) return v
    }
    return null
}

/** 判断是否 nanobot 后端（协议不同，地址规则也不同）。 */
private fun looksNano(name: String, url: String): Boolean =
    name.contains("nanobot", true) || name.contains("陆吾") ||
        url.contains("/luwu/", true) || url.contains(":8765") || url.contains("47653")

/** 规范化 WebSocket 地址：http(s)→ws(s)。AGH 无路径时补 /__agh_ws；nanobot 只保证末尾一个 /。 */
private fun normalizeWs(input: String, nano: Boolean = false): String {
    var s = input.trim()
    if (s.isEmpty()) return s
    s = when {
        s.startsWith("https://") -> "wss://" + s.removePrefix("https://")
        s.startsWith("http://") -> "ws://" + s.removePrefix("http://")
        else -> s
    }
    if (!s.startsWith("ws://") && !s.startsWith("wss://")) {
        s = "wss://$s"
    }
    if (nano) {
        // nanobot 的 WebSocket 挂在根路径上，补 /__agh_ws 会握手失败（服务端回 200 而不是 101）
        return s.trimEnd('/') + "/"
    }
    val schemeEnd = s.indexOf("://") + 3
    val afterScheme = s.substring(schemeEnd)
    val hasPath = afterScheme.contains("/")
    if (!hasPath || afterScheme.endsWith("/")) {
        s = s.trimEnd('/') + "/__agh_ws"
    }
    return s
}

/** 轻量连接测试：连上即成功，8 秒超时。按后端类型分流（nanobot 不认 ACP 握手）。 */
private fun testConnection(p: ServerProfile, cb: (Boolean, String) -> Unit) {
    val isNano = p.type.equals("Nanobot", true) ||
        p.name.contains("nanobot", true) || p.name.contains("陆吾") ||
        p.url.contains("/luwu/", true) || p.url.contains(":8765") || p.url.contains("47653")
    if (isNano) {
        val n = NanobotClient(p.url, p.pass)
        var done = false
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        fun finish(ok: Boolean, msg: String) {
            if (done) return
            done = true
            try { n.close() } catch (_: Exception) { }
            handler.post { cb(ok, msg) }
        }
        n.setListener(object : NanobotClient.Listener {
            override fun onConnected(chatId: String) { finish(true, "连接成功（已建会话）") }
            override fun onReply(text: String, done2: Boolean) { }
            override fun onError(msg: String) { finish(false, msg) }
        })
        n.connect()
        handler.postDelayed({ finish(false, "连接超时（请检查地址 / token 是否完整）") }, 8000)
        return
    }
    val client = AcpClient()
    var done = false
    val handler = android.os.Handler(android.os.Looper.getMainLooper())
    fun finish(ok: Boolean, msg: String) {
        if (done) return
        done = true
        try { client.close() } catch (_: Exception) { }
        handler.post { cb(ok, msg) }
    }
    client.setListener(object : AcpClient.Listener {
        override fun onConnected() { finish(true, "连接成功") }
        override fun onError(msg: String?) { finish(false, msg ?: "失败") }
        override fun onClosed() { finish(false, "连接被关闭") }
        override fun onReconnecting(msg: String?) { finish(false, "无法连接") }
        override fun onMessageChunk(text: String?) {}
        override fun onAgentMessage(text: String?) {}
        override fun onToolCall(id: String?, ti: String?, k: String?, s: String?, i: String?, o: String?) {}
        override fun onToolCallUpdate(id: String?, ti: String?, k: String?, s: String?, i: String?, o: String?) {}
        override fun onPermissionRequest(reqId: Any?, title: String?, options: MutableList<AcpClient.PermOption>?) {}
        override fun onUserMessage(text: String?, images: MutableList<String>?, turnId: String?) {}
        override fun onSessionLoaded(sid: String?) {}
        override fun onPromptDone(stopReason: String?) {}
    })
    client.connect(p.url, p.user, p.pass)
    handler.postDelayed({ finish(false, "连接超时（请检查地址/网络）") }, 8000)
}

// ==================== 模型配置（D5 + D6）====================
// D5：通过 config.get / config.save / config.test 管理 AGH 的模型配置
// D6：加模型时用内置规格库自动填上下文窗口，避免"每个模型都标 200K"

@Composable
private fun ModelsScreen(ctx: Context, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val profile = remember { AppConfig.activeProfile(ctx) }
    val client = remember { AcpClient() }

    var connected by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("正在连接 AGH…") }
    var busy by remember { mutableStateOf(false) }

    var providerId by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var curModel by remember { mutableStateOf("") }
    var curCtx by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf("") }   // 服务端账号 id：config.save/test 必带，否则保存报 CONFIG_TEST_FAILED

    var modelId by remember { mutableStateOf("") }
    var ctxWindow by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var specNote by remember { mutableStateOf("输入模型 id，自动匹配上下文窗口") }

    // —— 模型管理：全量账号/模型 + 折叠 + 搜索 + 常用勾选 + 账号级刷新 ——
    var acctList by remember { mutableStateOf<List<org.json.JSONObject>>(emptyList()) }
    var expandedAcct by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var favs by remember { mutableStateOf(ModelFavs.load(ctx)) }
    var refreshAt by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var refreshingId by remember { mutableStateOf<String?>(null) }

    // 读取当前配置
    fun loadConfig() {
        client.configGet(object : AcpClient.RespCb {
            override fun onResult(result: JSONObject) {
                val p = result.optJSONObject("provider")
                if (p != null) {
                    providerId = p.optString("id", providerId)
                    baseUrl = p.optString("baseUrl", baseUrl)
                    curModel = p.optString("model", curModel)
                }
                // 上下文窗口：从 accounts[].models[] 里找当前模型
                var win = ""
                val arr = result.optJSONArray("accounts")
                if (arr != null) {
                    // 全量账号 + 其模型列表 → 交给「模型管理」区按 key 分组折叠展示
                    val list = (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i) }
                    acctList = list
                    if (expandedAcct == null) expandedAcct = list.firstOrNull()?.optString("accountId", "")
                    // 取第一个账号的 id：config.save/test 必带，否则保存报 CONFIG_TEST_FAILED
                    if (arr.length() > 0) {
                        val a0 = arr.optJSONObject(0)
                        if (a0 != null && a0.optString("accountId", "").isNotEmpty())
                            accountId = a0.optString("accountId", accountId)
                    }
                    outer@ for (i in 0 until arr.length()) {
                        val a = arr.optJSONObject(i) ?: continue
                        val ms = a.optJSONArray("models") ?: continue
                        for (j in 0 until ms.length()) {
                            val m = ms.optJSONObject(j) ?: continue
                            if (m.optString("id", "") == curModel) {
                                win = m.optInt("contextWindow", 0).toString()
                                break@outer
                            }
                        }
                    }
                }
                curCtx = win
                if (modelId.isEmpty() && curModel.isNotEmpty()) {
                    modelId = curModel
                    if (ctxWindow.isEmpty()) ctxWindow = win
                }
                status = if (p == null) "尚未配置模型，请在下方添加" else "已读取配置"
            }

            override fun onError(msg: String?) { status = "读取配置失败：" + AcpClient.friendlyError(msg) }
        })
    }

    // 加规格库自动填
    fun applySpec(id: String) {
        val spec = ModelCatalog.find(ctx, id)
        if (spec != null) {
            ctxWindow = spec.ctxWindow.toString()
            specNote = "✓ 已匹配规格库：${spec.name} · ${spec.ctxWindow / 1000}K" +
                (if (spec.note.isNotEmpty()) " · ${spec.note}" else "")
        } else {
            specNote = "规格库未收录该型号，请手动填上下文窗口（或联系补录）"
        }
    }

    fun save(makeDefault: Boolean) {
        if (modelId.trim().isEmpty()) { status = "请先填模型 id"; return }
        busy = true
        status = "正在保存…"
        val win = ctxWindow.trim().toIntOrNull() ?: 0
        val input = JSONObject().apply {
            put("providerId", providerId.ifEmpty { "agnes-ai" })
            // 必带账号 id：服务端保存前会自动跑连通测试，缺它就报 CONFIG_TEST_FAILED
            if (accountId.isNotEmpty()) put("accountId", accountId)
            if (baseUrl.trim().isNotEmpty()) put("baseUrl", baseUrl.trim())
            if (apiKey.trim().isNotEmpty()) put("apiKey", apiKey.trim())
            put("model", modelId.trim())
            put("label", modelId.trim())
            put("enabled", true)
            put("makeDefault", makeDefault)
            // D6：把规格库给的上下文窗口一起写进去
            if (win > 0) put("defaultSettings", JSONObject().put("contextWindow", win))
        }
        client.configSave(input, object : AcpClient.RespCb {
            override fun onResult(result: JSONObject) {
                busy = false
                // 记入本地规格库，下次自动识别
                if (win > 0) ModelCatalog.remember(ctx,
                    com.mengxing.agh.data.ModelSpec(modelId.trim(), modelId.trim(), win, 0.0, 0.0, false, "自填"))
                apiKey = ""          // 密钥不回显，用完即清
                status = if (makeDefault) "已保存并设为默认模型 ✓" else "已保存 ✓"
                loadConfig()
            }

            override fun onError(msg: String?) {
                busy = false
                status = "保存失败：" + AcpClient.friendlyError(msg)
            }
        })
    }

    fun testModel() {
        busy = true
        status = "正在测试连通…"
        val input = JSONObject().apply {
            put("providerId", providerId.ifEmpty { "agnes-ai" })
            if (baseUrl.trim().isNotEmpty()) put("baseUrl", baseUrl.trim())
            if (apiKey.trim().isNotEmpty()) put("apiKey", apiKey.trim())
            if (modelId.trim().isNotEmpty()) put("model", modelId.trim())
            if (accountId.isNotEmpty()) put("accountId", accountId)
        }
        client.configTest(input, object : AcpClient.RespCb {
            override fun onResult(result: JSONObject) {
                busy = false
                status = "✓ 测试通过"
            }

            override fun onError(msg: String?) {
                busy = false
                status = "✗ 测试失败：" + AcpClient.friendlyError(msg)
            }
        })
    }

    /** 账号级刷新：config.test 从 provider 端点拉最新模型目录（只更新展示，不写回配置）。 */
    fun refreshAccount(a: org.json.JSONObject) {
        val acctId = a.optString("accountId", "")
        if (acctId.isEmpty()) { status = "该账号缺少 accountId，无法刷新"; return }
        refreshingId = acctId
        status = "正在从 provider 拉取模型目录…"
        val input = org.json.JSONObject().apply {
            put("providerId", a.optString("providerId", providerId.ifEmpty { "agnes-ai" }))
            put("baseUrl", a.optString("baseUrl", baseUrl))
            put("accountId", acctId)
            val def = a.optString("model", "")
            if (def.isNotEmpty()) put("model", def)
        }
        client.configTest(input, object : AcpClient.RespCb {
            override fun onResult(result: org.json.JSONObject) {
                refreshingId = null
                val fresh = result.optJSONArray("models")
                if (fresh != null) {
                    acctList = acctList.map { acc ->
                        if (acc.optString("accountId", "") == acctId) {
                            val merged = org.json.JSONObject(acc.toString())
                            merged.put("models", fresh)
                            merged
                        } else acc
                    }
                    val now = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date())
                    refreshAt = refreshAt + (acctId to now)
                    status = "已刷新：$acctId · ${fresh.length()} 个模型"
                } else status = "刷新完成，但未返回模型目录"
            }

            override fun onError(msg: String?) {
                refreshingId = null
                status = "刷新失败：" + AcpClient.friendlyError(msg)
            }
        })
    }

    /** 勾选/取消常用 —— 勾过的才出现在对话页小下拉。 */
    fun toggleFav(id: String) {
        val now = ModelFavs.toggle(ctx, id)
        favs = ModelFavs.load(ctx)
        status = if (now) "已加入常用「$id」→ 对话页小下拉可见"
                 else "已移出常用「$id」"
    }

    /** 把某个账号下的某模型设为默认（写回 AGH：makeDefault=true）。 */
    fun setDefaultModel(a: org.json.JSONObject, m: org.json.JSONObject) {
        val mid = m.optString("id", "")
        if (mid.isEmpty()) return
        providerId = a.optString("providerId", providerId)
        baseUrl = a.optString("baseUrl", baseUrl)
        val accId = a.optString("accountId", "")
        if (accId.isNotEmpty()) accountId = accId
        modelId = mid
        val w = m.optInt("contextWindow", 0)
        if (w > 0) ctxWindow = w.toString()
        status = "正在把默认模型设为 $mid …"
        save(true)
    }

    DisposableEffect(Unit) {
        client.setListener(object : AcpClient.Listener {
            override fun onConnected() {
                connected = true
                status = "已连接"
                loadConfig()
            }
            override fun onError(msg: String?) { status = "连接失败：" + (msg ?: "") }
            override fun onClosed() { connected = false }
            override fun onReconnecting(msg: String?) { status = "正在重连…" }
            override fun onMessageChunk(text: String?) {}
            override fun onAgentMessage(text: String?) {}
            override fun onToolCall(id: String?, ti: String?, k: String?, s: String?, i: String?, o: String?) {}
            override fun onToolCallUpdate(id: String?, ti: String?, k: String?, s: String?, i: String?, o: String?) {}
            override fun onPermissionRequest(reqId: Any?, title: String?, options: MutableList<AcpClient.PermOption>?) {}
            override fun onUserMessage(text: String?, images: MutableList<String>?, turnId: String?) {}
            override fun onSessionLoaded(sid: String?) {}
            override fun onPromptDone(stopReason: String?) {}
        })
        client.connect(profile.url, profile.user, profile.pass)
        onDispose { try { client.close() } catch (_: Exception) {} }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 32.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "返回", tint = cs.onSurfaceVariant)
            }
            Text("模型配置", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = cs.onBackground)
        }

        Spacer(Modifier.height(8.dp))
        Text(status, style = MaterialTheme.typography.bodySmall,
            color = if (status.startsWith("✗") || status.contains("失败")) cs.error else cs.onSurfaceVariant)

        Spacer(Modifier.height(16.dp))
        GroupTitle("当前模型")
        Surface(
            color = cs.surface, shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, cs.outline),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(if (curModel.isEmpty()) "（未配置）" else curModel,
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    color = cs.onSurface)
                Spacer(Modifier.height(4.dp))
                Text("Provider：" + providerId.ifEmpty { "-" } + "  ·  上下文：" +
                    (if (curCtx.isEmpty()) "未知" else curCtx + " tokens"),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                if (baseUrl.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(baseUrl, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        GroupTitle("模型管理 · 全量（按 key 折叠）")

        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("搜索模型（跨 key，如 nemotron / kimi）") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        ModelAccountsCard(
            accounts = acctList,
            query = query.trim(),
            expandedId = expandedAcct,
            onExpand = { id -> expandedAcct = if (expandedAcct == id) null else id },
            favs = favs,
            onToggleFav = { toggleFav(it) },
            defaultModel = curModel,
            onSetDefault = { a, m -> setDefaultModel(a, m) },
            onRefresh = { a -> refreshAccount(a) },
            refreshingId = refreshingId,
            refreshAt = refreshAt
        )

        Spacer(Modifier.height(18.dp))
        GroupTitle("添加 / 切换模型")

        OutlinedTextField(
            value = modelId, onValueChange = { modelId = it; applySpec(it) },
            label = { Text("模型 id（如 agnes-3.0-flash）") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(specNote, style = MaterialTheme.typography.bodySmall,
            color = if (specNote.startsWith("✓")) cs.primary else cs.onSurfaceVariant)

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = ctxWindow, onValueChange = { ctxWindow = it },
            label = { Text("上下文窗口（token）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = apiKey, onValueChange = { apiKey = it },
            label = { Text("API Key（留空则沿用现有的）") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = baseUrl, onValueChange = { baseUrl = it },
            label = { Text("接口地址（留空则沿用现有的）") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { testModel() }, enabled = connected && !busy,
                modifier = Modifier.weight(1f)) {
                Text(if (busy) "…" else "测试连通")
            }
            Button(onClick = { save(true) }, enabled = connected && !busy,
                modifier = Modifier.weight(1f)) {
                Text("保存并设为默认")
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { save(false) }, enabled = connected && !busy,
            modifier = Modifier.fillMaxWidth()) {
            Text("仅保存（不切换默认）")
        }

        Spacer(Modifier.height(14.dp))
        Text("说明：上下文窗口由内置规格库自动填写，可手动修改；保存后会写入 AGH 配置，状态条的「已用 / 窗口」即按此计算。",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}

// ==================== 模型管理：按 key 折叠 + 全量 + 常用勾选 ====================
// 结构：一个 key = 一个账号（account），账号下挂它的模型列表（NVIDIA 这类能到 80+）。
// 默认按账号折叠，搜索时强制展开命中账号；勾选(常用) = 推送给对话页小下拉。

@Composable
private fun ModelAccountsCard(
    accounts: List<org.json.JSONObject>,
    query: String,
    expandedId: String?,
    onExpand: (String) -> Unit,
    favs: Set<String>,
    onToggleFav: (String) -> Unit,
    defaultModel: String,
    onSetDefault: (org.json.JSONObject, org.json.JSONObject) -> Unit,
    onRefresh: (org.json.JSONObject) -> Unit,
    refreshingId: String?,
    refreshAt: Map<String, String>
) {
    val cs = MaterialTheme.colorScheme
    if (accounts.isEmpty()) {
        Text("账号加载中…（连接 AGH 后自动填充：一个 key 一个账号，账号下挂它的模型）",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        return
    }
    val searching = query.isNotEmpty()

    accounts.forEach { a ->
        val acctId = a.optString("accountId", "")
        val label = a.optString("label", "").ifEmpty { a.optString("providerId", "未命名账号") }
        val provider = a.optString("providerId", "")
        val defModel = a.optString("model", "")
        val modelsArr = a.optJSONArray("models")
        val all = ArrayList<org.json.JSONObject>()
        if (modelsArr != null) {
            for (i in 0 until modelsArr.length()) {
                val mo = modelsArr.optJSONObject(i)
                if (mo != null) all.add(mo)
            }
        }
        val shown = if (searching) all.filter { mo ->
            mo.optString("id", "").contains(query, true) ||
                mo.optString("name", "").contains(query, true)
        } else all
        if (searching && shown.isEmpty()) return@forEach   // 搜索时跳过无命中的账号

        val expanded = searching || expandedId == acctId

        Surface(
            color = cs.surface, shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, cs.outline),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                .clip(RoundedCornerShape(14.dp))
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { onExpand(acctId) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("$label · $provider", style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium, color = cs.onSurface)
                        Spacer(Modifier.height(2.dp))
                        val meta = StringBuilder().append(all.size).append(" 个模型")
                        if (defModel.isNotEmpty()) meta.append(" · 默认★ ").append(defModel)
                        refreshAt[acctId]?.let { t -> meta.append(" · 刷新于 ").append(t) }
                        Text(meta.toString(), style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant)
                    }
                    TextButton(onClick = { onRefresh(a) }, enabled = refreshingId == null) {
                        Text(if (refreshingId == acctId) "刷新中…" else "刷新",
                            style = MaterialTheme.typography.labelMedium)
                    }
                    Text(if (expanded) "▾" else "▸", color = cs.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge)
                }

                if (expanded) {
                    Box(Modifier.fillMaxWidth().height(1.dp)
                        .background(cs.outline.copy(alpha = 0.4f)))
                    if (shown.isEmpty()) {
                        Text(if (searching) "搜索无命中" else "该账号下暂无模型",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                    }
                    shown.forEach { m ->
                        val mid = m.optString("id", "")
                        ModelRow(
                            m = m,
                            isDefault = mid.isNotEmpty() && mid == defModel,
                            isFav = favs.contains(mid.lowercase()),
                            onToggleFav = { onToggleFav(mid) },
                            onSetDefault = { onSetDefault(a, m) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelRow(
    m: org.json.JSONObject,
    isDefault: Boolean,
    isFav: Boolean,
    onToggleFav: () -> Unit,
    onSetDefault: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val id = m.optString("id", "")
    val win = m.optInt("contextWindow", 0)

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = isFav, onCheckedChange = { onToggleFav() })
        Column(Modifier.weight(1f)) {
            Text(id, style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isDefault) FontWeight.Bold else FontWeight.Normal,
                color = cs.onSurface)
            Text(
                (if (win > 0) "${win / 1000}K 窗口" else "窗口未知") +
                    (if (isDefault) " · 当前默认★" else if (isFav) " · 常用✓" else ""),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )
        }
        if (isDefault) {
            Text("★", color = cs.primary, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(6.dp))
        } else {
            TextButton(onClick = onSetDefault) {
                Text("设为默认", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ==================== AGH 管理（D18-D22）====================
// 技能 / MCP / 扩展 / 资源 / 包管理，对齐网页版设置页各分区。

/** 管理条目（通用解析：尽量取 id/name/title/status，取不到就兜底显示）。 */
private data class ManageItem(
    val title: String, val subtitle: String, val id: String,
    val raw: org.json.JSONObject, val desc: String = ""
)

/**
 * 管理项中文简介：AGH 服务端不给描述（只有 license/contributions 等技术字段），这里内置一份。
 * 认不出的项留空、不硬编，避免瞎编误导。
 */
private val MANAGE_DESC = mapOf(
    "@agnes/document-reader" to "文档读取：让 Agent 能读 PDF / Word / Excel 等文件",
    "@agnes/mcp-helper" to "MCP 辅助：连接与调试 MCP 服务",
    "@agnes/plugin-helper" to "插件辅助：插件安装与管理支持",
    "@agnes/skill-helper" to "技能辅助：技能加载与发现",
    "package" to "插件包内自带的技能",
    "user-agents" to "Agnes 用户级技能目录",
    "user-agnes" to "Agnes 用户技能目录（放 SKILL.md 即被识别）",
    "user-claude" to "Claude 用户技能目录",
    "user-codex" to "Codex 用户技能目录",
    "workspace-agnes" to "当前工作区（repo）下的技能目录"
)

private fun descOf(id: String): String = MANAGE_DESC[id.lowercase()] ?: ""

/** 从返回里尽量抽出条目列表（各分区字段名不一，逐个试）。 */
/** rootsOnly=true（资源 tab 专用）：只列「技能/资源的加载目录」，与技能 tab 的条目列表区分开。 */
private fun parseManageItems(result: org.json.JSONObject, rootsOnly: Boolean = false): List<ManageItem> {
    if (rootsOnly) return parseSkillRoots(result)
    val arrKeys = listOf(
        "packages", "servers", "resources", "skills", "extensions",
        "items", "entries", "list", "modules", "rows", "surfaces", "statuses"
    )
    var arr: org.json.JSONArray? = null
    for (k in arrKeys) {
        arr = result.optJSONArray(k)
        if (arr != null && arr.length() > 0) break
        if (arr != null && arr.length() == 0) arr = null      // 空数组继续找别的键
    }
    if (arr == null) {
        // items 为空时退化展示「技能加载目录」（比空列表有信息量）
        val roots = parseSkillRoots(result)
        if (roots.isNotEmpty()) return roots
        // 或是对象集合 {k: {...}}
        val obj = result.optJSONObject("items") ?: result.optJSONObject("data")
        if (obj != null) {
            val out = mutableListOf<ManageItem>()
            for (k in obj.keys()) {
                val v = obj.optJSONObject(k) ?: continue
                out.add(itemFrom(k, v))
            }
            return out
        }
        return emptyList()
    }
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        itemFrom("", o)
    }
}

private fun itemFrom(fallbackId: String, o: org.json.JSONObject): ManageItem {
    val id = o.optString("id", o.optString("name",
        o.optString("rootKey", o.optString("path", fallbackId))))
    val name = o.optString("name", o.optString("title",
        o.optString("label", o.optString("rootKey", id))))
    val status = o.optString("status", o.optString("state",
        o.optString("desired", o.optString("actual", ""))))
    val ver = o.optString("version", "")
    val trust = when (o.optBoolean("trusted", false)) {
        true -> "已信任"; false -> if (o.has("trusted")) "未信任" else ""
    }
    val sub = listOf(ver, status, trust).filter { it.isNotEmpty() }.joinToString(" · ")
    return ManageItem(name, sub, id, o, descOf(id))
}

/** 技能/资源的「加载目录」列表（skillRoots）—— 每项带中文说明。 */
private fun parseSkillRoots(result: org.json.JSONObject): List<ManageItem> {
    for (k in listOf("skillRoots", "skillRoot", "roots")) {
        val alt = result.optJSONArray(k) ?: continue
        if (alt.length() == 0) continue
        return (0 until alt.length()).mapNotNull { i ->
            val o = alt.optJSONObject(i) ?: return@mapNotNull null
            val key = o.optString("rootKey", o.optString("root", ""))
            val scope = o.optString("scope", "")
            val st = o.optString("state", "")
            val nm = o.optString("workspaceId", "")
            ManageItem(
                if (key.isEmpty()) "(目录)" else key,
                listOf(scope, st, if (nm.isEmpty()) "" else "ws:" + nm.take(8))
                    .filter { it.isNotEmpty() }.joinToString(" · "),
                key, o, descOf(key)
            )
        }
    }
    return emptyList()
}

@Composable
private fun ManageScreen(ctx: Context, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val profile = remember { AppConfig.activeProfile(ctx) }
    val client = remember { AcpClient() }

    var tab by remember { mutableStateOf("packages") }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("正在加载管理面…") }
    var items by remember { mutableStateOf<List<ManageItem>>(emptyList()) }
    var profileName by remember { mutableStateOf("") }   // 管理面 profile：POST body 必须与之一致

    fun load() {
        loading = true
        status = "加载中…"
        val cb = object : AcpClient.RespCb {
            override fun onResult(result: org.json.JSONObject) {
                // 资源 tab 只列「加载目录」，技能 tab 列技能条目 —— 两者不再重复
                items = parseManageItems(result, rootsOnly = (tab == "resources"))
                loading = false
                status = if (items.isEmpty()) {
                    when (tab) {
                        "skills" -> "还没技能：把 SKILL.md 放进任一技能目录即可"
                        "resources" -> "暂无资源目录信息"
                        else -> "暂无内容（服务端该分区为空）"
                    }
                } else "共 ${items.size} 项 · profile $profileName"
            }

            override fun onError(msg: String?) {
                loading = false
                status = "加载失败：" + AcpClient.friendlyError(msg)
                items = emptyList()
            }
        }
        // 管理动作走 HTTP admin 面（WS 侧会 CAPABILITY_DENIED）：
        // 先 GET context 拿 profile，再 POST 对应 action。
        fun ctxThen(actionPath: String) {
            client.adminGet("/admin/plugins/api/context", object : AcpClient.RespCb {
                override fun onResult(ctx: org.json.JSONObject) {
                    profileName = ctx.optString("profile", profileName)
                    val body = org.json.JSONObject().put("profile", profileName)
                    client.adminPost(actionPath, body, cb)
                }
                override fun onError(msg: String?) { cb.onError(msg) }
            })
        }
        when (tab) {
            "packages" -> ctxThen("/admin/plugins/api/list")
            "mcp" -> ctxThen("/admin/resources/api/mcp/list")
            "skills", "resources" -> ctxThen("/admin/resources/api/skills/list")
            "extensions" -> {
                // 扩展列表是 WS 方法（clientModules.list，实测可用），也要 profile
                if (profileName.isEmpty()) {
                    client.adminGet("/admin/plugins/api/context", object : AcpClient.RespCb {
                        override fun onResult(ctx: org.json.JSONObject) {
                            profileName = ctx.optString("profile", profileName)
                            client.clientModulesList(profileName, cb)
                        }
                        override fun onError(msg: String?) { cb.onError(msg) }
                    })
                } else client.clientModulesList(profileName, cb)
            }
        }
    }

    DisposableEffect(Unit) {
        client.setListener(object : AcpClient.Listener {
            override fun onConnected() { status = "已连接"; load() }
            override fun onError(msg: String?) { status = "连接失败：" + (msg ?: "") }
            override fun onClosed() {}
            override fun onReconnecting(msg: String?) {}
            override fun onMessageChunk(text: String?) {}
            override fun onAgentMessage(text: String?) {}
            override fun onToolCall(id: String?, ti: String?, k: String?, s: String?, i: String?, o: String?) {}
            override fun onToolCallUpdate(id: String?, ti: String?, k: String?, s: String?, i: String?, o: String?) {}
            override fun onPermissionRequest(reqId: Any?, title: String?, options: MutableList<AcpClient.PermOption>?) {}
            override fun onUserMessage(text: String?, images: MutableList<String>?, turnId: String?) {}
            override fun onSessionLoaded(sid: String?) {}
            override fun onPromptDone(stopReason: String?) {}
        })
        client.connect(profile.url, profile.user, profile.pass)
        onDispose { try { client.close() } catch (_: Exception) {} }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 32.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "返回", tint = cs.onSurfaceVariant)
            }
            Text("AGH 管理", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = cs.onBackground)
        }

        Spacer(Modifier.height(8.dp))
        // 分区切换
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ManageTab("包", tab == "packages") { tab = "packages"; load() }
            ManageTab("MCP", tab == "mcp") { tab = "mcp"; load() }
            ManageTab("技能", tab == "skills") { tab = "skills"; load() }
            ManageTab("资源", tab == "resources") { tab = "resources"; load() }
            ManageTab("扩展", tab == "extensions") { tab = "extensions"; load() }
        }

        Spacer(Modifier.height(8.dp))
        // 每个分区一句人话说明（回答"这些是什么"）
        Text(
            when (tab) {
                "packages" -> "AGH 已装的插件包。内置 @agnes/* 4 个是官方组件，一般不要卸载"
                "mcp" -> "MCP 服务：给 Agent 挂外部工具 / 数据源（没配则为空）"
                "skills" -> "Agent 可用的技能（SKILL.md）。为空＝还没放技能"
                "resources" -> "技能 / 资源的加载目录与状态（技能放进这些目录会被识别）"
                else -> "客户端扩展模块（界面插件）"
            },
            style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant
        )

        Spacer(Modifier.height(10.dp))
        Text(status, style = MaterialTheme.typography.bodySmall,
            color = if (status.contains("失败")) cs.error else cs.onSurfaceVariant)

        Spacer(Modifier.height(12.dp))
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
        } else if (items.isEmpty()) {
            Surface(color = cs.surface, shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, cs.outline), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无内容", color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { load() }) { Text("刷新") }
                }
            }
        } else {
            items.forEach { item ->
                Surface(
                    color = cs.surface, shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, cs.outline),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.bodyLarge,
                                color = cs.onSurface, fontWeight = FontWeight.Medium)
                            if (item.subtitle.isNotEmpty()) {
                                Spacer(Modifier.height(2.dp))
                                Text(item.subtitle, style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant)
                            }
                            if (item.desc.isNotEmpty()) {
                                Spacer(Modifier.height(3.dp))
                                Text(item.desc, style = MaterialTheme.typography.bodySmall,
                                    color = cs.primary)
                            }
                            if (item.id.isNotEmpty() && item.id != item.title) {
                                Spacer(Modifier.height(2.dp))
                                Text(item.id, style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                            }
                        }
                        // 分区相关操作
                        when (tab) {
                            "mcp" -> {
                                TextButton(onClick = {
                                    status = "测试中…"
                                    client.mcpTest(item.id, object : AcpClient.RespCb {
                                        override fun onResult(r: org.json.JSONObject) { status = "✓ ${item.title} 连通" }
                                        override fun onError(m: String?) { status = "✗ ${item.title}：${m ?: ""}" }
                                    })
                                }) { Text("测试") }
                            }
                            "packages" -> {
                                TextButton(onClick = {
                                    status = "卸载中…"
                                    client.packagesRemove(item.id, object : AcpClient.RespCb {
                                        override fun onResult(r: org.json.JSONObject) { status = "已卸载 ${item.title}"; load() }
                                        override fun onError(m: String?) { status = "卸载失败：${m ?: ""}" }
                                    })
                                }) { Text("卸载", color = cs.error) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManageTab(text: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) cs.primaryContainer else cs.surface,
        border = BorderStroke(1.dp, if (selected) cs.primary else cs.outline),
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onClick() }
    ) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant)
    }
}
