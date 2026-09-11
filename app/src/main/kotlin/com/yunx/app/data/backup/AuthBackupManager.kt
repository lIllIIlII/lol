package com.yunx.app.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.yunx.app.data.db.BaiduAccountDao
import com.yunx.app.data.db.BaiduAccountEntity
import com.yunx.app.data.db.C139AccountDao
import com.yunx.app.data.db.C139AccountEntity
import com.yunx.app.data.db.Pan123AccountDao
import com.yunx.app.data.db.Pan123AccountEntity
import com.yunx.app.data.db.QuarkAccountDao
import com.yunx.app.data.db.QuarkAccountEntity
import com.yunx.app.data.db.UCAccountDao
import com.yunx.app.data.db.UCAccountEntity
import com.yunx.app.data.db.XunleiAccountDao
import com.yunx.app.data.db.XunleiAccountEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AuthBackupManager(
    private val quarkDao: QuarkAccountDao,
    private val ucDao: UCAccountDao,
    private val xunleiDao: XunleiAccountDao,
    private val baiduDao: BaiduAccountDao,
    private val c139Dao: C139AccountDao,
    private val pan123Dao: Pan123AccountDao
) {

    private companion object {
        const val APP_TAG = "yunx_auth_backup"
        const val VERSION = 1
    }

    suspend fun export(password: String? = null, onlyLoggedIn: Boolean = true): String =
        withContext(Dispatchers.IO) {
            require(!password.isNullOrBlank() && password.length >= 8) { "备份口令至少 8 位" }
            val json = exportJson(onlyLoggedIn)
            AuthCrypto.encrypt(json, password)
        }

    suspend fun exportJson(onlyLoggedIn: Boolean = true): String = withContext(Dispatchers.IO) {
        val accounts = JSONArray()
        quarkDao.getAccount()?.let { a ->
            if (!onlyLoggedIn || a.cookie.isNotBlank()) accounts.put(
                JSONObject()
                    .put("platform", "quark")
                    .put("cookie", a.cookie)
                    .put("nickname", a.nickname)
                    .put("updatedAt", a.updatedAt)
            )
        }
        ucDao.getAccount()?.let { a ->
            if (!onlyLoggedIn || a.cookie.isNotBlank()) accounts.put(
                JSONObject()
                    .put("platform", "uc")
                    .put("cookie", a.cookie)
                    .put("nickname", a.nickname)
                    .put("updatedAt", a.updatedAt)
            )
        }
        xunleiDao.getAccount()?.let { a ->
            if (!onlyLoggedIn || a.accessToken.isNotBlank()) accounts.put(
                JSONObject()
                    .put("platform", "xunlei")
                    .put("accessToken", a.accessToken)
                    .put("refreshToken", a.refreshToken)
                    .put("deviceId", a.deviceId)
                    .put("captchaToken", a.captchaToken)
                    .put("nickname", a.nickname)
                    .put("updatedAt", a.updatedAt)
            )
        }
        baiduDao.getAccount()?.let { a ->
            if (!onlyLoggedIn || a.cookie.isNotBlank()) accounts.put(
                JSONObject()
                    .put("platform", "baidu")
                    .put("cookie", a.cookie)
                    .put("nickname", a.nickname)
                    .put("updatedAt", a.updatedAt)
            )
        }
        c139Dao.getAccount()?.let { a ->
            if (!onlyLoggedIn || a.cookie.isNotBlank()) accounts.put(
                JSONObject()
                    .put("platform", "c139")
                    .put("cookie", a.cookie)
                    .put("authorization", a.authorization)
                    .put("nickname", a.nickname)
                    .put("updatedAt", a.updatedAt)
            )
        }
        pan123Dao.getAccount()?.let { a ->
            if (!onlyLoggedIn || a.accessToken.isNotBlank()) accounts.put(
                JSONObject()
                    .put("platform", "pan123")
                    .put("accessToken", a.accessToken)
                    .put("account", a.account)
                    .put("nickname", a.nickname)
                    .put("updatedAt", a.updatedAt)
            )
        }
        JSONObject()
            .put("app", APP_TAG)
            .put("version", VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("accounts", accounts)
            .toString(2)
    }

    suspend fun import(content: String, password: String? = null): Int = withContext(Dispatchers.IO) {
        val json = if (password.isNullOrBlank()) content else AuthCrypto.decrypt(content, password)
        importJson(json)
    }

    suspend fun importJson(json: String): Int = withContext(Dispatchers.IO) {
        val root = JSONObject(json)
        if (root.optString("app") != APP_TAG) {
            throw IllegalArgumentException("不是有效的云析认证备份文件")
        }
        val accounts = root.optJSONArray("accounts") ?: return@withContext 0
        var count = 0
        for (i in 0 until accounts.length()) {
            val obj = accounts.optJSONObject(i) ?: continue
            when (obj.optString("platform")) {
                "quark" -> {
                    val c = obj.optString("cookie")
                    if (c.isNotBlank()) {
                        quarkDao.upsert(
                            QuarkAccountEntity(
                                id = "quark", cookie = c,
                                nickname = obj.optString("nickname"),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                            )
                        ); count++
                    }
                }
                "uc" -> {
                    val c = obj.optString("cookie")
                    if (c.isNotBlank()) {
                        ucDao.upsert(
                            UCAccountEntity(
                                id = "uc", cookie = c,
                                nickname = obj.optString("nickname"),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                            )
                        ); count++
                    }
                }
                "xunlei" -> {
                    val t = obj.optString("accessToken")
                    if (t.isNotBlank()) {
                        xunleiDao.upsert(
                            XunleiAccountEntity(
                                id = "xunlei", accessToken = t,
                                refreshToken = obj.optString("refreshToken"),
                                deviceId = obj.optString("deviceId"),
                                captchaToken = obj.optString("captchaToken"),
                                nickname = obj.optString("nickname"),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                            )
                        ); count++
                    }
                }
                "baidu" -> {
                    val c = obj.optString("cookie")
                    if (c.isNotBlank()) {
                        baiduDao.upsert(
                            BaiduAccountEntity(
                                id = "baidu", cookie = c,
                                nickname = obj.optString("nickname"),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                            )
                        ); count++
                    }
                }
                "c139" -> {
                    val c = obj.optString("cookie")
                    if (c.isNotBlank()) {
                        c139Dao.upsert(
                            C139AccountEntity(
                                id = "c139", cookie = c,
                                authorization = obj.optString("authorization"),
                                nickname = obj.optString("nickname"),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                            )
                        ); count++
                    }
                }
                "pan123" -> {
                    val t = obj.optString("accessToken")
                    if (t.isNotBlank()) {
                        pan123Dao.upsert(
                            Pan123AccountEntity(
                                id = "pan123", accessToken = t,
                                account = obj.optString("account"),
                                nickname = obj.optString("nickname"),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                            )
                        ); count++
                    }
                }
            }
        }
        count
    }

    suspend fun saveToDownloads(context: Context, content: String, encrypted: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val fileName = if (encrypted) {
                    "yunx_auth_backup_${timestamp()}.yunx"
                } else {
                    "yunx_auth_backup_${timestamp()}.json"
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(
                            MediaStore.Downloads.MIME_TYPE,
                            if (encrypted) "application/octet-stream" else "application/json"
                        )
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri: Uri = context.contentResolver
                        .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: return@runCatching false
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    } ?: return@runCatching false
                    true
                } else {
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!dir.exists()) dir.mkdirs()
                    val file = File(dir, fileName)
                    FileOutputStream(file).use { it.write(content.toByteArray(Charsets.UTF_8)) }
                    true
                }
            }.getOrDefault(false)
        }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}
