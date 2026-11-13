package com.a4sizepaper.typing.texttopdf.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

enum class ImportFormat(val label: String, val mimeTypes: Array<String>) {
    WORD(
        "Word",
        arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    ),
    EXCEL(
        "Excel",
        arrayOf(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "text/csv",
            "text/comma-separated-values",
        ),
    ),
    PDF("PDF", arrayOf("application/pdf"));

    companion object {
        val allMimeTypes
            get() = entries.flatMap { it.mimeTypes.toList() }.toTypedArray()

        fun detect(context: Context, uri: Uri): ImportFormat {
            require(uri.scheme == "content") { "Choose a file with the document picker." }
            val name =
                context.contentResolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0).orEmpty() else "" }
                    .orEmpty()
            return resolve(name, context.contentResolver.getType(uri))
        }

        /** A known filename wins over a provider's sometimes generic or incorrect MIME type. */
        internal fun resolve(name: String, mime: String?, expected: ImportFormat? = null): ImportFormat {
            val extension = name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
            val actual = when (extension) {
                "doc" -> error("Old Word .doc files are not supported. Save as .docx first.")
                "docx" -> WORD
                "xlsx", "xls", "csv" -> EXCEL
                "pdf" -> PDF
                "" -> entries.firstOrNull { mime?.substringBefore(';')?.trim()?.lowercase() in it.mimeTypes }
                    ?: error("Choose a Word (.docx), Excel (.xlsx/.xls/.csv) or PDF file.")
                else -> error("Choose a Word (.docx), Excel (.xlsx/.xls/.csv) or PDF file.")
            }
            require(expected == null || actual == expected) {
                when (expected) {
                    WORD -> "Choose a Word (.docx) file from the Word import button."
                    EXCEL -> "Choose an Excel (.xlsx/.xls/.csv) file from the Excel import button."
                    else -> "Choose a PDF (.pdf) file from the PDF import button."
                }
            }
            return actual
        }

        fun validate(context: Context, uri: Uri, expected: ImportFormat?): ImportFormat {
            val actual = detect(context, uri)
            require(expected == null || actual == expected) {
                "Choose a ${expected?.label} file for this import button."
            }
            return actual
        }
    }
}

data class PendingImport(
    val uri: Uri,
    val format: ImportFormat,
    val external: Boolean = false,
    val openPdfMarkup: Boolean = false,
)
