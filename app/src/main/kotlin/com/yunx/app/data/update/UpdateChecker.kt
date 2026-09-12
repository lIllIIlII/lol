package com.yunx.app.data.update

import android.content.Context
import com.yunx.app.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

object UpdateChecker {

    private const val GITHUB_REPO = "lIllIIlII/lol"

    private val DEFAULT_UPDATE_URLS: List<String> get() = listOf(
        "https://api.github.com/repos/$GITHUB_REPO/releases/latest",
        "https://cdn.jsdelivr.net/gh/$GITHUB_REPO@main/updated.json",
        "https://gh-proxy.com/https://raw.githubusercontent.com/$GITHUB_REPO/main/updated.json",
        "https://raw.githubusercontent.com/$GITHUB_REPO/main/updated.json"
    )

    private const val PREFS = "yunx_settings"
    private const val PREF_KEY_OVERRIDE = "update_url_override"
    private const val PREF_KEY_IGNORED = "ignored_version"

    data class Release(
        val version: String,
        val notes: String,
        val downloadUrl: String,
        val publishedAt: String,
        val mirrorUrl: String = ""
    )

    fun compareVersions(v1: String, v2: String): Int {
        val cleaned1 = v1.trim().trimStart('v').trimStart('V')
        val cleaned2 = v2.trim().trimStart('v').trimStart('V')
        val parts1 = cleaned1.split(".", "-", "+")
        val parts2 = cleaned2.split(".", "-", "+")
        val maxLength = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLength) {
            val num1 = parts1.getOrNull(i)?.toIntOrNull() ?: 0
            val num2 = parts2.getOrNull(i)?.toIntOrNull() ?: 0
            if (num1 != num2) return num1.compareTo(num2)
        }
        return 0
    }

    fun currentVersion(context: Context): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"

    fun getOverrideUrl(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PREF_KEY_OVERRIDE, null)
            ?.takeIf { it.isNotBlank() }

    fun setOverrideUrl(context: Context, url: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_KEY_OVERRIDE, url.orEmpty())
            .apply()
    }

    fun getIgnoredVersion(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PREF_KEY_IGNORED, null)

    fun setIgnoredVersion(context: Context, version: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_KEY_IGNORED, version)
            .apply()
    }

    suspend fun fetchLatestRelease(context: Context): Release? = withContext(Dispatchers.IO) {
        val urls = buildList {
            getOverrideUrl(context)?.let { add(it) }
            addAll(DEFAULT_UPDATE_URLS)
        }
        for (url in urls) {
            val resolved = runCatching {
                if (url.contains("api.github.com")) fetchGithubApi(url) else fetchJson(url)
            }.getOrNull()
            if (resolved != null && resolved.version.isNotBlank() && resolved.downloadUrl.isNotBlank()) {
                return@withContext resolved
            }
        }
        null
    }

    private fun cacheBust(url: String): String {
        val sep = if (url.contains("?")) "&" else "?"
        return url + sep + "_t=" + System.currentTimeMillis()
    }

    private fun fetchGithubApi(url: String): Release? {
        val client = HttpClients.apiClient()
        val request = Request.Builder()
            .url(cacheBust(url))
            .header("User-Agent", "XiXiAt-UpdateChecker")
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()
        val body = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body?.string() ?: return null
        }
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val tag = json.optString("tag_name").trim().trimStart('v').trimStart('V')
        if (tag.isBlank()) return null
        val assets = json.optJSONArray("assets") ?: return null
        var apkUrl = ""
        var mirrorUrl = ""
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name").lowercase()
            val browserUrl = a.optString("browser_download_url")
            if (name.endsWith(".apk")) {
                apkUrl = browserUrl
                break
            }
        }
        if (apkUrl.isBlank()) return null
        val notes = json.optString("body").ifBlank { json.optString("notes") }
        val publishedAt = json.optString("published_at").ifBlank { json.optString("date") }
        return Release(
            version = tag,
            notes = notes,
            downloadUrl = apkUrl,
            publishedAt = publishedAt,
            mirrorUrl = mirrorUrl
        )
    }

    private fun fetchJson(url: String): Release? {
        val client = HttpClients.apiClient()
        val request = Request.Builder()
            .url(cacheBust(url))
            .header("User-Agent", "XiXiAt-UpdateChecker")
            .header("Cache-Control", "no-cache")
            .get()
            .build()
        val body = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body?.string() ?: return null
        }
        val start = body.indexOf('{')
        val end = body.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val json = JSONObject(body.substring(start, end + 1))
        val version = json.optString("version")
            .ifBlank { json.optString("tag_name") }
            .ifBlank { json.optString("tagName") }
        if (version.isBlank()) return null
        val downloadUrl = json.optString("url")
            .ifBlank { json.optString("downloadUrl") }
            .ifBlank { json.optString("download_url") }
        val mirrorUrl = json.optString("mirror")
            .ifBlank { json.optString("mirrorUrl") }
            .ifBlank { json.optString("mirror_url") }
        return Release(
            version = version.trim(),
            notes = json.optString("notes").ifBlank { json.optString("body") },
            downloadUrl = downloadUrl.trim(),
            publishedAt = json.optString("date").ifBlank { json.optString("published_at") },
            mirrorUrl = mirrorUrl.trim()
        )
    }

    suspend fun resolveDownloadUrl(release: Release): String = withContext(Dispatchers.IO) {
        val candidates = buildList {
            if (release.downloadUrl.isNotBlank()) add(release.downloadUrl)
            if (release.mirrorUrl.isNotBlank()) add(release.mirrorUrl)
            release.downloadUrl.takeIf { it.contains("github.com/") && !it.contains("gh-proxy") }?.let {
                add("https://gh-proxy.com/$it")
            }
        }
        if (candidates.isEmpty()) return@withContext ""
        for (url in candidates) {
            val ok = runCatching {
                val req = Request.Builder().url(url).head().header("User-Agent", "XiXiAt").build()
                HttpClients.apiClient().newCall(req).execute().use { it.isSuccessful }
            }.getOrDefault(false)
            if (ok) return@withContext url
        }
        candidates.first()
    }
}
