package com.a4sizepaper.typing.texttopdf.data

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class GridSheet(val name: String, val cells: Map<Pair<Int, Int>, String>)

internal data class SheetBook(
    val title: String,
    val sheets: List<GridSheet>,
    val warnings: List<String>,
    val dirty: Boolean = false,
)

internal fun sheetColumn(index: Int): String {
    var value = index + 1
    return buildString {
            while (value > 0) {
                value--
                append(('A'.code + value % 26).toChar())
                value /= 26
            }
        }
        .reversed()
}

/** Writes an editable values-only workbook without evaluating formulas. */
internal fun writeGridWorkbook(book: SheetBook, output: OutputStream) {
    val spreadsheet = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    val office = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    val packageRels = "http://schemas.openxmlformats.org/package/2006/relationships"
    ZipOutputStream(output).use { zip ->
        fun part(name: String, xml: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(xml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        part(
            "[Content_Types].xml",
            buildString {
                append(
                    """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>"""
                )
                book.sheets.indices.forEach {
                    append(
                        """<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
                    )
                }
                append("</Types>")
            },
        )
        part(
            "_rels/.rels",
            """<Relationships xmlns="$packageRels"><Relationship Id="rId1" Type="$office/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
        )
        part(
            "xl/workbook.xml",
            buildString {
                append("""<workbook xmlns="$spreadsheet" xmlns:r="$office"><sheets>""")
                book.sheets.forEachIndexed { i, sheet ->
                    append(
                        """<sheet name="${xmlValue(sheet.name.take(31))}" sheetId="${i + 1}" r:id="rId${i + 1}"/>"""
                    )
                }
                append("</sheets></workbook>")
            },
        )
        part(
            "xl/_rels/workbook.xml.rels",
            buildString {
                append("""<Relationships xmlns="$packageRels">""")
                book.sheets.indices.forEach {
                    append(
                        """<Relationship Id="rId${it + 1}" Type="$office/worksheet" Target="worksheets/sheet${it + 1}.xml"/>"""
                    )
                }
                append("</Relationships>")
            },
        )
        book.sheets.forEachIndexed { index, sheet ->
            part(
                "xl/worksheets/sheet${index + 1}.xml",
                buildString {
                    append("""<worksheet xmlns="$spreadsheet"><sheetData>""")
                    sheet.cells.entries
                        .groupBy { it.key.first }
                        .toSortedMap()
                        .forEach { (row, cells) ->
                            append("""<row r="${row + 1}">""")
                            cells
                                .sortedBy { it.key.second }
                                .forEach { (position, value) ->
                                    val ref = sheetColumn(position.second) + (row + 1)
                                    append(
                                        """<c r="$ref" t="inlineStr"><is><t xml:space="preserve">${xmlValue(value)}</t></is></c>"""
                                    )
                                }
                            append("</row>")
                        }
                    append("</sheetData></worksheet>")
                },
            )
        }
    }
}

private fun xmlValue(value: String) = buildString {
    var index = 0
    while (index < value.length) {
        val point = value.codePointAt(index)
        index += Character.charCount(point)
        when (point) {
            38 -> append("&amp;")
            60 -> append("&lt;")
            62 -> append("&gt;")
            34 -> append("&quot;")
            13 -> append("&#13;")
            9,
            10,
            in 32..0xD7FF,
            in 0xE000..0xFFFD,
            in 0x10000..0x10FFFF -> appendCodePoint(point)
        }
    }
}
