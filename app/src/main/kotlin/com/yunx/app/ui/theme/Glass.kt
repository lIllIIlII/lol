package com.yunx.app.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.yunx.app.R
import com.yunx.app.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object GlassWallpaper {
    var sharp: ImageBitmap? by mutableStateOf(null)
    var blurred: ImageBitmap? by mutableStateOf(null)
    @Volatile var screenW: Int = 0
    @Volatile var screenH: Int = 0

    var version by mutableIntStateOf(0)

    private val decodeLock = Any()
    private val blurLock = Any()

    private const val CUSTOM_FILE = "custom_wallpaper.img"

    private fun customFile(context: Context): File? {
        val name = runCatching { SettingsRepository(context).customWallpaper }.getOrNull()
        if (name.isNullOrBlank()) return null
        val f = File(context.filesDir, name)
        return if (f.isFile && f.length() > 0) f else null
    }

    fun hasCustom(context: Context): Boolean = customFile(context) != null

    fun applyCustom(context: Context, uri: Uri): Boolean {
        return runCatching {
            val target = File(context.filesDir, CUSTOM_FILE)
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return false
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(target.absolutePath, opts)
            check(opts.outWidth > 0 && opts.outHeight > 0) { "不是有效图片" }
            SettingsRepository(context).customWallpaper = CUSTOM_FILE
            reload(context)
            true
        }.getOrElse {
            runCatching { File(context.filesDir, CUSTOM_FILE).delete() }
            false
        }
    }

    fun clearCustom(context: Context) {
        runCatching {
            File(context.filesDir, CUSTOM_FILE).delete()
            SettingsRepository(context).customWallpaper = null
        }
        reload(context)
    }

    fun reload(context: Context) {
        synchronized(decodeLock) {
            synchronized(blurLock) {
                sharp = null
                blurred = null
            }
        }
        version += 1
    }

    fun ensureSharp(context: Context) {
        if (sharp != null) return
        synchronized(decodeLock) {
            if (sharp != null) return
            runCatching {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val bounds: Rect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    wm.maximumWindowMetrics.bounds
                } else {
                    @Suppress("DEPRECATION")
                    Rect(0, 0, wm.defaultDisplay.width, wm.defaultDisplay.height)
                }
                screenW = bounds.width().coerceAtLeast(720)
                screenH = bounds.height().coerceAtLeast(1280)
                val maxSide = 1600
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                val custom = customFile(context)
                if (custom != null) {
                    BitmapFactory.decodeFile(custom.absolutePath, opts)
                } else {
                    BitmapFactory.decodeResource(context.resources, R.drawable.bg_wallpaper, opts)
                }
                var sample = 1
                while (maxOf(opts.outWidth, opts.outHeight) / (sample + 1) >= maxSide) sample++
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                val raw = if (custom != null) {
                    BitmapFactory.decodeFile(custom.absolutePath, decodeOpts)
                } else {
                    BitmapFactory.decodeResource(context.resources, R.drawable.bg_wallpaper, decodeOpts)
                } ?: return
                sharp = centerCropScale(raw, screenW, screenH).asImageBitmap()
            }
        }
    }

    fun ensureBlurred(context: Context) {
        if (blurred != null) return
        synchronized(blurLock) {
            if (blurred != null) return
            runCatching {
                ensureSharp(context)
                val sharpBmp = sharp ?: return
                blurred = sharpBmp.asAndroidBitmap().fastBlur().asImageBitmap()
            }
        }
    }

    private fun centerCropScale(src: Bitmap, w: Int, h: Int): Bitmap {
        val srcRatio = src.width.toFloat() / src.height
        val dstRatio = w.toFloat() / h
        val cropW: Int
        val cropH: Int
        if (srcRatio > dstRatio) {
            cropH = src.height
            cropW = (src.height * dstRatio).toInt().coerceAtLeast(1)
        } else {
            cropW = src.width
            cropH = (src.width / dstRatio).toInt().coerceAtLeast(1)
        }
        val x = (src.width - cropW) / 2
        val y = (src.height - cropH) / 2
        val cropped = Bitmap.createBitmap(src, x, y, cropW, cropH)
        return Bitmap.createScaledBitmap(cropped, w, h, true)
    }

    private fun Bitmap.fastBlur(scale: Int = 10, passes: Int = 3): Bitmap {
        val w = (width / scale).coerceAtLeast(2)
        val h = (height / scale).coerceAtLeast(2)
        var small = Bitmap.createScaledBitmap(this, w, h, true)
        repeat(passes) { small = boxBlur(small) }
        return Bitmap.createScaledBitmap(small, width, height, true)
    }

    private fun boxBlur(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        val out = IntArray(w * h)
        val r = 3
        val win = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var a = 0; var rr = 0; var g = 0; var b = 0
            for (x in -r..r) {
                val px = pixels[row + x.coerceIn(0, w - 1)]
                a += (px ushr 24) and 0xFF; rr += (px ushr 16) and 0xFF
                g += (px ushr 8) and 0xFF; b += px and 0xFF
            }
            for (x in 0 until w) {
                tmp[row + x] = pack(a / win, rr / win, g / win, b / win)
                val add = pixels[row + (x + r + 1).coerceIn(0, w - 1)]
                val sub = pixels[row + (x - r).coerceIn(0, w - 1)]
                a += ((add ushr 24) and 0xFF) - ((sub ushr 24) and 0xFF)
                rr += ((add ushr 16) and 0xFF) - ((sub ushr 16) and 0xFF)
                g += ((add ushr 8) and 0xFF) - ((sub ushr 8) and 0xFF)
                b += (add and 0xFF) - (sub and 0xFF)
            }
        }
        for (x in 0 until w) {
            var a = 0; var rr = 0; var g = 0; var b = 0
            for (y in -r..r) {
                val px = tmp[y.coerceIn(0, h - 1) * w + x]
                a += (px ushr 24) and 0xFF; rr += (px ushr 16) and 0xFF
                g += (px ushr 8) and 0xFF; b += px and 0xFF
            }
            for (y in 0 until h) {
                out[y * w + x] = pack(a / win, rr / win, g / win, b / win)
                val add = tmp[(y + r + 1).coerceIn(0, h - 1) * w + x]
                val sub = tmp[(y - r).coerceIn(0, h - 1) * w + x]
                a += ((add ushr 24) and 0xFF) - ((sub ushr 24) and 0xFF)
                rr += ((add ushr 16) and 0xFF) - ((sub ushr 16) and 0xFF)
                g += ((add ushr 8) and 0xFF) - ((sub ushr 8) and 0xFF)
                b += (add and 0xFF) - (sub and 0xFF)
            }
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    private fun pack(a: Int, r: Int, g: Int, b: Int): Int =
        (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
}

@Composable
fun WallpaperBackground(
    darkScrimTop: Float = 0.20f,
    darkScrimBottom: Float = 0.45f
) {
    val context = LocalContext.current
    LaunchedEffect(GlassWallpaper.version) {
        withContext(Dispatchers.IO) { GlassWallpaper.ensureSharp(context) }
        withContext(Dispatchers.IO) { GlassWallpaper.ensureBlurred(context) }
    }
    Box(Modifier.fillMaxSize()) {
        val bmp = GlassWallpaper.sharp
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = darkScrimTop),
                                    Color.Black.copy(alpha = (darkScrimTop + darkScrimBottom) / 2f),
                                    Color.Black.copy(alpha = darkScrimBottom)
                                )
                            )
                        )
                    }
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind { drawRect(Color(0xFF10131A)) }
            )
        }
    }
}

