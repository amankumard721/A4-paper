package com.a4sizepaper.typing.texttopdf.ui

import android.content.Context
import androidx.compose.ui.geometry.Offset
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import java.io.File
import java.io.OutputStream

internal data class PdfMark(val page: Int, val highlight: Boolean, val points: List<Offset>)

internal fun writeMarkedPdf(context: Context, file: File, marks: List<PdfMark>, output: OutputStream) {
    PDFBoxResourceLoader.init(context.applicationContext)
    PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly().setTempDir(context.cacheDir)).use { doc ->
        require(!doc.isEncrypted && doc.currentAccessPermission.canModify()) { "This PDF does not allow changes." }
        require(doc.signatureDictionaries.isEmpty()) { "Marking this PDF would invalidate its signatures." }
        for ((index, pageMarks) in marks.groupBy { it.page }) {
            val page = doc.getPage(index)
            val box = page.cropBox
            val rotation = ((page.rotation % 360) + 360) % 360
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                for (mark in pageMarks) {
                    stream.saveGraphicsState()
                    if (mark.highlight) {
                        stream.setGraphicsStateParameters(PDExtendedGraphicsState().apply { strokingAlphaConstant = 0.38f })
                        stream.setStrokingColor(255, 193, 7)
                    } else stream.setStrokingColor(239, 68, 68)
                    val width = if (rotation == 90 || rotation == 270) box.height else box.width
                    stream.setLineWidth(width * if (mark.highlight) 0.017f else 0.004f)
                    stream.setLineCapStyle(1)
                    stream.setLineJoinStyle(1)
                    mark.points.forEachIndexed { pointIndex, point ->
                        val (x, y) = when (rotation) {
                            90 -> point.y to point.x
                            180 -> (1f - point.x) to point.y
                            270 -> (1f - point.y) to (1f - point.x)
                            else -> point.x to (1f - point.y)
                        }
                        val px = box.lowerLeftX + box.width * x
                        val py = box.lowerLeftY + box.height * y
                        if (pointIndex == 0) stream.moveTo(px, py) else stream.lineTo(px, py)
                    }
                    stream.stroke()
                    stream.restoreGraphicsState()
                }
            }
        }
        doc.save(output)
    }
}
