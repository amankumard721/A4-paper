package com.a4lekhak.documentmaker.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.a4lekhak.documentmaker.data.*
import com.a4lekhak.documentmaker.domain.*
import com.a4lekhak.documentmaker.pdf.*
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
    val current = MutableStateFlow<Document?>(null)
    val saving = MutableStateFlow("Ready")
    val error = MutableStateFlow<String?>(null)
    val recovered = MutableStateFlow(false)
    val exporting = MutableStateFlow(false)
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
                    if (path != null && File(path).exists()) {
                        pdf.value =
                            ExportedPdf(
                                File(path),
                                data.getInt("pages", 1),
                                data.getBoolean("landscape", false),
                                data.getString("title") ?: "Document",
                            )
                        val id = data.getString("documentId")
                        if (id != null && completedExport != job.id) {
                            completedExport = job.id
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
