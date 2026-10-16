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
        public final int lastSeq;          // 会话消息数（也用于 fork 的 at 边界）
        public final boolean archived;     // 是否已归档

        public SessionInfo(String sessionId, String title, String cwd, String createdAt,
                           int lastSeq, boolean archived) {
            this.sessionId = sessionId;
            this.title = title;
            this.cwd = cwd;
            this.createdAt = createdAt;
            this.lastSeq = lastSeq;
            this.archived = archived;
        }
    }

    /** session.list 回调。 */
    public interface SessionsCb {
        void onResult(List<SessionInfo> sessions, String next);
        void onError(String msg);
    }

    /** 可选模型（来自 _agnes/v1/apis.list 的 profile.models）。 */
    public static class ModelInfo {
        public final String id, route;
        public final int contextWindow;
        public ModelInfo(String id, String route, int contextWindow) {
            this.id = id; this.route = route; this.contextWindow = contextWindow;
        }
    }

    /** 模型列表回调。 */
    public interface ModelsCb {
        void onResult(List<ModelInfo> models);
        void onError(String msg);
    }

    /**
     * 会话用量快照（来自 _agnes/v1/session.projectUIOpening 的 result.timeline.usage / .yolo）。
     * ctxTokens/ctxWindow 为「当前上下文占用 / 模型窗口」，totals 为整段会话累计。
     */
    public static class UsageInfo {
        public final int ctxTokens, ctxWindow;
        public final boolean autoCompact;
        public final int input, output, cacheRead, cacheWrite, reasoning;
        public final long costUsdMicros;
        public final String modelId;
        public final boolean yolo;

        public UsageInfo(int ctxTokens, int ctxWindow, boolean autoCompact,
                         int input, int output, int cacheRead, int cacheWrite, int reasoning,
                         long costUsdMicros, String modelId, boolean yolo) {
            this.ctxTokens = ctxTokens; this.ctxWindow = ctxWindow; this.autoCompact = autoCompact;
            this.input = input; this.output = output;
            this.cacheRead = cacheRead; this.cacheWrite = cacheWrite; this.reasoning = reasoning;
            this.costUsdMicros = costUsdMicros; this.modelId = modelId; this.yolo = yolo;
        }
    }

    /** 用量回调。 */
    public interface UsageCb {
        void onResult(UsageInfo u);
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
    /** 用户选定的工作区路径（优先用作新会话 cwd）；null=用 AGH 首个工作区。 */
    private String preferredCwd;

    public void setPreferredCwd(String p) { this.preferredCwd = p; }
    public String getPreferredCwd() { return preferredCwd; }
    /** 当前会话使用的模型/预设名（尽力解析，可能为 null）。 */
    private volatile String modelName;
    /** profile 里的默认模型（会话未产出内容时兜底显示用）。 */
    private volatile String fallbackModel;

    // —— 重连状态 ——
    private String url, user, pass;
    private boolean manualClose = false;
    /** 连接代号：每次 openSocket 自增；旧 socket 的回调据此忽略，避免切换连接后重连串台。 */
    private int gen = 0;
    private int retryCount = 0;
    private Runnable reconnectTask;

    public interface RespCb { void onResult(JSONObject result); void onError(String msg); }

    /** 断线/重连时清掉悬挂的请求回调，避免调用方永远等待。 */
    private void clearPending(String msg) {
        if (pending.isEmpty()) return;
        List<RespCb> cbs = new ArrayList<>(pending.values());
        pending.clear();
        main.post(() -> { for (RespCb cb : cbs) cb.onError(msg); });
    }

    public AcpClient() {
        client = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // 长连接不超时
            .pingInterval(25, TimeUnit.SECONDS)
            .build();
    }

    public void setListener(Listener l) { this.listener = l; }
    public String getSessionId() { return sessionId; }
    public String getDefaultCwd() { return defaultCwd; }
    public String getModelName() { return modelName != null ? modelName : fallbackModel; }

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
        final int myGen = ++gen;
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
                if (myGen != gen) { try { webSocket.close(1000, "stale"); } catch (Exception e) {} return; }
                retryCount = 0;
                clearPending("连接中断，请重试");   // 旧连接上悬挂的请求永远不会回来，全部落错误
                notifyConnected();
                doInitialize();
            }
            @Override public void onMessage(WebSocket webSocket, String text) {
                if (myGen != gen) return;
                handleMessage(text);
            }
            @Override public void onFailure(WebSocket webSocket, Throwable t, Response r) {
                if (myGen != gen) return;   // 已切换连接，旧 socket 的失败忽略
                if (manualClose) notifyClosed(); else scheduleReconnect();
            }
            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                if (myGen != gen) return;   // 旧 socket 关闭不触发重连
                if (manualClose) notifyClosed(); else scheduleReconnect();
            }
        });
    }

    private void scheduleReconnect() {
        final int myGen = gen;
        // 指数退避：1,2,4,8,16,30,30…（秒）
        int delay = (int) Math.min(30, Math.pow(2, retryCount));
        retryCount++;
        notifyReconnecting("连接中断，第 " + retryCount + " 次重连（" + delay + "s 后）…");
        if (reconnectTask != null) main.removeCallbacks(reconnectTask);
        reconnectTask = () -> { if (!manualClose && myGen == gen) openSocket(); };
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

    /** 模型名回调（可能为 null）。 */
    public interface ModelCb { void onModel(String model); }

    /**
     * 拉取 AGH profile 里的默认模型名（本地档只配 agnes 系列）。
     * 取带 defaultSettings 的首个模型；没有则取列表第一个；解析不到回调 null。
     * 用途：会话尚无产出时，气泡下也能显示"当前模型"。
     */
    public void fetchProfileDefaultModel(ModelCb cb) {
        request("_agnes/v1/apis.list", new JSONObject(), new RespCb() {
            public void onResult(JSONObject result) {
                String m = parseProfileModel(result);
                if (m != null) fallbackModel = m;
                if (cb != null) cb.onModel(m);
            }
            public void onError(String msg) { if (cb != null) cb.onModel(null); }
        });
    }

    private String parseProfileModel(JSONObject result) {
        if (result == null) return null;
        JSONObject profile = result.optJSONObject("profile");
        if (profile == null) return null;
        JSONArray models = profile.optJSONArray("models");
        if (models == null || models.length() == 0) return null;
        String first = null;
        for (int i = 0; i < models.length(); i++) {
            JSONObject mo = models.optJSONObject(i);
            if (mo == null) continue;
            String id = mo.optString("id", "");
            if (id.isEmpty()) continue;
            if (first == null) first = id;
            if (mo.has("defaultSettings")) return id;   // 服务端标注的默认档
        }
        return first;
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
    public void listSessions(String cursor, SessionsCb cb) {
        try {
            JSONObject params = new JSONObject();
            params.put("limit", 100);
            if (cursor != null && !cursor.isEmpty()) params.put("cursor", cursor);
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
                                o.optString("createdAt", ""),
                                o.optInt("lastSeq", 0),
                                o.optBoolean("archived", false)));
                        }
                    }
                    String next = result.optString("next", "");
                    if (cb != null) cb.onResult(list, next);
                }
                public void onError(String msg) { if (cb != null) cb.onError(msg); }
            });
        } catch (Exception e) { if (cb != null) cb.onError(e.getMessage()); }
    }

    /** 不带 cursor 的首屏拉取。 */
    public void listSessions(SessionsCb cb) { listSessions(null, cb); }

    // ==================== AGH 配置（config.*）====================
    // 注意：密钥只提交不返回（服务端永不回传 apiKey），所以列表里看不到明文。

    /** 读当前配置快照（_agnes/v1/config.get → ConfigSnapshot）。 */
    public void configGet(RespCb cb) {
        request("_agnes/v1/config.get", new JSONObject(), cb);
    }

    /** 可用 Provider 列表（_agnes/v1/config.providers → {providers:[...]}）。 */
    public void configProviders(RespCb cb) {
        request("_agnes/v1/config.providers", new JSONObject(), cb);
    }

    /**
     * 保存模型配置（_agnes/v1/config.save）。
     * input 支持：providerId*、baseUrl、apiKey、model*、label、enabled、makeDefault、
     * accountId、expectedRevision、defaultSettings{thinking, contextWindow}。
     * 其中 defaultSettings.contextWindow 正是模型规格库要写进去的地方。
     */
    public void configSave(JSONObject input, RespCb cb) {
        request("_agnes/v1/config.save", input, cb);
    }

    /** 测试连通（_agnes/v1/config.test）：providerId*、baseUrl、apiKey、model、accountId。 */
    public void configTest(JSONObject input, RespCb cb) {
        request("_agnes/v1/config.test", input, cb);
    }

    // ==================== 工作区与皮肤（D 组）====================

    /** 添加工作区（_agnes/v1/workspace.add，params {path}）。 */
    public void addWorkspace(String path, RespCb cb) {
        try {
            JSONObject p = new JSONObject();
            p.put("path", path);
            request("_agnes/v1/workspace.add", p, cb);
        } catch (Exception e) {
            if (cb != null) cb.onError(e.getMessage());
        }
    }

    /**
     * 皮肤列表（_agnes/v1/skins.list → {revision, skins:[{id,name,packageName,cssUrl,...}]}）。
     * AGH 的皮肤由"包"贡献（SkinContribution：id/name/css/tokens），
     * tokens 是 {key:{light,dark}} 的设计令牌，可映射成原生主题色。
     * 未安装任何皮肤包时返回空列表，此时 App 用内置皮肤兜底。
     */
    public void skinsList(RespCb cb) {
        request("_agnes/v1/skins.list", new JSONObject(), cb);
    }

    /** 读单个皮肤（_agnes/v1/skins.read）。 */
    public void skinsRead(String id, RespCb cb) {
        try {
            JSONObject p = new JSONObject();
            p.put("id", id);
            request("_agnes/v1/skins.read", p, cb);
        } catch (Exception e) {
            if (cb != null) cb.onError(e.getMessage());
        }
    }

    // ==================== AGH 管理（D18-D22）====================
    // 技能 / MCP / 扩展 / 资源 / 包管理，对应网页版设置页的各分区。

    /** D18 技能：刷新技能列表（skills.refresh）。 */
    public void skillsRefresh(RespCb cb) {
        request("_agnes/v1/skills.refresh", new JSONObject(), cb);
    }

    /** D18 技能：信任某技能（skills.trust）。 */
    public void skillsTrust(String id, boolean trust, RespCb cb) {
        try {
            JSONObject p = new JSONObject();
            p.put("id", id);
            p.put("trust", trust);
            request("_agnes/v1/skills.trust", p, cb);
        } catch (Exception e) {
            if (cb != null) cb.onError(e.getMessage());
        }
    }

    /** D19 MCP：读 MCP 配置（mcp.read）。 */
    public void mcpRead(RespCb cb) {
        request("_agnes/v1/mcp.read", new JSONObject(), cb);
    }

    /** D19 MCP：测试连通（mcp.test）。 */
    public void mcpTest(String id, RespCb cb) {
        try {
            JSONObject p = new JSONObject();
            p.put("id", id);
            request("_agnes/v1/mcp.test", p, cb);
        } catch (Exception e) {
            if (cb != null) cb.onError(e.getMessage());
        }
    }

    /** D19 MCP：重连（mcp.reconnect）。 */
    public void mcpReconnect(String id, RespCb cb) {
        try {
            JSONObject p = new JSONObject();
            p.put("id", id);
            request("_agnes/v1/mcp.reconnect", p, cb);
        } catch (Exception e) {
            if (cb != null) cb.onError(e.getMessage());
        }
    }

    /** D21 资源：读资源（resources.read）。 */
    public void resourcesRead(RespCb cb) {
        request("_agnes/v1/resources.read", new JSONObject(), cb);
    }

    /** D21 资源：对账/刷新（resources.reconcile）。 */
    public void resourcesReconcile(RespCb cb) {
        request("_agnes/v1/resources.reconcile", new JSONObject(), cb);
    }

    /** D22 包管理：读已安装包（packages.read）。 */
    public void packagesRead(RespCb cb) {
        request("_agnes/v1/packages.read", new JSONObject(), cb);
    }

    /** D22 包管理：安装（packages.install）。 */
    public void packagesInstall(String spec, RespCb cb) {
        try {
            JSONObject p = new JSONObject();
            p.put("spec", spec);
            request("_agnes/v1/packages.install", p, cb);
        } catch (Exception e) {
            if (cb != null) cb.onError(e.getMessage());
        }
    }

    /** D22 包管理：卸载（packages.remove）。 */
    public void packagesRemove(String id, RespCb cb) {
        try {
            JSONObject p = new JSONObject();
            p.put("id", id);
            request("_agnes/v1/packages.remove", p, cb);
        } catch (Exception e) {
            if (cb != null) cb.onError(e.getMessage());
        }
    }

    /** D20 扩展：调用扩展服务（extension.call）。 */
    public void extensionCall(String id, JSONObject input, RespCb cb) {
        try {
            JSONObject p = new JSONObject();
            p.put("id", id);
            if (input != null) p.put("input", input);
            request("_agnes/v1/extension.call", p, cb);
        } catch (Exception e) {
            if (cb != null) cb.onError(e.getMessage());
        }
    }

    /** AGH 工作区条目（_agnes/v1/workspace.list 的 items）。 */
    public static class Workspace {
        public final String path, name;
        public Workspace(String path, String name) { this.path = path; this.name = name; }
    }

    public interface WorkspacesCb {
        void onResult(List<Workspace> workspaces);
        void onError(String msg);
    }

    /** 列出已注册工作区（_agnes/v1/workspace.list → items[{path,...}]）。 */
    public void listWorkspaces(final WorkspacesCb cb) {
        request("_agnes/v1/workspace.list", new JSONObject(), new RespCb() {
            public void onResult(JSONObject result) {
                List<Workspace> out = new ArrayList<>();
                JSONArray items = result.optJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject o = items.optJSONObject(i);
                        if (o == null) continue;
                        String path = o.optString("path", "");
                        if (path.isEmpty()) path = o.optString("cwd", "");
                        if (path.isEmpty()) continue;
                        String name = o.optString("name", "");
                        if (name.isEmpty()) {
                            int slash = path.lastIndexOf('/');
                            name = slash >= 0 ? path.substring(slash + 1) : path;
                        }
                        out.add(new Workspace(path, name));
                    }
                }
                if (cb != null) cb.onResult(out);
            }
            public void onError(String msg) { if (cb != null) cb.onError(msg); }
        });
    }

    // —— 模型 / 权限 / 用量（B 组）——

    /** 拉取可选模型列表（profile.models：id / route / contextWindow）。 */
    public void fetchModels(ModelsCb cb) {
        request("_agnes/v1/apis.list", new JSONObject(), new RespCb() {
            public void onResult(JSONObject result) {
                List<ModelInfo> list = new ArrayList<>();
                JSONObject profile = result.optJSONObject("profile");
                JSONArray arr = profile != null ? profile.optJSONArray("models") : null;
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o == null) continue;
                        String id = o.optString("id", "");
                        if (id.isEmpty()) continue;
                        int cw = o.optInt("contextWindow", 0);
                        if (cw <= 0) {
                            JSONObject ds = o.optJSONObject("defaultSettings");
                            if (ds != null) cw = ds.optInt("contextWindow", 0);
                        }
                        list.add(new ModelInfo(id, o.optString("route", ""), cw));
                    }
                }
                if (cb != null) cb.onResult(list);
            }
            public void onError(String msg) { if (cb != null) cb.onError(msg); }
        });
    }

    /**
     * 切换当前会话主模型（_agnes/v1/session.setModel）。
     * 实测 slot 固定传 "primary"；thinking/contextWindow 传 null 表示用模型默认。
     * result: EffectiveFromResult { effectiveFromSeq }。
     */
    public void setModel(String route, String model, RespCb cb) {
        if (sessionId == null) { if (cb != null) cb.onError("会话未就绪"); return; }
        try {
            JSONObject p = new JSONObject();
            p.put("sessionId", sessionId);
            p.put("slot", "primary");
            p.put("route", route);
            p.put("model", model);
            p.put("thinking", JSONObject.NULL);
            p.put("contextWindow", JSONObject.NULL);
            request("_agnes/v1/session.setModel", p, new RespCb() {
                public void onResult(JSONObject result) {
                    modelName = model;   // 同步本地缓存，气泡 meta 立即显示新模型
                    if (cb != null) cb.onResult(result);
                }
                public void onError(String msg) { if (cb != null) cb.onError(msg); }
            });
        } catch (Exception e) { if (cb != null) cb.onError(e.getMessage()); }
    }

    /** 切换会话「完全权限」（_agnes/v1/session.setYolo；enabled=true 即免逐次审批）。 */
    public void setYolo(boolean enabled, RespCb cb) {
        if (sessionId == null) { if (cb != null) cb.onError("会话未就绪"); return; }
        try {
            JSONObject p = new JSONObject();
            p.put("sessionId", sessionId);
            p.put("enabled", enabled);
            request("_agnes/v1/session.setYolo", p, cb);
        } catch (Exception e) { if (cb != null) cb.onError(e.getMessage()); }
    }

    /** 读取当前会话用量（projectUIOpening → timeline.usage / timeline.yolo）。 */
    public void fetchUsage(UsageCb cb) {
        if (sessionId == null) { if (cb != null) cb.onError("会话未就绪"); return; }
        try {
            JSONObject p = new JSONObject();
            p.put("sessionId", sessionId);
            request("_agnes/v1/session.projectUIOpening", p, new RespCb() {
                public void onResult(JSONObject result) {
                    UsageInfo u = parseUsage(result);
                    if (cb == null) return;
                    if (u != null) cb.onResult(u); else cb.onError("暂无用量数据");
                }
                public void onError(String msg) { if (cb != null) cb.onError(msg); }
            });
        } catch (Exception e) { if (cb != null) cb.onError(e.getMessage()); }
    }

    private UsageInfo parseUsage(JSONObject result) {
        if (result == null) return null;
        JSONObject tl = result.optJSONObject("timeline");
        if (tl == null) return null;
        JSONObject usage = tl.optJSONObject("usage");
        if (usage == null) return null;
        JSONObject totals = usage.optJSONObject("totals");
        JSONObject ctx = usage.optJSONObject("context");
        JSONObject model = usage.optJSONObject("model");
        JSONObject cost = usage.optJSONObject("cost");
        int in = totals != null ? totals.optInt("input", 0) : 0;
        int out = totals != null ? totals.optInt("output", 0) : 0;
        int cr = totals != null ? totals.optInt("cacheRead", 0) : 0;
        int cw = totals != null ? totals.optInt("cacheWrite", 0) : 0;
        int rs = totals != null ? totals.optInt("reasoning", 0) : 0;
        int ctxTokens = ctx != null ? ctx.optInt("tokens", 0) : 0;
        int ctxWindow = ctx != null ? ctx.optInt("window", 0) : 0;
        boolean auto = ctx != null && ctx.optBoolean("autoCompact", false);
        long usd = cost != null ? cost.optLong("usdMicros", 0) : 0;
        String mid = model != null ? model.optString("id", null) : null;
        boolean yolo = tl.optBoolean("yolo", false);
        return new UsageInfo(ctxTokens, ctxWindow, auto, in, out, cr, cw, rs, usd, mid, yolo);
    }

    /** 重命名会话（_agnes/v1/session.rename；title 长度 1~256）。 */
    public void renameSession(String sid, String title, RespCb cb) {
        try {
            JSONObject p = new JSONObject();
            p.put("sessionId", sid);
            p.put("title", title);
            request("_agnes/v1/session.rename", p, cb);
        } catch (Exception e) { if (cb != null) cb.onError(e.getMessage()); }
    }

    /** 归档 / 取消归档（_agnes/v1/session.archive）。 */
    public void archiveSession(String sid, boolean archived, RespCb cb) {
        try {
            JSONObject p = new JSONObject();
            p.put("sessionId", sid);
            p.put("archived", archived);
            request("_agnes/v1/session.archive", p, cb);
        } catch (Exception e) { if (cb != null) cb.onError(e.getMessage()); }
    }

    /**
     * 分叉会话（_agnes/v1/session.fork）：从 at 指定边界分出一个新会话。
     * 实测 at 必须是「已完成 turn/end」的 seq，传会话末尾的 lastSeq 即可成功。
     */
    public void forkSession(String sid, int at, RespCb cb) {
        try {
            JSONObject p = new JSONObject();
            p.put("sessionId", sid);
            p.put("at", at);
            request("_agnes/v1/session.fork", p, cb);
        } catch (Exception e) { if (cb != null) cb.onError(e.getMessage()); }
    }

    /**
     * 加载历史会话（ACP session/load）：服务端会通过 session/update 流式重放全部历史消息。
     * params: LoadSessionRequest { mcpServers*, cwd*, sessionId* }
     */
    public void loadSession(String sid, String cwd) { loadSession(sid, cwd, null, false); }
    public void loadSession(String sid, String cwd, RespCb cb) { loadSession(sid, cwd, cb, false); }

    /**
     * 加载历史会话（ACP session/load）：服务端会通过 session/update 流式重放全部历史消息。
     * params: LoadSessionRequest { mcpServers*, cwd*, sessionId* }
     * cb.onError 供调用方在【恢复上次会话】失败时回退到新建会话；
     * quiet=true 时不弹错误气泡（用于启动时静默恢复尝试）。
     */
    public void loadSession(String sid, String cwd, RespCb cb, boolean quiet) {
        if (sid == null || sid.isEmpty()) {
            if (!quiet) notifyError("会话 id 无效");
            if (cb != null) cb.onError("会话 id 无效");
            return;
        }
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
                    if (cb != null) cb.onResult(result);
                }
                public void onError(String msg) {
                    if (!quiet) notifyError("加载会话失败：" + msg);
                    if (cb != null) cb.onError(msg);
                }
            });
        } catch (Exception e) {
            if (!quiet) notifyError(e.getMessage());
            if (cb != null) cb.onError(e.getMessage());
        }
    }

    public void newSession(String cwd) { newSession(cwd, null); }

    /**
     * 新建会话。cb.onResult 在 sessionId 已更新后回调（此时可安全清屏/切换）；
     * cb.onError 时 sessionId 保持不变（仍连在原会话上）。
     */
    public void newSession(String cwd, RespCb cb) {
        // 先查已注册 workspace，取有效 path 做 cwd（传 "." 会被 SEMANTIC_REJECTED）
        request("_agnes/v1/workspace.list", new JSONObject(), new RespCb() {
            public void onResult(JSONObject result) {
                String path = null;
                if (preferredCwd != null && !preferredCwd.isEmpty()) {
                    path = preferredCwd;   // 用户选定的工作区优先
                } else {
                    JSONArray items = result.optJSONArray("items");
                    if (items != null && items.length() > 0) {
                        JSONObject first = items.optJSONObject(0);
                        if (first != null) path = first.optString("path", null);
                    }
                }
                if (path != null) defaultCwd = path;
                doNewSession(path != null ? path : (cwd == null ? "." : cwd), cb);
            }
            public void onError(String msg) {
                doNewSession(cwd == null ? "." : cwd, cb);
            }
        });
    }

    private void doNewSession(String cwd, RespCb cb) {
        try {
            JSONObject params = new JSONObject();
            params.put("cwd", cwd);
            params.put("mcpServers", new JSONArray());
            // ★ 必须带 _meta.sessionKey（随机 UUID）：否则 AGH 按 workspace 路径复用同一个
            // 固定会话 id，"新建会话"永远建不出独立会话。网页版 app.js 就是靠这个 key 新建的。
            JSONObject meta = new JSONObject();
            meta.put("sessionKey", java.util.UUID.randomUUID().toString());
            JSONObject _meta = new JSONObject();
            _meta.put("ai.agnes.harness", meta);
            params.put("_meta", _meta);
            request("session/new", params, new RespCb() {
                public void onResult(JSONObject result) {
                    sessionId = result.optString("sessionId", null);
                    modelName = parseModel(result);
                    if (cb != null) cb.onResult(result);
                }
                public void onError(String msg) {
                    if (cb != null) cb.onError(msg);
                    else notifyError("创建会话失败：" + msg);
                }
            });
        } catch (Exception e) {
            if (cb != null) cb.onError(e.getMessage());
            else notifyError(e.getMessage());
        }
    }

    public void prompt(String text) { prompt(text, null); }

    /**
     * 发送消息，可带附件（E 组）。
     * attachments 为额外的 ContentBlock，支持：
     *   {"type":"image","data":"<base64>","mimeType":"image/jpeg"}
     *   {"type":"file","data":"<base64>","mimeType":"application/pdf","name":"报告.pdf"}
     */
    public void prompt(String text, List<JSONObject> attachments) {
        if (sessionId == null) { notifyError("会话未就绪"); return; }
        try {
            JSONArray prompt = new JSONArray();
            if (text != null && !text.isEmpty()) {
                JSONObject textBlock = new JSONObject();
                textBlock.put("type", "text");
                textBlock.put("text", text);
                prompt.put(textBlock);
            }
            if (attachments != null) {
                for (JSONObject b : attachments) prompt.put(b);
            }
            if (prompt.length() == 0) { notifyError("内容为空"); return; }
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
