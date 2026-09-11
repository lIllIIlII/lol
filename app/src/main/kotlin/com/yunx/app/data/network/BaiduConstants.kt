package com.yunx.app.data.network

object BaiduConstants {

    const val LOGIN_URL = "https://pan.baidu.com/"

    const val COOKIE_DOMAIN = "https://pan.baidu.com"

    const val UA_WEB =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    const val UA_NETDISK =
        "netdisk;12.24.6;piano;android-android;16;JSbridge4.4.0;jointBridge;1.1.0"

    const val APP_ID = "250528"

    const val TEMP_DIR_NAME = "YunX临时转存"

    fun isValidCookie(cookie: String?): Boolean =
        cookie != null && cookie.contains("BDUSS=")
}
