package com.yunx.app.data.network

import android.util.Base64
import java.nio.charset.StandardCharsets

object C139Constants {

    const val LOGIN_URL = "https://yun.139.com/m/#/login"

    const val COOKIE_DOMAIN = "https://mail.10086.cn"

    const val COOKIE_DOMAIN_BACKUP = "https://yun.139.com"

    const val SHARE_BASE = "https://share-kd-njs.yun.139.com"

    const val SHARE_LIST_URL = "$SHARE_BASE/yun-share/richlifeApp/devapp/IOutLink/getOutLinkInfoV6"

    const val SHARE_LINK_URL = "$SHARE_BASE/yun-share/richlifeApp/devapp/IOutLink/dlFromOutLinkV3"

    const val SHARE_GENERAL_URL = "$SHARE_BASE/yun-share/richlifeApp/devapp/IOutLink/getOutLinkGeneral"

    const val SHARE_AES_KEY = "PVGDwmcvfs1uV3d1"

    const val CLOUD_BASE = "https://personal-kd-njs.yun.139.com"

    const val YUN_CHANNEL_SOURCE = "10000034"

    const val MCLOUD_VERSION = "7.17.9"

    const val MCLOUD_CLIENT = "10701"

    const val MCLOUD_CHANNEL = "1000101"

    const val YUN_MODULE_TYPE = "100"

    const val M4C_SRC = "10002"

    const val M4C_CALLER = "PC"

    const val X_DEVICEINFO = "||9|7.17.9|chrome|116.0.0.0|2cdaf7ada9e353c70eba99092e177991||windows 10||zh-CN|||"

    const val X_CLIENT_INFO = "||9|7.17.9|chrome|116.0.0.0|2cdaf7ada9e353c70eba99092e177991||windows 10||zh-CN|||dW5kZWZpbmVk||"

    const val FILE_LIST_URL = "$CLOUD_BASE/hcy/file/list"

    const val FILE_UPDATE_URL = "$CLOUD_BASE/hcy/file/update"

    const val BATCH_MOVE_URL = "$CLOUD_BASE/hcy/file/batchMove"

    const val BATCH_TRASH_URL = "$CLOUD_BASE/hcy/recyclebin/batchTrash"

    const val DOWNLOAD_URL = "$CLOUD_BASE/hcy/file/getDownloadUrl"

    const val TASK_GET_URL = "$CLOUD_BASE/hcy/task/get"

    const val OUTLINK_CREATE_URL =
        "https://yun.139.com/orchestration/personalCloud-rebuild/outlink/v1.0/getOutLink"

    const val TRANSFER_CREATE_URL =
        "$SHARE_BASE/yun-share/richlifeApp/devapp/IBatchOprTask/createOuterLinkBatchOprTask"

    const val TRANSFER_QUERY_URL =
        "$SHARE_BASE/yun-share/richlifeApp/devapp/IBatchOprTask/queryBatchOprTaskDetail"

    const val SHARE_X_DEVICEINFO = "||3|12.27.0|||||chrome 150.0.0.0|360X444|zh-cn|||"
    const val SHARE_X_HUAWEI_CHANNELSRC = "10245500"
    const val SHARE_X_MM_SOURCE = "0002"

    const val SHARE_MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/150.0.0.0 Mobile Safari/537.36"

    const val PC_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Safari/537.36"

    private val REQUIRED_FAST_KEYS = setOf("Os_SSo_Sid", "RMKEY")

    private val KEEP_KEYS = setOf(
        "Os_SSo_Sid", "RMKEY", "UserData", "Login_UserNumber",
        "_139_index_isLoginType", "UUIDToken", "JSESSIONID",
        "areaCode8011", "provCode8011",
        "authorization", "auth_token", "token", "ud_id",
        "ORCHES-I-ACCOUNT-SIMPLIFY", "ORCHES-I-ACCOUNT-ENCRYPT", "nation_code",
        "platform", "cutover_status", "isUserDomainError", "a_k", "skey", "WT_FPC",
        "hecaiyun_stay_url", "hecaiyun_stay_time",
        "hecaiyundata2021jssdkcross", "sajssdk_2015_cross_new_user"
    )

