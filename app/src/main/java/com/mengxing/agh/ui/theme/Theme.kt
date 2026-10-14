package com.mengxing.agh.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 品牌主色：豆沙绿 */
val YunoGreen = Color(0xFF7C9F69)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F7D5B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBDA),
    onPrimaryContainer = Color(0xFF12301F),
    secondary = YunoGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EEDD),
    onSecondaryContainer = Color(0xFF1C331F),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEDF1EE),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFE2E6EA),
    outlineVariant = Color(0xFFEDF0F2),
    error = Color(0xFFC0392B),
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = YunoGreen,
    onPrimary = Color(0xFF07271C),
    primaryContainer = Color(0xFF1E3A2C),
    onPrimaryContainer = Color(0xFFD9EBD9),
    secondary = YunoGreen,
    onSecondary = Color(0xFF07271C),
    secondaryContainer = Color(0xFF20342A),
    onSecondaryContainer = Color(0xFFD3E5D5),
    background = Color(0xFF0B1410),
    onBackground = Color(0xFFF2F7F4),
    surface = Color(0xFF14231C),
    onSurface = Color(0xFFF2F7F4),
    surfaceVariant = Color(0xFF1C2E25),
    onSurfaceVariant = Color(0xFF9FB3A8),
    outline = Color(0xFF2A3D33),
    outlineVariant = Color(0xFF22322A),
    error = Color(0xFFE06A6A),
    onError = Color(0xFF3A0A0A)
)

private val YunoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun YunoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        shapes = YunoShapes,
        typography = Typography(),
        content = content
    )
}

/** 全局主题：读取用户偏好（跟随系统 / 固定浅色 / 固定深绿）后套用 YunoTheme。 */
@Composable
fun YunoAppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val prefs = ctx.getSharedPreferences("yuno_prefs", Context.MODE_PRIVATE)
    val theme = prefs.getString("theme", "system") ?: "system"
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    YunoTheme(darkTheme = dark, content = content)
}
