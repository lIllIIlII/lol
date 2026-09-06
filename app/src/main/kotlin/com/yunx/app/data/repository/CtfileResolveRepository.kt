/*
 * 吸析At - 城通网盘文件分享解析仓库。
 *
 * 城通为「单文件直链」型网盘：解析即列出唯一文件，下载时取一次性直链。
 * stoken 打包 {k: 链接形态, i: fileid, p: 密码, h: 分享域名}；
 * /dir/ 文件夹分享暂不支持（提示浏览器打开）。
 */

package com.yunx.app.data.repository

import com.yunx.app.data.network.CtfileApi
import com.yunx.app.data.network.ShareLinkParser
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareSession
import org.json.JSONObject

class CtfileResolveRepository : ShareResolveRepository {

    private fun packStoken(kind: String, fileid: String, pwd: String, refHost: String): String =
        JSONObject().put("k", kind).put("i", fileid).put("p", pwd).put("h", refHost).toString()

    private fun unpack(stoken: String): Array<String> = runCatching {
        val j = JSONObject(stoken)
        arrayOf(j.optString("k"), j.optString("i"), j.optString("p"), j.optString("h"))
    }.getOrDefault(arrayOf("", "", "", ""))

    override suspend fun createSession(link: String, pwd: String?, cookie: String): Result<ShareSession> {
        val parsed = ShareLinkParser.parse(link)
            ?: return Result.failure(IllegalArgumentException("无法识别城通网盘链接"))
        return runCatching {
            // shareId 形态 "f:235978-1320342970-0dad31"（parser 归一化打包）
            val raw = parsed.shareId
            val kind = raw.substringBefore(':')
            val fileid = raw.substringAfter(':')
            if (kind == "dir") {
                throw UnsupportedOperationException("城通文件夹分享暂不支持解析，请在浏览器打开后复制单个文件链接")
            }
            val refHost = Regex("https?://([^/]+)/").find(link.trim() + "/")?.groupValues?.getOrNull(1)
                ?: "www.ctfile.com"
            val sharePwd = pwd?.takeIf { it.isNotBlank() } ?: parsed.pwd.orEmpty()
            val info = CtfileApi.fetchFileInfo(fileid, sharePwd, refHost)
            ShareSession(
                shareId = raw,
                stoken = packStoken(kind, fileid, sharePwd, refHost),
                title = info.fileName.ifBlank { "城通网盘分享" }
            )
        }
    }

    override suspend fun listFiles(session: ShareSession, dirFid: String, cookie: String): Result<List<ShareFile>> {
        val (kind, fileid, pwd, refHost) = unpack(session.stoken)
        return runCatching {
            if (kind.isBlank()) throw IllegalStateException("会话已失效，请重新解析")
            if (dirFid.isNotBlank()) throw IllegalStateException("城通分享为单文件，无子目录")
            val info = CtfileApi.fetchFileInfo(fileid, pwd, refHost)
            listOf(
                ShareFile(
                    fid = fileid,
                    fname = info.fileName,
                    fsize = info.fileSize,
                    isdir = false,
                    pdirFid = "",
                    fidToken = "",
                    modifyTime = ""
                )
            )
        }
    }

    override suspend fun ensureTempDir(cookie: String): Result<String> =
        Result.failure(UnsupportedOperationException("城通网盘无需转存"))

    override suspend fun transferFile(
        session: ShareSession,
        file: ShareFile,
        toDirFid: String,
        cookie: String
    ): Result<String> = Result.failure(UnsupportedOperationException("城通网盘暂不支持转存"))

    override suspend fun getDownloadLink(fid: String, cookie: String): Result<DownloadLink> =
        Result.failure(UnsupportedOperationException("城通网盘请使用分享直链下载"))

    override suspend fun getShareDownloadLink(
        session: ShareSession,
        file: ShareFile,
        cookie: String
    ): Result<DownloadLink> {
        val (kind, fileid, pwd, refHost) = unpack(session.stoken)
        return runCatching {
            if (kind.isBlank()) throw IllegalStateException("会话已失效，请重新解析")
            // 直链参数有时效性：下载时重新 getfile 获取最新 file_chk/verifycode
            val info = CtfileApi.fetchFileInfo(fileid, pwd, refHost)
            val url = CtfileApi.fetchDirectLink(info)
            DownloadLink(
                fid = file.fid,
                filename = file.fname.ifBlank { "ctfile_file" },
                downloadUrl = url,
                size = file.fsize
            )
        }
    }
}
