/*
 * 吸析At - 小飞机网盘（feijipan）分享解析仓库。
 * stoken 打包：{p: 提取码, u: userId}；文件 fid 直接用 fileIds（下载时再取直链）。
 */

package com.yunx.app.data.repository

import com.yunx.app.data.network.FeijiApi
import com.yunx.app.data.network.ShareLinkParser
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareSession
import org.json.JSONObject

class FeijiResolveRepository : ShareResolveRepository {

    private fun packStoken(pwd: String, userId: String): String =
        JSONObject().put("p", pwd).put("u", userId).toString()

    override suspend fun createSession(link: String, pwd: String?, cookie: String): Result<ShareSession> {
        val parsed = ShareLinkParser.parse(link)
            ?: return Result.failure(IllegalArgumentException("无法识别小飞机网盘链接"))
        return runCatching {
            val sharePwd = pwd?.takeIf { it.isNotBlank() } ?: parsed.pwd.orEmpty()
            val info = FeijiApi.fetchShare(parsed.shareId, sharePwd)
            ShareSession(
                shareId = parsed.shareId,
                stoken = packStoken(sharePwd, info.userId),
                title = info.title.ifBlank { "小飞机网盘分享" }
            )
        }
    }

    override suspend fun listFiles(session: ShareSession, dirFid: String, cookie: String): Result<List<ShareFile>> {
        val pwd = runCatching { JSONObject(session.stoken).optString("p") }.getOrDefault("")
        return runCatching {
            val info = FeijiApi.fetchShare(session.shareId, pwd)
            info.entries.map { e ->
                ShareFile(
                    fid = e.fileId,
                    fname = e.fileName,
                    fsize = e.fileSize,
                    isdir = e.isDir,
                    pdirFid = "",
                    fidToken = ""
                )
            }
        }
    }

    override suspend fun ensureTempDir(cookie: String): Result<String> =
        Result.failure(UnsupportedOperationException("小飞机网盘无需转存"))

    override suspend fun transferFile(
        session: ShareSession,
        file: ShareFile,
        toDirFid: String,
        cookie: String
    ): Result<String> = Result.failure(UnsupportedOperationException("小飞机网盘暂不支持转存"))

    override suspend fun getDownloadLink(fid: String, cookie: String): Result<DownloadLink> =
        Result.failure(UnsupportedOperationException("小飞机网盘请使用分享直链下载"))

    override suspend fun getShareDownloadLink(
        session: ShareSession,
        file: ShareFile,
        cookie: String
    ): Result<DownloadLink> = runCatching {
        val userId = runCatching { JSONObject(session.stoken).optString("u") }.getOrDefault("")
        if (userId.isBlank()) throw IllegalStateException("会话已失效，请重新解析")
        val link = FeijiApi.fetchDirectLink(session.shareId, file.fid, userId)
        DownloadLink(
            fid = file.fid,
            filename = file.fname.ifBlank { "feiji_file" },
            downloadUrl = link,
            size = file.fsize
        )
    }
}
