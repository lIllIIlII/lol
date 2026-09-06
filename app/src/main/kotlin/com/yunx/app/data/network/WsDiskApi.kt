/*
 * 吸析At - 「小飞机网盘 / 蓝奏云优享版(ilanzou)」通用 API 引擎。
 *
 * 两站为同源产品（dinghao 系，前端代码几乎一致），协议完全同构，
 * 仅 域名 / AES 密钥 / 接口路径前缀 不同：
 * - feijipan: api.feijipan.com + 前缀 ws/      + 密钥 dingHao-disk-app
 * - ilanzou : apix.ilanzou.com + 前缀 unproved/ + 密钥 lanZouY-disk-app
 *
 * 协议（由 www.feijix.com / www.ilanzou.com 分享页前端 JS 抓包还原）：
 * 1) POST {recommend/list} {shareId, code?, userId?, type=0, offset, limit}
 *    → {code:200, msg, total, status, list:[{shareId,userId,fileIds,
 *        fileList:[{fileName|folderName, fileId|folderId, fileType, fileSize, iconId}],
 *        map:{userId,userName,avatar,...}}]}
 *    - status: -1 分享失效/维护；2 提取码相关；0 且 list 空 → 无文件；1 正常
 *    - 「需要提取码」的响应特征：fileList[0] 为空对象（无 fileName/folderName/fileId）
 *    - 文件夹条目字段是 folderName/folderId（旧版只读 fileName → 全部变成
 *      「未知文件夹」的根源），文件条目是 fileName/fileId（下载用自身 fileId，
 *      而非共享级 fileIds）
 * 2) 文件夹内容 POST {share/file/list | share/list} {shareId, folderId(null=根),
 *    offset, limit} → {code:200, list:[条目...], total}（条目结构同上）
 * 3) 直链 GET {file/redirect}?downloadId=hex(fileId|userPart)&enable=1&devType=6
 *    &uuid&timestamp=hex(now)&auth=hex(fileId|now)&shareId= → 302 Location
 *    - feijipan 的 userPart = 分享者 userId；ilanzou 匿名下载 = 空串
 */

package com.yunx.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** 引擎配置（两站差异全部收敛在这里） */
data class WsDiskConfig(
    /** 主域名 + 备用域名（同协议不同入口，任一可用即可；防单一域名被污染/屏蔽） */
    val hosts: List<String>,
    val recommendPath: String,
    val folderListPath: String,
    val redirectPath: String,
    val aesKey: String,
    val origin: String,
    val label: String
)

/** 统一条目：文件用 fileId，文件夹用 folderId；entryUserId 为条目自带的目标用户段 */
data class WsDiskEntry(
    val id: String,
    val name: String,
    /** 字节（接口返回 KB，此处已乘 1024） */
    val size: Long,
    val isDir: Boolean,
    /** 条目自带 userId（downloadId 用户段，优先于分享者 userId） */
    val entryUserId: String = ""
)

data class WsDiskShareInfo(
    val shareId: String,
    val userId: String,
    val title: String,
    val entries: List<WsDiskEntry>,
    val total: Int
)

class WsDiskApi(val config: WsDiskConfig) {

    /** 平台名（错误提示用） */
    val configLabel: String get() = config.label

    companion object {
        private const val CHARSET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-_"
        private val random = SecureRandom()

        class WrongPwdException(message: String = "提取码不正确") : Exception(message)
        class NeedsPwdException(message: String = "该分享需要提取码") : Exception(message)

        /** 小飞机网盘引擎（downloadId 用户段：条目/分享者 userId）
         *  文件夹列表用 ws/share/list（nfd 生产验证的通用旧接口，无需服务端解析目标用户） */
        val FEIJI = WsDiskApi(
            WsDiskConfig(
                hosts = listOf("api.feijipan.com"),
                recommendPath = "ws/recommend/list",
                folderListPath = "ws/share/list",
                redirectPath = "ws/file/redirect",
                aesKey = "dingHao-disk-app",
                origin = "https://www.feijix.com",
                label = "小飞机网盘"
            )
        )

        /** 蓝奏云优享版引擎（downloadId 用户段：条目 userId → 分享者 → 空串）
         *  双入口：apix（官网前端现行）+ api（AList / nfd 长期使用），互为容灾 */
        val ILANZOU = WsDiskApi(
            WsDiskConfig(
                hosts = listOf("apix.ilanzou.com", "api.ilanzou.com"),
                recommendPath = "unproved/recommend/list",
                folderListPath = "unproved/share/list",
                redirectPath = "unproved/file/redirect",
                aesKey = "lanZouY-disk-app",
                origin = "https://www.ilanzou.com",
                label = "蓝奏云优享版"
            )
        )
    }

