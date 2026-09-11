package com.yunx.app.data.repository

import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareSession

interface ShareResolveRepository {
    suspend fun createSession(link: String, pwd: String?, cookie: String): Result<ShareSession>
    suspend fun listFiles(session: ShareSession, dirFid: String, cookie: String): Result<List<ShareFile>>
    suspend fun ensureTempDir(cookie: String): Result<String>
    suspend fun transferFile(
        session: ShareSession,
        file: ShareFile,
        toDirFid: String,
        cookie: String
    ): Result<String>
    suspend fun getDownloadLink(fid: String, cookie: String): Result<DownloadLink>

    suspend fun getShareDownloadLink(
        session: ShareSession,
        file: ShareFile,
        cookie: String
    ): Result<DownloadLink>

    suspend fun cleanupTempDir(dirFid: String, cookie: String) {}
}
