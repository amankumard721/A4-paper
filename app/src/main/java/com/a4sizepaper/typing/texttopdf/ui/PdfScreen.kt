package com.a4sizepaper.typing.texttopdf.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.print.PrintManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.a4sizepaper.typing.texttopdf.pdf.*
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfScreen(
    pdf: ExportedPdf,
    initialAction: String? = null,
    onActionConsumed: () -> Unit = {},
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var page by rememberSaveable(pdf.file.path) { mutableIntStateOf(0) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val save =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/pdf")
        ) { uri ->
            if (uri != null)
                scope.launch {
                    saving = true
                    try {
                        withContext(Dispatchers.IO) {
                            val out =
                                context.contentResolver.openOutputStream(uri, "w")
                                    ?: error("Cannot open destination")
                            out.use { output -> pdf.file.inputStream().use { it.copyTo(output) } }
                        }
                        snackbar.showSnackbar("PDF saved")
                    } catch (e: Exception) {
                        error = e.message ?: "Could not save PDF"
                    } finally {
                        saving = false
                    }
                }
        }
    val sharePdf: () -> Unit = {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", pdf.file)
            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = android.content.ClipData.newRawUri("PDF", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            context.startActivity(Intent.createChooser(intent, "Share PDF"))
        } catch (_: Exception) {
            error = "No application could share this PDF."
        }
    }
    LaunchedEffect(pdf.file, initialAction) {
        val action = initialAction ?: return@LaunchedEffect
        onActionConsumed()
        if (action == "share") sharePdf()
        if (action == "save")
            save.launch(
                pdf.title.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(100).ifBlank {
                    "Document"
                } + ".pdf"
            )
    }
    LaunchedEffect(pdf.file, page) {
        bitmap = null
        try {
            bitmap =
                withContext(Dispatchers.IO) {
                    ParcelFileDescriptor.open(pdf.file, ParcelFileDescriptor.MODE_READ_ONLY).use {
                        fd ->
                        PdfRenderer(fd).use { renderer ->
                            renderer.openPage(page).use { p ->
                                val width = 1500
                                Bitmap.createBitmap(
                                        width,
                                        (width.toFloat() * p.height / p.width).toInt(),
                                        Bitmap.Config.ARGB_8888,
                                    )
                                    .also {
                                        it.eraseColor(AndroidColor.WHITE)
                                        p.render(
                                            it,
                                            null,
                                            null,
                                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                                        )
                                    }
                            }
                        }
                    }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Could not preview PDF"
        }
    }
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(tr("Your PDF is ready", "आपका PDF तैयार है"))
                        Text(
                            "A4 · ${pdf.pages} ${tr("pages","पेज")}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            tr("Back to document", "दस्तावेज़ पर वापस"),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    FluentButton(
                        onClick = {
                            save.launch(
                                pdf.title
                                    .replace(Regex("[^\\p{L}\\p{N} ._-]"), "_")
                                    .take(100)
                                    .ifBlank { "Document" } + ".pdf"
                            )
                        },
                        enabled = !saving,
                    ) {
                        Icon(Icons.Rounded.SaveAlt, null)
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Save as", "सहेजें"))
                    }
                    FluentOutlinedButton(onClick = sharePdf) {
                        Icon(Icons.Rounded.Share, tr("Share", "साझा करें"))
                    }
                    FluentOutlinedButton(
                        onClick = {
                            try {
                                (context.getSystemService(android.content.Context.PRINT_SERVICE)
                                        as PrintManager)
                                    .print(
                                        pdf.title,
                                        ExistingPdfPrintAdapter(pdf),
                                        PdfEngine.attributes(pdf.landscape),
                                    )
                            } catch (e: Exception) {
                                error = "Printing is unavailable on this device."
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.Print, tr("Print", "प्रिंट"))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                tr("Previewing the actual exported file", "निर्यात की गई असली फ़ाइल का प्रीव्यू"),
                style = MaterialTheme.typography.labelMedium,
            )
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                bitmap?.let {
                    Image(
                        it.asImageBitmap(),
                        tr("PDF page ${page+1}", "PDF पेज ${page+1}"),
                        Modifier.fillMaxSize(),
                    )
                } ?: CircularProgressIndicator()
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { page-- }, enabled = page > 0) {
                    Icon(Icons.Rounded.ChevronLeft, tr("Previous page", "पिछला पेज"))
                }
                Text("${page+1} / ${pdf.pages}")
                IconButton(onClick = { page++ }, enabled = page < pdf.pages - 1) {
                    Icon(Icons.Rounded.ChevronRight, tr("Next page", "अगला पेज"))
                }
            }
            if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    error?.let {
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text(tr("PDF action failed", "PDF क्रिया विफल")) },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { error = null }) { Text(tr("OK", "ठीक है")) } },
        )
    }
}
