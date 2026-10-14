package com.mengxing.agh;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 原生对话页：完全原生 UI，通过 AcpClient 直连 AGH（WebSocket）。
 *
 * v0.0.8 新增：
 * - 工具轨迹卡片（tool_call / tool_call_update，状态角标 + 可展开原始输入输出）
 * - 审批交互卡片（session/request_permission，协议方向已修正）
 * - 断线自动重连提示
 * - 退到后台时 Agent 回复完发系统通知
 */
public class ChatActivity extends Activity implements AcpClient.Listener {

    private static final String WS_URL =
        "wss://i-69df90b938a3bc3d5392c8bb-1.tail6125f3.ts.net:8443/__agh_ws";
    private static final String AUTH_USER = "agh";
    private static final String AUTH_PASS = "11ad3c6798ca7e19";

    private static final String CHANNEL_ID = "yuno_reply";
    private static final int NOTIF_ID = 1;
    private static final int REQ_NOTIF_PERM = 1001;

    private AcpClient acp;
    private LinearLayout messages;
    private ScrollView scroll;
    private EditText input;
    private Button sendBtn;
    private TextView streamBubble;
    private StringBuilder streamBuf = new StringBuilder();
    private TextView statusView;

    // 工具卡片索引（toolCallId → 卡片）
    private final Map<String, ToolCard> toolCards = new HashMap<>();

    // 通知 / 前后台
    private boolean foreground = true;
    private String lastAgentText = "";

