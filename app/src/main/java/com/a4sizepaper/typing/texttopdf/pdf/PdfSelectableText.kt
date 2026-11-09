package com.a4sizepaper.typing.texttopdf.pdf

import android.content.Context
import android.util.LruCache
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class PdfGlyph(val start: Int, val end: Int, val left: Float, val top: Float, val right: Float, val bottom: Float)
internal data class PdfSelectableText(val text: String, val glyphs: List<PdfGlyph>)

internal fun rotatedGlyph(start: Int, end: Int, x: Float, y: Float, w: Float, h: Float, rotation: Int): PdfGlyph {
    val corners = listOf(x to y, (x+w) to y, x to (y+h), (x+w) to (y+h)).map { (a,b) ->
        when (rotation) { 90 -> (1f-b) to a; 180 -> (1f-a) to (1f-b); 270 -> b to (1f-a); else -> a to b }
    }
    return PdfGlyph(start, end, corners.minOf { it.first }, corners.minOf { it.second }, corners.maxOf { it.first }, corners.maxOf { it.second })
}

/** Lazy text index; native PDF rendering remains responsible for fonts, graphics and images. */
internal class PdfTextSession(private val context: Context, private val file: File) {
    private val lock = Mutex()
    private var document: PDDocument? = null
    private var closed = false
    private val cache = object : LruCache<Int, PdfSelectableText>(500_000) {
        override fun sizeOf(key: Int, value: PdfSelectableText) = value.text.length + value.glyphs.size * 12
    }
    suspend fun page(index: Int): PdfSelectableText = withContext(Dispatchers.IO) {
        lock.withLock {
            ensureActive()
            check(!closed)
            cache.get(index)?.let { return@withLock it }
            PDFBoxResourceLoader.init(context.applicationContext)
            val doc = document ?: PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly(256L*1024*1024).setTempDir(context.cacheDir)).also { document = it }
            require(doc.currentAccessPermission.canExtractContent()) { "This PDF does not allow text selection." }
            val page = doc.getPage(index)
            val rotation = ((page.rotation % 360) + 360) % 360
            val box = page.cropBox
            val text = StringBuilder()
            val glyphs = mutableListOf<PdfGlyph>()
            val cancellation = currentCoroutineContext()
            try {
                page.rotation = 0
                val stripper = object : PDFTextStripper() {
                    var positions = 0
                    override fun processTextPosition(position: TextPosition) {
                        cancellation.ensureActive()
                        require(++positions <= 100_000) { "This page is too complex for text selection." }
                        super.processTextPosition(position)
                    }
                    override fun writeString(value: String, positions: MutableList<TextPosition>) {
                        var offset = 0
                        for (p in positions) {
                            val valueAt = p.unicode ?: continue
                            val found = value.indexOf(valueAt, offset)
                            if (found >= offset) { text.append(value.substring(offset, found)); offset = found + valueAt.length }
                            val start = text.length
                            text.append(valueAt)
                            val height = p.height.coerceAtLeast(1f)
                            glyphs += rotatedGlyph(start, text.length, p.x / box.width, (p.y-height) / box.height,
                                p.width.coerceAtLeast(1f)/box.width, height*1.2f/box.height, rotation)
                        }
                        if (offset < value.length) text.append(value.substring(offset))
                    }
                    override fun writeWordSeparator() { text.append(' ') }
                    override fun writeLineSeparator() { text.append('\n') }
                    override fun writeParagraphEnd() { text.append('\n') }
                }.apply { sortByPosition = true; startPage = index + 1; endPage = index + 1 }
                stripper.getText(doc)
                ensureActive()
                PdfSelectableText(text.toString(), glyphs).also { cache.put(index, it) }
            } finally { page.rotation = rotation }
        }
    }
    suspend fun close() = withContext(Dispatchers.IO) {
        lock.withLock { closed = true; cache.evictAll(); document?.close(); document = null }
    }
}