    fun extractCookies(getCookie: (String) -> String?): String {
        val out = linkedMapOf<String, String>()
        val domains = listOf(COOKIE_DOMAIN, COOKIE_DOMAIN_BACKUP)
        for (domain in domains) {
            val raw = getCookie(domain) ?: continue
            for (kv in raw.split(";")) {
                val kv2 = kv.trim()
                val eq = kv2.indexOf('=')
                if (eq <= 0) continue
                val k = kv2.substring(0, eq)
                val v = kv2.substring(eq + 1)
                if (k in KEEP_KEYS && k !in out) out[k] = v
            }
        }
        return out.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    fun isValidCookie(cookie: String?): Boolean {
        if (cookie.isNullOrBlank()) return false
        if (cookie.split(";").any {
                val kv = it.trim()
                kv.startsWith("authorization=") && kv.length > "authorization=".length
            }
        ) return true
        return REQUIRED_FAST_KEYS.all { key ->
            cookie.split(";").any {
                val kv = it.trim()
                kv.startsWith("$key=") && kv.length > key.length + 1
            }
        }
    }

    fun extractAuthorization(cookie: String?): String? {
        if (cookie.isNullOrBlank()) return null
        for (kv in cookie.split(";")) {
            val kv2 = kv.trim()
            if (kv2.startsWith("authorization=")) {
                val v = kv2.substringAfter('=')
                if (v.isNotBlank()) return v
            }
        }
        return null
    }

    fun extractAccountFull(cookie: String?): String? {
        if (cookie.isNullOrBlank()) return null
        cookie.split(";").forEach { kv ->
            val kv2 = kv.trim()
            if (kv2.startsWith("ORCHES-I-ACCOUNT-ENCRYPT=")) {
                val v = kv2.substringAfter('=')
                if (v.isNotBlank()) {
                    val decoded = runCatching {
                        String(Base64.decode(v, Base64.DEFAULT), StandardCharsets.UTF_8)
                    }.getOrNull()
                    if (!decoded.isNullOrBlank()) return decoded
                }
            }
        }
        extractAuthorization(cookie)?.let { auth ->
            val account = runCatching {
                val b64 = auth.removePrefix("Basic").trim()
                String(Base64.decode(b64, Base64.DEFAULT), StandardCharsets.UTF_8)
                    .split(":").getOrNull(1)
            }.getOrNull()
            if (!account.isNullOrBlank()) return account
        }
        cookie.split(";").forEach { kv ->
            val kv2 = kv.trim()
            if (kv2.startsWith("Login_UserNumber=")) {
                val v = kv2.substringAfter('=')
                if (v.isNotBlank()) return v
            }
        }
        return null
    }

    fun extractAccount(cookie: String?): String? {
        if (cookie.isNullOrBlank()) return null
        cookie.split(";").forEach { kv ->
            val kv2 = kv.trim()
            if (kv2.startsWith("ORCHES-I-ACCOUNT-SIMPLIFY=")) {
                val v = kv2.substringAfter('=')
                if (v.isNotBlank()) return v
            }
        }
        cookie.split(";").forEach { kv ->
            val kv2 = kv.trim()
            if (kv2.startsWith("ORCHES-I-ACCOUNT-ENCRYPT=")) {
                val v = kv2.substringAfter('=')
                if (v.isNotBlank()) {
                    return runCatching {
                        String(Base64.decode(v, Base64.DEFAULT), StandardCharsets.UTF_8)
                    }.getOrNull()?.takeIf { it.isNotBlank() } ?: v
                }
            }
        }
        cookie.split(";").forEach { kv ->
            val kv2 = kv.trim()
            if (kv2.startsWith("Login_UserNumber=")) {
                val v = kv2.substringAfter('=')
                if (v.isNotBlank()) return v
            }
        }
        return null
    }
}
