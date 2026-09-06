/*
 * 吸析At - 奶牛快传分享解析仓库。
 */

package com.yunx.app.data.repository

import com.yunx.app.data.network.CowTransferApi
import com.yunx.app.data.network.ShareLinkParser
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareSession
import org.json.JSONObject

class CowTransferResolveRepository : ShareResolveRepository {

    private fun packStoken(pwd: String): String = JSONObject().put("p", pwd).toString()

    override suspend fun createSession(link: String, pwd: String?, cookie: String): Result<ShareSession> {
        val parsed = ShareLinkParser.parse(link)
            ?: return Result.failure(IllegalArgumentException("无法识别奶牛快传链接"))
        return runCatching {
            val sharePwd = pwd?.takeIf { it.isNotBlank() } ?: parsed.pwd.orEmpty()
            val info = CowTransferApi.fetchShare(parsed.shareId, sharePwd)
            ShareSession(
                shareId = parsed.shareId,
                stoken = packStoken(sharePwd),
                title = info.title.ifBlank { "奶牛快传分享" }
            )
        }
    }

    override suspend fun listFiles(session: ShareSession, dirFid: String, cookie: String): Result<List<ShareFile>> {
        val pwd = runCatching { JSONObject(session.stoken).optString("p") }.getOrDefault("")
        return runCatching {
            val info = CowTransferApi.fetchShare(session.shareId, pwd)
            info.entries.map { e ->
                ShareFile(
                    fid = e.guid,
                    fname = e.fileName,
                    fsize = CowTransferApi.sizeOf(e.sizeText),
                    isdir = false,
                    pdirFid = "",
                    fidToken = "",
                )
            }
        }
    }

    override suspend fun ensureTempDir(cookie: String): Result<String> =
        Result.failure(UnsupportedOperationException("奶牛快传无需转存"))

    override suspend fun transferFile(
        session: ShareSession,
        file: ShareFile,
        toDirFid: String,
        cookie: String
    ): Result<String> = Result.failure(UnsupportedOperationException("奶牛快传暂不支持转存"))

    override suspend fun getDownloadLink(fid: String, cookie: String): Result<DownloadLink> =
        Result.failure(UnsupportedOperationException("奶牛快传请使用分享直链下载"))

    override suspend fun getShareDownloadLink(
        session: ShareSession,
        file: ShareFile,
        cookie: String
    ): Result<DownloadLink> = runCatching {
        val link = CowTransferApi.fetchDirectLink(session.shareId, file.fid)
        DownloadLink(
            fid = file.fid,
            filename = file.fname.ifBlank { "cowtransfer_file" },
            downloadUrl = link,
            size = file.fsize
        )
    }
}
