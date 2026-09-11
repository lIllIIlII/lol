package com.yunx.app.data.network

import com.yunx.app.data.network.model.QuotaInfo
import com.yunx.app.data.network.model.ShareFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder

data class BaiduShareList(
    val title: String,
    val shareId: String,
    val uk: String,
    val files: List<ShareFile>
)

data class BaiduTransferResult(
    val fsId: String,
    val path: String
)

class BaiduApi(
    private val clientProvider: () -> OkHttpClient = { HttpClients.apiClient() }
) {
    private val client get() = clientProvider()

    private val formMediaType = "application/x-www-form-urlencoded".toMediaType()

    @Volatile
    private var cachedBdstoken: String? = null

    suspend fun fetchNickname(cookie: String): String? = withContext(Dispatchers.IO) {
        val result = templateVariable(cookie, """["username"]""") ?: return@withContext null
        result.optString("username").takeIf { it.isNotBlank() }
    }

    suspend fun getBdstoken(cookie: String): String? = withContext(Dispatchers.IO) {
        cachedBdstoken?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        val result = templateVariable(cookie, """["bdstoken"]""") ?: return@withContext null
        val token = result.optString("bdstoken").takeIf { it.isNotBlank() } ?: return@withContext null
        cachedBdstoken = token
        token
    }

    private suspend fun templateVariable(cookie: String, fields: String): JSONObject? =
        withContext(Dispatchers.IO) {
            val url = "https://pan.baidu.com/api/gettemplatevariable" +
                "?clienttype=0&app_id=${BaiduConstants.APP_ID}&web=1&fields=" +
                URLEncoder.encode(fields, "UTF-8")
            val request = Request.Builder()
                .url(url)
                .header("Cookie", cookie)
                .header("User-Agent", BaiduConstants.UA_WEB)
                .get()
                .build()
            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val json = JSONObject(response.body?.string() ?: "{}")
                    if (json.optInt("errno") != 0) return@use null
                    json.optJSONObject("result")
                }
            }.getOrNull()
        }

    suspend fun verifyShare(surl: String, pwd: String, cookie: String): String =
        withContext(Dispatchers.IO) {
            val body = "pwd=${urlEncode(pwd)}&vcode_str=&vcode="
            val request = Request.Builder()
                .url("https://pan.baidu.com/share/verify?surl=$surl")
                .header("Cookie", cookie)
                .header("User-Agent", BaiduConstants.UA_WEB)
                .header("Referer", "https://pan.baidu.com/s/$surl")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(body.toRequestBody(formMediaType))
                .build()
            val json = executeJson(request)
            checkErrno(json, "验证提取码失败")
            json.optString("randsk").takeIf { it.isNotBlank() }
                ?: throw BaiduApiException("未返回分享密钥")
        }

