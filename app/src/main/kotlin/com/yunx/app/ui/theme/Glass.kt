/*
 * 吸析At - 液态玻璃系统（Liquid Glass）。
 *
 * 设计目标：真实玻璃观感 + 零逐帧开销 + 圆角完美裁剪。
 * - 壁纸解码一次并预模糊（降采样 + 三趟盒式模糊 + 双线性放大，近似高斯），
 *   玻璃面板绘制时直接从预模糊位图按屏幕坐标取样（一次 GPU 纹理搬运，无 RenderEffect）；
 * - 所有玻璃绘制都在 clip(shape) 内完成 → 模糊像素严格裁剪在圆角内，
 *   彻底修复「边角突出/不是圆角」的问题；
 * - 面板叠加：模糊取样 + 玻璃色罩 + 135° 渐变描边（边缘高光）+ 顶部镜面高光。
 */

package com.yunx.app.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 壁纸与玻璃的全局状态（进程级缓存，只计算一次）。
 *  采用 Compose 状态：模糊层就绪时，正在展示的玻璃面板（弹窗/胶囊栏）会自动重绘取样，
 *  后台线程写入全局快照同样触发重绘（Recomposer 观察全局写入）。 */
object GlassWallpaper {
    var sharp: ImageBitmap? by mutableStateOf(null)
    var blurred: ImageBitmap? by mutableStateOf(null)
    @Volatile var screenW: Int = 0
    @Volatile var screenH: Int = 0

    private val decodeLock = Any()
    private val blurLock = Any()

    /** 解码 + 中心裁剪到屏幕尺寸（Dispatchers.IO 一次性执行）。
     *  清晰层立即可用（背景先上屏），模糊层另行补算（玻璃面板取样用）。 */
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
                // 限制解码尺寸（内存友好）：长边 ≤ 1600
                val maxSide = 1600
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeResource(context.resources, R.drawable.bg_wallpaper, opts)
                var sample = 1
                while (maxOf(opts.outWidth, opts.outHeight) / (sample + 1) >= maxSide) sample++
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                val raw = BitmapFactory.decodeResource(context.resources, R.drawable.bg_wallpaper, decodeOpts)
                    ?: return
                sharp = centerCropScale(raw, screenW, screenH).asImageBitmap()
            }
        }
    }

    /** 预模糊层（玻璃取样用）：依赖清晰层，后台补算，不影响背景上屏速度 */
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

    /** 中心裁剪到目标比例并缩放到目标尺寸（与背景 1:1 对应，玻璃取样才能对齐） */
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

    /** 快速近似高斯模糊：降采样 1/10 → 三趟盒式模糊 → 双线性放大 */
    private fun Bitmap.fastBlur(scale: Int = 10, passes: Int = 3): Bitmap {
        val w = (width / scale).coerceAtLeast(2)
        val h = (height / scale).coerceAtLeast(2)
        var small = Bitmap.createScaledBitmap(this, w, h, true)
        repeat(passes) { small = boxBlur(small) }
        return Bitmap.createScaledBitmap(small, width, height, true)
    }

    /** 单趟可分离盒式模糊（水平 + 垂直），滑窗半径 3（小图上等效大半径） */
    private fun boxBlur(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        val out = IntArray(w * h)
        val r = 3
        val win = 2 * r + 1
        // 水平
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
        // 垂直
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

/** 全屏壁纸背景（清晰层 + 上下渐变暗化罩，内容更聚焦）
 *  分两步加载：清晰层就绪立即上屏（消除旧版「启动后背景长时间纯黑」的问题），
 *  模糊层随后补算供玻璃面板取样。 */
@Composable
fun WallpaperBackground(
    darkScrimTop: Float = 0.20f,
    darkScrimBottom: Float = 0.45f
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        // 清晰层：解码完成即上屏（状态写入触发重组）
        withContext(Dispatchers.IO) { GlassWallpaper.ensureSharp(context) }
        // 模糊层：后台补算（玻璃面板取样用；就绪后各面板自动重绘）
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
            // 解码中：深色底避免闪白
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind { drawRect(Color(0xFF10131A)) }
            )
        }
    }
}

/**
 * 液态玻璃修饰符（静态面板专用，勿用于列表 item）：
 * 1. clip(shape) 先行 → 后续全部绘制（模糊取样/色罩/描边/高光）都被圆角裁剪，
 *    修复旧版「模糊从方角溢出」的问题；
 * 2. 从预模糊壁纸按「面板在屏幕上的真实位置」取样（onGloballyPositioned 记录，
 *    预模糊图与屏幕 1:1，故直接对应像素）；
 * 3. 玻璃色罩（上浅下深）+ 135° 渐变描边 + 顶部镜面高光。
 *
 * @param alignToScreen true=按屏幕坐标对齐取样（主窗口内面板）；
 *        false=取模糊图中心区域（Dialog 独立窗口坐标不同，模糊下视觉差异不可辨）
 */
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
                    // Dialog：取中心区域
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
            // 玻璃色罩（上浅下深，模拟厚度）
            drawRect(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = tintAlpha * 0.65f),
                        Color.White.copy(alpha = tintAlpha * 1.25f),
                        Color.Black.copy(alpha = tintAlpha * 0.35f)
                    )
                )
            )
            // 135° 渐变描边（边缘高光，在 clip 内绘制 → 圆角无突出）
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
            // 顶部内侧镜面高光
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
