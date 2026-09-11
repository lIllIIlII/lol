package com.yunx.app.data.download

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File

object DownloadSaver {

    private const val TAG = "YunX-DL"

    private const val COPY_BUFFER_SIZE = 1 * 1024 * 1024

    fun save(context: Context, fileName: String, source: File, targetDirUri: String? = null): String? {
        val safePath = DownloadPathPolicy.sanitize(
            fileName,
            fallbackName = "download_${System.currentTimeMillis()}"
        ) ?: run {
            Log.e(TAG, "拒绝不安全的下载相对路径")
            return null
        }
        val safeName = safePath.fileName
        val safeDir = safePath.relativeDirectory
        if (!targetDirUri.isNullOrBlank()) {
            return saveViaSaf(context, safeName, safeDir, source, targetDirUri)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, safeName, safeDir, source)?.let { return it }
            Log.e(TAG, "MediaStore 保存失败，回退传统路径：$safeDir/$safeName")
        }
        saveLegacy(context, safeName, safeDir, source)?.let { return it }
        Log.e(TAG, "传统路径保存失败（Android 9- 需存储权限；Android 10+ 分区存储不可写），放弃保存")
        return null
    }

    private fun saveViaSaf(
        context: Context,
        fileName: String,
        subDir: String,
        source: File,
        treeUriString: String
    ): String? {
        val resolver = context.contentResolver
        val treeUri = android.net.Uri.parse(treeUriString)
        return runCatching {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            var dirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
            if (subDir.isNotBlank()) {
                for (part in subDir.split('/').filter { it.isNotBlank() }) {
                    dirUri = getOrCreateSafDir(resolver, treeUri, dirUri, part) ?: return@runCatching null
                }
            }
            val candidates = buildList {
                add(fileName)
                repeat(3) { i -> add(timestampedName(fileName, i)) }
            }
            for (candidate in candidates) {
                try {
                    val docUri = DocumentsContract.createDocument(
                        resolver, dirUri, mimeOf(candidate), candidate
                    ) ?: continue
                    val wrote = resolver.openOutputStream(docUri)?.use { out ->
                        source.inputStream().use { it.copyTo(out, COPY_BUFFER_SIZE) }
                        true
                    } ?: run {
                        resolver.delete(docUri, null, null)
                        false
                    }
                    if (wrote) return docUri.toString()
                } catch (e: Exception) {
                    Log.e(TAG, "SAF 保存异常（$candidate）: ${e.message}")
                }
            }
            null
        }.getOrNull()
    }

    private fun getOrCreateSafDir(
        resolver: ContentResolver,
        treeUri: android.net.Uri,
        parentDocUri: android.net.Uri,
        name: String
    ): android.net.Uri? {
        val parentDocId = DocumentsContract.getDocumentId(parentDocUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val display = cursor.getString(1)
                val mime = cursor.getString(2)
                if (display == name && mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                }
            }
        }
        return DocumentsContract.createDocument(
            resolver, parentDocUri, DocumentsContract.Document.MIME_TYPE_DIR, name
        )
    }

    fun safDirDisplay(uriString: String): String {
        return runCatching {
            val treeId = DocumentsContract.getTreeDocumentId(android.net.Uri.parse(uriString))
            treeId.substringAfterLast(':').replace("%2F", "/").replace("%2f", "/")
                .ifBlank { "自定义目录" }
        }.getOrDefault("自定义目录")
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveViaMediaStore(context: Context, fileName: String, subDir: String, source: File): String? {
        val resolver = context.contentResolver
        val relativePath = if (subDir.isBlank()) {
            Environment.DIRECTORY_DOWNLOADS
        } else {
            "${Environment.DIRECTORY_DOWNLOADS}/$subDir"
        }
        val candidates = buildList {
            add(fileName)
            repeat(3) { i -> add(timestampedName(fileName, i)) }
        }
        for (candidate in candidates) {
            try {
                if (mediaStoreNameExists(resolver, candidate, relativePath)) continue
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, candidate)
                    put(MediaStore.Downloads.MIME_TYPE, mimeOf(candidate))
                    put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: continue
                val wrote = resolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { it.copyTo(out, COPY_BUFFER_SIZE) }
                    true
                } ?: run {
                    resolver.delete(uri, null, null)
                    false
                }
                if (!wrote) continue
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return uri.toString()
            } catch (e: Exception) {
                Log.e(TAG, "MediaStore 保存异常（$candidate）: ${e.message}")
            }
        }
        return null
    }

    private fun timestampedName(fileName: String, attempt: Int): String {
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        val ts = System.currentTimeMillis()
        return if (attempt == 0) "${base}_$ts$ext" else "${base}_${ts}_${attempt + 1}$ext"
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mediaStoreNameExists(
        resolver: ContentResolver,
        fileName: String,
        relativePath: String
    ): Boolean = runCatching {
            val selection = "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?"
            val projection = arrayOf(MediaStore.Downloads._ID)
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                arrayOf(fileName, relativePath),
                null
            )?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        }.onFailure { Log.e(TAG, "查询 MediaStore 同名记录失败: ${it.message}") }
            .getOrDefault(true)

    private fun saveLegacy(context: Context, fileName: String, subDir: String, source: File): String? = runCatching {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).canonicalFile
        val destDir = (if (subDir.isBlank()) dir else File(dir, subDir)).canonicalFile
        if (destDir != dir && !DownloadPathPolicy.isContained(dir, destDir)) {
            throw SecurityException("下载目录越界")
        }
        if (!destDir.exists()) destDir.mkdirs()
        val candidates = buildList {
            add(fileName)
            repeat(3) { i -> add(timestampedName(fileName, i)) }
        }
        val dest = candidates.asSequence()
            .map { File(destDir, it).canonicalFile }
            .firstOrNull { candidate ->
                DownloadPathPolicy.isContained(dir, candidate) && !candidate.exists()
            } ?: return@runCatching null
        source.copyTo(dest, overwrite = false)
        dest.absolutePath
    }.getOrNull()

    fun delete(context: Context, savePath: String): Boolean {
        if (savePath.isBlank()) return false
        return runCatching {
            if (savePath.startsWith("content://")) {
                val uri = android.net.Uri.parse(savePath)
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    deleteSafDocument(context, uri)
                } else {
                    context.contentResolver.delete(uri, null, null) > 0
                }
            } else {
                File(savePath).delete()
            }
        }.onFailure {
            Log.e(TAG, "删除本地文件失败: ${it.message}")
        }.getOrDefault(false)
    }

    private fun deleteSafDocument(context: Context, docUri: android.net.Uri): Boolean {
        if (runCatching { context.contentResolver.delete(docUri, null, null) > 0 }.getOrDefault(false)) {
            return true
        }
        if (runCatching { DocumentsContract.deleteDocument(context.contentResolver, docUri) }.getOrDefault(false)) {
            return true
        }
        return runCatching {
            val segments = docUri.pathSegments
            val treeIdx = segments.indexOf("tree")
            if (treeIdx < 0 || segments.size < treeIdx + 2) return@runCatching false
            val treeUri = docUri.buildUpon().path("/" + segments.subList(0, treeIdx + 2).joinToString("/")).build()
            val treeDocId = android.net.Uri.decode(segments[treeIdx + 1])
            val fileDocId = DocumentsContract.getDocumentId(docUri)
            if (!fileDocId.startsWith(treeDocId)) return@runCatching false
            val relParts = fileDocId.removePrefix(treeDocId).trimStart('/').split('/').filter { it.isNotBlank() }
            if (relParts.isEmpty()) return@runCatching false
            val resolver = context.contentResolver
            var parentDocId = treeDocId
            for ((i, part) in relParts.withIndex()) {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
                var foundId: String? = null
                resolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    ),
                    null, null, null
                )?.use { c ->
                    while (c.moveToNext()) {
                        if (c.getString(1) == part) {
                            foundId = c.getString(0)
                            break
                        }
                    }
                } ?: return@runCatching false
                val id = foundId ?: return@runCatching false
                if (i == relParts.lastIndex) {
                    val target = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    return@runCatching runCatching { resolver.delete(target, null, null) > 0 }
                        .getOrElse { DocumentsContract.deleteDocument(resolver, target) }
                }
                parentDocId = id
            }
            false
        }.getOrDefault(false)
    }

    private fun mimeOf(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf" -> "application/pdf"
            "zip", "rar", "7z" -> "application/zip"
            "mp4", "mkv", "mov", "avi", "webm" -> "video/mp4"
            "mp3", "wav", "flac", "aac" -> "audio/mpeg"
            "jpg", "jpeg", "png", "gif", "webp" -> "image/jpeg"
            "txt", "md", "log" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
