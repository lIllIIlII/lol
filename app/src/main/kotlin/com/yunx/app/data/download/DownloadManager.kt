package com.yunx.app.data.download

import android.content.Context
import android.util.Log
import com.yunx.app.util.LogRedactor
import com.yunx.app.data.db.DownloadTaskDao
import com.yunx.app.data.db.DownloadTaskEntity
import com.yunx.app.data.security.AndroidKeystoreCredentialCipher
import com.yunx.app.data.security.CredentialCipher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.min

data class DownloadStats(
    val speed: Long = 0L,
    val remainMillis: Long = -1L,
    val chunkCount: Int = 1
)

private const val TAG = "YunX-DL"

private const val RANGE_WORKERS_CAP = 8

private const val STAGGER_CAP = 8
private const val STAGGER_MS = 25L

private const val RANGE_IGNORED_TOLERANCE = 3

private data class RetryRange(val start: Long, val end: Long, val file: File)

private class ElasticAllocator(
    private val total: Long,
    private val elasticStart: Long,
    private val workers: Int,
    private val chunkSize: Long
) {
    private val lock = Any()
    private var nextStart = elasticStart

    @Volatile
    var recentSpeedBps: Long = 0L

    fun take(): LongRange? = synchronized(lock) {
        if (nextStart >= total) return null
        val remaining = total - nextStart
        val base = baseBlockSize()
        val w = workers.coerceAtLeast(1)
        val block = if (remaining <= w * base) {
            (remaining / w).coerceIn(MIN_TAIL_BLOCK, base)
        } else {
            base
        }
        val s = nextStart
        val e = minOf(s + block - 1, total - 1)
        nextStart = e + 1
        s..e
    }

    private fun baseBlockSize(): Long {
        val w = workers.coerceAtLeast(1)
        val perWorker = (recentSpeedBps / w).coerceAtLeast(0L)
        if (perWorker <= 0L) return chunkSize.coerceIn(MIN_ELASTIC_BLOCK, MAX_ELASTIC_BLOCK)
        return (perWorker * TARGET_SECONDS).coerceIn(MIN_ELASTIC_BLOCK, MAX_ELASTIC_BLOCK)
    }

    fun skipTo(start: Long) = synchronized(lock) {
        if (start > nextStart) nextStart = start
    }

    companion object {
        const val MIN_ELASTIC_BLOCK = 256 * 1024L
        const val MAX_ELASTIC_BLOCK = 4 * 1024 * 1024L
        const val MIN_TAIL_BLOCK = 64 * 1024L
        const val TARGET_SECONDS = 5L
    }
}

