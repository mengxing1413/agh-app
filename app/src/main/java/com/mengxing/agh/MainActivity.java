package com.mengxing.agh;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.HttpAuthHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends Activity {
    // 连接信息在 onCreate 里从「设置 → 连接」的当前配置载入
    private String START_URL = "";
    private String AUTH_USER = "";
    private String AUTH_PASS = "";
    public static final String VERSION = "0.3.5";

    private WebView web;
    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private View splash;
    private View offlineView;
    private View settingsFab;
    private boolean offline = false;
    private SharedPreferences prefs;
    private String prefTheme;

    // —— 配色跟随系统深色模式 ——
    private boolean isNight() {
        int m = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return m == Configuration.UI_MODE_NIGHT_YES;
    }

    private int cBgTop()  { return isNight() ? 0xFF0B1410 : 0xFFF6F7F9; }
    private int cBgBot()  { return isNight() ? 0xFF12281F : 0xFFEDF1EE; }
    private int cText()   { return isNight() ? 0xFFF2F7F4 : 0xFF111827; }
    private int cSub()    { return isNight() ? 0xFF9FB3A8 : 0xFF6B7280; }
    private int cAccent() { return isNight() ? 0xFF7C9F69 : 0xFF2F7D5B; }

    /** JS 桥：页面(polyfill)上报主题色，壳同步状态栏/导航栏 */
    public class NativeBridge {
        @android.webkit.JavascriptInterface
        public void setChrome(String bgHexOrRgb, String dark) {
            runOnUiThread(() -> applyChrome(bgHexOrRgb, "1".equals(dark)));
        }
    }

    private void applyChrome(String bg, boolean dark) {
        try {
            int c = Color.parseColor(normalizeColor(bg));
            boolean light = !dark;
            getWindow().setStatusBarColor(c);
            getWindow().setNavigationBarColor(c);
            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (light) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                decor.setSystemUiVisibility(flags);
            }
        } catch (Exception ignored) {}
    }

    private static String normalizeColor(String s) {
        if (s == null) return "#FFFFFF";
        s = s.trim();
        if (s.startsWith("rgb")) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\d+").matcher(s);
            int[] v = new int[3]; int i = 0;
            while (m.find() && i < 3) v[i++] = Integer.parseInt(m.group());
            return String.format("#%02X%02X%02X", v[0], v[1], v[2]);
        }
        if (s.startsWith("#") && s.length() == 4) {
            return "#" + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2)
                     + s.charAt(3) + s.charAt(3);
        }
        return s;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
            getResources().getDisplayMetrics());
    }

    private GradientDrawable brandBg() {
        return new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, new int[]{cBgTop(), cBgBot()});
    }

    /** 注入跟随系统的 color-scheme：让 AGH 网页随手机深色模式切换 */
    private void injectColorScheme() {
        String scheme = isNight() ? "dark" : "light";
        String js = "(function(){var id='yuno-scheme';var s=document.getElementById(id);"
            + "if(!s){s=document.createElement('style');s.id=id;document.head.appendChild(s);}"
            + "s.textContent='html{color-scheme:" + scheme + " !important;}';})();";
        try { web.evaluateJavascript(js, null); } catch (Exception ignored) {}
    }

    /** 原生闪屏（配色跟随系统） */
    private View buildSplash() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(brandBg());

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(120), dp(120));
        ip.gravity = Gravity.CENTER_HORIZONTAL;
        icon.setLayoutParams(ip);

        TextView title = new TextView(this);
        title.setText("YunoAgent");
        title.setTextColor(cText());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(18), 0, 0);

        TextView sub = new TextView(this);
        sub.setText("你的随身 AGH · Agent on the go");
        sub.setTextColor(cSub());
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(8), 0, 0);

        ProgressBar ring = new ProgressBar(this);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(36), dp(36));
        rp.gravity = Gravity.CENTER_HORIZONTAL;
        rp.topMargin = dp(28);
        ring.setLayoutParams(rp);
        ring.setIndeterminateTintList(
            android.content.res.ColorStateList.valueOf(cAccent()));

        center.addView(icon);
        center.addView(title);
        center.addView(sub);
        center.addView(ring);

        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.gravity = Gravity.CENTER;
        frame.addView(center, cp);

        TextView ver = new TextView(this);
        ver.setText("v" + VERSION);
        ver.setTextColor(cSub());
        ver.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        ver.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams vp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vp.gravity = Gravity.BOTTOM;
        vp.bottomMargin = dp(28);
        frame.addView(ver, vp);

        return frame;
    }

    /** 原生离线视图（配色跟随系统） */
    private View buildOffline() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackground(brandBg());
        root.setPadding(dp(32), dp(32), dp(32), dp(32));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(96), dp(96));
        ip.gravity = Gravity.CENTER_HORIZONTAL;
        icon.setLayoutParams(ip);

        TextView title = new TextView(this);
        title.setText("连不上 YunoAgent");
        title.setTextColor(cText());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(20), 0, 0);

        TextView desc = new TextView(this);
        desc.setText("手机上的 Tailscale 好像不在线。\n先打开 Tailscale（VPN 开关）再重试。");
        desc.setTextColor(cSub());
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        desc.setGravity(Gravity.CENTER);
        desc.setLineSpacing(0, 1.5f);
        desc.setPadding(dp(16), dp(12), dp(16), 0);

        Button retry = new Button(this);
        retry.setText("重新连接");
        retry.setTextColor(Color.WHITE);
        retry.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(24));
        bg.setColor(cAccent());
        retry.setBackground(bg);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(200), dp(48));
        bp.gravity = Gravity.CENTER_HORIZONTAL;
        bp.topMargin = dp(28);
        retry.setLayoutParams(bp);
        retry.setOnClickListener(v -> {
            offline = false;
            offlineView.setVisibility(View.GONE);
            swipe.setVisibility(View.VISIBLE);
            web.loadUrl(START_URL);
        });

        root.addView(icon);
        root.addView(title);
        root.addView(desc);
        root.addView(retry);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        root.setLayoutParams(lp);
        return root;
    }

    /** 右上角悬浮设置按钮 */
    private View buildSettingsFab() {
        TextView fab = new TextView(this);
        fab.setText("⚙");
        fab.setTextColor(cText());
        fab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        fab.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(isNight() ? 0x6614231C : 0x66FFFFFF);
        fab.setBackground(g);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(36), dp(36));
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.topMargin = dp(14);
        lp.rightMargin = dp(14);
        fab.setLayoutParams(lp);
        fab.setOnClickListener(v -> openSettings());
        return fab;
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    /** 左上角悬浮"原生对话"入口 */
    private View buildChatFab() {
        TextView fab = new TextView(this);
        fab.setText("💬");
        fab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        fab.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0x6614231C);
        fab.setBackground(g);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(36), dp(36));
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.topMargin = dp(14);
        lp.leftMargin = dp(14);
        fab.setLayoutParams(lp);
        fab.setOnClickListener(v ->
            startActivity(new Intent(this, ChatActivity.class)));
        return fab;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE);
        prefTheme = prefs.getString(SettingsActivity.KEY_THEME, SettingsActivity.THEME_SYSTEM);

        // 连接信息取自「设置 → 连接」中选定的配置
        com.mengxing.agh.data.ServerProfile prof =
            com.mengxing.agh.data.AppConfig.INSTANCE.activeProfile(this);
        START_URL = prof.webUrl();
        AUTH_USER = prof.getUser();
        AUTH_PASS = prof.getPass();

        getWindow().setStatusBarColor(cBgTop());
        getWindow().setNavigationBarColor(cBgTop());

        FrameLayout root = new FrameLayout(this);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        // 不强制深色，让 AGH 网页跟随系统深色模式
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            s.setForceDark(WebSettings.FORCE_DARK_AUTO);
        }
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);
        web.addJavascriptInterface(new NativeBridge(), "AGHNative");

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
        });

        web.setDownloadListener((url, userAgent, contentDisposition, mimetype, length) -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
            catch (Exception ignored) {}
        });

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler,
                                                  String host, String realm) {
                handler.proceed(AUTH_USER, AUTH_PASS);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String scheme = u.getScheme() == null ? "" : u.getScheme();
                String host = u.getHost() == null ? "" : u.getHost();
                if (scheme.equals("retry")) {
                    offline = false;
                    offlineView.setVisibility(View.GONE);
                    swipe.setVisibility(View.VISIBLE);
                    view.loadUrl(START_URL);
                    return true;
                }
                if (scheme.startsWith("http") && !host.endsWith("tail6125f3.ts.net")) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                    return true;
                }
                return false;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request.isForMainFrame()) showOffline();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                swipe.setRefreshing(false);
                if (!offline) {
                    splash.setVisibility(View.GONE);
                    offlineView.setVisibility(View.GONE);
                    swipe.setVisibility(View.VISIBLE);
                    injectColorScheme();
                }
            }
        });

        swipe = new SwipeRefreshLayout(this);
        swipe.addView(web, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        swipe.setOnRefreshListener(() -> web.reload());
        swipe.setVisibility(View.GONE);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        progress.setLayoutParams(pl);
        progress.getProgressDrawable().setColorFilter(
            cAccent(), android.graphics.PorterDuff.Mode.SRC_IN);
        progress.setVisibility(View.GONE);

        splash = buildSplash();
        offlineView = buildOffline();
        offlineView.setVisibility(View.GONE);
        settingsFab = buildSettingsFab();

        root.addView(swipe, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(offlineView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(splash, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(settingsFab);
        root.addView(buildChatFab());
        root.addView(progress, pl);
        setContentView(root);

        web.loadUrl(START_URL);
    }

    private void showOffline() {
        offline = true;
        swipe.setRefreshing(false);
        progress.setVisibility(View.GONE);
        splash.setVisibility(View.GONE);
        offlineView.setVisibility(View.VISIBLE);
        swipe.setVisibility(View.GONE);
    }

    @Override
    public void onBackPressed() {
        if (offline) {
            offline = false;
            offlineView.setVisibility(View.GONE);
            swipe.setVisibility(View.VISIBLE);
            web.loadUrl(START_URL);
        }
        else if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
