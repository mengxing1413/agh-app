package com.mengxing.agh;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * 原生 ACP（Agent Client Protocol）客户端：WebSocket + JSON-RPC 直连 AGH。
 *
 * v0.0.8 变更：
 * 1) 修正 session/request_permission 方向——它是 s2c request（服务端下发带 id 的请求），
 *    客户端必须用【同一个 id】回 response，而不是再发一个新请求。
 * 2) tool_call 全量字段（toolCallId/title/kind/status/rawInput/rawOutput）+ tool_call_update。
 * 3) 断线指数退避自动重连（1s→2s→4s…封顶 30s）。
 */
public class AcpClient {

    /** 审批选项（PermissionOption）。 */
    public static class PermOption {
        public final String optionId, name, kind;
        public PermOption(String optionId, String name, String kind) {
            this.optionId = optionId;
            this.name = name;
            this.kind = kind;
        }
    }

    /** 会话元信息（SessionMeta，来自 _agnes/v1/session.list）。 */
    public static class SessionInfo {
        public final String sessionId, title, cwd, createdAt;
        public SessionInfo(String sessionId, String title, String cwd, String createdAt) {
            this.sessionId = sessionId;
            this.title = title;
            this.cwd = cwd;
            this.createdAt = createdAt;
        }
    }

    /** session.list 回调。 */
    public interface SessionsCb {
        void onResult(List<SessionInfo> sessions, String next);
        void onError(String msg);
    }

    public interface Listener {
        void onConnected();
        void onMessageChunk(String text);
        void onAgentMessage(String text);
        /** 新工具调用（tool_call）。 */
        void onToolCall(String toolCallId, String title, String kind, String status,
                        String rawInput, String rawOutput);
        /** 工具调用状态/内容更新（tool_call_update）。 */
        void onToolCallUpdate(String toolCallId, String title, String kind, String status,
                              String rawInput, String rawOutput);
        /** 权限请求：reqId 是服务端请求的 JSON-RPC id，回包必须原样带回。 */
        void onPermissionRequest(Object reqId, String title, List<PermOption> options);
        /** 历史回放：用户消息分片（user_message_chunk）。 */
        void onUserMessage(String text);
        /** 会话加载完成（session/load 返回）。 */
        void onSessionLoaded(String sessionId);
        void onPromptDone(String stopReason);
        void onError(String msg);
        void onClosed();
        /** 断线，正在自动重连。 */
        void onReconnecting(String msg);
    }

    private final OkHttpClient client;
    private final Handler main = new Handler(Looper.getMainLooper());
    private WebSocket ws;
    private Listener listener;
    private int nextId = 1;
    private final Map<Integer, RespCb> pending = new HashMap<>();
    private String sessionId;
    /** 最近一次 workspace.list 得到的有效 cwd，供 session/load 回退使用。 */
    private String defaultCwd;
    /** 当前会话使用的模型/预设名（尽力解析，可能为 null）。 */
    private volatile String modelName;

    // —— 重连状态 ——
    private String url, user, pass;
    private boolean manualClose = false;
    private int retryCount = 0;
    private Runnable reconnectTask;

    public interface RespCb { void onResult(JSONObject result); void onError(String msg); }

    public AcpClient() {
        client = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // 长连接不超时
            .pingInterval(25, TimeUnit.SECONDS)
            .build();
    }

    public void setListener(Listener l) { this.listener = l; }
    public String getSessionId() { return sessionId; }
    public String getModelName() { return modelName; }

    // —— 连接 / 重连 ——
    public void connect(String url, String user, String pass) {
        this.url = url;
        this.user = user;
        this.pass = pass;
        this.manualClose = false;
        this.retryCount = 0;
        openSocket();
    }

