package com.yunx.app.data.repository

import com.yunx.app.data.network.BaiduApi
import com.yunx.app.data.network.BaiduConstants
import com.yunx.app.data.network.ShareLinkParser
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareSession

class BaiduResolveRepository(private val api: BaiduApi) : ShareResolveRepository {

    private val sekeys = mutableMapOf<String, String>()

    private val shareInfos = mutableMapOf<String, Pair<String, String>>()

    override suspend fun createSession(link: String, pwd: String?, cookie: String): Result<ShareSession> =
        runCatching {
            val parsed = ShareLinkParser.parse(link)
                ?: throw IllegalArgumentException("无法识别百度分享链接")
            val surl = parsed.shareId
            val effectivePwd = pwd?.takeIf { it.isNotBlank() } ?: parsed.pwd
            val sekey = if (effectivePwd.isNullOrBlank()) {
                ""
            } else {
                api.verifyShare(surl, effectivePwd, cookie)
            }
            sekeys[surl] = sekey
            ShareSession(surl, sekey, "")
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) }
        )

    override suspend fun listFiles(session: ShareSession, dirFid: String, cookie: String): Result<List<ShareFile>> =
        runCatching {
            val sekey = session.stoken.ifBlank { sekeys[session.shareId] ?: "" }
            val all = mutableListOf<ShareFile>()
            var page = 1
            var result: com.yunx.app.data.network.BaiduShareList
            do {
                result = api.listShare(session.shareId, sekey, dirFid, cookie, page)
                all += result.files
                page++
            } while (result.files.size == 100 && page <= 100)
            shareInfos[session.shareId] = result.shareId to result.uk
            all
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) }
        )

    override suspend fun ensureTempDir(cookie: String): Result<String> = runCatching {
        val dir = "/${BaiduConstants.TEMP_DIR_NAME}"
        val exists = runCatching { api.listDir("/", cookie).any { it == dir } }.getOrDefault(false)
        val ok = exists || api.createDir(dir, cookie)
        if (ok) dir else "/"
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
        val (shareId, uk) = requireShareInfo(session, cookie)
        val result = api.transfer(shareId, uk, session.stoken, file.fid, toDirFid, cookie)
        result.fsId
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it) }
    )

    override suspend fun getDownloadLink(fid: String, cookie: String): Result<DownloadLink> = runCatching {
        val dlink = api.fileMetasDlink(fid, cookie)
        DownloadLink(fid = fid, filename = "", downloadUrl = dlink, size = 0L)
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it) }
    )

    override suspend fun getShareDownloadLink(
        session: ShareSession,
        file: ShareFile,
        cookie: String
    ): Result<DownloadLink> = runCatching {
        val (shareId, uk) = requireShareInfo(session, cookie)
        val dirPath = ensureTempDir(cookie).getOrThrow()
        val transferred = api.transfer(shareId, uk, session.stoken, file.fid, dirPath, cookie)
        val dlink = api.locateDownload(transferred.path, cookie)
        deleteTransferred(transferred.path, cookie)
        DownloadLink(
            fid = transferred.fsId,
            filename = file.fname,
            downloadUrl = dlink,
            size = file.fsize
        )
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it) }
    )

    private suspend fun deleteTransferred(path: String, cookie: String) {
        runCatching { api.deleteFile(path, cookie) }
        val tempDir = "/${BaiduConstants.TEMP_DIR_NAME}"
        if (path.startsWith("$tempDir/")) {
            runCatching { api.deleteFile(tempDir, cookie) }
        }
    }

    private suspend fun requireShareInfo(session: ShareSession, cookie: String): Pair<String, String> {
        shareInfos[session.shareId]?.let { return it }
        val sekey = session.stoken.ifBlank { sekeys[session.shareId] ?: "" }
        val result = api.listShare(session.shareId, sekey, "/", cookie)
        val info = result.shareId to result.uk
        shareInfos[session.shareId] = info
        return info
    }
}
