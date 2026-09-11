package com.yunx.app.data.network

object Pan123Constants {

    const val API_BASE = "https://yun.123pan.cn"

    const val DOWNLOAD_BASE = "https://www.123865.com"

    const val WEB_LOGIN_URL = "https://yun.123pan.cn/"

    const val LOCAL_STORAGE_TOKEN_KEY = "authorToken"

    const val SHARE_GET_URL = "$API_BASE/b/api/share/get"

    const val SHARE_DOWNLOAD_INFO_URL = "$DOWNLOAD_BASE/b/api/share/download/info"

    const val FILE_LIST_URL = "$API_BASE/b/api/file/list/new"

    const val FILE_DOWNLOAD_INFO_URL = "$API_BASE/api/file/download_info"

    const val TRAFFIC_CHECK_URL = "$API_BASE/b/api/file/download/traffic/check"

    const val FILE_TRASH_URL = "$API_BASE/b/api/file/trash"

    const val FILE_RENAME_URL = "$API_BASE/b/api/file/rename"

    const val FILE_MOD_PID_URL = "$API_BASE/b/api/file/mod_pid"

    const val SHARE_CREATE_URL = "$API_BASE/b/api/share/create"

    const val USER_INFO_URL = "$API_BASE/b/api/user/info"

    const val WEB_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36"

    const val DART_UA = "Dart/3.12 (dart:io)"

    const val PLATFORM_WEB = "web"

    const val PLATFORM_ANDROID = "android"

    const val APP_VERSION_WEB = "3"

    const val APP_VERSION_ANDROID = "39"

    const val DOWNLOAD_REFERER = "https://yun.123pan.cn/"

    const val SIGN_TABLE = "adefghlmyijnopkqrstubcvwsz"

    const val SIGN_OS = "web"

    const val SIGN_VER = "3"

    const val SIGN_OFFSET_SECONDS = 57600L

    const val EXPIRATION_FOREVER = "2099-12-12T08:00:00+08:00"

    fun newLoginUuid(): String {
        val chars = "0123456789abcdef"
        return buildString {
            repeat(32) { append(chars.random()) }
        }
    }
}
