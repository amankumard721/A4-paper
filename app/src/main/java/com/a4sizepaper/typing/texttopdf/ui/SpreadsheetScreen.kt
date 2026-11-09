package com.a4sizepaper.typing.texttopdf.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.a4sizepaper.typing.texttopdf.data.SheetBook
import com.a4sizepaper.typing.texttopdf.data.sheetColumn
import com.a4sizepaper.typing.texttopdf.data.writeGridWorkbook
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpreadsheetScreen(
    book: SheetBook,
    onChange: (SheetBook) -> Unit,
    onBack: () -> Unit,
    onExport: (String) -> Unit,
) {
    val c = AppTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var sheetIndex by rememberSaveable { mutableIntStateOf(0) }
    var row by rememberSaveable { mutableIntStateOf(0) }
    var column by rememberSaveable { mutableIntStateOf(0) }
    var edit by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val sheet = book.sheets[sheetIndex.coerceIn(book.sheets.indices)]
    val rowCount = ((sheet.cells.keys.maxOfOrNull { it.first } ?: 0) + 6).coerceIn(20, 2000)
    val columnCount = ((sheet.cells.keys.maxOfOrNull { it.second } ?: 0) + 3).coerceIn(8, 64)
    val cell = row to column
    val value = sheet.cells[cell].orEmpty()
    val savedLabel = tr("Excel copy saved", "Excel कॉपी सहेजी गई")
    val failedLabel = tr("Could not save the Excel copy.", "Excel कॉपी सहेजी नहीं जा सकी।")
    val save =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            )
        ) { uri ->
            if (uri != null && !saving) {
                val snapshot = book
                saving = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val target =
                                checkNotNull(context.contentResolver.openOutputStream(uri, "w"))
                            target.use { writeGridWorkbook(snapshot, it) }
                        }
                        onChange(snapshot.copy(dirty = false))
                        launch { snackbar.showSnackbar(savedLabel) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        failure = failedLabel
                    } finally {
                        saving = false
                    }
                }
            }
        }
    val back = {
        if (!saving) {
            if (book.dirty) confirmExit = true else onBack()
        }
    }
    BackHandler(onBack = back)
    Scaffold(
        containerColor = c.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (book.dirty) tr("Unsaved changes", "बदलाव सहेजे नहीं गए")
                            else tr("Spreadsheet", "स्प्रेडशीट"),
                            style = MaterialTheme.typography.labelSmall,
                            color = c.muted,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(back, enabled = !saving) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back", "वापस"))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            save.launch(book.title.replace(Regex("[/\\\\:*?\"<>|]"), "_") + ".xlsx")
                        },
                        enabled = !saving,
                    ) {
                        Icon(Icons.Rounded.SaveAlt, tr("Save Excel copy", "Excel कॉपी सहेजें"))
                    }
                    Box {
                        IconButton(onClick = { menu = true }, enabled = !saving) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                tr("Spreadsheet options", "स्प्रेडशीट विकल्प"),
                            )
                        }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(tr("Export PDF", "PDF बनाएँ")) },
                                onClick = {
                                    menu = false
                                    onExport("save")
                                },
                                leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, null) },
                            )
                            DropdownMenuItem(
                                text = { Text(tr("Print", "प्रिंट")) },
                                onClick = {
                                    menu = false
                                    onExport("print")
                                },
                                leadingIcon = { Icon(Icons.Rounded.Print, null) },
                            )
                            DropdownMenuItem(
                                text = { Text(tr("Workbook details", "वर्कबुक की जानकारी")) },
                                onClick = {
                                    menu = false
                                    showInfo = true
                                },
                                leadingIcon = { Icon(Icons.Rounded.Info, null) },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            Surface(color = c.accentSoft) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        sheetColumn(column) + (row + 1),
                        Modifier.width(54.dp),
                        fontWeight = FontWeight.Bold,
                        color = c.accentText,
                    )
                    TextButton(
                        onClick = { edit = value },
                        enabled = !saving,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            value.ifEmpty { tr("Edit selected cell", "चुनी सेल बदलें") },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    IconButton(onClick = { edit = value }, enabled = !saving) {
                        Icon(Icons.Rounded.Edit, tr("Edit cell", "सेल बदलें"))
                    }
                }
            }
            key(sheetIndex) {
                val horizontal = rememberScrollState()
                Column(Modifier.weight(1f).fillMaxWidth().horizontalScroll(horizontal)) {
                    Row {
                        GridCell("", 48, header = true)
                        repeat(columnCount) { index ->
                            GridCell(
                                sheetColumn(index),
                                112,
                                header = true,
                                selected = index == column,
                            )
                        }
                    }
                    LazyColumn(Modifier.width((48 + columnCount * 112).dp).weight(1f)) {
                        items(rowCount) { index ->
                            Row {
                                GridCell(
                                    (index + 1).toString(),
                                    48,
                                    header = true,
                                    selected = index == row,
                                )
                                repeat(columnCount) { col ->
                                    GridCell(
                                        sheet.cells[index to col].orEmpty(),
                                        112,
                                        selected = index == row && col == column,
                                    ) {
                                        if (!saving) {
                                            if (index == row && col == column) edit = value
                                            row = index
                                            column = col
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Surface(color = c.surface) {
                Row(
                    Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    book.sheets.forEachIndexed { index, item ->
                        FilterChip(
                            selected = sheetIndex == index,
                            onClick = {
                                sheetIndex = index
                                row = 0
                                column = 0
                            },
                            label = { Text(item.name, maxLines = 1) },
                        )
                    }
                }
            }
        }
    }

    edit?.let { input ->
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(sheetColumn(column) + (row + 1)) },
            text = {
                OutlinedTextField(
                    input,
                    { edit = it.take(32767) },
                    label = { Text(tr("Cell value", "सेल का मान")) },
                    maxLines = 5,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cells = sheet.cells.toMutableMap()
                        if (input.isEmpty()) cells.remove(cell) else cells[cell] = input
                        val updated = book.sheets.toMutableList()
                        updated[sheetIndex] = sheet.copy(cells = cells)
                        if (input != value) onChange(book.copy(sheets = updated, dirty = true))
                        edit = null
                    }
                ) {
                    Text(tr("Apply", "लागू करें"))
                }
            },
            dismissButton = {
                TextButton(onClick = { edit = null }) { Text(tr("Cancel", "रद्द करें")) }
            },
        )
    }
    if (showInfo)
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(tr("Workbook details", "वर्कबुक की जानकारी")) },
            text = {
                Column(
                    Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        tr(
                            "Excel export saves a new XLSX with visible cell values. Formulas, charts and original formatting are not included.",
                            "नई XLSX में दिखाई देने वाले सेल मान सहेजे जाते हैं। फ़ॉर्मूले, चार्ट और मूल फ़ॉर्मैटिंग इसमें शामिल नहीं हैं।",
                        )
                    )
                    book.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text(tr("Close", "बंद करें")) }
            },
        )
    if (confirmExit)
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(tr("Discard unsaved changes?", "बिना सहेजे बदलाव हटाएँ?")) },
            confirmButton = { TextButton(onClick = onBack) { Text(tr("Discard", "हटाएँ")) } },
            dismissButton = {
                TextButton(onClick = { confirmExit = false }) {
                    Text(tr("Keep editing", "संपादन जारी रखें"))
                }
            },
        )
    failure?.let { message ->
        AlertDialog(
            onDismissRequest = { failure = null },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { failure = null }) { Text(tr("OK", "ठीक है")) }
            },
        )
    }
}

@Composable
private fun GridCell(
    value: String,
    width: Int,
    header: Boolean = false,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val c = AppTheme.colors
    val background = if (selected) c.accentSoft else if (header) c.cardFooter else c.surface
    Box(
        Modifier.width(width.dp)
            .height(48.dp)
            .background(background)
            .border(if (selected) 2.dp else 0.5.dp, if (selected) c.accentText else c.line)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp),
        contentAlignment = if (header) Alignment.Center else Alignment.CenterStart,
    ) {
        Text(
            value,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = c.ink,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
