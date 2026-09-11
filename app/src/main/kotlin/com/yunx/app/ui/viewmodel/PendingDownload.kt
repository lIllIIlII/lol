package com.yunx.app.ui.viewmodel

internal data class PendingDownload(
    val url: String,
    val fileName: String,
    val size: Long,
    val headers: Map<String, String>
)
