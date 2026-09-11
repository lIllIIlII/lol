package com.yunx.app.data.network

object QuarkConstants {

    const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
    "Chrome/130.0.0.0 Safari/537.36 QuarkPC/6.0.8.649"

    const val LOGIN_URL = "https://pan.quark.cn/?fr=pc&platform=pc"

    const val COOKIE_DOMAIN = "https://pan.quark.cn"

    const val ACCOUNT_INFO_URL = "https://pan.quark.cn/account/info"

            const val API_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
    "quark-cloud-drive/2.5.20 Chrome/100.0.4896.160 Electron/18.3.5.12-a038f7b798 Safari/537.36 Channel/pckk_other_ch"

    const val API_BASE = "https://drive-pc.quark.cn"

    const val SHARE_TOKEN_URL = "$API_BASE/1/clouddrive/share/sharepage/token?pr=ucpro&fr=pc"

    const val SHARE_PASSWORD_URL = "$API_BASE/1/clouddrive/share/password?pr=ucpro&fr=pc"

    const val SHARE_DETAIL_URL = "$API_BASE/1/clouddrive/share/sharepage/detail?pr=ucpro&fr=pc"

    const val DOWNLOAD_URL = "$API_BASE/1/clouddrive/file/download?pr=ucpro&fr=pc&sys=win32&ve=3.23.2"

    const val DEFAULT_PDIR_FID = "0"

    const val FILE_URL = "$API_BASE/1/clouddrive/file?pr=ucpro&fr=pc"

    const val CLOUD_FILE_SORT_URL = "$API_BASE/1/clouddrive/file/sort?pr=ucpro&fr=pc"

    const val SAVE_URL = "$API_BASE/1/clouddrive/share/sharepage/save?pr=ucpro&fr=pc"

    const val TASK_URL = "$API_BASE/1/clouddrive/task?pr=ucpro&fr=pc"

    const val DELETE_URL = "$API_BASE/1/clouddrive/file/delete?pr=ucpro&fr=pc&uc_param_str="

    const val RENAME_URL = "$API_BASE/1/clouddrive/file/rename?pr=ucpro&fr=pc&uc_param_str="

    const val MOVE_URL = "$API_BASE/1/clouddrive/file/move?pr=ucpro&fr=pc&uc_param_str="

    const val SHARE_CREATE_URL = "$API_BASE/1/clouddrive/share?pr=ucpro&fr=pc&uc_param_str="

    const val SHARE_INFO_URL = "$API_BASE/1/clouddrive/share/password?pr=ucpro&fr=pc&uc_param_str="

    const val TEMP_DIR_NAME = "YunX临时转存"

    const val TEMP_SUBDIR_PREFIX = "tr_"

    const val DOWNLOAD_REFERER = "https://pan.quark.cn/"

    const val CONFIG_URL = "$API_BASE/1/clouddrive/config?pr=ucpro&fr=pc"

    const val PUUS_REFRESH_INTERVAL_MS = 90L * 60 * 1000

    fun isValidCookie(cookie: String?): Boolean =
        cookie != null && cookie.contains("__pus=") && cookie.contains("__puus=")
}
