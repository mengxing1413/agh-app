package com.mengxing.agh

import java.net.InetSocketAddress
import java.net.Socket

/**
 * 轻量端口探活：只做 TCP 握手，不跑任何后端协议。
 * 用于首页显示 Agent 的"真实在线状态"（协议各不相同，但端口通不通是统一的判断）。
 */
fun tcpProbe(url: String, timeoutMs: Int = 2500): Boolean {
    val u = url.trim()
    if (u.isEmpty()) return false
    val secure = u.startsWith("wss://") || u.startsWith("https://")
    val rest = u
        .removePrefix("wss://").removePrefix("ws://")
        .removePrefix("https://").removePrefix("http://")
    val hostPort = rest.substringBefore("/")
    if (hostPort.isEmpty()) return false
    val host = hostPort.substringBefore(":")
    val defaultPort = if (secure) 443 else 80
    val port = hostPort.substringAfter(":", "").toIntOrNull() ?: defaultPort
    if (host.isEmpty()) return false
    return try {
        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
        true
    } catch (e: Exception) {
        false
    }
}
