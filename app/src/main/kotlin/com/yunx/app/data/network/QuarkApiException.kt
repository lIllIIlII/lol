package com.yunx.app.data.network

class QuarkApiException(message: String, val code: Int = 0) : Exception(message)