class DownloadManager(
    private val context: Context,
    private val dao: DownloadTaskDao,
    private val downloader: ChunkDownloader,
    private val threadProvider: (String) -> Int = { 32 },
    private val saveDirProvider: () -> String? = { null },
    private val concurrencyProvider: () -> Int = { 3 },
    private val speedLimitProvider: () -> Long = { 0L },
    private val retryCountProvider: () -> Int = { 3 },
    private val keepWhenLockedProvider: () -> Boolean = { true },
    private val showSpeedProvider: () -> Boolean = { true }
) {
    private val credentialCipher: CredentialCipher = AndroidKeystoreCredentialCipher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val activeDownloads = java.util.concurrent.atomic.AtomicInteger(0)

    private val speedLimiter = SpeedLimiter()

    var storagePermissionProvider: suspend () -> Boolean = { true }

    private val activeJobs = ConcurrentHashMap<Long, CompletableDeferred<Job>>()
    private val jobsLock = Any()

    private val activeTaskCount = java.util.concurrent.atomic.AtomicInteger(0)

    private val notifyThrottleMs = 2000L
    private val lastNotifyTs = AtomicLong(0)

    private fun notifyProgress(id: Long, fileName: String, new: Long, total: Long) {
        val now = System.currentTimeMillis()
        if (now - lastNotifyTs.get() >= notifyThrottleMs) {
            lastNotifyTs.set(now)
            val percent = if (total > 0) ((new * 100 / total).toInt().coerceIn(0, 100)) else -1
            val speed = _stats.value[id]?.speed ?: 0L
            val speedText = if (speed > 0) formatSpeed(speed) else ""
            DownloadService.update(context, fileName, percent, speedText, showSpeedProvider())
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return ""
        val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
        var value = bytesPerSec.toDouble()
        var i = 0
        while (value >= 1024 && i < units.size - 1) {
            value /= 1024
            i++
        }
        return String.format("%.1f %s", value, units[i])
    }

    private suspend fun persistProgressIfDue(
        id: Long,
        new: Long,
        total: Long,
        force: Boolean,
        lastAt: AtomicLong
    ) {
        val now = System.currentTimeMillis()
        val last = lastAt.get()
        if (force || (total > 0 && new >= total) || now - last >= progressPersistIntervalMs) {
            if (lastAt.compareAndSet(last, now)) {
                dao.updateProgress(id, DownloadTaskEntity.STATUS_DOWNLOADING, new, total)
            }
        }
    }

    private suspend fun completeWithAvg(id: Long, savedPath: String, total: Long) {
        val start = taskStartTimes.remove(id) ?: 0L
        val elapsedSec = ((System.currentTimeMillis() - start) / 1000.0).coerceAtLeast(1.0)
        val avg = if (total > 0 && elapsedSec > 0) (total / elapsedSec).toLong() else 0L
        dao.complete(id, DownloadTaskEntity.STATUS_COMPLETED, savedPath, avg)
    }

    private val taskLocks = ConcurrentHashMap<Long, Mutex>()

    private val taskHeaders = ConcurrentHashMap<Long, Map<String, String>>()

    private val taskSizes = ConcurrentHashMap<Long, Long>()

    private val taskStartTimes = ConcurrentHashMap<Long, Long>()

    private val taskCallbacks = ConcurrentHashMap<Long, suspend () -> Unit>()

    private val _stats = MutableStateFlow<Map<Long, DownloadStats>>(emptyMap())
    val stats: StateFlow<Map<Long, DownloadStats>> = _stats.asStateFlow()

    private val progressPersistIntervalMs = 500L

    val tasks: Flow<List<DownloadTaskEntity>> = dao.observeAll()

    suspend fun enqueue(
        url: String,
        fileName: String,
        headers: Map<String, String> = emptyMap(),
        size: Long = -1L,
        platform: String = "",
        onComplete: suspend () -> Unit = {}
    ): Long {
        val safeName = fileName.ifBlank {
            url.substringAfterLast('/').substringBefore('?')
                .ifBlank { "download_${System.currentTimeMillis()}" }
        }
        Log.d(TAG, "enqueue: origin=${LogRedactor.url(url)} fileName=$safeName headers=${headers.keys} size=$size")
        val id = dao.insert(
            DownloadTaskEntity(
                url = url,
                fileName = safeName,
                requestHeadersJson = encodeHeaders(headers),
                platform = platform
            )
        )
        if (headers.isNotEmpty()) taskHeaders[id] = headers
        if (size > 0) taskSizes[id] = size
        taskCallbacks[id] = onComplete
        start(id, headers)
        return id
    }

    suspend fun redownload(id: Long): Boolean {
        val task = dao.get(id) ?: return false
        val headers = loadPersistedHeaders(id)
        val valid = runCatching { downloader.getTotalSize(task.url, headers) != null }.getOrDefault(false)
        if (!valid) return false
        enqueue(task.url, task.fileName, headers, task.totalSize, task.platform)
        return true
    }

    fun start(id: Long, headers: Map<String, String> = emptyMap()) {
        val effectiveHeaders = headers.ifEmpty { taskHeaders[id] ?: emptyMap() }
        Log.d(TAG, "start: id=$id headers=${effectiveHeaders.keys}")
        synchronized(jobsLock) {
            val existing = activeJobs[id]
            if (existing != null) {
                if (existing.isCompleted && existing.getCompleted().isActive) return
                activeJobs.remove(id)
            }
            val deferred = CompletableDeferred<Job>()
            activeJobs[id] = deferred
            val job = scope.launch {
                try {
                    onTaskStarted(id)
                    taskLocks.getOrPut(id) { Mutex() }.withLock {
                        val restoredHeaders = if (effectiveHeaders.isNotEmpty()) {
                            effectiveHeaders
                        } else {
                            loadPersistedHeaders(id)
                        }
                        if (restoredHeaders.isNotEmpty()) taskHeaders[id] = restoredHeaders
                        runTaskWithRetry(id, restoredHeaders)
                    }
                } catch (e: CancellationException) {
                    _stats.update { it - id }
                } catch (e: Exception) {
                    _stats.update { it - id }
                    if (isTaskActive()) {
                        Log.e(TAG, "task $id failed: ${e.message ?: e.javaClass.simpleName}", e)
                        dao.updateStatus(id, DownloadTaskEntity.STATUS_FAILED)
                        dao.updateError(id, e.message ?: e.javaClass.simpleName)
                    } else {
                        Log.w(TAG, "task $id cancelled: ${e.message}")
                    }
                } finally {
                    onTaskFinished()
                    synchronized(jobsLock) {
                        if (activeJobs[id] === deferred) activeJobs.remove(id)
                    }
                }
            }
            deferred.complete(job)
        }
    }

    private suspend fun onTaskStarted(id: Long) {
        if (activeTaskCount.getAndIncrement() == 0) {
            val name = runCatching { dao.get(id)?.fileName }.getOrNull() ?: "下载任务"
            DownloadService.start(context, name)
        }
        acquireWakeLockIfNeeded()
    }

    private fun onTaskFinished() {
        if (activeTaskCount.decrementAndGet() <= 0) {
            activeTaskCount.set(0)
            DownloadService.stop(context)
            releaseWakeLock()
        }
    }

    @Volatile
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    private fun acquireWakeLockIfNeeded() {
        if (!keepWhenLockedProvider()) return
        val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager ?: return
        if (wakeLock == null) {
            wakeLock = pm.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK, "yunx:download"
            ).apply { setReferenceCounted(false) }
        }
        wakeLock?.let { if (!it.isHeld) it.acquire() }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    fun pause(id: Long) {
        Log.d(TAG, "pause: id=$id")
        downloader.cancelCalls(id)
        val deferred = synchronized(jobsLock) { activeJobs.remove(id) }
        _stats.update { it - id }
        scope.launch {
            deferred?.let { runCatching { it.await().cancelAndJoin() } }
            val real = chunkDirOf(id).listFiles()
                ?.filter {
                    it.name.startsWith("part_") ||
                        (it.name.startsWith("seg_") && it.name.endsWith(".part"))
                }
                ?.sumOf { it.length() } ?: 0L
            val t = dao.get(id)
            if (t != null && real > t.downloadedSize) {
                dao.updateProgress(id, DownloadTaskEntity.STATUS_PAUSED, real, t.totalSize)
            } else {
                dao.updateStatus(id, DownloadTaskEntity.STATUS_PAUSED)
            }
        }
    }

    fun remove(id: Long, deleteLocal: Boolean = false) {
        Log.d(TAG, "remove: id=$id deleteLocal=$deleteLocal")
        downloader.cancelCalls(id)
        _stats.update { it - id }
        taskHeaders.remove(id)
        val cleanup = taskCallbacks.remove(id)
        taskLocks.remove(id)
        val deferred = synchronized(jobsLock) { activeJobs.remove(id) }
        scope.launch {
            if (deferred != null) {
                deferred.await().cancelAndJoin()
            }
            if (deleteLocal) {
                dao.get(id)?.savePath?.let {
                    val deleted = DownloadSaver.delete(context, it)
                    Log.d(TAG, "remove: id=$id 删除本地文件 ${if (deleted) "成功" else "失败/未找到"} ($it)")
                }
            }
            dao.delete(id)
            chunkDirOf(id).deleteRecursively()
            cleanup?.let { runCatching { it() } }
        }
    }

    private fun encodeHeaders(headers: Map<String, String>): String {
        val json = JSONObject().apply { headers.forEach { (name, value) -> put(name, value) } }.toString()
        return credentialCipher.encrypt(json, "download.requestHeaders")
    }

    private suspend fun loadPersistedHeaders(id: Long): Map<String, String> {
        val stored = dao.get(id)?.requestHeadersJson.orEmpty()
        if (stored.isBlank()) return emptyMap()
        return runCatching {
            val jsonText = credentialCipher.decrypt(stored, "download.requestHeaders")
            val json = JSONObject(jsonText)
            buildMap {
                json.keys().forEach { name -> put(name, json.getString(name)) }
            }.also {
                if (!credentialCipher.isEncrypted(stored)) {
                    dao.updateRequestHeaders(id, encodeHeaders(it))
                }
            }
        }.getOrElse {
            dao.updateRequestHeaders(id, encodeHeaders(emptyMap()))
            emptyMap()
        }
    }

    private suspend fun isTaskActive(): Boolean = coroutineContext[Job]?.isActive == true

    private suspend fun awaitConcurrencySlot() {
        val max = concurrencyProvider().coerceAtLeast(1)
        while (isTaskActive() && activeDownloads.get() >= max) {
            delay(300)
        }
    }

    private suspend fun runTaskWithRetry(id: Long, headers: Map<String, String>) {
        var attempts = 0
        val maxRetries = retryCountProvider().coerceIn(0, 10)
        while (true) {
            awaitConcurrencySlot()
            if (!isTaskActive()) return
            activeDownloads.incrementAndGet()
            try {
                try {
                    runTask(id, headers)
                    return
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    attempts++
                    if (isTaskActive() && attempts <= maxRetries) {
                        Log.d(TAG, "runTaskWithRetry: id=$id 失败，自动重试 $attempts/$maxRetries：${e.message}")
                        delay(1200L * attempts)
                    } else {
                        throw e
                    }
                }
            } finally {
                activeDownloads.decrementAndGet()
            }
        }
    }

    private suspend fun runTask(id: Long, headers: Map<String, String>) {
        if (!isTaskActive()) return
        val task = dao.get(id) ?: return
        dao.updateStatus(id, DownloadTaskEntity.STATUS_DOWNLOADING)
        taskStartTimes[id] = System.currentTimeMillis()
        Log.d(TAG, "runTask: id=$id fileName=${task.fileName}")

        if (task.url.contains(".m3u8", true) || task.url.contains(".m3u", true)) {
            Log.d(TAG, "runTask: id=$id HLS 转码流下载 origin=${LogRedactor.url(task.url)}")
            hlsDownload(id, task, headers)
            return
        }

        val total = downloader.getTotalSize(task.url, headers)
            ?: taskSizes[id]?.takeIf { it > 0 }
        if (total == null) {
            Log.w(TAG, "runTask: id=$id 无法获取总大小，降级流式下载 origin=${LogRedactor.url(task.url)}")
            streamDownload(id, task, headers)
            return
        }
        Log.d(TAG, "getTotalSize: id=$id total=$total origin=${LogRedactor.url(task.url)}")
        dao.updateProgress(id, DownloadTaskEntity.STATUS_DOWNLOADING, task.downloadedSize, total)
        if (!isTaskActive()) return

        val threadCount = threadProvider(task.platform).coerceAtLeast(1)
        val chunkCount = chunkCountFor(total, threadCount)
        val chunkSize = ceil(total.toDouble() / chunkCount).toLong()
        val chunkDir = chunkDirOf(id).apply { mkdirs() }
        val mainPoolCount = (chunkCount * 0.7).toInt().coerceIn(1, chunkCount)
        val elasticStart = mainPoolCount * chunkSize
        val planFile = File(chunkDir, "plan.txt")
        val plan = "chunks=$chunkCount total=$total main=$mainPoolCount"
        if (planFile.exists() && planFile.readText() != plan) {
            Log.w(TAG, "runTask: id=$id 分片计划变化（$plan），清空旧 part 重下")
            chunkDir.deleteRecursively()
            chunkDir.mkdirs()
        } else {
        }
        planFile.writeText(plan)
        val isXunlei = headers["User-Agent"]?.contains("xunlei", ignoreCase = true) == true ||
            task.url.contains("xunlei", ignoreCase = true)
        val effectiveWorkers = if (isXunlei) {
            min(threadCount, RANGE_WORKERS_CAP).coerceAtLeast(1)
        } else {
            threadCount.coerceAtLeast(1)
        }
        Log.d(TAG, "分片规划: id=$id chunks=$chunkCount main=$mainPoolCount elasticStart=$elasticStart size=$chunkSize threads=$threadCount effectiveWorkers=$effectiveWorkers isXunlei=$isXunlei")

        _stats.update { it + (id to DownloadStats(0L, -1L, effectiveWorkers)) }

        val downloaded = AtomicLong(0)
        (0 until mainPoolCount).forEach { i ->
            downloaded.addAndGet(File(chunkDir, "part_$i").length())
        }
        chunkDir.listFiles { f -> f.name.startsWith("seg_") && f.name.endsWith(".part") }
            ?.forEach { downloaded.addAndGet(it.length()) }
        val init = minOf(downloaded.get(), total)
        downloaded.set(init)
        if (init > task.downloadedSize) {
            dao.updateProgress(id, DownloadTaskEntity.STATUS_DOWNLOADING, init, total)
        }
        val lastPersistAt = AtomicLong(0L)
        val speedRecorder = SpeedRecorder()

        val results = arrayOfNulls<ChunkResult?>(mainPoolCount)
        val nextIdx = AtomicInteger(0)
        val fallback = AtomicBoolean(false)
        val failReason = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val rangeIgnoredCount = AtomicInteger(0)

        val elasticAllocator = ElasticAllocator(total, elasticStart, effectiveWorkers, chunkSize)
        if (elasticStart < total) {
            chunkDir.listFiles { f -> f.name.startsWith("seg_") && f.name.endsWith(".part") }?.forEach { f ->
                val name = f.name.removePrefix("seg_").removeSuffix(".part")
                val s = name.substringBefore('_').toLongOrNull() ?: return@forEach
                val e = name.substringAfter('_').toLongOrNull() ?: return@forEach
                if (f.length() < (e - s + 1)) f.delete()
            }
            val doneSegs = chunkDir.listFiles { f -> f.name.startsWith("seg_") && f.name.endsWith(".part") }
                ?.mapNotNull { f ->
                    val name = f.name.removePrefix("seg_").removeSuffix(".part")
                    val s = name.substringBefore('_').toLongOrNull() ?: return@mapNotNull null
                    val e = name.substringAfter('_').toLongOrNull() ?: return@mapNotNull null
                    if (f.length() >= (e - s + 1)) s to e else null
                }?.sortedBy { it.first } ?: emptyList()
            var resumeNext = elasticStart
            for ((s, e) in doneSegs) {
                if (s == resumeNext) resumeNext = e + 1 else break
            }
            elasticAllocator.skipTo(resumeNext)
        }
        val elasticResults = ConcurrentHashMap<String, ChunkResult>()

        val sem = Semaphore(effectiveWorkers)

        val allOk = coroutineScope {
            val workers = List(effectiveWorkers) {
                async(Dispatchers.IO) {
                    while (true) {
                        if (fallback.get()) break
                        val i = nextIdx.getAndIncrement()
                        if (i >= mainPoolCount) break
                        if (i > 0) delay(min(i.toLong(), STAGGER_CAP.toLong()) * STAGGER_MS)
                        sem.withPermit {
                            if (fallback.get()) return@withPermit
                            val start = i * chunkSize
                            val end = min(start + chunkSize - 1, total - 1)
                            val res = try {
                                downloader.downloadChunk(
                                    taskId = id, url = task.url, start = start, end = end,
                                    partFile = File(chunkDir, "part_$i"), headers = headers
                                ) { bytes ->
                                    speedLimiter.awaitAllow(bytes)
                                    val new = minOf(downloaded.addAndGet(bytes), total)
                                    if (!isTaskActive()) return@downloadChunk
                                    speedRecorder.onBytes(new)?.let { speed ->
                                        elasticAllocator.recentSpeedBps = speed
                                        val remain = if (speed > 0) (total - new) * 1000 / speed else -1L
                                        _stats.update { it + (id to DownloadStats(speed, remain, effectiveWorkers)) }
                                    }
                                    notifyProgress(id, task.fileName, new, total)
                                    persistProgressIfDue(id, new, total, force = false, lastAt = lastPersistAt)
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                failReason.compareAndSet(null, "分片 ${i + 1}/$mainPoolCount：${e.message ?: e.javaClass.simpleName}")
                                ChunkResult.FAILED
                            }
                            results[i] = res
                            when (res) {
                                ChunkResult.RANGE_IGNORED -> {
                                    val n = rangeIgnoredCount.incrementAndGet()
                                    Log.w(TAG, "runTask: id=$id 分片${i + 1} 检测到服务器忽略Range（累计 $n/$RANGE_IGNORED_TOLERANCE）")
                                    if (n >= RANGE_IGNORED_TOLERANCE) fallback.compareAndSet(false, true)
                                }
                                ChunkResult.FAILED -> failReason.compareAndSet(null, "分片 ${i + 1}/$mainPoolCount 下载失败")
                                else -> {}
                            }
                        }
                    }
                    while (!fallback.get()) {
                        val range = elasticAllocator.take() ?: break
                        val s = range.first
                        val e = range.last
                        val key = "${s}_${e}"
                        val res = try {
                            sem.withPermit {
                                if (fallback.get()) return@withPermit ChunkResult.FAILED
                                downloader.downloadChunk(
                                    taskId = id, url = task.url, start = s, end = e,
                                    partFile = File(chunkDir, "seg_$key.part"), headers = headers
                                ) { bytes ->
                                    speedLimiter.awaitAllow(bytes)
                                    val new = minOf(downloaded.addAndGet(bytes), total)
                                    if (!isTaskActive()) return@downloadChunk
                                    speedRecorder.onBytes(new)?.let { speed ->
                                        elasticAllocator.recentSpeedBps = speed
                                        val remain = if (speed > 0) (total - new) * 1000 / speed else -1L
                                        _stats.update { it + (id to DownloadStats(speed, remain, effectiveWorkers)) }
                                    }
                                    notifyProgress(id, task.fileName, new, total)
                                    persistProgressIfDue(id, new, total, force = false, lastAt = lastPersistAt)
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            ChunkResult.FAILED
                        }
                        elasticResults[key] = res
                        when (res) {
                            ChunkResult.RANGE_IGNORED -> {
                                val n = rangeIgnoredCount.incrementAndGet()
                                Log.w(TAG, "runTask: id=$id 弹性区间 $key 检测到服务器忽略Range（累计 $n/$RANGE_IGNORED_TOLERANCE）")
                                if (n >= RANGE_IGNORED_TOLERANCE) fallback.compareAndSet(false, true)
                            }
                            ChunkResult.FAILED -> failReason.compareAndSet(null, "弹性区间 ${s}-${e} 下载失败")
                            else -> {}
                        }
                    }
                }
            }
            workers.awaitAll()
            !fallback.get() && results.all { it == ChunkResult.OK } &&
                elasticResults.values.all { it == ChunkResult.OK }
        }

        if (fallback.get()) {
            Log.w(TAG, "runTask: id=$id 回退单流整文件下载（避免重复下载整文件）")
            singleStreamFallback(id, task, headers, total, chunkDir, failReason)
            return
        }
        if (!allOk) {
            val missing = buildList {
                for (i in 0 until mainPoolCount) {
                    val f = File(chunkDir, "part_$i")
                    val s = i * chunkSize
                    val e = min(s + chunkSize - 1, total - 1)
                    if (f.length() < (e - s + 1)) add(RetryRange(s, e, f))
                }
                elasticResults.forEach { (key, res) ->
                    if (res != ChunkResult.OK) {
                        val s = key.substringBefore('_').toLong()
                        val e = key.substringAfter('_').toLong()
                        add(RetryRange(s, e, File(chunkDir, "seg_$key.part")))
                    }
                }
            }
            Log.e(TAG, "runTask: id=$id 缺失区间 ${missing.size} 个 reason=${failReason.get()}，并行重试")
            val retryOk = if (missing.isEmpty()) true else coroutineScope {
                val retryIdx = AtomicInteger(0)
                val retryResults = arrayOfNulls<ChunkResult?>(missing.size)
                val retryWorkers = List(min(effectiveWorkers, missing.size)) {
                    async(Dispatchers.IO) {
                        while (true) {
                            if (!isTaskActive()) break
                            val pos = retryIdx.getAndIncrement()
                            if (pos >= missing.size) break
                            val m = missing[pos]
                            val res = try {
                                downloader.downloadChunk(
                                    taskId = id, url = task.url, start = m.start, end = m.end,
                                    partFile = m.file, headers = headers
                                ) { bytes ->
                                    speedLimiter.awaitAllow(bytes)
                                    val new = minOf(downloaded.addAndGet(bytes), total)
                                    if (!isTaskActive()) return@downloadChunk
                                    dao.updateProgress(id, DownloadTaskEntity.STATUS_DOWNLOADING, new, total)
                                    notifyProgress(id, task.fileName, new, total)
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                ChunkResult.FAILED
                            }
                            retryResults[pos] = res
                            if (res != ChunkResult.OK) {
                                failReason.compareAndSet(null, "区间 ${m.start}-${m.end} 重试仍失败")
                            }
                        }
                    }
                }
                retryWorkers.awaitAll()
                retryResults.all { it == ChunkResult.OK }
            }
            if (retryOk) {
                Log.d(TAG, "runTask: id=$id 重试补齐所有区间，开始合并")
                finishDownload(id, chunkDir, finalChunkFiles(chunkDir, mainPoolCount), task.fileName, total)
                return
            }
            Log.w(TAG, "runTask: id=$id 分片重试失败，回退单流整文件下载")
            singleStreamFallback(id, task, headers, total, chunkDir, failReason)
            return
        }
        Log.d(TAG, "runTask: id=$id 所有区间完成，开始合并")
        finishDownload(id, chunkDir, finalChunkFiles(chunkDir, mainPoolCount), task.fileName, total)
    }

    private fun finalChunkFiles(chunkDir: File, mainPoolCount: Int): List<File> {
        val mainFiles = (0 until mainPoolCount).map { File(chunkDir, "part_$it") }
        val elasticFiles = chunkDir.listFiles { f ->
            f.name.startsWith("seg_") && f.name.endsWith(".part")
        }?.sortedBy { it.name.removePrefix("seg_").substringBefore('_').toLong() }
            ?: emptyList()
        return mainFiles + elasticFiles
    }

    private suspend fun singleStreamFallback(
        id: Long,
        task: DownloadTaskEntity,
        headers: Map<String, String>,
        total: Long,
        chunkDir: File,
        failReason: java.util.concurrent.atomic.AtomicReference<String?>
    ) {
        val fullFile = File(chunkDir, "full_single.bin").apply { delete() }
        val fullDownloaded = AtomicLong(0)
        val fullLastAt = AtomicLong(0L)
        val ok = downloader.downloadFull(id, task.url, fullFile, headers, total) { bytes ->
            speedLimiter.awaitAllow(bytes)
            val new = minOf(fullDownloaded.addAndGet(bytes), total)
            if (!isTaskActive()) return@downloadFull
            persistProgressIfDue(id, new, total, force = false, lastAt = fullLastAt)
            notifyProgress(id, task.fileName, new, total)
        }
        if (!ok) throw IllegalStateException(failReason.get() ?: "分片与单流下载均失败")
        finishDownload(id, chunkDir, listOf(fullFile), task.fileName, total)
    }

    private suspend fun streamDownload(id: Long, task: DownloadTaskEntity, headers: Map<String, String>) {
        if (!isTaskActive()) return
        dao.updateProgress(id, DownloadTaskEntity.STATUS_DOWNLOADING, task.downloadedSize, 0)
        if (!isTaskActive()) return
        _stats.update { it + (id to DownloadStats(0L, -1L, 1)) }
        val chunkDir = chunkDirOf(id).apply { mkdirs() }
        val partFile = File(chunkDir, "part_0")
        val downloaded = AtomicLong(partFile.length())
        val streamLastAt = AtomicLong(0L)
        val ok = downloader.downloadChunk(
            taskId = id,
            url = task.url,
            start = 0,
            end = Long.MAX_VALUE,
            partFile = partFile,
            headers = headers
        ) { bytes ->
            speedLimiter.awaitAllow(bytes)
            val new = downloaded.addAndGet(bytes)
            if (!isTaskActive()) return@downloadChunk
            persistProgressIfDue(id, new, 0, force = false, lastAt = streamLastAt)
            notifyProgress(id, task.fileName, new, 0)
        }
        if (ok != ChunkResult.OK) {
            Log.w(TAG, "streamDownload: id=$id Range 失败，回退完整 GET 下载")
            downloaded.set(0)
            dao.updateProgress(id, DownloadTaskEntity.STATUS_DOWNLOADING, 0, 0)
            val ok2 = downloader.downloadFull(
                taskId = id,
                url = task.url,
                partFile = partFile,
                headers = headers
            ) { bytes ->
                speedLimiter.awaitAllow(bytes)
                val new = downloaded.addAndGet(bytes)
                if (!isTaskActive()) return@downloadFull
                persistProgressIfDue(id, new, 0, force = false, lastAt = streamLastAt)
            }
            if (!ok2) throw IllegalStateException("下载失败（Range 与完整下载均失败）")
        }
        if (!isTaskActive()) return
        finishDownload(id, chunkDir, listOf(partFile), task.fileName, 0)
    }

    private suspend fun hlsDownload(id: Long, task: DownloadTaskEntity, headers: Map<String, String>) {
        if (!isTaskActive()) return
        _stats.update { it + (id to DownloadStats(0L, -1L, 1)) }
        val hlsFile = File(context.cacheDir, "hls_$id")
        hlsFile.delete()
        val downloaded = AtomicLong(0)
        val hlsLastAt = AtomicLong(0L)
        val ok = HlsDownloader.download(task.url, headers, hlsFile) { bytes ->
            speedLimiter.awaitAllow(bytes)
            val new = downloaded.addAndGet(bytes)
            persistProgressIfDue(id, new, 0, force = false, lastAt = hlsLastAt)
            notifyProgress(id, task.fileName, new, 0)
        }
        if (!isTaskActive()) return
        if (!ok) {
            hlsFile.delete()
            throw IllegalStateException("HLS 转码流下载失败")
        }
        if (!storagePermissionProvider()) {
            hlsFile.delete()
            throw IllegalStateException("未授予存储权限，无法保存到下载目录")
        }
        val savedPath = withContext(Dispatchers.IO) {
            DownloadSaver.save(context, task.fileName, hlsFile, saveDirProvider())
        }
            ?: throw IllegalStateException("保存到下载目录失败")
        val hlsTotal = dao.get(id)?.totalSize ?: 0L
        completeWithAvg(id, savedPath, hlsTotal)
        Log.d(TAG, "hlsDownload: id=$id 下载完成 savedPath=$savedPath size=${hlsFile.length()}")
        taskCallbacks.remove(id)?.let { cb -> runCatching { cb() } }
        _stats.update { it - id }
        hlsFile.delete()
    }

    private suspend fun finishDownload(
        id: Long,
        chunkDir: File,
        chunkFiles: List<File>,
        fileName: String,
        total: Long
    ) {
        if (!isTaskActive()) return
        for (part in chunkFiles) {
            if (!part.exists() || part.length() <= 0) {
                Log.e(TAG, "finishDownload: id=$id 分片缺失/为空 $part")
                throw IllegalStateException("分片文件缺失或为空，拒绝合并（防止文件损坏）")
            }
        }
        val merged = File(context.cacheDir, "merged_$id")
        if (!downloader.mergeChunks(chunkFiles, merged)) {
            Log.e(TAG, "finishDownload: id=$id 合并分片失败")
            throw IllegalStateException("合并分片失败")
        }
        if (total > 0 && merged.length() != total) {
            Log.e(TAG, "finishDownload: id=$id 文件大小校验失败 期望=$total 实际=${merged.length()}")
            merged.delete()
            throw IllegalStateException("文件大小校验失败：期望 $total 字节，实际 ${merged.length()} 字节（已拒绝保存损坏文件）")
        }
        if (!storagePermissionProvider()) {
            merged.delete()
            throw IllegalStateException("未授予存储权限，无法保存到下载目录")
        }
        val savedPath = withContext(Dispatchers.IO) {
            DownloadSaver.save(context, fileName, merged, saveDirProvider())
        }
            ?: throw IllegalStateException("保存到下载目录失败")
        completeWithAvg(id, savedPath, total)
        Log.d(TAG, "finishDownload: id=$id 下载完成 savedPath=$savedPath size=${merged.length()}")
        taskCallbacks.remove(id)?.let { cb ->
            runCatching { cb() }
        }
        _stats.update { it - id }
        merged.delete()
        chunkDir.deleteRecursively()
    }

    private class SpeedRecorder {
        private data class Sample(val timeMs: Long, val bytes: Long)

        private val samples = ArrayDeque<Sample>()
        private var lastEmit = 0L

        @Synchronized
        fun onBytes(total: Long): Long? {
            val now = System.currentTimeMillis()
            samples.addLast(Sample(now, total))
            while (samples.size > 2 && now - samples.first().timeMs > WINDOW_MS) {
                samples.removeFirst()
            }
            if (now - lastEmit < 250) return null
            val first = samples.first()
            val elapsed = now - first.timeMs
            val speed = if (elapsed > 0) {
                ((total - first.bytes) * 1000 / elapsed).coerceAtLeast(0)
            } else 0L
            lastEmit = now
            return speed
        }

        private companion object {
            const val WINDOW_MS = 5000L
        }
    }

    private inner class SpeedLimiter {
        @Volatile
        private var tokens = 0L
        @Volatile
        private var lastRefillNanos = System.nanoTime()

        @Synchronized
        private fun refill(limit: Long) {
            val now = System.nanoTime()
            val elapsedSec = ((now - lastRefillNanos).coerceAtLeast(0) / 1_000_000_000.0)
            lastRefillNanos = now
            tokens = minOf(limit, tokens + (elapsedSec * limit).toLong())
        }

        suspend fun awaitAllow(bytes: Long) {
            val limit = speedLimitProvider().coerceAtLeast(0L)
            if (limit <= 0L) return
            while (true) {
                val waitMs = synchronized(this) {
                    refill(limit)
                    if (bytes <= tokens) {
                        tokens -= bytes
                        return
                    }
                    ((bytes - tokens) * 1000 / limit).coerceIn(1L, 200L)
                }
                delay(waitMs)
            }
        }
    }

    private fun cacheBase(): File = context.externalCacheDir ?: context.cacheDir

    private fun chunkDirOf(id: Long): File = File(cacheBase(), "download_tmp/$id")

    private fun chunkCountFor(total: Long, threads: Int): Int {
        if (total <= 0) return 1
        val minChunkBytes = 256 * 1024L
        val bySize = when {
            total < 5 * 1024 * 1024 -> 1
            total < 50 * 1024 * 1024 -> 8
            total < 500 * 1024 * 1024 -> 32
            else -> 64
        }
        val want = maxOf(bySize, threads * 8)
        return minOf(want, (total / minChunkBytes).toInt().coerceAtLeast(1), 512)
    }
}
