package com.a4sizepaper.typing.texttopdf.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.a4sizepaper.typing.texttopdf.domain.Document
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Copies picker content into a bounded private staging file and converts it offline. */
class ExcelImporter(private val context: Context) {
    data class Imported(val document: Document, val warnings: List<String>)

    suspend fun import(uri: Uri): Imported =
        readSource(uri) { staging, extension, name ->
            val result = ExcelReader().read(staging, extension)
            Imported(
                Document(title = name, content = result.content.toString(), page = result.page),
                result.warnings,
            )
        }

    internal suspend fun openGrid(uri: Uri): SheetBook =
        readSource(uri) { staging, extension, name ->
            ExcelReader().readGrid(staging, extension, name)
        }

    private suspend fun <T> readSource(uri: Uri, read: (File, String, String) -> T): T =
        withContext(Dispatchers.IO) {
            require(uri.scheme == "content") { "Choose the Excel file using the document picker." }
            val name =
                context.contentResolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    ?: "Spreadsheet.xlsx"
            val namedExtension = name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
            val extension =
                if (namedExtension in setOf("xlsx", "xls", "csv")) namedExtension
                else
                    when (context.contentResolver.getType(uri)) {
                        "application/vnd.ms-excel" -> "xls"
                        "text/csv",
                        "text/comma-separated-values" -> "csv"
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ->
                            "xlsx"
                        else -> namedExtension
                    }
            require(extension in setOf("xlsx", "xls", "csv")) {
                "Choose an .xlsx, .xls or UTF-8 .csv spreadsheet. Save other formats as .xlsx in Excel."
            }
            val staging = File.createTempFile("excel-import-", ".$extension", context.cacheDir)
            try {
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "The selected spreadsheet is unavailable." }
                    staging.outputStream().use { output ->
                        val buffer = ByteArray(16384)
                        var total = 0L
                        while (true) {
                            ensureActive()
                            val size = input.read(buffer)
                            if (size < 0) break
                            total += size
                            require(total <= ExcelReader.MAX_FILE_BYTES) {
                                "Choose a spreadsheet smaller than 20 MB."
                            }
                            output.write(buffer, 0, size)
                        }
                    }
                }
                ensureActive()
                read(
                    staging,
                    extension,
                    name.substringBeforeLast('.').trim().ifEmpty { "Spreadsheet" }.take(200),
                )
            } finally {
                staging.delete()
            }
        }
}
