package com.a4sizepaper.typing.texttopdf.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a4sizepaper.typing.texttopdf.data.AppPreferences
import com.a4sizepaper.typing.texttopdf.domain.*
import com.a4sizepaper.typing.texttopdf.editor.EditorHost

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    model: MainViewModel,
    document: Document,
    prefs: AppPreferences,
    onBack: () -> Unit,
    onSetup: () -> Unit,
    onExport: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val dark = prefs.theme == "dark" || (prefs.theme == "system" && isSystemInDarkTheme())
    val saved by model.saving.collectAsStateWithLifecycle()
    var host by remember(document.id) { mutableStateOf<EditorHost?>(null) }
    var ready by remember(document.id) { mutableStateOf(false) }
    var pageCount by remember { mutableIntStateOf(1) }
    var bold by remember { mutableStateOf(false) }
    var italic by remember { mutableStateOf(false) }
    var underline by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var media by remember { mutableStateOf(false) }
    var signature by rememberSaveable { mutableStateOf(false) }
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Notifications are optional; denial must not prevent document export.
            onExport()
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
        } else onExport()
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
    BackHandler(onBack = finish)
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
        topBar = {
            TopAppBar(
                title = {
                    var renaming by remember { mutableStateOf(false) }
                    val savedLabel =
                        when (saved) {
                            "Saved on device" -> tr("Saved on device", "सहेजा गया")
                            "Saving…" -> tr("Saving…", "सहेजा जा रहा है…")
                            "Unsaved" -> tr("Unsaved", "सहेजना बाकी")
                            "Draft protected" -> tr("Draft protected", "ड्राफ़्ट सुरक्षित")
                            "Recovered draft" -> tr("Recovered draft", "ड्राफ़्ट बहाल किया गया")
                            "Save failed" -> tr("Save failed", "सहेजने में विफल")
                            "Ready" -> tr("Ready", "तैयार")
                            else -> saved
                        }
                    Column(verticalArrangement = Arrangement.Center) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { renaming = true },
                        ) {
                            Text(
                                document.title.ifBlank { tr("Untitled document", "नया दस्तावेज़") },
                                style =
                                    MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        textDecoration =
                                            androidx.compose.ui.text.style.TextDecoration.Underline,
                                    ),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = tr("Rename", "नाम बदलें"),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            savedLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    if (renaming) {
                        var value by remember { mutableStateOf(document.title) }
                        val fallbackTitle = tr("Untitled document", "नया दस्तावेज़")
                        AlertDialog(
                            onDismissRequest = { renaming = false },
                            title = { Text(tr("Rename document", "नाम बदलें")) },
                            text = {
                                OutlinedTextField(
                                    value,
                                    { value = it },
                                    singleLine = true,
                                    label = { Text(tr("Document title", "दस्तावेज़ का नाम")) },
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
                },
                navigationIcon = {
                    IconButton(finish) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back", "वापस"))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { host?.command("undo") },
                        enabled = ready,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, tr("Undo", "पहले जैसा"))
                    }
                    IconButton(
                        onClick = { host?.command("redo") },
                        enabled = ready,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Redo, tr("Redo", "फिर से"))
                    }
                    IconButton(onClick = onSetup, enabled = ready) {
                        Icon(Icons.Rounded.Tune, tr("Page setup", "पेज सेटअप"))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Sleek 1-row scrollable formatting toolbar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                shadowElevation = 2.dp,
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Tool(Icons.Rounded.FormatBold, tr("Bold", "बोल्ड"), bold, ready) {
                        host?.command("bold")
                    }
                    Tool(Icons.Rounded.FormatItalic, tr("Italic", "इटैलिक"), italic, ready) {
                        host?.command("italic")
                    }
                    Tool(Icons.Rounded.FormatUnderlined, tr("Underline", "रेखांकित"), underline, ready) {
                        host?.command("underline")
                    }
                    Tool(
                        Icons.AutoMirrored.Rounded.FormatListBulleted,
                        tr("Bullets", "बुलेट"),
                        enabled = ready,
                    ) {
                        host?.command("bulletList")
                    }
                    Tool(
                        Icons.Rounded.FormatListNumbered,
                        tr("Numbers", "क्रमांक"),
                        enabled = ready,
                    ) {
                        host?.command("orderedList")
                    }
                    Tool(
                        Icons.Rounded.FormatColorText,
                        tr("Style", "शैली"),
                        enabled = ready,
                    ) {
                        format = true
                    }
                    Tool(
                        Icons.Rounded.AddPhotoAlternate,
                        tr("Insert", "जोड़ें"),
                        enabled = ready,
                    ) {
                        media = true
                    }
                    Box {
                        Tool(
                            Icons.Rounded.MoreHoriz,
                            tr("More", "अन्य"),
                            enabled = ready,
                        ) {
                            more = true
                        }
                        DropdownMenu(more, { more = false }) {
                            listOf(
                                "copy" to tr("Copy", "कॉपी"),
                                "cut" to tr("Cut", "काटें"),
                                "paste" to tr("Paste", "चिपकाएँ"),
                                "selectAll" to tr("Select all", "सभी चुनें"),
                                "date" to tr("Insert date", "दिनांक जोड़ें"),
                                "pageBreak" to tr("Insert page break", "नया पेज जोड़ें"),
                                "clear" to tr("Clear formatting", "फ़ॉर्मैट हटाएँ"),
                                "save" to tr("Save now", "अभी सहेजें"),
                            ).forEach { (command, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        more = false
                                        when (command) {
                                            "paste" -> {
                                                val clip =
                                                    context.getSystemService(
                                                        Context.CLIPBOARD_SERVICE
                                                    ) as ClipboardManager
                                                host?.command(
                                                    "paste",
                                                    clip.primaryClip
                                                        ?.getItemAt(0)
                                                        ?.coerceToText(context)
                                                        ?.toString() ?: "",
                                                )
                                            }
                                            "save" -> {
                                                host?.snapshot {
                                                    if (it != null)
                                                        model.edit(it, document.id)
                                                    model.save()
                                                }
                                            }
                                            else -> host?.command(command)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            // Document writing canvas
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        EditorHost(ctx) { message ->
                                when (message.optString("type")) {
                                    "ready" -> {
                                        ready = true
                                    }
                                    "change" ->
                                        model.edit(
                                            message.getJSONObject("content").toString(),
                                            document.id,
                                        )
                                    "selection" -> {
                                        bold = message.optBoolean("bold")
                                        italic = message.optBoolean("italic")
                                        underline = message.optBoolean("underline")
                                    }
                                    "layout" -> pageCount = message.optInt("pageCount", 1)
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
            HorizontalDivider()
            // Bottom Action Bar: Page stats + Next: Preview & PDF
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.Description,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "$pageCount ${tr("page(s)", "पेज")}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    Button(
                        onClick = {
                            host?.snapshot {
                                if (it != null) {
                                    model.edit(it, document.id)
                                    model.save()
                                    exportWithNotification()
                                }
                            }
                        },
                        enabled = ready,
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            tr("Next: Preview & PDF →", "आगे बढ़ें: प्रीव्यू और PDF →"),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
        }
    }
    if (format)
        FormattingDialog(onDismiss = { format = false }) { name, value ->
            host?.command(name, value)
        }
    if (media)
        MediaDialog(
            onDismiss = { media = false },
            command = { name, value -> host?.command(name, value) },
            pick = { isSignature ->
                signature = isSignature
                media = false
                pickImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
        )
}

@Composable
private fun MediaDialog(
    onDismiss: () -> Unit,
    command: (String, String?) -> Unit,
    pick: (Boolean) -> Unit,
) {
    var selectedBorder by remember { mutableStateOf(1) }
    var selectedCellAlign by remember { mutableStateOf("left") }
    var selectedImgWidth by remember { mutableStateOf(50) }
    var selectedImgAlign by remember { mutableStateOf("left") }

    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val error = MaterialTheme.colorScheme.error
    val outline = MaterialTheme.colorScheme.outline
    val sectionLabelStyle = MaterialTheme.typography.labelSmall.copy(color = outline)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {

                // ── Colored Header ──────────────────────────────────────
                Surface(
                    color = primary,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Rounded.GridOn,
                            contentDescription = null,
                            tint = onPrimary,
                            modifier = Modifier.size(28.dp),
                        )
                        Column {
                            Text(
                                tr("Tables & images", "टेबल और चित्र"),
                                color = onPrimary,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                tr("Cursor in cell to edit table", "सेल में कर्सर रखकर बदलें"),
                                color = onPrimary.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Section: New Table ───────────────────────────────────
                Text(tr("NEW TABLE", "नई टेबल"), style = sectionLabelStyle)
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { command("table", null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.GridOn, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Insert 3 × 3 table", "3 × 3 टेबल जोड़ें"))
                }

                Spacer(Modifier.height(16.dp))

                // ── Section: Row & Column ────────────────────────────────
                Text(tr("ROW / COLUMN", "पंक्ति / कॉलम"), style = sectionLabelStyle)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { command("addRowAfter", null) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.AddBox, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr("Add row", "पंक्ति जोड़ें"), style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = { command("deleteRow", null) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = error),
                        border = BorderStroke(1.dp, error.copy(alpha = 0.5f)),
                    ) {
                        Icon(Icons.Rounded.IndeterminateCheckBox, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr("Del row", "पंक्ति हटाएँ"), style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { command("addColumnAfter", null) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.AddBox, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr("Add col", "कॉलम जोड़ें"), style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = { command("deleteColumn", null) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = error),
                        border = BorderStroke(1.dp, error.copy(alpha = 0.5f)),
                    ) {
                        Icon(Icons.Rounded.IndeterminateCheckBox, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr("Del col", "कॉलम हटाएँ"), style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Section: Cell ────────────────────────────────────────
                Text(tr("CELL", "सेल"), style = sectionLabelStyle)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { command("toggleHeaderRow", null) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.TableRows, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Toggle header row", "शीर्षक पंक्ति टॉगल"))
                    }
                    OutlinedButton(
                        onClick = { command("mergeCells", null) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Merge, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Merge selected cells", "चुने सेल मिलाएँ"))
                    }
                    OutlinedButton(
                        onClick = { command("splitCell", null) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.CallSplit, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Split merged cell", "मिला सेल बाँटें"))
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Section: Cell Border ─────────────────────────────────
                Text(tr("CELL BORDER", "सेल की सीमा"), style = sectionLabelStyle)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 1, 2).forEach { width ->
                        val selected = selectedBorder == width
                        if (selected) {
                            Button(
                                onClick = {
                                    selectedBorder = width
                                    command("cellBorder", width.toString())
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("$width px") }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    selectedBorder = width
                                    command("cellBorder", width.toString())
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("$width px") }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Section: Cell Alignment ──────────────────────────────
                Text(tr("CELL ALIGNMENT", "सेल संरेखण"), style = sectionLabelStyle)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "left" to Icons.Rounded.FormatAlignLeft,
                        "center" to Icons.Rounded.FormatAlignCenter,
                        "right" to Icons.Rounded.FormatAlignRight,
                    ).forEach { (v, icon) ->
                        val selected = selectedCellAlign == v
                        if (selected) {
                            Button(
                                onClick = { selectedCellAlign = v; command("align", v) },
                                modifier = Modifier.weight(1f),
                            ) { Icon(icon, null) }
                        } else {
                            OutlinedButton(
                                onClick = { selectedCellAlign = v; command("align", v) },
                                modifier = Modifier.weight(1f),
                            ) { Icon(icon, null) }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Danger: Delete Table ─────────────────────────────────
                OutlinedButton(
                    onClick = { command("deleteTable", null) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = error),
                    border = BorderStroke(1.dp, error.copy(alpha = 0.6f)),
                ) {
                    Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Delete table", "टेबल हटाएँ"))
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                // ── Section: Image ───────────────────────────────────────
                Text(tr("IMAGE", "चित्र"), style = sectionLabelStyle)
                Spacer(Modifier.height(6.dp))
                Text(
                    tr(
                        "Tap an image first, then choose its width or alignment.",
                        "पहले चित्र चुनें, फिर चौड़ाई या संरेखण बदलें।",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = outline,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { pick(false) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Photo, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Photo", "फ़ोटो"), style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = { pick(true) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Draw, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Signature", "हस्ताक्षर"), style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Image width picker
                Text(tr("Image width", "चित्र चौड़ाई"), style = sectionLabelStyle)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(25, 50, 75, 100).forEach { v ->
                        val selected = selectedImgWidth == v
                        if (selected) {
                            Button(
                                onClick = { selectedImgWidth = v; command("imageWidth", v.toString()) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            ) { Text("$v%", style = MaterialTheme.typography.labelMedium) }
                        } else {
                            OutlinedButton(
                                onClick = { selectedImgWidth = v; command("imageWidth", v.toString()) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            ) { Text("$v%", style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Image alignment picker
                Text(tr("Image alignment", "चित्र संरेखण"), style = sectionLabelStyle)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "left" to Icons.Rounded.FormatAlignLeft,
                        "center" to Icons.Rounded.FormatAlignCenter,
                        "right" to Icons.Rounded.FormatAlignRight,
                    ).forEach { (v, icon) ->
                        val selected = selectedImgAlign == v
                        if (selected) {
                            Button(
                                onClick = { selectedImgAlign = v; command("imageAlign", v) },
                                modifier = Modifier.weight(1f),
                            ) { Icon(icon, null) }
                        } else {
                            OutlinedButton(
                                onClick = { selectedImgAlign = v; command("imageAlign", v) },
                                modifier = Modifier.weight(1f),
                            ) { Icon(icon, null) }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Danger: Delete Image
                OutlinedButton(
                    onClick = { command("deleteImage", null) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = error),
                    border = BorderStroke(1.dp, error.copy(alpha = 0.6f)),
                ) {
                    Icon(Icons.Rounded.HideImage, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Delete selected image", "चुना चित्र हटाएँ"))
                }

                Spacer(Modifier.height(16.dp))

                // ── Done Button (Prominent) ──────────────────────────────
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Done", "पूर्ण"), style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun Tool(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.width(62.dp).heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.small,
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val tint =
                if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                else if (active) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Text(
                label,
                color = tint,
                style = MaterialTheme.typography.labelSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FormattingDialog(onDismiss: () -> Unit, command: (String, String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Text & paragraph", "टेक्स्ट और अनुच्छेद")) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(tr("Font family", "फ़ॉन्ट"))
                PageSettings.FONTS.forEach { font ->
                    FluentOutlinedButton(onClick = { command("fontFamily", font) }) { Text(font) }
                }
                Text(tr("Size (pt)", "आकार (pt)"))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(10, 12, 14, 16, 18, 24, 32, 48).forEach { size ->
                        TextButton(onClick = { command("fontSize", size.toString()) }) {
                            Text("$size")
                        }
                    }
                }
                Text(tr("Alignment", "संरेखण"))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(
                            "left" to tr("Left", "बाएँ"),
                            "center" to tr("Center", "बीच"),
                            "right" to tr("Right", "दाएँ"),
                            "justify" to tr("Justify", "दोनों ओर"),
                        )
                        .forEach { (key, label) ->
                            TextButton(onClick = { command("align", key) }) { Text(label) }
                        }
                }
                Text(tr("Text color", "टेक्स्ट का रंग"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("#111827", "#2563eb", "#0f766e", "#b91c1c", "#7c3aed").forEach { hex ->
                        FluentButton(
                            onClick = { command("color", hex) },
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = Color(android.graphics.Color.parseColor(hex))
                                ),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(48.dp),
                        ) {
                            Text("A")
                        }
                    }
                }
                Text(tr("Line spacing", "पंक्ति अंतर"))
                Row {
                    listOf("1.0", "1.25", "1.5", "2.0").forEach { v ->
                        TextButton(onClick = { command("lineHeight", v) }) { Text(v) }
                    }
                }
                Text(tr("Paragraph spacing (pt)", "अनुच्छेद अंतर (pt)"))
                Row {
                    listOf("0", "4", "8", "12").forEach { v ->
                        TextButton(onClick = { command("paragraphSpacing", v) }) { Text(v) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(tr("Done", "पूर्ण")) } },
    )
}

@Composable
fun PageSetupDialog(initial: PageSettings, onDismiss: () -> Unit, onSave: (PageSettings) -> Unit) {
    var landscape by remember { mutableStateOf(initial.landscape) }
    var top by remember { mutableStateOf(initial.marginTop.toString()) }
    var right by remember { mutableStateOf(initial.marginRight.toString()) }
    var bottom by remember { mutableStateOf(initial.marginBottom.toString()) }
    var left by remember { mutableStateOf(initial.marginLeft.toString()) }
    var font by remember { mutableStateOf(initial.fontFamily) }
    var size by remember { mutableStateOf(initial.fontSize.toString()) }
    var line by remember { mutableStateOf(initial.lineHeight.toString()) }
    var spacing by remember { mutableStateOf(initial.paragraphSpacing.toString()) }
    val value =
        runCatching {
                PageSettings(
                        landscape,
                        top.toDouble(),
                        right.toDouble(),
                        bottom.toDouble(),
                        left.toDouble(),
                        font,
                        size.toInt(),
                        line.toDouble(),
                        spacing.toInt(),
                    )
                    .validate()
            }
            .getOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Page setup", "पेज सेटअप")) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("A4 · 210 × 297 mm", fontWeight = FontWeight.Bold)
                ChoiceRow(
                    listOf(
                        "portrait" to tr("Portrait", "पोर्ट्रेट"),
                        "landscape" to tr("Landscape", "लैंडस्केप"),
                    ),
                    if (landscape) "landscape" else "portrait",
                ) {
                    landscape = it == "landscape"
                }
                Text(tr("Margins · 5–60 mm", "मार्जिन · 5–60 मिमी"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(top, { top = it }, tr("Top", "ऊपर"), Modifier.weight(1f))
                    NumberField(bottom, { bottom = it }, tr("Bottom", "नीचे"), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(left, { left = it }, tr("Left", "बाएँ"), Modifier.weight(1f))
                    NumberField(right, { right = it }, tr("Right", "दाएँ"), Modifier.weight(1f))
                }
                Text(tr("Default font", "डिफ़ॉल्ट फ़ॉन्ट"))
                ChoiceRow(PageSettings.FONTS.map { it to it }, font) { font = it }
                NumberField(size, { size = it }, tr("Font size · 8–36 pt", "फ़ॉन्ट आकार · 8–36 pt"))
                NumberField(line, { line = it }, tr("Line spacing · 1–2.5", "पंक्ति अंतर · 1–2.5"))
                NumberField(
                    spacing,
                    { spacing = it },
                    tr("Paragraph spacing · 0–30 pt", "अनुच्छेद अंतर · 0–30 pt"),
                )
                if (value == null)
                    Text(
                        tr("Enter values within the shown ranges.", "दिखाई गई सीमा में मान भरें।"),
                        color = MaterialTheme.colorScheme.error,
                    )
            }
        },
        confirmButton = {
            TextButton(onClick = { value?.let(onSave) }, enabled = value != null) {
                Text(tr("Apply", "लागू करें"))
            }
        },
        dismissButton = { TextButton(onDismiss) { Text(tr("Cancel", "रद्द करें")) } },
    )
}

@Composable
private fun NumberField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value,
        onValue,
        modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}
