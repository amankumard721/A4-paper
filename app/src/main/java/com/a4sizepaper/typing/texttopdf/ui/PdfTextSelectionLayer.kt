package com.a4sizepaper.typing.texttopdf.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.a4sizepaper.typing.texttopdf.pdf.PdfSelectableText
import kotlin.math.*

@Composable
internal fun PdfTextSelectionLayer(data: PdfSelectableText, aspectRatio: Float, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    var anchor by remember(data) { mutableIntStateOf(-1) }
    var end by remember(data) { mutableIntStateOf(-1) }
    var dragging by remember(data) { mutableStateOf(false) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    fun selected(): IntRange? = if (anchor >= 0 && end >= 0) min(anchor,end)..max(anchor,end) else null
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().pointerInput(data, aspectRatio) {
            val width = minOf(size.width.toFloat(), size.height * aspectRatio)
            val height = width / aspectRatio
            val left = (size.width-width)/2f
            val top = (size.height-height)/2f
            fun nearest(point: Offset, closeOnly: Boolean): Int {
                val x=(point.x-left)/width; val y=(point.y-top)/height
                val glyph = data.glyphs.minByOrNull {
                    val dx=maxOf(it.left-x, 0f, x-it.right)*width
                    val dy=maxOf(it.top-y, 0f, y-it.bottom)*height
                    dx*dx+dy*dy
                } ?: return -1
                if (closeOnly && (x < glyph.left-12.dp.toPx()/width || x > glyph.right+12.dp.toPx()/width || y < glyph.top-12.dp.toPx()/height || y > glyph.bottom+12.dp.toPx()/height)) return -1
                return glyph.start
            }
            detectDragGesturesAfterLongPress(
                onDragStart = { point ->
                    finger=point
                    val hit=nearest(point, true)
                    if (hit >= 0) {
                        var first=hit; var last=hit
                        while (first>0 && !data.text[first-1].isWhitespace()) first--
                        while (last<data.text.lastIndex && !data.text[last+1].isWhitespace()) last++
                        anchor=first; end=last; dragging=true
                    }
                },
                onDragEnd = { dragging=false },
                onDragCancel = { dragging=false },
            ) { change, delta ->
                if (anchor >= 0) { change.consume(); finger += delta; val hit=nearest(finger, false); if (hit>=0) end=hit }
            }
        }) {
            val selection=selected() ?: return@Canvas
            val width=minOf(size.width,size.height*aspectRatio); val height=width/aspectRatio
            val left=(size.width-width)/2; val top=(size.height-height)/2
            data.glyphs.filter { it.end>selection.first && it.start<=selection.last }.forEach {
                drawRect(Color(0x663F68EC), Offset(left+it.left*width,top+it.top*height), Size((it.right-it.left)*width,(it.bottom-it.top)*height))
            }
        }
        if (selected()!=null && !dragging) Surface(Modifier.align(Alignment.TopCenter).padding(4.dp), shadowElevation=4.dp, shape=MaterialTheme.shapes.large) {
            Row {
                TextButton(onClick={ selected()?.let { clipboard.setText(AnnotatedString(data.text.substring(it.first, (it.last+1).coerceAtMost(data.text.length)))) }; anchor=-1; end=-1 }) { Text(tr("Copy", "कॉपी")) }
                TextButton(onClick={ anchor=0; end=data.text.lastIndex }) { Text(tr("Select page", "पेज चुनें")) }
                TextButton(onClick={ anchor=-1; end=-1 }) { Text(tr("Close", "बंद करें")) }
            }
        }
    }
}
