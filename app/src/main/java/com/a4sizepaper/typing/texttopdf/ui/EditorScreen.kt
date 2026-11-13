package com.a4sizepaper.typing.texttopdf.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a4sizepaper.typing.texttopdf.data.AppPreferences
import com.a4sizepaper.typing.texttopdf.domain.*
import com.a4sizepaper.typing.texttopdf.editor.EditorHost
import kotlinx.coroutines.delay
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    model: MainViewModel,
    document: Document,
    prefs: AppPreferences,
    onBack: () -> Unit,
    onSetup: () -> Unit,
    onExport: (String?) -> Unit,
    onDelete: () -> Unit,
    review: Boolean,
    importedDocument: Boolean,
    onReview: (Boolean) -> Unit,
) {
    A4Theme(
        darkTheme = AppTheme.colors == DarkColors
    ) {
        EditorWorkspace(
            model,
            document,
            prefs,
            onBack,
            onSetup,
            onExport,
            onDelete,
            review,
            importedDocument,
            onReview,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditorWorkspace(
    model: MainViewModel,
    document: Document,
    prefs: AppPreferences,
    onBack: () -> Unit,
    onSetup: () -> Unit,
    onExport: (String?) -> Unit,
    onDelete: () -> Unit,
    review: Boolean,
    importedDocument: Boolean,
    onReview: (Boolean) -> Unit,
) {
    val colors = AppTheme.colors
    var exportSheet by remember { mutableStateOf(false) }
    var preparingExport by remember { mutableStateOf(false) }
    var exportAction by rememberSaveable { mutableStateOf<String?>(null) }
    val exporting by model.exporting.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val dark = AppTheme.colors == DarkColors
    val saved by model.saving.collectAsStateWithLifecycle()

    var host by remember(document.id) { mutableStateOf<EditorHost?>(null) }
    var ready by remember(document.id) { mutableStateOf(false) }

    // Live Formatting & History State
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    var bold by remember { mutableStateOf(false) }
    var italic by remember { mutableStateOf(false) }
    var underline by remember { mutableStateOf(false) }
    var strike by remember { mutableStateOf(false) }
    var textColor by remember(document.id) { mutableStateOf("#111827") }
    var highlightColor by remember(document.id) { mutableStateOf("") }
    var findOpen by remember(document.id) { mutableStateOf(false) }
    var findQuery by rememberSaveable(document.id) { mutableStateOf("") }
    var replaceText by rememberSaveable(document.id) { mutableStateOf("") }
    var matchCase by rememberSaveable(document.id) { mutableStateOf(false) }
    var wholeWord by rememberSaveable(document.id) { mutableStateOf(false) }
    var replaceMode by rememberSaveable(document.id) { mutableStateOf(false) }
    var matchTotal by remember { mutableIntStateOf(0) }
    var matchIndex by remember { mutableIntStateOf(0) }
    var matchLimited by remember { mutableStateOf(false) }
    var mediaOpen by remember { mutableStateOf(false) }
    val closeFind: () -> Unit = {
        findOpen = false
        host?.command("findClose")
    }
    LaunchedEffect(findOpen, findQuery, matchCase, wholeWord, ready) {
        if (findOpen && ready) {
            delay(160)
            host?.command(
                "find",
                JSONObject()
                    .put("query", findQuery)
                    .put("matchCase", matchCase)
                    .put("wholeWord", wholeWord)
                    .toString(),
            )
        }
    }
    var alignment by remember { mutableStateOf("left") }
    var heading by remember { mutableIntStateOf(0) }
    var bulletList by remember { mutableStateOf(false) }
    var orderedList by remember { mutableStateOf(false) }
    var fontSize by remember { mutableIntStateOf(12) }
    var fontFamily by remember { mutableStateOf("Noto Sans") }
    var lineHeight by remember { mutableDoubleStateOf(1.15) }
    var paragraphSpacing by remember { mutableIntStateOf(0) }

    // Live Document Metrics
    var wordCount by remember { mutableIntStateOf(0) }
    var charCount by remember { mutableIntStateOf(0) }
    var paragraphCount by remember { mutableIntStateOf(0) }

    // Modal UI States
    var ribbonOpen by remember { mutableStateOf(false) }
    var ribbonTab by remember { mutableIntStateOf(0) }
    var colorPicker by remember { mutableStateOf(false) }
    var statsOpen by remember { mutableStateOf(false) }
    var mediaState by remember { mutableStateOf(MediaSelection()) }
    var signature by rememberSaveable { mutableStateOf(false) }
    var insertMenu by remember { mutableStateOf(false) }
    var overflowMenu by remember { mutableStateOf(false) }
    var alignMenu by remember { mutableStateOf(false) }
    var headingMenu by remember { mutableStateOf(false) }

    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            onExport(exportAction)
            exportAction = null
        }

    val exportWithNotification: () -> Unit = {
        if (
            android.os.Build.VERSION.SDK_INT >= 33 &&
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onExport(exportAction)
            exportAction = null
        }
    }

    val requestExport: (String?) -> Unit = { action ->
        if (ready && !exporting && !preparingExport) {
            preparingExport = true
            host?.snapshot { content ->
                preparingExport = false
                if (content != null) {
                    model.edit(content, document.id)
                    model.save()
                    exportSheet = false
                    exportAction = action
                    exportWithNotification()
                }
            }
        }
    }

    val pickImage =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null)
                model.importImage(uri, document.id, signature) { value ->
                    host?.command("image", value)
                }
        }

    val finish: () -> Unit = {
        host?.snapshot {
            if (it != null) model.edit(it, document.id)
            onBack()
        } ?: onBack()
    }

    val showReview: () -> Unit = {
        closeFind()
        host?.snapshot { content ->
            if (content != null) {
                model.edit(content, document.id)
                model.save()
                onReview(true)
            }
        }
    }

    BackHandler { if (findOpen) closeFind() else if (review) finish() else showReview() }
    LaunchedEffect(review, ready) { if (ready) host?.setReview(review) }

    DisposableEffect(lifecycle, host) {
        val screenHost = host
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START,
                Lifecycle.Event.ON_RESUME -> screenHost?.onResume()
                Lifecycle.Event.ON_STOP -> {
                    screenHost?.snapshot { if (it != null) model.edit(it, document.id) }
                    screenHost?.onPause()
                }
                else -> Unit
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        if (lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
            screenHost?.onResume()
        else screenHost?.onPause()
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(document.page, ready) {
        if (ready) host?.setPageSettings(document.page.toJson())
    }

    LaunchedEffect(dark, ready) { if (ready) host?.setDark(dark) }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = colors.bg,
        floatingActionButton = {
            if (importedDocument && review && ready && !exporting) {
                FloatingActionButton(
                    onClick = { closeFind(); onReview(false) },
                    containerColor = colors.accentSoft,
                    contentColor = colors.accentText,
                ) {
                    Icon(Icons.Rounded.Edit, tr("Edit document", "दस्तावेज़ संपादित करें"))
                }
            }
        },
        bottomBar = {
            if (importedDocument && !review && !findOpen) {
                Surface(color = colors.cardFooter) {
                    Column(Modifier.navigationBarsPadding()) {
                        HorizontalDivider(color = colors.line)
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FormatToggleButton(Icons.Rounded.FormatBold, bold, ready, tr("Bold", "बोल्ड")) { host?.command("bold") }
                            FormatToggleButton(Icons.Rounded.FormatItalic, italic, ready, tr("Italic", "इटैलिक")) { host?.command("italic") }
                            FormatToggleButton(Icons.Rounded.FormatUnderlined, underline, ready, tr("Underline", "रेखांकित")) { host?.command("underline") }
                            IconButton(onClick = { colorPicker = true }, enabled = ready) {
                                Icon(Icons.Rounded.FormatColorText, tr("Text color", "टेक्स्ट का रंग"))
                            }
                            VerticalDivider(Modifier.height(24.dp), color = colors.line)
                            IconButton(onClick = { ribbonTab = 1; ribbonOpen = true }, enabled = ready) {
                                Icon(
                                    when (alignment) {
                                        "center" -> Icons.Rounded.FormatAlignCenter
                                        "right" -> Icons.Rounded.FormatAlignRight
                                        "justify" -> Icons.Rounded.FormatAlignJustify
                                        else -> Icons.Rounded.FormatAlignLeft
                                    },
                                    tr("Paragraph alignment", "अनुच्छेद का संरेखण"),
                                )
                            }
                            FormatToggleButton(Icons.Rounded.FormatListBulleted, bulletList, ready, tr("Bulleted list", "बुलेट सूची")) {
                                host?.command("bulletList")
                            }
                        }
                    }
                }
            } else if (review && !importedDocument) {
                Surface(color = colors.surface) {
                    Column(Modifier.navigationBarsPadding()) {
                        HorizontalDivider(color = colors.line)
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = { requestExport("share") },
                                enabled = ready && !exporting && !preparingExport,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            ) {
                                Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(tr("Share PDF", "PDF शेयर"))
                            }
                            Button(
                                onClick = { requestExport("save") },
                                enabled = ready && !exporting && !preparingExport,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = colors.accent,
                                        contentColor = colors.onAccent,
                                    ),
                            ) {
                                Icon(Icons.Rounded.SaveAlt, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(tr("Save PDF", "PDF सहेजें"))
                            }
                        }
                    }
                }
            }
        },
        topBar = {
            if (review) {
                TopAppBar(
                    title = {
                        Column {
                        Text(
                            document.title.ifBlank { tr("Document", "दस्तावेज़") },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold,
                        )
                            if (importedDocument) Text(
                                tr("On device", "फ़ोन में"),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.muted,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(finish) { Icon(Icons.Rounded.Close, tr("Close", "बंद करें")) }
                    },
                    actions = {
                        if (importedDocument) {
                            IconButton(onClick = { exportSheet = true }, enabled = ready && !exporting) {
                                Icon(Icons.Rounded.PictureAsPdf, tr("Export PDF", "PDF बनाएँ"))
                            }
                        } else {
                            TextButton(onClick = { onReview(false) }, enabled = ready && !exporting) {
                                Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(tr("Edit", "लिखें"))
                            }
                        }
                        IconButton(
                            onClick = { overflowMenu = true },
                            enabled = ready && !exporting,
                        ) {
                            Icon(Icons.Rounded.MoreVert, tr("Document options", "दस्तावेज़ विकल्प"))
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                )
            } else {
                Surface(color = colors.surface) {
                    Column {
                        // Main Executive Word/Docs App Bar
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .statusBarsPadding()
                                    .heightIn(min = 64.dp)
                                    .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Done Button: Word/Docs checkmark
                            IconButton(onClick = showReview, enabled = ready) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription =
                                                tr("Done & Preview", "सहेजें और देखें"),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }

                            // Document Title & Auto-Save status
                            var renaming by remember { mutableStateOf(false) }
                            Column(
                                modifier =
                                    Modifier.weight(1f)
                                        .clickable { renaming = true }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text =
                                            document.title.ifBlank {
                                                tr("Untitled document", "नया दस्तावेज़")
                                            },
                                        style =
                                            MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    Icon(
                                        Icons.Rounded.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint =
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = 0.7f
                                            ),
                                    )
                                }

                                // Auto-Save Status & Specs
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    val isSaved =
                                        saved == "Saved on device" || saved == "Recovered draft"
                                    val saveLabel =
                                        when (saved) {
                                            "Saved on device" ->
                                                tr("Saved on device", "फ़ोन में सहेजा गया")
                                            "Recovered draft" ->
                                                tr("Recovered draft", "ड्राफ़्ट वापस मिला")
                                            "Saving…",
                                            "Saving..." -> tr("Saving…", "सहेज रहे हैं…")
                                            "Save failed" -> tr("Save failed", "सहेजा नहीं जा सका")
                                            else -> tr("Unsaved", "अभी नहीं सहेजा")
                                        }
                                    Box(
                                        modifier =
                                            Modifier.size(6.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSaved) colors.accentText else colors.muted
                                                )
                                    )
                                    Text(
                                        text = saveLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color =
                                            if (isSaved) MaterialTheme.colorScheme.onSurfaceVariant
                                            else colors.muted,
                                    )
                                    Text(
                                        "•",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                    Text(
                                        "A4",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            if (renaming) {
                                var value by remember { mutableStateOf(document.title) }
                                val fallbackTitle = tr("Untitled document", "नया दस्तावेज़")
                                AlertDialog(
                                    onDismissRequest = { renaming = false },
                                    title = { Text(tr("Rename document", "नाम बदलें")) },
                                    text = {
                                        OutlinedTextField(
                                            value = value,
                                            onValueChange = { value = it },
                                            singleLine = true,
                                            label = {
                                                Text(tr("Document title", "दस्तावेज़ का नाम"))
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                model.title(value.trim().ifBlank { fallbackTitle })
                                                renaming = false
                                            }
                                        ) {
                                            Text(tr("Save", "सहेजें"))
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { renaming = false }) {
                                            Text(tr("Cancel", "रद्द करें"))
                                        }
                                    },
                                )
                            }

                            Button(
                                onClick = { exportSheet = true },
                                enabled = ready && !exporting && !preparingExport,
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = colors.accent,
                                        contentColor = colors.onAccent,
                                    ),
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("PDF", fontWeight = FontWeight.SemiBold)
                            }

                            // Overflow Menu ⋮
                            IconButton(
                                onClick = { overflowMenu = true },
                                enabled = ready && !exporting,
                            ) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    tr("Document options", "दस्तावेज़ विकल्प"),
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (findOpen)
                EditorFindBar(
                    query = findQuery,
                    onQuery = {
                        findQuery = it
                        matchTotal = 0
                        matchIndex = 0
                    },
                    replacement = replaceText,
                    onReplacement = { replaceText = it },
                    matchCase = matchCase,
                    onMatchCase = {
                        matchCase = it
                        matchTotal = 0
                        matchIndex = 0
                    },
                    wholeWord = wholeWord,
                    onWholeWord = {
                        wholeWord = it
                        matchTotal = 0
                        matchIndex = 0
                    },
                    total = matchTotal,
                    index = matchIndex,
                    limited = matchLimited,
                    replaceMode = replaceMode,
                    onReplaceMode = { replaceMode = it },
                    onPrevious = { host?.command("findPrevious") },
                    onNext = { host?.command("findNext") },
                    onReplace = { host?.command("replaceOne", replaceText) },
                    onReplaceAll = { host?.command("replaceAll", replaceText) },
                    onClose = closeFind,
                    enabled = ready,
                    editable = !review,
                )
            if (!review && !findOpen) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 2.dp,
                ) {
                    Row(
                        Modifier.fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                ribbonTab = 0
                                ribbonOpen = true
                            },
                            enabled = ready,
                            modifier = Modifier.heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            Text(tr("Format", "फ़ॉर्मैट"))
                        }
                        TextButton(
                            onClick = {
                                ribbonTab = 2
                                ribbonOpen = true
                            },
                            enabled = ready,
                            modifier = Modifier.heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            Text(tr("Insert", "जोड़ें"))
                        }
                        TextButton(onClick = onSetup, enabled = ready) { Text(tr("Page", "पेज")) }
                        VerticalDivider(
                            Modifier.height(24.dp).padding(horizontal = 4.dp),
                            color = colors.line,
                        )
                        // Undo Button
                        IconButton(
                            onClick = { host?.command("undo") },
                            enabled = ready && canUndo,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Undo,
                                contentDescription = tr("Undo", "पहले जैसा"),
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        // Redo Button
                        IconButton(
                            onClick = { host?.command("redo") },
                            enabled = ready && canRedo,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Redo,
                                contentDescription = tr("Redo", "फिर से"),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                HorizontalDivider(color = colors.line)
            }
            // Document Writing Canvas (Pure white, true A4 layout)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        EditorHost(ctx) { message ->
                                when (message.optString("type")) {
                                    "ready" -> {
                                        ready = true
                                        host?.setReview(review)
                                    }
                                    "change" ->
                                        model.edit(
                                            message.getJSONObject("content").toString(),
                                            document.id,
                                        )
                                    "selection" -> {
                                        mediaState =
                                            MediaSelection(
                                                table = message.optBoolean("isTable"),
                                                canMerge = message.optBoolean("canMerge"),
                                                canSplit = message.optBoolean("canSplit"),
                                                image = message.optBoolean("isImage"),
                                                imageWidth = message.optInt("imageWidth", 50),
                                                imageRotation =
                                                    message
                                                        .optDouble("imageRotation", 0.0)
                                                        .toFloat(),
                                                imageAspectLocked =
                                                    message.optBoolean("imageAspectLocked", true),
                                                imageAlign =
                                                    message.optString("imageAlign", "left"),
                                                border = message.optInt("cellBorder", 1),
                                            )
                                        canUndo = message.optBoolean("canUndo")
                                        canRedo = message.optBoolean("canRedo")
                                        textColor = message.optString("color", "#111827")
                                        highlightColor = message.optString("highlight", "")
                                        bold = message.optBoolean("bold")
                                        italic = message.optBoolean("italic")
                                        underline = message.optBoolean("underline")
                                        strike = message.optBoolean("strike")
                                        alignment = message.optString("align", "left")
                                        heading = message.optInt("heading", 0)
                                        bulletList = message.optBoolean("bulletList")
                                        orderedList = message.optBoolean("orderedList")
                                        fontSize = message.optInt("fontSize", 12)
                                        fontFamily = message.optString("fontFamily", "Noto Sans")
                                        lineHeight = message.optDouble("lineHeight", 1.15)
                                        paragraphSpacing = message.optInt("paragraphSpacing", 0)
                                        wordCount = message.optInt("wordCount", 0)
                                        charCount = message.optInt("charCount", 0)
                                        paragraphCount = message.optInt("paragraphCount", 0)
                                    }
                                    "search" -> {
                                        if (
                                            message.optString("query") == findQuery &&
                                                message.optBoolean("matchCase") == matchCase &&
                                                message.optBoolean("wholeWord") == wholeWord
                                        ) {
                                            matchTotal = message.optInt("total")
                                            matchIndex = message.optInt("index")
                                            matchLimited = message.optBoolean("limited")
                                        }
                                    }
                                    "layout" -> Unit
                                    "clipboard" -> {
                                        (context.getSystemService(Context.CLIPBOARD_SERVICE)
                                                as ClipboardManager)
                                            .setPrimaryClip(
                                                ClipData.newPlainText(
                                                    "Document selection",
                                                    message.getString("text"),
                                                )
                                            )
                                    }
                                    "error" -> model.error.value = message.optString("message")
                                }
                            }
                            .also {
                                host = it
                                it.onSelectionFormat = { action ->
                                    if (action == "textColor") colorPicker = true
                                    else {
                                        ribbonTab = 0
                                        ribbonOpen = true
                                    }
                                }
                                it.load(document, prefs.language, dark)
                            }
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = {
                        it.dispose()
                        host = null
                    },
                )
                if (!ready) CircularProgressIndicator(Modifier.align(Alignment.Center))
            }

            if (!review && !findOpen && (mediaState.image || mediaState.table)) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (mediaState.table) Icons.Rounded.GridOn else Icons.Rounded.Photo,
                            null,
                            Modifier.size(20.dp),
                        )
                        Text(
                            if (mediaState.table) tr("Table selected", "टेबल चुनी गई")
                            else tr("Image selected", "चित्र चुना गया"),
                            Modifier.weight(1f).padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        TextButton(onClick = { mediaOpen = true }, enabled = ready) {
                            Text(tr("Adjust", "बदलें"))
                        }
                    }
                }
            }
        }
    }

    if (overflowMenu) {
        EditorMoreSheet(
            title = document.title,
            review = review,
            onDismiss = { overflowMenu = false },
            onAction = { action ->
                overflowMenu = false
                when (action) {
                    "page" -> onSetup()
                    "stats" -> statsOpen = true
                    "find" -> findOpen = true
                    "delete" -> onDelete()
                    "save" ->
                        host?.snapshot { content ->
                            if (content != null) {
                                model.edit(content, document.id)
                                model.save()
                            }
                        }
                    "paste" -> {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = clipboard.primaryClip
                        val text =
                            if (clip != null && clip.itemCount > 0)
                                clip.getItemAt(0).coerceToText(context).toString()
                            else ""
                        host?.command("paste", text)
                    }
                    else -> host?.command(action)
                }
            },
        )
    }

    if (exportSheet) {
        EditorExportSheet(
            title = document.title,
            busy = preparingExport || exporting,
            onDismiss = { if (!preparingExport) exportSheet = false },
            onExport = requestExport,
        )
    }

    // Modal Document Statistics Dialog
    if (statsOpen) {
        DocumentStatsDialog(
            wordCount = wordCount,
            charCount = charCount,
            paragraphCount = paragraphCount,
            onDismiss = { statsOpen = false },
        )
    }

    // Standalone Font Color Palette Sheet
    if (colorPicker) {
        FontColorSheet(
            selectedColor = textColor,
            onDismiss = { colorPicker = false },
            onSelect = { hex ->
                host?.command("color", hex)
                colorPicker = false
            },
        )
    }

    if (ribbonOpen) {
        EditorToolsSheet(
            state =
                EditorFormatState(
                    bold,
                    italic,
                    underline,
                    strike,
                    textColor,
                    highlightColor,
                    alignment,
                    heading,
                    bulletList,
                    orderedList,
                    fontSize,
                    fontFamily,
                    lineHeight,
                    paragraphSpacing,
                ),
            initialTab = ribbonTab,
            onDismiss = { ribbonOpen = false },
            command = { name, value -> host?.command(name, value) },
            onPickImage = { isSig ->
                signature = isSig
                ribbonOpen = false
                pickImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onTable = {
                ribbonOpen = false
                mediaOpen = true
            },
            onSetup = {
                ribbonOpen = false
                onSetup()
            },
            onFind = {
                ribbonOpen = false
                findOpen = true
            },
            onReview = {
                ribbonOpen = false
                showReview()
            },
        )
    }
    if (mediaOpen)
        MediaToolsSheet(
            selection = mediaState,
            onDismiss = { mediaOpen = false },
            command = { name, value -> host?.command(name, value) },
            pick = { isSig ->
                signature = isSig
                mediaOpen = false
                pickImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
        )
}

@Composable
private fun FormatToggleButton(
    icon: ImageVector,
    active: Boolean,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier.size(48.dp).semantics { selected = active },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = description,
                tint =
                    if (active) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun StatsDashboard(wordCount: Int, charCount: Int, paragraphCount: Int) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard(
                title = tr("Words", "शब्द"),
                value = "$wordCount",
                icon = Icons.AutoMirrored.Rounded.Article,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                title = tr("Characters", "वर्ण"),
                value = "$charCount",
                icon = Icons.Rounded.TextFields,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard(
                title = tr("Paragraphs", "अनुच्छेद"),
                value = "$paragraphCount",
                icon = Icons.AutoMirrored.Rounded.Segment,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                title = tr("Reading Time", "पढ़ने का समय"),
                value = "${(wordCount / 200).coerceAtLeast(1)} ${tr("min", "मिनट")}",
                icon = Icons.Rounded.Schedule,
                modifier = Modifier.weight(1f),
            )
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(
                        "ISO 216 Standard A4",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "210 × 297 mm • Printable Vector PDF",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    icon,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

/** Dedicated Modal Dialog for Word / Docs Statistics (Triggered by Word Count chip) */
@Composable
private fun DocumentStatsDialog(
    wordCount: Int,
    charCount: Int,
    paragraphCount: Int,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.Analytics, null, tint = MaterialTheme.colorScheme.primary)
                Text(tr("Document Statistics", "दस्तावेज़ सांख्यिकी"))
            }
        },
        text = {
            StatsDashboard(
                wordCount = wordCount,
                charCount = charCount,
                paragraphCount = paragraphCount,
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Close", "बंद करें")) } },
    )
}
