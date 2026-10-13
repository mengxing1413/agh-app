package com.mengxing.agh;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 原生设置页：参考 iOS 卡片式设置范式。
 * 配色在 App 内固定，不随系统深色模式变化。
 */
public class SettingsActivity extends Activity {

    public static final String PREFS = "yuno_prefs";
    public static final String KEY_THEME = "theme";
    public static final String THEME_DARK = "dark";   // 品牌深绿（默认）
    public static final String THEME_LIGHT = "light"; // 浅色

    private SharedPreferences prefs;
    private String theme;

    // 当前主题色
    private int bg, cardBg, border, textColor, subColor, selBg, selBorder, accent;

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
            getResources().getDisplayMetrics());
    }

    private void applyThemeColors() {
        if (THEME_LIGHT.equals(theme)) {
            bg = 0xFFF6F7F9; cardBg = 0xFFFFFFFF; border = 0xFFE5E7EB;
            textColor = 0xFF111827; subColor = 0xFF6B7280;
            selBg = 0xFFF0F0FF; selBorder = 0xFF2563EB; accent = 0xFF2563EB;
        } else {
            bg = 0xFF0B1410; cardBg = 0xFF14231C; border = 0xFF24382F;
            textColor = 0xFFF2F7F4; subColor = 0xFF9FB3A8;
            selBg = 0xFF173D2E; selBorder = 0xFF7C9F69; accent = 0xFF7C9F69;
        }
    }

    private TextView tv(String s, int sizeSp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /** 单选卡片：圆角边框 + 选中态 */
    private LinearLayout optionCard(String title, String desc, boolean selected, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(12));
        g.setColor(selected ? selBg : cardBg);
        g.setStroke(dp(selected ? 2 : 1), selected ? selBorder : border);
        row.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        row.setLayoutParams(lp);

        row.addView(tv(title, 16, textColor, true));
        if (desc != null && !desc.isEmpty()) {
            TextView d = tv(desc, 12, subColor, false);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dlp.topMargin = dp(4);
            d.setLayoutParams(dlp);
            row.addView(d);
        }
        row.setOnClickListener(click);
        return row;
    }

    /** 分组卡片（白/深底 + 圆角 + 分组标题） */
    private LinearLayout group(String title, View... children) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = dp(22);
        wrap.setLayoutParams(wlp);

        TextView label = tv(title, 13, subColor, false);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.leftMargin = dp(6); llp.bottomMargin = dp(8);
        label.setLayoutParams(llp);
        wrap.addView(label);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(16));
        g.setColor(cardBg);
        box.setBackground(g);
        for (View c : children) box.addView(c);
        wrap.addView(box);
        return wrap;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        theme = prefs.getString(KEY_THEME, THEME_DARK);
        applyThemeColors();

        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(12), dp(20), dp(32));

        // 顶部：标题 + 关闭
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        top.setLayoutParams(tlp);

        TextView title = tv("设置", 22, textColor, true);
        top.addView(title);

        View spacer = new View(this);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, 0);
        slp.weight = 1;
        spacer.setLayoutParams(slp);
        top.addView(spacer);

        TextView close = tv("✕", 22, subColor, false);
        close.setPadding(dp(12), dp(6), dp(4), dp(6));
        close.setOnClickListener(v -> finish());
        top.addView(close);
        root.addView(top);

        // 功能入口（占位，真功能在 AGH 主界面）
        LinearLayout entryGrid = new LinearLayout(this);
        entryGrid.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.topMargin = dp(18);
        entryGrid.setLayoutParams(glp);
        String[] entries = {"模型与账户", "技能", "插件管理", "MCP"};
        for (String e : entries) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(12), dp(16), dp(12), dp(16));
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(14));
            g.setColor(cardBg);
            card.setBackground(g);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            clp.leftMargin = dp(6); clp.rightMargin = dp(6);
            card.setLayoutParams(clp);
            card.addView(tv(e, 13, textColor, true));
            card.setOnClickListener(v -> finish());
            entryGrid.addView(card);
        }
        root.addView(entryGrid);

        // 配色（固定，不随系统）
        root.addView(group("配色",
            optionCard("浅色", "固定浅色，不随系统深色模式变化",
                THEME_LIGHT.equals(theme), v -> setThemeAndRecreate(THEME_LIGHT)),
            optionCard("品牌深绿", "YunoAgent 默认配色，固定不变",
                THEME_DARK.equals(theme), v -> setThemeAndRecreate(THEME_DARK))
        ));

        // 皮肤
        root.addView(group("皮肤",
            optionCard("默认", "标准视觉", true, v -> {}),
            optionCard("极简", "敬请期待", false, v -> {})
        ));

        // 字号
        root.addView(group("字号",
            optionCard("标准", "", true, v -> {}),
            optionCard("大", "敬请期待", false, v -> {})
        ));

        // 关于
        LinearLayout about = group("关于",
            optionCard("YunoAgent", "v" + MainActivity.VERSION + " · 你的随身 AGH", false, v -> {})
        );
        root.addView(about);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void setThemeAndRecreate(String newTheme) {
        theme = newTheme;
        prefs.edit().putString(KEY_THEME, newTheme).apply();
        recreate();
    }
}
