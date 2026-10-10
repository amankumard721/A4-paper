package com.a4lekhak.documentmaker

import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.a4lekhak.documentmaker.data.DocumentRepository
import com.a4lekhak.documentmaker.data.LekhakDatabase
import com.a4lekhak.documentmaker.data.SaveQueue
import com.a4lekhak.documentmaker.domain.Document
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class BlankSaveTest {
    @Test
    fun queuedTypingThenClearRemovesSavedDraftAndMalformedWritePreservesOriginal() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root =
            File(base.cacheDir, "save-test-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val context =
            object : ContextWrapper(base) {
                override fun getFilesDir() = root
            }
        val database = Room.inMemoryDatabaseBuilder(context, LekhakDatabase::class.java).build()
        val repository = DocumentRepository(context, database)
        val failures = mutableListOf<Throwable>()
        val queue = SaveQueue<Document>({ repository.save(it) }, {}, { failures.add(it) })
        try {
            val blank = Document()
            val typed =
                blank.copy(
                    content =
                        """{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"hello"}]}]}"""
                )
            queue.save(typed.id, typed)
            queue.save(blank.id, blank)
            queue.flush()
            assertTrue(database.documents().observe().first().isEmpty())
            assertFalse(File(root, "documents/${blank.id}.json").exists())
            repository.save(typed)
            assertTrue(runCatching { repository.save(typed.copy(content = "{broken")) }.isFailure)
            assertEquals(typed.content, repository.load(typed.id).content)
            assertTrue(failures.isEmpty())
        } finally {
            queue.close()
            queue.awaitClosed()
            database.close()
            root.deleteRecursively()
        }
    }
}
