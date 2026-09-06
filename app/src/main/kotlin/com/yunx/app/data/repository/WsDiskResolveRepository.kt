/*
 * 吸析At - 「小飞机网盘 / 蓝奏云优享版」通用分享解析仓库（WsDiskApi 引擎封装）。
 *
 * v1.4.0 根治「无数个未知文件夹」：
 * - 旧版把所有条目的 fid 都写成共享级 fileIds、名称只读 fileName（文件夹字段
 *   实际是 folderName/name + folderId）→ 文件夹分享渲染成一串「未知名称」文件夹，
 *   且点击任何文件夹都重复返回同一份根列表，永远无法进入；
 * - 新版：文件夹条目 fid=folderId、文件条目 fid=自身 fileId；进入文件夹改走
 *   share/list 接口（真正的目录浏览，带翻页与去重防护）。
 *
 * stoken 打包：{p: 提取码, u: 分享者 userId}；下载时 downloadId 用户段取
 * 条目自带 userId → 分享者 userId → 空串（两站通用，服务端均接受）。
 */

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
            // 根目录：recommend/list（分享根内容）；子目录：share/list（真实目录浏览）
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
                    // 复用 fidToken 通道携带条目自带 userId（downloadId 用户段优先用它）
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
        // downloadId 用户段优先级：条目自带 userId → 分享者 userId → 空串
        //（小飞机用分享者 id（nfd/markcxx 同款）；优享版官网匿名传空串，但条目/分享者 id 亦被服务端接受）
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
