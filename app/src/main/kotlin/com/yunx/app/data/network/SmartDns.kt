/*
 * 吸析At - 智能 DNS 解析（系统 DNS + DoH 双路，防域名污染）。
 *
 * 背景：蓝奏云家族域名（wwbll.lanzoul.com / wwap.lanzoub.com 等）与
 * 蓝奏云优享版域名（www.ilanzou.com）在部分运营商网络存在 DNS 污染——
 * 系统解析返回假 IP，TCP 连接超时，应用表现为「网络错误」，而其他网盘一切正常
 * （真机复现：www.ilanzou.com 系统 DNS 返回 113.215.245.x 假 IP 不可连，
 *   DoH 解析真实 CDN 182.242.90.x 直连可用）。
 *
 * 策略：
 * - 每次解析同时走 系统DNS 与 DoH（阿里公共 DNS JSON API，IP 直连不依赖 DNS）；
 * - DoH 结果排前面（真实 CDN IP 优先），系统结果殿后兜底；OkHttp 在路由失败时
 *   会自动切换下一个地址（retryOnConnectionFailure），因此两路互为保险：
 *   · 系统 DNS 被污染 → 先连 DoH 真实 IP，成功；
 *   · DoH 不可达/被墙 → 退回系统 IP；
 * - 结果缓存 60 秒（TTL 上限同样 60s），DoH 查询 3s 超时静默失败；
 * - 仅 IPv4（A 记录）：国内移动网络 IPv6 路由质量参差，优先 v4 稳定性。
 */
package com.yunx.app.data.network

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object SmartDns : Dns {

    /** DoH 服务器（url 模板：阿里公共 DNS 主备 + 腾讯 DNSPod，均以 IP 直连不依赖 DNS）
     *  多服务商串联：任一可用即返回，最大化防污染/防劫持覆盖 */
    private val dohEndpoints = listOf(
        "https://223.5.5.5/resolve?name=%s&type=A",
        "https://223.6.6.6/resolve?name=%s&type=A",
        "https://119.29.29.29/dns-query?name=%s&type=1" // DNSPod（Google DoH JSON 同构）
    )

    /** 缓存：60s 内直接复用（域名解析不是热路径，避免每次请求都打 DoH） */
    private val cache = ConcurrentHashMap<String, Cached>()
    private const val CACHE_MS = 60_000L

    private class Cached(val expiresAt: Long, val addresses: List<InetAddress>)

    /** bootstrap 客户端：连 DoH 服务器本身就是 IP 直连，无需 DNS，形成解析起点 */
    private val dohClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        // IP 字面量直接返回（不该也不会进 DoH）
        if (isLiteralIp(hostname)) return listOf(InetAddress.getByName(hostname))
        val hit = cache[hostname]
        if (hit != null && System.currentTimeMillis() < hit.expiresAt) return hit.addresses

        val system = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrDefault(emptyList())
        val doh = resolveViaDoH(hostname)

        // DoH 优先（防污染），系统兜底；保序去重
        val merged = ArrayList<InetAddress>(doh.size + system.size)
        (doh.asSequence() + system.asSequence())
            .filter { it is java.net.Inet4Address }
            .distinctBy { it.hostAddress }
            .forEach { merged.add(it) }
        // DoH 与系统都没有 v4 → 放行系统 v6 结果（极端 IPv6-only 网络仍可用）
        if (merged.isEmpty()) merged.addAll(system)
        if (merged.isEmpty()) throw UnknownHostException("无法解析域名: $hostname")

        cache[hostname] = Cached(System.currentTimeMillis() + CACHE_MS, merged)
        return merged
    }

    /** DoH JSON API（阿里 /resolve?name=<host>&type=A；DNSPod /dns-query?name=<host>&type=1） */
    private fun resolveViaDoH(hostname: String): List<InetAddress> {
        for (template in dohEndpoints) {
            val body = runCatching {
                dohClient.newCall(
                    Request.Builder()
                        .url(template.format(hostname))
                        .header("Accept", "application/dns-json")
                        .get()
                        .build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    resp.body?.string()
                }
            }.getOrNull() ?: continue
            if (body.isNullOrBlank()) continue
            val answers = runCatching { JSONObject(body).optJSONArray("Answer") }.getOrNull() ?: continue
            val out = ArrayList<InetAddress>(answers.length())
            for (i in 0 until answers.length()) {
                val a = answers.optJSONObject(i) ?: continue
                if (a.optInt("type") != 1) continue // 仅 A 记录（CNAME 链中的 A 也在同一 Answer 数组）
                val ip = a.optString("data").trim()
                if (ip.isEmpty()) continue
                // 字面量 IP 构造，不触发任何解析
                runCatching { InetAddress.getByName(ip) }.getOrNull()?.let { out.add(it) }
            }
            if (out.isNotEmpty()) return out
        }
        return emptyList()
    }

    private fun isLiteralIp(host: String): Boolean {
        val h = host.removeSuffix(".").removePrefix("[").removeSuffix("]")
        return h.count { it == '.' } == 3 && h.all { it.isDigit() || it == '.' } ||
            h.contains(':') && h.none { it.isLetter() && it !in "abcdefABCDEF" }
    }
}
