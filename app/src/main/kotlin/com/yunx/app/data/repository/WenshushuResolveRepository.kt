/*
 * 吸析At - 文叔叔（wenshushu.cn）分享解析仓库。
 *
 * stoken 打包 {t: tid, b: bid, p: 根 pid, w: 密码}；
 * 目录浏览：根 → nlist(bid, 根pid)；子目录 → nlist(bid, folderFid)；
 * 直链：dl/sign(fileFid)（匿名 token 由 API 层缓存管理）。
 */

package com.yunx.app.data.repository

import com.yunx.app.data.network.ShareLinkParser
import com.yunx.app.data.network.WenshushuApi
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareSession
import org.json.JSONObject

class WenshushuResolveRepository : ShareResolveRepository {

    private fun packStoken(tid: String, bid: String, rootPid: String, pwd: String): String =
        JSONObject().put("t", tid).put("b", bid).put("p", rootPid).put("w", pwd).toString()

    private fun unpack(stoken: String): Array<String> = runCatching {
        val j = JSONObject(stoken)
        arrayOf(j.optString("t"), j.optString("b"), j.optString("p"), j.optString("w"))
    }.getOrDefault(arrayOf("", "", "", ""))

    override suspend fun createSession(link: String, pwd: String?, cookie: String): Result<ShareSession> {
        val parsed = ShareLinkParser.parse(link)
            ?: return Result.failure(IllegalArgumentException("无法识别文叔叔链接"))
        return runCatching {
            val sharePwd = pwd?.takeIf { it.isNotBlank() } ?: parsed.pwd.orEmpty()
            val task = WenshushuApi.fetchTaskInfo(parsed.shareId, sharePwd)
            ShareSession(
                shareId = task.tid,
                stoken = packStoken(task.tid, task.bid, task.rootPid, sharePwd),
                title = "文叔叔分享（${task.fileCount} 项，${formatSize(task.fileSize)}）"
            )
        }
    }

    override suspend fun listFiles(session: ShareSession, dirFid: String, cookie: String): Result<List<ShareFile>> {
        val (tid, bid, rootPid, pwd) = unpack(session.stoken)
        return runCatching {
            if (bid.isBlank()) throw IllegalStateException("会话已失效，请重新解析")
            val pid = dirFid.ifBlank { rootPid }
            val entries = WenshushuApi.listFiles(bid, pid)
            if (entries.isEmpty()) throw IllegalStateException("该目录为空")
            entries.map { e ->
                ShareFile(
                    fid = e.fid,
                    fname = e.fname,
                    fsize = e.size,
                    isdir = e.isDir,
                    pdirFid = pid,
                    fidToken = "",
                    modifyTime = ""
                )
            }
        }
    }

    override suspend fun ensureTempDir(cookie: String): Result<String> =
        Result.failure(UnsupportedOperationException("文叔叔无需转存"))

    override suspend fun transferFile(
        session: ShareSession,
        file: ShareFile,
        toDirFid: String,
        cookie: String
    ): Result<String> = Result.failure(UnsupportedOperationException("文叔叔暂不支持转存"))

    override suspend fun getDownloadLink(fid: String, cookie: String): Result<DownloadLink> =
        Result.failure(UnsupportedOperationException("文叔叔请使用分享直链下载"))

    override suspend fun getShareDownloadLink(
        session: ShareSession,
        file: ShareFile,
        cookie: String
    ): Result<DownloadLink> {
        val (tid, bid, rootPid, pwd) = unpack(session.stoken)
        return runCatching {
            if (file.isdir) throw IllegalStateException("文件夹请进入后选择文件下载")
            val url = WenshushuApi.fetchDirectLink(file.fid)
            DownloadLink(
                fid = file.fid,
                filename = file.fname.ifBlank { "wss_file" },
                downloadUrl = url,
                size = file.fsize
            )
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0B"
        val gb = bytes / 1024.0 / 1024 / 1024
        val mb = bytes / 1024.0 / 1024
        return when {
            gb >= 1 -> String.format("%.1fGB", gb)
            mb >= 1 -> String.format("%.0fMB", mb)
            else -> "${bytes / 1024}KB"
        }
    }
}
