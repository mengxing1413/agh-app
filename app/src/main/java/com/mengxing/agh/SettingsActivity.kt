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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.mengxing.agh.data.AppConfig
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
    var screen by remember { mutableStateOf("main") }        // main / connections / edit
    var editId by remember { mutableStateOf<String?>(null) } // 正在编辑的连接 id（null=新增）
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
            onAdd = { editId = null; screen = "edit" },
            onEdit = { id -> editId = id; screen = "edit" },
            onDelete = { id -> AppConfig.deleteProfile(ctx, id); reload() },
            onTest = { p, cb -> testConnection(p, cb) }
        )

        "edit" -> EditConnectionScreen(
            ctx = ctx,
            original = profiles.firstOrNull { it.id == editId },
            onBack = { screen = "connections" },
            onSave = { p ->
                AppConfig.upsertProfile(ctx, p)
                if (editId == null) AppConfig.setActiveId(ctx, p.id)   // 新增即设为当前
                reload(); screen = "connections"
            }
        )

        else -> MainSettings(
            ctx = ctx,
            active = AppConfig.activeProfile(ctx),
            onClose = onClose,
            onManageConnections = { screen = "connections" }
        )
    }
}

// ==================== 主设置页 ====================

@Composable
private fun MainSettings(
    ctx: Context,
    active: ServerProfile,
    onClose: () -> Unit,
    onManageConnections: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var theme by remember { mutableStateOf(AppConfig.theme(ctx)) }
    var skin by remember { mutableStateOf(AppConfig.skin(ctx)) }
    var font by remember { mutableStateOf(AppConfig.fontScale(ctx)) }

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

        GroupTitle("配色")
        OptionRow("跟随系统", "随手机深色模式自动切换（推荐）", theme == "system") {
            theme = "system"; AppConfig.setString(ctx, AppConfig.KEY_THEME, theme); recreate()
        }
        OptionRow("浅色", "固定浅色，不随系统变化", theme == "light") {
            theme = "light"; AppConfig.setString(ctx, AppConfig.KEY_THEME, theme); recreate()
        }
        OptionRow("品牌深绿", "固定深绿，不随系统变化", theme == "dark") {
            theme = "dark"; AppConfig.setString(ctx, AppConfig.KEY_THEME, theme); recreate()
        }

        GroupTitle("皮肤")
        SkinRow(skin) { k -> skin = k; AppConfig.setString(ctx, AppConfig.KEY_SKIN, k); recreate() }

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
    val skins = listOf(
        Triple("green", "豆沙绿", YunoSkinDot(Color(0xFF7C9F69))),
        Triple("blue", "海洋蓝", YunoSkinDot(Color(0xFF4A82C4))),
        Triple("orange", "暖橙", YunoSkinDot(Color(0xFFE08A3C))),
        Triple("violet", "紫罗兰", YunoSkinDot(Color(0xFF8E6BC8))),
        Triple("slate", "石墨灰", YunoSkinDot(Color(0xFF6B7280)))
    )
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
    onBack: () -> Unit,
    onSave: (ServerProfile) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val isNew = original == null
    var name by remember { mutableStateOf(original?.name ?: "") }
    var url by remember { mutableStateOf(original?.url ?: "") }
    var user by remember { mutableStateOf(original?.user ?: "") }
    var pass by remember { mutableStateOf(original?.pass ?: "") }
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
            placeholder = { Text("wss://主机:端口/__agh_ws") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))

        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = user, onValueChange = { user = it },
                label = { Text("用户名") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = pass, onValueChange = { pass = it },
                label = { Text("密码") }, singleLine = true,
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
                        name.ifEmpty { "未命名" }, normalizeWs(url), user, pass)
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
                    name.ifEmpty { "未命名" }, normalizeWs(url), user, pass)
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

/** 规范化成 AGH WebSocket 地址：http(s)→ws(s)，无路径则补 /__agh_ws。 */
private fun normalizeWs(input: String): String {
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
    val schemeEnd = s.indexOf("://") + 3
    val afterScheme = s.substring(schemeEnd)
    val hasPath = afterScheme.contains("/")
    if (!hasPath || afterScheme.endsWith("/")) {
        s = s.trimEnd('/') + "/__agh_ws"
    }
    return s
}

/** 轻量连接测试：连上即成功，8 秒超时。 */
private fun testConnection(p: ServerProfile, cb: (Boolean, String) -> Unit) {
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
        override fun onUserMessage(text: String?) {}
        override fun onSessionLoaded(sid: String?) {}
        override fun onPromptDone(stopReason: String?) {}
    })
    client.connect(p.url, p.user, p.pass)
    handler.postDelayed({ finish(false, "连接超时（请检查地址/网络）") }, 8000)
}
