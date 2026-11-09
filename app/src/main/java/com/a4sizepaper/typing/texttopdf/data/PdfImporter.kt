package com.a4sizepaper.typing.texttopdf.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.a4sizepaper.typing.texttopdf.domain.Document
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.Writer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class PdfImporter(
    private val context: Context,
    private val store: ImportedPdfStore = ImportedPdfStore(context),
) {
    data class Imported(val document: Document, val warnings: List<String>)

    suspend fun import(uri: Uri): ImportedPdfEntry =
        withContext(Dispatchers.IO) {
            require(uri.scheme == "content") { "Choose the PDF using the document picker." }
            val displayName =
                context.contentResolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    ?: "Imported PDF"
            copyImport(uri, displayName)
        }

    suspend fun importScan(uri: Uri, expectedPages: Int, title: String): ImportedPdfEntry =
        withContext(Dispatchers.IO) {
            require(expectedPages in 1..ScanSource.MAX_PAGES) { "Scan up to 50 pages at a time." }
            val cacheFile = ScanSource.privateCacheFile(context.cacheDir, uri)
            val saved = copyImport(uri, title, expectedPages)
            // Commit the durable original before removing the SDK-owned temporary output.
            cacheFile?.delete()
            saved
        }

    private suspend fun copyImport(
        uri: Uri,
        displayName: String,
        scanPages: Int? = null,
    ): ImportedPdfEntry {
        val staging = store.newStaging()
        try {
            (if (scanPages != null) ScanSource.open(context, uri)
                else context.contentResolver.openInputStream(uri))
                .use { input ->
                    requireNotNull(input) { "The selected PDF is unavailable." }
                    FileOutputStream(staging).use { output ->
                        val buffer = ByteArray(16_384)
                        var total = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total % (1024 * 1024) < buffer.size)
                                ImportedPdfStore.requireCopySpace(staging)
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
            currentCoroutineContext().ensureActive()
            val pages = validateForViewing(staging)
            if (scanPages != null) ScanSource.validatePages(scanPages, pages)
            currentCoroutineContext().ensureActive()
            return store.publish(staging, displayName, pages)
        } finally {
            staging.delete()
        }
    }

    /** Explicitly creates a reflowed text document; the original PDF remains untouched. */
    suspend fun extractText(entry: ImportedPdfEntry): Imported =
        withContext(Dispatchers.IO) {
            val current = store.get(entry.id)
            require(current.pages <= MAX_TEXT_PAGES) {
                "Text editing supports up to 100 pages. Use Manage pages to save a smaller PDF copy first."
            }
            val cancellation = currentCoroutineContext()
            initialize()
            try {
                load(current.file).use { pdf ->
                    ensureActive()
                    require(!pdf.isEncrypted) { ENCRYPTED_MESSAGE }
                    require(pdf.currentAccessPermission.canExtractContent()) {
                        "This PDF does not allow copying its text."
                    }
                    require(pdf.numberOfPages in 1..MAX_TEXT_PAGES) {
                        "Text editing supports up to 100 PDF pages."
                    }
                    // Page-display rotations can make the stripper break ordinary words into
                    // letters.
                    // This is an in-memory text copy; retained PDF rotation/bytes stay unchanged.
                    for (page in pdf.pages) {
                        page.rotation = 0
                    }
                    val writer = BoundedTextWriter(MAX_TEXT_CHARS) { cancellation.ensureActive() }
                    val stripper =
                        object : PDFTextStripper() {
                                private var positions = 0
                                private var pagePositions = 0

                                override fun processPage(page: PDPage) {
                                    cancellation.ensureActive()
                                    pagePositions = 0
                                    super.processPage(page)
                                    cancellation.ensureActive()
                                }

                                override fun processTextPosition(text: TextPosition) {
                                    cancellation.ensureActive()
                                    positions += text.unicode?.length ?: 1
                                    pagePositions += text.unicode?.length ?: 1
                                    require(
                                        positions <= MAX_TEXT_CHARS &&
                                            pagePositions <= MAX_PAGE_CHARS
                                    ) {
                                        "This PDF contains too much text to edit on this device. Save a smaller page selection first."
                                    }
                                    super.processTextPosition(text)
                                }
                            }
                            .apply {
                                sortByPosition = true
                                lineSeparator = "\n"
                                pageEnd = "\n\u000c\n"
                            }
                    stripper.writeText(pdf, writer)
                    ensureActive()
                    val text = writer.value()
                    require(text.any { !it.isWhitespace() && it != '\u000c' }) {
                        "No selectable text was found. This may be a scanned PDF; OCR is not available. You can still view, save, share and print the original PDF."
                    }
                    val content = textDocument(text)
                    Imported(
                        Document(
                            title = "${current.title} (text copy)".take(200),
                            content = content.toString(),
                        ),
                        listOf(
                            "This is an editable text copy. PDF fonts, images, tables and exact layout are not preserved. The original PDF is kept separately."
                        ),
                    )
                }
            } catch (error: InvalidPasswordException) {
                throw IllegalArgumentException(ENCRYPTED_MESSAGE, error)
            } catch (error: IOException) {
                throw IllegalArgumentException(
                    "Could not read this PDF. It may be damaged or use unsupported text encoding.",
                    error,
                )
            }
        }

    /**
     * Selected pages are copied in the requested order, with rotation added to their existing
     * rotation.
     */
    suspend fun compress(uri: Uri): ImportedPdfEntry = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "Choose the PDF using the document picker." }
        val title = context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
            ?.let { File(it).nameWithoutExtension } ?: "PDF"
        val source = store.newStaging()
        try {
            val cancellation = currentCoroutineContext()
            val input = requireNotNull(context.contentResolver.openInputStream(uri)) {
                "The selected PDF is unavailable."
            }
            input.use { stream ->
                source.outputStream().use { staging ->
                    val buffer = ByteArray(16_384)
                    var bytes = 0L
                    while (true) {
                        cancellation.ensureActive()
                        val count = stream.read(buffer)
                        if (count < 0) break
                        bytes += count
                        require(bytes <= ImportedPdfStore.MAX_BYTES) { "Choose a PDF smaller than 30 MB." }
                        staging.write(buffer, 0, count)
                    }
                }
            }
            val pages = validate(source)
            val output = store.newStaging()
            try {
                initialize()
                load(source).use { pdf ->
                    require(!pdf.isEncrypted) { ENCRYPTED_MESSAGE }
                    require(pdf.currentAccessPermission.canModify()) { "This PDF does not allow changes." }
                    require(pdf.signatureDictionaries.isEmpty()) { "Compressing this PDF would invalidate its signatures." }
                    val visited = java.util.Collections.newSetFromMap(
                        java.util.IdentityHashMap<com.tom_roush.pdfbox.cos.COSDictionary, Boolean>()
                    )
                    for (page in pdf.pages) {
                        cancellation.ensureActive()
                        compressImages(pdf, page.resources, visited, 0) { cancellation.ensureActive() }
                    }
                    FileOutputStream(output).use { stream ->
                        pdf.save(BoundedPdfOutput(stream) { cancellation.ensureActive() })
                        stream.fd.sync()
                    }
                }
                require(output.length() < source.length()) { "This PDF is already compact. No smaller copy was created." }
                require(validate(output) == pages) { "The compressed copy could not be verified." }
                cancellation.ensureActive()
                store.publish(output, "$title (compressed)", pages)
            } finally {
                output.delete()
            }
        } catch (error: InvalidPasswordException) {
            throw IllegalArgumentException(ENCRYPTED_MESSAGE, error)
        } finally {
            source.delete()
        }
    }

    private fun compressImages(
        pdf: PDDocument,
        resources: PDResources?,
        visited: MutableSet<com.tom_roush.pdfbox.cos.COSDictionary>,
        depth: Int,
        check: () -> Unit,
    ) {
        if (depth >= 32 || resources == null || !visited.add(resources.cosObject)) return
        for (name in resources.xObjectNames.toList()) {
            check()
            when (val image = resources.getXObject(name)) {
                is PDFormXObject -> compressImages(pdf, image.resources, visited, depth + 1, check)
                is PDImageXObject -> {
                    val raw = image.cosObject
                    val filters = raw.filters
                    if (filters != COSName.DCT_DECODE ||
                        raw.containsKey(COSName.MASK) || raw.containsKey(COSName.SMASK) ||
                        raw.containsKey(COSName.DECODE) || image.colorSpace !is PDDeviceRGB ||
                        image.isStencil || image.width.toLong() * image.height > 12_000_000L ||
                        maxOf(image.width, image.height) <= 1600
                    ) continue

                    val bitmap = image.image ?: continue
                    val scale = 1600f / maxOf(bitmap.width, bitmap.height)
                    val smaller = Bitmap.createScaledBitmap(
                        bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1),
                        (bitmap.height * scale).toInt().coerceAtLeast(1), true,
                    )
                    try {
                        val jpeg = JPEGFactory.createFromImage(pdf, smaller, 0.75f)
                        if (jpeg.cosObject.length < raw.length) resources.put(name, jpeg)
                    } finally {
                        if (smaller !== bitmap) smaller.recycle()
                    }
                }
            }
        }
    }

    suspend fun merge(uris: List<Uri>): ImportedPdfEntry = withContext(Dispatchers.IO) {
        require(uris.size in 2..12) { "Choose 2–12 PDF files." }
        initialize()
        val cancellation = currentCoroutineContext()
        val target = store.newStaging()
        try {
            val merger = com.tom_roush.pdfbox.multipdf.PDFMergerUtility()
            var totalBytes = 0L
            PDDocument(memorySettings()).use { output ->
                for (uri in uris) {
                    cancellation.ensureActive()
                    require(uri.scheme == "content") { "Choose PDFs using the document picker." }
                    val sourceFile = store.newStaging()
                    try {
                        val input = requireNotNull(context.contentResolver.openInputStream(uri)) {
                            "A selected PDF is unavailable."
                        }
                        input.use { stream ->
                            sourceFile.outputStream().use { staged ->
                                val buffer = ByteArray(16_384)
                                while (true) {
                                    cancellation.ensureActive()
                                    val count = stream.read(buffer)
                                    if (count < 0) break
                                    totalBytes += count
                                    require(totalBytes <= ImportedPdfStore.MAX_BYTES) {
                                        "Choose PDFs with a combined size under 30 MB."
                                    }
                                    staged.write(buffer, 0, count)
                                }
                            }
                        }
                        load(sourceFile).use { source ->
                            require(!source.isEncrypted) { ENCRYPTED_MESSAGE }
                            require(source.currentAccessPermission.canAssembleDocument()) {
                                "A selected PDF does not allow assembling its pages."
                            }
                            require(source.numberOfPages > 0 &&
                                output.numberOfPages + source.numberOfPages <= ImportedPdfStore.MAX_PAGES) {
                                "Choose PDFs with 1–300 pages in total."
                            }
                            merger.appendDocument(output, source)
                        }
                    } finally {
                        sourceFile.delete()
                    }
                }
                FileOutputStream(target).use { stream ->
                    output.save(BoundedPdfOutput(stream) { cancellation.ensureActive() })
                    stream.fd.sync()
                }
            }
            val pages = validate(target)
            cancellation.ensureActive()
            store.publish(target, "Merged PDF", pages)
        } catch (error: InvalidPasswordException) {
            throw IllegalArgumentException(ENCRYPTED_MESSAGE, error)
        } finally {
            target.delete()
        }
    }

    suspend fun createCopy(
        entry: ImportedPdfEntry,
        pageRanges: String,
        rotationDegrees: Int = 0,
    ): ImportedPdfEntry =
        withContext(Dispatchers.IO) {
            require(rotationDegrees in listOf(0, 90, 180, 270)) {
                "Choose a rotation of 0°, 90°, 180° or 270°."
            }
            val current = store.get(entry.id)
            require(current.file.length() <= ImportedPdfStore.MAX_BYTES) {
                "Page editing supports PDFs up to 30 MB. You can still read, save, share and print this PDF."
            }
            val selected = parsePageRanges(pageRanges, current.pages)
            val staging = store.newStaging()
            val cancellation = currentCoroutineContext()
            try {
                writePageCopy(current.file, current.pages, selected, rotationDegrees, staging) {
                    cancellation.ensureActive()
                }
                ensureActive()
                val pages = validate(staging)
                ensureActive()
                store.publish(staging, "${current.title} (pages copy)", pages)
            } catch (error: InvalidPasswordException) {
                throw IllegalArgumentException(ENCRYPTED_MESSAGE, error)
            } catch (error: IOException) {
                throw IllegalArgumentException(
                    "Could not create this PDF copy. The file may be damaged or too complex.",
                    error,
                )
            } finally {
                staging.delete()
            }
        }

    internal fun writePageCopy(
        sourceFile: File,
        expectedPages: Int,
        selected: List<Int>,
        rotationDegrees: Int,
        target: File,
        check: () -> Unit,
    ) {
        initialize()
        load(sourceFile).use { source ->
            check()
            require(!source.isEncrypted) { ENCRYPTED_MESSAGE }
            require(source.currentAccessPermission.canAssembleDocument()) {
                "This PDF does not allow changing its pages."
            }
            require(source.numberOfPages == expectedPages) {
                "This PDF changed. Reopen it and try again."
            }
            PDDocument(memorySettings()).use { output ->
                for (index in selected) {
                    check()
                    val original = source.getPage(index)
                    output.importPage(original).apply {
                        // Page-tree inherited resources/boxes must be explicit in the new tree.
                        resources = original.resources
                        mediaBox = original.mediaBox
                        cropBox = original.cropBox
                        rotation = ((original.rotation + rotationDegrees) % 360 + 360) % 360
                    }
                }
                FileOutputStream(target).use { stream ->
                    output.save(BoundedPdfOutput(stream, check))
                    stream.fd.sync()
                }
            }
        }
    }

    /** Viewing needs only the native renderer, not a second full PDFBox parse. */
    internal fun validateForViewing(file: File): Int {
        require(file.isFile && file.length() >= 5L) { "This PDF is empty or unavailable." }
        val header = file.inputStream().use { input ->
            val buffer = ByteArray(1024)
            val count = input.read(buffer)
            if (count > 0) String(buffer, 0, count, Charsets.ISO_8859_1) else ""
        }
        require(header.contains("%PDF-")) { "The selected file is not a PDF." }
        try {
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    require(renderer.pageCount > 0) { "This PDF has no pages." }
                    renderer.pageCount
                }
            }
        } catch (error: SecurityException) {
            throw IllegalArgumentException(ENCRYPTED_MESSAGE, error)
        } catch (error: IOException) {
            throw IllegalArgumentException("Could not open this PDF. It may be damaged or unsupported.", error)
        }
    }

    private fun validate(file: File): Int {
        require(file.length() in 5L..ImportedPdfStore.MAX_BYTES) {
            "Choose a PDF smaller than 30 MB."
        }
        val header =
            file.inputStream().use { input ->
                val bytes = ByteArray(1024)
                val count = input.read(bytes)
                if (count <= 0) "" else String(bytes, 0, count, Charsets.ISO_8859_1)
            }
        require(header.contains("%PDF-")) { "The selected file is not a PDF." }
        initialize()
        try {
            val expected =
                load(file).use { pdf ->
                    require(!pdf.isEncrypted) { ENCRYPTED_MESSAGE }
                    require(pdf.numberOfPages in 1..ImportedPdfStore.MAX_PAGES) {
                        "Choose a PDF with 1–300 pages."
                    }
                    pdf.numberOfPages
                }
            // Verify compatibility with the same native renderer used by the viewer, off the UI
            // thread.
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    require(renderer.pageCount == expected) {
                        "This PDF has an unsupported page structure."
                    }
                }
            }
            return expected
        } catch (error: InvalidPasswordException) {
            throw IllegalArgumentException(ENCRYPTED_MESSAGE, error)
        } catch (error: SecurityException) {
            throw IllegalArgumentException(ENCRYPTED_MESSAGE, error)
        } catch (error: IOException) {
            throw IllegalArgumentException(
                "Could not open this PDF. It may be damaged or unsupported.",
                error,
            )
        }
    }

    private fun initialize() {
        synchronized(resourceLock) { PDFBoxResourceLoader.init(context.applicationContext) }
    }

    private fun memorySettings(): MemoryUsageSetting =
        MemoryUsageSetting.setupMixed(4L * 1024 * 1024, 128L * 1024 * 1024)
            .setTempDir(context.cacheDir)

    private fun load(file: File): PDDocument = PDDocument.load(file, "", memorySettings())

    companion object {
        private val resourceLock = Any()
        private const val MAX_TEXT_PAGES = 100
        private const val MAX_TEXT_CHARS = 1_000_000
        private const val MAX_PAGE_CHARS = 100_000
        private const val ENCRYPTED_MESSAGE =
            "Password-protected or encrypted PDFs are not supported. Save an unlocked copy in your PDF app, then import it here."

        /** Blank selects all. Ranges and individual pages retain the user's explicit order. */
        fun parsePageRanges(value: String, pageCount: Int): List<Int> {
            require(pageCount in 1..ImportedPdfStore.MAX_PAGES) { "Choose a PDF with 1–300 pages." }
            require(value.length <= 2048) { "The page selection is too long." }
            if (value.isBlank()) return (0 until pageCount).toList()
            val result = linkedSetOf<Int>()
            val pattern = Regex("([0-9]+)(?:\\s*-\\s*([0-9]+))?")
            for (part in value.split(',')) {
                val match =
                    pattern.matchEntire(part.trim())
                        ?: throw IllegalArgumentException(
                            "Enter pages such as 1-3,5. Each page can appear once."
                        )
                val first = match.groupValues[1].toIntOrNull() ?: 0
                val lastText = match.groupValues[2]
                val last = if (lastText.isEmpty()) first else lastText.toIntOrNull() ?: 0
                require(first in 1..pageCount && last in first..pageCount) {
                    "Page numbers must be within 1–$pageCount, with ranges in ascending order."
                }
                for (page in first..last) require(result.add(page - 1)) {
                    "Page $page is selected more than once."
                }
            }
            return result.toList()
        }

        internal fun textDocument(text: String): JSONObject {
            require(text.length <= MAX_TEXT_CHARS) { "This PDF contains too much text to edit." }
            val nodes = JSONArray()
            var lines = 0
            val pages = text.split('\u000c')
            for ((pageIndex, page) in pages.withIndex()) {
                if (pageIndex == pages.lastIndex && page.isBlank()) continue
                if (pageIndex > 0) nodes.put(JSONObject().put("type", "pageBreak"))
                for (line in page.trim('\r', '\n').lineSequence()) {
                    require(++lines <= 20_000) {
                        "This PDF has too many text lines to edit. Save a smaller page selection first."
                    }
                    nodes.put(
                        JSONObject().put("type", "paragraph").apply {
                            val cleaned = line.filter { it == '\t' || !it.isISOControl() }
                            require(cleaned.length <= MAX_PAGE_CHARS) {
                                "This PDF contains a text line that is too large to edit."
                            }
                            if (cleaned.isNotEmpty())
                                put(
                                    "content",
                                    JSONArray()
                                        .put(JSONObject().put("type", "text").put("text", cleaned)),
                                )
                        }
                    )
                }
            }
            return JSONObject().put("type", "doc").put("content", nodes)
        }
    }

    private class BoundedTextWriter(private val maximum: Int, private val check: () -> Unit) :
        Writer() {
        private val result = StringBuilder()

        override fun write(chars: CharArray, offset: Int, length: Int) {
            check()
            require(result.length + length <= maximum) {
                "This PDF contains too much text to edit. Save a smaller page selection first."
            }
            result.append(chars, offset, length)
        }

        override fun flush() = Unit

        override fun close() = Unit

        fun value() = result.toString()
    }

    private class BoundedPdfOutput(
        private val output: OutputStream,
        private val check: () -> Unit,
    ) : OutputStream() {
        private var written = 0L

        override fun write(value: Int) {
            reserve(1)
            output.write(value)
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            reserve(length)
            output.write(bytes, offset, length)
        }

        override fun flush() = output.flush()

        private fun reserve(count: Int) {
            check()
            written += count
            require(written <= ImportedPdfStore.MAX_BYTES) {
                "This PDF copy exceeds 30 MB. Choose fewer pages."
            }
        }
    }
}
