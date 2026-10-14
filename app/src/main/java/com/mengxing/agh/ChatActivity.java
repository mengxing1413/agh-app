package com.mengxing.agh;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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

/**
 * 原生对话页：完全原生 UI，通过 AcpClient 直连 AGH（WebSocket），
 * 消息气泡 + 输入框 + 流式输出。不依赖 WebView。
 */
public class ChatActivity extends Activity implements AcpClient.Listener {

    private static final String WS_URL =
        "wss://i-69df90b938a3bc3d5392c8bb-1.tail6125f3.ts.net:8443/__agh_ws";
    private static final String AUTH_USER = "agh";
    private static final String AUTH_PASS = "11ad3c6798ca7e19";

    private AcpClient acp;
    private LinearLayout messages;
    private ScrollView scroll;
    private EditText input;
    private Button sendBtn;
    private TextView streamBubble;
    private StringBuilder streamBuf = new StringBuilder();
    private TextView statusView;

    // —— 跟随系统的配色 ——
    private boolean isNight() {
        int m = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return m == Configuration.UI_MODE_NIGHT_YES;
    }
    private int cBg()     { return isNight() ? 0xFF0B1410 : 0xFFF6F7F9; }
    private int cText()   { return isNight() ? 0xFFF2F7F4 : 0xFF111827; }
    private int cSub()    { return isNight() ? 0xFF9FB3A8 : 0xFF6B7280; }
    private int cAccent() { return isNight() ? 0xFF7C9F69 : 0xFF2F7D5B; }
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
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        top.setLayoutParams(tlp);

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
            startActivity(new android.content.Intent(this, MainActivity.class)));
        top.addView(workbenchBtn);

        Button settingsBtn = new Button(this);
        settingsBtn.setText("⚙");
        settingsBtn.setTextColor(cText());
        settingsBtn.setBackgroundColor(Color.TRANSPARENT);
        settingsBtn.setOnClickListener(v ->
            startActivity(new android.content.Intent(this, SettingsActivity.class)));
        top.addView(settingsBtn);

        // —— 消息列表 ——
        scroll = new ScrollView(this);
        scroll.setBackgroundColor(cBg());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        scroll.setLayoutParams(slp);
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
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        input.setLayoutParams(ilp);

        sendBtn = new Button(this);
        sendBtn.setText("发送");
        sendBtn.setTextColor(Color.WHITE);
        sendBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        GradientDrawable sb = new GradientDrawable();
        sb.setCornerRadius(dp(18));
        sb.setColor(cAccent());
        sendBtn.setBackground(sb);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
            dp(64), dp(40));
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
        acp.prompt(text);
    }

    // —— 气泡 ——
    private void addUserBubble(String text) {
        LinearLayout wrap = bubbleWrap(Gravity.END);
        TextView bubble = makeBubble(text, cUserBubble(), Color.WHITE);
        wrap.addView(bubble);
        messages.addView(wrap);
        scrollToBottom();
    }

    private void addAgentBubble(String text) {
        LinearLayout wrap = bubbleWrap(Gravity.START);
        TextView bubble = makeBubble(text, cAgentBubble(), cText());
        wrap.addView(bubble);
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
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(16));
        g.setColor(bg);
        bubble.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(8); lp.rightMargin = dp(8);
        bubble.setLayoutParams(lp);
        return bubble;
    }

    private void scrollToBottom() {
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    // —— AcpClient.Listener（流式）——
    @Override public void onConnected() {
        statusView.setText("已连接");
        statusView.setTextColor(cAccent());
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
        scrollToBottom();
    }

    @Override public void onToolCall(String name, String args) {
        addAgentBubble("🔧 调用工具：" + name);
    }

    @Override public void onPermissionRequest(String reqId, String desc) {
        addAgentBubble("⚠️ 权限请求：" + desc + "（已自动允许）");
    }

    @Override public void onPromptDone(String stopReason) {
        streamBubble = null;
        streamBuf.setLength(0);
    }

    @Override public void onError(String msg) {
        addAgentBubble("❌ " + msg);
    }

    @Override public void onClosed() {
        addAgentBubble("🔌 连接已断开");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (acp != null) acp.close();
    }
}
