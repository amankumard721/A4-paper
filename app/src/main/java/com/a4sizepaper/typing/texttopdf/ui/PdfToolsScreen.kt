package com.a4sizepaper.typing.texttopdf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class PdfTool(val title: String, val detail: String, val icon: ImageVector, val open: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfToolsScreen(
    busy: Boolean,
    onBack: () -> Unit,
    onOpenPdf: () -> Unit,
    onSplit: () -> Unit,
    onRemove: () -> Unit,
    onMerge: () -> Unit,
    onCompress: () -> Unit,
    onScan: () -> Unit,
    onWord: () -> Unit,
    onExcel: () -> Unit,
    onImage: () -> Unit,
    banner: @Composable () -> Unit,
) {
    val colors = AppTheme.colors
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(240)) }
    val pageTools = listOf(
        PdfTool(tr("Merge PDF", "PDF जोड़ें"), tr("Combine multiple PDFs in the order you choose.", "कई PDF को अपने चुने हुए क्रम में जोड़ें।"), Icons.Rounded.MergeType, onMerge),
        PdfTool(tr("Split PDF", "PDF बाँटें"), tr("Save selected pages as a separate PDF.", "चुने हुए पेज अलग PDF में सहेजें।"), Icons.Rounded.ContentCut, onSplit),
        PdfTool(tr("Remove pages", "पेज हटाएँ"), tr("Choose pages to omit from a new copy.", "नई कॉपी से हटाने वाले पेज चुनें।"), Icons.Rounded.DeleteOutline, onRemove),
        PdfTool(tr("Manage PDF pages", "PDF पेज व्यवस्थित करें"), tr("Open a PDF to extract, reorder or rotate pages.", "पेज निकालने, क्रम बदलने या घुमाने के लिए PDF खोलें।"), Icons.Rounded.PictureAsPdf, onOpenPdf),
        PdfTool(tr("Scan to PDF", "स्कैन से PDF"), tr("Capture and crop pages with your camera.", "कैमरे से पेज स्कैन और क्रॉप करें।"), Icons.Rounded.DocumentScanner, onScan),
    )
    val conversions = listOf(
        PdfTool(tr("Word to PDF", "Word से PDF"), tr("Open a DOCX, review it and save as PDF.", "DOCX खोलें, देखें और PDF में सहेजें।"), Icons.Rounded.Description, onWord),
        PdfTool(tr("Excel to PDF", "Excel से PDF"), tr("Open a spreadsheet and review it before export.", "स्प्रेडशीट खोलें और PDF बनाने से पहले देखें।"), Icons.Rounded.TableChart, onExcel),
        PdfTool(tr("Image to PDF", "चित्र से PDF"), tr("Choose a photo and place it on an A4 page.", "फ़ोटो चुनें और A4 पेज पर रखें।"), Icons.Rounded.Image, onImage),
    )
    val compress = PdfTool(
        tr("Compress PDF", "PDF का आकार घटाएँ"),
        tr("Reduce large photos while keeping text selectable.", "बड़ी फ़ोटो का आकार घटाएँ और टेक्स्ट चुनने योग्य रखें।"),
        Icons.Rounded.Compress,
        onCompress,
    )
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text(tr("PDF tools", "PDF टूल्स"), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back", "वापस")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface, titleContentColor = colors.ink),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(
                    color = colors.accentSoft,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.graphicsLayer {
                        alpha = entrance.value
                        translationY = 8.dp.toPx() * (1f - entrance.value)
                    },
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(tr("Work with PDFs", "PDF पर काम करें"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = colors.ink)
                        Text(tr("Choose a tool, then pick your file.", "टूल चुनें, फिर अपनी फ़ाइल चुनें।"), color = colors.muted)
                    }
                }
            }
            item { Text(tr("Organize & scan", "व्यवस्थित करें और स्कैन करें"), style = MaterialTheme.typography.titleMedium, color = colors.ink) }
            items(pageTools) { PdfToolRow(it, !busy) }
            item { Text(tr("Optimize PDF", "PDF का आकार घटाएँ"), style = MaterialTheme.typography.titleMedium, color = colors.ink) }
            item { PdfToolRow(compress, !busy) }
            item { Text(tr("Convert to PDF", "PDF में बदलें"), style = MaterialTheme.typography.titleMedium, color = colors.ink) }
            items(conversions) { PdfToolRow(it, !busy) }
            if (!busy) item(key = "pdf-tools-ad") { banner() }
        }
    }
}

@Composable
private fun PdfToolRow(tool: PdfTool, enabled: Boolean) {
    val colors = AppTheme.colors
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(120), label = "PDF tool press")
    Surface(
        onClick = tool.open,
        enabled = enabled,
        interactionSource = interactions,
        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale },
        color = colors.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, colors.line),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(color = colors.accentSoft, shape = RoundedCornerShape(14.dp)) {
                Icon(tool.icon, null, Modifier.padding(12.dp).size(22.dp), tint = colors.accentText)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tool.title, color = colors.ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(tool.detail, color = colors.muted, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = colors.accentText)
        }
    }
}

@Composable
internal fun PdfMergeDialog(
    files: List<android.net.Uri>,
    onOrder: (List<android.net.Uri>) -> Unit,
    onDismiss: () -> Unit,
    onMerge: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var names by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(emptyMap<android.net.Uri, String>()) }
    androidx.compose.runtime.LaunchedEffect(files.toSet()) {
        names = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            files.associateWith { uri ->
                runCatching {
                    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                }.getOrNull() ?: "PDF"
            }
        }
    }
    fun move(from: Int, to: Int) {
        val next = files.toMutableList()
        next.add(to, next.removeAt(from))
        onOrder(next)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Arrange PDFs", "PDF का क्रम तय करें")) },
        text = {
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(files.size) { index ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}. ${names[files[index]] ?: "PDF"}", Modifier.weight(1f), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        IconButton(onClick = { move(index, index - 1) }, enabled = index > 0) {
                            Icon(Icons.Rounded.KeyboardArrowUp, tr("Move up", "ऊपर करें"))
                        }
                        IconButton(onClick = { move(index, index + 1) }, enabled = index < files.lastIndex) {
                            Icon(Icons.Rounded.KeyboardArrowDown, tr("Move down", "नीचे करें"))
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onMerge) { Text(tr("Merge PDFs", "PDF जोड़ें")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "रद्द करें")) } },
    )
}
