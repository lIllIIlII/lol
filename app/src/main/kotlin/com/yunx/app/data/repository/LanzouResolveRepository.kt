/*
 * 吸析At - 蓝奏云分享解析仓库。
 * 会话状态打包进 stoken（JSON：baseUrl / pwd / cookie），列表与直链均按需解析。
 * 支持文件与文件夹分享、提取码、acw 反爬自动解算。
 */

package com.yunx.app.data.repository

import com.yunx.app.data.network.LanzouApi
import com.yunx.app.data.network.ShareLinkParser
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareSession
import org.json.JSONObject

class LanzouResolveRepository(
    private val cookieProvider: suspend () -> String?
) : ShareResolveRepository {

    private fun packStoken(baseUrl: String, pwd: String, cookie: String): String =
        JSONObject().put("b", baseUrl).put("p", pwd).put("c", cookie).toString()

    private fun unpack(stoken: String): Triple<String, String, String> = runCatching {
        val j = JSONObject(stoken)
        Triple(j.optString("b"), j.optString("p"), j.optString("c"))
    }.getOrDefault(Triple("", "", ""))

    override suspend fun createSession(link: String, pwd: String?, cookie: String): Result<ShareSession> {
        val parsed = ShareLinkParser.parse(link)
            ?: return Result.failure(IllegalArgumentException("无法识别蓝奏云链接"))
        return runCatching {
            val baseUrl = Regex("^(https?://[^/]+)").find(link.trim())?.value
                ?: throw IllegalStateException("链接格式异常")
            val sharePwd = pwd?.takeIf { it.isNotBlank() } ?: parsed.pwd.orEmpty()
            val accountCookie = cookie.ifBlank { cookieProvider().orEmpty() }
            val page = LanzouApi.fetchPage(baseUrl, parsed.shareId, sharePwd, accountCookie)
            ShareSession(
                shareId = parsed.shareId,
                // 用容灾后实际可用的域名（原域名被拦截时已自动切换）
                stoken = packStoken(page.baseUrl, sharePwd, accountCookie),
                title = page.title.ifBlank { "蓝奏云分享" }
            )
        }
    }

    override suspend fun listFiles(session: ShareSession, dirFid: String, cookie: String): Result<List<ShareFile>> {
        val (baseUrl, pwd, accountCookie) = unpack(session.stoken)
        return runCatching {
            if (baseUrl.isBlank()) throw IllegalStateException("会话已失效，请重新解析")
            val targetId = dirFid.ifBlank { session.shareId }
            val page = LanzouApi.fetchPage(baseUrl, targetId, pwd, accountCookie)
            if (page.isFolder) {
                page.entries.map { e ->
                    ShareFile(
                        fid = e.id,
                        fname = e.name,
                        fsize = LanzouApi.sizeOf(e.sizeText),
                        isdir = e.isDir,
                        pdirFid = targetId,
                        fidToken = "",
                        modifyTime = e.timeText
                    )
                }
            } else {
                listOf(
                    ShareFile(
                        fid = targetId,
                        fname = page.title,
                        fsize = LanzouApi.sizeOf(page.sizeText),
                        isdir = false,
                        pdirFid = "",
                        fidToken = "",
                        modifyTime = page.timeText
                    )
                )
            }
        }
    }

    override suspend fun ensureTempDir(cookie: String): Result<String> =
        Result.failure(UnsupportedOperationException("蓝奏云无需转存"))

    override suspend fun transferFile(
        session: ShareSession,
        file: ShareFile,
        toDirFid: String,
        cookie: String
    ): Result<String> = Result.failure(UnsupportedOperationException("蓝奏云暂不支持转存"))

    override suspend fun getDownloadLink(fid: String, cookie: String): Result<DownloadLink> =
        Result.failure(UnsupportedOperationException("蓝奏云请使用分享直链下载"))

    override suspend fun getShareDownloadLink(
        session: ShareSession,
        file: ShareFile,
        cookie: String
    ): Result<DownloadLink> {
        val (baseUrl, pwd, accountCookie) = unpack(session.stoken)
        return runCatching {
            if (baseUrl.isBlank()) throw IllegalStateException("会话已失效，请重新解析")
            val url = LanzouApi.fetchDirectLink(baseUrl, file.fid, pwd, accountCookie)
            DownloadLink(
                fid = file.fid,
                filename = file.fname.ifBlank { "lanzou_file" },
                downloadUrl = url,
                size = file.fsize
            )
        }
    }
}