    private fun randomUuid(): String {
        val sb = StringBuilder(21)
        repeat(21) { sb.append(CHARSET[random.nextInt(CHARSET.length)]) }
        return sb.toString()
    }

    private fun aesHex(plain: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(config.aesKey.toByteArray(Charsets.UTF_8), "AES"))
        return cipher.doFinal(plain.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    /** 独立客户端：SmartDns 防 DNS 污染 + 不跟随重定向（直链 302 探测） */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(SmartDns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    /** 当前可用域名（网络层失败时自动切换到下一个入口，成功后粘住） */
    @Volatile
    private var activeHostIdx = 0

    private fun httpUrl(host: String, path: String, params: Map<String, String>): okhttp3.HttpUrl {
        val url = okhttp3.HttpUrl.Builder().scheme("https").host(host)
            .addPathSegments(path).build()
        val b = url.newBuilder()
        params.forEach { (k, v) -> b.addQueryParameter(k, v) }
        return b.build()
    }

    private fun baseHeaders(): okhttp3.Headers.Builder {
        return okhttp3.Headers.Builder()
            .set("User-Agent", LanzouApi.USER_AGENT)
            .set("Accept", "application/json, text/plain, */*")
            .set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .set("Origin", config.origin)
            .set("Referer", "$config.origin/")
    }

    /** POST：依次尝试各入口域名（网络层失败/非 2xx 切下一个；仅单入口时直接抛错） */
    private fun postWithFailover(path: String, params: Map<String, String>): String {
        var lastError: Exception = IllegalStateException("网络请求失败")
        for (attempt in config.hosts.indices) {
            val host = config.hosts[(activeHostIdx + attempt) % config.hosts.size]
            try {
                val req = Request.Builder().url(httpUrl(host, path, params))
                    .headers(baseHeaders().build())
                    .post(okhttp3.FormBody.Builder().build())
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        activeHostIdx = (activeHostIdx + attempt) % config.hosts.size
                        return resp.body?.string() ?: ""
                    }
                    lastError = IllegalStateException("网络请求失败（HTTP ${resp.code}）")
                }
            } catch (e: java.io.IOException) {
                lastError = e
            }
        }
        throw lastError
    }

    // ---------- 条目解析（根治「未知文件夹」：文件夹字段是 folderName/name + folderId） ----------

    /**
     * 条目字段兼容（recommend/list 与 share/list 两类响应字段名有差异，
     * 官网前端与 nfd/AList 实现综合）：
     * - 文件夹：folderName 或 name / folderId / fileType==2
     * - 文件：fileName 或 name / fileId / fileSize（单位 KB）
     */
    private fun parseEntry(
        f: JSONObject,
        shareFileIds: String,
        allowPwdProbe: Boolean
    ): WsDiskEntry? {
        val isDir = f.optInt("fileType", 1) == 2
        val id = if (isDir) f.optString("folderId") else f.optString("fileId")
        val name = when {
            isDir -> f.optString("folderName").ifBlank { f.optString("name") }
            else -> f.optString("fileName").ifBlank { f.optString("name") }
        }
        // 「需要提取码」响应：条目为空对象（无名称无 ID）
        if (name.isBlank() && id.isBlank()) {
            if (allowPwdProbe) return null else throw NeedsPwdException()
        }
        if (isDir && id.isBlank()) return null // 文件夹无 folderId，不可进入，不入列
        val finalId = id.ifBlank { shareFileIds } // 单文件分享自身无 fileId 时回退共享级 fileIds
        if (finalId.isBlank()) return null // 无任何可用 ID（文件夹内容兜底为空串时跳过）
        return WsDiskEntry(
            id = finalId,
            name = name.ifBlank { "未知名称" },
            size = f.optLong("fileSize", 0L) * 1024L, // 接口返回 KB（nfd/AList 同款换算）
            isDir = isDir,
            entryUserId = f.optString("userId")
        )
    }

