package com.mengxing.agh

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Nanobot 后端适配器（陆吾）。
 *
 * 协议（实测 + 对齐 Clawket 实现）：
 *   → {"type":"new_chat"}                          可选：新建会话
 *   → {"type":"message","chat_id":"...","content":"..."}
 *   ← 服务端回包字段是 `event`（不是 type），事件：
 *       ready          连接就绪，带 chat_id
 *       attached       新建会话完成，带 chat_id
 *       delta          流式增量（text）
 *       reasoning_delta 思考增量
 *       stream_end     回合结束
 *       message        完整回复（text）
 *       error          错误（detail）
 *   兼容：tag = msg.event ?? msg.type
 */
class NanobotClient(
    private val url: String,
    private val token: String
) {
    interface Listener {
        fun onConnected(chatId: String)
        fun onReply(text: String, done: Boolean)
        fun onError(msg: String)
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)   // 弱网下大包不被 10s 默认写超时判死
        .connectTimeout(20, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()
    private val main = Handler(Looper.getMainLooper())
    private var ws: WebSocket? = null
    private var listener: Listener? = null
    private var chatId: String? = null

    fun setListener(l: Listener) { listener = l }
    fun getChatId(): String? = chatId

    fun connect() {
        val full = if (url.contains("?")) "$url&token=$token" else "$url?token=$token"
        val req = Request.Builder().url(full).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // nanobot 连接后会主动推 ready（带 chat_id），不需要客户端握手
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val o = JSONObject(text)
                    val tag = if (o.has("event")) o.optString("event", "") else o.optString("type", "")
                    val cid = o.optString("chat_id", "")
                    when (tag) {
                        "ready", "attached" -> {
                            if (cid.isNotEmpty()) chatId = cid
                            val c = chatId ?: ""
                            main.post { listener?.onConnected(c) }
                        }
                        "delta" -> {
                            val t = o.optString("text", o.optString("content", ""))
                            if (t.isNotEmpty()) main.post { listener?.onReply(t, false) }
                        }
                        "stream_end" -> main.post { listener?.onReply("", true) }
                        "message" -> {
                            val t = o.optString("text", o.optString("content", ""))
                            if (t.isNotEmpty()) main.post { listener?.onReply(t, true) }
                        }
                        "error" -> {
                            val d = o.optString("detail", o.optString("message", "未知错误"))
                            main.post { listener?.onError(d) }
                        }
                    }
                } catch (e: Exception) {
                    main.post { listener?.onError("响应解析失败：" + e.message) }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                main.post { listener?.onError("连接失败：" + t.message) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                main.post { listener?.onError("连接已关闭") }
            }
        })
    }

    /** 新建会话（可选；不调用则直接用 ready 给的默认 chat_id）。 */
    fun newChat() {
        ws?.send(JSONObject().put("type", "new_chat").toString())
    }

    fun send(text: String) {
        val cid = chatId
        if (cid.isNullOrEmpty()) { main.post { listener?.onError("尚未连接成功") }; return }
        ws?.send(
            JSONObject().put("type", "message").put("chat_id", cid).put("content", text).toString()
        )
    }

    fun close() {
        try { ws?.close(1000, "bye") } catch (e: Exception) {}
        ws = null
    }
}
