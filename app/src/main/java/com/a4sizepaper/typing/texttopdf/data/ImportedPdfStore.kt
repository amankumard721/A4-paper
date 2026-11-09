package com.a4sizepaper.typing.texttopdf.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ImportedPdfEntry(
    val id: String,
    val title: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val pages: Int,
    val file: File,
)

/** A durable private library. Imported originals never enter the disposable exports directory. */
class ImportedPdfStore internal constructor(private val filesDirectory: File) {
    constructor(context: Context) : this(context.applicationContext.filesDir)

    private val directory = File(filesDirectory, "imported-pdfs")

    suspend fun entries(): List<ImportedPdfEntry> =
        withContext(Dispatchers.IO) {
            lock.withLock {
                prepare()
                recoverDeletions()
                cleanStaging()
                directory
                    .listFiles()
                    .orEmpty()
                    .filter { it.extension == "json" }
                    .mapNotNull { metadata ->
                        // An unreadable record is preserved for recovery, never used as a path.
                        runCatching { read(metadata.nameWithoutExtension) }.getOrNull()
                    }
                    .sortedByDescending { it.modifiedAt }
            }
        }

    suspend fun get(id: String): ImportedPdfEntry =
        withContext(Dispatchers.IO) {
            lock.withLock {
                prepare()
                read(id)
            }
        }

