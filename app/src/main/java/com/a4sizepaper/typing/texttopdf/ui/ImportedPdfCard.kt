package com.a4sizepaper.typing.texttopdf.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.a4sizepaper.typing.texttopdf.data.ImportedPdfEntry
import com.a4sizepaper.typing.texttopdf.data.PdfImporter
import java.text.DateFormat
import java.util.Date

@Composable
internal fun ImportedPdfCard(
    entry: ImportedPdfEntry,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: (String) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var title by remember(entry.title) { mutableStateOf(entry.title) }
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                color = HomeRed.copy(alpha = .09f),
            ) {
                Icon(Icons.Rounded.PictureAsPdf, null, Modifier.padding(14.dp).size(26.dp), tint = HomeRed)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "PDF · ${entry.pages} " +
                        tr("pages", "पेज") +
                        " · " +
                        DateFormat.getDateInstance(DateFormat.SHORT).format(Date(entry.modifiedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Rounded.Share, tr("Share PDF", "PDF शेयर करें"), tint = HomeBlue, modifier = Modifier.size(20.dp))
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, tr("PDF actions", "PDF के विकल्प"))
                }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(tr("Rename", "नाम बदलें")) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = {
                            menu = false
                            renaming = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(tr("Duplicate", "कॉपी बनाएँ")) },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                        onClick = {
                            menu = false
                            onDuplicate()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(tr("Delete", "हटाएँ"), color = MaterialTheme.colorScheme.error)
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            menu = false
                            deleting = true
                        },
                    )
                }
            }
        }
    }
    if (renaming)
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(tr("Rename PDF", "PDF का नाम बदलें")) },
            text = {
                OutlinedTextField(
                    title,
                    { title = it.take(200) },
                    singleLine = true,
                    label = { Text(tr("Name", "नाम")) },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = title.isNotBlank(),
                    onClick = {
                        onRename(title)
                        renaming = false
                    },
                ) {
                    Text(tr("Save", "सेव करें"))
                }
            },
            dismissButton = {
                TextButton(onClick = { renaming = false }) { Text(tr("Cancel", "रद्द करें")) }
            },
        )
    if (deleting)
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(tr("Delete PDF?", "PDF हटाएँ?")) },
            text = {
                Text(
                    tr(
                        "Remove this private copy from the app? Copies saved elsewhere remain available.",
                        "ऐप की यह कॉपी हटाएँ? दूसरी जगह सेव की गई कॉपी उपलब्ध रहेगी।",
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        deleting = false
                    }
                ) {
                    Text(tr("Delete", "हटाएँ"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = false }) { Text(tr("Cancel", "रद्द करें")) }
            },
        )
}

internal enum class PdfPageTool { Manage, Split, Remove }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PdfPagesDialog(
    entry: ImportedPdfEntry,
    onDismiss: () -> Unit,
    tool: PdfPageTool = PdfPageTool.Manage,
    onCreate: (String, Int) -> Unit,
) {
    var ranges by remember(entry.id) { mutableStateOf("") }
    var rotation by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(when (tool) {
                PdfPageTool.Split -> tr("Split PDF", "PDF बाँटें")
                PdfPageTool.Remove -> tr("Remove pages", "पेज हटाएँ")
                PdfPageTool.Manage -> tr("PDF pages", "PDF के पेज")
            })
        },
        text = {
            Column(
                Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(when (tool) {
                    PdfPageTool.Split -> tr(
                        "Enter the pages to save in a separate PDF. This file has ${entry.pages} pages.",
                        "अलग PDF में सहेजने वाले पेज लिखें। इस फ़ाइल में ${entry.pages} पेज हैं।",
                    )
                    PdfPageTool.Remove -> tr(
                        "Enter the pages to remove. The remaining pages go into a new copy.",
                        "हटाने वाले पेज लिखें। बाकी पेजों की नई कॉपी बनेगी।",
                    )
                    PdfPageTool.Manage -> tr(
                        "Enter pages in the order you want, for example 3,1-2. Leave blank for all ${entry.pages} pages.",
                        "पेज का क्रम लिखें, जैसे 3,1-2। सभी ${entry.pages} पेज के लिए खाली रखें।",
                    )
                })
                OutlinedTextField(
                    ranges,
                    {
                        ranges = it.take(2000)
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(tr("Page numbers", "पेज नंबर")) },
                    placeholder = { Text("1-3,5") },
                    isError = error != null,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (tool == PdfPageTool.Manage) {
                    Text(tr("Rotate selected pages clockwise", "चुने पेज दाईं ओर घुमाएँ"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 90, 180, 270).forEach { degrees ->
                            FilterChip(
                                selected = rotation == degrees,
                                onClick = { rotation = degrees },
                                label = { Text("$degrees°") },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val needPages = tr("Enter at least one page.", "कम से कम एक पेज लिखें।")
            val keepPage = tr("Keep at least one page in the PDF.", "PDF में कम से कम एक पेज रखें।")
            TextButton(
                onClick = {
                    try {
                        require(tool == PdfPageTool.Manage || ranges.isNotBlank()) { needPages }
                        val selected = PdfImporter.parsePageRanges(ranges, entry.pages)
                        val output = if (tool == PdfPageTool.Remove) {
                            val removed = selected.toSet()
                            val kept = (0 until entry.pages).filterNot { it in removed }
                            require(kept.isNotEmpty()) { keepPage }
                            kept.joinToString(",") { (it + 1).toString() }
                        } else ranges
                        onCreate(output, rotation)
                    } catch (e: IllegalArgumentException) {
                        error = e.message
                    }
                }
            ) {
                Text(tr("Create PDF copy", "PDF कॉपी बनाएँ"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "रद्द करें")) } },
    )
}
