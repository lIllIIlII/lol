package com.yunx.app.data.network

object UCConstants {

    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    const val WEB_ORIGIN = "https://drive.uc.cn"
    const val DOWNLOAD_REFERER = "$WEB_ORIGIN/"
    const val LOGIN_URL = "https://drive.uc.cn/"

    const val COOKIE_DOMAIN = "https://drive.uc.cn"

    const val ACCOUNT_INFO_URL = "https://drive.uc.cn/account/info"

    const val API_BASE = "https://pc-api.uc.cn"

    const val SHARE_TOKEN_URL = "$API_BASE/1/clouddrive/share/sharepage/token?pr=UCBrowser&fr=pc"

    const val SHARE_DETAIL_URL = "$API_BASE/1/clouddrive/share/sharepage/v2/detail?pr=UCBrowser&fr=pc"

    const val TRANSFER_SHARE_DETAIL_URL = "$API_BASE/1/clouddrive/transfer_share/detail?entry=ft&fr=pc&pr=UCBrowser"

    const val DOWNLOAD_URL = "$API_BASE/1/clouddrive/file/download?entry=ft&fr=pc&pr=UCBrowser"

    const val PLAY_URL = "$API_BASE/1/clouddrive/file/v2/play/project?pr=UCBrowser&fr=pc"

    const val VIDEO_PREVIEW_URL = "$API_BASE/1/clouddrive/share/sharepage/video_preview"

    const val DEFAULT_PDIR_FID = "0"

    const val FILE_URL = "$API_BASE/1/clouddrive/file?pr=UCBrowser&fr=pc"

    const val SAVE_URL = "$API_BASE/1/clouddrive/share/sharepage/save?pr=UCBrowser&fr=pc"

    const val TASK_URL = "$API_BASE/1/clouddrive/task?pr=UCBrowser&fr=pc"

    const val CLOUD_FILE_SORT_URL = "$API_BASE/1/clouddrive/file/sort?pr=UCBrowser&fr=pc"

    const val RENAME_URL = "$API_BASE/1/clouddrive/file/rename?pr=UCBrowser&fr=pc"

    const val MOVE_URL = "$API_BASE/1/clouddrive/file/move?pr=UCBrowser&fr=pc"

    const val SHARE_CREATE_URL = "$API_BASE/1/clouddrive/share?pr=UCBrowser&fr=pc"

    const val SHARE_INFO_URL = "$API_BASE/1/clouddrive/share/password?pr=UCBrowser&fr=pc"

    const val CLOUD_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "uc-cloud-drive/1.6.1 Chrome/100.0.4896.160 Electron/18.3.5.16-b62cf9c50d Safari/537.36 Channel/ucpan_other_ch"

    const val CLOUD_DOWNLOAD_URL = "$API_BASE/1/clouddrive/file/download"

    const val DELETE_URL = "$API_BASE/1/clouddrive/file/delete?pr=UCBrowser&fr=pc"

    const val TEMP_DIR_NAME = "YunX临时转存"

    const val CONFIG_URL = "$API_BASE/1/clouddrive/config?pr=UCBrowser&fr=pc"

    const val PUUS_REFRESH_INTERVAL_MS = 90L * 60 * 1000

    fun isValidCookie(cookie: String?): Boolean =
        cookie != null && cookie.contains("__pus=") && cookie.contains("__puus=")
}
