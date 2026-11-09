package com.a4sizepaper.typing.texttopdf.data

import org.junit.Assert.*
import org.junit.Test

class PdfPageRangesTest {
    @Test
    fun preservesListedOrderAndExpandsRanges() {
        assertEquals(listOf(3, 0, 1), PdfImporter.parsePageRanges("4, 1 - 2", 5))
        assertEquals(listOf(0, 1, 2), PdfImporter.parsePageRanges(" ", 3))
        assertEquals(listOf(0), PdfImporter.parsePageRanges("01", 3))
    }

    @Test
    fun rejectsDuplicatesOutOfRangeMalformedAndOverflow() {
        for (value in
            listOf(
                "0",
                "6",
                "3-2",
                "1,1",
                "1-3,2",
                "1,",
                "1--2",
                "a",
                "1-999999999999999999999999",
                "999999999999999999999999",
                "-1",
            )) {
            assertThrows(value, IllegalArgumentException::class.java) {
                PdfImporter.parsePageRanges(value, 5)
            }
        }
        assertThrows(IllegalArgumentException::class.java) { PdfImporter.parsePageRanges("1", 301) }
        assertThrows(IllegalArgumentException::class.java) {
            PdfImporter.parsePageRanges(" ".repeat(2049), 3)
        }
    }

    @Test
    fun createsPlainUnicodeTextAndPageBreaksWithoutHtmlInterpretation() {
        val content =
            PdfImporter.textDocument("नमस्ते & <script>\nLine two\n\u000c\nSecond page\n\u000c\n")
                .getJSONArray("content")
        assertEquals(
            "नमस्ते & <script>",
            content.getJSONObject(0).getJSONArray("content").getJSONObject(0).getString("text"),
        )
        assertEquals("pageBreak", content.getJSONObject(2).getString("type"))
        assertEquals(4, content.length())
    }

    @Test
    fun excessiveTextFailsInsteadOfSilentlyTruncating() {
        assertThrows(IllegalArgumentException::class.java) {
            PdfImporter.textDocument("x".repeat(1_000_001))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfImporter.textDocument("x\n".repeat(20_001))
        }
    }
}
