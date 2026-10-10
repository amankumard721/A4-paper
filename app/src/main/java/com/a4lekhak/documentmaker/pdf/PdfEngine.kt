package com.a4lekhak.documentmaker.pdf

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.*
import android.print.*
import android.util.Base64
import com.a4lekhak.documentmaker.domain.Document
import com.a4lekhak.documentmaker.editor.EditorHost
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONObject

data class ExportedPdf(val file: File, val pages: Int, val landscape: Boolean, val title: String)

/** Direct offline vector PDF export. No Android print callback constructors are invoked. */
object PdfEngine {
    fun attributes(landscape: Boolean): PrintAttributes =
        PrintAttributes.Builder()
            .setMediaSize(
                if (landscape) PrintAttributes.MediaSize.ISO_A4.asLandscape()
                else PrintAttributes.MediaSize.ISO_A4
            )
            .setResolution(PrintAttributes.Resolution("pdf", "PDF", 300, 300))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()

    suspend fun render(context: Context, document: Document): ExportedPdf =
        withContext(Dispatchers.Main) {
            val directory =
                withContext(Dispatchers.IO) { File(context.filesDir, "exports").apply { mkdirs() } }
            val temporary = File(directory, "${document.id}-${java.util.UUID.randomUUID()}.partial")
            val destination = File(directory, temporary.nameWithoutExtension + ".pdf")
            val messages = Channel<JSONObject>(4)
            val host =
                EditorHost(context) { message ->
                    if (
                        message.optString("type") in
                            setOf("pdfBegin", "pdfChunk", "pdfEnd", "error")
                    )
                        if (messages.trySend(message).isFailure)
                            messages.close(IllegalStateException("PDF stream overflow"))
                }
            host.layout(
                0,
                0,
                if (document.page.landscape) 1123 else 794,
                if (document.page.landscape) 794 else 1123,
            )
            try {
                host.load(document, renderOnly = true)
                val expected =
                    withTimeout(300_000) {
                        withContext(Dispatchers.IO) {
                            FileOutputStream(temporary).use { output ->
                                var expectedLength = -1
                                var pages = 0
                                var received = 0
                                var nextChunk = 0
                                while (true) {
                                    val message = messages.receive()
                                    when (message.getString("type")) {
                                        "pdfBegin" -> {
                                            check(expectedLength < 0)
                                            expectedLength = message.getInt("length")
                                            pages = message.getInt("pages")
                                            require(
                                                expectedLength in 1..100_000_000 && pages in 1..1000
                                            ) {
                                                "PDF exceeds safe export limits"
                                            }
                                        }
                                        "pdfChunk" -> {
                                            check(expectedLength > 0)
                                            val index = message.getInt("index")
                                            check(index == nextChunk)
                                            val encoded = message.getString("data")
                                            require(encoded.length <= 131072)
                                            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                                            received += bytes.size
                                            check(received <= expectedLength)
                                            output.write(bytes)
                                            nextChunk++
                                            withContext(Dispatchers.Main) {
                                                host.evaluateJavascript(
                                                    "window.Lekhak.ack($index)",
                                                    null,
                                                )
                                            }
                                        }
                                        "pdfEnd" -> {
                                            check(
                                                received == expectedLength &&
                                                    message.getInt("chunks") == nextChunk
                                            )
                                            output.flush()
                                            output.fd.sync()
                                            break
                                        }
                                        "error" ->
                                            throw IllegalStateException(
                                                message.optString("message", "PDF export failed")
                                            )
                                    }
                                }
                                pages
                            }
                        }
                    }
                withContext(Dispatchers.IO) {
                    ParcelFileDescriptor.open(temporary, ParcelFileDescriptor.MODE_READ_ONLY).use {
                        descriptor ->
                        PdfRenderer(descriptor).use { renderer ->
                            require(renderer.pageCount == expected) {
                                "PDF page count differs from preview"
                            }
                            repeat(renderer.pageCount) { index ->
                                renderer.openPage(index).use { page ->
                                    val width = if (document.page.landscape) 842 else 595
                                    val height = if (document.page.landscape) 595 else 842
                                    require(
                                        kotlin.math.abs(page.width - width) <= 2 &&
                                            kotlin.math.abs(page.height - height) <= 2
                                    ) {
                                        "PDF is not A4"
                                    }
                                }
                            }
                        }
                    }
                    check(temporary.renameTo(destination)) { "Could not finalize PDF" }
                }
                ExportedPdf(destination, expected, document.page.landscape, document.title)
            } finally {
                messages.close()
                host.dispose()
                withContext(NonCancellable + Dispatchers.IO) { temporary.delete() }
            }
        }
}

/** System callbacks are supplied by PrintManager through supported public APIs only. */
class ExistingPdfPrintAdapter(private val pdf: ExportedPdf) : PrintDocumentAdapter() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onLayout(
        old: PrintAttributes?,
        new: PrintAttributes,
        signal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (signal.isCanceled) callback.onLayoutCancelled()
        else
            callback.onLayoutFinished(
                PrintDocumentInfo.Builder(pdf.title)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(pdf.pages)
                    .build(),
                false,
            )
    }

    override fun onWrite(
        ranges: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        signal: CancellationSignal,
        callback: WriteResultCallback,
    ) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    pdf.file.inputStream().use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            val buffer = ByteArray(65536)
                            while (true) {
                                ensureActive()
                                if (signal.isCanceled) throw CancellationException()
                                val size = input.read(buffer)
                                if (size < 0) break
                                output.write(buffer, 0, size)
                            }
                        }
                    }
                }
                callback.onWriteFinished(arrayOf(PageRange(0, pdf.pages - 1)))
            } catch (e: CancellationException) {
                callback.onWriteCancelled()
            } catch (e: Exception) {
                callback.onWriteFailed("Could not read the PDF for printing")
            }
        }
    }

    override fun onFinish() {
        scope.cancel()
    }
}
