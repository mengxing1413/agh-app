package com.mengxing.agh.data

import android.content.Context
import org.json.JSONArray

/**
 * 「常用模型」勾选存储 —— 模型管理页勾选 → 推送给对话页小下拉。
 *
 * 为什么存手机本地：AGH 服务端没有"常用/收藏"字段（网页版也看不到同一套），
 * 用户已确认先本地、够用；勾选按**模型 id**记录（跨账号 id 通常唯一，如 agnes-3.0-flash）。
 * 模型下架只影响列表展示，勾选不丢（真删了在管理页取消即可）。
 */
object ModelFavs {
    private const val PREFS = "yuno_model_favs"
    private const val KEY = "fav_ids"

    /** 已勾选的模型 id 集合（小写规范化）。 */
    fun load(ctx: Context): Set<String> {
        return try {
            val raw = ctx.getSharedPreferences(PREFS, 0).getString(KEY, null) ?: "[]"
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i -> arr.optString(i, null)?.lowercase() }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    /** 切换勾选，返回切换后是否为常用。 */
    fun toggle(ctx: Context, modelId: String): Boolean {
        val id = modelId.trim().lowercase()
        val cur = load(ctx).toMutableSet()
        val now = if (cur.contains(id)) { cur.remove(id); false } else { cur.add(id); true }
        save(ctx, cur)
        return now
    }

    private fun save(ctx: Context, set: Set<String>) {
        try {
            val arr = JSONArray()
            set.forEach { arr.put(it) }
            ctx.getSharedPreferences(PREFS, 0).edit().putString(KEY, arr.toString()).apply()
        } catch (e: Exception) {
            // 存失败不影响主流程
        }
    }
}