    private void openSocket() {
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
                retryCount = 0;
                notifyConnected();
                doInitialize();
            }
            @Override public void onMessage(WebSocket webSocket, String text) {
                handleMessage(text);
            }
            @Override public void onFailure(WebSocket webSocket, Throwable t, Response r) {
                if (manualClose) notifyClosed(); else scheduleReconnect();
            }
            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                if (manualClose) notifyClosed(); else scheduleReconnect();
            }
        });
    }

    private void scheduleReconnect() {
        // 指数退避：1,2,4,8,16,30,30…（秒）
        int delay = (int) Math.min(30, Math.pow(2, retryCount));
        retryCount++;
        notifyReconnecting("连接中断，第 " + retryCount + " 次重连（" + delay + "s 后）…");
        if (reconnectTask != null) main.removeCallbacks(reconnectTask);
        reconnectTask = () -> { if (!manualClose) openSocket(); };
        main.postDelayed(reconnectTask, delay * 1000L);
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
                // —— 服务端对本客户端请求的响应 ——
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
                // —— 服务端主动下发：notification（无 id）或 request（有 id）——
                handleServerMessage(m.getString("method"), m.optJSONObject("params"), m.opt("id"));
            }
        } catch (JSONException e) { /* ignore non-json */ }
    }

    private void handleServerMessage(String method, JSONObject p, Object rpcId) {
        if (p == null) p = new JSONObject();
        if ("session/update".equals(method)) {
            JSONObject u = p.optJSONObject("update");
            if (u == null) return;
            String kind = u.optString("sessionUpdate", "");
            if ("user_message_chunk".equals(kind)) {
                String text = extractText(u);
                if (!text.isEmpty()) notifyUserMessage(text);
            } else if ("agent_message_chunk".equals(kind)) {
                String text = extractText(u);
                if (!text.isEmpty()) notifyChunk(text);
            } else if ("agent_message".equals(kind)) {
                String text = extractText(u);
                if (!text.isEmpty()) notifyAgentMessage(text);
            } else if ("tool_call".equals(kind)) {
                notifyToolCall(
                    u.optString("toolCallId", ""),
                    u.optString("title", ""),
                    u.optString("kind", ""),
                    u.optString("status", ""),
                    toStr(u.opt("rawInput")),
                    toStr(u.opt("rawOutput")));
            } else if ("tool_call_update".equals(kind)) {
                notifyToolCallUpdate(
                    u.optString("toolCallId", ""),
                    u.optString("title", ""),
                    u.optString("kind", ""),
                    u.optString("status", ""),
                    toStr(u.opt("rawInput")),
                    toStr(u.opt("rawOutput")));
            }
            // 其余：agent_thought_chunk / plan / available_commands_update / current_mode_update 暂不展示
        } else if ("session/request_permission".equals(method)) {
            JSONObject toolCall = p.optJSONObject("toolCall");
            String title = (toolCall != null) ? toolCall.optString("title", "工具操作") : "工具操作";
            List<PermOption> opts = new ArrayList<>();
            JSONArray arr = p.optJSONArray("options");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    opts.add(new PermOption(
                        o.optString("optionId", ""),
                        o.optString("name", ""),
                        o.optString("kind", "")));
                }
            }
            notifyPermission(rpcId, title, opts);
        }
    }

    /**
     * 回应权限请求（关键修正）：用服务端请求的【同一个 id】回 response。
     * 报文：{"jsonrpc":"2.0","id":<reqId>,"result":{"outcome":{"outcome":"selected","optionId":"..."}}}
     */
    public void respondPermission(Object reqId, String optionId) {
        try {
            JSONObject outcome = new JSONObject();
            outcome.put("outcome", "selected");
            outcome.put("optionId", optionId);
            sendPermissionResult(reqId, outcome);
        } catch (JSONException e) { notifyError(e.getMessage()); }
    }

    /** 取消权限请求：outcome = cancelled。 */
    public void cancelPermission(Object reqId) {
        try {
            JSONObject outcome = new JSONObject();
            outcome.put("outcome", "cancelled");
            sendPermissionResult(reqId, outcome);
        } catch (JSONException e) { notifyError(e.getMessage()); }
    }

    private void sendPermissionResult(Object reqId, JSONObject outcome) {
        try {
            JSONObject result = new JSONObject();
            result.put("outcome", outcome);
            JSONObject m = new JSONObject();
            m.put("jsonrpc", "2.0");
            m.put("id", reqId == null ? 0 : reqId);
            m.put("result", result);
            send(m);
        } catch (JSONException e) { notifyError(e.getMessage()); }
    }

    /** 从 session/new|load 的返回里尽力解析当前模型/预设名；解析不到返回 null。 */
    private String parseModel(JSONObject result) {
        if (result == null) return null;
        String m = result.optString("model", "");
        if (!m.isEmpty()) return m;
        JSONArray opts = result.optJSONArray("configOptions");
        if (opts != null) {
            for (int i = 0; i < opts.length(); i++) {
                JSONObject o = opts.optJSONObject(i);
                if (o == null) continue;
                String tag = (o.optString("id", "") + o.optString("name", "")).toLowerCase();
                if (tag.contains("model")) {
                    String v = o.optString("currentValue", "");
                    if (v.isEmpty()) v = o.optString("currentValueId", "");
                    if (v.isEmpty()) v = o.optString("currentValueLabel", "");
                    if (!v.isEmpty()) return v;
                }
            }
        }
        return null;
    }

    private String extractText(JSONObject update) {
        JSONObject content = update.optJSONObject("content");
        if (content != null) {
            String t = content.optString("text", "");
            if (!t.isEmpty()) return t;
        }
        return update.optString("text", "");
    }

    private String toStr(Object o) {
        if (o == null || o == JSONObject.NULL) return "";
        if (o instanceof String) return (String) o;
        try {
            if (o instanceof JSONObject) return ((JSONObject) o).toString(2);
            if (o instanceof JSONArray) return ((JSONArray) o).toString(2);
        } catch (Exception ignored) {}
        return o.toString();
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

    /**
     * 列出历史会话（_agnes/v1/session.list）。
     * params: SessionListParams { q?, cursor?, limit? }（additionalProperties:false，只传合法字段）
     * result: PageSessionMeta { items:[SessionMeta], next? }
     */
    public void listSessions(SessionsCb cb) {
        try {
            JSONObject params = new JSONObject();
            params.put("limit", 100);
            request("_agnes/v1/session.list", params, new RespCb() {
                public void onResult(JSONObject result) {
                    List<SessionInfo> list = new ArrayList<>();
                    JSONArray items = result.optJSONArray("items");
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject o = items.optJSONObject(i);
                            if (o == null) continue;
                            list.add(new SessionInfo(
                                o.optString("sessionId", ""),
                                o.optString("title", ""),
                                o.optString("cwd", ""),
                                o.optString("createdAt", "")));
                        }
                    }
                    String next = result.optString("next", "");
                    if (cb != null) cb.onResult(list, next);
                }
                public void onError(String msg) { if (cb != null) cb.onError(msg); }
            });
        } catch (Exception e) { if (cb != null) cb.onError(e.getMessage()); }
    }

    /**
     * 加载历史会话（ACP session/load）：服务端会通过 session/update 流式重放全部历史消息。
     * params: LoadSessionRequest { mcpServers*, cwd*, sessionId* }
     */
    public void loadSession(String sid, String cwd) {
        if (sid == null || sid.isEmpty()) { notifyError("会话 id 无效"); return; }
        try {
            String useCwd = (cwd != null && !cwd.isEmpty())
                ? cwd : (defaultCwd != null ? defaultCwd : ".");
            JSONObject params = new JSONObject();
            params.put("sessionId", sid);
            params.put("cwd", useCwd);
            params.put("mcpServers", new JSONArray());
            request("session/load", params, new RespCb() {
                public void onResult(JSONObject result) {
                    sessionId = sid;   // 后续 prompt 打到该会话
                    modelName = parseModel(result);
                    notifySessionLoaded(sid);
                }
                public void onError(String msg) { notifyError("加载会话失败：" + msg); }
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
                if (path != null) defaultCwd = path;
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
                    modelName = parseModel(result);
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
        manualClose = true;
        if (reconnectTask != null) main.removeCallbacks(reconnectTask);
        if (ws != null) { try { ws.close(1000, "bye"); } catch (Exception ignored) {} }
    }

    // —— 回调（主线程）——
    private void notifyConnected() { main.post(() -> { if (listener != null) listener.onConnected(); }); }
    private void notifyChunk(String t) { main.post(() -> { if (listener != null) listener.onMessageChunk(t); }); }
    private void notifyAgentMessage(String t) { main.post(() -> { if (listener != null) listener.onAgentMessage(t); }); }
    private void notifyToolCall(String id, String ti, String k, String s, String in, String out) {
        main.post(() -> { if (listener != null) listener.onToolCall(id, ti, k, s, in, out); });
    }
    private void notifyToolCallUpdate(String id, String ti, String k, String s, String in, String out) {
        main.post(() -> { if (listener != null) listener.onToolCallUpdate(id, ti, k, s, in, out); });
    }
    private void notifyPermission(Object id, String ti, List<PermOption> opts) {
        main.post(() -> { if (listener != null) listener.onPermissionRequest(id, ti, opts); });
    }
    private void notifyUserMessage(String t) { main.post(() -> { if (listener != null) listener.onUserMessage(t); }); }
    private void notifySessionLoaded(String sid) { main.post(() -> { if (listener != null) listener.onSessionLoaded(sid); }); }
    private void notifyPromptDone(String s) { main.post(() -> { if (listener != null) listener.onPromptDone(s); }); }
    private void notifyError(String m) { main.post(() -> { if (listener != null) listener.onError(m); }); }
    private void notifyClosed() { main.post(() -> { if (listener != null) listener.onClosed(); }); }
    private void notifyReconnecting(String m) { main.post(() -> { if (listener != null) listener.onReconnecting(m); }); }
}
