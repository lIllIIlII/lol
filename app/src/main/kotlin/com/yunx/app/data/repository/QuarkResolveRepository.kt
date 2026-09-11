package com.yunx.app.data.repository

import com.yunx.app.data.network.QuarkApi
import com.yunx.app.data.network.QuarkConstants
import com.yunx.app.data.network.ShareLinkParser
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareSession

class QuarkResolveRepository(private val api: QuarkApi) : ShareResolveRepository {

    override suspend fun createSession(link: String, pwd: String?, cookie: String): Result<ShareSession> {
        val parsed = ShareLinkParser.parse(link)
            ?: return Result.failure(IllegalArgumentException("无法识别分享链接"))
        val effectivePwd = pwd?.takeIf { it.isNotBlank() } ?: parsed.pwd
        return runCatching {
            val token = api.getShareToken(parsed.shareId, effectivePwd, cookie)
                ?: throw IllegalStateException("未获取到分享凭证")
            ShareSession(parsed.shareId, token.stoken, token.title)
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
                val batch = api.getShareFiles(session.shareId, session.stoken, dirFid, cookie, page, 100)
                    ?: throw IllegalStateException("未获取到文件列表")
                all += batch
                page++
            } while (batch.size == 100 && page <= 100)
            all
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) }
        )

    override suspend fun ensureTempDir(cookie: String): Result<String> = runCatching {
        val rootFiles = api.getFileList(QuarkConstants.DEFAULT_PDIR_FID, cookie)
            ?: throw IllegalStateException("获取网盘目录失败")
        rootFiles.firstOrNull { it.isdir && it.fname == QuarkConstants.TEMP_DIR_NAME }?.fid
            ?: api.createFolder(QuarkConstants.TEMP_DIR_NAME, QuarkConstants.DEFAULT_PDIR_FID, cookie)
            ?: throw IllegalStateException("创建临时目录失败")
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it) }
    )

    override suspend fun transferFile(
        session: ShareSession,
        file: ShareFile,
        toDirFid: String,
        cookie: String
    ): Result<String> = runCatching {
        val taskId = api.saveShareFile(
            shareId = session.shareId,
            stoken = session.stoken,
            pdirFid = file.pdirFid,
            fid = file.fid,
            fidToken = file.fidToken,
            toPdirFid = toDirFid,
            cookie = cookie
        ) ?: throw IllegalStateException("转存失败")
        api.pollTask(taskId, cookie)
            ?: throw IllegalStateException("转存超时，请稍后重试")
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it) }
    )

    override suspend fun getDownloadLink(fid: String, cookie: String): Result<DownloadLink> = runCatching {
        api.getDownloadLink(fid, cookie)
            ?: throw IllegalStateException("获取下载链接失败")
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it) }
    )

    override suspend fun getShareDownloadLink(
        session: ShareSession,
        file: ShareFile,
        cookie: String
    ): Result<DownloadLink> = runCatching {
        val baseDir = ensureTempDir(cookie).getOrThrow()

        val subDirName = "tr_${System.nanoTime()}_${(Math.random() * 1_000_000).toInt()}"
        val subDirFid = api.createFolder(subDirName, baseDir, cookie)
            ?: throw IllegalStateException("创建临时转存目录失败")

        val savedFid = transferFileTo(session, file, subDirFid, cookie).getOrThrow()
        val link = api.getDownloadLink(savedFid, cookie)
            ?: throw IllegalStateException("获取下载链接失败")

        link.copy(cleanupDirFid = subDirFid)
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it) }
    )

    suspend fun saveToCloud(
        session: ShareSession,
        file: ShareFile,
        toDirFid: String,
        cookie: String
    ): Result<String> = transferFileTo(session, file, toDirFid, cookie)

    private suspend fun transferFileTo(
        session: ShareSession,
        file: ShareFile,
        toDirFid: String,
        cookie: String
    ): Result<String> = runCatching {
        val taskId = api.saveShareFile(
            shareId = session.shareId,
            stoken = session.stoken,
            pdirFid = file.pdirFid,
            fid = file.fid,
            fidToken = file.fidToken,
            toPdirFid = toDirFid,
            cookie = cookie
        ) ?: throw IllegalStateException("转存失败")
        api.pollTask(taskId, cookie)
            ?: throw IllegalStateException("转存超时，请稍后重试")
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it) }
    )

    override suspend fun cleanupTempDir(dirFid: String, cookie: String) {
        runCatching { api.deleteFile(dirFid, cookie) }
    }
}
