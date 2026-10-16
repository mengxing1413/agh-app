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
 * Nanobot 后端适配器（多 Agent 后端之一）。
 *
 * 协议：WebSocket JSON 消息（与 AGH 的 ACP 不同）
 *   → {"type":"new_chat"}                    建会话，回 {"type":"chat_created","chat_id":"..."}
 *   → {"type":"message","chat_id":"...","content":"..."}   发消息
 *   ← {"type":"message_delta"/"message","chat_id":"...","content":"..."}  流式/完整回复
 *   ← {"type":"turn_end","chat_id":"..."}    一轮结束
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
    private val pending = StringBuilder()

    fun setListener(l: Listener) { listener = l }

    fun connect() {
        val full = if (url.contains("?")) "$url&token=$token" else "$url?token=$token"
        val req = Request.Builder().url(full).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(JSONObject().put("type", "new_chat").toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val o = JSONObject(text)
                    when (o.optString("type", "")) {
                        "chat_created" -> {
                            chatId = o.optString("chat_id", "")
                            main.post { listener?.onConnected(chatId ?: "") }
                        }
                        "message_delta" -> {
                            val t = o.optString("content", "")
                            if (t.isNotEmpty()) main.post { listener?.onReply(t, false) }
                        }
                        "message" -> {
                            val t = o.optString("content", "")
                            if (t.isNotEmpty()) main.post { listener?.onReply(t, true) }
                        }
                        "turn_end" -> main.post { listener?.onReply("", true) }
                        "error" -> main.post { listener?.onError(o.optString("message", "未知错误")) }
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

    fun send(text: String) {
        val cid = chatId
        if (cid.isNullOrEmpty()) { main.post { listener?.onError("尚未连接成功") }; return }
        ws?.send(JSONObject().put("type", "message").put("chat_id", cid).put("content", text).toString())
    }

    fun close() {
        try { ws?.close(1000, "bye") } catch (e: Exception) {}
        ws = null
    }
}
