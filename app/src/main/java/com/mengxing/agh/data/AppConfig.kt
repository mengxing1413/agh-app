package com.mengxing.agh.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 一套服务器连接配置（AGH 接入点）。
 *
 * [url] 为 WebSocket 地址（wss://host:port/__agh_ws）；
 * [webUrl] 由它推导出「工作台网页」地址（wss→https / ws→http，去掉 /__agh_ws）。
 */
data class ServerProfile(
    val id: String,
    val name: String,
    val url: String,
    val user: String,
    val pass: String
) {
    fun webUrl(): String {
        return try {
            val s = url.trim()
            val https = s.startsWith("wss://") || s.startsWith("https://")
            val rest = s
                .removePrefix("wss://").removePrefix("ws://")
                .removePrefix("https://").removePrefix("http://")
            val hostPort = rest.substringBefore("/")
            if (hostPort.isEmpty()) url else (if (https) "https://" else "http://") + hostPort + "/"
        } catch (e: Exception) {
            url
        }
    }
}

/**
 * 全局配置：多套服务器连接 + 外观偏好。
 * 存储于 SharedPreferences("yuno_config")，连接以 JSON 数组持久化。
 */
object AppConfig {
    private const val PREFS = "yuno_config"
    private const val KEY_PROFILES = "profiles_json"
    private const val KEY_ACTIVE = "active_id"
    const val KEY_THEME = "theme"        // system / light / dark
    const val KEY_SKIN = "skin"          // green / blue / orange / violet / slate
    const val KEY_FONT = "font_scale"    // small / normal / large / xlarge
    const val KEY_GLASS = "glass_enabled" // 玻璃质感开关（叠加在当前主题色之上，见 YunoTheme）

    /** 内置默认连接（默认不丢，老用户升级后仍可用）。 */
    const val DEFAULT_URL =
        "wss://i-69df90b938a3bc3d5392c8bb-1.tail6125f3.ts.net:8443/__agh_ws"
    const val DEFAULT_USER = "agh"
    const val DEFAULT_PASS = "11ad3c6798ca7e19"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun defaultProfile() =
        ServerProfile("default", "默认服务器", DEFAULT_URL, DEFAULT_USER, DEFAULT_PASS)

    fun loadProfiles(ctx: Context): MutableList<ServerProfile> {
        val raw = prefs(ctx).getString(KEY_PROFILES, null)
        val out = mutableListOf<ServerProfile>()
        if (!raw.isNullOrEmpty()) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    out.add(
                        ServerProfile(
                            o.optString("id", UUID.randomUUID().toString()),
                            o.optString("name", ""),
                            o.optString("url", ""),
                            o.optString("user", ""),
                            o.optString("pass", "")
                        )
                    )
                }
            } catch (_: Exception) {
            }
        }
        if (out.isEmpty()) out.add(defaultProfile())
        return out
    }

    fun saveProfiles(ctx: Context, list: List<ServerProfile>) {
        val arr = JSONArray()
        for (p in list) {
            val o = JSONObject()
            o.put("id", p.id)
            o.put("name", p.name)
            o.put("url", p.url)
            o.put("user", p.user)
            o.put("pass", p.pass)
            arr.put(o)
        }
        prefs(ctx).edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    fun activeId(ctx: Context): String {
        val saved = prefs(ctx).getString(KEY_ACTIVE, null)
        val list = loadProfiles(ctx)
        if (saved != null && list.any { it.id == saved }) return saved
        return list.first().id
    }

    fun setActiveId(ctx: Context, id: String) {
        prefs(ctx).edit().putString(KEY_ACTIVE, id).apply()
    }

    fun activeProfile(ctx: Context): ServerProfile {
        val id = activeId(ctx)
        val list = loadProfiles(ctx)
        return list.firstOrNull { it.id == id } ?: list.first()
    }

    fun upsertProfile(ctx: Context, p: ServerProfile) {
        val list = loadProfiles(ctx)
        val idx = list.indexOfFirst { it.id == p.id }
        if (idx >= 0) list[idx] = p else list.add(p)
        saveProfiles(ctx, list)
    }

    fun deleteProfile(ctx: Context, id: String) {
        val list = loadProfiles(ctx)
        list.removeAll { it.id == id }
        if (list.isEmpty()) list.add(defaultProfile())
        saveProfiles(ctx, list)
        setActiveId(ctx, list.first().id)
    }

    // ---- 外观偏好 ----
    fun theme(ctx: Context): String =
        prefs(ctx).getString(KEY_THEME, "system") ?: "system"

    fun skin(ctx: Context): String =
        prefs(ctx).getString(KEY_SKIN, "green") ?: "green"

    fun fontScale(ctx: Context): String =
        prefs(ctx).getString(KEY_FONT, "normal") ?: "normal"

    /** 玻璃质感开关：开启后 YunoTheme 会给面板加玻璃材质 + 按主题色铺渐变壁纸。 */
    fun glass(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_GLASS, false)

    fun setGlass(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_GLASS, on).apply()
    }

    fun setString(ctx: Context, key: String, value: String) {
        prefs(ctx).edit().putString(key, value).apply()
    }
}
