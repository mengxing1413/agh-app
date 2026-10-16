package com.mengxing.agh.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
    ),
    // —— 第二批补充（取自 dsh-theme-pack，凑满 16 款）———————————
    "rosepine" to Skin(
        Color(0xFFEB6F92), Color.White, Color(0xFFFBE4EA), Color(0xFF3D1A24),
        Color(0xFFEBA0B0), Color(0xFFFBECF0), Color(0xFF4A2230),
        Color(0xFFEB6F92), Color(0xFFFFE8EE), Color(0xFF3A1F28), Color(0xFFFFE8EE),
        Color(0xFFF0A9BC), Color(0xFF4A2833), Color(0xFFFFE8EE),
        Color(0xFFFDF5F7), Color(0xFFFFFFFF), Color(0xFFF6E9ED), Color(0xFFEBD8DE),
        Color(0xFF191724), Color(0xFF1F1D2E), Color(0xFF26233A), Color(0xFF3A3550)
    ),
    "gruvbox" to Skin(
        Color(0xFFB57614), Color.White, Color(0xFFF8E7BC), Color(0xFF3D2C0A),
        Color(0xFFD79821), Color(0xFFFAEDCB), Color(0xFF4A3510),
        Color(0xFFFABD2F), Color(0xFF3D2C0A), Color(0xFF504531), Color(0xFFFFE9A8),
        Color(0xFFD79921), Color(0xFF5A4A30), Color(0xFFFFE9A8),
        Color(0xFFFBF1C7), Color(0xFFFFFFFF), Color(0xFFF2E5B8), Color(0xFFE5D7A8),
        Color(0xFF282828), Color(0xFF3C3836), Color(0xFF504945), Color(0xFF665C54)
    ),
    "kanagawa" to Skin(
        Color(0xFF5A7BB5), Color.White, Color(0xFFDDE5F5), Color(0xFF1B2233),
        Color(0xFF7E9CD8), Color(0xFFE8EEF9), Color(0xFF23293D),
        Color(0xFF7E9CD8), Color(0xFF0F1420), Color(0xFF2A3045), Color(0xFFD9E2F5),
        Color(0xFF957FB8), Color(0xFF363B54), Color(0xFFD9E2F5),
        Color(0xFFF4F6FB), Color(0xFFFFFFFF), Color(0xFFE8ECF6), Color(0xFFD8DFEE),
        Color(0xFF1F1F28), Color(0xFF2A2A37), Color(0xFF363646), Color(0xFF54546D)
    ),
    "solarized" to Skin(
        Color(0xFF2076B8), Color.White, Color(0xFFD8EAF7), Color(0xFF073642),
        Color(0xFF2AA198), Color(0xFFE3F2F0), Color(0xFF073642),
        Color(0xFF4FB3E8), Color(0xFF03242E), Color(0xFF0F4652), Color(0xFFD6EDF3),
        Color(0xFF2AA198), Color(0xFF17505C), Color(0xFFD6EDF3),
        Color(0xFFFDF6E3), Color(0xFFFFFFFF), Color(0xFFF5EEDB), Color(0xFFE7E0CC),
        Color(0xFF002B36), Color(0xFF073642), Color(0xFF0E4652), Color(0xFF1A5A66)
    ),
    "nightowl" to Skin(
        Color(0xFF4A78D8), Color.White, Color(0xFFDCE6FB), Color(0xFF12203F),
        Color(0xFF7E57C2), Color(0xFFE9E2F8), Color(0xFF1A1740),
        Color(0xFF82AAFF), Color(0xFF0A1E38), Color(0xFF17304F), Color(0xFFDCE8FF),
        Color(0xFFC792EA), Color(0xFF23405F), Color(0xFFE6D9FA),
        Color(0xFFF3F7FD), Color(0xFFFFFFFF), Color(0xFFE7EFFA), Color(0xFFD6E2F2),
        Color(0xFF011627), Color(0xFF0B2942), Color(0xFF1D3B53), Color(0xFF2C4B63)
    ),
    "onedark" to Skin(
        Color(0xFF3A82C4), Color.White, Color(0xFFDBEAF8), Color(0xFF1B2A38),
        Color(0xFF56B6C2), Color(0xFFE0F2F4), Color(0xFF1B2A38),
        Color(0xFF61AFEF), Color(0xFF12202E), Color(0xFF22334A), Color(0xFFD7E7FA),
        Color(0xFF56B6C2), Color(0xFF2C3E52), Color(0xFFD7E7FA),
        Color(0xFFF6F7F9), Color(0xFFFFFFFF), Color(0xFFEAEEF2), Color(0xFFD9DFE6),
        Color(0xFF282C34), Color(0xFF21252B), Color(0xFF333842), Color(0xFF4B5263)
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
    // 浮层容器（下拉菜单 / 弹窗 / 底部弹层）显式跟随皮肤：
    // 不设的话 Material3 从出厂紫调基线派生 → 权限下拉泛蓝紫、与主题对不上（v0.3.5 实测）。
    surfaceContainerLowest = skin.lightSurface,
    surfaceContainerLow = skin.lightSurface,
    surfaceContainer = skin.lightSurfaceVariant,
    surfaceContainerHigh = skin.lightSurfaceVariant,
    surfaceContainerHighest = skin.lightSurfaceVariant,
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
    surfaceContainerLowest = skin.darkBg,
    surfaceContainerLow = skin.darkSurface,
    surfaceContainer = skin.darkSurfaceVariant,
    surfaceContainerHigh = skin.darkSurfaceVariant,
    surfaceContainerHighest = skin.darkSurfaceVariant,
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
    glass: Boolean = false,
    content: @Composable () -> Unit
) {
    val s = SKINS[skin] ?: SKINS["green"]!!
    val base = if (darkTheme) darkScheme(s) else lightScheme(s)
    val colors = if (glass) glassScheme(base, darkTheme) else base

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
        content = {
            if (glass) {
                // 玻璃质感：先铺一段「按当前主题色派生」的柔光渐变壁纸，近不透明面板之间的缝隙透出它。
                // （真 backdrop 模糊 Compose 单层做不了；面板 ≥95% 不透明才不会透出底下文字叠字）
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.linearGradient(glassWallpaper(s, darkTheme))
                        )
                    )
                    content()
                }
            } else content()
        }
    )
}

