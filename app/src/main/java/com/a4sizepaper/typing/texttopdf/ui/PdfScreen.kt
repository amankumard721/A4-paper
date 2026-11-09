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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.a4sizepaper.typing.texttopdf.pdf.*
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfScreen(
    pdf: ExportedPdf,
    initialAction: String? = null,
    externalReader: Boolean = false,
    onActionConsumed: () -> Unit = {},
    onBack: () -> Unit,
    onSavedExit: ((() -> Unit) -> Unit)? = null,
    onEditText: (() -> Unit)? = null,
    onManagePages: (() -> Unit)? = null,
    onAddPages: (() -> Unit)? = null,
    addingPages: Boolean = false,
) {
    val context = LocalContext.current
    var marking by remember(pdf.file.path) { mutableStateOf(false) }
    var marker by remember { mutableStateOf(false) }
    var eraser by remember { mutableStateOf(false) }
    var marks by remember(pdf.file.path) { mutableStateOf(emptyList<PdfMark>()) }
    var redoMarks by remember(pdf.file.path) { mutableStateOf(emptyList<PdfMark>()) }
    var draft by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var showMarks by remember { mutableStateOf(true) }
    var savedMarksSnapshot by remember(pdf.file.path) { mutableStateOf(emptyList<PdfMark>()) }
    var savedSinceOpen by rememberSaveable(pdf.file.path) { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var focusedReader by rememberSaveable(pdf.file.path) { mutableStateOf(false) }
    val continuous = !marking && !focusedReader
    val exitReader: () -> Unit = {
        when {
            marking && marks != savedMarksSnapshot -> confirmExit = true
            marking -> marking = false
            focusedReader -> focusedReader = false
            marks != savedMarksSnapshot -> confirmExit = true
            savedSinceOpen && onSavedExit != null -> onSavedExit(onBack)
            else -> onBack()
        }
    }

    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val renderMutex = remember { Mutex() }
    val readerList = key(pdf.file.path) { rememberLazyListState() }
    var textPage by remember(pdf.file.path) { mutableStateOf<Int?>(null) }
    var searchOpen by rememberSaveable(pdf.file.path) { mutableStateOf(false) }
    var query by rememberSaveable(pdf.file.path) { mutableStateOf("") }
    var hits by remember(pdf.file.path) { mutableStateOf(emptyList<PdfSearchHit>()) }
    var hitIndex by remember(pdf.file.path) { mutableIntStateOf(0) }
    var searching by remember(pdf.file.path) { mutableStateOf(false) }
    var searchError by remember(pdf.file.path) { mutableStateOf(false) }
    val searchSession = remember(pdf.file.path) { com.a4sizepaper.typing.texttopdf.pdf.PdfTextSession(context.applicationContext, pdf.file) }
    LaunchedEffect(searchSession) {
        try { kotlinx.coroutines.awaitCancellation() }
        finally { withContext(kotlinx.coroutines.NonCancellable) { searchSession.close() } }
    }


    var page by rememberSaveable(pdf.file.path) { mutableIntStateOf(0) }
    var pageCount by remember(pdf.file.path) { mutableIntStateOf(pdf.pages.coerceAtLeast(1)) }
    var bitmap by remember(pdf.file.path) { mutableStateOf<Bitmap?>(null) }
    var previewError by remember(pdf.file.path) { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var loading by remember(pdf.file.path) { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var goToOpen by remember { mutableStateOf(false) }
    var pageInput by remember { mutableStateOf("") }
    var zoom by remember(pdf.file.path, page) { mutableFloatStateOf(1f) }
    var pan by remember(pdf.file.path, page) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val safeTitle =
        remember(pdf.title) {
            pdf.title.trim().replace(Regex("[/\\\\:*?\"<>|\\x00-\\x1f]"), "_").take(100).ifBlank {
                "Document"
            }
        }
    val save =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/pdf")
        ) { uri ->
            if (uri != null) {
                val savedMarks = marks.toList()
                scope.launch {
                    saving = true
                    try {
                        withContext(Dispatchers.IO) {
                            val out =
                                context.contentResolver.openOutputStream(uri, "w")
                                    ?: error("Cannot open destination")
                            out.use { output ->
                                if (savedMarks.isEmpty()) pdf.file.inputStream().use { it.copyTo(output) }
                                else writeMarkedPdf(context, pdf.file, savedMarks, output)
                            }
                        }
                        savedMarksSnapshot = savedMarks
                        savedSinceOpen = true
                        snackbar.showSnackbar("PDF saved")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        actionError = e.message ?: "Could not save PDF"
                    } finally {
                        saving = false
                    }
                }
            }
        }
    val sharePdf: () -> Unit = {
        if (!sharing)
            scope.launch {
                sharing = true
                try {
                    val shareFile =
                        withContext(Dispatchers.IO) {
                            val shareDir =
                                File(File(context.filesDir, "exports"), "shared").apply { mkdirs() }
                            val now = System.currentTimeMillis()
                            shareDir
                                .listFiles()
                                ?.filter { now - it.lastModified() > 24L * 60 * 60 * 1000 }
                                ?.forEach {
                                    if (it.isDirectory) it.deleteRecursively() else it.delete()
                                }
                            val session =
                                File(shareDir, java.util.UUID.randomUUID().toString()).apply {
                                    check(mkdirs())
                                }
                            File(session, safeTitle + ".pdf").also {
                                if (marks.isEmpty()) pdf.file.copyTo(it, overwrite = true)
                                else it.outputStream().use { out -> writeMarkedPdf(context, pdf.file, marks.toList(), out) }
                            }
                        }
                    val uri =
                        FileProvider.getUriForFile(
                            context,
                            context.packageName + ".files",
                            shareFile,
                        )
                    val intent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, safeTitle)
                            putExtra(Intent.EXTRA_TITLE, safeTitle + ".pdf")
                            clipData = android.content.ClipData.newRawUri(safeTitle, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    context.startActivity(Intent.createChooser(intent, "Share PDF"))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    actionError = "No application could share this PDF."
                } finally {
                    sharing = false
                }
            }
    }
    LaunchedEffect(pdf.file.path, initialAction) {
        val action = initialAction ?: return@LaunchedEffect
        onActionConsumed()
        if (action == "sign") marking = true
        if (action == "share") sharePdf()
        if (action == "print") {
            runCatching {
                val printer = context.getSystemService(android.content.Context.PRINT_SERVICE) as PrintManager
                printer.print(pdf.title, ExistingPdfPrintAdapter(pdf), PdfEngine.attributes(pdf.landscape))
            }.onFailure { actionError = "Printing is unavailable on this device." }
        }
        if (action == "save") save.launch(safeTitle + ".pdf")
    }
    LaunchedEffect(pdf.file.path, page, retry, continuous) {
        if (continuous) return@LaunchedEffect
        bitmap = null
        loading = true
        previewError = null
        var unpublished: Bitmap? = null
        try {
            val result =
                withContext(Dispatchers.IO) {
                    // Cancellation cannot interrupt native rendering. Serialize renders and close
                    // all
                    // handles before publishing a result, including during rapid page changes.
                    renderMutex.withLock {
                        ensureActive()
                        ParcelFileDescriptor.open(pdf.file, ParcelFileDescriptor.MODE_READ_ONLY)
                            .use { fd ->
                                PdfRenderer(fd).use { renderer ->
                                    check(renderer.pageCount > 0) { "This PDF has no pages." }
                                    val index = page.coerceIn(0, renderer.pageCount - 1)
                                    renderer.openPage(index).use { source ->
                                        check(source.width > 0 && source.height > 0) {
                                            "Invalid PDF page size."
                                        }
                                        val scale = min(1600f / source.width, 1600f / source.height)
                                        val width =
                                            (source.width * scale).roundToInt().coerceIn(1, 1600)
                                        val height =
                                            (source.height * scale).roundToInt().coerceIn(1, 1600)
                                        val rendered =
                                            Bitmap.createBitmap(
                                                width,
                                                height,
                                                Bitmap.Config.ARGB_8888,
                                            )
                                        unpublished = rendered
                                        rendered.eraseColor(AndroidColor.WHITE)
                                        source.render(
                                            rendered,
                                            null,
                                            null,
                                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                                        )
                                        ensureActive()
                                        Triple(rendered, renderer.pageCount, index)
                                    }
                                }
                            }
                    }
                }
            ensureActive()
            pageCount = result.second
            page = result.third
            bitmap = result.first
            unpublished = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            previewError = e.message ?: "Could not preview this PDF."
        } finally {
            // Published bitmaps use GC; recycling them could race Compose drawing.
            unpublished?.recycle()
            if (isActive) loading = false
        }
    }
    fun boundedPan(value: Offset, scale: Float): Offset {
        val current = bitmap ?: return Offset.Zero
        if (viewport.width == 0 || viewport.height == 0) return Offset.Zero
        val fit =
            min(
                viewport.width.toFloat() / current.width,
                viewport.height.toFloat() / current.height,
            )
        val maxX = ((current.width * fit * scale - viewport.width) / 2f).coerceAtLeast(0f)
        val maxY = ((current.height * fit * scale - viewport.height) / 2f).coerceAtLeast(0f)
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }
    fun setZoom(value: Float) {
        zoom = value.coerceIn(1f, 5f)
        pan = boundedPan(pan, zoom)
    }
    val zoomInLabel = tr("Zoom in", "ज़ूम बढ़ाएँ")
    val zoomOutLabel = tr("Zoom out", "ज़ूम घटाएँ")
    val resetZoomLabel = tr("Fit page", "पूरा पेज दिखाएँ")
    val goToLabel = tr("Go to page", "पेज पर जाएँ")
    val activeHit = hits.getOrNull(hitIndex)
    LaunchedEffect(activeHit?.page) {
        activeHit?.let { readerList.scrollToItem(it.page) }
    }
    LaunchedEffect(searchOpen, query, searchSession, pageCount) {
        hits = emptyList()
        hitIndex = 0
        searchError = false
        searching = searchOpen && query.isNotBlank()
        if (!searching) return@LaunchedEffect
        kotlinx.coroutines.delay(250)
        val pattern = Regex(query.trim().split(Regex("\\s+")).joinToString("\\s+") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
        val found = mutableListOf<PdfSearchHit>()
        try {
            repeat(pageCount) { index ->
                val matches = withContext(Dispatchers.Default) {
                    val text = searchSession.page(index)
                    pattern.findAll(text.text).map { PdfSearchHit(index, it.range) }.toList()
                }
                found.addAll(matches)
                if (hits.isEmpty() && found.isNotEmpty()) hits = found.toList()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            searchError = true
        }
        hits = found
        searching = false
    }
    BackHandler(onBack = exitReader)
    BackHandler(enabled = searchOpen) { searchOpen = false; query = "" }
    Scaffold(
        topBar = {
            if (searchOpen) PdfInlineSearchBar(
                query = query,
                onQuery = { query = it.take(128) },
                count = hits.size,
                index = hitIndex,
                searching = searching,
                failed = searchError,
                onClose = { searchOpen = false; query = "" },
                onPrevious = { if (hits.isNotEmpty()) hitIndex = (hitIndex + hits.size - 1) % hits.size },
                onNext = { if (hits.isNotEmpty()) hitIndex = (hitIndex + 1) % hits.size },
            ) else TopAppBar(
                title = {
                    Column {
                        Text(
                            if (externalReader) safeTitle.removeSuffix(".pdf") + ".pdf" else pdf.title.ifBlank { "Document" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (!externalReader) Text(
                            pageCount.toString() + " " + tr("pages", "पेज") + " · PDF",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(exitReader) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back", "वापस"))
                    }
                },
                actions = {
                    if (marking) {
                        Button(onClick = { save.launch(safeTitle.removeSuffix(".pdf") + "-marked.pdf") }, enabled = !saving && marks.isNotEmpty()) {
                            Text(tr("Save copy", "कॉपी सहेजें"))
                        }
                    } else {
                        run {
                            IconButton(onClick = { focusedReader = false; searchOpen = true }) {
                                Icon(Icons.Rounded.Search, tr("Search PDF", "PDF में खोजें"))
                            }
                        }
                        Box {
                        IconButton(onClick = { moreOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, tr("PDF options", "PDF विकल्प"))
                        }
                        DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                            if (externalReader) {
                                DropdownMenuItem(text = { Text(tr("Save a copy", "कॉपी सहेजें")) }, onClick = {
                                    moreOpen = false
                                    save.launch(safeTitle.removeSuffix(".pdf") + ".pdf")
                                })
                                DropdownMenuItem(text = { Text(tr("Share", "साझा करें")) }, onClick = {
                                    moreOpen = false
                                    sharePdf()
                                })
                            }
                            if (externalReader) DropdownMenuItem(
                                text = { Text(tr("Print", "प्रिंट")) },
                                leadingIcon = { Icon(Icons.Rounded.Print, null) },
                                enabled = !saving,
                                onClick = {
                                    moreOpen = false
                                    val printMarks = marks.toList()
                                    scope.launch {
                                        saving = true
                                        try {
                                            val printPdf = withContext(Dispatchers.IO) {
                                                if (printMarks.isEmpty()) pdf else {
                                                    val directory = File(context.cacheDir, "marked-print").apply { mkdirs() }
                                                    directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
                                                    val file = File.createTempFile("print-", ".pdf", directory)
                                                    try { file.outputStream().use { writeMarkedPdf(context, pdf.file, printMarks, it) } }
                                                    catch (e: Exception) { file.delete(); throw e }
                                                    pdf.copy(file = file)
                                                }
                                            }
                                            val printer = context.getSystemService(android.content.Context.PRINT_SERVICE) as PrintManager
                                            printer.print(pdf.title, ExistingPdfPrintAdapter(printPdf), null)
                                        } catch (e: CancellationException) { throw e }
                                        catch (_: Exception) { actionError = "Could not prepare this PDF for printing." }
                                        finally { saving = false }
                                    }
                                },
                            )
                            DropdownMenuItem(text = { Text(tr("Select / copy text", "टेक्स्ट चुनें / कॉपी करें")) }, onClick = {
                                moreOpen = false
                                textPage = if (continuous) readerList.firstVisibleItemIndex else page
                            })
                            if (onEditText != null)
                                DropdownMenuItem(
                                    text = {
                                        Text(tr("Edit text copy", "टेक्स्ट की कॉपी संपादित करें"))
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                    onClick = {
                                        moreOpen = false
                                        onEditText()
                                    },
                                )
                            if (onManagePages != null)
                                DropdownMenuItem(
                                    text = { Text(tr("Manage pages", "पेज व्यवस्थित करें")) },
                                    leadingIcon = { Icon(Icons.Rounded.Dashboard, null) },
                                    onClick = {
                                        moreOpen = false
                                        onManagePages()
                                    },
                                )
                            DropdownMenuItem(
                                text = { Text(goToLabel) },
                                leadingIcon = { Icon(Icons.Rounded.FindInPage, null) },
                                onClick = {
                                    moreOpen = false
                                    pageInput = ((if (continuous) readerList.firstVisibleItemIndex else page) + 1).toString()
                                    goToOpen = true
                                },
                            )
                            if (externalReader) DropdownMenuItem(
                                text = { Text(if (continuous) tr("Zoom current page", "इस पेज को ज़ूम करें") else tr("Continuous scroll", "लगातार स्क्रॉल")) },
                                leadingIcon = { Icon(Icons.Rounded.ZoomIn, null) },
                                onClick = {
                                    moreOpen = false
                                    if (continuous) page = readerList.firstVisibleItemIndex.coerceIn(0, pageCount - 1)
                                    focusedReader = !focusedReader
                                },
                            )
                            if (!continuous) DropdownMenuItem(
                                text = { Text(resetZoomLabel) },
                                leadingIcon = { Icon(Icons.Rounded.FitScreen, null) },
                                onClick = {
                                    moreOpen = false
                                    zoom = 1f
                                    pan = Offset.Zero
                                },
                            )
                        }
                    }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (externalReader && !marking) {
                FloatingActionButton(
                    onClick = {
                        if (continuous) page = readerList.firstVisibleItemIndex.coerceIn(0, pageCount - 1)
                        zoom = 1f
                        pan = Offset.Zero
                        showMarks = true
                        eraser = false
                        marking = true
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Rounded.Edit, tr("Annotate PDF", "PDF पर लिखें"))
                }
            }
        },
        bottomBar = {
            if (marking) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shadowElevation = 8.dp,
                        tonalElevation = 4.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            IconButton(onClick = { eraser = false; marker = false }) {
                                Icon(
                                    Icons.Rounded.Edit,
                                    tr("Pen", "पेन"),
                                    tint = if (!eraser && !marker) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { eraser = false; marker = true }) {
                                Icon(
                                    Icons.Rounded.Highlight,
                                    tr("Highlighter", "हाइलाइटर"),
                                    tint = if (!eraser && marker) androidx.compose.ui.graphics.Color(0xFFEAB308) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { eraser = true }) {
                                Icon(
                                    Icons.Rounded.AutoFixNormal,
                                    tr("Eraser", "इरेज़र"),
                                    tint = if (eraser) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))
                            IconButton(
                                enabled = marks.isNotEmpty(),
                                onClick = {
                                    redoMarks = redoMarks + marks.last()
                                    marks = marks.dropLast(1)
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Undo, tr("Undo", "पहले जैसा"))
                            }
                            IconButton(
                                enabled = redoMarks.isNotEmpty(),
                                onClick = {
                                    marks = marks + redoMarks.last()
                                    redoMarks = redoMarks.dropLast(1)
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Redo, tr("Redo", "फिर करें"))
                            }
                            IconButton(onClick = { showMarks = !showMarks }) {
                                Icon(
                                    if (showMarks) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                    tr("Show markings", "निशान दिखाएँ")
                                )
                            }
                        }
                    }
                }
            } else if (!externalReader) Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Column {
                    if (onAddPages != null) TextButton(
                        onClick = onAddPages,
                        enabled = !addingPages && !saving && !sharing,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(tr("+ Add pages", "+ पेज जोड़ें")) }
                    Row(
                        Modifier.fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FluentButton(
                            onClick = { save.launch(safeTitle + ".pdf") },
                            enabled = !saving,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.SaveAlt, null)
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Save as", "सहेजें"), maxLines = 1)
                        }
                        OutlinedIconButton(
                            onClick = sharePdf,
                            enabled = !sharing,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Rounded.Share, tr("Share", "साझा करें"))
                        }
                        OutlinedIconButton(
                            modifier = Modifier.size(48.dp),
                            onClick = {
                                try {
                                    (context.getSystemService(android.content.Context.PRINT_SERVICE)
                                            as PrintManager)
                                        .print(
                                            pdf.title,
                                            ExistingPdfPrintAdapter(pdf),
                                            PdfEngine.attributes(pdf.landscape),
                                        )
                                } catch (_: Exception) {
                                    actionError = "Printing is unavailable on this device."
                                }
                            },
                        ) {
                            Icon(Icons.Rounded.Print, tr("Print", "प्रिंट"))
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (continuous) {
            PdfContinuousPages(
                file = pdf.file,
                pageCount = pageCount,
                textSession = searchSession,
                searchHits = hits,
                activeHit = activeHit,
                state = readerList,
                marks = if (showMarks) marks else emptyList(),
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            if (saving || sharing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(padding))
        } else Column(
            Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(8.dp)
                    .clipToBounds()
                    .onSizeChanged {
                        viewport = it
                        pan = boundedPan(pan, zoom)
                    },
                contentAlignment = Alignment.Center,
            ) {
                bitmap?.let { current ->
                    val zoomDescription = (zoom * 100).roundToInt().toString() + "%"
                    Image(
                        current.asImageBitmap(),
                        tr("PDF page " + (page + 1), "PDF पेज " + (page + 1)),
                        Modifier.fillMaxSize()
                            .semantics {
                                stateDescription = zoomDescription
                                customActions =
                                    listOf(
                                        CustomAccessibilityAction(zoomInLabel) {
                                            setZoom(zoom + 0.5f)
                                            true
                                        },
                                        CustomAccessibilityAction(zoomOutLabel) {
                                            setZoom(zoom - 0.5f)
                                            true
                                        },
                                        CustomAccessibilityAction(resetZoomLabel) {
                                            zoom = 1f
                                            pan = Offset.Zero
                                            true
                                        },
                                    )
                            }
                            .pointerInput(pdf.file.path, page, current, marking) {
                                if (marking) return@pointerInput
                                detectTapGestures(
                                    onDoubleTap = { point ->
                                        val next = if (zoom > 1f) 1f else 2.5f
                                        val center = Offset(size.width / 2f, size.height / 2f)
                                        pan =
                                            boundedPan(
                                                (point - center) * (1f - next / zoom) +
                                                    pan * (next / zoom),
                                                next,
                                            )
                                        zoom = next
                                    }
                                )
                            }
                            .pointerInput(pdf.file.path, page, current, marking) {
                                if (marking) return@pointerInput
                                detectTransformGestures { centroid, change, scale, _ ->
                                    val next = (zoom * scale).coerceIn(1f, 5f)
                                    val ratio = next / zoom
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    pan =
                                        boundedPan(
                                            pan * ratio +
                                                (centroid - center) * (1f - ratio) +
                                                change,
                                            next,
                                        )
                                    zoom = next
                                }
                            }
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = pan.x
                                translationY = pan.y
                            },
                        contentScale = ContentScale.Fit,
                    )
                    if (marking || (showMarks && marks.any { it.page == page })) {
                        val overlay = if (!marking) Modifier.graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = pan.x
                            translationY = pan.y
                        } else Modifier.pointerInput(page, current, marker, eraser, showMarks) {
                            if (!showMarks) return@pointerInput
                            val fit = min(size.width.toFloat() / current.width, size.height.toFloat() / current.height)
                            val width = current.width * fit
                            val height = current.height * fit
                            val origin = Offset((size.width - width) / 2f, (size.height - height) / 2f)
                            fun normalize(point: Offset) = Offset(
                                ((point.x - origin.x) / width).coerceIn(0f, 1f),
                                ((point.y - origin.y) / height).coerceIn(0f, 1f),
                            )
                            fun eraseAt(point: Offset) {
                                val norm = normalize(point)
                                val filtered = marks.filterNot { m ->
                                    m.page == page && m.points.any { p ->
                                        val dx = p.x - norm.x
                                        val dy = p.y - norm.y
                                        (dx * dx + dy * dy) < 0.0012f
                                    }
                                }
                                if (filtered.size != marks.size) {
                                    marks = filtered
                                    redoMarks = emptyList()
                                }
                            }
                            detectDragGestures(
                                onDragStart = { point ->
                                    if (eraser) {
                                        eraseAt(point)
                                    } else {
                                        draft = if (point.x in origin.x..(origin.x + width) &&
                                            point.y in origin.y..(origin.y + height)) listOf(normalize(point)) else emptyList()
                                    }
                                },
                                onDragCancel = { draft = emptyList() },
                                onDragEnd = {
                                    if (!eraser && draft.size > 1 && marks.size < 500) {
                                        marks = marks + PdfMark(page, marker, draft)
                                        redoMarks = emptyList()
                                    }
                                    draft = emptyList()
                                },
                            ) { change, _ ->
                                change.consume()
                                if (eraser) {
                                    eraseAt(change.position)
                                } else {
                                    if (draft.isNotEmpty() && draft.size < 1500) draft = draft + normalize(change.position)
                                }
                            }
                        }
                        Canvas(Modifier.fillMaxSize().then(overlay)) {
                            if (!showMarks) return@Canvas
                            val fit = min(size.width / current.width, size.height / current.height)
                            val width = current.width * fit
                            val height = current.height * fit
                            val origin = Offset((size.width - width) / 2f, (size.height - height) / 2f)
                            fun drawMark(mark: PdfMark) {
                                val color = if (mark.highlight) androidx.compose.ui.graphics.Color(0x60FFC107) else androidx.compose.ui.graphics.Color(0xFFEF4444)
                                val path = androidx.compose.ui.graphics.Path()
                                mark.points.forEachIndexed { index, point ->
                                    val x = origin.x + point.x * width
                                    val y = origin.y + point.y * height
                                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = width * if (mark.highlight) 0.017f else 0.004f,
                                    cap = StrokeCap.Round,
                                ))
                            }
                            marks.filter { it.page == page }.forEach(::drawMark)
                            if (draft.isNotEmpty()) drawMark(PdfMark(page, marker, draft))
                        }
                    }
                    if (!marking && zoom > 1.01f)
                        FilledTonalButton(
                            onClick = {
                                zoom = 1f
                                pan = Offset.Zero
                            },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                        ) {
                            Icon(Icons.Rounded.FitScreen, resetZoomLabel)
                            Spacer(Modifier.width(6.dp))
                            Text(zoomDescription)
                        }
                }
                if (loading)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(tr("Loading page…", "पेज खुल रहा है…"))
                    }
                previewError?.let { message ->
                    Column(
                        Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(message)
                        TextButton(onClick = { retry++ }) {
                            Text(tr("Try again", "फिर कोशिश करें"))
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(onClick = { page-- }, enabled = page > 0) {
                    Icon(Icons.Rounded.ChevronLeft, tr("Previous page", "पिछला पेज"))
                }
                TextButton(
                    onClick = {
                        pageInput = (page + 1).toString()
                        goToOpen = true
                    },
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                (page + 1).toString() + " / " + pageCount + ". " + goToLabel
                        },
                ) {
                    Text((page + 1).toString() + " / " + pageCount)
                    Icon(Icons.Rounded.ArrowDropDown, null)
                }
                IconButton(onClick = { page++ }, enabled = page < pageCount - 1) {
                    Icon(Icons.Rounded.ChevronRight, tr("Next page", "अगला पेज"))
                }
            }
            if (saving || sharing) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    if (confirmExit) AlertDialog(
        onDismissRequest = { confirmExit = false },
        title = { Text(tr("Save changes?", "बदलाव सहेजें?")) },
        text = { Text(tr("Save a copy before leaving to keep your changes.", "बदलाव रखने के लिए जाने से पहले कॉपी सहेजें।")) },
        confirmButton = { TextButton(onClick = {
            confirmExit = false
            save.launch(safeTitle.removeSuffix(".pdf") + "-marked.pdf")
        }) { Text(tr("Save copy", "कॉपी सहेजें")) } },
        dismissButton = { TextButton(onClick = {
            confirmExit = false
            marks = savedMarksSnapshot
            if (marking) marking = false else onBack()
        }) {
            Text(tr("Discard", "छोड़ें"))
        } },
    )
    textPage?.let { PdfTextPanel(pdf.file, it) { textPage = null } }
    if (goToOpen) {
        val destination = pageInput.toIntOrNull()?.takeIf { it in 1..pageCount }
        fun go() {
            if (destination != null) {
                if (continuous) scope.launch { readerList.scrollToItem(destination - 1) }
                else page = destination - 1
                goToOpen = false
            }
        }
        AlertDialog(
            onDismissRequest = { goToOpen = false },
            title = { Text(goToLabel) },
            text = {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { pageInput = it.filter(Char::isDigit).take(7) },
                    label = { Text(tr("Page number", "पेज नंबर")) },
                    supportingText = { Text("1 – " + pageCount) },
                    isError = pageInput.isNotEmpty() && destination == null,
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Go,
                        ),
                    keyboardActions = KeyboardActions(onGo = { go() }),
                )
            },
            confirmButton = {
                TextButton(onClick = { go() }, enabled = destination != null) {
                    Text(tr("Go", "जाएँ"))
                }
            },
            dismissButton = {
                TextButton(onClick = { goToOpen = false }) { Text(tr("Cancel", "रद्द करें")) }
            },
        )
    }
    actionError?.let { message ->
        AlertDialog(
            onDismissRequest = { actionError = null },
            title = { Text(tr("PDF action failed", "PDF क्रिया विफल")) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { actionError = null }) { Text(tr("OK", "ठीक है")) }
            },
        )
    }
}
