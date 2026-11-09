package com.a4sizepaper.typing.texttopdf.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.a4sizepaper.typing.texttopdf.pdf.PdfSelectableText

internal data class PdfSearchHit(val page: Int, val range: IntRange)

@Composable
internal fun PdfInlineSearchBar(
    query: String,
    onQuery: (String) -> Unit,
    count: Int,
    index: Int,
    searching: Boolean,
    failed: Boolean,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.statusBarsPadding()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { keyboard?.hide(); onClose() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Close search", "खोज बंद करें"))
                }
                TextField(
                    value = query,
                    onValueChange = onQuery,
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    placeholder = { Text(tr("Search PDF", "PDF में खोजें")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                if (query.isNotBlank()) {
                    Text(
                        when {
                            count > 0 -> "${index + 1} / $count"
                            searching -> "…"
                            failed -> "—"
                            else -> "0 / 0"
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                IconButton(onClick = onPrevious, enabled = count > 1) {
                    Icon(Icons.Rounded.ChevronLeft, tr("Previous match", "पिछला परिणाम"))
                }
                IconButton(onClick = onNext, enabled = count > 1) {
                    Icon(Icons.Rounded.ChevronRight, tr("Next match", "अगला परिणाम"))
                }
            }
            if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed) Text(tr("Some PDF text could not be searched.", "कुछ PDF टेक्स्ट में खोज नहीं हो सकी।"), Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PdfSearchHighlights(
    text: PdfSelectableText,
    hits: List<PdfSearchHit>,
    active: PdfSearchHit?,
    aspect: Float,
) {
    val requester = remember { BringIntoViewRequester() }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val selected = active?.takeIf { it in hits }
    val rectangles = remember(text, hits) {
        hits.map { hit ->
            hit to text.glyphs.filter { it.start <= hit.range.last && it.end > hit.range.first }
        }
    }
    LaunchedEffect(selected, viewport, rectangles) {
        val first = rectangles.firstOrNull { it.first == selected }?.second?.firstOrNull()
            ?: return@LaunchedEffect
        if (viewport.width == 0 || viewport.height == 0) return@LaunchedEffect
        val width = minOf(viewport.width.toFloat(), viewport.height * aspect)
        val height = width / aspect
        val y = (viewport.height - height) / 2f + first.top * height
        requester.bringIntoView(Rect(0f, y, viewport.width.toFloat(), y + (first.bottom - first.top) * height))
    }
    Canvas(Modifier.fillMaxSize().onSizeChanged { viewport = it }.bringIntoViewRequester(requester)) {
        val width = minOf(size.width, size.height * aspect)
        val height = width / aspect
        val left = (size.width - width) / 2f
        val top = (size.height - height) / 2f
        for ((hit, glyphs) in rectangles) {
            val color = if (hit == selected) Color(0x99FF9200) else Color(0x88FFDF00)
            for (glyph in glyphs) {
                drawRect(
                    color,
                    Offset(left + glyph.left * width, top + glyph.top * height),
                    Size((glyph.right - glyph.left) * width, (glyph.bottom - glyph.top) * height),
                )
            }
        }
    }
}
