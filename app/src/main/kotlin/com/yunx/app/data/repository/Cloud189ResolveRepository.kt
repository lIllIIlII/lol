package com.yunx.app.data.repository

import com.yunx.app.data.network.Cloud189Api
import com.yunx.app.data.network.ShareLinkParser
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareSession
import org.json.JSONObject

class Cloud189ResolveRepository(
    private val cookieProvider: suspend () -> String?
) : ShareResolveRepository {

    private fun packStoken(shareKey: String, pwd: String, cookie: String): String =
        JSONObject().put("k", shareKey).put("p", pwd).put("c", cookie).toString()

    private fun unpack(stoken: String): Triple<String, String, String> = runCatching {
        val j = JSONObject(stoken)
        Triple(j.optString("k"), j.optString("p"), j.optString("c"))
    }.getOrDefault(Triple("", "", ""))

    override suspend fun createSession(link: String, pwd: String?, cookie: String): Result<ShareSession> {
        val parsed = ShareLinkParser.parse(link)
            ?: return Result.failure(IllegalArgumentException("无法识别天翼云盘链接"))
        return runCatching {
            val sharePwd = pwd?.takeIf { it.isNotBlank() } ?: parsed.pwd.orEmpty()
            val accountCookie = cookie.ifBlank { cookieProvider().orEmpty() }
            val info = Cloud189Api.fetchShare(parsed.shareId, sharePwd, accountCookie)
            ShareSession(
                shareId = info.shareId,
                stoken = packStoken(parsed.shareId, sharePwd, accountCookie),
                title = info.title
            )
        }
    }

    override suspend fun listFiles(session: ShareSession, dirFid: String, cookie: String): Result<List<ShareFile>> {
        val (shareKey, pwd, accountCookie) = unpack(session.stoken)
        return runCatching {
            if (shareKey.isBlank()) throw IllegalStateException("会话已失效，请重新解析")
            val info = Cloud189Api.fetchShare(shareKey, pwd, accountCookie)
            info.entries.map { e ->
                ShareFile(
                    fid = e.id,
                    fname = e.name,
                    fsize = e.size,
                    isdir = e.isDir,
                    pdirFid = e.parentId,
                    fidToken = e.md5,
                    modifyTime = ""
                )
            }
        }
    }

    override suspend fun ensureTempDir(cookie: String): Result<String> =
        Result.failure(UnsupportedOperationException("天翼云盘无需转存"))

    override suspend fun transferFile(
        session: ShareSession,
        file: ShareFile,
        toDirFid: String,
        cookie: String
    ): Result<String> = Result.failure(UnsupportedOperationException("天翼云盘暂不支持转存"))

    override suspend fun getDownloadLink(fid: String, cookie: String): Result<DownloadLink> =
        Result.failure(UnsupportedOperationException("天翼云盘请使用分享直链下载"))

    override suspend fun getShareDownloadLink(
        session: ShareSession,
        file: ShareFile,
        cookie: String
    ): Result<DownloadLink> {
        val (shareKey, pwd, accountCookie) = unpack(session.stoken)
        return runCatching {
            if (shareKey.isBlank()) throw IllegalStateException("会话已失效，请重新解析")
            val url = Cloud189Api.fetchDirectLink(session.shareId, shareKey, file.fid, pwd, accountCookie)
            DownloadLink(
                fid = file.fid,
                filename = file.fname.ifBlank { "cloud189_file" },
                downloadUrl = url,
                size = file.fsize
            )
        }
    }
}
