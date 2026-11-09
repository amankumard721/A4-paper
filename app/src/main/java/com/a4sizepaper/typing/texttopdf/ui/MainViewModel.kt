package com.a4sizepaper.typing.texttopdf.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.a4sizepaper.typing.texttopdf.data.*
import com.a4sizepaper.typing.texttopdf.domain.*
import com.a4sizepaper.typing.texttopdf.pdf.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject

@HiltViewModel
class MainViewModel
@Inject
constructor(
    private val repository: DocumentRepository,
    private val preferences: PreferenceRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val prefs = preferences.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val documents =
        repository.documents.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    suspend fun previewParagraphs(id: String): List<String> =
        withContext(Dispatchers.IO) {
            try {
                val root = JSONObject(repository.load(id).content)
                val lines = mutableListOf<String>()
                fun visit(node: JSONObject, depth: Int) {
                    if (depth > 80 || lines.size >= 8) return
                    node
                        .optString("text")
                        .takeIf { it.isNotBlank() }
                        ?.let { lines.add(it.take(100)) }
                    if (node.optString("type") == "image") lines.add("▧")
                    val children = node.optJSONArray("content") ?: return
                    for (i in 0 until children.length()) {
                        if (lines.size >= 8) break
                        children.optJSONObject(i)?.let { visit(it, depth + 1) }
                    }
                }
                visit(root, 0)
                lines
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
        }

    val current = MutableStateFlow<Document?>(null)
    val saving = MutableStateFlow("Ready")
    val error = MutableStateFlow<String?>(null)
    val recovered = MutableStateFlow(false)
    val exporting = MutableStateFlow(false)
    val importing = MutableStateFlow(false)
    val importNotice = MutableStateFlow<List<String>?>(null)
    val importLabel = MutableStateFlow("Document")
    val pendingImport = MutableStateFlow<PendingImport?>(null)
    val importedPdfs = MutableStateFlow<List<ImportedPdfEntry>>(emptyList())
    val viewingImportedPdf = MutableStateFlow<ImportedPdfEntry?>(null)
    private val importedPdfStore = ImportedPdfStore(context)
    private var importJob: Job? = null

    fun importImageDocument(uri: android.net.Uri, onReady: () -> Unit) {
        if (importing.value || exporting.value) return
        importing.value = true
        importLabel.value = "Image"
        importJob =
            viewModelScope.launch {
                try {
                    flush()
                    val id = AssetStore(context).importImage(uri)
                    val page = prefs.value?.defaults ?: PageSettings()
                    val content =
                        withContext(Dispatchers.Default) {
                            val bitmap =
                                android.graphics.BitmapFactory.Options().apply {
                                    inJustDecodeBounds = true
                                }
                            android.graphics.BitmapFactory.decodeFile(
                                AssetStore(context).file(id)!!.path,
                                bitmap,
                            )
                            val availableWidth = page.widthMm - page.marginLeft - page.marginRight
                            val availableHeight =
                                page.heightMm - page.marginTop - page.marginBottom - 14.0
                            val width =
                                minOf(
                                        100.0,
                                        availableHeight * bitmap.outWidth /
                                            bitmap.outHeight /
                                            availableWidth * 100,
                                    )
                                    .also { require(it >= 10.0) { "This image is too tall for one A4 page. Crop it before importing." } }
                            JSONObject()
                                .put("type", "doc")
                                .put(
                                    "content",
                                    org.json
                                        .JSONArray()
                                        .put(
                                            JSONObject()
                                                .put("type", "image")
                                                .put(
                                                    "attrs",
                                                    JSONObject()
                                                        .put("assetId", id)
                                                        .put("width", width)
                                                        .put("align", "center")
                                                        .put("alt", "Imported image"),
                                                )
                                        )
                                        .put(JSONObject().put("type", "paragraph")),
                                )
                                .toString()
                        }
                    val document =
                        Document(
                            title =
                                if (prefs.value?.language == "hi") "चित्र दस्तावेज़"
                                else "Image document",
                            content = content,
                            page = page,
                            assets = listOf(id),
                        )
                    repository.save(document)
                    current.value = document
                    saving.value = "Saved on device"
                    onReady()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    error.value = e.message ?: "Could not import the image."
                } finally {
                    importing.value = false
                    importLabel.value = "Document"
                }
            }
    }

    fun offerImport(uri: android.net.Uri, format: ImportFormat? = null, external: Boolean = false) {
        if (importing.value || exporting.value) {
            error.value = "Wait for the current import or export to finish."
            return
        }
        viewModelScope.launch {
            try {
                val kind =
                    withContext(Dispatchers.IO) { ImportFormat.validate(context, uri, format) }
                pendingImport.value = PendingImport(uri, kind, external)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.message ?: "Cannot open this file."
            }
        }
    }

    private var spreadsheetExportJob: Job? = null
    internal val spreadsheet = MutableStateFlow<SheetBook?>(null)

    internal fun openSpreadsheet(request: PendingImport, onReady: () -> Unit) {
        if (importing.value || exporting.value) return
        importing.value = true
        importLabel.value = request.format.label
        pendingImport.value = null
        importJob = viewModelScope.launch {
            try {
                spreadsheet.value = ExcelImporter(context).openGrid(request.uri)
                onReady()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error.value = failure.message ?: "Could not open the spreadsheet."
            } finally {
                importing.value = false
            }
        }
    }

    internal fun exportSpreadsheet(book: SheetBook): Boolean {
        if (exporting.value || importing.value) return false
        viewingImportedPdf.value = null
        pdf.value = null
        exporting.value = true
        spreadsheetExportJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) { ExcelReader().exportGrid(book) }
                ExportWorker.enqueue(context, Document(title = book.title, content = result.content.toString(), page = result.page))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                exporting.value = false
                error.value = failure.message ?: "Could not export the spreadsheet."
            }
        }
        return true
    }

    fun importFile(request: PendingImport, onReady: (Boolean) -> Unit) {
        if (importing.value) return
        importing.value = true
        importLabel.value = request.format.label
        pendingImport.value = null
        importJob =
            viewModelScope.launch {
                try {
                    flush()
                    if (request.format == ImportFormat.PDF) {
                        val entry = PdfImporter(context, importedPdfStore).import(request.uri)
                        refreshImportedPdfs()
                        showImportedPdf(entry)
                        onReady(true)
                    } else {
                        val (document, warnings) =
                            when (request.format) {
                                ImportFormat.EXCEL ->
                                    ExcelImporter(context).import(request.uri).let {
                                        it.document to it.warnings
                                    }
                                else ->
                                    WordImporter(context).import(request.uri).let {
                                        it.document to it.warnings
                                    }
                            }
                        repository.save(document)
                        current.value = document
                        saving.value = "Saved on device"
                        importNotice.value = warnings
                        onReady(false)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error.value =
                        "${request.format.label} import failed: " +
                            (e.message ?: "Choose an unprotected supported file.")
                } finally {
                    importing.value = false
                }
            }
    }

    fun buildPhotoPdf(
        photos: List<android.net.Uri>,
        previous: ImportedPdfEntry? = null,
        scan: Pair<android.net.Uri, Int>? = null,
        onReady: (ImportedPdfEntry) -> Unit,
    ) {
        if (importing.value || exporting.value) return
        importing.value = true
        importLabel.value = "photo PDF"
        importJob = viewModelScope.launch {
            try {
                val entry = PdfImporter(context, importedPdfStore).photoPdf(photos, previous, scan)
                refreshImportedPdfs()
                showImportedPdf(entry)
                onReady(entry)
            } catch (error: CancellationException) {
                throw error
            } catch (failure: Exception) {
                error.value = failure.message ?: "Could not add these pages."
            } finally {
                importing.value = false
            }
        }
    }

    fun importScan(uri: android.net.Uri, pages: Int, onReady: () -> Unit) {
        if (importing.value || exporting.value) {
            error.value = "Wait for the current import or export before saving a scan."
            return
        }
        importing.value = true
        importLabel.value = "scanned PDF"
        importJob =
            viewModelScope.launch {
                try {
                    val title =
                        "Scan " +
                            java.time.LocalDateTime.now()
                                .format(
                                    java.time.format.DateTimeFormatter.ofPattern(
                                        "yyyy-MM-dd HH.mm.ss"
                                    )
                                )
                    val entry = PdfImporter(context, importedPdfStore).importScan(uri, pages, title)
                    refreshImportedPdfs()
                    showImportedPdf(entry)
                    onReady()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error.value =
                        e.message
                            ?: "Could not save the scanned PDF. Scan a smaller batch and try again."
                } finally {
                    importing.value = false
                }
            }
    }

    fun cancelImport() {
        importJob?.cancel()
    }

    private suspend fun refreshImportedPdfs() {
        importedPdfs.value = importedPdfStore.entries()
    }

    private fun showImportedPdf(entry: ImportedPdfEntry) {
        viewingImportedPdf.value = entry
        pdf.value = ExportedPdf(entry.file, entry.pages, false, entry.title)
    }

    fun openImportedPdf(entry: ImportedPdfEntry, onReady: () -> Unit) =
        viewModelScope.launch {
            try {
                showImportedPdf(importedPdfStore.get(entry.id))
                onReady()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.message ?: "Cannot open PDF."
            }
        }

    fun renameImportedPdf(entry: ImportedPdfEntry, title: String) = pdfMutation {
        importedPdfStore.rename(entry.id, title)
    }

    fun duplicateImportedPdf(entry: ImportedPdfEntry) = pdfMutation {
        importedPdfStore.duplicate(entry.id)
    }

    fun deleteImportedPdf(entry: ImportedPdfEntry) = pdfMutation {
        importedPdfStore.delete(entry.id)
        if (viewingImportedPdf.value?.id == entry.id) {
            viewingImportedPdf.value = null
            pdf.value = null
        }
    }

    private fun pdfMutation(block: suspend () -> Unit) =
        viewModelScope.launch {
            try {
                block()
                refreshImportedPdfs()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.message ?: "Could not update the PDF."
            }
        }

    fun editPdfText(entry: ImportedPdfEntry, onReady: () -> Unit) {
        if (importing.value) return
        importing.value = true
        importLabel.value = "PDF text"
        importJob =
            viewModelScope.launch {
                try {
                    flush()
                    val imported = PdfImporter(context, importedPdfStore).extractText(entry)
                    repository.save(imported.document)
                    current.value = imported.document
                    saving.value = "Saved on device"
                    importNotice.value = imported.warnings
                    onReady()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error.value = e.message ?: "Could not extract PDF text."
                } finally {
                    importing.value = false
                }
            }
    }

    fun compressPdf(uri: android.net.Uri, onReady: () -> Unit) {
        if (importing.value || exporting.value) return
        importing.value = true
        importLabel.value = "PDF compression"
        importJob = viewModelScope.launch {
            try {
                val compressed = PdfImporter(context, importedPdfStore).compress(uri)
                refreshImportedPdfs()
                showImportedPdf(compressed)
                onReady()
            } catch (error: CancellationException) {
                throw error
            } catch (failure: Exception) {
                error.value = failure.message ?: "Could not compress this PDF."
            } finally {
                importing.value = false
            }
        }
    }

    fun mergePdfs(uris: List<android.net.Uri>, onReady: () -> Unit) {
        if (importing.value || exporting.value) return
        importing.value = true
        importLabel.value = "PDF merge"
        importJob = viewModelScope.launch {
            try {
                val merged = PdfImporter(context, importedPdfStore).merge(uris)
                refreshImportedPdfs()
                showImportedPdf(merged)
                onReady()
            } catch (error: CancellationException) {
                throw error
            } catch (failure: Exception) {
                error.value = failure.message ?: "Could not merge these PDFs."
            } finally {
                importing.value = false
            }
        }
    }

    fun copyPdfPages(entry: ImportedPdfEntry, ranges: String, rotation: Int, onReady: () -> Unit) {
        if (importing.value) return
        importing.value = true
        importLabel.value = "PDF pages"
        importJob =
            viewModelScope.launch {
                try {
                    val copy =
                        PdfImporter(context, importedPdfStore).createCopy(entry, ranges, rotation)
                    refreshImportedPdfs()
                    showImportedPdf(copy)
                    onReady()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error.value = e.message ?: "Could not create PDF copy."
                } finally {
                    importing.value = false
                }
            }
    }

    val pdf = MutableStateFlow<ExportedPdf?>(null)
    private var completedExport: java.util.UUID? = null
    private val writes =
        SaveQueue<Document>(
            write = { repository.save(it) },
            onSaved = { doc ->
                if (current.value == doc)
                    saving.value =
                        if (doc.hasDefaultTitle() && doc.isBlankContent()) "Unsaved"
                        else if (doc.isDraft) "Draft protected" else "Saved on device"
            },
            onFailure = {
                error.value = it.message ?: "Could not save the document"
                saving.value = "Save failed"
            },
        )

    init {
        viewModelScope.launch {
            try {
                refreshImportedPdfs()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.message ?: "Could not load imported PDFs."
            }
        }
        enqueue {
            try {
                val failed = repository.recover()
                if (failed.isNotEmpty())
                    error.value =
                        "${failed.size} document(s) could not be recovered. Original files were preserved."
            } finally {
                recovered.value = true
            }
        }
        viewModelScope.launch {
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow("pdf-export").collect {
                jobs ->
                val job =
                    jobs.maxByOrNull {
                        it.tags
                            .firstOrNull { tag -> tag.startsWith("export-time:") }
                            ?.substringAfter(':')
                            ?.toLongOrNull() ?: 0L
                    } ?: return@collect
                exporting.value =
                    job.state == WorkInfo.State.RUNNING ||
                        job.state == WorkInfo.State.ENQUEUED ||
                        job.state == WorkInfo.State.BLOCKED
                if (job.state == WorkInfo.State.SUCCEEDED) {
                    val data = job.outputData
                    val path = data.getString("path")
                    if (path != null && File(path).exists() && completedExport != job.id) {
                        completedExport = job.id
                        if (viewingImportedPdf.value == null)
                            pdf.value =
                                ExportedPdf(
                                    File(path),
                                    data.getInt("pages", 1),
                                    data.getBoolean("landscape", false),
                                    data.getString("title") ?: "Document",
                                )
                        val id = data.getString("documentId")
                        if (id != null) {
                            markExport(id, data.getInt("pages", 1))
                        }
                    }
                }
                if (job.state == WorkInfo.State.FAILED)
                    error.value = job.outputData.getString("error") ?: "PDF export failed"
            }
        }
    }

    private fun enqueue(action: suspend () -> Unit) = writes.enqueue(action)

    private fun persist(document: Document) {
        current.value = document
        saving.value = "Saving…"
        writes.save(document.id, document)
    }

    suspend fun flush() = writes.flush()

    fun create(
        template: Template? = null,
        values: Map<String, String> = emptyMap(),
        onReady: () -> Unit,
    ) {
        val document =
            Document(
                title =
                    template?.name
                        ?: if (prefs.value?.language == "hi") "नया दस्तावेज़"
                        else "Untitled document",
                content = template?.content(values) ?: Document().content,
                page = prefs.value?.defaults ?: PageSettings(),
            )
        if (template != null) {
            persist(document)
        } else {
            current.value = document
            saving.value = "Unsaved"
        }
        onReady()
    }

    fun open(id: String, onReady: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                    flush()
                    repository.load(id)
                }
                .onSuccess {
                    current.value = it
                    saving.value = if (it.isDraft) "Recovered draft" else "Saved on device"
                    onReady()
                }
                .onFailure { error.value = it.message }
        }
    }

    private fun isDefaultTitle(title: String) =
        title.isBlank() || title == "Untitled document" || title == "नया दस्तावेज़"

    fun edit(content: String, documentId: String) {
        val before = current.value ?: return
        if (before.id != documentId) return
        if (content == before.content) return
        // The save queue validates JSON once off the UI thread before replacing the saved file.
        if (content.length > 8_000_000) {
            error.value = "The document is too large"
            return
        }
        val updated =
            before.copy(
                content = content,
                modifiedAt = System.currentTimeMillis(),
                isDraft = prefs.value?.autosave == false,
            )
        persist(updated)
    }

    fun title(value: String) {
        current.value?.let {
            persist(it.copy(title = value.take(200), modifiedAt = System.currentTimeMillis()))
        }
    }

    fun importImage(
        uri: android.net.Uri,
        documentId: String,
        signature: Boolean,
        onReady: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { AssetStore(context).importImage(uri, signature) }
                .onSuccess { id ->
                    if (current.value?.id == documentId)
                        onReady(
                            JSONObject().put("assetId", id).put("signature", signature).toString()
                        )
                }
                .onFailure { error.value = it.message ?: "Could not import image" }
        }
    }

    fun page(value: PageSettings) {
        current.value?.let {
            val updated = it.copy(page = value.validate(), modifiedAt = System.currentTimeMillis())
            if (updated.isBlankContent() && isDefaultTitle(updated.title)) {
                current.value = updated
            } else {
                persist(updated)
            }
        }
    }

    fun save() {
        current.value?.let {
            if (!it.isBlankContent() || !isDefaultTitle(it.title)) {
                persist(it.copy(isDraft = false))
            }
        }
    }

    fun export(): Boolean {
        viewingImportedPdf.value = null
        val snapshot = current.value ?: return false
        if (snapshot.isBlankContent()) {
            error.value =
                if (prefs.value?.language == "hi") "PDF बनाने से पहले दस्तावेज़ में कुछ लिखें।"
                else "Please write something in the document before generating a PDF."
            return false
        }
        pdf.value = null
        exporting.value = true
        viewModelScope.launch {
            runCatching {
                    flush()
                    ExportWorker.enqueue(context, snapshot)
                }
                .onFailure {
                    exporting.value = false
                    error.value = it.message
                }
        }
        return true
    }

    fun cancelExport() {
        spreadsheetExportJob?.cancel()
        spreadsheetExportJob = null
        WorkManager.getInstance(context).cancelUniqueWork("pdf-export")
        exporting.value = false
    }

    fun markExport(id: String, pages: Int) {
        current.value
            ?.takeIf { it.id == id }
            ?.let { persist(it.copy(exportedAt = System.currentTimeMillis(), exportPages = pages)) }
    }

    fun rename(entry: DocumentEntry, title: String) {
        enqueue {
            val doc = repository.load(entry.id)
            repository.save(
                doc.copy(
                    title = title.trim().ifBlank { "Untitled document" }.take(200),
                    modifiedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    fun duplicate(entry: DocumentEntry) {
        enqueue {
            val doc = repository.load(entry.id)
            val now = System.currentTimeMillis()
            repository.save(
                doc.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    title = "${doc.title} (copy)",
                    createdAt = now,
                    modifiedAt = now,
                    exportedAt = null,
                    exportPages = null,
                )
            )
        }
    }

    fun delete(entry: DocumentEntry) {
        if (current.value?.id == entry.id) current.value = null
        enqueue { repository.delete(entry.id) }
    }

    fun libraryPreferences(sort: String? = null, grid: Boolean? = null) {
        viewModelScope.launch {
            try {
                preferences.updateLibrary(sort, grid)
            } catch (e: java.io.IOException) {
                error.value = e.message
            }
        }
    }

    fun preferences(value: AppPreferences) {
        viewModelScope.launch {
            runCatching { preferences.update(value) }.onFailure { error.value = it.message }
        }
    }

    override fun onCleared() {
        writes.close()
        super.onCleared()
    }
}
