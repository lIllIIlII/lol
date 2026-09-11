package com.yunx.app.data.repository

import com.yunx.app.data.network.ShareLinkParser
import com.yunx.app.data.network.WsDiskApi
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareSession
import org.json.JSONObject

class WsDiskResolveRepository(
    private val api: WsDiskApi
) : ShareResolveRepository {

    private fun packStoken(pwd: String, userId: String): String =
        JSONObject().put("p", pwd).put("u", userId).toString()

    private fun unpackPwd(stoken: String): String = runCatching {
        JSONObject(stoken).optString("p")
    }.getOrDefault("")

    private fun unpackUserId(stoken: String): String = runCatching {
        JSONObject(stoken).optString("u")
    }.getOrDefault("")

    override suspend fun createSession(link: String, pwd: String?, cookie: String): Result<ShareSession> {
        val parsed = ShareLinkParser.parse(link)
            ?: return Result.failure(IllegalArgumentException("无法识别${api.configLabel}链接"))
        return runCatching {
            val sharePwd = pwd?.takeIf { it.isNotBlank() } ?: parsed.pwd.orEmpty()
            val info = api.fetchShare(parsed.shareId, sharePwd)
            ShareSession(
                shareId = parsed.shareId,
                stoken = packStoken(sharePwd, info.userId),
                title = info.title.ifBlank { "${api.configLabel}分享" }
            )
        }
    }

    override suspend fun listFiles(session: ShareSession, dirFid: String, cookie: String): Result<List<ShareFile>> {
        val pwd = unpackPwd(session.stoken)
        return runCatching {
            val entries = if (dirFid.isBlank() || dirFid == "0") {
                api.fetchShare(session.shareId, pwd).entries
            } else {
                api.fetchFolderFiles(session.shareId, dirFid, pwd)
            }
            if (entries.isEmpty()) throw IllegalStateException("该文件夹为空")
            entries.map { e ->
                ShareFile(
                    fid = e.id,
                    fname = e.name,
                    fsize = e.size,
                    isdir = e.isDir,
                    pdirFid = dirFid,
                    fidToken = e.entryUserId
                )
            }
        }
    }

    override suspend fun ensureTempDir(cookie: String): Result<String> =
        Result.failure(UnsupportedOperationException("${api.configLabel}无需转存"))

    override suspend fun transferFile(
        session: ShareSession,
        file: ShareFile,
        toDirFid: String,
        cookie: String
    ): Result<String> = Result.failure(UnsupportedOperationException("${api.configLabel}暂不支持转存"))

    override suspend fun getDownloadLink(fid: String, cookie: String): Result<DownloadLink> =
        Result.failure(UnsupportedOperationException("${api.configLabel}请使用分享直链下载"))

    override suspend fun getShareDownloadLink(
        session: ShareSession,
        file: ShareFile,
        cookie: String
    ): Result<DownloadLink> = runCatching {
        val userPart = file.fidToken.ifBlank { unpackUserId(session.stoken) }
        val link = api.fetchDirectLink(session.shareId, file.fid, userPart)
        DownloadLink(
            fid = file.fid,
            filename = file.fname.ifBlank { "disk_file" },
            downloadUrl = link,
            size = file.fsize
        )
    }
}
