package com.a4sizepaper.typing.texttopdf.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a4sizepaper.typing.texttopdf.BuildConfig
import com.a4sizepaper.typing.texttopdf.ads.AdBanner
import com.a4sizepaper.typing.texttopdf.ads.AdsController
import com.a4sizepaper.typing.texttopdf.data.*
import com.a4sizepaper.typing.texttopdf.domain.*
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LekhakApp(model: MainViewModel, ads: AdsController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val adsState by ads.state.collectAsStateWithLifecycle()
    val prefs by model.prefs.collectAsStateWithLifecycle()
    val ready by model.recovered.collectAsStateWithLifecycle()
    val p = prefs
    if (p == null || !ready) {
        LekhakTheme("system", "en") {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        return
    }
    LaunchedEffect(ads, lifecycle) {
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        (context as? android.app.Activity)?.let(ads::start)
    }
    CompositionLocalProvider(LocalDocumentPreview provides model::previewParagraphs) {
        LekhakTheme(p.theme, p.language) {
            val docs by model.documents.collectAsStateWithLifecycle()
            val current by model.current.collectAsStateWithLifecycle()
            val error by model.error.collectAsStateWithLifecycle()
            val pdf by model.pdf.collectAsStateWithLifecycle()
            val exporting by model.exporting.collectAsStateWithLifecycle()
            val importing by model.importing.collectAsStateWithLifecycle()
            val importNotice by model.importNotice.collectAsStateWithLifecycle()
            val pendingImport by model.pendingImport.collectAsStateWithLifecycle()
            val importLabel by model.importLabel.collectAsStateWithLifecycle()
            val importedPdfs by model.importedPdfs.collectAsStateWithLifecycle()
            val viewingImportedPdf by model.viewingImportedPdf.collectAsStateWithLifecycle()
            var tab by rememberSaveable { mutableStateOf("home") }
            var screen by rememberSaveable { mutableStateOf("main") }
            var selectedTemplate by rememberSaveable { mutableStateOf<String?>(null) }
            var search by rememberSaveable { mutableStateOf("") }
            val homeListState = rememberLazyListState()
            var homeSearchVisible by rememberSaveable { mutableStateOf(false) }
            val homeSearchFocus = remember { FocusRequester() }
            val homeKeyboard = LocalSoftwareKeyboardController.current
            val homeFocusManager = LocalFocusManager.current
            val pullThreshold = with(LocalDensity.current) { 72.dp.toPx() }
            val searchPull = remember { floatArrayOf(0f) }
            fun closeHomeSearch() {
                homeSearchVisible = false
                search = ""
                searchPull[0] = 0f
                homeFocusManager.clearFocus()
                homeKeyboard?.hide()
            }
            val homeSearchPull = remember(homeListState, pullThreshold) {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (source != NestedScrollSource.UserInput) return Offset.Zero
                        if (tab != "home") {
                            searchPull[0] = 0f
                            return Offset.Zero
                        }
                        if (homeSearchVisible) {
                            if (available.y < 0f) {
                                searchPull[0] += available.y
                                if (searchPull[0] <= -pullThreshold * .65f) closeHomeSearch()
                            } else {
                                searchPull[0] = 0f
                            }
                            return Offset.Zero
                        }
                        if (available.y <= 0f || homeListState.canScrollBackward) {
                            searchPull[0] = 0f
                            return Offset.Zero
                        }
                        // Observe the finger before LazyColumn's edge stretch consumes the pull.
                        // Consume only downward movement at the top; ordinary scrolling is untouched.
                        searchPull[0] += available.y
                        if (searchPull[0] >= pullThreshold) {
                            homeSearchVisible = true
                            searchPull[0] = 0f
                        }
                        return Offset(0f, available.y)
                    }

                    override suspend fun onPreFling(available: Velocity): Velocity {
                        searchPull[0] = 0f
                        return Velocity.Zero
                    }

                    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                        searchPull[0] = 0f
                        return Velocity.Zero
                    }
                }
            }
            LaunchedEffect(homeSearchVisible, tab) {
                if (homeSearchVisible && tab == "home") {
                    // Finish the header transition before the IME changes the viewport.
                    kotlinx.coroutines.delay(220)
                    homeSearchFocus.requestFocus()
                    homeKeyboard?.show()
                }
                if (tab != "home") homeSearchVisible = false
            }
            BackHandler(enabled = screen == "main" && tab == "home" && homeSearchVisible) {
                closeHomeSearch()
            }

            val settingsListState = rememberLazyListState()
            val librarySnackbar = remember { SnackbarHostState() }
            val pendingLibraryDeletes = remember { mutableStateListOf<String>() }
            val libraryScope = rememberCoroutineScope()
            val removedMessage = tr("Document removed", "दस्तावेज़ हटाया गया")
            val undoLabel = tr("Undo", "पूर्ववत करें")
            val homeScope = rememberCoroutineScope()
            var searchPopup by rememberSaveable { mutableStateOf(false) }
            var homeUpdates by rememberSaveable { mutableStateOf(false) }
            var homeUnread by rememberSaveable { mutableStateOf(true) }
            var rename by remember { mutableStateOf<DocumentEntry?>(null) }
            var delete by remember { mutableStateOf<DocumentEntry?>(null) }
            var pageSetup by remember { mutableStateOf(false) }
            var defaultsSetup by remember { mutableStateOf(false) }
            var defaultViewerPrompt by rememberSaveable { mutableStateOf(false) }
            val viewerPromptPrefs = remember(context) {
                context.getSharedPreferences("viewer_prompt", android.content.Context.MODE_PRIVATE)
            }
            fun offerDefaultViewerPrompt(external: Boolean) {
                if (external && !viewerPromptPrefs.getBoolean("shown", false)) {
                    viewerPromptPrefs.edit().putBoolean("shown", true).apply()
                    defaultViewerPrompt = true
                }
            }
            var defaultViewerHelp by rememberSaveable { mutableStateOf(false) }
            var info by remember { mutableStateOf<String?>(null) }
            var importedEditorId by rememberSaveable { mutableStateOf<String?>(null) }
            var editorReview by
                rememberSaveable(current?.id) { mutableStateOf(model.importNotice.value != null) }
            var pdfAction by rememberSaveable { mutableStateOf<String?>(null) }
            var awaitingPdf by rememberSaveable { mutableStateOf(false) }
            var externalPdfPath by rememberSaveable { mutableStateOf<String?>(null) }
            var pdfReturnScreen by rememberSaveable { mutableStateOf("main") }
            var pdfPagesDialog by remember { mutableStateOf<ImportedPdfEntry?>(null) }
            var scanPageLimit by rememberSaveable { mutableIntStateOf(50) }
            var scanCleanup by rememberSaveable { mutableStateOf(true) }
            var scanGallery by rememberSaveable { mutableStateOf(true) }
            val documentScanner =
                rememberDocumentScanner(
                    pageLimit = scanPageLimit,
                    enhancedCleanup = scanCleanup,
                    allowGallery = scanGallery,
                    blocked = importing || exporting,
                    active = screen == "main" || screen == "scanner" || screen == "pdfTools",
                    language = p.language,
                    onPdf = { uri, pages ->
                        val returnScreen = screen
                        model.importScan(uri, pages) {
                            selectedTemplate = null
                            awaitingPdf = false
                            pdfAction = null
                            pdfReturnScreen = returnScreen
                            screen = "pdf"
                        }
                    },
                    onError = { model.error.value = it },
                )
            val wordPicker =
                rememberLauncherForActivityResult(FilteredDocumentContract()) { uri ->
                    uri?.let { model.offerImport(it, ImportFormat.WORD) }
                }
            val excelPicker =
                rememberLauncherForActivityResult(FilteredDocumentContract()) { uri ->
                    uri?.let { model.offerImport(it, ImportFormat.EXCEL) }
                }
            var requestedPageTool by rememberSaveable { mutableStateOf(PdfPageTool.Manage) }
            var activePageTool by remember { mutableStateOf(PdfPageTool.Manage) }
            val pdfPicker =
                rememberLauncherForActivityResult(FilteredDocumentContract()) { uri ->
                    if (uri == null) { requestedPageTool = PdfPageTool.Manage }
                    else model.offerImport(uri, ImportFormat.PDF)
                }
            val compressPicker = rememberLauncherForActivityResult(FilteredDocumentContract()) { uri ->
                uri?.let {
                    model.compressPdf(it) {
                        pdfAction = null
                        pdfReturnScreen = "pdfTools"
                        screen = "pdf"
                    }
                }
            }
            var mergeFiles by rememberSaveable { mutableStateOf(arrayListOf<android.net.Uri>()) }
            val mergePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                if (uris.isNotEmpty()) {
                    if (uris.size in 2..12) mergeFiles = ArrayList(uris)
                    else model.error.value = "Choose 2–12 PDF files."
                }
            }
            val anyPicker =
                rememberLauncherForActivityResult(FilteredDocumentContract()) { uri ->
                    uri?.let { model.offerImport(it) }
                }
            val homeImagePicker =
                rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri
                    ->
                    uri?.let {
                        model.importImageDocument(it) {
                            selectedTemplate = null
                            editorReview = true
                            screen = "editor"
                        }
                    }
                }
            val openImage = {
                homeImagePicker.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            }
            val openExcel = { excelPicker.launch(ImportFormat.EXCEL.mimeTypes) }
            val openPdf = { pdfPicker.launch(ImportFormat.PDF.mimeTypes) }
            val openWord = { wordPicker.launch(ImportFormat.WORD.mimeTypes) }
            val openTool: (() -> Unit) -> Unit = { action ->
                val activity = context as? android.app.Activity
                if (activity == null) action() else ads.beforePdfTool(activity, action)
            }
            val spreadsheet by model.spreadsheet.collectAsStateWithLifecycle()
            var spreadsheetReturn by rememberSaveable { mutableStateOf("main") }
            LaunchedEffect(pendingImport, p.onboarded) {
                if (p.onboarded)
                    pendingImport?.let { request ->
                        if (request.format == ImportFormat.EXCEL) {
                            val origin = if (screen == "pdfTools") "pdfTools" else "main"
                            model.openSpreadsheet(request) {
                                offerDefaultViewerPrompt(request.external)
                                spreadsheetReturn = origin
                                screen = "spreadsheet"
                            }
                            return@let
                        }
                        val pageTool = requestedPageTool
                        requestedPageTool = PdfPageTool.Manage
                        model.importFile(request) { isPdf ->
                            offerDefaultViewerPrompt(request.external)
                            selectedTemplate = null
                            if (isPdf) {
                                externalPdfPath = if (request.external) model.pdf.value?.file?.path else null
                                awaitingPdf = false
                                pdfAction = null
                                pdfReturnScreen = if (screen == "pdfTools") "pdfTools" else "main"
                                screen = "pdf"
                                if (pageTool != PdfPageTool.Manage) {
                                    activePageTool = pageTool
                                    pdfPagesDialog = model.viewingImportedPdf.value
                                }
                            } else {
                                importedEditorId = model.current.value?.id
                                editorReview = true
                                screen = "editor"
                            }
                        }
                    }
            }

            BackHandler(enabled = screen == "main" && tab != "home") {
                tab = "home"
                search = ""
            }

            LaunchedEffect(pdf, exporting) {
                if (awaitingPdf && pdf != null && !exporting) {
                    screen = "pdf"
                    awaitingPdf = false
                }
            }
            LaunchedEffect(current) { if (screen == "editor" && current == null) screen = "main" }
            if (!p.onboarded) {
                Onboarding(ads, p.language) { model.preferences(p.copy(onboarded = true)) }
                return@LekhakTheme
            }
            if (screen == "scanner") {
                ScannerScreen(
                    pageLimit = scanPageLimit,
                    onPageLimit = { scanPageLimit = it },
                    enhancedCleanup = scanCleanup,
                    onCleanup = { scanCleanup = it },
                    allowGallery = scanGallery,
                    onGallery = { scanGallery = it },
                    preparing = documentScanner.busy || importing,
                    available = documentScanner.available,
                    onBack = {
                        documentScanner.cancelPreparation()
                        screen = "main"
                    },
                    onScan = documentScanner.start,
                    onImportPdf = openPdf,
                )
            } else if (screen == "spreadsheet" && spreadsheet != null) {
                SpreadsheetScreen(
                    book = checkNotNull(spreadsheet),
                    onChange = { model.spreadsheet.value = it },
                    onBack = { screen = spreadsheetReturn; model.spreadsheet.value = null },
                    onExport = { action ->
                        spreadsheet?.let { book ->
                            if (model.exportSpreadsheet(book)) {
                                pdfAction = action
                                pdfReturnScreen = "spreadsheet"
                                awaitingPdf = true
                            }
                        }
                    },
                )
            } else if (screen == "pdfTools") {
                PdfToolsScreen(
                    busy = importing || exporting,
                    onBack = { screen = "main" },
                    onOpenPdf = openPdf,
                    onSplit = { openTool { requestedPageTool = PdfPageTool.Split; openPdf() } },
                    onRemove = { openTool { requestedPageTool = PdfPageTool.Remove; openPdf() } },
                    banner = { AdBanner(ads, language = p.language) },
                    onMerge = { openTool { mergePicker.launch(ImportFormat.PDF.mimeTypes) } },
                    onCompress = { openTool { compressPicker.launch(ImportFormat.PDF.mimeTypes) } },
                    onScan = documentScanner.start,
                    onWord = openWord,
                    onExcel = openExcel,
                    onImage = openImage,
                )
            } else if (screen == "editor" && current != null) {
                EditorScreen(
                    model,
                    current!!,
                    p,
                    onBack = { screen = "main" },
                    onSetup = { pageSetup = true },
                    onDelete = {
                        current?.let { doc ->
                            delete = DocumentEntry(doc.id, doc.title, doc.createdAt, doc.modifiedAt, doc.isDraft)
                        }
                    },
                    review = editorReview,
                    importedDocument = current?.id == importedEditorId,
                    onReview = { editorReview = it },
                    onExport = { action ->
                        if (model.export()) {
                            pdfAction = action
                            pdfReturnScreen = "editor"
                            awaitingPdf = true
                        }
                    },
                )
            } else if (screen == "pdf" && pdf != null) {
                PdfScreen(
                    pdf!!,
                    onEditText =
                        viewingImportedPdf?.let { entry ->
                            {
                                model.editPdfText(entry) {
                                    editorReview = true
                                    screen = "editor"
                                }
                            }
                        },
                    onManagePages = viewingImportedPdf?.let { entry -> { activePageTool = PdfPageTool.Manage; pdfPagesDialog = entry } },
                    externalReader = viewingImportedPdf != null || pdf!!.file.path == externalPdfPath,
                    onSavedExit = { resume ->
                        val activity = context as? android.app.Activity
                        if (activity != null && pdf!!.file.path != externalPdfPath) {
                            ads.afterSavedPdf(activity, resume)
                        } else {
                            resume()
                        }
                    },
                    initialAction = pdfAction,
                    onActionConsumed = { pdfAction = null },
                    onBack = {
                        screen =
                            if (pdfReturnScreen == "scanner") "scanner"
                            else if (pdfReturnScreen == "pdfTools") "pdfTools"
                            else if (pdfReturnScreen == "spreadsheet" && spreadsheet != null) "spreadsheet"
                            else if (pdfReturnScreen == "editor" && current != null) "editor"
                            else "main"
                    },
                )
            } else if (selectedTemplate != null) {
                TemplatePreview(
                    Templates.all.first { it.id == selectedTemplate },
                    onBack = { selectedTemplate = null },
                ) { template, fields ->
                    model.create(template, fields) {
                        selectedTemplate = null
                        screen = "editor"
                    }
                }
            } else {
                val librarySort = p.documentSort
                val locale = java.util.Locale.forLanguageTag(p.language)
                val libraryDocs = remember(docs, pendingLibraryDeletes.toList()) {
                    docs.filterNot { it.id in pendingLibraryDeletes }
                }
                val visible = remember(libraryDocs, search, librarySort, locale) {
                    val collator = java.text.Collator.getInstance(locale)
                    val matching = libraryDocs.filter { it.title.contains(search, true) }
                    when (librarySort) {
                        "name" -> matching.sortedWith { a, b -> collator.compare(a.title, b.title) }
                        "created" -> matching.sortedByDescending { it.createdAt }
                        else -> matching.sortedByDescending { it.modifiedAt }
                    }
                }
                HomeColorTheme(
                    enabled = tab == "home" || tab == "documents" || tab == "settings",
                    darkTheme = AppTheme.colors == DarkColors,
                ) {
                Scaffold(
                    modifier =
                        if (tab == "home" || tab == "documents" || tab == "settings") Modifier.fillMaxSize().background(homeBackdrop())
                        else Modifier.fillMaxSize(),
                    containerColor =
                        if (tab == "home" || tab == "documents" || tab == "settings") androidx.compose.ui.graphics.Color.Transparent
                        else MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                        if (tab == "home") {
                            Column {
                            HomeHeader(
                                query = search,
                                onQuery = { search = it },
                                unread = homeUnread,
                                onUpdates = {
                                    homeUpdates = true
                                    homeUnread = false
                                },
                                onSettings = {
                                    tab = "settings"
                                    search = ""
                                },
                                onSearch = {
                                    homeSearchVisible = true
                                },
                            )
                            AnimatedVisibility(
                                visible = homeSearchVisible,
                                enter = expandVertically(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)) + fadeIn(tween(140)),
                                exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(),
                            ) {
                                TextField(
                                    value = search,
                                    onValueChange = { search = it },
                                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 10.dp).focusRequester(homeSearchFocus),
                                    placeholder = { Text(tr("Find documents…", "दस्तावेज़ खोजें…")) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(28.dp),
                                    leadingIcon = {
                                        IconButton(onClick = { closeHomeSearch() }) {
                                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Close search", "खोज बंद करें"))
                                        }
                                    },
                                    trailingIcon = {
                                        if (search.isNotEmpty()) IconButton(onClick = { search = "" }) {
                                            Icon(Icons.Rounded.Close, tr("Clear search", "खोज मिटाएँ"))
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    colors = TextFieldDefaults.colors(
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    ),
                                )
                            }
                            }
                        }
                    },
                    bottomBar = {
                        Column {
                            if (tab == "documents" && !importing && !exporting) {
                                AdBanner(
                                    ads,
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    p.language,
                                )
                            }
                            HomeNavigation(
                                tab,
                                onScan = documentScanner.start,
                                onPdf = { screen = "pdfTools" },
                                onAi = {
                                    android.widget.Toast.makeText(
                                            context,
                                            "AI Tools — coming soon",
                                            android.widget.Toast.LENGTH_SHORT,
                                        )
                                        .show()
                                },
                            ) { selected ->
                                tab = selected
                                search = ""
                            }
                        }
                    },
                    snackbarHost = { SnackbarHost(librarySnackbar) },
                ) { padding ->
                    if (tab == "documents") {
                        val pdfs = remember(importedPdfs, search, librarySort, locale) {
                            val collator = java.text.Collator.getInstance(locale)
                            val matching = importedPdfs.filter { it.title.contains(search, true) }
                            when (librarySort) {
                                "name" -> matching.sortedWith { a, b -> collator.compare(a.title, b.title) }
                                "created" -> matching.sortedByDescending { it.createdAt }
                                else -> matching.sortedByDescending { it.modifiedAt }
                            }
                        }
                        DocumentsLibrary(
                            modifier = Modifier.fillMaxSize().padding(padding).clipToBounds(),
                            docs = visible,
                            pdfs = pdfs,
                            total = libraryDocs.size + importedPdfs.size,
                            query = search,
                            onQuery = { search = it },
                            sort = librarySort,
                            onSort = { model.libraryPreferences(sort = it) },
                            grid = p.documentGrid,
                            onGrid = { model.libraryPreferences(grid = it) },
                            language = p.language,
                            importing = importing,
                            onImport = { anyPicker.launch(ImportFormat.allMimeTypes) },
                            onCreate = { model.create { screen = "editor" } },
                            onOpen = { doc -> model.open(doc.id) { screen = "editor" } },
                            onExport = { doc, action ->
                                model.open(doc.id) {
                                    if (model.export()) {
                                        pdfAction = action
                                        pdfReturnScreen = "main"
                                        awaitingPdf = true
                                    }
                                }
                            },
                            onRename = { rename = it },
                            onDuplicate = model::duplicate,
                            onDelete = { delete = it },
                            pdfCard = { entry ->
                                ImportedPdfCard(
                                    entry,
                                    onOpen = {
                                        model.openImportedPdf(entry) {
                                            pdfAction = null
                                            pdfReturnScreen = "main"
                                            screen = "pdf"
                                        }
                                    },
                                    onShare = {
                                        model.openImportedPdf(entry) {
                                            pdfAction = "share"
                                            pdfReturnScreen = "main"
                                            screen = "pdf"
                                        }
                                    },
                                    onRename = { model.renameImportedPdf(entry, it) },
                                    onDuplicate = { model.duplicateImportedPdf(entry) },
                                    onDelete = { model.deleteImportedPdf(entry) },
                                )
                            },
                        )
                    } else
                    LazyColumn(
                        state = when (tab) { "settings" -> settingsListState; else -> homeListState },
                        modifier = if (tab == "settings" || tab == "documents" || tab == "home") Modifier.fillMaxSize().then(if (tab == "home") Modifier.nestedScroll(homeSearchPull) else Modifier).padding(
                            top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()
                        ).then(if (tab == "settings") Modifier.statusBarsPadding() else Modifier).clipToBounds() else Modifier.fillMaxSize().nestedScroll(homeSearchPull),
                        contentPadding =
                            PaddingValues(
                                start = if (tab == "home") 0.dp else 20.dp,
                                end = if (tab == "home") 0.dp else 20.dp,
                                top = if (tab == "home") 6.dp else if (tab == "settings" || tab == "documents") 8.dp else padding.calculateTopPadding() + 8.dp,
                                bottom = if (tab == "settings" || tab == "home") 24.dp else if (tab == "documents") 92.dp else padding.calculateBottomPadding() + 92.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (tab == "home") {
                            if (search.isBlank())
                                insetItem(key = "home-create", contentType = "create") {
                                    HomeCreateActions(
                                        importing = importing,
                                        onCreate = { model.create { screen = "editor" } },
                                        onImport = { anyPicker.launch(ImportFormat.allMimeTypes) },
                                        onGallery = openImage,
                                        onMerge = { openTool { mergePicker.launch(ImportFormat.PDF.mimeTypes) } },
                                        onRemove = { openTool { requestedPageTool = PdfPageTool.Remove; openPdf() } },
                                        onCompress = { openTool { compressPicker.launch(ImportFormat.PDF.mimeTypes) } },
                                        onSplit = { openTool { requestedPageTool = PdfPageTool.Split; openPdf() } },
                                    )
                                }
                            val homeDocs = if (search.isBlank()) libraryDocs.take(4) else visible
                            val homePdfs =
                                importedPdfs
                                    .filter { it.title.contains(search, true) }
                                    .let { if (search.isBlank()) it.take(3) else it }
                            if (search.isBlank())
                                insetItem(key = "home-recent", contentType = "recent") {
                                    HomeRecentGroup(
                                        homeDocs,
                                        afterThirdDocument = {
                                            if (!importing && !exporting) AdBanner(ads, language = p.language, inline = true)
                                        },
                                        onOpen = { doc ->
                                            model.open(doc.id) { screen = "editor" }
                                        },
                                        onPdf = { doc ->
                                            model.open(doc.id) {
                                                if (model.export()) {
                                                    pdfAction = null
                                                    pdfReturnScreen = "main"
                                                    awaitingPdf = true
                                                }
                                            }
                                        },
                                        onShare = { doc ->
                                            model.open(doc.id) {
                                                if (model.export()) {
                                                    pdfAction = "share"
                                                    pdfReturnScreen = "main"
                                                    awaitingPdf = true
                                                }
                                            }
                                        },
                                        onRename = { rename = it },
                                        onDuplicate = model::duplicate,
                                        onDelete = { delete = it },
                                        onViewAll = { tab = "documents" },
                                    )
                                }
                            if (search.isNotBlank()) {
                                insetItem {
                                    SectionHeading(
                                        tr("Search results", "खोज के नतीजे"),
                                        tr("View all", "सभी देखें"),
                                    ) {
                                        tab = "documents"
                                    }
                                }
                                if (homeDocs.isEmpty() && homePdfs.isEmpty())
                                    insetItem { EmptyState(true) }
                                items(homeDocs, key = { it.id }) { doc ->
                                    DocumentCard(
                                        doc,
                                        { model.open(doc.id) { screen = "editor" } },
                                        {
                                            model.open(doc.id) {
                                                if (model.export()) {
                                                    pdfAction = "share"
                                                    pdfReturnScreen = "main"
                                                    awaitingPdf = true
                                                }
                                            }
                                        },
                                        { rename = doc },
                                        { model.duplicate(doc) },
                                        { delete = doc },
                                        Modifier.padding(horizontal = 20.dp),
                                    )
                                }
                            }
                            if (homePdfs.isNotEmpty()) {
                                insetItem {
                                    SectionHeading(
                                        tr("Saved PDFs", "सेव की गई PDF"),
                                        tr("View all", "सभी देखें"),
                                    ) {
                                        tab = "documents"
                                    }
                                }
                                items(homePdfs, key = { "pdf-${it.id}" }) { entry ->
                                    ImportedPdfCard(
                                        entry,
                                        Modifier.padding(horizontal = 20.dp),
                                        onOpen = {
                                            model.openImportedPdf(entry) {
                                                pdfAction = null
                                                pdfReturnScreen = "main"
                                                screen = "pdf"
                                            }
                                        },
                                        onShare = {
                                            model.openImportedPdf(entry) {
                                                pdfAction = "share"
                                                pdfReturnScreen = "main"
                                                screen = "pdf"
                                            }
                                        },
                                        onRename = { model.renameImportedPdf(entry, it) },
                                        onDuplicate = { model.duplicateImportedPdf(entry) },
                                        onDelete = { model.deleteImportedPdf(entry) },
                                    )
                                }
                            }
                            if (search.isBlank())
                                insetItem(key = "home-templates", contentType = "templates") {
                                    HomeTemplateShelf(
                                        { model.create { screen = "editor" } },
                                        { selectedTemplate = it.id },
                                        { tab = "templates" },
                                    )
                                }
                        }
                        if (tab == "templates") {
                            val filteredTemplates =
                                Templates.all.filter {
                                    (it.name + it.category).contains(search, true)
                                }
                            item {
                                Text(
                                    tr("A head start, every time.", "हर बार एक अच्छी शुरुआत।"),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            item {
                                Text(
                                    tr(
                                        "16 editable templates • Make them yours",
                                        "16 संपादन योग्य टेम्पलेट • अपने अनुसार बदलें",
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            item { SearchBox(search, { search = it }, isTemplates = true) }
                            if (filteredTemplates.isEmpty()) {
                                item { EmptyState(searching = true) }
                            }
                            items(filteredTemplates.chunked(2)) { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    row.forEach { template ->
                                        TemplateCard(template, Modifier.weight(1f)) {
                                            selectedTemplate = template.id
                                        }
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                        if (tab == "settings") {
                            item { SettingsWelcome() }
                            item {
                                SettingsCard(tr("Preferences", "पसंद")) {
                                    CompactSettingsRow(Icons.Outlined.Language, tr("App language", "ऐप की भाषा")) {
                                        SettingsChoices(
                                            listOf("en" to "English", "hi" to "हिंदी"),
                                            p.language,
                                            onSelect = { model.preferences(p.copy(language = it)) },
                                        )
                                    }
                                    HorizontalDivider(color = AppTheme.colors.line)
                                    CompactSettingsRow(Icons.Outlined.Palette, tr("Appearance", "थीम")) {
                                        SettingsChoices(
                                            listOf(
                                                "system" to tr("System", "सिस्टम"),
                                                "light" to tr("Light", "लाइट"),
                                                "dark" to tr("Dark", "डार्क"),
                                            ),
                                            p.theme,
                                            icons = listOf(Icons.Outlined.PhoneAndroid, Icons.Outlined.LightMode, Icons.Outlined.DarkMode),
                                            onSelect = { model.preferences(p.copy(theme = it)) },
                                        )
                                    }
                                    HorizontalDivider(color = AppTheme.colors.line)
                                    CompactSettingsRow(
                                        Icons.Outlined.DocumentScanner,
                                        tr("Scanner settings", "स्कैनर सेटिंग्स"),
                                        onClick = { screen = "scanner" },
                                    )
                                }
                            }
                            item {
                                SettingsCard(tr("Documents", "दस्तावेज़")) {
                                    CompactSettingsRow(
                                        Icons.Outlined.Straighten,
                                        tr("Page setup", "पेज सेटअप"),
                                        "A4 · ${p.defaults.fontFamily} · ${p.defaults.fontSize} pt · ${p.defaults.lineHeight}×",
                                        onClick = { defaultsSetup = true },
                                    )
                                    HorizontalDivider(color = AppTheme.colors.line)
                                    CompactSettingsRow(
                                        Icons.Outlined.Save,
                                        tr("Autosave", "अपने आप सहेजें"),
                                        tr("Draft recovery is always on", "ड्राफ़्ट रिकवरी हमेशा चालू रहती है"),
                                    ) {
                                        Switch(
                                            checked = p.autosave,
                                            onCheckedChange = { model.preferences(p.copy(autosave = it)) },
                                            colors = SwitchDefaults.colors(
                                                checkedTrackColor = AppTheme.colors.accent,
                                                checkedThumbColor = AppTheme.colors.onAccent,
                                            ),
                                        )
                                    }
                                    HorizontalDivider(color = AppTheme.colors.line)
                                    CompactSettingsRow(
                                        Icons.Outlined.OpenInNew,
                                        tr("Make default app", "डिफ़ॉल्ट ऐप बनाएँ"),
                                        tr("Open PDFs and Office files with this app", "इस ऐप में PDF और Office फ़ाइलें खोलें"),
                                        onClick = { defaultViewerHelp = true },
                                    )
                                    HorizontalDivider(color = AppTheme.colors.line)
                                    CompactSettingsRow(
                                        Icons.Outlined.FolderOpen,
                                        tr("Manage documents", "दस्तावेज़ प्रबंधन"),
                                        onClick = { search = ""; tab = "documents" },
                                    )
                                    if (pdf != null) {
                                        HorizontalDivider(color = AppTheme.colors.line)
                                        CompactSettingsRow(
                                            Icons.Outlined.PictureAsPdf,
                                            tr("Open latest exported PDF", "आखिरी निर्यात किया PDF खोलें"),
                                            onClick = { screen = "pdf" },
                                        )
                                    }
                                }
                            }
                            item {
                                SettingsCard(tr("About", "ऐप के बारे में")) {
                                    CompactSettingsRow(
                                        Icons.Outlined.Shield,
                                        tr("Privacy policy", "गोपनीयता नीति"),
                                        onClick = { info = "privacy" },
                                    )
                                    HorizontalDivider(color = AppTheme.colors.line)
                                    CompactSettingsRow(
                                        Icons.Outlined.Support,
                                        tr("Contact support", "सहायता से संपर्क करें"),
                                        onClick = {
                                            val intent = android.content.Intent(
                                                android.content.Intent.ACTION_SENDTO,
                                                android.net.Uri.fromParts("mailto", "AMAKUNAR.IN@GMAIL.COM", null),
                                            ).putExtra(android.content.Intent.EXTRA_SUBJECT, "A4 Paper Typing support")
                                            try {
                                                context.startActivity(intent)
                                            } catch (_: android.content.ActivityNotFoundException) {
                                                model.error.value = "Email: AMAKUNAR.IN@GMAIL.COM"
                                            }
                                        },
                                    )
                                    HorizontalDivider(color = AppTheme.colors.line)
                                    CompactSettingsRow(
                                        Icons.Outlined.Article,
                                        tr("App info and licenses", "ऐप जानकारी और लाइसेंस"),
                                        onClick = { info = "about" },
                                    )
                                }
                            }
                            if (BuildConfig.ADS_ENABLED) item {
                                SettingsCard(tr("Advertising", "विज्ञापन")) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text(
                                            if (BuildConfig.ADS_DEMO_MODE)
                                                tr(
                                                    "Test ads only · no earnings",
                                                    "केवल टेस्ट विज्ञापन · कमाई नहीं",
                                                )
                                            else
                                                tr(
                                                    "Supported by advertising",
                                                    "विज्ञापनों से समर्थित",
                                                ),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        if (adsState.privacyOptionsRequired) {
                                            TextButton(
                                                enabled = !adsState.busy,
                                                onClick = {
                                                    (context as? android.app.Activity)?.let(
                                                        ads::showPrivacyOptions
                                                    )
                                                },
                                            ) {
                                                Text(
                                                    tr(
                                                        "Ad privacy choices",
                                                        "विज्ञापन गोपनीयता विकल्प",
                                                    )
                                                )
                                            }
                                        }
                                        if (adsState.error != null) {
                                            Text(
                                                tr(
                                                    "Ads are unavailable. You can keep using all document tools.",
                                                    "विज्ञापन उपलब्ध नहीं हैं। दस्तावेज़ के सभी टूल इस्तेमाल कर सकते हैं।",
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                            TextButton(
                                                enabled = !adsState.busy,
                                                onClick = {
                                                    (context as? android.app.Activity)?.let(
                                                        ads::start
                                                    )
                                                },
                                            ) {
                                                Text(
                                                    tr("Retry ad setup", "विज्ञापन सेटअप फिर करें")
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
            if (homeUpdates)
                AlertDialog(
                    onDismissRequest = { homeUpdates = false },
                    title = { Text(tr("App updates", "ऐप अपडेट")) },
                    text = {
                        Text(
                            tr(
                                "Your new Home brings writing, Word import, recent documents and templates together. PDF, spreadsheet and image import will be added in a later update.",
                                "नए Home पर लिखना, Word फ़ाइल खोलना, हाल के दस्तावेज़ और टेम्पलेट एक साथ हैं। PDF, spreadsheet और image import आने वाले अपडेट में जुड़ेंगे।",
                            )
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { homeUpdates = false }) {
                            Text(tr("Got it", "ठीक है"))
                        }
                    },
                )
            if (searchPopup) {
                SearchPopup(
                    docs = docs,
                    onDismiss = { searchPopup = false },
                    onOpenDoc = { doc ->
                        searchPopup = false
                        model.open(doc.id) { screen = "editor" }
                    },
                    onShareDoc = { doc ->
                        searchPopup = false
                        model.open(doc.id) {
                            if (model.export()) {
                                pdfAction = "share"
                                pdfReturnScreen = "main"
                                awaitingPdf = true
                            }
                        }
                    },
                    onRenameDoc = { doc -> rename = doc },
                    onDuplicateDoc = { doc -> model.duplicate(doc) },
                    onDeleteDoc = { doc -> delete = doc },
                    onSelectTemplate = { template ->
                        searchPopup = false
                        selectedTemplate = template.id
                    },
                )
            }
            if (pageSetup || defaultsSetup) {
                A4Theme(darkTheme = AppTheme.colors == DarkColors) {
                    if (pageSetup && current != null)
                        PageSetupDialog(current!!.page, onDismiss = { pageSetup = false }) {
                            model.page(it)
                            pageSetup = false
                        }
                    if (defaultsSetup)
                        PageSetupDialog(p.defaults, onDismiss = { defaultsSetup = false }) {
                            model.preferences(p.copy(defaults = it))
                            defaultsSetup = false
                        }
                }
            }
            rename?.let { doc ->
                var value by remember(doc.id) { mutableStateOf(doc.title) }
                AlertDialog(
                    onDismissRequest = { rename = null },
                    title = { Text(tr("Rename document", "नाम बदलें")) },
                    text = {
                        OutlinedTextField(
                            value,
                            { value = it },
                            label = { Text(tr("Title", "शीर्षक")) },
                            singleLine = true,
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                model.rename(doc, value)
                                rename = null
                            }
                        ) {
                            Text(tr("Save", "सहेजें"))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { rename = null }) { Text(tr("Cancel", "रद्द करें")) }
                    },
                )
            }
            delete?.let { doc ->
                AlertDialog(
                    onDismissRequest = { delete = null },
                    title = { Text(tr("Delete document?", "दस्तावेज़ हटाएँ?")) },
                    text = {
                        Text(
                            tr(
                                "“${doc.title}” will be removed from this device. Previously exported PDFs are kept.",
                                "“${doc.title}” इस डिवाइस से हट जाएगा। निर्यात किए गए PDF बने रहेंगे।",
                            )
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (screen == "editor" && current?.id == doc.id) {
                                    screen = "main"
                                    editorReview = false
                                }
                                if (tab == "documents" && screen == "main" && doc.id !in pendingLibraryDeletes) {
                                    pendingLibraryDeletes.add(doc.id)
                                    libraryScope.launch {
                                        val result = librarySnackbar.showSnackbar(
                                            removedMessage, undoLabel, duration = SnackbarDuration.Long,
                                        )
                                        if (result != SnackbarResult.ActionPerformed) model.delete(doc)
                                        pendingLibraryDeletes.remove(doc.id)
                                    }
                                } else {
                                    model.delete(doc)
                                }
                                delete = null
                            }
                        ) {
                            Text(tr("Delete", "हटाएँ"), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { delete = null }) { Text(tr("Cancel", "रद्द करें")) }
                    },
                )
            }
            if (exporting)
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text(tr("Preparing your PDF", "आपका PDF बन रहा है")) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(
                                tr(
                                    "Laying out pages and checking the actual PDF. This continues in the background.",
                                    "पेज तैयार हो रहे हैं और PDF जाँचा जा रहा है। यह पृष्ठभूमि में जारी रहेगा।",
                                )
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                awaitingPdf = false
                                model.cancelExport()
                            }
                        ) {
                            Text(tr("Cancel export", "निर्यात रद्द करें"))
                        }
                    },
                )
            if (mergeFiles.isNotEmpty()) {
                PdfMergeDialog(
                    files = mergeFiles,
                    onOrder = { mergeFiles = ArrayList(it) },
                    onDismiss = { mergeFiles = arrayListOf() },
                    onMerge = {
                        val files = mergeFiles.toList()
                        mergeFiles = arrayListOf()
                        model.mergePdfs(files) {
                            pdfAction = null
                            pdfReturnScreen = "pdfTools"
                            screen = "pdf"
                        }
                    },
                )
            }
            pdfPagesDialog?.let { entry ->
                PdfPagesDialog(entry, tool = activePageTool, onDismiss = { pdfPagesDialog = null }) { ranges, rotation ->
                    pdfPagesDialog = null
                    model.copyPdfPages(entry, ranges, rotation) {
                        pdfAction = null
                        if (pdfReturnScreen != "pdfTools") pdfReturnScreen = "main"
                        screen = "pdf"
                    }
                }
            }
            if (importing)
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text(tr("Opening $importLabel", "$importLabel खुल रहा है")) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(
                                tr(
                                    "Processing the selected file on your device…",
                                    "चुनी हुई फ़ाइल आपके फोन पर पढ़ी जा रही है…",
                                )
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = model::cancelImport) {
                            Text(tr("Cancel", "रद्द करें"))
                        }
                    },
                    properties =
                        androidx.compose.ui.window.DialogProperties(
                            dismissOnBackPress = false,
                            dismissOnClickOutside = false,
                        ),
                )
            importNotice?.let { notices ->
                AlertDialog(
                    onDismissRequest = { model.importNotice.value = null },
                    title = { Text(tr("Editable copy ready", "संपादन के लिए कॉपी तैयार है")) },
                    text = {
                        Column(
                            Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                tr(
                                    "Your original file is unchanged. You can edit this copy, then save or share it as PDF.",
                                    "मूल फ़ाइल नहीं बदली है। इस कॉपी को संपादित करके PDF में सेव या शेयर कर सकते हैं।",
                                )
                            )
                            Text(
                                tr(
                                    "Check the page preview before exporting. Imported fonts, spacing and page breaks can differ from the original.",
                                    "PDF बनाने से पहले पेज का पूर्वावलोकन देखें। फ़ॉन्ट, दूरी और पेज मूल फ़ाइल से अलग हो सकते हैं।",
                                )
                            )
                            notices.forEach {
                                Text("• $it", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { model.importNotice.value = null }) {
                            Text(tr("Continue", "आगे बढ़ें"))
                        }
                    },
                )
            }
            error?.let { message ->
                AlertDialog(
                    onDismissRequest = { model.error.value = null },
                    title = { Text(tr("Something needs attention", "ध्यान दें")) },
                    text = { Text(message) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                model.error.value = null
                                awaitingPdf = false
                            }
                        ) {
                            Text(tr("OK", "ठीक है"))
                        }
                    },
                )
            }
            if (defaultViewerPrompt && !importing && importNotice == null) {
                AlertDialog(
                    onDismissRequest = { defaultViewerPrompt = false },
                    title = { Text(tr("Open files here by default?", "फ़ाइलें हमेशा इस ऐप में खोलें?")) },
                    text = { Text(tr(
                        "You can choose A4 Paper Typing in Android for supported file types.",
                        "समर्थित फ़ाइलों के लिए Android में A4 Paper Typing चुन सकते हैं।",
                    )) },
                    confirmButton = {
                        TextButton(onClick = {
                            defaultViewerPrompt = false
                            defaultViewerHelp = true
                        }) { Text(tr("How to set it", "सेट करने का तरीका")) }
                    },
                    dismissButton = {
                        TextButton(onClick = { defaultViewerPrompt = false }) {
                            Text(tr("Not now", "अभी नहीं"))
                        }
                    },
                )
            }
            if (defaultViewerHelp) {
                AlertDialog(
                    onDismissRequest = { defaultViewerHelp = false },
                    title = { Text(tr("Make default app", "डिफ़ॉल्ट ऐप बनाएँ")) },
                    text = {
                        Text(tr(
                            "In Files, open a PDF, DOCX, XLSX, XLS or CSV. In Open with, choose A4 Paper Typing and tap Always if Android offers it. Repeat for each file type. If another app opens directly, clear its defaults in Android Settings first.",
                            "Files में PDF, DOCX, XLSX, XLS या CSV खोलें। Open with में A4 Paper Typing चुनें और अगर Always दिखे तो उसे दबाएँ। हर फ़ाइल प्रकार के लिए यही करें। अगर कोई दूसरा ऐप सीधे खुलता है, तो Android Settings में पहले उसके defaults हटाएँ।",
                        ))
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_SETTINGS)
                            runCatching { context.startActivity(intent) }.onFailure {
                                model.error.value = "Open Android Settings and choose Apps."
                            }
                            defaultViewerHelp = false
                        }) { Text(tr("Open app settings", "ऐप सेटिंग्स खोलें")) }
                    },
                    dismissButton = {
                        TextButton(onClick = { defaultViewerHelp = false }) { Text(tr("Close", "बंद करें")) }
                    },
                )
            }
            info?.let { kind ->
                AlertDialog(
                    onDismissRequest = { info = null },
                    title = {
                        Text(
                            if (kind == "privacy") tr("Privacy policy", "गोपनीयता नीति")
                            else
                                "A4 Paper Typing ${com.a4sizepaper.typing.texttopdf.BuildConfig.VERSION_NAME}"
                        )
                    },
                    text = {
                        Text(
                            modifier =
                                Modifier.heightIn(max = 420.dp)
                                    .verticalScroll(rememberScrollState()),
                            text =
                                if (kind == "privacy")
                                    tr(
                                        "A4 Paper Typing — AMAN KUMAR\nUpdated 2 November 2026\nContact: AMAKUNAR.IN@GMAIL.COM\n\nDocuments, selected photos and PDFs are processed on this device. No account is required and we do not upload your documents to our servers. Document editing and PDF export work offline. Ads and privacy messages use the Internet. Google AdMob serves ads, with Meta Audience Network available through mediation when configured. Ad SDKs may collect IP-derived approximate location, device/advertising identifiers, app/ad interactions and diagnostics for advertising, measurement and fraud prevention. Document text, filenames, photos and PDF content are not attached to ad requests. Ad privacy choices are available in Settings where required. Test builds use Google demo ad units and do not earn revenue. Provider policies: https://policies.google.com/privacy and https://www.facebook.com/privacy/policy/. PDF scanning uses Google Play services / ML Kit: initial components may be downloaded, and Google components may process technical usage, device and diagnostic data under Google terms. Scan images and results are processed on-device. We do not receive them.\n\nPhoto Picker grants access only to images you choose. A private resized copy is stored; image metadata is removed. Notification permission is optional and shows PDF export progress.\n\nFiles leave the app when you choose Save as, Share or Print. The selected app, cloud provider or printer handles that copy under its own policy. We do not receive your documents.\n\nScanned PDFs are saved as private copies and can be deleted from Documents. Delete documents within the app. Unused images can be removed after a 24-hour grace period at a later startup. Each document’s latest private PDF is retained; earlier PDFs can be removed after seven days. Clearing app storage or uninstalling removes private files. Copies saved elsewhere must be deleted there. Save important PDFs outside the app before uninstalling.\n\nIf you email support, your email address and message are handled to answer your request. Do not send sensitive documents unless necessary; contact us to request deletion of support correspondence. Advertising providers handle SDK data under their own policies and your applicable privacy choices.",
                                        "A4 Paper Typing — AMAN KUMAR\nअपडेट: 2 नवंबर 2026\nसंपर्क: AMAKUNAR.IN@GMAIL.COM\n\nदस्तावेज़, चुनी हुई तस्वीरें और PDF इसी डिवाइस पर बनते हैं। लॉगिन की ज़रूरत नहीं है और आपके दस्तावेज़ हमारे सर्वर पर अपलोड नहीं होते। दस्तावेज़ संपादन और PDF बनाना ऑफ़लाइन काम करते हैं। विज्ञापन और गोपनीयता संदेश इंटरनेट का उपयोग करते हैं। विज्ञापन Google AdMob से आते हैं; setup होने पर Meta Audience Network भी mediation के ज़रिए विज्ञापन दे सकता है। ये SDK विज्ञापन, माप और धोखाधड़ी रोकने के लिए IP से अनुमानित स्थान, डिवाइस/विज्ञापन पहचान, ऐप/विज्ञापन interactions और diagnostics संभाल सकते हैं। दस्तावेज़ का टेक्स्ट, फ़ाइल नाम, तस्वीर या PDF content विज्ञापन अनुरोध में नहीं भेजा जाता। जहाँ ज़रूरी हो, Settings में विज्ञापन गोपनीयता विकल्प उपलब्ध हैं। टेस्ट बिल्ड में Google demo विज्ञापन हैं; उनसे कमाई नहीं होती। प्रदाता नीतियाँ: https://policies.google.com/privacy और https://www.facebook.com/privacy/policy/. PDF स्कैन के लिए Google Play services / ML Kit का उपयोग होता है। पहली बार components डाउनलोड हो सकते हैं; Google अपने नियमों के अनुसार तकनीकी उपयोग, डिवाइस और diagnostics data संभाल सकता है। स्कैन की तस्वीरें और परिणाम फोन पर process होते हैं; हमें नहीं मिलते।\n\nPhoto Picker से केवल आपकी चुनी हुई तस्वीर की निजी कॉपी बनती है; तस्वीर का मेटाडेटा हटाया जाता है। PDF की प्रगति दिखाने की notification अनुमति वैकल्पिक है।\n\nSave as, Share या Print करने पर चुना हुआ ऐप, क्लाउड प्रदाता या प्रिंटर आपकी कॉपी संभालता है। हमें दस्तावेज़ प्राप्त नहीं होते।\n\nदस्तावेज़ ऐप में हटा सकते हैं। अनुपयोगी तस्वीरें 24 घंटे बाद अगले startup पर साफ हो सकती हैं। हर दस्तावेज़ की नई PDF रहती है; पुराने versions सात दिन बाद साफ हो सकते हैं। ऐप का डेटा साफ करने या uninstall करने पर निजी फ़ाइलें मिटती हैं। बाहर सहेजी कॉपी वहीं से हटाएँ।\n\nसहायता के लिए email करने पर आपका पता और संदेश जवाब देने के लिए उपयोग होते हैं। ज़रूरत के बिना संवेदनशील दस्तावेज़ न भेजें। सहायता-संदेश हटवाने के लिए हमसे संपर्क करें। विज्ञापन प्रदाता SDK data को अपनी नीतियों और लागू गोपनीयता विकल्पों के अनुसार संभालते हैं।",
                                    )
                                else
                                    tr(
                                        "Offline document tools • Ad-supported • No watermark\n\nBuilt with Android Jetpack, Tiptap / ProseMirror (MIT), Noto fonts (SIL OFL), PDFBox, JExcelAPI, Google ML Kit Document Scanner, Google Mobile Ads / UMP and Meta Audience Network. Third-party license texts and provider notices are bundled in assets/licenses.\n\nPDF fidelity depends on the installed Android System WebView. Inspect the actual PDF preview before printing. Templates are editable drafts, not certifications.",
                                        "ऑफ़लाइन दस्तावेज़ टूल • विज्ञापन समर्थित • कोई वॉटरमार्क नहीं\n\nAndroid Jetpack, Tiptap / ProseMirror (MIT) और Noto (SIL OFL) का उपयोग करता है।\n\nप्रिंट करने से पहले असली PDF प्रीव्यू जाँचें। टेम्पलेट संपादन योग्य ड्राफ़्ट हैं, प्रमाणपत्र नहीं।",
                                    ),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { info = null }) { Text(tr("Close", "बंद करें")) }
                    },
                )
            }
        }
    }
}

@Composable
private fun WordImportButton(busy: Boolean, onClick: () -> Unit) {
    FluentOutlinedButton(onClick = onClick, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Rounded.FileOpen, null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(tr("Import a document", "दस्तावेज़ खोलें"), fontWeight = FontWeight.SemiBold)
            Text(
                tr("Word • Excel • PDF", "Word • Excel • PDF"),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Onboarding(ads: AdsController, language: String, onStart: () -> Unit) {
    val colors = AppTheme.colors
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(260)) }
    Surface(Modifier.fillMaxSize(), color = colors.bg) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val showBanner = maxHeight >= 600.dp
            Column(Modifier.fillMaxSize()) {
                if (showBanner) {
                    AdBanner(ads, language = language, modifier = Modifier.padding(top = 12.dp, start = 16.dp, end = 16.dp))
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(com.a4sizepaper.typing.texttopdf.R.drawable.ic_a4_splash),
                        contentDescription = null,
                        modifier = Modifier.size(112.dp).align(Alignment.CenterHorizontally).graphicsLayer {
                            alpha = entrance.value
                            translationY = 10.dp.toPx() * (1f - entrance.value)
                        },
                    )
                    Text("A4 Paper Typing", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = colors.ink)
                    Text(
                        tr("Write, scan and make PDFs", "लिखें, स्कैन करें और PDF बनाएँ"),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.accentText,
                    )
                    Surface(shape = RoundedCornerShape(24.dp), color = colors.surface, border = BorderStroke(1.dp, colors.line)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            listOf(
                                Icons.Rounded.Edit to tr("Type in your language with your phone’s keyboard", "अपने फ़ोन के कीबोर्ड से अपनी भाषा में लिखें"),
                                Icons.Rounded.Description to tr("Start with a ready-made template", "तैयार टेम्पलेट से शुरू करें"),
                                Icons.Rounded.PictureAsPdf to tr("Save and share A4 PDFs", "A4 PDF सहेजें और शेयर करें"),
                            ).forEach { (icon, label) ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(icon, null, Modifier.size(22.dp), tint = colors.accentText)
                                    Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.ink)
                                }
                            }
                        }
                    }
                }
                Button(
                    onClick = onStart,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                ) { Text(tr("Get started", "शुरू करें"), fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
internal fun HomeNavigation(tab: String, onScan: () -> Unit, onAi: () -> Unit, onPdf: () -> Unit, onSelect: (String) -> Unit) {
    Surface(color = AppTheme.colors.surface) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = AppTheme.colors.line)
            Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("home", "documents", "scan", "ai", "pdf").forEach { key ->
                    if (key == "scan") {
                        Column(Modifier.weight(1.1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            FilledIconButton(
                                onClick = onScan,
                                modifier = Modifier.size(56.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = AppTheme.colors.accent,
                                    contentColor = AppTheme.colors.onAccent,
                                ),
                            ) {
                                Icon(androidx.compose.material.icons.Icons.Outlined.PhotoCamera, tr("Scan", "स्कैन"))
                            }
                            Text(tr("Scan", "स्कैन"), color = AppTheme.colors.accentText, fontSize = 11.sp)
                        }
                    } else {
                        val label = when(key) { "home" -> tr("Home", "होम"); "documents" -> tr("Documents", "दस्तावेज़"); "ai" -> "AI Tools"; else -> tr("PDF Tools", "PDF टूल्स") }
                        val icon = when(key) { "home" -> Icons.Outlined.Home; "documents" -> Icons.Outlined.Description; "ai" -> Icons.Outlined.AutoAwesome; else -> Icons.Outlined.PictureAsPdf }
                        val selected = tab == key
                        val tint by animateColorAsState(
                            if (selected) AppTheme.colors.accentText else AppTheme.colors.muted,
                            tween(180),
                            label = "Navigation icon color",
                        )
                        val tileColor by animateColorAsState(
                            if (selected) AppTheme.colors.accentSoft else Color.Transparent,
                            tween(180),
                            label = "Navigation selection",
                        )
                        val iconScale by animateFloatAsState(
                            if (selected) 1.08f else 1f,
                            tween(180),
                            label = "Navigation icon scale",
                        )
                        Column(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).background(tileColor).clickable { when (key) { "ai" -> onAi(); "pdf" -> onPdf(); else -> onSelect(key) } }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(icon, null, Modifier.size(22.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale }, tint = tint)
                            Spacer(Modifier.height(4.dp))
                            Text(label, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = tint, fontWeight = if(tab == key) FontWeight.SemiBold else FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PreviewCursor() {
    val lifecycle = LocalLifecycleOwner.current
    val state by lifecycle.lifecycle.currentStateFlow.collectAsState()
    val alpha =
        if (state.isAtLeast(Lifecycle.State.RESUMED)) {
            val transition = rememberInfiniteTransition(label = "Writing cursor")
            val opacity by
                transition.animateFloat(
                    initialValue = 1f,
                    targetValue = 0f,
                    animationSpec =
                        infiniteRepeatable(
                            animation =
                                keyframes {
                                    durationMillis = 1100
                                    1f at 0
                                    1f at 500
                                    0f at 550
                                    0f at 1050
                                },
                            repeatMode = RepeatMode.Restart,
                        ),
                    label = "Cursor blink",
                )
            opacity
        } else 1f
    Box(Modifier.width(2.dp).height(22.dp).graphicsLayer { this.alpha = alpha }.background(Blue))
}

@Composable
private fun SearchBox(
    value: String,
    onChange: (String) -> Unit,
    isTemplates: Boolean = false,
    modifier: Modifier = Modifier,
) {
    BouncySearchBar(
        query = value,
        onQuery = onChange,
        placeholder =
            if (isTemplates) tr("Find templates…", "टेम्पलेट खोजें…")
            else tr("Find your documents…", "दस्तावेज़ खोजें…"),
        accentColor = MaterialTheme.colorScheme.primary,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchPopup(
    docs: List<DocumentEntry>,
    onDismiss: () -> Unit,
    onOpenDoc: (DocumentEntry) -> Unit,
    onShareDoc: (DocumentEntry) -> Unit,
    onRenameDoc: (DocumentEntry) -> Unit,
    onDuplicateDoc: (DocumentEntry) -> Unit,
    onDeleteDoc: (DocumentEntry) -> Unit,
    onSelectTemplate: (Template) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val matchingDocs =
        remember(docs, query) { docs.filter { it.title.contains(query, ignoreCase = true) } }
    val matchingTemplates =
        remember(query) {
            Templates.all.filter { (it.name + it.category).contains(query, ignoreCase = true) }
        }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties =
            androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize().imePadding(),
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = tr("Back", "वापस"),
                            )
                        }
                    },
                    title = {
                        androidx.compose.foundation.text.BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle =
                                MaterialTheme.typography.titleMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                            decorationBox = { innerTextField ->
                                if (query.isEmpty()) {
                                    Text(
                                        tr(
                                            "Search documents & templates…",
                                            "दस्तावेज़ और टेम्पलेट खोजें…",
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                innerTextField()
                            },
                        )
                    },
                    actions = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = tr("Clear search", "खोज साफ़ करें"),
                                )
                            }
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (matchingDocs.isEmpty() && matchingTemplates.isEmpty()) {
                    item { EmptyState(searching = true) }
                }
                if (matchingDocs.isNotEmpty()) {
                    item {
                        Text(
                            tr("Recent documents", "हाल के दस्तावेज़"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(matchingDocs, key = { "doc-${it.id}" }) { doc ->
                        DocumentCard(
                            doc = doc,
                            onOpen = { onOpenDoc(doc) },
                            onShare = { onShareDoc(doc) },
                            onRename = { onRenameDoc(doc) },
                            onDuplicate = { onDuplicateDoc(doc) },
                            onDelete = { onDeleteDoc(doc) },
                        )
                    }
                }
                if (matchingTemplates.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tr("Templates", "टेम्पलेट"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(matchingTemplates.chunked(2)) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { template ->
                                TemplateCard(template, Modifier.weight(1f)) {
                                    onSelectTemplate(template)
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        TextButton(onClick) { Text(action) }
    }
}

@Composable
internal fun TemplateCard(template: Template, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 0.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Box(
                Modifier.fillMaxWidth()
                    .height(72.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.shapes.small,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                MiniPaper(
                    remember(template.id) { template.filledParagraphs() },
                    Modifier.width(46.dp).height(64.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(template.name, fontWeight = FontWeight.SemiBold, minLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(
                template.category,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun DocumentCard(
    doc: DocumentEntry,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loadPreview = LocalDocumentPreview.current
    val preview by
        produceState<List<String>>(emptyList(), doc.id, doc.modifiedAt) {
            value = loadPreview(doc.id)
        }
    var menu by remember { mutableStateOf(false) }
    val locale =
        if (LocalHindi.current) java.util.Locale.forLanguageTag("hi-IN")
        else java.util.Locale.ENGLISH
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = HomeBlue.copy(alpha = .08f)) {
                Box(Modifier.padding(8.dp)) {
                    MiniPaper(preview, Modifier.width(38.dp).height(54.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    doc.title.ifBlank { tr("Untitled document", "नया दस्तावेज़") },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
                        .format(Date(doc.modifiedAt)) +
                        (if (doc.isDraft) tr(" · Draft", " · ड्राफ़्ट") else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, tr("Share", "शेयर करें"), tint = HomeBlue, modifier = Modifier.size(20.dp)) }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, tr("Document actions", "दस्तावेज़ विकल्प"))
                }
                DropdownMenu(menu, { menu = false }) {
                    listOf(
                            Triple(tr("Rename", "नाम बदलें"), onRename, Icons.Rounded.Edit),
                            Triple(
                                tr("Duplicate", "कॉपी बनाएँ"),
                                onDuplicate,
                                Icons.Rounded.ContentCopy,
                            ),
                            Triple(tr("Delete", "हटाएँ"), onDelete, Icons.Rounded.Delete),
                        )
                        .forEach { (label, action, icon) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                leadingIcon = { Icon(icon, contentDescription = null) },
                                colors =
                                    MenuDefaults.itemColors(
                                        textColor =
                                            if (icon == Icons.Rounded.Delete)
                                                MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurface,
                                        leadingIconColor =
                                            if (icon == Icons.Rounded.Delete)
                                                MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                onClick = {
                                    menu = false
                                    action()
                                },
                            )
                        }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(searching: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.FolderOpen,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (searching) tr("No matching documents", "कोई दस्तावेज़ नहीं मिला")
            else tr("Your first page is waiting", "आपके पहले पेज का इंतज़ार है"),
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            if (searching) tr("Try a different title.", "दूसरा नाम खोजें।")
            else tr("Tap New document to begin.", "शुरू करने के लिए नया दस्तावेज़ चुनें।"),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title.uppercase(java.util.Locale.getDefault()),
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = colors.muted,
            fontWeight = FontWeight.Bold,
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.line),
        ) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun CompactSettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val colors = AppTheme.colors
    val action = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Row(
        Modifier.fillMaxWidth().then(action).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(21.dp), tint = colors.accentText)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = colors.ink)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
        Spacer(Modifier.width(8.dp))
        if (onClick != null) Icon(Icons.Outlined.ChevronRight, null, Modifier.size(19.dp), tint = colors.muted)
        else trailing()
    }
}

@Composable
private fun SettingsWelcome() {
    val colors = AppTheme.colors
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tr("Settings", "सेटिंग्स"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = colors.ink)
            Text("A4 Paper Typing", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
        }
        Surface(shape = CircleShape, color = colors.accentSoft) {
            Text(
                "v${BuildConfig.VERSION_NAME}",
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = colors.accentText,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SettingsChoices(
    options: List<Pair<String, String>>,
    selected: String,
    icons: List<androidx.compose.ui.graphics.vector.ImageVector>? = null,
    onSelect: (String) -> Unit,
) {
    val colors = AppTheme.colors
    Surface(shape = RoundedCornerShape(12.dp), color = colors.bg, border = BorderStroke(1.dp, colors.line)) {
        Row(Modifier.padding(2.dp)) {
            options.forEachIndexed { index, (key, label) ->
                val active = key == selected
                Surface(
                    onClick = { onSelect(key) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (active) colors.accent else Color.Transparent,
                    contentColor = if (active) colors.onAccent else colors.muted,
                ) {
                    Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), contentAlignment = Alignment.Center) {
                        if (icons != null) Icon(icons[index], label, Modifier.size(19.dp))
                        else Text(if (key == "en") "EN" else "हिं", fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.semantics { contentDescription = label })
                    }
                }
            }
        }
    }
}

@Composable
fun ChoiceRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (key, label) ->
            FilterChip(selected == key, { onSelect(key) }, label = { Text(label) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TemplatePreview(
    template: Template,
    onBack: () -> Unit,
    onUse: (Template, Map<String, String>) -> Unit,
) {
    val values = remember(template.id) { mutableStateMapOf<String, String>() }
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(template.name) },
                navigationIcon = {
                    IconButton(onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back", "वापस"))
                    }
                },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                FluentButton(
                    onClick = { onUse(template, values.toMap()) },
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp),
                ) {
                    Text(tr("Use this template", "इस टेम्पलेट का उपयोग करें"))
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Text(template.description, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        template.filledParagraphs(values).forEachIndexed { i, text ->
                            Text(
                                text,
                                color = Color(0xFF172033),
                                fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    tr("Fill in the details (optional)", "जानकारी भरें (वैकल्पिक)"),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            items(template.fields) { field ->
                OutlinedTextField(
                    values[field] ?: "",
                    { values[field] = it },
                    label = { Text(field) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    tr(
                        "Everything remains editable in the document.",
                        "दस्तावेज़ में सभी चीज़ें बदली जा सकती हैं।",
                    )
                )
            }
        }
    }
}

private fun LazyListScope.insetItem(
    key: String? = null,
    contentType: String? = null,
    content: @Composable () -> Unit,
) {
    item(key = key, contentType = contentType) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { content() }
    }
}