fun Modifier.liquidGlass(
    shape: Shape,
    darkTheme: Boolean = true,
    tintAlpha: Float = if (darkTheme) 0.16f else 0.40f,
    borderAlpha: Float = if (darkTheme) 0.60f else 0.90f,
    alignToScreen: Boolean = true
): Modifier = composed {
    var panelPos by remember { mutableStateOf(IntOffset.Zero) }
    this
        .onGloballyPositioned { coords ->
            if (alignToScreen) {
                val p = coords.positionInRoot()
                panelPos = IntOffset(p.x.toInt(), p.y.toInt())
            }
        }
        .clip(shape)
        .drawBehind {
            val blur = GlassWallpaper.blurred
            if (blur != null) {
                val srcX: Int
                val srcY: Int
                val srcW: Int
                val srcH: Int
                if (alignToScreen) {
                    srcW = size.width.toInt().coerceAtLeast(1).coerceAtMost(blur.width)
                    srcH = size.height.toInt().coerceAtLeast(1).coerceAtMost(blur.height)
                    srcX = panelPos.x.coerceIn(0, (blur.width - srcW).coerceAtLeast(0))
                    srcY = panelPos.y.coerceIn(0, (blur.height - srcH).coerceAtLeast(0))
                } else {
                    srcW = size.width.toInt().coerceAtLeast(1).coerceAtMost(blur.width)
                    srcH = size.height.toInt().coerceAtLeast(1).coerceAtMost(blur.height)
                    srcX = (blur.width - srcW) / 2
                    srcY = (blur.height - srcH) / 2
                }
                drawImage(
                    image = blur,
                    srcOffset = IntOffset(srcX, srcY),
                    srcSize = IntSize(srcW, srcH),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
                )
            }
            drawRect(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = tintAlpha * 0.65f),
                        Color.White.copy(alpha = tintAlpha * 1.25f),
                        Color.Black.copy(alpha = tintAlpha * 0.35f)
                    )
                )
            )
            val borderBrush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = borderAlpha),
                    Color.White.copy(alpha = borderAlpha * 0.15f),
                    Color.White.copy(alpha = borderAlpha * 0.5f),
                    Color.White.copy(alpha = borderAlpha * 0.10f)
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            )
            when (val outline = shape.createOutline(size, layoutDirection, this)) {
                is androidx.compose.ui.graphics.Outline.Rounded -> {
                    val rr = outline.roundRect
                    drawRoundRect(
                        brush = borderBrush,
                        topLeft = Offset(rr.left, rr.top),
                        size = Size(rr.width, rr.height),
                        cornerRadius = rr.topLeftCornerRadius,
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                }
                is androidx.compose.ui.graphics.Outline.Rectangle -> {
                    drawRect(
                        brush = borderBrush,
                        topLeft = outline.rect.topLeft,
                        size = outline.rect.size,
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                }
                else -> {}
            }
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.White.copy(alpha = 0f),
                        Color.White.copy(alpha = if (darkTheme) 0.28f else 0.55f),
                        Color.White.copy(alpha = 0f)
                    )
                ),
                topLeft = Offset(size.width * 0.08f, 0.7.dp.toPx()),
                size = Size(size.width * 0.84f, 1.1.dp.toPx())
            )
        }
}
