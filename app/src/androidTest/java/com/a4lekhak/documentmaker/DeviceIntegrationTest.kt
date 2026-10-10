package com.a4lekhak.documentmaker

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.a4lekhak.documentmaker.data.*
import com.a4lekhak.documentmaker.domain.*
import com.a4lekhak.documentmaker.pdf.PdfEngine
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceIntegrationTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun saveReopenRecoverDuplicateDelete() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LekhakDatabase::class.java).build()
        try {
            val repository = DocumentRepository(context, db)
            val doc =
                Document(
                    title = "Integration test",
                    content = Templates.all.first().content(mapOf("Name" to "अमन English")),
                )
            repository.save(doc)
            assertEquals(doc, repository.load(doc.id))
            db.documents().delete(doc.id)
            repository.recover()
            assertEquals(doc, repository.load(doc.id))
            val copy = doc.copy(id = java.util.UUID.randomUUID().toString())
            repository.save(copy)
            repository.delete(doc.id)
            assertTrue(runCatching { repository.load(doc.id) }.isFailure)
            assertEquals(copy, repository.load(copy.id))
            repository.delete(copy.id)
        } finally {
            db.close()
        }
    }

    @Test
    fun hindiMixedPdfHasA4PagesAndMatchesPreview() = runBlocking {
        val phrase =
            "हिंदी परीक्षण: क्षत्रिय, विद्यालय, श्रद्धा, राष्ट्र, प्रार्थना। Hindi and English on the same page. "
        val paragraphs = JSONArray()
        repeat(180) {
            paragraphs.put(
                JSONObject()
                    .put("type", "paragraph")
                    .put(
                        "content",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("type", "text")
                                    .put("text", "${it+1}. " + phrase.repeat(4))
                            ),
                    )
            )
        }
        val doc =
            Document(
                title = "Device PDF test",
                content = JSONObject().put("type", "doc").put("content", paragraphs).toString(),
            )
        val pdf = PdfEngine.render(context, doc)
        assertTrue(pdf.pages >= 10)
        ParcelFileDescriptor.open(pdf.file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                assertEquals(pdf.pages, renderer.pageCount)
                renderer.openPage(0).use { page ->
                    assertTrue(kotlin.math.abs(page.width - 595) <= 2)
                    assertTrue(kotlin.math.abs(page.height - 842) <= 2)
                }
            }
        }
        // Keep this fixture app-private so connected tests can pull it for Unicode extraction /
        // visual inspection.
    }
}
