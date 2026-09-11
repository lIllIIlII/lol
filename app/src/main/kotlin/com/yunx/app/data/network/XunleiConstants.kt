package com.yunx.app.data.network

object XunleiConstants {

    const val AUTH_BASE = "https://xluser-ssl.xunlei.com"

    const val PAN_BASE = "https://api-pan.xunlei.com"

    const val CLIENT_ID = "Xp6pAdwyJv9sQuoN"
    const val CLIENT_SECRET = "standard_a@api#"

    const val APP_CLIENT_ID = "Xp6vsxz_7IYVw2BB"
    const val APP_CLIENT_SECRET = "Xp6vsy4tN9toTVdMSpomVdXpRmES"

    const val APP_CLIENT_VERSION = "8.31.0.9726"
    const val APP_PACKAGE_NAME = "com.xunlei.downloadprovider"

    val CAPTCHA_SALTS = listOf(
        "9uJNVj/wLmdwKrJaVj/omlQ",
        "Oz64Lp0GigmChHMf/6TNfxx7O9PyopcczMsnf",
        "Eb+L7Ce+Ej48u",
        "jKY0",
        "ASr0zCl6v8W4aidjPK5KHd1Lq3t+vBFf41dqv5+fnOd",
        "wQlozdg6r1qxh0eRmt3QgNXOvSZO6q/GXK",
        "gmirk+ciAvIgA/cxUUCema47jr/YToixTT+Q6O",
        "5IiCoM9B1/788ntB",
        "P07JH0h6qoM6TSUAK2aL9T5s2QBVeY9JWvalf",
        "+oK0AN"
    )

    const val APP_UA =
        "ANDROID-com.xunlei.downloadprovider/8.31.0.9726 netWorkType/5G appid/40 " +
            "deviceName/Xiaomi_M2004j7ac deviceModel/M2004J7AC OSVersion/12 protocolVersion/301 " +
            "platformVersion/10 sdkVersion/512000 Oauth2Client/0.9 (Linux 4_14_186-perf-gddfs8vbb238b) (JAVA 0)"

    const val WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

    const val DEVICE_ID = "78a70629a2b17d0b4302317ffa94807a"

    const val PEER_ID = "92df4c42e0926ff55f1c605ebe4c3754"

    const val DEVICE_SIGN = "div101.78a70629a2b17d0b4302317ffa94807a31491e163e795b39e798ed33ae58858b"

    const val CAPTCHA_INIT_URL = "$AUTH_BASE/v1/shield/captcha/init"

    const val LOGIN_URL = "$AUTH_BASE/xluser.core.login/v3/login"

    const val SEND_SMS_URL = "$AUTH_BASE/xluser.core.login/v3/sendsms"

    const val SMS_LOGIN_URL = "$AUTH_BASE/xluser.core.login/v3/smslogin"

    const val TOKEN_URL = "$AUTH_BASE/v1/auth/signin/token"

    const val REFRESH_URL = "$AUTH_BASE/v1/auth/token"

    const val FILES_URL = "$PAN_BASE/drive/v1/files"

    const val SHARE_URL = "$PAN_BASE/drive/v1/share"

    const val SHARE_DETAIL_URL = "$PAN_BASE/drive/v1/share/detail"

    const val RESTORE_URL = "$PAN_BASE/drive/v1/share/restore"

    const val TASKS_URL = "$PAN_BASE/drive/v1/tasks"

    const val TEMP_DIR_NAME = "YunX临时转存"

    const val MOVE_URL = "$PAN_BASE/drive/v1/files:batchMove"

    const val TRASH_URL = "$PAN_BASE/drive/v1/files:batchTrash"

    const val SHARE_CREATE_URL = "$PAN_BASE/drive/v1/share"
}
