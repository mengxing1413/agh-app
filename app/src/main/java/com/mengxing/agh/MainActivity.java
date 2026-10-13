package com.mengxing.agh;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.HttpAuthHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private static final String START_URL =
        "https://i-69df90b938a3bc3d5392c8bb-1.tail6125f3.ts.net:8443/";
    private static final String AUTH_USER = "agh";
    private static final String AUTH_PASS = "11ad3c6798ca7e19";
    private WebView web;
    private boolean offline = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#0E1512"));
        }
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

        web.setWebChromeClient(new WebChromeClient());
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
        });

        setContentView(web);
        web.loadUrl(START_URL);
    }

    private void showOffline() {
        offline = true;
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