    // —— 跟随系统的配色 ——
    private boolean isNight() {
        int m = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return m == Configuration.UI_MODE_NIGHT_YES;
    }
    private int cBg()     { return isNight() ? 0xFF0B1410 : 0xFFF6F7F9; }
    private int cText()   { return isNight() ? 0xFFF2F7F4 : 0xFF111827; }
    private int cSub()    { return isNight() ? 0xFF9FB3A8 : 0xFF6B7280; }
    private int cAccent() { return isNight() ? 0xFF7C9F69 : 0xFF2F7D5B; }
    private int cWarn()   { return isNight() ? 0xFFE0A030 : 0xFFB7791F; }
    private int cCard()   { return isNight() ? 0xFF16241D : 0xFFFFFFFF; }
    private int cUserBubble()  { return isNight() ? 0xFF2F7D5B : 0xFF2F7D5B; }
    private int cAgentBubble() { return isNight() ? 0xFF14231C : 0xFFFFFFFF; }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
            getResources().getDisplayMetrics());
    }

    private TextView tv(String s, int sizeSp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(radiusDp));
        g.setColor(color);
        return g;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cBg());

        // —— 顶部标题栏 ——
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(14), dp(16), dp(14));
        top.setBackgroundColor(cBg());
        top.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView back = tv("‹", 28, cText(), false);
        back.setOnClickListener(v -> finish());
        top.addView(back);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tblp = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tblp.leftMargin = dp(10);
        titleBox.setLayoutParams(tblp);
        titleBox.addView(tv("YunoAgent", 18, cText(), true));
        statusView = tv("连接中…", 11, cSub(), false);
        titleBox.addView(statusView);
        top.addView(titleBox);

        Button workbenchBtn = new Button(this);
        workbenchBtn.setText("🖥");
        workbenchBtn.setTextColor(cText());
        workbenchBtn.setBackgroundColor(Color.TRANSPARENT);
        workbenchBtn.setOnClickListener(v ->
            startActivity(new Intent(this, MainActivity.class)));
        top.addView(workbenchBtn);

        Button settingsBtn = new Button(this);
        settingsBtn.setText("⚙");
        settingsBtn.setTextColor(cText());
        settingsBtn.setBackgroundColor(Color.TRANSPARENT);
        settingsBtn.setOnClickListener(v ->
            startActivity(new Intent(this, SettingsActivity.class)));
        top.addView(settingsBtn);

        // —— 消息列表 ——
        scroll = new ScrollView(this);
        scroll.setBackgroundColor(cBg());
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(dp(16), dp(12), dp(16), dp(12));
        scroll.addView(messages);

        // —— 底部输入 ——
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(12), dp(8), dp(12), dp(8));
        bottom.setBackgroundColor(cBg());

        input = new EditText(this);
        input.setHint("给 YunoAgent 发消息…");
        input.setHintTextColor(cSub());
        input.setTextColor(cText());
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        input.setSingleLine(false);
        input.setMaxLines(4);
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable ib = new GradientDrawable();
        ib.setCornerRadius(dp(20));
        ib.setColor(cAgentBubble());
        ib.setStroke(dp(1), cSub());
        input.setBackground(ib);
        input.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        sendBtn = new Button(this);
        sendBtn.setText("发送");
        sendBtn.setTextColor(Color.WHITE);
        sendBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        GradientDrawable sb = new GradientDrawable();
        sb.setCornerRadius(dp(18));
        sb.setColor(cAccent());
        sendBtn.setBackground(sb);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(64), dp(40));
        blp.leftMargin = dp(8);
        sendBtn.setLayoutParams(blp);
        sendBtn.setOnClickListener(v -> doSend());

        bottom.addView(input);
        bottom.addView(sendBtn);

        root.addView(top);
        root.addView(scroll);
        root.addView(bottom);
        setContentView(root);

        // 欢迎气泡
        addAgentBubble("你好，我是 YunoAgent 🐹\n你的随身 AGH，有什么想聊的？");

        // 通知渠道 + 权限
        ensureChannel();
        requestNotifPerm();

        // 连接 AGH
        acp = new AcpClient();
        acp.setListener(this);
        acp.connect(WS_URL, AUTH_USER, AUTH_PASS);
    }

    private void doSend() {
        String text = input.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;
        addUserBubble(text);
        input.setText("");
        streamBuf.setLength(0);
        lastAgentText = "";
        acp.prompt(text);
    }

    // —— 气泡 ——
    private void addUserBubble(String text) {
        LinearLayout wrap = bubbleWrap(Gravity.END);
        wrap.addView(makeBubble(text, cUserBubble(), Color.WHITE));
        messages.addView(wrap);
        scrollToBottom();
    }

    private void addAgentBubble(String text) {
        LinearLayout wrap = bubbleWrap(Gravity.START);
        wrap.addView(makeBubble(text, cAgentBubble(), cText()));
        messages.addView(wrap);
        scrollToBottom();
    }

    private LinearLayout bubbleWrap(int gravity) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(gravity);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        wrap.setLayoutParams(lp);
        return wrap;
    }

    private TextView makeBubble(String text, int bg, int textColor) {
        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextColor(textColor);
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        bubble.setLineSpacing(dp(3), 1);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setBackground(rounded(bg, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(8); lp.rightMargin = dp(8);
        bubble.setLayoutParams(lp);
        return bubble;
    }

    private void scrollToBottom() {
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    // ===================== 工具轨迹卡片 =====================

    private static class ToolCard {
        LinearLayout card;
        TextView header;
        TextView body;
        boolean expanded = false;
        String title = "", kind = "", status = "", rawInput = "", rawOutput = "";
    }

    private static String statusIcon(String status) {
        if (status == null) return "•";
        switch (status) {
            case "completed": return "✓";
            case "failed":    return "✗";
            case "in_progress": return "⏳";
            case "pending":   return "⏳";
            default:          return "•";
        }
    }

    private int statusColor(String status) {
        if (status == null) return cSub();
        switch (status) {
            case "completed": return cAccent();
            case "failed":    return isNight() ? 0xFFE06A6A : 0xFFC0392B;
            case "in_progress": return cWarn();
            case "pending":   return cWarn();
            default:          return cSub();
        }
    }

    private String toolKindIcon(String kind) {
        if (kind == null) return "🔧";
        switch (kind) {
            case "read":    return "📖";
            case "edit":    return "✏️";
            case "delete":  return "🗑";
            case "move":    return "📦";
            case "search":  return "🔍";
            case "execute": return "▶️";
            case "think":   return "💭";
            case "fetch":   return "🌐";
            default:        return "🔧";
        }
    }

    private void onToolEvent(String toolCallId, String title, String kind, String status,
                             String rawInput, String rawOutput, boolean isUpdate) {
        String key = TextUtils.isEmpty(toolCallId) ? ("t:" + title) : toolCallId;
        ToolCard card = toolCards.get(key);
        if (card == null) {
            card = buildToolCard();
            toolCards.put(key, card);
            LinearLayout wrap = bubbleWrap(Gravity.START);
            wrap.addView(card.card);
            messages.addView(wrap);
        }
        // 合并更新：仅在字段非空时覆盖（tool_call_update 是增量）
        if (!TextUtils.isEmpty(title)) card.title = title;
        if (!TextUtils.isEmpty(kind)) card.kind = kind;
        if (!TextUtils.isEmpty(status)) card.status = status;
        if (!TextUtils.isEmpty(rawInput)) card.rawInput = rawInput;
        if (!TextUtils.isEmpty(rawOutput)) card.rawOutput = rawOutput;
        refreshToolCard(card);
        scrollToBottom();
    }

    private ToolCard buildToolCard() {
        ToolCard c = new ToolCard();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable g = rounded(cCard(), 12);
        g.setStroke(dp(1), isNight() ? 0xFF24352B : 0xFFE2E6EA);
        card.setBackground(g);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = dp(8); clp.rightMargin = dp(8);
        card.setLayoutParams(clp);

        c.header = tv("", 13, cText(), true);
        card.addView(c.header);

        c.body = tv("", 12, cSub(), false);
        c.body.setPadding(0, dp(6), 0, 0);
        c.body.setVisibility(View.GONE);
        card.addView(c.body);

        card.setOnClickListener(v -> {
            c.expanded = !c.expanded;
            c.body.setVisibility(c.expanded ? View.VISIBLE : View.GONE);
            scrollToBottom();
        });
        c.card = card;
        return c;
    }

    private void refreshToolCard(ToolCard c) {
        String title = TextUtils.isEmpty(c.title) ? "工具调用" : c.title;
        c.header.setText(toolKindIcon(c.kind) + " " + statusIcon(c.status) + "  " + title);
        c.header.setTextColor(statusColor(c.status));

        StringBuilder b = new StringBuilder();
        b.append("状态：").append(TextUtils.isEmpty(c.status) ? "—" : c.status);
        if (!TextUtils.isEmpty(c.kind)) b.append("　·　类型：").append(c.kind);
        if (!TextUtils.isEmpty(c.rawInput)) b.append("\n\n参数：\n").append(c.rawInput);
        if (!TextUtils.isEmpty(c.rawOutput)) b.append("\n\n结果：\n").append(c.rawOutput);
        c.body.setText(b.toString());

        boolean hasDetail = !TextUtils.isEmpty(c.rawInput) || !TextUtils.isEmpty(c.rawOutput);
        c.header.setText(c.header.getText() + (hasDetail ? (c.expanded ? "  ▾" : "  ▸") : ""));
    }

    // ===================== 审批卡片 =====================

    private static String optionLabel(AcpClient.PermOption o) {
        if (!TextUtils.isEmpty(o.name)) return o.name;
        switch (o.kind) {
            case "allow_once":    return "本次允许";
            case "allow_always":  return "总是允许";
            case "reject_once":   return "拒绝";
            case "reject_always": return "总是拒绝";
            default:              return TextUtils.isEmpty(o.optionId) ? "选择" : o.optionId;
        }
    }

    private boolean isAllow(AcpClient.PermOption o) {
        return o.kind != null && o.kind.startsWith("allow");
    }

    private void makePermissionCard(Object reqId, String title, List<AcpClient.PermOption> options) {
        LinearLayout wrap = bubbleWrap(Gravity.START);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable g = rounded(cCard(), 14);
        g.setStroke(dp(2), cWarn());
        card.setBackground(g);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = dp(8); clp.rightMargin = dp(8);
        card.setLayoutParams(clp);

        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.bottomMargin = dp(10);
        TextView t = tv("⚠️ 需要授权：" + title, 14, cText(), true);
        t.setLayoutParams(tlp);
        card.addView(t);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        if (options == null || options.isEmpty()) {
            // 协议兜底：无选项时给一个取消
            Button b = new Button(this);
            b.setText("取消");
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            b.setTextColor(Color.WHITE);
            b.setBackground(rounded(isNight() ? 0xFF3A3A3A : 0xFF6B7280, 16));
            b.setOnClickListener(v -> {
                acp.cancelPermission(reqId);
                lockPermissionRow(btnRow, "已取消");
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
            lp.rightMargin = dp(8);
            b.setLayoutParams(lp);
            btnRow.addView(b);
        } else {
            for (AcpClient.PermOption o : options) {
                Button b = new Button(this);
                b.setText(optionLabel(o));
                b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                b.setTextColor(Color.WHITE);
                b.setBackground(rounded(isAllow(o) ? cAccent()
                    : (isNight() ? 0xFF3A3A3A : 0xFF6B7280), 16));
                b.setAllCaps(false);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
                lp.rightMargin = dp(8);
                b.setLayoutParams(lp);
                final AcpClient.PermOption opt = o;
                b.setOnClickListener(v -> {
                    acp.respondPermission(reqId, opt.optionId);
                    lockPermissionRow(btnRow, "已选择：" + optionLabel(opt));
                });
                btnRow.addView(b);
            }
        }
        card.addView(btnRow);
        wrap.addView(card);
        messages.addView(wrap);
        scrollToBottom();
    }

    private void lockPermissionRow(LinearLayout row, String note) {
        for (int i = 0; i < row.getChildCount(); i++) {
            row.getChildAt(i).setEnabled(false);
            row.getChildAt(i).setAlpha(0.5f);
        }
        TextView done = tv("✓ " + note, 12, cSub(), false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(6);
        done.setLayoutParams(lp);
        row.addView(done);
    }

    // ===================== 通知 =====================

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Agent 回复", NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("YunoAgent 在后台时收到新回复");
                nm.createNotificationChannel(ch);
            }
        }
    }

    private void requestNotifPerm() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIF_PERM);
            }
        }
    }

    private void notifyReply(String text) {
        if (foreground) return;
        if (TextUtils.isEmpty(text)) text = "Agent 已回复";
        String brief = text.length() > 120 ? text.substring(0, 120) + "…" : text;
        try {
            Intent i = new Intent(this, ChatActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getActivity(this, 0, i, flags);

            Notification.Builder nb = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
            Notification n = nb
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle("YunoAgent")
                .setContentText(brief)
                .setStyle(new Notification.BigTextStyle().bigText(brief))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();

            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIF_ID, n);
        } catch (Exception ignored) {}
    }

    private void clearNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NOTIF_ID);
        } catch (Exception ignored) {}
    }

    @Override protected void onResume() {
        super.onResume();
        foreground = true;
        clearNotification();
    }

    @Override protected void onPause() {
        super.onPause();
        foreground = false;
    }

    // ===================== AcpClient.Listener =====================

    @Override public void onConnected() {
        statusView.setText("已连接");
        statusView.setTextColor(cAccent());
        toolCards.clear(); // 重连后是新会话，重置工具卡片索引
        acp.newSession(".");
    }

    @Override public void onMessageChunk(String text) {
        if (streamBubble == null) {
            LinearLayout wrap = bubbleWrap(Gravity.START);
            streamBubble = makeBubble("", cAgentBubble(), cText());
            wrap.addView(streamBubble);
            messages.addView(wrap);
        }
        streamBuf.append(text);
        lastAgentText += text;
        streamBubble.setText(streamBuf.toString());
        scrollToBottom();
    }

    @Override public void onAgentMessage(String text) {
        if (streamBubble != null) {
            streamBubble.setText(text);
            streamBubble = null;
            streamBuf.setLength(0);
        } else {
            addAgentBubble(text);
        }
        if (TextUtils.isEmpty(lastAgentText)) lastAgentText = text;
        scrollToBottom();
    }

    @Override public void onToolCall(String toolCallId, String title, String kind, String status,
                                     String rawInput, String rawOutput) {
        onToolEvent(toolCallId, title, kind, status, rawInput, rawOutput, false);
    }

    @Override public void onToolCallUpdate(String toolCallId, String title, String kind, String status,
                                           String rawInput, String rawOutput) {
        onToolEvent(toolCallId, title, kind, status, rawInput, rawOutput, true);
    }

    @Override public void onPermissionRequest(Object reqId, String title, List<AcpClient.PermOption> options) {
        makePermissionCard(reqId, title, options);
    }

    @Override public void onPromptDone(String stopReason) {
        streamBubble = null;
        streamBuf.setLength(0);
        notifyReply(lastAgentText);
    }

    @Override public void onError(String msg) {
        addAgentBubble("❌ " + msg);
    }

    @Override public void onClosed() {
        statusView.setText("已断开");
        statusView.setTextColor(cSub());
        addAgentBubble("🔌 连接已断开");
    }

    @Override public void onReconnecting(String msg) {
        statusView.setText(msg);
        statusView.setTextColor(cWarn());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (acp != null) acp.close();
    }
}
