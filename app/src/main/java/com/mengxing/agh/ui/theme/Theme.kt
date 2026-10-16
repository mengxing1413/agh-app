package com.mengxing.agh.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
    val darkOnSecContainer: Color,
    // 底色（背景/卡片/分隔线）：必须随皮肤走，否则会出现"选海洋蓝还是绿底"的错版感
    val lightBg: Color,
    val lightSurface: Color,
    val lightSurfaceVariant: Color,
    val lightOutline: Color,
    val darkBg: Color,
    val darkSurface: Color,
    val darkSurfaceVariant: Color,
    val darkOutline: Color
)

private val SKINS = mapOf(
    "green" to Skin(
        Color(0xFF2F7D5B), Color.White, Color(0xFFDCEBDA), Color(0xFF12301F),
        Color(0xFF7C9F69), Color(0xFFE4EEDD), Color(0xFF1C331F),
        Color(0xFF7C9F69), Color(0xFF07271C), Color(0xFF1E3A2C), Color(0xFFD9EBD9),
        Color(0xFF7C9F69), Color(0xFF20342A), Color(0xFFD3E5D5),
        Color(0xFFF4F8F3), Color(0xFFFFFFFF), Color(0xFFE9F0E7), Color(0xFFDCE5DA),
        Color(0xFF0B1410), Color(0xFF14231C), Color(0xFF1C2E25), Color(0xFF2A3D33)
    ),
    "blue" to Skin(
        Color(0xFF2563A8), Color.White, Color(0xFFD9E6F5), Color(0xFF0E2138),
        Color(0xFF4A82C4), Color(0xFFE1EBF7), Color(0xFF122A45),
        Color(0xFF6AA6E0), Color(0xFF04182E), Color(0xFF1B2F47), Color(0xFFD3E3F5),
        Color(0xFF6AA6E0), Color(0xFF1D334E), Color(0xFFD3E3F5),
        Color(0xFFF3F7FB), Color(0xFFFFFFFF), Color(0xFFE8F0F8), Color(0xFFD9E5F1),
        Color(0xFF0A1119), Color(0xFF13212E), Color(0xFF1A2B3B), Color(0xFF28394B)
    ),
    "orange" to Skin(
        Color(0xFFC2691E), Color.White, Color(0xFFF8E4D2), Color(0xFF3A1E08),
        Color(0xFFE08A3C), Color(0xFFFAEADB), Color(0xFF432612),
        Color(0xFFE8974C), Color(0xFF2A1503), Color(0xFF40270F), Color(0xFFF8E4D2),
        Color(0xFFE8974C), Color(0xFF432612), Color(0xFFF8E4D2),
        Color(0xFFFBF6F0), Color(0xFFFFFFFF), Color(0xFFF6EDE3), Color(0xFFEBE0D3),
        Color(0xFF14100B), Color(0xFF221A11), Color(0xFF2D2217), Color(0xFF3E2F21)
    ),
    "violet" to Skin(
        Color(0xFF6D4BA8), Color.White, Color(0xFFE6DDF5), Color(0xFF241441),
        Color(0xFF8E6BC8), Color(0xFFEDE6F9), Color(0xFF2C1B4E),
        Color(0xFFA98BDD), Color(0xFF1A0E30), Color(0xFF2C1F4A), Color(0xFFE6DDF5),
        Color(0xFFA98BDD), Color(0xFF31234F), Color(0xFFE6DDF5),
        Color(0xFFF8F6FB), Color(0xFFFFFFFF), Color(0xFFEFEAF7), Color(0xFFE3DCEF),
        Color(0xFF100C18), Color(0xFF1C1626), Color(0xFF262034), Color(0xFF362B48)
    ),
    "slate" to Skin(
        Color(0xFF4B5563), Color.White, Color(0xFFE4E7EB), Color(0xFF1A1F26),
        Color(0xFF6B7280), Color(0xFFECEEF1), Color(0xFF20252C),
        Color(0xFF9AA4B2), Color(0xFF14181E), Color(0xFF262C34), Color(0xFFE4E7EB),
        Color(0xFF9AA4B2), Color(0xFF2A3038), Color(0xFFE4E7EB),
        Color(0xFFF5F6F8), Color(0xFFFFFFFF), Color(0xFFEBEDF1), Color(0xFFDDE1E7),
        Color(0xFF101216), Color(0xFF1A1D22), Color(0xFF23272E), Color(0xFF323840)
    ),
    // —— 社区主题（配色取自 dsh-theme-pack，明暗双套）———————————
    // 深色侧用社区原色，浅色侧按同一强调色派生，保证浅/深都协调。
    "nord" to Skin(
        Color(0xFF5E81AC), Color.White, Color(0xFFDDE5EE), Color(0xFF2E3440),
        Color(0xFF81A1C1), Color(0xFFE7EDF3), Color(0xFF2E3440),
        Color(0xFF81A1C1), Color(0xFF2E3440), Color(0xFF3B4252), Color(0xFFD8DEE9),
        Color(0xFF88C0D0), Color(0xFF434C5E), Color(0xFFD8DEE9),
        Color(0xFFEEF1F5), Color(0xFFFFFFFF), Color(0xFFE3E8EE), Color(0xFFD3DAE2),
        Color(0xFF2E3440), Color(0xFF3B4252), Color(0xFF434C5E), Color(0xFF4C566A)
    ),
    "dracula" to Skin(
        Color(0xFF7C4DCC), Color.White, Color(0xFFECE4FB), Color(0xFF282A36),
        Color(0xFFBD93F9), Color(0xFFF0E9FD), Color(0xFF282A36),
        Color(0xFFBD93F9), Color(0xFF282A36), Color(0xFF44475A), Color(0xFFF8F8F2),
        Color(0xFFFF79C6), Color(0xFF4E5266), Color(0xFFF8F8F2),
        Color(0xFFF5F3FA), Color(0xFFFFFFFF), Color(0xFFEBE7F3), Color(0xFFDDD7EA),
        Color(0xFF282A36), Color(0xFF2F3241), Color(0xFF44475A), Color(0xFF4E5266)
    ),
    "tokyo" to Skin(
        Color(0xFF3B6FD4), Color.White, Color(0xFFDFE8FB), Color(0xFF1A1B26),
        Color(0xFF7AA2F7), Color(0xFFE8EEFC), Color(0xFF1A1B26),
        Color(0xFF7AA2F7), Color(0xFF1A1B26), Color(0xFF1F2335), Color(0xFFC0CAF5),
        Color(0xFFBB9AF7), Color(0xFF292E42), Color(0xFFC0CAF5),
        Color(0xFFEEF1F8), Color(0xFFFFFFFF), Color(0xFFE2E7F1), Color(0xFFD2D9E6),
        Color(0xFF1A1B26), Color(0xFF1F2335), Color(0xFF292E42), Color(0xFF3B4261)
    ),
    "catppuccin" to Skin(
        Color(0xFF6C8FD6), Color.White, Color(0xFFE2E9FA), Color(0xFF1E1E2E),
        Color(0xFF89B4FA), Color(0xFFEAF0FC), Color(0xFF1E1E2E),
        Color(0xFF89B4FA), Color(0xFF1E1E2E), Color(0xFF313244), Color(0xFFCDD6F4),
        Color(0xFFCBA6F7), Color(0xFF45475A), Color(0xFFCDD6F4),
        Color(0xFFF2F3F9), Color(0xFFFFFFFF), Color(0xFFE8EAF3), Color(0xFFD9DCE8),
        Color(0xFF1E1E2E), Color(0xFF262738), Color(0xFF313244), Color(0xFF45475A)
    ),
    "everforest" to Skin(
        Color(0xFF4F8A70), Color.White, Color(0xFFDDEEE6), Color(0xFF2D353B),
        Color(0xFF7FBBB3), Color(0xFFE5F2EE), Color(0xFF2D353B),
        Color(0xFF7FBBB3), Color(0xFF2D353B), Color(0xFF343F44), Color(0xFFD3C6AA),
        Color(0xFFA7C080), Color(0xFF3D484D), Color(0xFFD3C6AA),
        Color(0xFFEEF4F1), Color(0xFFFFFFFF), Color(0xFFE2EBE7), Color(0xFFD2DED9),
        Color(0xFF2D353B), Color(0xFF343F44), Color(0xFF3D484D), Color(0xFF475258)
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
    // 底色随皮肤走（豆沙绿→绿灰底、海洋蓝→蓝灰底…），不再写死
    background = skin.lightBg,
    onBackground = Color(0xFF111827),
    surface = skin.lightSurface,
    onSurface = Color(0xFF111827),
    surfaceVariant = skin.lightSurfaceVariant,
    onSurfaceVariant = Color(0xFF6B7280),
    outline = skin.lightOutline,
    outlineVariant = skin.lightSurfaceVariant,
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
    // 深色底色也随皮肤走（之前写死深绿，导致"海洋蓝却是绿底"）
    background = skin.darkBg,
    onBackground = Color(0xFFF2F7F4),
    surface = skin.darkSurface,
    onSurface = Color(0xFFF2F7F4),
    surfaceVariant = skin.darkSurfaceVariant,
    onSurfaceVariant = Color(0xFF9FB3A8),
    outline = skin.darkOutline,
    outlineVariant = skin.darkSurfaceVariant,
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

    // window 背景 / 状态栏 / 导航栏跟随主题。
    // 根因：Android 的 windowBackground 由 themes.xml 决定，只认系统深浅色
    // （values / values-night），不认 App 内选的"固定深色" —— 页面根容器没涂色时
    // 就会露出系统浅色的 #F6F7F9 白底（现象：选了深色，设置页仍是白底+深卡片）。
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val bg = colors.background.toArgb()
                window.decorView.setBackgroundColor(bg)
                @Suppress("DEPRECATION")
                window.statusBarColor = bg
                @Suppress("DEPRECATION")
                window.navigationBarColor = colors.surface.toArgb()
            }
        }
    }

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