    // ---------- 分享根解析 ----------

    /** 拉取分享根信息（recommend/list） */
    suspend fun fetchShare(shareId: String, code: String?): WsDiskShareInfo = withContext(Dispatchers.IO) {
        val params = linkedMapOf(
            "devType" to "6",
            "devModel" to "Chrome",
            "uuid" to randomUuid(),
            "extra" to "2",
            "timestamp" to aesHex(System.currentTimeMillis().toString()),
            "shareId" to shareId,
            "type" to "0",
            "offset" to "1",
            "limit" to "100"
        )
        code?.takeIf { it.isNotBlank() }?.let { params["code"] = it }

        val body = postWithFailover(config.recommendPath, params)
        if (body.isBlank()) throw IllegalStateException("获取分享信息失败，请检查网络")
        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: throw IllegalStateException("分享信息响应异常")

        when (json.optInt("code", -1)) {
            200 -> { /* 正常继续（status 语义在下方分流） */ }
            else -> {
                val msg = json.optString("msg")
                if (msg.contains("密码") || msg.contains("提取") || msg.contains("code")) {
                    // 未提供提取码 → 提示需要提取码；已提供仍报错 → 提取码不正确
                    throw if (code.isNullOrBlank()) NeedsPwdException(msg) else WrongPwdException(msg)
                }
                throw IllegalStateException(msg.ifBlank { "分享链接无效或已失效" })
            }
        }
        // status 分流（前端同款语义）：-1 失效；2 提取码相关
        when (json.opt("status").toString()) {
            "-1" -> throw IllegalStateException("分享已取消或已失效")
            "2" -> {
                if (code.isNullOrBlank()) throw NeedsPwdException() else throw WrongPwdException()
            }
        }

        val list: JSONArray = json.optJSONArray("list") ?: JSONArray()
        if (list.length() == 0) throw IllegalStateException("分享内容为空或链接已失效")
        val info = list.optJSONObject(0) ?: throw IllegalStateException("分享数据异常")
        val shareFileIds = info.optString("fileIds")
        var userId = info.optString("userId")
        if (userId.isBlank()) userId = info.optJSONObject("map")?.optString("userId").orEmpty()
        if (userId.isBlank()) userId = info.optString("shareId").ifBlank { shareId }

        val fileList = info.optJSONArray("fileList") ?: JSONArray()
        // 「需要提取码」检测：唯一条目是空对象
        if (fileList.length() == 1) {
            val only = fileList.optJSONObject(0) ?: JSONObject()
            val noName = only.optString("fileName").isBlank() && only.optString("folderName").isBlank()
            val noId = only.optString("fileId").isBlank() && only.optString("folderId").isBlank()
            if (noName && noId) {
                if (code.isNullOrBlank()) throw NeedsPwdException() else throw WrongPwdException()
            }
        }
        val entries = ArrayList<WsDiskEntry>(fileList.length())
        var firstFileName = ""
        for (i in 0 until fileList.length()) {
            val f = fileList.optJSONObject(i) ?: continue
            val e = parseEntry(f, shareFileIds, allowPwdProbe = true) ?: continue
            if (firstFileName.isBlank()) firstFileName = e.name
            entries.add(e)
        }
        if (entries.isEmpty()) throw IllegalStateException("分享内容为空或链接已失效")
        WsDiskShareInfo(
            shareId = shareId,
            userId = userId,
            title = if (entries.size == 1) firstFileName else "${config.label}分享（${entries.size} 项）",
            entries = entries,
            total = json.optInt("total", entries.size)
        )
    }

