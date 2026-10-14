package com.mengxing.agh;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * 原生 ACP（Agent Client Protocol）客户端：
 * 通过 WebSocket + JSON-RPC 直连 AGH，实现 initialize / session/new /
 * session/prompt / session/update（流式）/ session/request_permission。
 * 完全不依赖 WebView。
 */
public class AcpClient {

    public interface Listener {
        void onConnected();
        void onMessageChunk(String text);       // 流式片段
        void onAgentMessage(String text);       // 完整消息
        void onToolCall(String name, String args);
        void onPermissionRequest(String reqId, String desc);
        void onPromptDone(String stopReason);
        void onError(String msg);
        void onClosed();
    }

    private final OkHttpClient client;
    private final Handler main = new Handler(Looper.getMainLooper());
    private WebSocket ws;
    private Listener listener;
    private int nextId = 1;
    private final Map<Integer, RespCb> pending = new HashMap<>();
    private String sessionId;

    public interface RespCb { void onResult(JSONObject result); void onError(String msg); }

    public AcpClient() {
        client = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // 长连接不超时
            .pingInterval(25, TimeUnit.SECONDS)
            .build();
    }

    public void setListener(Listener l) { this.listener = l; }
    public String getSessionId() { return sessionId; }

    public void connect(String url, String user, String pass) {
        String cred = android.util.Base64.encodeToString(
            (user + ":" + pass).getBytes(), android.util.Base64.NO_WRAP);
        Request req = new Request.Builder()
            .url(url)
            .header("Authorization", "Basic " + cred)
            .header("Origin", "http://127.0.0.1:4177")
            .header("Sec-WebSocket-Protocol", "agnes-v1")
            .build();

        ws = client.newWebSocket(req, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                notifyConnected();
                doInitialize();
            }
            @Override public void onMessage(WebSocket webSocket, String text) {
                handleMessage(text);
            }
            @Override public void onFailure(WebSocket webSocket, Throwable t, Response r) {
                notifyError("连接失败：" + t.getMessage());
            }
            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                notifyClosed();
            }
        });
    }

    // —— JSON-RPC ——
    private void send(JSONObject msg) {
        if (ws != null) ws.send(msg.toString());
    }

    private void request(String method, JSONObject params, RespCb cb) {
        int id = nextId++;
        if (cb != null) pending.put(id, cb);
        try {
            JSONObject m = new JSONObject();
            m.put("jsonrpc", "2.0");
            m.put("id", id);
            m.put("method", method);
            m.put("params", params == null ? new JSONObject() : params);
            send(m);
        } catch (JSONException e) { notifyError(e.getMessage()); }
    }

    private void notify(String method, JSONObject params) {
        try {
            JSONObject m = new JSONObject();
            m.put("jsonrpc", "2.0");
            m.put("method", method);
            m.put("params", params == null ? new JSONObject() : params);
            send(m);
        } catch (JSONException e) { notifyError(e.getMessage()); }
    }

    private void handleMessage(String text) {
        try {
            JSONObject m = new JSONObject(text);
            if (m.has("id") && (m.has("result") || m.has("error"))) {
                int id = m.getInt("id");
                RespCb cb = pending.remove(id);
                if (cb != null) {
                    if (m.has("error")) {
                        String msg = m.optJSONObject("error") != null
                            ? m.optJSONObject("error").optString("message", "error") : "error";
                        main.post(() -> cb.onError(msg));
                    } else {
                        JSONObject result = m.optJSONObject("result");
                        main.post(() -> cb.onResult(result == null ? new JSONObject() : result));
                    }
                }
            } else if (m.has("method")) {
                handleServerMessage(m.getString("method"), m.optJSONObject("params"));
            }
        } catch (JSONException e) { /* ignore non-json */ }
    }

    private void handleServerMessage(String method, JSONObject p) {
        if (p == null) p = new JSONObject();
        if ("session/update".equals(method)) {
            JSONObject update = p.optJSONObject("update");
            if (update == null) return;
            String kind = update.optString("sessionUpdate", "");
            if ("agent_message_chunk".equals(kind)) {
                String text = extractText(update);
                if (!text.isEmpty()) notifyChunk(text);
            } else if ("agent_message".equals(kind)) {
                String text = extractText(update);
                if (!text.isEmpty()) notifyAgentMessage(text);
            } else if ("tool_call".equals(kind)) {
                String name = update.optString("toolCallId", update.optString("title", "tool"));
                String args = update.optString("args", "");
                notifyToolCall(name, args);
            }
        } else if ("session/request_permission".equals(method)) {
            String reqId = p.optString("requestId", String.valueOf(System.currentTimeMillis()));
            String desc = p.optString("description", "Agent 请求权限");
            notifyPermission(reqId, desc);
            // 默认允许继续
            try {
                JSONObject resp = new JSONObject();
                resp.put("outcome", new JSONObject().put("outcome", "selected").put("optionId", "allow"));
                request("session/request_permission", resp, null);
            } catch (Exception ignored) {}
        }
    }

    private String extractText(JSONObject update) {
        JSONObject content = update.optJSONObject("content");
        if (content != null) {
            String t = content.optString("text", "");
            if (!t.isEmpty()) return t;
        }
        return update.optString("text", "");
    }

    // —— ACP 协议方法 ——
    private void doInitialize() {
        try {
            JSONObject params = new JSONObject();
            params.put("protocolVersion", 1);
            params.put("clientCapabilities", new JSONObject());
            request("initialize", params, new RespCb() {
                public void onResult(JSONObject result) { /* ready */ }
                public void onError(String msg) { notifyError("initialize 失败：" + msg); }
            });
        } catch (Exception e) { notifyError(e.getMessage()); }
    }

    public void newSession(String cwd) {
        // 先查已注册 workspace，取有效 path 做 cwd（传 "." 会被 SEMANTIC_REJECTED）
        request("_agnes/v1/workspace.list", new JSONObject(), new RespCb() {
            public void onResult(JSONObject result) {
                String path = null;
                JSONArray items = result.optJSONArray("items");
                if (items != null && items.length() > 0) {
                    JSONObject first = items.optJSONObject(0);
                    if (first != null) path = first.optString("path", null);
                }
                doNewSession(path != null ? path : (cwd == null ? "." : cwd));
            }
            public void onError(String msg) {
                doNewSession(cwd == null ? "." : cwd);
            }
        });
    }

    private void doNewSession(String cwd) {
        try {
            JSONObject params = new JSONObject();
            params.put("cwd", cwd);
            params.put("mcpServers", new JSONArray());
            request("session/new", params, new RespCb() {
                public void onResult(JSONObject result) {
                    sessionId = result.optString("sessionId", null);
                }
                public void onError(String msg) { notifyError("创建会话失败：" + msg); }
            });
        } catch (Exception e) { notifyError(e.getMessage()); }
    }

    public void prompt(String text) {
        if (sessionId == null) { notifyError("会话未就绪"); return; }
        try {
            JSONObject textBlock = new JSONObject();
            textBlock.put("type", "text");
            textBlock.put("text", text);
            JSONArray prompt = new JSONArray().put(textBlock);
            JSONObject params = new JSONObject();
            params.put("sessionId", sessionId);
            params.put("prompt", prompt);
            request("session/prompt", params, new RespCb() {
                public void onResult(JSONObject result) {
                    String stop = result.optString("stopReason", "end_turn");
                    notifyPromptDone(stop);
                }
                public void onError(String msg) { notifyError("发送失败：" + msg); }
            });
        } catch (Exception e) { notifyError(e.getMessage()); }
    }

    public void cancel() {
        if (sessionId == null) return;
        try {
            JSONObject params = new JSONObject();
            params.put("sessionId", sessionId);
            notify("session/cancel", params);
        } catch (Exception ignored) {}
    }

    public void close() {
        if (ws != null) { try { ws.close(1000, "bye"); } catch (Exception ignored) {} }
    }

    // —— 回调（主线程）——
    private void notifyConnected() { main.post(() -> { if (listener != null) listener.onConnected(); }); }
    private void notifyChunk(String t) { main.post(() -> { if (listener != null) listener.onMessageChunk(t); }); }
    private void notifyAgentMessage(String t) { main.post(() -> { if (listener != null) listener.onAgentMessage(t); }); }
    private void notifyToolCall(String n, String a) { main.post(() -> { if (listener != null) listener.onToolCall(n, a); }); }
    private void notifyPermission(String id, String d) { main.post(() -> { if (listener != null) listener.onPermissionRequest(id, d); }); }
    private void notifyPromptDone(String s) { main.post(() -> { if (listener != null) listener.onPromptDone(s); }); }
    private void notifyError(String m) { main.post(() -> { if (listener != null) listener.onError(m); }); }
    private void notifyClosed() { main.post(() -> { if (listener != null) listener.onClosed(); }); }
}
