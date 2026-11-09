package com.a4sizepaper.typing.texttopdf.data

import org.junit.Assert.*
import org.junit.Test

class ImportFormatTest {
    @Test fun namedExcelWorksWithGenericProviderMime() {
        assertEquals(ImportFormat.EXCEL, ImportFormat.resolve("REPORT.XLSX", "application/octet-stream", ImportFormat.EXCEL))
        assertEquals(ImportFormat.EXCEL, ImportFormat.resolve("report.xls", null, ImportFormat.EXCEL))
        assertEquals(ImportFormat.EXCEL, ImportFormat.resolve("report.csv", "text/plain", ImportFormat.EXCEL))
    }
    @Test fun wrongPickerCannotAcceptAnotherSupportedFormat() {
        assertThrows(IllegalArgumentException::class.java) { ImportFormat.resolve("report.pdf", "application/pdf", ImportFormat.WORD) }
        assertThrows(IllegalArgumentException::class.java) { ImportFormat.resolve("report.docx", null, ImportFormat.EXCEL) }
    }
    @Test fun wrongExtensionCannotBypassValidationWithProviderMime() {
        assertThrows(IllegalStateException::class.java) { ImportFormat.resolve("photo.jpg", ImportFormat.EXCEL.mimeTypes.first(), ImportFormat.EXCEL) }
    }
    @Test fun legacyWordGivesExplicitConversionMessage() {
        val error = assertThrows(IllegalStateException::class.java) { ImportFormat.resolve("letter.doc", "application/msword") }
        assertTrue(error.message.orEmpty().contains(".docx"))
    }
    @Test fun namelessProviderUsesSpecificMime() {
        assertEquals(ImportFormat.WORD, ImportFormat.resolve("", ImportFormat.WORD.mimeTypes.first(), ImportFormat.WORD))
    }
}
