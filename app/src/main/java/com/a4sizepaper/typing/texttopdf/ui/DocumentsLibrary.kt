package com.a4sizepaper.typing.texttopdf.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a4sizepaper.typing.texttopdf.R
import com.a4sizepaper.typing.texttopdf.data.DocumentEntry
import com.a4sizepaper.typing.texttopdf.data.ImportedPdfEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.flow.collect

private val LocalLibraryResources =
    staticCompositionLocalOf<android.content.res.Resources> {
        error("Documents library resources are missing")
    }

@Composable private fun libraryText(id: Int) = LocalLibraryResources.current.getString(id)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentsLibrary(
    modifier: Modifier,
    docs: List<DocumentEntry>,
    pdfs: List<ImportedPdfEntry>,
    total: Int,
    query: String,
    onQuery: (String) -> Unit,
    sort: String,
    onSort: (String) -> Unit,
    grid: Boolean,
    onGrid: (Boolean) -> Unit,
    language: String,
    importing: Boolean,
    inlineAd: (@Composable () -> Unit)? = null,
    onImport: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (DocumentEntry) -> Unit,
    onExport: (DocumentEntry, String) -> Unit,
    onRename: (DocumentEntry) -> Unit,
    onDuplicate: (DocumentEntry) -> Unit,
    onDelete: (DocumentEntry) -> Unit,
    pdfCard: @Composable (ImportedPdfEntry) -> Unit,
) {
    val colors = AppTheme.colors
    val state = rememberLazyGridState()
    var expanded by remember { mutableStateOf(true) }
    var menuId by remember { mutableStateOf<String?>(null) }
    val formatter =
        remember(language) {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(Locale.forLanguageTag(language))
                .withZone(ZoneId.systemDefault())
        }
    LaunchedEffect(state) {
        var previous = 0 to 0
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { position ->
                if (position != previous) {
                    expanded =
                        position.first < previous.first ||
                            (position.first == previous.first && position.second < previous.second)
                    previous = position
                }
            }
    }
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val resources =
        remember(context, configuration, language) {
            val localized = android.content.res.Configuration(configuration)
            localized.setLocale(Locale.forLanguageTag(language))
            context.createConfigurationContext(localized).resources
        }
    CompositionLocalProvider(LocalLibraryResources provides resources) {
        Box(modifier.statusBarsPadding()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (grid) 2 else 1),
                state = state,
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(if (grid) 12.dp else 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                libraryText(R.string.library_documents),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.ink,
                            )
                            val count =
                                LocalLibraryResources.current.getQuantityString(
                                    R.plurals.library_files,
                                    total,
                                    total,
                                )
                            Text(
                                count + " · " + libraryText(R.string.library_saved_on_this_device),
                                fontSize = 12.sp,
                                color = colors.muted,
                            )
                        }
                        FilledTonalButton(
                            onClick = onImport,
                            enabled = !importing,
                            colors =
                                ButtonDefaults.filledTonalButtonColors(
                                    containerColor = colors.accentSoft,
                                    contentColor = colors.accentText,
                                ),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            Icon(Icons.Outlined.FileUpload, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(libraryText(R.string.library_import))
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQuery,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        placeholder = { Text(libraryText(R.string.library_find_your_documents)) },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Search,
                                null,
                                Modifier.size(18.dp),
                                tint = colors.muted,
                            )
                        },
                        trailingIcon = {
                            if (query.isNotEmpty())
                                IconButton(onClick = { onQuery("") }) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        libraryText(R.string.library_clear_search),
                                        Modifier.size(17.dp),
                                    )
                                }
                        },
                        keyboardOptions =
                            androidx.compose.foundation.text.KeyboardOptions(
                                imeAction = androidx.compose.ui.text.input.ImeAction.Search
                            ),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = colors.surface,
                                focusedContainerColor = colors.surface,
                                unfocusedBorderColor = colors.line,
                            ),
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.SwapVert,
                            null,
                            Modifier.size(16.dp),
                            tint = colors.muted,
                        )
                        listOf(
                                "modified" to libraryText(R.string.library_last_edited),
                                "created" to libraryText(R.string.library_newest),
                                "name" to libraryText(R.string.library_title),
                            )
                            .forEach { (key, label) ->
                                FilterChip(
                                    selected = sort == key,
                                    onClick = { onSort(key) },
                                    label = {
                                        Text(
                                            label,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            containerColor = colors.surface,
                                            labelColor = colors.muted,
                                            selectedContainerColor = colors.accent,
                                            selectedLabelColor = colors.onAccent,
                                        ),
                                )
                            }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            libraryText(R.string.library_your_library) +
                                " · ${docs.size + pdfs.size}",
                            Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, colors.line),
                        ) {
                            Row {
                                listOf(true, false).forEach { useGrid ->
                                    FilledTonalIconButton(
                                        onClick = { onGrid(useGrid) },
                                        colors =
                                            IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor =
                                                    if (grid == useGrid) colors.accentSoft
                                                    else colors.surface,
                                                contentColor =
                                                    if (grid == useGrid) colors.accentText
                                                    else colors.muted,
                                            ),
                                    ) {
                                        Icon(
                                            if (useGrid) Icons.Outlined.GridView
                                            else Icons.Outlined.ViewList,
                                            if (useGrid) libraryText(R.string.library_grid)
                                            else libraryText(R.string.library_list),
                                            Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (docs.isEmpty() && pdfs.isEmpty())
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (query.isBlank())
                                LibraryPaper(emptyList(), Modifier.size(48.dp, 66.dp), false)
                            Text(
                                if (query.isBlank())
                                    libraryText(R.string.library_your_library_is_empty)
                                else libraryText(R.string.library_no_documents_found),
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (query.isBlank())
                                TextButton(onClick = onCreate) {
                                    Text(libraryText(R.string.library_new_document))
                                }
                            else
                                Text(
                                    libraryText(
                                        R.string.library_try_another_name_or_create_a_document
                                    ),
                                    color = colors.muted,
                                    fontSize = 12.sp,
                                )
                        }
                    }
                val firstSection = if (inlineAd != null && docs.size >= 12) docs.take(8) else docs
                items(firstSection, key = { "doc-${it.id}" }) { doc ->
                    LibraryDocument(
                        doc,
                        formatter.format(Instant.ofEpochMilli(doc.modifiedAt)),
                        grid,
                        onOpen = { onOpen(doc) },
                        onShare = { onExport(doc, "share") },
                        onMore = { menuId = doc.id },
                        modifier = Modifier.animateItem(),
                    )
                }
                if (firstSection.size < docs.size && inlineAd != null) {
                    item(key = "library-documents-ad", span = { GridItemSpan(maxLineSpan) }) { inlineAd() }
                    items(docs.drop(firstSection.size), key = { "doc-${it.id}" }) { doc ->
                        LibraryDocument(
                            doc, formatter.format(Instant.ofEpochMilli(doc.modifiedAt)), grid,
                            onOpen = { onOpen(doc) },
                            onShare = { onExport(doc, "share") },
                            onMore = { menuId = doc.id },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                if (pdfs.isNotEmpty())
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(libraryText(R.string.library_saved_pdfs), fontWeight = FontWeight.Bold)
                    }
                items(pdfs, key = { "pdf-${it.id}" }, span = { GridItemSpan(maxLineSpan) }) {
                    pdfCard(it)
                }
                if (inlineAd != null && docs.size + pdfs.size >= 4 &&
                    (firstSection.size == docs.size || docs.size - firstSection.size + pdfs.size >= 8)
                ) {
                    item(key = "library-end-ad", span = { GridItemSpan(maxLineSpan) }) { inlineAd() }
                }
            }
            ExtendedFloatingActionButton(
                expanded = expanded,
                onClick = onCreate,
                icon = {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription =
                            if (expanded) null else libraryText(R.string.library_new_document),
                    )
                },
                text = { Text(libraryText(R.string.library_new_document)) },
                containerColor = colors.accent,
                contentColor = colors.onAccent,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 14.dp),
            )
        }
        docs
            .firstOrNull { it.id == menuId }
            ?.let { doc ->
                ModalBottomSheet(
                    onDismissRequest = { menuId = null },
                    containerColor = colors.surface,
                    scrimColor = colors.scrim,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    dragHandle = { BottomSheetDefaults.DragHandle(color = colors.line) },
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        Text(
                            doc.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            formatter.format(Instant.ofEpochMilli(doc.modifiedAt)),
                            color = colors.muted,
                            fontSize = 12.sp,
                        )
                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.line)
                        LibraryAction(libraryText(R.string.library_rename), Icons.Outlined.Edit) {
                            menuId = null
                            onRename(doc)
                        }
                        LibraryAction(libraryText(R.string.library_share), Icons.Outlined.Share) {
                            menuId = null
                            onExport(doc, "share")
                        }
                        LibraryAction(
                            libraryText(R.string.library_save_as_pdf),
                            Icons.Outlined.FileDownload,
                        ) {
                            menuId = null
                            onExport(doc, "save")
                        }
                        LibraryAction(
                            libraryText(R.string.library_duplicate),
                            Icons.Outlined.ContentCopy,
                        ) {
                            menuId = null
                            onDuplicate(doc)
                        }
                        LibraryAction(
                            libraryText(R.string.library_delete),
                            Icons.Outlined.Delete,
                            destructive = true,
                        ) {
                            menuId = null
                            onDelete(doc)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
    }
}

@Composable
private fun LibraryAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val tint = if (destructive) colors.danger else colors.accentText
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = tint)
        Spacer(Modifier.width(12.dp))
        Text(label, color = if (destructive) tint else colors.ink, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LibraryDocument(
    doc: DocumentEntry,
    date: String,
    grid: Boolean,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier,
) {
    val colors = AppTheme.colors
    val load = LocalDocumentPreview.current
    val preview by
        produceState(emptyList<String>(), doc.id, doc.modifiedAt) { value = load(doc.id) }
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.line),
    ) {
        Column(Modifier.padding(if (grid) 10.dp else 12.dp)) {
            if (grid) LibraryPaper(preview, Modifier.fillMaxWidth().height(132.dp), true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!grid) {
                    LibraryPaper(preview, Modifier.size(40.dp, 52.dp), false)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        doc.title.ifBlank { libraryText(R.string.library_untitled_document) },
                        fontSize = if (grid) 13.sp else 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(date, color = colors.muted, fontSize = if (grid) 11.sp else 12.sp)
                }
                if (!grid)
                    IconButton(onClick = onShare) {
                        Icon(
                            Icons.Outlined.Share,
                            libraryText(R.string.library_share),
                            Modifier.size(18.dp),
                            tint = colors.accentText,
                        )
                    }
                IconButton(onClick = onMore) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        libraryText(R.string.library_more_options),
                        Modifier.size(18.dp),
                        tint = colors.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryPaper(lines: List<String>, modifier: Modifier, big: Boolean) {
    val colors = AppTheme.colors
    val line = colors.paperLine
    Surface(
        modifier = modifier,
        color = colors.paper,
        shape = RoundedCornerShape(if (big) 10.dp else 6.dp),
        border = BorderStroke(1.dp, line),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val margin = (if (big) 22.dp else 9.dp).toPx()
            drawLine(colors.margin, Offset(margin, 0f), Offset(margin, size.height), 1.dp.toPx())
            val start = (if (big) 30.dp else 13.dp).toPx()
            val available =
                (size.width - start - (if (big) 14.dp else 6.dp).toPx()).coerceAtLeast(0f)
            val top = (if (big) 14.dp else 6.dp).toPx()
            drawRoundRect(
                colors.paperTitle,
                Offset(start, top),
                Size(available * .55f, (if (big) 6.dp else 3.dp).toPx()),
                CornerRadius(2.dp.toPx()),
            )
            lines.take(if (big) 5 else 3).forEachIndexed { index, text ->
                val y = top + (if (big) 14.dp else 7.dp).toPx() * (index + 1)
                val fraction = (text.length / 48f).coerceIn(.18f, 1f)
                drawRoundRect(
                    line,
                    Offset(start, y),
                    Size(available * fraction, (if (big) 4.dp else 2.dp).toPx()),
                    CornerRadius(2.dp.toPx()),
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Documents Light",
    widthDp = 390,
    heightDp = 800,
)
@androidx.compose.ui.tooling.preview.Preview(
    name = "Documents Dark",
    widthDp = 390,
    heightDp = 800,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DocumentsLibraryPreview() {
    A4Theme {
        val titles = listOf("Untitled document", "Meeting notes", "Rent agreement", "Resume")
        val docs =
            titles.mapIndexed { index, title ->
                DocumentEntry("preview-$index", title, 0L, 0L, false)
            }
        DocumentsLibrary(
            modifier = Modifier.fillMaxSize(),
            docs = docs,
            pdfs = emptyList(),
            total = docs.size,
            query = "",
            onQuery = {},
            sort = "modified",
            onSort = {},
            grid = true,
            onGrid = {},
            language = "hi",
            importing = false,
            onImport = {},
            onCreate = {},
            onOpen = {},
            onExport = { _, _ -> },
            onRename = {},
            onDuplicate = {},
            onDelete = {},
            pdfCard = {},
        )
    }
}
