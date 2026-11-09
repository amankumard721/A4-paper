package com.a4sizepaper.typing.texttopdf.ui

import com.a4sizepaper.typing.texttopdf.pdf.PdfTextSession
import com.a4sizepaper.typing.texttopdf.pdf.PdfSelectableText
import android.graphics.Bitmap
import com.a4sizepaper.typing.texttopdf.pdf.PdfRenderSession
import com.a4sizepaper.typing.texttopdf.pdf.RenderedPdfPage
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.a4sizepaper.typing.texttopdf.pdf.PdfMatchWriter
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

@Composable
internal fun PdfContinuousPages(
    file: File,
    pageCount: Int,
    state: LazyListState,
    marks: List<PdfMark>,
    modifier: Modifier,
    textSession: PdfTextSession,
    searchHits: List<PdfSearchHit>,
    activeHit: PdfSearchHit?,
) {
    var session by remember(file) { mutableStateOf<PdfRenderSession?>(null) }
    var openError by remember(file) { mutableStateOf(false) }
    var reopen by remember(file) { mutableIntStateOf(0) }
    LaunchedEffect(file, reopen) {
        var opened: PdfRenderSession? = null
        try {
            // Install the handle even if the opening coroutine is cancelled during native work.
            withContext(Dispatchers.IO + NonCancellable) { opened = PdfRenderSession(file) }
            ensureActive()
            session = opened
            awaitCancellation()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            openError = true
        } finally {
            session = null
            withContext(NonCancellable) { opened?.close() }
        }
    }
    val density = LocalDensity.current
    val hitsByPage = remember(searchHits) { searchHits.groupBy { it.page } }
    BoxWithConstraints(modifier) {
        val renderWidth = with(density) { (maxWidth - 12.dp).roundToPx() }.coerceIn(320, 1600)
        val visibleInfo = state.layoutInfo.visibleItemsInfo
        val firstIndex = visibleInfo.firstOrNull()?.index ?: 0
        val lastIndex = visibleInfo.lastOrNull()?.index ?: 0
        val pillText = if (firstIndex == lastIndex) "${firstIndex + 1}/$pageCount"
                       else "${firstIndex + 1}–${lastIndex + 1}/$pageCount"
        var isScrolling by remember { mutableStateOf(false) }
        LaunchedEffect(state.isScrollInProgress) {
            if (state.isScrollInProgress) {
                isScrolling = true
            } else {
                delay(1200)
                isScrolling = false
            }
        }
        if (openError) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tr("Could not open this PDF", "यह PDF नहीं खुल सकी"))
                TextButton(onClick = { openError = false; reopen++ }) { Text(tr("Retry", "फिर कोशिश करें")) }
            }
            return@BoxWithConstraints
        }
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 6.dp, end = 6.dp, bottom = 88.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(pageCount) { index ->
                var rendered by remember(file, index, renderWidth) { mutableStateOf<RenderedPdfPage?>(null) }
                var failed by remember(file, index) { mutableStateOf(false) }
                var retry by remember(file, index) { mutableIntStateOf(0) }
                LaunchedEffect(session, index, renderWidth, retry) {
                    val reader = session ?: return@LaunchedEffect
                    failed = false
                    try {
                        rendered = reader.render(index, renderWidth)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        failed = true
                    }
                }
                var selectable by remember(file, index) { mutableStateOf<PdfSelectableText?>(null) }
                LaunchedEffect(textSession, index) {
                    try { selectable = textSession.page(index) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { /* A protected/unsupported page remains readable. */ }
                }
                val image = rendered?.bitmap
                Box(
                    Modifier.fillMaxWidth()
                        .height(((maxWidth.value - 12f) / (rendered?.aspectRatio ?: 0.707f)).coerceIn(1f, 20_000f).dp)
                        .background(AppTheme.colors.paper)
                        ,
                    contentAlignment = Alignment.Center,
                ) {
                    if (image != null) {
                        Image(image.asImageBitmap(), tr("Page ${index + 1}", "पेज ${index + 1}"), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        val pageHits = hitsByPage[index].orEmpty()
                        val textData = selectable
                        if (textData != null && pageHits.isNotEmpty()) {
                            PdfSearchHighlights(textData, pageHits, activeHit, image.width.toFloat() / image.height)
                        }
                        selectable?.takeIf { it.glyphs.isNotEmpty() }?.let {
                            PdfTextSelectionLayer(it, rendered!!.aspectRatio, Modifier.fillMaxSize())
                        }
                        val pageMarks = marks.filter { it.page == index }
                        if (pageMarks.isNotEmpty()) Canvas(Modifier.fillMaxSize()) {
                            val fit = minOf(size.width / image.width, size.height / image.height)
                            val drawWidth = image.width * fit
                            val drawHeight = image.height * fit
                            val left = (size.width - drawWidth) / 2f
                            val top = (size.height - drawHeight) / 2f
                            for (mark in pageMarks) {
                                val path = Path()
                                mark.points.forEachIndexed { i, point ->
                                    if (i == 0) path.moveTo(left + point.x * drawWidth, top + point.y * drawHeight)
                                    else path.lineTo(left + point.x * drawWidth, top + point.y * drawHeight)
                                }
                                drawPath(path, if (mark.highlight) Color(0x60FFC107) else Color(0xFFEF4444),
                                    style = Stroke(drawWidth * if (mark.highlight) 0.017f else 0.004f, cap = StrokeCap.Round))
                            }
                        }
                    } else if (failed) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tr("Could not display this page", "यह पेज नहीं खुल सका"), color = LightColors.ink)
                        TextButton(onClick = { retry++ }) { Text(tr("Retry", "फिर कोशिश करें"), color = LightColors.accentText) }
                    }
                    else CircularProgressIndicator(Modifier.size(28.dp))
                }
            }
        }
        AnimatedVisibility(
            visible = isScrolling && pageCount > 1,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 4.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = pillText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Icon(
                        Icons.Rounded.UnfoldMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

