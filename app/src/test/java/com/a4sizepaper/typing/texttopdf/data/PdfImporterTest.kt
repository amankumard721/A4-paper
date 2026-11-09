package com.a4sizepaper.typing.texttopdf.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises real PDF streams. Native PdfRenderer compatibility is covered on-device. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class PdfImporterTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var context: Application
    private lateinit var store: ImportedPdfStore
    private lateinit var importer: PdfImporter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        store = ImportedPdfStore(temporary.root)
        importer = PdfImporter(context, store)
    }

    private suspend fun fixture(
        blank: Boolean = false,
        encrypted: Boolean = false,
    ): ImportedPdfEntry {
        val stage = store.newStaging()
        PDDocument().use { pdf ->
            repeat(2) { index ->
                val page = PDPage(PDRectangle.A4).apply { rotation = if (index == 0) 0 else 180 }
                page.cropBox = PDRectangle(10f, 20f, 400f, 600f)
                pdf.addPage(page)
                if (!blank)
                    PDPageContentStream(pdf, page).use { canvas ->
                        canvas.beginText()
                        canvas.setFont(PDType1Font.HELVETICA, 12f)
                        canvas.newLineAtOffset(50f, 500f)
                        canvas.showText("Page ${index + 1} selectable text")
                        canvas.endText()
                    }
            }
            if (encrypted)
                pdf.protect(
                    StandardProtectionPolicy("owner", "", AccessPermission()).apply {
                        encryptionKeyLength = 128
                    }
                )
            pdf.save(stage)
        }
        return store.publish(stage, "Actual fixture", 2)
    }

    @Test
    fun extractsRealPdfTextIntoEditableCopyWithoutChangingOriginal() = runBlocking {
        val entry = fixture()
        val bytes = entry.file.readBytes()
        val result = importer.extractText(entry)
        assertTrue(result.document.content.contains("Page 1 selectable text"))
        assertTrue(result.document.content.contains("Page 2 selectable text"))
        assertTrue(result.document.content.contains("pageBreak"))
        assertTrue(result.warnings.single().contains("exact layout are not preserved"))
        assertArrayEquals(bytes, entry.file.readBytes())
        assertEquals(entry.id, store.entries().single().id)
    }

    @Test
    fun copiesActualPagesInOrderAndAddsRotationWhileKeepingSource() = runBlocking {
        val entry = fixture()
        val original = entry.file.readBytes()
        val output = temporary.newFile("selected.pdf")
        importer.writePageCopy(entry.file, 2, listOf(1, 0), 90, output) {}
        PDDocument.load(output).use { pdf ->
            assertEquals(2, pdf.numberOfPages)
            assertEquals(270, pdf.getPage(0).rotation)
            assertEquals(90, pdf.getPage(1).rotation)
            assertEquals(400f, pdf.getPage(0).cropBox.width)
            val text = PDFTextStripper().getText(pdf).replace(Regex("\\s+"), "")
            assertTrue(
                "Copied text should follow selected page order: $text",
                text.contains("Page2") &&
                    text.contains("Page1") &&
                    text.indexOf("Page2") < text.indexOf("Page1"),
            )
            assertNotNull(pdf.getPage(0).resources)
        }
        val stage = store.newStaging()
        output.copyTo(stage, overwrite = true)
        val copy = store.publish(stage, "Rotated copy", 2)
        val editable = importer.extractText(copy)
        assertTrue(editable.document.content.contains("Page 2 selectable text"))
        val artifact = File("../validation/imports/reordered-rotated.pdf")
        requireNotNull(artifact.parentFile).mkdirs()
        output.copyTo(artifact, overwrite = true)
        assertArrayEquals(original, entry.file.readBytes())
    }

    @Test
    fun scannedAndEncryptedFilesExplainWhyTextCannotBeEdited() = runBlocking {
        val blank = fixture(blank = true)
        val noText =
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { importer.extractText(blank) }
            }
        assertTrue(noText.message!!.contains("OCR is not available"))
        assertTrue(blank.file.exists())
        val encrypted = fixture(encrypted = true)
        val protected =
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { importer.extractText(encrypted) }
            }
        assertTrue(protected.message!!.contains("encrypted"))
        assertTrue(encrypted.file.exists())
    }

    @Test
    fun cancellationStopsPageCopyBeforeProducingAResult() = runBlocking {
        val entry = fixture()
        val output = temporary.newFile("cancelled.pdf")
        assertThrows(CancellationException::class.java) {
            importer.writePageCopy(entry.file, 2, listOf(0, 1), 0, output) {
                throw CancellationException("cancel")
            }
        }
        assertEquals(0L, output.length())
        assertTrue(entry.file.exists())
    }
}
