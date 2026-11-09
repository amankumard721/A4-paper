package com.a4sizepaper.typing.texttopdf.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal data class PdfRasterSize(val width: Int, val height: Int, val scale: Float)

/** Keep native bitmap allocations bounded independently of PDF paper dimensions. */
internal fun pdfRasterSize(width: Int, height: Int, requestedWidth: Int): PdfRasterSize {
    require(width > 0 && height > 0) { "Invalid PDF page size." }
    val scale = minOf(requestedWidth.coerceIn(1, 1600).toDouble() / width, 2400.0 / height)
    return PdfRasterSize(
        (width * scale).roundToInt().coerceIn(1, 1600),
        (height * scale).roundToInt().coerceIn(1, 2400),
        scale.toFloat(),
    )
}

internal data class RenderedPdfPage(val bitmap: Bitmap, val aspectRatio: Float)

/** One native document per reader; all page access/closure runs serially off the UI thread. */
internal class PdfRenderSession(file: File) {
    private val lock = Mutex()
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = try { PdfRenderer(descriptor) } catch (error: Throwable) {
        descriptor.close()
        throw error
    }
    private var closed = false
    private val cache = object : LruCache<String, RenderedPdfPage>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: RenderedPdfPage) = value.bitmap.allocationByteCount
    }

    suspend fun render(index: Int, width: Int): RenderedPdfPage = withContext(Dispatchers.IO) {
        lock.withLock {
            currentCoroutineContext().ensureActive()
            check(!closed) { "PDF reader was closed." }
            val key = "$index:$width"
            cache.get(key)?.let { return@withLock it }
            var unpublished: Bitmap? = null
            try {
                renderer.openPage(index).use { page ->
                    val size = pdfRasterSize(page.width, page.height, width)
                    val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
                    unpublished = bitmap
                    bitmap.eraseColor(Color.WHITE)
                    val matrix = Matrix().apply { setScale(size.scale, size.scale) }
                    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    currentCoroutineContext().ensureActive()
                    val result = RenderedPdfPage(bitmap, page.width.toFloat() / page.height)
                    cache.put(key, result)
                    // Cached/published images must not be recycled while Compose may draw them.
                    unpublished = null
                    result
                }
            } finally {
                unpublished?.recycle()
            }
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!closed) {
                closed = true
                cache.evictAll()
                try { renderer.close() } finally { descriptor.close() }
            }
        }
    }
}
