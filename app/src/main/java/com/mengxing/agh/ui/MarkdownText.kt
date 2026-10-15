package com.mengxing.agh.ui

import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin

private var sharedMarkwon: Markwon? = null

private fun markwonFor(tv: TextView): Markwon {
    var m = sharedMarkwon
    if (m == null) {
        // 用 applicationContext 构建单例，避免持有首个 TextView 的 Activity context 造成泄漏
        val appCtx = tv.context.applicationContext
        m = Markwon.builder(appCtx).usePlugin(TablePlugin.create(appCtx)).build()
        sharedMarkwon = m
    }
    return m
}

/** 用 Markwon 在 Compose 中渲染 Markdown（复用已验证的渲染引擎）。 */
@Composable
fun MarkdownText(
    markdown: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColor.toArgb())
                textSize = 15f
                setLineSpacing(0f, 1.12f)
            }
        },
        update = { tv ->
            tv.setTextColor(textColor.toArgb())
            markwonFor(tv).setMarkdown(tv, markdown)
        }
    )
}
