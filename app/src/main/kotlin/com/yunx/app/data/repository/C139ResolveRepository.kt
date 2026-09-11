package com.yunx.app.data.repository

import com.yunx.app.data.network.C139Api
import com.yunx.app.data.network.C139Constants
import com.yunx.app.data.network.ShareLinkParser
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareSession

class C139ResolveRepository(private val api: C139Api) : ShareResolveRepository {

    override suspend fun createSession(link: String, pwd: String?, cookie: String): Result<ShareSession> {
        val parsed = ShareLinkParser.parse(link)
            ?: return Result.failure(IllegalArgumentException("无法识别分享链接"))
        if (C139Constants.extractAccountFull(cookie).isNullOrBlank()) {
            return Result.failure(IllegalStateException("登录态缺少账号信息，请重新登录"))
        }
        return runCatching {
            val leakedPwd = api.getOutLinkPassword(parsed.shareId)
            val passwd = pwd?.takeIf { it.isNotBlank() } ?: leakedPwd.orEmpty()
            val title = api.getOutLinkTitle(parsed.shareId)
                ?.takeIf { it.isNotBlank() } ?: parsed.shareId
            ShareSession(parsed.shareId, passwd, title)
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun listFiles(session: ShareSession, dirFid: String, cookie: String): Result<List<ShareFile>> =
        runCatching {
            val pcaId = if (dirFid == "0" || dirFid.isBlank()) "root" else dirFid
            val all = mutableListOf<ShareFile>()
            var begin = 1
            do {
                val batch = api.getShareFiles(session.shareId, pcaId, session.stoken, begin, begin + 199)
                all += batch
                begin += 200
            } while (batch.size == 200 && begin <= 20_000)
            all
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) }
        )

    override suspend fun ensureTempDir(cookie: String): Result<String> =
        Result.failure(UnsupportedOperationException("139 分享无需转存"))

    override suspend fun transferFile(
        session: ShareSession,
        file: ShareFile,
        toDirFid: String,
        cookie: String
    ): Result<String> = runCatching {
        val account = C139Constants.extractAccountFull(cookie)
            ?: throw IllegalStateException("登录态缺少账号信息，请重新登录")
        val authorization = C139Constants.extractAuthorization(cookie)
        val taskId = api.createTransferTask(
            coIDLst = listOf(file.fid),
            catalogIDLst = emptyList(),
            toFolderId = toDirFid,
            linkID = session.shareId,
            account = account,
            authorization = authorization
        ) ?: throw IllegalStateException("创建转存任务失败")
        var newId: String? = null
        for (i in 0 until 30) {
            kotlinx.coroutines.delay(800)
            val result = api.queryTransferTask(taskId, account, authorization)
            if (result.done) {
                newId = result.mapping[file.fid]
                break
            }
        }
        newId ?: throw IllegalStateException("转存超时或失败")
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it) }
    )

    override suspend fun getDownloadLink(fid: String, cookie: String): Result<DownloadLink> = runCatching {
        api.getDownloadUrl(fid, cookie) ?: throw IllegalStateException("获取下载链接失败")
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it) }
    )

    override suspend fun getShareDownloadLink(
        session: ShareSession,
        file: ShareFile,
        cookie: String
    ): Result<DownloadLink> = runCatching {
        val account = C139Constants.extractAccountFull(cookie)
            ?: throw IllegalStateException("登录态缺少账号信息，请重新登录")
        val authorization = C139Constants.extractAuthorization(cookie)
        val link = api.getShareDownloadLink(file.fid, session.shareId, account, authorization)
            ?: throw IllegalStateException("获取下载链接失败")
        link.copy(filename = file.fname.ifBlank { link.filename })
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it) }
    )
}