    suspend fun rename(id: String, title: String): ImportedPdfEntry =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val current = read(id)
                current
                    .copy(title = cleanTitle(title), modifiedAt = System.currentTimeMillis())
                    .also { write(it) }
            }
        }

    suspend fun duplicate(id: String): ImportedPdfEntry =
        withContext(Dispatchers.IO) {
            // The lock prevents deletion while a source is copied; copies never rewrite PDFs.
            lock.withLock {
                val current = read(id)
                val staging = newStagingUnlocked()
                try {
                    current.file.inputStream().use { input ->
                        FileOutputStream(staging).use { output ->
                            val buffer = ByteArray(16_384)
                            var total = 0L
                            while (true) {
                                ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                if (total % (1024 * 1024) < buffer.size) requireCopySpace(staging)
                                output.write(buffer, 0, count)
                            }
                            output.fd.sync()
                        }
                    }
                    ensureActive()
                    publishUnlocked(staging, "${current.title} (copy)", current.pages)
                } finally {
                    staging.delete()
                }
            }
        }

    suspend fun delete(id: String) =
        withContext(Dispatchers.IO) {
            lock.withLock {
                prepare()
                val safeId = validId(id)
                // A tombstone completes an interrupted delete on the next library load.
                val marker = File(directory, "$safeId.deleted")
                FileOutputStream(marker).use { it.fd.sync() }
                completeDeletion(safeId)
            }
        }

    internal suspend fun newStaging(): File =
        withContext(Dispatchers.IO) { lock.withLock { newStagingUnlocked() } }

    internal suspend fun publish(staging: File, title: String, pages: Int): ImportedPdfEntry =
        withContext(Dispatchers.IO) { lock.withLock { publishUnlocked(staging, title, pages) } }

    private fun prepare() {
        require(directory.isDirectory || directory.mkdirs()) { "PDF storage is unavailable." }
    }

    private fun newStagingUnlocked(): File {
        prepare()
        return File.createTempFile(".stage-", ".pdf", directory)
    }

    private fun publishUnlocked(staging: File, title: String, pages: Int): ImportedPdfEntry {
        prepare()
        require(
            staging.canonicalFile.parentFile == directory.canonicalFile &&
                staging.name.startsWith(".stage-")
        )
        require(staging.isFile && staging.length() >= 5L) { "This PDF is empty or unavailable." }
        require(pages > 0) { "This PDF has no pages." }
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val entry = ImportedPdfEntry(id, cleanTitle(title), now, now, pages, pdfFile(id))
        move(staging, entry.file)
        try {
            write(entry)
        } catch (error: Exception) {
            entry.file.delete()
            throw error
        }
        return entry
    }

    private fun read(id: String): ImportedPdfEntry {
        val safeId = validId(id)
        require(!File(directory, "$safeId.deleted").exists()) { "This PDF was deleted." }
        val metadata = File(directory, "$safeId.json")
        require(metadata.isFile && metadata.length() <= 16_384) { "This PDF is unavailable." }
        val json = JSONObject(metadata.readText(Charsets.UTF_8))
        require(json.getInt("schema") == 1 && json.getString("id") == safeId) {
            "This PDF record is unsupported."
        }
        val file = pdfFile(safeId)
        val pages = json.getInt("pages")
        require(file.isFile && file.length() >= 5L && pages > 0) {
            "This PDF is unavailable."
        }
        return ImportedPdfEntry(
            safeId,
            cleanTitle(json.getString("title")),
            json.getLong("createdAt"),
            json.getLong("modifiedAt"),
            pages,
            file,
        )
    }

    private fun write(entry: ImportedPdfEntry) {
        val id = validId(entry.id)
        val metadata = File(directory, "$id.json")
        val pending = File(directory, "$id.json.new")
        val bytes =
            JSONObject()
                .put("schema", 1)
                .put("id", id)
                .put("title", entry.title)
                .put("createdAt", entry.createdAt)
                .put("modifiedAt", entry.modifiedAt)
                .put("pages", entry.pages)
                .toString()
                .toByteArray(Charsets.UTF_8)
        try {
            FileOutputStream(pending).use {
                it.write(bytes)
                it.fd.sync()
            }
            move(pending, metadata)
        } finally {
            pending.delete()
        }
    }

    private fun recoverDeletions() {
        directory
            .listFiles()
            .orEmpty()
            .filter { it.extension == "deleted" }
            .forEach { marker ->
                if (runCatching { validId(marker.nameWithoutExtension) }.isSuccess)
                    completeDeletion(marker.nameWithoutExtension)
            }
    }

    private fun completeDeletion(id: String) {
        for (suffix in listOf("json", "json.new", "pdf")) {
            val file = File(directory, "$id.$suffix")
            check(!file.exists() || file.delete()) {
                "Could not delete this PDF. Please try again."
            }
        }
        check(File(directory, "$id.deleted").delete()) { "Could not finish deleting this PDF." }
    }

    private fun cleanStaging() {
        val olderThan = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        directory
            .listFiles()
            .orEmpty()
            .filter { it.lastModified() < olderThan }
            .forEach { file ->
                when {
                    file.name.startsWith(".stage-") || file.name.endsWith(".json.new") ->
                        file.delete()
                    file.extension == "pdf" &&
                        runCatching { validId(file.nameWithoutExtension) }.isSuccess -> {
                        // Never remove a PDF whose metadata exists but could not be parsed.
                        if (!File(directory, "${file.nameWithoutExtension}.json").exists())
                            file.delete()
                    }
                }
            }
    }

    private fun pdfFile(id: String) = File(directory, "${validId(id)}.pdf")

    companion object {
        // Transformation tools keep a separate budget; the reader/library has no fixed size cap.
        internal fun requireCopySpace(file: File) {
            require(file.parentFile!!.usableSpace > 16L * 1024 * 1024) {
                "Not enough free storage to open or copy this PDF. Free some space and try again."
            }
        }

        const val MAX_BYTES = 30L * 1024 * 1024
        const val MAX_PAGES = 300
        private val lock = Mutex()

        private fun validId(id: String): String {
            require(UUID.fromString(id).toString() == id) { "Invalid PDF identifier." }
            return id
        }

        private fun cleanTitle(title: String): String =
            title
                .replace(Regex("[\\p{Cntrl}]"), " ")
                .trim()
                .replace(Regex("(?i)\\.pdf$"), "")
                .trim()
                .take(200)
                .ifEmpty { "Imported PDF" }

        private fun move(source: File, target: File) {
            try {
                Files.move(
                    source.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
