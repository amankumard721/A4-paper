package com.a4sizepaper.typing.texttopdf.data

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import jxl.Workbook
import jxl.format.Alignment
import jxl.format.Colour
import jxl.write.Formula
import jxl.write.Label
import jxl.write.Number
import jxl.write.WritableCellFormat
import jxl.write.WritableFont
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExcelReaderTest {
    @get:Rule val temporary = TemporaryFolder()
    private val namespace = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private val relationship = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private fun xlsx(
        rows: String,
        tail: String = "",
        extras: Map<String, ByteArray> = emptyMap(),
        workbook: String =
            """<workbook xmlns="$namespace" xmlns:r="$relationship"><sheets><sheet name="Sales नमस्ते" sheetId="1" r:id="sheet1"/></sheets></workbook>""",
        worksheet: String? = null,
    ): File {
        val file = temporary.newFile("${System.nanoTime()}.xlsx")
        val parts =
            linkedMapOf(
                "[Content_Types].xml" to
                    """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/></Types>"""
                        .toByteArray(),
                "xl/workbook.xml" to workbook.toByteArray(),
                "xl/_rels/workbook.xml.rels" to
                    """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="sheet1" Type="$relationship/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="styles" Type="$relationship/styles" Target="styles.xml"/><Relationship Id="strings" Type="$relationship/sharedStrings" Target="sharedStrings.xml"/><Relationship Id="sheet2" Type="$relationship/worksheet" Target="worksheets/sheet2.xml"/></Relationships>"""
                        .toByteArray(),
                "xl/worksheets/sheet1.xml" to
                    (worksheet
                            ?: """<worksheet xmlns="$namespace"><sheetData>$rows</sheetData>$tail</worksheet>""")
                        .toByteArray(),
                "xl/styles.xml" to
                    """<styleSheet xmlns="$namespace"><numFmts count="1"><numFmt numFmtId="164" formatCode="&quot;₹&quot; #,##0.00"/></numFmts><fonts count="2"><font><sz val="10"/></font><font><b/><i/><u/><color rgb="FF0066AA"/><sz val="11"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFFFFF00"/></patternFill></fill></fills><cellXfs count="5"><xf fontId="0" fillId="0" numFmtId="0"/><xf fontId="1" fillId="2" numFmtId="0"><alignment horizontal="center"/></xf><xf fontId="0" fillId="0" numFmtId="164"/><xf fontId="0" fillId="0" numFmtId="10"/><xf fontId="0" fillId="0" numFmtId="14"/></cellXfs></styleSheet>"""
                        .toByteArray(),
                "xl/sharedStrings.xml" to
                    """<sst xmlns="$namespace"><si><t>Revenue नमस्ते</t></si><si><r><t>Rich </t></r><r><t>text</t></r><rPh sb="0" eb="1"><t>not displayed</t></rPh></si></sst>"""
                        .toByteArray(),
            )
        parts.putAll(extras)
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, bytes) in parts) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    private fun cell(result: ExcelReader.Result, row: Int, column: Int): JSONObject =
        result.content
            .getJSONArray("content")
            .getJSONObject(1)
            .getJSONArray("content")
            .getJSONObject(row)
            .getJSONArray("content")
            .getJSONObject(column)

    private fun text(node: JSONObject): String = buildString {
        if (node.optString("type") == "text") append(node.getString("text"))
        node.optJSONArray("content")?.let { children ->
            repeat(children.length()) { append(text(children.getJSONObject(it))) }
        }
    }

    @Test
    fun importsUnicodeCachedFormulaStylesNumbersDatesAndHorizontalMerge() {
        val file =
            xlsx(
                """
            <row r="1"><c r="A1" t="s" s="1"><v>0</v></c><c r="C1" t="s"><v>1</v></c></row>
            <row r="2"><c r="A2"><v>2</v></c><c r="B2"><v>3</v></c><c r="C2" s="2"><f>A2+B2</f><v>5</v></c></row>
            <row r="3"><c r="A3" s="3"><v>0.125</v></c><c r="B3" s="4"><v>45292</v></c><c r="C3" t="inlineStr"><is><t>Line 1&#10;हिन्दी</t></is></c></row>
        """
                    .trimIndent(),
                "<mergeCells count=\"1\"><mergeCell ref=\"A1:B1\"/></mergeCells>",
                extras = mapOf("xl/drawings/drawing1.xml" to "<drawing/>".toByteArray()),
            )
        val result = ExcelReader().read(file)
        assertEquals("Revenue नमस्ते", text(cell(result, 0, 0)))
        assertEquals(2, cell(result, 0, 0).getJSONObject("attrs").getInt("colspan"))
        assertEquals(
            "#FFFF00",
            cell(result, 0, 0).getJSONObject("attrs").getString("backgroundColor"),
        )
        assertTrue(cell(result, 0, 0).toString().contains("#0066AA"))
        assertTrue(cell(result, 0, 0).toString().contains("bold"))
        assertEquals(
            "center",
            cell(result, 0, 0)
                .getJSONArray("content")
                .getJSONObject(0)
                .getJSONObject("attrs")
                .getString("textAlign"),
        )
        assertEquals("Rich text", text(cell(result, 0, 1)))
        assertEquals("₹ 5.00", text(cell(result, 1, 2)))
        assertEquals("12.50%", text(cell(result, 2, 0)))
        assertEquals("2024-01-01", text(cell(result, 2, 1)))
        assertTrue(cell(result, 2, 2).toString().contains("hardBreak"))
        assertTrue(result.warnings.any { it.contains("last saved") })
        assertTrue(result.warnings.any { it.contains("Charts") })
        val fixture = File("../validation/imports/excel-document.json")
        requireNotNull(fixture.parentFile).mkdirs()
        fixture.writeText(
            JSONObject()
                .put("title", "Imported Excel नमस्ते")
                .put("page", result.page.toJson())
                .put("content", result.content)
                .toString(),
            Charsets.UTF_8,
        )
    }

    @Test
    fun convertsEveryVisibleSheetAndSplitsWideSheets() {
        val workbook =
            """<workbook xmlns="$namespace" xmlns:r="$relationship"><sheets><sheet name="Wide" sheetId="1" r:id="sheet1"/><sheet name="Second" sheetId="2" r:id="sheet2"/></sheets></workbook>"""
        val rows =
            "<row r=\"1\">" +
                ('A'..'J').joinToString("") {
                    "<c r=\"${it}1\" t=\"inlineStr\"><is><t>Value $it</t></is></c>"
                } +
                "</row>"
        val result =
            ExcelReader()
                .read(
                    xlsx(
                        rows,
                        workbook = workbook,
                        extras =
                            mapOf(
                                "xl/worksheets/sheet2.xml" to
                                    """<worksheet xmlns="$namespace"><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>Second sheet हिन्दी</t></is></c></row></sheetData></worksheet>"""
                                        .toByteArray()
                            ),
                    )
                )
        val nodes = result.content.getJSONArray("content")
        assertEquals(
            2,
            (0 until nodes.length()).count {
                nodes.getJSONObject(it).optString("type") == "pageBreak"
            },
        )
        assertTrue(text(result.content).contains("Value J"))
        assertTrue(text(result.content).contains("Second sheet हिन्दी"))
        assertTrue(result.page.landscape)
        assertTrue(result.warnings.any { it.contains("8 columns") })
        val firstTable = nodes.getJSONObject(1)
        assertEquals(
            8,
            firstTable.getJSONArray("content").getJSONObject(0).getJSONArray("content").length(),
        )
    }

    @Test
    fun ignoresHiddenSheetsRowsAndColumns() {
        val workbook =
            """<workbook xmlns="$namespace" xmlns:r="$relationship"><sheets><sheet name="Visible" sheetId="1" r:id="sheet1"/><sheet name="Secret" sheetId="2" r:id="sheet2" state="veryHidden"/></sheets></workbook>"""
        val worksheet =
            """<worksheet xmlns="$namespace"><cols><col min="2" max="2" hidden="1"/></cols><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>Visible value</t></is></c><c r="B1" t="inlineStr"><is><t>Hidden column</t></is></c></row><row r="2" hidden="1"><c r="A2" t="inlineStr"><is><t>Hidden row</t></is></c></row></sheetData></worksheet>"""
        val result = ExcelReader().read(xlsx("", workbook = workbook, worksheet = worksheet))
        assertTrue(text(result.content).contains("Visible value"))
        assertFalse(text(result.content).contains("Hidden row"))
        assertFalse(text(result.content).contains("Hidden column"))
        assertTrue(result.warnings.any { it.contains("Hidden") })
    }

    @Test
    fun importsLegacyXlsWithCachedNumericFormulaUnicodeStylesAndMerge() {
        val file = temporary.newFile("legacy.xls")
        val workbook = Workbook.createWorkbook(file)
        val sheet = workbook.createSheet("Legacy हिन्दी", 0)
        val style =
            WritableCellFormat(WritableFont(WritableFont.ARIAL, 11, WritableFont.BOLD)).apply {
                setBackground(Colour.YELLOW)
                setAlignment(Alignment.CENTRE)
            }
        sheet.addCell(Label(0, 0, "नाम हिन्दी", style))
        sheet.mergeCells(0, 0, 1, 0)
        sheet.addCell(Number(0, 1, 10.0))
        sheet.addCell(Number(1, 1, 20.0))
        // JXL's writer stores a cached placeholder (4); the reader must not execute SUM (30).
        sheet.addCell(Formula(2, 1, "SUM(A2:B2)"))
        workbook.write()
        workbook.close()
        val result = ExcelReader().read(file)
        assertEquals("नाम हिन्दी", text(cell(result, 0, 0)))
        assertEquals(2, cell(result, 0, 0).getJSONObject("attrs").getInt("colspan"))
        assertEquals(
            "#FFFF00",
            cell(result, 0, 0).getJSONObject("attrs").getString("backgroundColor"),
        )
        assertEquals("4", text(cell(result, 1, 2)))
        assertTrue(result.warnings.any { it.contains("last saved") })
    }

    @Test
    fun importsQuotedUtf8CsvWithoutExecutingFormulas() {
        val file = temporary.newFile("quoted.csv")
        file.writeText(
            "\uFEFFनाम,Description,Formula\r\nहिन्दी,\"One, two\nthree \"\"quoted\"\"\",=SUM(A1:A3)\r\n",
            Charsets.UTF_8,
        )
        val result = ExcelReader().read(file)
        assertEquals("हिन्दी", text(cell(result, 1, 0)))
        assertEquals("One, twothree \"quoted\"", text(cell(result, 1, 1)))
        assertTrue(cell(result, 1, 1).toString().contains("hardBreak"))
        assertEquals("=SUM(A1:A3)", text(cell(result, 1, 2)))
    }

    @Test
    fun rejectsDtdTraversalMalformedXmlAndMissingFormulaCache() {
        assertThrows(Exception::class.java) {
            ExcelReader()
                .read(
                    xlsx(
                        "",
                        worksheet =
                            """<!DOCTYPE worksheet [<!ENTITY x SYSTEM "file:///private-data">]><worksheet><sheetData>&x;</sheetData></worksheet>""",
                    )
                )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExcelReader().read(xlsx("", extras = mapOf("../outside" to byteArrayOf(1))))
        }
        assertThrows(Exception::class.java) { ExcelReader().read(xlsx("<row>")) }
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                ExcelReader().read(xlsx("<row r=\"1\"><c r=\"A1\"><f>1+2</f></c></row>"))
            }
        assertTrue(error.message.orEmpty().contains("no saved result"))
    }

    @Test
    fun rejectsOversizedZipUsedRangeOverlappingMergesAndMalformedCsv() {
        assertThrows(IllegalArgumentException::class.java) {
            ExcelReader().read(xlsx("", extras = mapOf("xl/large" to ByteArray(13 * 1024 * 1024))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExcelReader().read(xlsx("<row r=\"2001\"><c r=\"A2001\"><v>5</v></c></row>"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExcelReader()
                .read(
                    xlsx(
                        "<row r=\"1\"><c r=\"A1\"><v>1</v></c></row>",
                        "<mergeCells><mergeCell ref=\"A1:B1\"/><mergeCell ref=\"B1:C1\"/></mergeCells>",
                    )
                )
        }
        val file = temporary.newFile("bad.csv").apply { writeText("a,\"unfinished") }
        assertThrows(IllegalArgumentException::class.java) { ExcelReader().read(file) }
    }

    @Test
    fun supports1904DatesAndSafeLiteralInlineStrings() {
        val workbook =
            """<workbook xmlns="$namespace" xmlns:r="$relationship"><workbookPr date1904="1"/><sheets><sheet name="Date" sheetId="1" r:id="sheet1"/></sheets></workbook>"""
        val result =
            ExcelReader()
                .read(
                    xlsx(
                        """<row r="1"><c r="A1" s="4"><v>0</v></c><c r="B1" t="inlineStr"><is><t>&lt;script&gt;alert('x')&lt;/script&gt;</t></is></c></row>""",
                        workbook = workbook,
                    )
                )
        assertEquals("1904-01-01", text(cell(result, 0, 0)))
        assertEquals("<script>alert('x')</script>", text(cell(result, 0, 1)))
    }
}
