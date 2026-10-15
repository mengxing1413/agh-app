package com.mengxing.agh.data

/**
 * 内置模型规格库。
 *
 * 背景：AGH 的 `config.save` 只接受 providerId/baseUrl/apiKey/model 等字段，
 * 而 OpenAI 兼容端点的 `/v1/models` 也**不返回**上下文窗口/价格——所以加模型时
 * 系统无从得知规格，只能靠人填（历史上全被写成了 200000，导致"每个模型都是 200K"）。
 *
 * 这里内置一份常用型号的规格表：添加模型时按 id 自动匹配，把 contextWindow 一起写进
 * `defaultSettings.contextWindow`，App 状态条的「已用 / 窗口」才会准。
 * 匹配不到的型号可手填，填完即入本地库，下次就认识了。
 *
 * 注：以下数值为常见档位的参考值，请以厂商文档为准，可在添加模型时手动修改。
 */
data class ModelSpec(
    val id: String,
    val name: String,
    val ctxWindow: Int,       // 上下文窗口（token）
    val inputPrice: Double,   // 每百万 token 输入价（元），0 = 免费或未知
    val outputPrice: Double,  // 每百万 token 输出价（元）
    val reasoning: Boolean,   // 是否推理/思考模型
    val note: String = ""
)

object ModelCatalog {

    /** 内置规格：常用型号 + 常见档位。 */
    val SPECS: List<ModelSpec> = listOf(
        // ---- Agnes（AGH 当前默认厂商）----
        ModelSpec("agnes-3.0-flash", "Agnes 3.0 Flash", 200000, 0.0, 0.0, false, "AGH 默认模型"),
        ModelSpec("agnes-2.5-pro", "Agnes 2.5 Pro", 200000, 0.0, 0.0, true),
        ModelSpec("agnes-2.5-pro-alpha", "Agnes 2.5 Pro Alpha", 200000, 0.0, 0.0, true),
        ModelSpec("agnes-2.5-pro-beta", "Agnes 2.5 Pro Beta", 200000, 0.0, 0.0, true),
        ModelSpec("agnes-2.5-flash", "Agnes 2.5 Flash", 200000, 0.0, 0.0, false),

        // ---- DeepSeek ----
        ModelSpec("deepseek-v4-flash", "DeepSeek V4 Flash", 128000, 0.5, 2.0, false, "快、便宜"),
        ModelSpec("deepseek-v4", "DeepSeek V4", 128000, 2.0, 8.0, false),
        ModelSpec("deepseek-reasoner", "DeepSeek Reasoner", 128000, 4.0, 16.0, true, "深度思考"),
        ModelSpec("deepseek-chat", "DeepSeek Chat", 128000, 2.0, 8.0, false),

        // ---- 智谱 GLM ----
        ModelSpec("glm-4.6v-flash", "GLM-4.6V Flash", 128000, 0.0, 0.0, false, "视觉模型"),
        ModelSpec("glm-4.6", "GLM-4.6", 128000, 0.8, 2.0, false),

        // ---- 月之暗面 Kimi ----
        ModelSpec("kimi-k2.5", "Kimi K2.5", 256000, 2.0, 8.0, false, "长上下文"),

        // ---- 通义千问 ----
        ModelSpec("qwen3-max", "Qwen3 Max", 128000, 2.4, 9.6, false),
        ModelSpec("qwen3-turbo", "Qwen3 Turbo", 1000000, 0.3, 1.2, false, "百万上下文"),

        // ---- 常见通用档位（手动填时可参考）----
        ModelSpec("64k-class", "通用 64K 档", 65536, 0.0, 0.0, false, "档位参考"),
        ModelSpec("128k-class", "通用 128K 档", 131072, 0.0, 0.0, false, "档位参考"),
        ModelSpec("200k-class", "通用 200K 档", 200000, 0.0, 0.0, false, "档位参考"),
        ModelSpec("1m-class", "通用 1M 档", 1000000, 0.0, 0.0, false, "档位参考")
    )

    /**
     * 查规格：先精确匹配 id，再按前缀/家族匹配。
     * 例：`agnes-3.0-flash-thinking` 会命中 `agnes-3.0-flash`。
     */
    fun lookup(id: String): ModelSpec? {
        val norm = id.trim().lowercase()
        if (norm.isEmpty()) return null
        SPECS.firstOrNull { it.id.equals(norm, true) }?.let { return it }
        // 家族前缀：取最接近的一条（前缀最长者优先）
        return SPECS
            .filter { norm.startsWith(it.id.lowercase()) }
            .maxByOrNull { it.id.length }
    }

    /** 可选型号 id（输入提示用）。 */
    fun knownIds(): List<String> = SPECS.map { it.id }

    /** 用户手填过的型号，落本地库，下次自动识别。 */
    private const val PREFS = "yuno_model_catalog"
    private const val KEY_EXTRA = "extra_specs"

    /** 读用户自填的规格（JSON 数组），合并到内置库。 */
    fun all(ctx: android.content.Context): List<ModelSpec> {
        val extra = try {
            val raw = ctx.getSharedPreferences(PREFS, 0).getString(KEY_EXTRA, null) ?: "[]"
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                ModelSpec(
                    o.optString("id", ""), o.optString("name", ""),
                    o.optInt("ctxWindow", 0), o.optDouble("inputPrice", 0.0),
                    o.optDouble("outputPrice", 0.0), o.optBoolean("reasoning", false),
                    o.optString("note", "自填")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
        return extra + SPECS
    }

    /** 保存一条自填规格（同 id 覆盖）。 */
    fun remember(ctx: android.content.Context, spec: ModelSpec) {
        try {
            val sp = ctx.getSharedPreferences(PREFS, 0)
            val raw = sp.getString(KEY_EXTRA, null) ?: "[]"
            val arr = org.json.JSONArray(raw)
            // 去掉同 id 的旧记录
            val kept = org.json.JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (!o.optString("id", "").equals(spec.id, true)) kept.put(o)
            }
            kept.put(org.json.JSONObject().apply {
                put("id", spec.id); put("name", spec.name); put("ctxWindow", spec.ctxWindow)
                put("inputPrice", spec.inputPrice); put("outputPrice", spec.outputPrice)
                put("reasoning", spec.reasoning); put("note", spec.note)
            })
            sp.edit().putString(KEY_EXTRA, kept.toString()).apply()
        } catch (e: Exception) {
            // 存失败不影响主流程
        }
    }

    /** 合并查找：先查内置，再查用户自填。 */
    fun find(ctx: android.content.Context, id: String): ModelSpec? =
        all(ctx).firstOrNull { it.id.equals(id.trim(), true) } ?: lookup(id)
}