suspend fun listShare(surl: String, sekey: String, dir: String, cookie: String, page: Int = 1): BaiduShareList =
    withContext(Dispatchers.IO) {
        val isRoot = dir.isBlank() || dir == "/"
        val root = if (isRoot) "1" else "0"
        val sekeyPart = if (sekey.isNotBlank()) "&sekey=$sekey" else ""
        val url = "https://pan.baidu.com/rest/2.0/xpan/share?method=list" +
            "&shorturl=$surl&page=$page&num=100&root=$root&dir=" +
            URLEncoder.encode(if (dir.isBlank()) "/" else dir, "UTF-8") +
            sekeyPart
        val authCookie = if (sekey.isNotBlank() && !cookie.contains("BDCLND="))
            "$cookie; BDCLND=$sekey" else cookie
        val request = Request.Builder()
            .url(url)
            .header("Cookie", authCookie)
            .header("User-Agent", BaiduConstants.UA_WEB)
            .header("Referer", "https://pan.baidu.com/s/$surl")
            .get()
            .build()
        val json = executeJson(request)
        val errno = json.optInt("errno")
        if (errno != 0) {
            if (sekey.isBlank()) throw BaiduApiException("该分享需要提取码")
            checkErrno(json, "获取分享文件列表失败")
        }
        val array = json.optJSONArray("list") ?: org.json.JSONArray()
        val files = buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val isdir = item.optString("isdir") == "1"
                val path = item.optString("path")
                    add(
                        ShareFile(
                            fid = if (isdir) path else item.optString("fs_id"),
                            fname = item.optString("server_filename"),
                            fsize = item.optLong("size"),
                            isdir = isdir,
                            pdirFid = path,
                            fidToken = "",
                            modifyTime = item.optString("server_mtime")
                        )
                    )
                }
            }
            BaiduShareList(
                title = json.optString("title"),
                shareId = json.optString("share_id"),
                uk = json.optString("uk"),
                files = files
            )
        }

    suspend fun createDir(path: String, cookie: String): Boolean = withContext(Dispatchers.IO) {
        val bdstoken = getBdstoken(cookie) ?: return@withContext false
        val body = "path=${urlEncode(path)}&isdir=1&size&block_list=%5B%5D&method=post&dataType=json"
        val request = Request.Builder()
            .url("https://pan.baidu.com/api/create?a=commit&channel=chunlei&web=1" +
                "&app_id=${BaiduConstants.APP_ID}&clienttype=0&bdstoken=$bdstoken")
            .header("Cookie", cookie)
            .header("User-Agent", BaiduConstants.UA_NETDISK)
            .header("Referer", "https://yun.baidu.com/disk/main")
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .post(body.toRequestBody(formMediaType))
            .build()
        runCatching {
            val json = executeJson(request)
            json.optInt("errno") == 0
        }.getOrDefault(false)
    }

    suspend fun listDir(dir: String, cookie: String): List<String> = withContext(Dispatchers.IO) {
        val url = "https://yun.baidu.com/api/list?clienttype=0&app_id=${BaiduConstants.APP_ID}" +
            "&web=1&order=time&desc=1&dir=" + URLEncoder.encode(dir, "UTF-8") + "&num=100&page=1"
        val request = Request.Builder()
            .url(url)
            .header("Cookie", cookie)
            .header("User-Agent", BaiduConstants.UA_NETDISK)
            .get()
            .build()
        runCatching {
            val json = executeJson(request)
            if (json.optInt("errno") != 0) return@runCatching emptyList()
            val array = json.optJSONArray("list") ?: return@runCatching emptyList()
            buildList {
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { add(it.optString("path")) }
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun transfer(
        shareId: String,
        uk: String,
        sekey: String,
        fsId: String,
        toDir: String,
        cookie: String
    ): BaiduTransferResult = withContext(Dispatchers.IO) {
        val bdstoken = getBdstoken(cookie)
            ?: throw BaiduApiException("获取 bdstoken 失败，请重新登录")
        val url = "https://pan.baidu.com/share/transfer?shareid=$shareId&from=$uk" +
            "&channel=chunlei&sekey=$sekey&ondup=newcopy&web=1&app_id=${BaiduConstants.APP_ID}" +
            "&bdstoken=$bdstoken&clienttype=0"
        val body = "fsidlist=%5B%22$fsId%22%5D&path=${urlEncode(toDir)}"
        val authCookie = if (cookie.contains("BDCLND=")) cookie else "$cookie; BDCLND=$sekey"
        val request = Request.Builder()
            .url(url)
            .header("Cookie", authCookie)
            .header("User-Agent", BaiduConstants.UA_WEB)
            .header("Origin", "https://pan.baidu.com")
            .header("Referer", "https://pan.baidu.com/s/")
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .post(body.toRequestBody(formMediaType))
            .build()
        val json = executeJson(request)
        checkErrno(json, "转存失败")
        val extra = json.optJSONObject("extra")
        val list = extra?.optJSONArray("list")
        val first = list?.optJSONObject(0)
        val fsIdNew = first?.optString("to_fs_id")?.takeIf { it.isNotBlank() }
            ?: throw BaiduApiException("转存失败：未返回新文件")
        val pathNew = first?.optString("to")?.takeIf { it.isNotBlank() }
            ?: "$toDir/"
        BaiduTransferResult(fsId = fsIdNew, path = pathNew)
    }

    suspend fun locateDownload(path: String, cookie: String): String = withContext(Dispatchers.IO) {
        val time = System.currentTimeMillis() / 1000
        val url = "https://d.pcs.baidu.com/rest/2.0/pcs/file" +
            "?method=locatedownload" +
            "&app_id=${BaiduConstants.APP_ID}" +
            "&clienttype=17&ver=4.0" +
            "&ant=1&check_blue=1&es=1&esl=1&apn_id=1_-1" +
            "&freeisp=0&queryfree=0&use=1&dtype=1&eck=1&ehps=1" +
            "&err_ver=1.0&network_type=WIFI&channel=0" +
            "&path=${urlEncode(path)}" +
            "&time=$time" +
            "&rand=5ed606e9da222cde0474cdf70eda884b" +
            "&devuid=0F1E9FC2E084472DA5A61C4CF4C759AF" +
            "&cuid=0F1E9FC2E084472DA5A61C4CF4C759AF" +
            "&deviceid=348642637967375013" +
            "&psign=860a071f77c860e8cea06e4e54c518f3" +
            "&version=2.2.111.34&version_app=12.24.6&vip=0"
        val request = Request.Builder()
            .url(url)
            .header("Cookie", cookie)
            .header("User-Agent", BaiduConstants.UA_NETDISK)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .post("0".toRequestBody(formMediaType))
            .build()
        val json = executeJson(request)
        checkErrno(json, "获取高速下载链接失败")
        val urls = json.optJSONArray("urls")
        val candidates = (0 until (urls?.length() ?: 0))
            .mapNotNull { urls?.optJSONObject(it) }
            .filter { it.optString("url").isNotBlank() }
        val directUrl = candidates
            .filter { it.optInt("encrypt", 1) == 0 }
            .sortedBy { !it.optString("url").startsWith("https") }
            .firstOrNull()?.optString("url")
            ?: candidates.firstOrNull { it.optString("url").startsWith("https") }?.optString("url")
            ?: candidates.firstOrNull()?.optString("url")
            ?: throw BaiduApiException("未返回下载链接")
        directUrl
    }

    suspend fun fileMetasDlink(fsId: String, cookie: String): String = withContext(Dispatchers.IO) {
        val bdstoken = getBdstoken(cookie)
            ?: throw BaiduApiException("获取 bdstoken 失败，请重新登录")
        val fsids = URLEncoder.encode("""["$fsId"]""", "UTF-8")
        val url = "https://pan.baidu.com/api/filemetas?dlink=1&fsids=$fsids&bdstoken=$bdstoken" +
            "&clienttype=0&app_id=${BaiduConstants.APP_ID}&web=1"
        val request = Request.Builder()
            .url(url)
            .header("Cookie", cookie)
            .header("User-Agent", BaiduConstants.UA_WEB)
            .get()
            .build()
        val json = executeJson(request)
        checkErrno(json, "获取下载链接失败")
        val info = json.optJSONArray("info")
        val dlink = info?.optJSONObject(0)?.optString("dlink")?.takeIf { it.isNotBlank() }
            ?: throw BaiduApiException("未返回下载链接")
        dlink
    }

    suspend fun deleteFile(path: String, cookie: String): Boolean = withContext(Dispatchers.IO) {
        val bdstoken = getBdstoken(cookie) ?: return@withContext false
        val body = "filelist=${URLEncoder.encode("""["$path"]""", "UTF-8")}"
        val request = Request.Builder()
            .url("https://pan.baidu.com/api/filemanager?async=2&onnest=fail&opera=delete" +
                "&bdstoken=$bdstoken&newVerify=1&clienttype=0&app_id=${BaiduConstants.APP_ID}&web=1")
            .header("Cookie", cookie)
            .header("User-Agent", BaiduConstants.UA_NETDISK)
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .post(body.toRequestBody(formMediaType))
            .build()
        runCatching {
            val json = executeJson(request)
            json.optInt("errno") == 0
        }.getOrDefault(false)
    }

    suspend fun listCloudFiles(dir: String, cookie: String): List<ShareFile> = withContext(Dispatchers.IO) {
        val all = mutableListOf<ShareFile>()
        var page = 1
        while (true) {
            val url = "https://yun.baidu.com/api/list?clienttype=0&app_id=${BaiduConstants.APP_ID}" +
                "&web=1&order=time&desc=1&dir=" + URLEncoder.encode(dir, "UTF-8") + "&num=100&page=$page"
            val request = Request.Builder()
                .url(url)
                .header("Cookie", cookie)
                .header("User-Agent", BaiduConstants.UA_NETDISK)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://yun.baidu.com/disk/main")
                .get()
                .build()
            val pageFiles = runCatching {
                val json = executeJson(request)
                if (json.optInt("errno") != 0) return@runCatching emptyList()
                val array = json.optJSONArray("list") ?: return@runCatching emptyList()
                buildList {
                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        add(
                            ShareFile(
                                fid = item.optString("fs_id"),
                                fname = item.optString("server_filename"),
                                fsize = item.optLong("size"),
                                isdir = item.optInt("isdir") == 1,
                                pdirFid = dir,
                                fidToken = item.optString("path"),
                                modifyTime = item.optString("server_mtime")
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
            if (pageFiles.isEmpty()) break
            all += pageFiles
            if (pageFiles.size < 100 || page >= 100) break
            page++
        }
        all
    }

    suspend fun renameFile(path: String, newName: String, cookie: String): Boolean = withContext(Dispatchers.IO) {
        val bdstoken = getBdstoken(cookie) ?: return@withContext false
        val filelist = """[{"path":"$path","newname":"$newName"}]"""
        val body = "filelist=${URLEncoder.encode(filelist, "UTF-8")}"
        val request = Request.Builder()
            .url("https://yun.baidu.com/api/filemanager?async=0&onnest=fail&opera=rename" +
                "&bdstoken=$bdstoken&clienttype=0&app_id=${BaiduConstants.APP_ID}&web=1")
            .header("Cookie", cookie)
            .header("User-Agent", BaiduConstants.UA_NETDISK)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Referer", "https://yun.baidu.com/disk/main")
            .post(body.toRequestBody(formMediaType))
            .build()
        runCatching {
            val json = executeJson(request)
            json.optInt("errno") == 0
        }.getOrDefault(false)
    }

    suspend fun moveFiles(
        paths: List<String>,
        dest: String,
        cookie: String
    ): Boolean = withContext(Dispatchers.IO) {
        val bdstoken = getBdstoken(cookie) ?: return@withContext false
        val items = paths.joinToString(",") { p ->
            val name = p.substringAfterLast('/')
            """{"path":"$p","dest":"$dest","newname":"$name"}"""
        }
        val body = "filelist=${URLEncoder.encode("[$items]", "UTF-8")}"
        val request = Request.Builder()
            .url("https://pan.baidu.com/api/filemanager?async=2&onnest=fail&opera=move" +
                "&bdstoken=$bdstoken&clienttype=0&app_id=${BaiduConstants.APP_ID}&web=1")
            .header("Cookie", cookie)
            .header("User-Agent", BaiduConstants.UA_NETDISK)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Referer", "https://yun.baidu.com/disk/main")
            .post(body.toRequestBody(formMediaType))
            .build()
        runCatching {
            val json = executeJson(request)
            json.optInt("errno") == 0
        }.getOrDefault(false)
    }

    suspend fun deleteFiles(paths: List<String>, cookie: String): Boolean = withContext(Dispatchers.IO) {
        val bdstoken = getBdstoken(cookie) ?: return@withContext false
        val body = "filelist=${URLEncoder.encode(paths.joinToString(",", "[", "]") { "\"$it\"" }, "UTF-8")}"
        val request = Request.Builder()
            .url("https://pan.baidu.com/api/filemanager?async=2&onnest=fail&opera=delete" +
                "&bdstoken=$bdstoken&newVerify=1&clienttype=0&app_id=${BaiduConstants.APP_ID}&web=1")
            .header("Cookie", cookie)
            .header("User-Agent", BaiduConstants.UA_NETDISK)
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .header("Referer", "https://yun.baidu.com/disk/main")
            .post(body.toRequestBody(formMediaType))
            .build()
        runCatching {
            val json = executeJson(request)
            json.optInt("errno") == 0
        }.getOrDefault(false)
    }

    data class BaiduShareResult(
        val link: String,
        val pwd: String,
        val shareId: String
    )

    suspend fun createShare(
        fsIds: List<String>,
        period: Int,
        pwd: String,
        cookie: String
    ): BaiduShareResult = withContext(Dispatchers.IO) {
        val bdstoken = getBdstoken(cookie)
            ?: throw BaiduApiException("获取 bdstoken 失败，请重新登录")
        val fidList = fsIds.joinToString(",", "[", "]")
        val body = "fid_list=${URLEncoder.encode(fidList, "UTF-8")}" +
            "&schannel=4&channel_list=%5B%5D&period=$period&pwd=${urlEncode(pwd)}"
        val request = Request.Builder()
            .url("https://pan.baidu.com/share/set?channel=chunlei&web=1" +
                "&app_id=${BaiduConstants.APP_ID}&bdstoken=$bdstoken&clienttype=0")
            .header("Cookie", cookie)
            .header("User-Agent", BaiduConstants.UA_NETDISK)
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .header("Referer", "https://yun.baidu.com/disk/main")
            .post(body.toRequestBody(formMediaType))
            .build()
        val json = executeJson(request)
        checkErrno(json, "创建分享失败")
        val link = json.optString("shorturl").ifBlank { json.optString("link") }
            .takeIf { it.isNotBlank() } ?: throw BaiduApiException("未返回分享链接")
        BaiduShareResult(
            link = link,
            pwd = pwd,
            shareId = json.optString("shareid")
        )
    }

    suspend fun getQuota(cookie: String): QuotaInfo? = withContext(Dispatchers.IO) {
        val url = "https://yun.baidu.com/api/quota?clienttype=0&app_id=${BaiduConstants.APP_ID}" +
            "&web=1&channel=chunlei&version=${System.currentTimeMillis()}"
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("Cookie", cookie)
                .header("User-Agent", BaiduConstants.UA_NETDISK)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://yun.baidu.com/disk/main")
                .get()
                .build()
            val response = client.newCall(request).execute()
            val body = response.use { it.body?.string() ?: return@runCatching null }
            val json = JSONObject(body)
            if (json.optInt("errno") != 0) return@runCatching null
            QuotaInfo(
                used = json.optLong("used"),
                total = json.optLong("total")
            )
        }.getOrNull()
    }

    private fun executeJson(request: Request): JSONObject {
        val response = client.newCall(request).execute()
        val body = response.use { it.body?.string() ?: throw BaiduApiException("请求失败：响应为空") }
        return runCatching { JSONObject(body) }.getOrElse {
            throw BaiduApiException("响应解析失败")
        }
    }

    private fun checkErrno(json: JSONObject, fallback: String) {
        val errno = json.optInt("errno")
        if (errno != 0) {
            val msg = json.optString("err_msg").ifBlank { json.optString("show_msg") }.ifBlank { fallback }
            throw BaiduApiException("$msg（errno=$errno）")
        }
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
