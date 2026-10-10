package com.a4sizepaper.typing.texttopdf.data

import android.content.Context
import android.util.AtomicFile
import androidx.room.*
import com.a4sizepaper.typing.texttopdf.domain.Document
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Entity(tableName = "documents")
data class DocumentEntry(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val isDraft: Boolean,
)

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY modifiedAt DESC")
    fun observe(): Flow<List<DocumentEntry>>

    @Upsert suspend fun upsert(entry: DocumentEntry)

    @Query("DELETE FROM documents WHERE id = :id") suspend fun delete(id: String)
}

@Database(entities = [DocumentEntry::class], version = 1, exportSchema = true)
abstract class LekhakDatabase : RoomDatabase() {
    abstract fun documents(): DocumentDao
}

@Singleton
class DocumentRepository
@Inject
constructor(@ApplicationContext context: Context, private val database: LekhakDatabase) {
    private val filesDirectory = context.filesDir
    private val directory = File(context.filesDir, "documents").apply { mkdirs() }
    private val mutex = Mutex()
    val documents = database.documents().observe()

    private fun file(id: String): File {
        java.util.UUID.fromString(id)
        return File(directory, "$id.json")
    }

    suspend fun recover(): List<String> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val failures = mutableListOf<String>()
                directory
                    .listFiles()
                    ?.filter { it.extension == "deleted" }
                    ?.forEach {
                        val id = it.nameWithoutExtension
                        database.documents().delete(id)
                        AtomicFile(file(id)).delete()
                        it.delete()
                    }
                directory
                    .listFiles()
                    ?.filter { it.extension == "json" || it.name.endsWith(".json.bak") }
                    ?.map { it.name.removeSuffix(".bak").removeSuffix(".json") }
                    ?.distinct()
                    ?.forEach { id ->
                        runCatching { loadUnlocked(id) }
                            .onSuccess { database.documents().upsert(it.entry()) }
                            .onFailure { failures += id }
                    }
                if (failures.isEmpty() && cleanupStarted.compareAndSet(false, true)) {
                    // Startup only: no live undo history exists yet. Fail closed on unreadable
                    // snapshots.
                    runCatching { cleanupAssets() }
                    runCatching {
                        com.a4sizepaper.typing.texttopdf.pdf.ExportRetention.clean(
                            File(filesDirectory, "exports")
                        )
                    }
                }
                failures
            }
        }

    suspend fun load(id: String): Document =
        withContext(Dispatchers.IO) { mutex.withLock { loadUnlocked(id) } }

    private fun loadUnlocked(id: String) =
        Document.fromJson(JSONObject(AtomicFile(file(id)).readFully().toString(Charsets.UTF_8)))

    suspend fun save(document: Document) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                document.page.validate()
                require(document.content.length <= 8_000_000)
                val json = document.toJson()
                val references = AssetStore.references(json.getJSONObject("content"))
                json.put("assets", org.json.JSONArray(references))
                // Classify inside the ordered IO transaction, not from a lagging UI list.
                if (
                    document.hasDefaultTitle() &&
                        Document.isBlankJson(json.getJSONObject("content"))
                ) {
                    deleteUnlocked(document.id)
                    return@withLock
                }
                val atomic = AtomicFile(file(document.id))
                val stream = atomic.startWrite()
                try {
                    stream.write(json.toString().toByteArray(Charsets.UTF_8))
                    atomic.finishWrite(stream)
                } catch (e: Exception) {
                    atomic.failWrite(stream)
                    throw e
                }
                database.documents().upsert(document.entry())
            }
        }

    suspend fun delete(id: String) =
        withContext(Dispatchers.IO) { mutex.withLock { deleteUnlocked(id) } }

    private suspend fun deleteUnlocked(id: String) {
        val documentFile = file(id)
        val tombstone = AtomicFile(File(directory, "$id.deleted"))
        val stream = tombstone.startWrite()
        tombstone.finishWrite(stream)
        database.documents().delete(id)
        AtomicFile(documentFile).delete()
        tombstone.delete()
    }

    private fun cleanupAssets() {
        val retained = linkedSetOf<String>()
        for (folder in listOf(directory, File(filesDirectory, "export-requests"))) {
            if (!folder.exists()) continue
            val files = requireNotNull(folder.listFiles())
            val snapshots =
                files
                    .filter { it.name.endsWith(".json") || it.name.endsWith(".json.bak") }
                    .map { File(folder, it.name.removeSuffix(".bak")) }
                    .distinct()
            for (snapshot in snapshots) {
                val json = JSONObject(AtomicFile(snapshot).readFully().toString(Charsets.UTF_8))
                retained.addAll(AssetStore.references(json.getJSONObject("content")))
            }
        }
        AssetGarbageCollector.collect(
            File(filesDirectory, "assets"),
            retained,
            processStartedAt - 24L * 60 * 60 * 1000,
        )
    }

    companion object {
        private val cleanupStarted = java.util.concurrent.atomic.AtomicBoolean(false)
        private val processStartedAt = System.currentTimeMillis()
    }

    private fun Document.entry() = DocumentEntry(id, title, createdAt, modifiedAt, isDraft)
}
