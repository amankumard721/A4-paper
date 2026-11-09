package com.a4sizepaper.typing.texttopdf.data

import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImportedPdfStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun store() = ImportedPdfStore(temporary.root)

    private suspend fun add(
        store: ImportedPdfStore,
        title: String = "A PDF",
        pages: Int = 2,
    ): ImportedPdfEntry {
        val stage = store.newStaging()
        stage.writeText("%PDF-1.4\nprivate original bytes")
        return store.publish(stage, title, pages)
    }

    @Test
    fun survivesNewStoreInstanceAndPreservesOriginalBytesOnRenameAndDuplicate() = runBlocking {
        val original = add(store(), "Tax.PDF")
        val bytes = original.file.readBytes()
        val reopened = ImportedPdfStore(temporary.root)
        assertEquals(original.id, reopened.entries().single().id)
        assertEquals(2, reopened.get(original.id).pages)
        val renamed = reopened.rename(original.id, " ../user name/ नमस्ते ")
        assertEquals("../user name/ नमस्ते", renamed.title)
        assertEquals(original.file, renamed.file)
        assertArrayEquals(bytes, renamed.file.readBytes())
        val duplicate = reopened.duplicate(original.id)
        assertNotEquals(original.id, duplicate.id)
        assertEquals(original.file.parentFile, duplicate.file.parentFile)
        assertArrayEquals(bytes, duplicate.file.readBytes())
        reopened.delete(original.id)
        assertFalse(original.file.exists())
        assertArrayEquals(bytes, reopened.entries().single().file.readBytes())
    }

    @Test
    fun identifiersCannotEscapePrivateDirectory() = runBlocking {
        val store = store()
        val entry = add(store)
        val outside = temporary.newFile("outside.pdf").apply { writeText("keep") }
        for (id in listOf("../outside", outside.absolutePath, entry.id.uppercase(), "1-1-1-1-1")) {
            assertThrows(IllegalArgumentException::class.java) { runBlocking { store.delete(id) } }
        }
        assertEquals("keep", outside.readText())
        assertEquals(entry.id, store.entries().single().id)
    }

    @Test
    fun deletesInterruptedRecordsAndOldStagingButPreservesUnreadableRecord() = runBlocking {
        val store = store()
        val entry = add(store)
        val directory = entry.file.parentFile!!
        File(directory, "${entry.id}.deleted").writeText("")
        val oldStage =
            File(directory, ".stage-crash.pdf").apply {
                writeText("partial")
                setLastModified(System.currentTimeMillis() - 48L * 60 * 60 * 1000)
            }
        val freshStage = File(directory, ".stage-live.pdf").apply { writeText("partial") }
        val unknown = UUID.randomUUID().toString()
        val unknownPdf =
            File(directory, "$unknown.pdf").apply {
                writeText("%PDF-keep")
                setLastModified(System.currentTimeMillis() - 48L * 60 * 60 * 1000)
            }
        File(directory, "$unknown.json").writeText("broken metadata")
        assertTrue(store.entries().isEmpty())
        assertFalse(entry.file.exists())
        assertFalse(File(directory, "${entry.id}.json").exists())
        assertFalse(oldStage.exists())
        assertTrue(freshStage.exists())
        assertTrue(unknownPdf.exists())
    }

    @Test
    fun forgedMetadataCannotPointAtAnOutsideFileOrInventPageCounts() = runBlocking {
        val store = store()
        val entry = add(store)
        val metadata = File(entry.file.parentFile, "${entry.id}.json")
        val json = JSONObject(metadata.readText()).put("file", "../../outside.pdf")
        metadata.writeText(json.toString())
        assertEquals(entry.file, store.get(entry.id).file)
        metadata.writeText(json.put("pages", 0).toString())
        assertTrue(store.entries().isEmpty())
        assertTrue(entry.file.exists())
    }

    @Test
    fun largeReaderFilesSurviveReopenButInvalidStagingLocationsAreRejected() = runBlocking {
        val store = store()
        val staging = store.newStaging()
        java.io.RandomAccessFile(staging, "rw").use { it.setLength(ImportedPdfStore.MAX_BYTES + 1) }
        val entry = store.publish(staging, "Large", 1001)
        val reopened = ImportedPdfStore(temporary.root).get(entry.id)
        assertEquals(1001, reopened.pages)
        assertEquals(ImportedPdfStore.MAX_BYTES + 1, reopened.file.length())
        store.delete(entry.id)
        val outside = temporary.newFile(".stage-outside.pdf").apply { writeText("%PDF-1.4") }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store.publish(outside, "Wrong folder", 1) }
        }
        assertTrue(store.entries().isEmpty())
    }
}
