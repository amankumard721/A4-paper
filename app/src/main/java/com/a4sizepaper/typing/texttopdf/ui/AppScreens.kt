package com.a4sizepaper.typing.texttopdf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a4sizepaper.typing.texttopdf.data.*
import com.a4sizepaper.typing.texttopdf.domain.*
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LekhakApp(model: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
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
    LekhakTheme(p.theme, p.language) {
        val docs by model.documents.collectAsStateWithLifecycle()
        val current by model.current.collectAsStateWithLifecycle()
        val error by model.error.collectAsStateWithLifecycle()
        val pdf by model.pdf.collectAsStateWithLifecycle()
        val exporting by model.exporting.collectAsStateWithLifecycle()
        var tab by rememberSaveable { mutableStateOf("home") }
        var screen by rememberSaveable { mutableStateOf("main") }
        var selectedTemplate by rememberSaveable { mutableStateOf<String?>(null) }
        var search by rememberSaveable { mutableStateOf("") }
        var searchPopup by rememberSaveable { mutableStateOf(false) }
        var sort by rememberSaveable { mutableStateOf("modified") }
        var rename by remember { mutableStateOf<DocumentEntry?>(null) }
        var delete by remember { mutableStateOf<DocumentEntry?>(null) }
        var pageSetup by remember { mutableStateOf(false) }
        var defaultsSetup by remember { mutableStateOf(false) }
        var info by remember { mutableStateOf<String?>(null) }
        var awaitingPdf by rememberSaveable { mutableStateOf(false) }
        var pdfReturnScreen by rememberSaveable { mutableStateOf("main") }

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
            Onboarding { model.preferences(p.copy(onboarded = true)) }
            return@LekhakTheme
        }
        if (screen == "editor" && current != null) {
            EditorScreen(
                model,
                current!!,
                p,
                onBack = { screen = "main" },
                onSetup = { pageSetup = true },
                onExport = {
                    if (model.export()) {
                        pdfReturnScreen = "editor"
                        awaitingPdf = true
                    }
                },
            )
        } else if (screen == "pdf" && pdf != null) {
            PdfScreen(
                pdf!!,
                onBack = {
                    screen =
                        if (pdfReturnScreen == "editor" && current != null) "editor" else "main"
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
            val visible =
                docs
                    .filter { it.title.contains(search, true) }
                    .let {
                        if (sort == "name") it.sortedBy { d -> d.title.lowercase() }
                        else if (sort == "created") it.sortedByDescending { d -> d.createdAt }
                        else it
                    }
            val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    if (tab == "home") {
                        TopAppBar(
                            title = {
                                Column {
                                    Text("A4 Lekhak", fontWeight = FontWeight.Bold)
                                    Text(
                                        tr(
                                            "A little space for your ideas",
                                            "आपके विचारों के लिए एक जगह",
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = { searchPopup = true }) {
                                    Icon(
                                        Icons.Rounded.Search,
                                        contentDescription = tr("Search", "खोजें"),
                                    )
                                }
                            },
                            scrollBehavior = scrollBehavior,
                        )
                    }
                },
                bottomBar = {
                    HomeNavigation(tab) { selected ->
                        tab = selected
                        search = ""
                    }
                },
                floatingActionButton = {
                    if (tab == "documents")
                        ExtendedFloatingActionButton(
                            onClick = { model.create { screen = "editor" } },
                            icon = { Icon(Icons.Rounded.Add, null) },
                            text = { Text(tr("New document", "नया दस्तावेज़")) },
                        )
                },
            ) { padding ->
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding =
                        PaddingValues(
                            start = if (tab == "home") 0.dp else 20.dp,
                            end = if (tab == "home") 0.dp else 20.dp,
                            top = 10.dp,
                            bottom = if (tab == "documents") 100.dp else 24.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (tab == "home") {
                        insetItem { HeroCard { model.create { screen = "editor" } } }
                        insetItem {
                            SectionHeading(
                                tr("Start with a template", "टेम्पलेट से शुरू करें"),
                                tr("View all", "सभी देखें"),
                            ) {
                                tab = "templates"
                            }
                        }
                        item {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(Templates.all.take(4), key = { it.id }) { template ->
                                    TemplateCard(template, Modifier.width(168.dp)) {
                                        selectedTemplate = template.id
                                    }
                                }
                            }
                        }
                        insetItem {
                            SectionHeading(
                                tr("Recent documents", "हाल के दस्तावेज़"),
                                tr("View all", "सभी देखें"),
                            ) {
                                tab = "documents"
                            }
                        }
                        if (docs.isEmpty()) insetItem { EmptyState(false) }
                        items(docs.take(6), key = { it.id }) { doc ->
                            DocumentCard(
                                doc,
                                { model.open(doc.id) { screen = "editor" } },
                                {
                                    model.open(doc.id) {
                                        if (model.export()) {
                                            pdfReturnScreen = "main"
                                            awaitingPdf = true
                                        }
                                    }
                                },
                                { rename = doc },
                                { model.duplicate(doc) },
                                { delete = doc },
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    }
                    if (tab == "templates") {
                        val filteredTemplates =
                            Templates.all.filter { (it.name + it.category).contains(search, true) }
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
                    if (tab == "documents") {
                        item {
                            Text(
                                tr("Your writing, together.", "आपके सभी दस्तावेज़।"),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        item { SearchBox(search, { search = it }) }
                        item {
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(
                                        "modified" to tr("Last edited", "हाल में बदले"),
                                        "created" to tr("Newest", "नवीनतम"),
                                        "name" to tr("Title", "शीर्षक"),
                                    )
                                    .forEach { (key, label) ->
                                        FilterChip(
                                            sort == key,
                                            { sort = key },
                                            label = { Text(label) },
                                        )
                                    }
                            }
                        }
                        if (visible.isEmpty()) item { EmptyState(search.isNotEmpty()) }
                        items(visible, key = { it.id }) { doc ->
                            DocumentCard(
                                doc,
                                { model.open(doc.id) { screen = "editor" } },
                                {
                                    model.open(doc.id) {
                                        if (model.export()) {
                                            pdfReturnScreen = "main"
                                            awaitingPdf = true
                                        }
                                    }
                                },
                                { rename = doc },
                                { model.duplicate(doc) },
                                { delete = doc },
                            )
                        }
                    }
                    if (tab == "settings") {
                        item {
                            Text(
                                tr("Make yourself at home.", "अपनी पसंद की सेटिंग।"),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        item {
                            SettingsCard(tr("App language", "ऐप की भाषा")) {
                                ChoiceRow(listOf("en" to "English", "hi" to "हिंदी"), p.language) {
                                    model.preferences(p.copy(language = it))
                                }
                            }
                        }
                        item {
                            SettingsCard(tr("Appearance", "थीम")) {
                                ChoiceRow(
                                    listOf(
                                        "system" to tr("System", "सिस्टम"),
                                        "light" to tr("Light", "लाइट"),
                                        "dark" to tr("Dark", "डार्क"),
                                    ),
                                    p.theme,
                                ) {
                                    model.preferences(p.copy(theme = it))
                                }
                            }
                        }
                        item {
                            SettingsCard(tr("Documents", "दस्तावेज़")) {
                                Text(tr("Paper: A4 · 210 × 297 mm", "कागज़: A4 · 210 × 297 मिमी"))
                                Text(
                                    "${p.defaults.fontFamily} · ${p.defaults.fontSize} pt · ${p.defaults.lineHeight}×",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = { defaultsSetup = true }) {
                                    Text(
                                        tr(
                                            "Default margins & typography",
                                            "डिफ़ॉल्ट मार्जिन और फ़ॉन्ट",
                                        )
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(tr("Autosave", "अपने आप सहेजें"))
                                        Text(
                                            tr(
                                                "Draft recovery stays on even with autosave off.",
                                                "ऑटोसहेज बंद होने पर भी ड्राफ़्ट सुरक्षित रहता है।",
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    Switch(p.autosave, { model.preferences(p.copy(autosave = it)) })
                                }
                                TextButton(onClick = { tab = "documents" }) {
                                    Text(tr("Manage documents", "दस्तावेज़ प्रबंधन"))
                                }
                            }
                        }
                        item {
                            SettingsCard(tr("About A4 Lekhak", "A4 Lekhak के बारे में")) {
                                TextButton(onClick = { info = "privacy" }) {
                                    Text(tr("Privacy policy", "गोपनीयता नीति"))
                                }
                                TextButton(
                                    onClick = {
                                        try {
                                            context.startActivity(
                                                android.content
                                                    .Intent(
                                                        android.content.Intent.ACTION_SENDTO,
                                                        android.net.Uri.fromParts(
                                                            "mailto",
                                                            "AMAKUNAR.IN@GMAIL.COM",
                                                            null,
                                                        ),
                                                    )
                                                    .putExtra(
                                                        android.content.Intent.EXTRA_SUBJECT,
                                                        "A4 Lekhak support",
                                                    )
                                            )
                                        } catch (_: android.content.ActivityNotFoundException) {
                                            model.error.value = "Email: AMAKUNAR.IN@GMAIL.COM"
                                        }
                                    }
                                ) {
                                    Text(tr("Contact support", "सहायता से संपर्क करें"))
                                }
                                TextButton(onClick = { info = "about" }) {
                                    Text(tr("App information & licenses", "ऐप जानकारी और लाइसेंस"))
                                }
                                if (pdf != null)
                                    TextButton(onClick = { screen = "pdf" }) {
                                        Text(
                                            tr(
                                                "Open latest exported PDF",
                                                "आखिरी निर्यात किया PDF खोलें",
                                            )
                                        )
                                    }
                            }
                        }
                    }
                }
            }
        }
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
                            model.delete(doc)
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
        info?.let { kind ->
            AlertDialog(
                onDismissRequest = { info = null },
                title = {
                    Text(
                        if (kind == "privacy") tr("Privacy policy", "गोपनीयता नीति")
                        else "A4 Lekhak ${com.a4sizepaper.typing.texttopdf.BuildConfig.VERSION_NAME}"
                    )
                },
                text = {
                    Text(
                        modifier =
                            Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        text =
                            if (kind == "privacy")
                                tr(
                                    "A4 Lekhak — AMAN KUMAR\nUpdated 10 October 2026\nContact: AMAKUNAR.IN@GMAIL.COM\n\nDocuments, selected photos and PDFs are processed on this device. No account, analytics, ads or document uploads. The app requests no Internet permission.\n\nPhoto Picker grants access only to images you choose. A private resized copy is stored; image metadata is removed. Notification permission is optional and shows PDF export progress.\n\nFiles leave the app when you choose Save as, Share or Print. The selected app, cloud provider or printer handles that copy under its own policy. We do not receive your documents.\n\nDelete documents within the app. Unused images can be removed after a 24-hour grace period at a later startup. Each document’s latest private PDF is retained; earlier PDFs can be removed after seven days. Clearing app storage or uninstalling removes private files. Copies saved elsewhere must be deleted there. Save important PDFs outside the app before uninstalling.\n\nIf you email support, your email address and message are handled to answer your request. Do not send sensitive documents unless necessary; contact us to request deletion of support correspondence. We do not sell your information.",
                                    "A4 Lekhak — AMAN KUMAR\nअपडेट: 10 अक्टूबर 2026\nसंपर्क: AMAKUNAR.IN@GMAIL.COM\n\nदस्तावेज़, चुनी हुई तस्वीरें और PDF इसी डिवाइस पर बनते हैं। लॉगिन, विज्ञापन, एनालिटिक्स या दस्तावेज़ अपलोड नहीं हैं। ऐप इंटरनेट अनुमति नहीं माँगता।\n\nPhoto Picker से केवल आपकी चुनी हुई तस्वीर की निजी कॉपी बनती है; तस्वीर का मेटाडेटा हटाया जाता है। PDF की प्रगति दिखाने की notification अनुमति वैकल्पिक है।\n\nSave as, Share या Print करने पर चुना हुआ ऐप, क्लाउड प्रदाता या प्रिंटर आपकी कॉपी संभालता है। हमें दस्तावेज़ प्राप्त नहीं होते।\n\nदस्तावेज़ ऐप में हटा सकते हैं। अनुपयोगी तस्वीरें 24 घंटे बाद अगले startup पर साफ हो सकती हैं। हर दस्तावेज़ की नई PDF रहती है; पुराने versions सात दिन बाद साफ हो सकते हैं। ऐप का डेटा साफ करने या uninstall करने पर निजी फ़ाइलें मिटती हैं। बाहर सहेजी कॉपी वहीं से हटाएँ।\n\nसहायता के लिए email करने पर आपका पता और संदेश जवाब देने के लिए उपयोग होते हैं। ज़रूरत के बिना संवेदनशील दस्तावेज़ न भेजें। सहायता-संदेश हटवाने के लिए हमसे संपर्क करें। आपकी जानकारी बेची नहीं जाती।",
                                )
                            else
                                tr(
                                    "Offline document maker • No watermark\n\nBuilt with Android Jetpack, Tiptap / ProseMirror (MIT) and Noto fonts (SIL OFL). Third-party license texts are bundled in assets/licenses.\n\nPDF fidelity depends on the installed Android System WebView. Inspect the actual PDF preview before printing. Templates are editable drafts, not certifications.",
                                    "ऑफ़लाइन दस्तावेज़ निर्माता • कोई वॉटरमार्क नहीं\n\nAndroid Jetpack, Tiptap / ProseMirror (MIT) और Noto (SIL OFL) का उपयोग करता है।\n\nप्रिंट करने से पहले असली PDF प्रीव्यू जाँचें। टेम्पलेट संपादन योग्य ड्राफ़्ट हैं, प्रमाणपत्र नहीं।",
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

@Composable
private fun Onboarding(onStart: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(shape = MaterialTheme.shapes.large, color = Blue) {
                Icon(
                    Icons.Rounded.Description,
                    null,
                    tint = Color.White,
                    modifier = Modifier.padding(24.dp).size(56.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
            Text(
                "A4 Lekhak",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                tr("From a thought\nto a finished page.", "विचार से\nतैयार दस्तावेज़ तक।"),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                tr(
                    "Write in Hindi and English. Start with a template. Create clean A4 PDFs, entirely on your device.",
                    "हिंदी और अंग्रेज़ी में लिखें। टेम्पलेट चुनें। इसी डिवाइस पर साफ़ A4 PDF बनाएँ।",
                )
            )
            Spacer(Modifier.height(24.dp))
            Text(
                tr(
                    "Offline • No account • No watermark",
                    "ऑफ़लाइन • बिना खाते के • बिना वॉटरमार्क",
                ),
                color = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.height(40.dp))
            FluentButton(onStart, Modifier.fillMaxWidth().height(54.dp)) {
                Text(tr("Start writing", "लिखना शुरू करें"))
            }
        }
    }
}

@Composable
private fun HomeNavigation(tab: String, onSelect: (String) -> Unit) {
    Surface(
        modifier = Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            listOf(
                    Triple("home", tr("Home", "होम"), Icons.Rounded.Home),
                    Triple("templates", tr("Templates", "टेम्पलेट"), Icons.Rounded.Dashboard),
                    Triple("documents", tr("Documents", "दस्तावेज़"), Icons.Rounded.FolderOpen),
                    Triple("settings", tr("Settings", "सेटिंग"), Icons.Rounded.Settings),
                )
                .forEach { (key, label, icon) ->
                    val selected = tab == key
                    val scale by
                        animateFloatAsState(
                            if (selected) 1.12f else 1f,
                            animationSpec = tween(200, easing = FastOutSlowInEasing),
                            label = "Navigation icon",
                        )
                    NavigationBarItem(
                        selected = selected,
                        onClick = { onSelect(key) },
                        icon = {
                            Icon(
                                icon,
                                null,
                                Modifier.graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                },
                            )
                        },
                        label = {
                            Text(
                                label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            )
                        },
                        colors =
                            NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                            ),
                    )
                }
        }
    }
}

@Composable
private fun PreviewCursor() {
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
private fun HeroCard(onCreate: () -> Unit) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val reveal by
        animateFloatAsState(
            if (appeared) 1f else 0f,
            animationSpec = tween(250, easing = LinearOutSlowInEasing),
            label = "Document preview entrance",
        )
    Card(
        onClick = onCreate,
        modifier =
            Modifier.fillMaxWidth().graphicsLayer {
                alpha = reveal
                translationY = (1f - reveal) * 12.dp.toPx()
            },
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    tr("Your next page", "आपका नया पेज"),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "A4",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                contentColor = Color(0xFF111827),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shadowElevation = 2.dp,
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        tr("Start writing here…", "यहाँ से लिखना शुरू करें…"),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    PreviewCursor()
                    listOf(1f, 0.86f, 0.62f).forEach { fraction ->
                        Box(
                            Modifier.fillMaxWidth(fraction)
                                .height(2.dp)
                                .background(Color(0xFFEDF0F4))
                        )
                    }
                    Text(
                        tr("Tap to open a blank document", "खाली दस्तावेज़ खोलने के लिए टैप करें"),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B),
                    )
                }
            }
            // The whole card is one accessible action; this is its visible button label.
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.small,
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        tr("Write a new document", "नया दस्तावेज़ लिखें"),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBox(
    value: String,
    onChange: (String) -> Unit,
    isTemplates: Boolean = false,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value,
        onChange,
        modifier.fillMaxWidth(),
        placeholder = {
            Text(
                if (isTemplates) tr("Find templates…", "टेम्पलेट खोजें…")
                else tr("Find your documents…", "दस्तावेज़ खोजें…")
            )
        },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Rounded.Close, tr("Clear search", "खोज साफ़ करें"))
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
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
        Column(Modifier.padding(16.dp)) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Article,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
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
    var menu by remember { mutableStateOf(false) }
    val locale =
        if (LocalHindi.current) java.util.Locale.forLanguageTag("hi-IN")
        else java.util.Locale.ENGLISH
    Card(
        onClick = onOpen,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Description,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    doc.title.ifBlank { tr("Untitled document", "नया दस्तावेज़") },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
                        .format(Date(doc.modifiedAt)) +
                        (if (doc.isDraft) tr(" · Draft", " · ड्राफ़्ट") else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, tr("Share", "शेयर करें")) }
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
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            content()
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

private fun LazyListScope.insetItem(content: @Composable () -> Unit) {
    item { Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { content() } }
}
