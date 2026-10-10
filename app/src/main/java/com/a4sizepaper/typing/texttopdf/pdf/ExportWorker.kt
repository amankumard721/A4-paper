package com.a4sizepaper.typing.texttopdf.pdf

import android.content.Context
import android.util.AtomicFile
import androidx.work.*
import com.a4sizepaper.typing.texttopdf.domain.Document
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Persisted work retries after process death; the exact document snapshot travels with the job. */
class ExportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        var requestFile: File? = null
        return try {
            val requestId = inputData.getString("request") ?: return Result.failure()
            java.util.UUID.fromString(requestId)
            val file = File(applicationContext.filesDir, "export-requests/$requestId.json")
            requestFile = file
            val document =
                withContext(Dispatchers.IO) {
                    Document.fromJson(
                        JSONObject(AtomicFile(file).readFully().toString(Charsets.UTF_8))
                    )
                }
            setForeground(foregroundInfo())
            val exported = PdfEngine.render(applicationContext, document)
            withContext(Dispatchers.IO) { AtomicFile(file).delete() }
            Result.success(
                workDataOf(
                    "path" to exported.file.absolutePath,
                    "pages" to exported.pages,
                    "landscape" to exported.landscape,
                    "title" to exported.title,
                    "documentId" to document.id,
                )
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            withContext(Dispatchers.IO) { requestFile?.let { AtomicFile(it).delete() } }
            Result.failure(workDataOf("error" to (e.message ?: "PDF export failed").take(500)))
        }
    }

    private fun foregroundInfo(): ForegroundInfo {
        val context = applicationContext
        val channel = "pdf-export"
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(
            android.app.NotificationChannel(
                channel,
                context.getString(com.a4sizepaper.typing.texttopdf.R.string.export_channel),
                android.app.NotificationManager.IMPORTANCE_LOW,
            )
        )
        val open =
            android.app.PendingIntent.getActivity(
                context,
                0,
                android.content.Intent(
                    context,
                    com.a4sizepaper.typing.texttopdf.MainActivity::class.java,
                ),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            androidx.core.app.NotificationCompat.Builder(context, channel)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle(
                    context.getString(com.a4sizepaper.typing.texttopdf.R.string.export_running)
                )
                .setContentText(context.getString(com.a4sizepaper.typing.texttopdf.R.string.export_wait))
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(0, 0, true)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    context.getString(com.a4sizepaper.typing.texttopdf.R.string.export_cancel),
                    WorkManager.getInstance(context).createCancelPendingIntent(id),
                )
                .build()
        return if (android.os.Build.VERSION.SDK_INT >= 34)
            ForegroundInfo(
                4101,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE,
            )
        else ForegroundInfo(4101, notification)
    }

    companion object {
        suspend fun enqueue(context: Context, document: Document): java.util.UUID =
            withContext(Dispatchers.IO) {
                val id = java.util.UUID.randomUUID()
                val directory = File(context.filesDir, "export-requests").apply { mkdirs() }
                val now = System.currentTimeMillis()
                ExportRetention.clean(File(context.filesDir, "exports"))
                // Keep active/restartable requests; clean only old finished jobs and partial PDFs.
                directory
                    .listFiles()
                    ?.filter {
                        it.extension == "json" && now - it.lastModified() > 7L * 24 * 60 * 60 * 1000
                    }
                    ?.forEach { old ->
                        val oldId =
                            runCatching { java.util.UUID.fromString(old.nameWithoutExtension) }
                                .getOrNull()
                        if (oldId != null) {
                            val info = WorkManager.getInstance(context).getWorkInfoById(oldId).get()
                            if (info == null || info.state.isFinished) AtomicFile(old).delete()
                        }
                    }
                File(context.filesDir, "exports")
                    .listFiles()
                    ?.filter {
                        it.extension == "partial" && now - it.lastModified() > 24L * 60 * 60 * 1000
                    }
                    ?.forEach { it.delete() }
                val atomic = AtomicFile(File(directory, "$id.json"))
                val stream = atomic.startWrite()
                try {
                    stream.write(document.toJson().toString().toByteArray(Charsets.UTF_8))
                    atomic.finishWrite(stream)
                } catch (e: Exception) {
                    atomic.failWrite(stream)
                    throw e
                }
                val request =
                    OneTimeWorkRequestBuilder<ExportWorker>()
                        .setId(id)
                        .setInputData(workDataOf("request" to id.toString()))
                        .addTag("pdf-export")
                        .addTag("export-time:${System.currentTimeMillis()}")
                        .build()
                WorkManager.getInstance(context)
                    .enqueueUniqueWork("pdf-export", ExistingWorkPolicy.REPLACE, request)
                    .result
                    .get()
                id
            }
    }
}
