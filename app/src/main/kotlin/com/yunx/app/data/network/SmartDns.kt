package com.yunx.app.data.network

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

object SmartDns : Dns {

    private val dohEndpoints = listOf(
        "https://223.5.5.5/resolve?name=%s&type=A",
        "https://223.6.6.6/resolve?name=%s&type=A",
        "https://119.29.29.29/dns-query?name=%s&type=1"
    )

    private val dohExecutor: ExecutorService = Executors.newFixedThreadPool(3) { r ->
        Thread(r, "smart-dns-doh").apply { isDaemon = true }
    }

    private val cache = ConcurrentHashMap<String, Cached>()
    private const val CACHE_MS = 60_000L

    private val negativeCache = ConcurrentHashMap<String, Long>()

    private class Cached(val expiresAt: Long, val addresses: List<InetAddress>)

    private val dohClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        if (isLiteralIp(hostname)) return listOf(InetAddress.getByName(hostname))
        val hit = cache[hostname]
        if (hit != null && System.currentTimeMillis() < hit.expiresAt) return hit.addresses

        val dohFutures = submitDoh(hostname)
        val system = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrDefault(emptyList())
        val doh = awaitDoh(hostname, dohFutures)

        val merged = ArrayList<InetAddress>(doh.size + system.size)
        (doh.asSequence() + system.asSequence())
            .filter { it is java.net.Inet4Address }
            .distinctBy { it.hostAddress }
            .forEach { merged.add(it) }
        if (merged.isEmpty()) merged.addAll(system)
        if (merged.isEmpty()) throw UnknownHostException("无法解析域名: $hostname")

        cache[hostname] = Cached(System.currentTimeMillis() + CACHE_MS, merged)
        return merged
    }

    private fun submitDoh(hostname: String): List<Future<List<InetAddress>>> {
        if (isNegativeCached(hostname)) return emptyList()
        return dohEndpoints.map { template ->
            dohExecutor.submit(Callable { queryDoh(template.format(hostname)) })
        }
    }

    private fun awaitDoh(hostname: String, futures: List<Future<List<InetAddress>>>): List<InetAddress> {
        if (futures.isEmpty()) return emptyList()
        val deadline = System.currentTimeMillis() + 2500
        try {
            while (System.currentTimeMillis() < deadline) {
                for (f in futures) {
                    if (f.isDone) {
                        val r = runCatching { f.get() }.getOrNull()
                        if (!r.isNullOrEmpty()) return r
                    }
                }
                Thread.sleep(100)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        futures.forEach { it.cancel(true) }
        negativeCache[hostname] = System.currentTimeMillis() + CACHE_MS
        return emptyList()
    }

    private fun isNegativeCached(hostname: String): Boolean {
        val until = negativeCache[hostname] ?: return false
        return if (System.currentTimeMillis() < until) true else {
            negativeCache.remove(hostname)
            false
        }
    }

    private fun queryDoh(url: String): List<InetAddress> {
        val body = runCatching {
            dohClient.newCall(
                Request.Builder()
                    .url(url)
                    .header("Accept", "application/dns-json")
                    .get()
                    .build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body?.string()
            }
        }.getOrNull() ?: return emptyList()
        if (body.isBlank()) return emptyList()
        val answers = runCatching { JSONObject(body).optJSONArray("Answer") }.getOrNull() ?: return emptyList()
        val out = ArrayList<InetAddress>(answers.length())
        for (i in 0 until answers.length()) {
            val a = answers.optJSONObject(i) ?: continue
            if (a.optInt("type") != 1) continue
            val ip = a.optString("data").trim()
            if (ip.isEmpty()) continue
            runCatching { InetAddress.getByName(ip) }.getOrNull()?.let { out.add(it) }
        }
        return out
    }

    private fun isLiteralIp(host: String): Boolean {
        val h = host.removeSuffix(".").removePrefix("[").removeSuffix("]")
        return h.count { it == '.' } == 3 && h.all { it.isDigit() || it == '.' } ||
            h.contains(':') && h.none { it.isLetter() && it !in "abcdefABCDEF" }
    }
}
