package com.yunx.app.data.repository

import com.yunx.app.data.network.Pan123Api
import com.yunx.app.data.network.ShareLinkParser
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareSession

class Pan123ResolveRepository(
    private val api: Pan123Api,
    private val tokenProvider: suspend () -> String?
) : ShareResolveRepository {

    override suspend fun createSession(link: String, pwd: String?, cookie: String): Result<ShareSession> {
        val parsed = ShareLinkParser.parse(link)
            ?: return Result.failure(IllegalArgumentException("无法识别分享链接"))
        return runCatching {
            val sharePwd = pwd?.takeIf { it.isNotBlank() } ?: parsed.pwd.orEmpty()
            val (files, _) = api.getShareFiles(parsed.shareId, sharePwd, "0", "0", 1)
            val title = files.firstOrNull()?.fname?.takeIf { it.isNotBlank() } ?: parsed.shareId
            ShareSession(shareId = parsed.shareId, stoken = sharePwd, title = title)
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun listFiles(session: ShareSession, dirFid: String, cookie: String): Result<List<ShareFile>> =
        runCatching {
            val all = mutableListOf<ShareFile>()
            var page = 1
            do {
                val (files, nextCursor) = api.getShareFiles(session.shareId, session.stoken, dirFid, "0", page)
                all += files
                val hasMore = files.isNotEmpty() && nextCursor != null
                page++
            } while (hasMore && page < 50)
            all
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) }
        )

    override suspend fun ensureTempDir(cookie: String): Result<String> =
        Result.failure(UnsupportedOperationException("123 分享无需转存"))

    override suspend fun transferFile(
        session: ShareSession,
        file: ShareFile,
        toDirFid: String,
        cookie: String
    ): Result<String> = runCatching {
        val token = cookie.ifBlank { tokenProvider() ?: "" }
        if (token.isBlank()) throw IllegalStateException("请先登录123云盘")
        val (taskId, shareId) = api.copySave(
            shareKey = session.shareId,
            sharePwd = session.stoken,
            file = file,
            toDirFid = toDirFid.ifBlank { "0" },
            token = token
        ) ?: throw IllegalStateException("创建转存任务失败")
        api.pollCopySave(taskId, shareId, token)
            ?: throw IllegalStateException("转存超时或失败")
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it) }
    )

    override suspend fun getDownloadLink(fid: String, cookie: String): Result<DownloadLink> {
        val token = cookie.ifBlank { tokenProvider() ?: "" }
        if (token.isBlank()) return Result.failure(IllegalStateException("请先登录123云盘"))
        return runCatching {
            val file = ShareFile(fid = fid, fname = "", fsize = 0L, isdir = false, pdirFid = "", fidToken = "")
            api.getDownloadLink(file, token) ?: throw IllegalStateException("获取下载链接失败")
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun getSavedFileDownloadLink(fid: String, dirFid: String, cookie: String): Result<DownloadLink> {
        val token = cookie.ifBlank { tokenProvider() ?: "" }
        if (token.isBlank()) return Result.failure(IllegalStateException("请先登录123云盘"))
        return runCatching {
            val files = runCatching { api.listCloudFiles(dirFid.ifBlank { "0" }, token) }.getOrDefault(emptyList())
            val target = files.firstOrNull { it.fid == fid }
                ?: throw IllegalStateException("转存文件尚未就绪，请稍后在网盘页下载")
            api.getDownloadLink(target, token) ?: throw IllegalStateException("获取下载链接失败")
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun getShareDownloadLink(
        session: ShareSession,
        file: ShareFile,
        cookie: String
    ): Result<DownloadLink> = runCatching {
        val token = cookie.ifBlank { tokenProvider() ?: "" }
        if (token.isBlank()) throw IllegalStateException("请先登录123云盘")
        val link = api.getShareDownloadLink(session.shareId, file, token)
            ?: throw IllegalStateException("获取下载链接失败")
        link.copy(filename = file.fname.ifBlank { link.filename })
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it) }
    )
}
