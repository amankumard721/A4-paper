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
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    onExport: (String?) -> Unit,
    review: Boolean,
    onReview: (Boolean) -> Unit,
) {
    var exportAction by rememberSaveable { mutableStateOf<String?>(null) }
    val exporting by model.exporting.collectAsStateWithLifecycle()
    val colorLabel = tr("Font color", "टेक्स्ट का रंग")
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val dark = prefs.theme == "dark" || (prefs.theme == "system" && isSystemInDarkTheme())
    val saved by model.saving.collectAsStateWithLifecycle()
    var host by remember(document.id) { mutableStateOf<EditorHost?>(null) }
    var ready by remember(document.id) { mutableStateOf(false) }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    var bold by remember { mutableStateOf(false) }
    var italic by remember { mutableStateOf(false) }
    var underline by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf(false) }
    var colorPicker by remember { mutableStateOf(false) }
    var textColor by remember(document.id) { mutableStateOf("#111827") }
    var media by remember { mutableStateOf(false) }
    var mediaState by remember { mutableStateOf(MediaSelection()) }
    var signature by rememberSaveable { mutableStateOf(false) }
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Notifications are optional; denial must not prevent document export.
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
        host?.snapshot { content ->
            if (content != null) {
                model.edit(content, document.id)
                model.save()
                onReview(true)
            }
        }
    }
    BackHandler { if (review) finish() else showReview() }
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
    var insertMenu by remember { mutableStateOf(false) }
    var overflowMenu by remember { mutableStateOf(false) }

    LaunchedEffect(dark, ready) { if (ready) host?.setDark(dark) }
    Scaffold(
        modifier = Modifier.imePadding(),
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            if (review)
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                                "save" to tr("Save PDF", "PDF सहेजें"),
                                "share" to tr("Share PDF", "PDF शेयर करें"),
                            )
                            .forEach { (action, label) ->
                                TextButton(
                                    enabled = ready && !exporting,
                                    onClick = {
                                        host?.snapshot { content ->
                                            if (content != null) {
                                                model.edit(content, document.id)
                                                model.save()
                                                exportAction = action
                                                exportWithNotification()
                                            }
                                        }
                                    },
                                ) {
                                    Icon(
                                        if (action == "save") Icons.Rounded.SaveAlt
                                        else Icons.Rounded.Share,
                                        null,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(label)
                                }
                            }
                    }
                }
        },
        topBar = {
            if (review) {
                TopAppBar(
                    title = {
                        Text(
                            document.title.ifBlank { tr("Document", "दस्तावेज़") },
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(finish) { Icon(Icons.Rounded.Close, tr("Close", "बंद करें")) }
                    },
                    actions = {
                        IconButton(onClick = { onReview(false) }, enabled = ready && !exporting) {
                            Icon(Icons.Rounded.Edit, tr("Edit document", "दस्तावेज़ लिखें"))
                        }
                        IconButton(onClick = onSetup, enabled = ready && !exporting) {
                            Icon(Icons.Rounded.Tune, tr("Page setup", "पेज सेटअप"))
                        }
                    },
                )
            } else
                Column {
                    TopAppBar(
                        title = {
                            var renaming by remember { mutableStateOf(false) }
                            Column {
                                Text(
                                    document.title.ifBlank {
                                        tr("Untitled document", "नया दस्तावेज़")
                                    },
                                    style =
                                        MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier =
                                        Modifier.clickable { renaming = true }
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                )
                                Text(
                                    tr("A4 document", "A4 दस्तावेज़"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                            label = {
                                                Text(tr("Document title", "दस्तावेज़ का नाम"))
                                            },
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
                            // One 48 dp target finishes editing and saves the snapshot.
                            Box(Modifier.padding(start = 12.dp, end = 4.dp)) {
                                FilledIconButton(
                                    onClick = showReview,
                                    enabled = ready,
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    colors =
                                        IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = tr("Done", "सहेजें"),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shadowElevation = 1.dp,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { host?.command("undo") },
                                enabled = ready && canUndo,
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Undo, tr("Undo", "पहले जैसा"))
                            }
                            IconButton(
                                onClick = { host?.command("redo") },
                                enabled = ready && canRedo,
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Redo, tr("Redo", "फिर से"))
                            }
                            IconButton(onClick = { format = true }, enabled = ready) {
                                Icon(Icons.Rounded.FormatColorText, tr("Format", "शैली"))
                            }
                            // Insert +
                            Box {
                                IconButton(onClick = { insertMenu = true }, enabled = ready) {
                                    Icon(Icons.Rounded.Add, tr("Insert", "जोड़ें"))
                                }
                                DropdownMenu(
                                    expanded = insertMenu,
                                    onDismissRequest = { insertMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(tr("Table & Media", "टेबल और चित्र")) },
                                        onClick = {
                                            insertMenu = false
                                            media = true
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.GridOn, null) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(tr("Insert Photo", "फ़ोटो जोड़ें")) },
                                        onClick = {
                                            insertMenu = false
                                            signature = false
                                            pickImage.launch(
                                                PickVisualMediaRequest(
                                                    ActivityResultContracts.PickVisualMedia
                                                        .ImageOnly
                                                )
                                            )
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.Photo, null) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(tr("Insert Signature", "हस्ताक्षर जोड़ें")) },
                                        onClick = {
                                            insertMenu = false
                                            signature = true
                                            pickImage.launch(
                                                PickVisualMediaRequest(
                                                    ActivityResultContracts.PickVisualMedia
                                                        .ImageOnly
                                                )
                                            )
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.Draw, null) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(tr("Page Break", "नया पेज (पेज ब्रेक)")) },
                                        onClick = {
                                            insertMenu = false
                                            host?.command("pageBreak")
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.InsertPageBreak, null) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(tr("Insert Date", "आज की तारीख")) },
                                        onClick = {
                                            insertMenu = false
                                            host?.command("date")
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.CalendarToday, null) },
                                    )
                                }
                            }
                            // Overflow ⋮
                            Box {
                                IconButton(onClick = { overflowMenu = true }, enabled = ready) {
                                    Icon(Icons.Rounded.MoreVert, tr("More", "अन्य"))
                                }
                                DropdownMenu(
                                    expanded = overflowMenu,
                                    onDismissRequest = { overflowMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(tr("Export / Create PDF", "PDF बनाएँ")) },
                                        onClick = {
                                            overflowMenu = false
                                            host?.snapshot {
                                                if (it != null) {
                                                    model.edit(it, document.id)
                                                    model.save()
                                                    exportWithNotification()
                                                }
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, null) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(tr("Page Setup", "पेज सेटअप")) },
                                        onClick = {
                                            overflowMenu = false
                                            onSetup()
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.Tune, null) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(tr("Select All", "सभी चुनें")) },
                                        onClick = {
                                            overflowMenu = false
                                            host?.command("selectAll")
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.SelectAll, null) },
                                    )
                                    listOf(
                                            "copy" to tr("Copy", "कॉपी"),
                                            "cut" to tr("Cut", "काटें"),
                                            "paste" to tr("Paste", "चिपकाएँ"),
                                            "clear" to tr("Clear formatting", "फ़ॉर्मैट हटाएँ"),
                                        )
                                        .forEach { (action, label) ->
                                            DropdownMenuItem(
                                                text = { Text(label) },
                                                onClick = {
                                                    overflowMenu = false
                                                    if (action == "paste") {
                                                        val clipboard =
                                                            context.getSystemService(
                                                                Context.CLIPBOARD_SERVICE
                                                            ) as ClipboardManager
                                                        val clip = clipboard.primaryClip
                                                        val text =
                                                            if (clip != null && clip.itemCount > 0)
                                                                clip
                                                                    .getItemAt(0)
                                                                    .coerceToText(context)
                                                                    .toString()
                                                            else ""
                                                        host?.command("paste", text)
                                                    } else host?.command(action)
                                                },
                                            )
                                        }
                                    DropdownMenuItem(
                                        text = { Text(tr("Save Now", "अभी सहेजें")) },
                                        onClick = {
                                            overflowMenu = false
                                            host?.snapshot {
                                                if (it != null) {
                                                    model.edit(it, document.id)
                                                    model.save()
                                                }
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.Save, null) },
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    host?.snapshot { content ->
                                        if (content != null) {
                                            model.edit(content, document.id)
                                            model.save()
                                            exportWithNotification()
                                        }
                                    }
                                },
                                enabled = ready,
                            ) {
                                Icon(Icons.Rounded.PictureAsPdf, tr("Create PDF", "PDF बनाएँ"))
                            }
                        }
                    }
                }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Document writing canvas (Pure white, edge-to-edge)
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
                                                imageAlign =
                                                    message.optString("imageAlign", "left"),
                                                border = message.optInt("cellBorder", 1),
                                            )
                                        canUndo = message.optBoolean("canUndo")
                                        canRedo = message.optBoolean("canRedo")
                                        textColor = message.optString("color", "#111827")
                                        bold = message.optBoolean("bold")
                                        italic = message.optBoolean("italic")
                                        underline = message.optBoolean("underline")
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
                                it.load(document, prefs.language, dark)
                            }
                    },
                    modifier = Modifier.fillMaxSize().padding(bottom = if (review) 84.dp else 0.dp),
                    onRelease = {
                        it.dispose()
                        host = null
                    },
                )
                if (!ready) CircularProgressIndicator(Modifier.align(Alignment.Center))
            }

            if (!review && (mediaState.image || mediaState.table)) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (mediaState.image)
                                tr("Drag a corner to resize", "कोना खींचकर आकार बदलें")
                            else tr("Drag ↔ to adjust columns", "↔ खींचकर कॉलम बदलें"),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        TextButton(onClick = { media = true }) {
                            Text(
                                if (mediaState.image) tr("Image", "चित्र") else tr("Table", "टेबल")
                            )
                        }
                    }
                }
            }
            // The Scaffold IME inset places this strip immediately above the keyboard.
            if (!review)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 2.dp,
                    shadowElevation = 4.dp,
                    border =
                        BorderStroke(
                            0.5.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        ),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        IconButton(
                            onClick = { host?.command("bold") },
                            enabled = ready,
                            colors =
                                if (bold) IconButtonDefaults.filledTonalIconButtonColors()
                                else IconButtonDefaults.iconButtonColors(),
                        ) {
                            Icon(
                                Icons.Rounded.FormatBold,
                                contentDescription = tr("Bold", "बोल्ड"),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(
                            onClick = { host?.command("italic") },
                            enabled = ready,
                            colors =
                                if (italic) IconButtonDefaults.filledTonalIconButtonColors()
                                else IconButtonDefaults.iconButtonColors(),
                        ) {
                            Icon(
                                Icons.Rounded.FormatItalic,
                                contentDescription = tr("Italic", "इटैलिक"),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(
                            onClick = { host?.command("underline") },
                            enabled = ready,
                            colors =
                                if (underline) IconButtonDefaults.filledTonalIconButtonColors()
                                else IconButtonDefaults.iconButtonColors(),
                        ) {
                            Icon(
                                Icons.Rounded.FormatUnderlined,
                                contentDescription = tr("Underline", "रेखांकित"),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(
                            onClick = { colorPicker = true },
                            enabled = ready,
                            modifier =
                                Modifier.semantics {
                                    contentDescription = "$colorLabel: $textColor"
                                },
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "A",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                Box(
                                    Modifier.width(24.dp)
                                        .height(4.dp)
                                        .background(
                                            runCatching {
                                                    Color(
                                                        android.graphics.Color.parseColor(textColor)
                                                    )
                                                }
                                                .getOrDefault(Color(0xFF111827))
                                        )
                                )
                            }
                        }
                        Box {
                            var alignMenu by remember { mutableStateOf(false) }
                            IconButton(onClick = { alignMenu = true }, enabled = ready) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.FormatAlignLeft,
                                    contentDescription = tr("Align", "संरेखण"),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            DropdownMenu(
                                expanded = alignMenu,
                                onDismissRequest = { alignMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(tr("Left", "बाएँ")) },
                                    onClick = {
                                        alignMenu = false
                                        host?.command("align", "left")
                                    },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Rounded.FormatAlignLeft, null)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(tr("Center", "बीच")) },
                                    onClick = {
                                        alignMenu = false
                                        host?.command("align", "center")
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.FormatAlignCenter, null) },
                                )
                                DropdownMenuItem(
                                    text = { Text(tr("Right", "दाएँ")) },
                                    onClick = {
                                        alignMenu = false
                                        host?.command("align", "right")
                                    },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Rounded.FormatAlignRight, null)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(tr("Justify", "दोनों ओर")) },
                                    onClick = {
                                        alignMenu = false
                                        host?.command("align", "justify")
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.FormatAlignJustify, null) },
                                )
                            }
                        }
                        Box {
                            var lists by remember { mutableStateOf(false) }
                            IconButton(onClick = { lists = true }, enabled = ready) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.FormatListBulleted,
                                    tr("Lists", "सूची"),
                                )
                            }
                            DropdownMenu(lists, { lists = false }) {
                                DropdownMenuItem(
                                    text = { Text(tr("Bulleted list", "बुलेट सूची")) },
                                    onClick = {
                                        lists = false
                                        host?.command("bulletList")
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(tr("Numbered list", "क्रमांक सूची")) },
                                    onClick = {
                                        lists = false
                                        host?.command("orderedList")
                                    },
                                )
                            }
                        }
                    }
                }
        }
    }
    if (colorPicker)
        FontColorSheet(textColor, onDismiss = { colorPicker = false }) { hex ->
            host?.command("color", hex)
            colorPicker = false
        }
    if (format)
        FormattingDialog(onDismiss = { format = false }) { name, value ->
            host?.command(name, value)
        }
    if (media)
        MediaToolsSheet(
            selection = mediaState,
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
