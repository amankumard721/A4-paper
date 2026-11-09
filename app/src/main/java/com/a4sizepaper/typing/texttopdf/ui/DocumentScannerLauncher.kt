package com.a4sizepaper.typing.texttopdf.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.a4sizepaper.typing.texttopdf.data.ScanSource
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

internal data class DocumentScannerLauncher(
    val busy: Boolean,
    val available: Boolean,
    val start: () -> Unit,
    val cancelPreparation: () -> Unit,
)

/** Register at app scope so an Activity result survives leaving/recreating the scanner screen. */
@Composable
internal fun rememberDocumentScanner(
    blocked: Boolean,
    active: Boolean,
    language: String,
    pageLimit: Int = 50,
    enhancedCleanup: Boolean = true,
    allowGallery: Boolean = true,
    onPdf: (Uri, Int) -> Unit,
    onError: (String) -> Unit,
): DocumentScannerLauncher {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activity = remember(context) { context.scannerActivity() }
    val scope = rememberCoroutineScope()
    var preparing by remember { mutableStateOf(false) }
    var inScanner by rememberSaveable { mutableStateOf(false) }
    var preparationJob by remember { mutableStateOf<Job?>(null) }
    val latestActive by rememberUpdatedState(active)
    val latestBlocked by rememberUpdatedState(blocked)
    LaunchedEffect(active) { if (!active) preparationJob?.cancel() }
    val latestPdf by rememberUpdatedState(onPdf)
    val latestError by rememberUpdatedState(onError)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            result ->
            inScanner = false
            if (result.resultCode == Activity.RESULT_OK && latestActive && !latestBlocked) {
                try {
                    val pdf =
                        requireNotNull(
                            GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pdf
                        ) {
                            "No PDF was returned. Please scan again."
                        }
                    require(pdf.pageCount in 1..ScanSource.MAX_PAGES) {
                        "Scan up to 50 pages at a time."
                    }
                    latestPdf(pdf.uri, pdf.pageCount)
                } catch (e: Exception) {
                    latestError(e.message ?: "Could not read the scan. Please try again.")
                }
            } else if (
                result.resultCode != Activity.RESULT_CANCELED &&
                    result.resultCode != Activity.RESULT_OK &&
                    latestActive
            ) {
                latestError(
                    if (language == "hi") "स्कैन पूरा नहीं हुआ। फिर कोशिश करें।"
                    else "The scan did not finish. Please try again."
                )
            }
        }
    val start: () -> Unit = start@{
        if (!active || blocked || preparing || inScanner) return@start
        val host = activity
        if (host == null || host.isFinishing || host.isDestroyed) {
            latestError(
                if (language == "hi") "स्कैन खोलने के लिए ऐप दोबारा खोलें।"
                else "Reopen the app to start the scanner."
            )
            return@start
        }
        preparing = true
        preparationJob =
            scope.launch {
                try {
                    val options =
                        GmsDocumentScannerOptions.Builder()
                            .setGalleryImportAllowed(allowGallery)
                            .setPageLimit(pageLimit.coerceIn(1, ScanSource.MAX_PAGES))
                            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                            .setScannerMode(if (enhancedCleanup) GmsDocumentScannerOptions.SCANNER_MODE_FULL else GmsDocumentScannerOptions.SCANNER_MODE_BASE_WITH_FILTER)
                            .build()
                    val intent =
                        GmsDocumentScanning.getClient(options)
                            .getStartScanIntent(host)
                            .scannerResult()
                    // Module preparation may finish while another app is on top. Wait for this
                    // Activity.
                    val state =
                        lifecycle.currentStateFlow.first {
                            it == Lifecycle.State.DESTROYED || it.isAtLeast(Lifecycle.State.RESUMED)
                        }
                    ensureActive()
                    if (state == Lifecycle.State.DESTROYED || host.isFinishing || host.isDestroyed)
                        return@launch
                    if (!latestActive || latestBlocked) return@launch
                    inScanner = true
                    launcher.launch(IntentSenderRequest.Builder(intent).build())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    inScanner = false
                    latestError(
                        if (language == "hi")
                            "स्कैनर खुल नहीं सका। Google Play services अपडेट करें और पहली सेटअप के लिए इंटरनेट चालू रखें। इस डिवाइस पर उपलब्ध न हो तो मौजूदा PDF खोल सकते हैं।"
                        else
                            "Scanner could not start. Update Google Play services and connect to the internet for initial setup. If scanning is unavailable on this device, you can still import a PDF."
                    )
                } finally {
                    preparing = false
                }
            }
    }
    return DocumentScannerLauncher(preparing || inScanner, activity != null, start) {
        preparationJob?.cancel()
    }
}

private suspend fun <T> Task<T>.scannerResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

private fun Context.scannerActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val next = current.baseContext
        if (next === current) return null
        current = next
    }
    return current as? Activity
}
