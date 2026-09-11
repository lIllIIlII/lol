package com.yunx.app.data.network.model

data class ShareSession(
    val shareId: String,
    val stoken: String,
    val title: String
)

data class ShareToken(
    val stoken: String,
    val title: String,
    val firstFid: String
)

data class ShareFile(
    val fid: String,
    val fname: String,
    val fsize: Long,
    val isdir: Boolean,
    val pdirFid: String,
    val fidToken: String,
    val modifyTime: String = ""
)

data class ShareInfo(
    val shareUrl: String,
    val passcode: String,
    val pwdId: String,
    val title: String,
    val expiredType: Int
)

data class QuotaInfo(
    val used: Long,
    val total: Long,
    val usedInTrash: Long = 0L
)

data class DownloadLink(
    val fid: String,
    val filename: String,
    val downloadUrl: String,
    val size: Long,
    val cleanupDirFid: String? = null,
    val isHls: Boolean = false
)

data class PlayLink(
    val url: String,
    val resolution: String,
    val format: String,
    val isHls: Boolean
)
