package com.yunx.app.data.repository

import com.yunx.app.data.network.ShareLinkParser
import com.yunx.app.data.network.XunleiApi
import com.yunx.app.data.network.XunleiConstants
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareSession

class XunleiResolveRepository(
    private val api: XunleiApi,
    private val accountProvider: suspend () -> String?,
    private val deviceIdProvider: suspend () -> String?,
    private val captchaProvider: suspend () -> String?,
    private val refreshProvider: (suspend () -> Pair<String, String>?)? = null
) : ShareResolveRepository {

    private val passCodes = mutableMapOf<String, String>()

    private suspend fun token(): String =
        accountProvider() ?: throw IllegalStateException("请先登录迅雷网盘")

    private suspend fun access(): String {
        ensureFreshToken()
        val t = token()
        api.cacheUserId(t)
        return t
    }

    private suspend fun ensureFreshToken() {
        val acc = accountProvider() ?: return
        val exp = api.jwtExp(acc)
        if (exp > 0 && exp - System.currentTimeMillis() / 1000 > 60) return
        if (refreshProvider?.invoke() == null) {
            throw IllegalStateException("迅雷登录已过期，请重新登录")
        }
    }

    private suspend fun deviceId(): String =
        deviceIdProvider() ?: throw IllegalStateException("缺少设备标识")

    private suspend fun captcha(): String = captchaProvider() ?: ""

    override suspend fun createSession(link: String, pwd: String?, cookie: String): Result<ShareSession> =
        runCatching {
            val shareId = ShareLinkParser.parse(link)?.shareId
                ?: throw IllegalArgumentException("无法识别迅雷分享链接")
            val effectivePwd = pwd?.takeIf { it.isNotBlank() } ?: ShareLinkParser.parse(link)?.pwd ?: ""
            passCodes[shareId] = effectivePwd
            val access = access()
            val result = api.getShare(shareId, effectivePwd, access, deviceId(), captcha())
                ?: throw IllegalStateException("未获取到分享信息")
            ShareSession(shareId, result.passCodeToken, result.title)
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) }
        )

    override suspend fun listFiles(session: ShareSession, dirFid: String, cookie: String): Result<List<ShareFile>> =
        runCatching {
            val access = access()
            val files = mutableListOf<ShareFile>()
            var pageToken = ""
            var pages = 0
            do {
                val next = if (dirFid.isBlank() || dirFid == "0") {
                    val page = api.getShare(
                        session.shareId, passCodes[session.shareId] ?: "", access,
                        deviceId(), captcha(), pageToken
                    ) ?: throw IllegalStateException("未获取到文件列表")
                    files += page.files
                    page.nextPageToken
                } else {
                    val page = api.getShareDetail(
                        session.shareId, dirFid, session.stoken, access,
                        deviceId(), captcha(), pageToken
                    ) ?: throw IllegalStateException("未获取到文件列表")
                    files += page.files
                    page.nextPageToken
                }
                pageToken = next
                pages++
            } while (pageToken.isNotBlank() && pages < 100)
            files
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) }
        )

    override suspend fun ensureTempDir(cookie: String): Result<String> = runCatching {
        api.ensureTempDir(access(), deviceId(), captcha())
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
        val newId = api.restore(
            shareId = session.shareId,
            passCodeToken = session.stoken,
            parentFolderId = toDirFid,
            fileIds = listOf(file.fid),
            accessToken = access(),
            deviceId = deviceId(),
            captchaToken = captcha()
        ) ?: throw IllegalStateException("转存失败")
        newId
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it) }
    )

    override suspend fun getDownloadLink(fid: String, cookie: String): Result<DownloadLink> = runCatching {
        api.getFileDetail(fid, access(), deviceId(), captcha())
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
        val dirFid = ensureTempDir(cookie).getOrThrow()
        val savedFid = transferFile(session, file, dirFid, cookie).getOrThrow()
        val link = api.getFileDetail(savedFid, access(), deviceId(), captcha())
            ?: throw IllegalStateException("获取下载链接失败")
        runCatching { api.batchDelete(listOf(savedFid), access(), deviceId(), captcha()) }
        link
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it) }
    )
}
