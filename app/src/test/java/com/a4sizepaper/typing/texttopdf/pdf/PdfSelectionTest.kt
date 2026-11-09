package com.a4sizepaper.typing.texttopdf.pdf

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class PdfSelectionTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun nativeTextAndRotatedPagesRetainSelectableWordsWhileScanPagesDoNot() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        PDFBoxResourceLoader.init(context)
        val file=temporary.newFile("selection.pdf")
        PDDocument().use { doc ->
            for (rotation in listOf(0,90,180,270)) {
                val page=PDPage(PDRectangle.A4).apply { this.rotation=rotation }
                doc.addPage(page)
                PDPageContentStream(doc,page).use { stream ->
                    stream.beginText(); stream.setFont(PDType1Font.HELVETICA, 14f)
                    stream.newLineAtOffset(50f,700f); stream.showText("Hello selectable world"); stream.endText()
                }
            }
            doc.addPage(PDPage(PDRectangle.A4))
            doc.save(file)
        }
        val original=file.readBytes()
        val session=PdfTextSession(context,file)
        try {
            repeat(4) { index ->
                val page=session.page(index)
                assertTrue(page.text.contains("Hello selectable world"))
                assertTrue(page.glyphs.isNotEmpty())
                page.glyphs.forEach {
                    assertTrue(it.left>=0f && it.right<=1f && it.top>=0f && it.bottom<=1f)
                    assertTrue(it.right>it.left && it.bottom>it.top)
                    assertTrue(it.start in page.text.indices && it.end<=page.text.length)
                }
            }
            assertTrue(session.page(4).glyphs.isEmpty())
        } finally { session.close() }
        assertArrayEquals(original,file.readBytes())
    }
}
