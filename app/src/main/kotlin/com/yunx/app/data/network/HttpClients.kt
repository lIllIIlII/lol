package com.yunx.app.data.network

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

object HttpClients {

    private val lock = Any()

    @Volatile
    private var apiCache: OkHttpClient? = null

    @Volatile
    private var downloadCache: OkHttpClient? = null

    fun apiClient(): OkHttpClient {
        apiCache?.let { return it }
        synchronized(lock) {
            apiCache?.let { return it }
            return buildApi().also { apiCache = it }
        }
    }

    fun downloadClient(): OkHttpClient {
        downloadCache?.let { return it }
        synchronized(lock) {
            downloadCache?.let { return it }
            return buildDownload().also { downloadCache = it }
        }
    }

    private fun buildApi(): OkHttpClient {
        return OkHttpClient.Builder()
            .dns(SmartDns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun buildDownload(): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequests = 512
            maxRequestsPerHost = 512
        }
        return OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(
                ConnectionPool(
                    maxIdleConnections = 64,
                    keepAliveDuration = 5,
                    timeUnit = TimeUnit.MINUTES
                )
            )
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .dns(SmartDns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
