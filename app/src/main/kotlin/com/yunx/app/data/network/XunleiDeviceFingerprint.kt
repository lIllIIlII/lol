package com.yunx.app.data.network

import android.content.Context
import java.security.MessageDigest
import kotlin.random.Random

object XunleiDeviceFingerprint {

    private const val PREFS = "xunlei_device_fp"
    private const val KEY_ID = "device_id"
    private const val KEY_PEER = "peer_id"
    private const val KEY_SIGN = "device_sign"

    private const val PACKAGE_NAME = "com.xunlei.downloadprovider"
    private const val APPID = "40"
    private const val APP_KEY = "34a062aaa22f906fca4fefe9fb3a3021"
    private const val HEX = "0123456789abcdef"

    @Volatile
    private var initialized = false

    @Volatile
    private var deviceId: String = XunleiConstants.DEVICE_ID
    @Volatile
    private var peerId: String = XunleiConstants.PEER_ID
    @Volatile
    private var deviceSign: String = XunleiConstants.DEVICE_SIGN

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val savedId = prefs.getString(KEY_ID, null)
            if (savedId != null) {
                deviceId = savedId
                peerId = prefs.getString(KEY_PEER, XunleiConstants.PEER_ID)!!
                deviceSign = prefs.getString(KEY_SIGN, XunleiConstants.DEVICE_SIGN)!!
            } else {
                val newId = randomHex(32)
                val newPeer = randomHex(32)
                val newSign = buildDeviceSign(newId)
                prefs.edit()
                    .putString(KEY_ID, newId)
                    .putString(KEY_PEER, newPeer)
                    .putString(KEY_SIGN, newSign)
                    .apply()
                deviceId = newId
                peerId = newPeer
                deviceSign = newSign
            }
            initialized = true
        }
    }

    fun deviceId(): String = deviceId

    fun peerId(): String = peerId

    fun deviceSign(): String = deviceSign

    private fun buildDeviceSign(id: String): String {
        val base = id + PACKAGE_NAME + APPID + APP_KEY
        val sha1 = sha1Hex(base)
        val md5 = md5Hex(sha1)
        return "div101.$id$md5"
    }

    private fun randomHex(len: Int): String = buildString {
        repeat(len) { append(HEX[Random.nextInt(16)]) }
    }

    private fun sha1Hex(input: String): String =
        MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
