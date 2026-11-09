package com.a4sizepaper.typing.texttopdf.pdf

import org.junit.Assert.*
import org.junit.Test

class PdfReaderTest {
    @Test fun rendersDifferentPaperSizesWithinAMemoryBudget() {
        for ((width, height) in listOf(595 to 842, 842 to 595, 612 to 792, 612 to 1008,
            2384 to 3370, 100 to 10000, 10000 to 100, 1 to Int.MAX_VALUE, Int.MAX_VALUE to 1)) {
            val size = pdfRasterSize(width, height, 1400)
            assertTrue(size.width in 1..1600)
            assertTrue(size.height in 1..2400)
            assertTrue(size.width.toLong() * size.height * 4 <= 1600L * 2400 * 4)
            assertTrue(kotlin.math.abs(width * size.scale - size.width) <= 1.01f)
            assertTrue(kotlin.math.abs(height * size.scale - size.height) <= 1.01f)
        }
        assertThrows(IllegalArgumentException::class.java) { pdfRasterSize(0, 100, 800) }
    }
    @Test fun findsCaseInsensitiveAndHindiMatchesAcrossWriterChunks() {
        val english = PdfMatchWriter("hello world")
        english.write("prefix HELLO ")
        english.write("WOR")
        english.write("LD suffix")
        assertTrue(english.matched)
        val wrapped = PdfMatchWriter("hello world")
        wrapped.write("hello\n")
        wrapped.write("  \tworld")
        assertTrue(wrapped.matched)
        val hindi = PdfMatchWriter("नमस्ते")
        hindi.write("नम")
        hindi.write("स्ते")
        assertTrue(hindi.matched)
        val absent = PdfMatchWriter("missing")
        repeat(1000) { absent.write("readable text ".repeat(100)) }
        assertTrue(absent.hasText)
        assertFalse(absent.matched)
        val blank = PdfMatchWriter("x")
        blank.write(" \n\t")
        assertFalse(blank.hasText)
    }
}