/** 玻璃材质：不动主题色，只把面板换成「近不透明微透 + 高光边」（<95% 会透出底下文字，实测会叠字）。 */
private fun glassScheme(base: ColorScheme, dark: Boolean): ColorScheme = base.copy(
    surface = base.surface.copy(alpha = 0.98f),
    surfaceVariant = base.surfaceVariant.copy(alpha = 0.94f),
    outline = if (dark) Color(0x4DFFFFFF) else Color(0x99FFFFFF),
    surfaceContainer = base.surfaceVariant.copy(alpha = 0.96f),
    surfaceContainerHigh = base.surfaceVariant.copy(alpha = 0.96f),
    surfaceContainerHighest = base.surfaceVariant.copy(alpha = 0.96f)
)

/** 按当前主题色派生三段柔光渐变壁纸：底色向强调色偏移 —— 每款主题各有自己的玻璃色调。 */
private fun glassWallpaper(s: Skin, dark: Boolean): List<Color> {
    val bg = if (dark) s.darkBg else s.lightBg
    val tint = if (dark) s.darkPrimary else s.lightPrimary
    return listOf(
        mixColor(bg, tint, if (dark) 0.12f else 0.05f),
        mixColor(bg, tint, if (dark) 0.26f else 0.17f),
        mixColor(bg, tint, if (dark) 0.14f else 0.10f)
    )
}

private fun mixColor(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t
)

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
    YunoTheme(darkTheme = dark, skin = skin, fontScale = font,
        glass = AppConfig.glass(ctx), content = content)
}
