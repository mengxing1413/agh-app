package com.mengxing.agh

/**
 * 后端能力声明（能力契约）。
 *
 * 设计要点：UI 只依赖这张表决定"显示什么、隐藏什么、灰掉什么"，
 * 不再按后端类型写 if/else，也不再出现"点了才发现这个后端不支持"的情况。
 *
 * 以后每接一个新后端，只需要在 [of] 里补一张能力表，UI 自动适配。
 */
data class AgentCaps(
    val text: Boolean = true,       // 发文本（所有后端都有）
    val image: Boolean = false,     // 发图片
    val file: Boolean = false,      // 发文件
    val history: Boolean = false,   // 历史回放
    val sessions: Boolean = false,  // 会话列表 / 切换
    val models: Boolean = false,    // 切换模型
    val usage: Boolean = false,     // 用量统计
    val approval: Boolean = false,  // 权限审批
    val workspace: Boolean = false, // 工作区切换
    val yolo: Boolean = false       // 免审批开关
) {
    companion object {
        /** Agnes Harness：能力全开。 */
        val AGH = AgentCaps(
            text = true, image = true, file = true, history = true, sessions = true,
            models = true, usage = true, approval = true, workspace = true, yolo = true
        )

        /** Nanobot：只有纯文本对话（WS 协议无附件/历史/模型接口）。 */
        val NANOBOT = AgentCaps(text = true)

        /** Hermes / Pi / OpenClaw：尚未适配，先给最小能力。 */
        val UNSUPPORTED = AgentCaps(text = false)

        fun of(type: String?, url: String?): AgentCaps =
            when {
                looksNano(type, url) -> NANOBOT
                type.equals("AGH", true) || type.equals("Agnes Harness", true) -> AGH
                url.isNullOrBlank() -> UNSUPPORTED
                url.contains("/__agh_ws", true) -> AGH
                else -> when (type?.lowercase()) {
                    null, "", "agh" -> AGH
                    else -> UNSUPPORTED
                }
            }

        /** 后端类型判定（nanobot 的地址/名称特征）。 */
        fun looksNano(type: String?, url: String?): Boolean =
            type.equals("Nanobot", true) ||
                !url.isNullOrBlank() &&
                (url.contains("/luwu/", true) || url.contains(":8765") || url.contains("47653"))

        /** 展示用名称：AGH 用全称。 */
        fun displayType(type: String?): String = when (type?.lowercase()) {
            "agh", "agnes harness", null, "" -> "Agnes Harness"
            "nanobot" -> "Nanobot"
            "hermes" -> "Hermes"
            "pi" -> "Pi"
            "openclaw" -> "OpenClaw"
            else -> type ?: "Unknown"
        }
    }
}