    // ---------- 文件夹内容 ----------

    /**
     * 分页拉取文件夹内容（folderId 为空 = 根目录）。
     * @return 条目列表（已按 total 自动翻页，防大文件夹截断）
     */
    suspend fun fetchFolderFiles(
        shareId: String,
        folderId: String?,
        code: String?,
        maxPages: Int = 10
    ): List<WsDiskEntry> = withContext(Dispatchers.IO) {
        val out = ArrayList<WsDiskEntry>()
        val seen = HashSet<String>()
        var offset = 1
        val limit = 100
        var total = -1
        repeat(maxPages) {
            val params = linkedMapOf(
                "devType" to "6",
                "devModel" to "Chrome",
                "uuid" to randomUuid(),
                "extra" to "2",
                "timestamp" to aesHex(System.currentTimeMillis().toString()),
                "shareId" to shareId,
                "offset" to offset.toString(),
                "limit" to limit.toString()
            )
            folderId?.takeIf { it.isNotBlank() }?.let { params["folderId"] = it }
            code?.takeIf { it.isNotBlank() }?.let { params["code"] = it }

            val body = postWithFailover(config.folderListPath, params)
            if (body.isBlank()) return@withContext out
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return@withContext out
            if (json.optInt("code", -1) != 200) return@withContext out
            val pageList = json.optJSONArray("list") ?: return@withContext out
            if (total < 0) total = json.optInt("total", pageList.length())
            var added = 0
            for (i in 0 until pageList.length()) {
                val f = pageList.optJSONObject(i) ?: continue
                val e = parseEntry(f, "", allowPwdProbe = true) ?: continue
                // 去重：服务端翻页异常时不无限叠加（旧版未知文件夹问题的镜像防护）
                if (!seen.add("${if (e.isDir) 'd' else 'f'}:${e.id}")) continue
                out.add(e)
                added++
            }
            if (pageList.length() == 0 || out.size >= total || added == 0) return@withContext out
            offset++
        }
        out
    }

    // ---------- 直链 ----------

    /**
     * 获取单文件直链（302 Location）。多入口依次尝试（网络层失败切换）。
     * @param userPart downloadId 的用户段：小飞机=分享者 userId；优享版匿名=空串
     */
    suspend fun fetchDirectLink(shareId: String, fileId: String, userPart: String): String =
        withContext(Dispatchers.IO) {
            val uuid = randomUuid()
            val now = System.currentTimeMillis().toString()
            val params = linkedMapOf(
                "downloadId" to aesHex("$fileId|$userPart"),
                "enable" to "1",
                "devType" to "6",
                "uuid" to uuid,
                "timestamp" to aesHex(now),
                "auth" to aesHex("$fileId|$now"),
                "shareId" to shareId
            )
            var lastError: Exception = IllegalStateException("获取直链失败")
            for (attempt in config.hosts.indices) {
                val host = config.hosts[(activeHostIdx + attempt) % config.hosts.size]
                try {
                    val req = Request.Builder()
                        .url(httpUrl(host, config.redirectPath, params))
                        .headers(baseHeaders().build())
                        .get()
                        .build()
                    client.newCall(req).execute().use { resp ->
                        when {
                            resp.isRedirect || resp.code in 301..308 -> {
                                val loc = resp.header("Location").orEmpty()
                                if (loc.isNotBlank()) {
                                    activeHostIdx = (activeHostIdx + attempt) % config.hosts.size
                                    return@withContext loc
                                }
                                lastError = IllegalStateException("直链返回为空，请稍后重试")
                            }
                            else -> lastError = IllegalStateException(
                                "获取直链失败（HTTP ${resp.code}），可能需要会员或链接已失效"
                            )
                        }
                    }
                } catch (e: java.io.IOException) {
                    lastError = e
                }
            }
            throw lastError
        }
}
