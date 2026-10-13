package com.mengxing.agh;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends Activity {
    private static final String START_URL =
        "https://i-69df90b938a3bc3d5392c8bb-1.tail6125f3.ts.net:8443/";
    private static final String AUTH_USER = "agh";
    private static final String AUTH_PASS = "11ad3c6798ca7e19";

    private WebView web;
    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private boolean offline = false;

    /** JS 桥：页面(polyfill)上报主题色，壳同步状态栏/导航栏，消除"浏览器感" */
    public class NativeBridge {
        @android.webkit.JavascriptInterface
        public void setChrome(String bgHexOrRgb, String dark) {
            runOnUiThread(() -> applyChrome(bgHexOrRgb, "1".equals(dark)));
        }
    }

    private void applyChrome(String bg, boolean dark) {
        try {
            int c = Color.parseColor(normalizeColor(bg));
            boolean light = !dark; // 浅色页面 → 深色状态栏图标
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.WHITE);
        }

        FrameLayout root = new FrameLayout(this);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
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
                if (scheme.equals("retry")) { offline = false; view.loadUrl(START_URL); return true; }
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
            }
        });

        swipe = new SwipeRefreshLayout(this);
        swipe.addView(web, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        swipe.setOnRefreshListener(() -> web.reload());

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        progress.setLayoutParams(pl);
        progress.getProgressDrawable().setColorFilter(
            Color.parseColor("#2F7D5B"), android.graphics.PorterDuff.Mode.SRC_IN);
        progress.setVisibility(View.GONE);

        root.addView(swipe, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(progress, pl);
        setContentView(root);
        web.loadUrl(START_URL);
    }

    private void showOffline() {
        offline = true;
        swipe.setRefreshing(false);
        progress.setVisibility(View.GONE);
        web.loadDataWithBaseURL(null,
            "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<style>body{background:#0E1512;color:#E8EFEA;font-family:sans-serif;display:flex;"
            + "flex-direction:column;justify-content:center;align-items:center;height:100vh;margin:0}"
            + "h2{font-size:20px}p{color:#9FB3A8;font-size:14px;line-height:1.6;text-align:center;padding:0 24px}"
            + "a{display:inline-block;margin-top:16px;padding:10px 28px;border-radius:24px;"
            + "background:#2F7D5B;color:#fff;text-decoration:none;font-size:15px}</style></head><body>"
            + "<h2>连不上 AGH</h2>"
            + "<p>手机上的 Tailscale 好像不在线。<br/>先打开 Tailscale（VPN 开关）再重试。</p>"
            + "<a href='retry://reload'>重新连接</a></body></html>",
            "text/html", "utf-8", null);
    }

    @Override
    public void onBackPressed() {
        if (offline) { offline = false; web.loadUrl(START_URL); }
        else if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
