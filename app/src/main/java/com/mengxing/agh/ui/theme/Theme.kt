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
import androidx.compose.ui.unit.dp
import com.mengxing.agh.data.AppConfig

/** 品牌主色：豆沙绿 */
val YunoGreen = Color(0xFF7C9F69)

/** 一套皮肤的核心色。 */
private class Skin(
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightContainer: Color,
    val lightOnContainer: Color,
    val lightSecondary: Color,
    val lightSecContainer: Color,
    val lightOnSecContainer: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkContainer: Color,
    val darkOnContainer: Color,
    val darkSecondary: Color,
    val darkSecContainer: Color,
    val darkOnSecContainer: Color
)

private val SKINS = mapOf(
    "green" to Skin(
        Color(0xFF2F7D5B), Color.White, Color(0xFFDCEBDA), Color(0xFF12301F),
        Color(0xFF7C9F69), Color(0xFFE4EEDD), Color(0xFF1C331F),
        Color(0xFF7C9F69), Color(0xFF07271C), Color(0xFF1E3A2C), Color(0xFFD9EBD9),
        Color(0xFF7C9F69), Color(0xFF20342A), Color(0xFFD3E5D5)
    ),
    "blue" to Skin(
        Color(0xFF2563A8), Color.White, Color(0xFFD9E6F5), Color(0xFF0E2138),
        Color(0xFF4A82C4), Color(0xFFE1EBF7), Color(0xFF122A45),
        Color(0xFF6AA6E0), Color(0xFF04182E), Color(0xFF1B2F47), Color(0xFFD3E3F5),
        Color(0xFF6AA6E0), Color(0xFF1D334E), Color(0xFFD3E3F5)
    ),
    "orange" to Skin(
        Color(0xFFC2691E), Color.White, Color(0xFFF8E4D2), Color(0xFF3A1E08),
        Color(0xFFE08A3C), Color(0xFFFAEADB), Color(0xFF432612),
        Color(0xFFE8974C), Color(0xFF2A1503), Color(0xFF40270F), Color(0xFFF8E4D2),
        Color(0xFFE8974C), Color(0xFF432612), Color(0xFFF8E4D2)
    ),
    "violet" to Skin(
        Color(0xFF6D4BA8), Color.White, Color(0xFFE6DDF5), Color(0xFF241441),
        Color(0xFF8E6BC8), Color(0xFFEDE6F9), Color(0xFF2C1B4E),
        Color(0xFFA98BDD), Color(0xFF1A0E30), Color(0xFF2C1F4A), Color(0xFFE6DDF5),
        Color(0xFFA98BDD), Color(0xFF31234F), Color(0xFFE6DDF5)
    ),
    "slate" to Skin(
        Color(0xFF4B5563), Color.White, Color(0xFFE4E7EB), Color(0xFF1A1F26),
        Color(0xFF6B7280), Color(0xFFECEEF1), Color(0xFF20252C),
        Color(0xFF9AA4B2), Color(0xFF14181E), Color(0xFF262C34), Color(0xFFE4E7EB),
        Color(0xFF9AA4B2), Color(0xFF2A3038), Color(0xFFE4E7EB)
    )
)

private fun lightScheme(skin: Skin) = lightColorScheme(
    primary = skin.lightPrimary,
    onPrimary = skin.lightOnPrimary,
    primaryContainer = skin.lightContainer,
    onPrimaryContainer = skin.lightOnContainer,
    secondary = skin.lightSecondary,
    onSecondary = Color.White,
    secondaryContainer = skin.lightSecContainer,
    onSecondaryContainer = skin.lightOnSecContainer,
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

private fun darkScheme(skin: Skin) = darkColorScheme(
    primary = skin.darkPrimary,
    onPrimary = skin.darkOnPrimary,
    primaryContainer = skin.darkContainer,
    onPrimaryContainer = skin.darkOnContainer,
    secondary = skin.darkSecondary,
    onSecondary = skin.darkOnPrimary,
    secondaryContainer = skin.darkSecContainer,
    onSecondaryContainer = skin.darkOnSecContainer,
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

private fun fontScaleOf(key: String): Float = when (key) {
    "small" -> 0.90f
    "large" -> 1.12f
    "xlarge" -> 1.25f
    else -> 1.0f
}

private fun scaledTypography(s: Float): Typography {
    if (s == 1.0f) return Typography()
    val b = Typography()
    return b.copy(
        displayLarge = b.displayLarge.copy(fontSize = b.displayLarge.fontSize * s),
        displayMedium = b.displayMedium.copy(fontSize = b.displayMedium.fontSize * s),
        displaySmall = b.displaySmall.copy(fontSize = b.displaySmall.fontSize * s),
        headlineLarge = b.headlineLarge.copy(fontSize = b.headlineLarge.fontSize * s),
        headlineMedium = b.headlineMedium.copy(fontSize = b.headlineMedium.fontSize * s),
        headlineSmall = b.headlineSmall.copy(fontSize = b.headlineSmall.fontSize * s),
        titleLarge = b.titleLarge.copy(fontSize = b.titleLarge.fontSize * s),
        titleMedium = b.titleMedium.copy(fontSize = b.titleMedium.fontSize * s),
        titleSmall = b.titleSmall.copy(fontSize = b.titleSmall.fontSize * s),
        bodyLarge = b.bodyLarge.copy(fontSize = b.bodyLarge.fontSize * s),
        bodyMedium = b.bodyMedium.copy(fontSize = b.bodyMedium.fontSize * s),
        bodySmall = b.bodySmall.copy(fontSize = b.bodySmall.fontSize * s),
        labelLarge = b.labelLarge.copy(fontSize = b.labelLarge.fontSize * s),
        labelMedium = b.labelMedium.copy(fontSize = b.labelMedium.fontSize * s),
        labelSmall = b.labelSmall.copy(fontSize = b.labelSmall.fontSize * s)
    )
}

@Composable
fun YunoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    skin: String = "green",
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val s = SKINS[skin] ?: SKINS["green"]!!
    val colors = if (darkTheme) darkScheme(s) else lightScheme(s)
    MaterialTheme(
        colorScheme = colors,
        shapes = YunoShapes,
        typography = scaledTypography(fontScale),
        content = content
    )
}

/** 全局主题：读取用户偏好（配色 / 皮肤 / 字号）后套用 YunoTheme。 */
@Composable
fun YunoAppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val theme = AppConfig.theme(ctx)
    val skin = AppConfig.skin(ctx)
    val font = fontScaleOf(AppConfig.fontScale(ctx))
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    YunoTheme(darkTheme = dark, skin = skin, fontScale = font, content = content)
}
