package com.a4sizepaper.typing.texttopdf.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfTextPanel(file: File, page: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var text by remember(file, page) { mutableStateOf<String?>(null) }
    var error by remember(file, page) { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(file, page) {
        try {
            text = withContext(Dispatchers.IO) {
                val cancellation = currentCoroutineContext()
                PDFBoxResourceLoader.init(context.applicationContext)
                PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly(256L * 1024 * 1024).setTempDir(context.cacheDir)).use { doc ->
                    require(doc.currentAccessPermission.canExtractContent()) { "This PDF does not allow copying text." }
                    require(page in 0 until doc.numberOfPages)
                    val stripper = object : PDFTextStripper() {
                        var characters = 0
                        override fun processTextPosition(position: TextPosition) {
                            cancellation.ensureActive()
                            characters += position.unicode?.length ?: 1
                            require(characters <= 250_000) { "This page contains too much text to select at once." }
                            super.processTextPosition(position)
                        }
                    }.apply { sortByPosition = true; startPage = page + 1; endPage = page + 1 }
                    stripper.getText(doc)
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Could not read page text." }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text(tr("Page ${page + 1} · Select text", "पेज ${page + 1} · टेक्स्ट चुनें"), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(enabled = !text.isNullOrBlank(), onClick = { clipboard.setText(AnnotatedString(text!!)); copied = true }) {
                    Text(if (copied) tr("Copied", "कॉपी किया") else tr("Copy page", "पेज कॉपी करें"))
                }
                TextButton(onClick = onDismiss) { Text(tr("Close", "बंद करें")) }
            }
            when {
                error != null -> Text(error!!, Modifier.padding(bottom = 24.dp))
                text == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                text!!.isBlank() -> Text(tr("This page is an image with no selectable text. OCR is needed to copy or search it.", "यह पेज तस्वीर है, इसमें चुनने योग्य टेक्स्ट नहीं है। कॉपी और खोज के लिए OCR चाहिए।"), Modifier.padding(bottom = 24.dp))
                else -> {
                    Text(tr("Long-press the text below to select and copy.", "नीचे टेक्स्ट पर देर तक दबाकर चुनें और कॉपी करें।"), style = MaterialTheme.typography.bodySmall)
                    SelectionContainer {
                        Text(text!!, Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(vertical = 16.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
