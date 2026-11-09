package com.a4sizepaper.typing.texttopdf.data

import com.a4sizepaper.typing.texttopdf.domain.PageSettings
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory
import jxl.Workbook
import jxl.WorkbookSettings
import org.json.JSONArray
import org.json.JSONObject
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.ext.DefaultHandler2

/** Bounded spreadsheet-to-editable-table conversion. Never calculates formulas or follows links. */
class ExcelReader {
    data class Result(val content: JSONObject, val page: PageSettings, val warnings: List<String>)

    private data class Xml(
        val name: String,
        val attrs: Map<String, String>,
        val children: MutableList<Xml> = mutableListOf(),
        val text: StringBuilder = StringBuilder(),
    ) {
        fun child(name: String) = children.firstOrNull { it.name == name }

        fun all(name: String): List<Xml> =
            children.flatMap { (if (it.name == name) listOf(it) else emptyList()) + it.all(name) }

        fun textValue(): String = text.toString() + children.joinToString("") { it.textValue() }
    }

    private data class Style(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val color: String? = null,
        val fill: String? = null,
        val align: String = "left",
        val fontSize: Int = 10,
        val numberFormat: String = "General",
    )

    private data class Cell(val text: String, val style: Style = Style())

    private data class Merge(val top: Int, val left: Int, val bottom: Int, val right: Int)

    private data class Sheet(
        val name: String,
        val cells: Map<Pair<Int, Int>, Cell>,
        val widths: Map<Int, Double> = emptyMap(),
        val merges: List<Merge> = emptyList(),
        val hiddenRows: Set<Int> = emptySet(),
        val hiddenColumns: Set<Int> = emptySet(),
    )

    private data class Rel(val target: String, val type: String, val external: Boolean)

    private val warnings = linkedSetOf<String>()
    private var cellCount = 0
    private var textCount = 0

    fun read(file: File, extension: String = file.extension.lowercase(Locale.ROOT)): Result {
        return convert(readSheets(file, extension))
    }

    internal fun readGrid(file: File, extension: String, title: String): SheetBook {
        val sheets = readSheets(file, extension)
        require(sheets.isNotEmpty()) { "This workbook has no visible worksheets." }
        return SheetBook(
            title,
            sheets.map { sheet ->
                GridSheet(
                    sheet.name,
                    sheet.cells
                        .filterKeys {
                            it.first !in sheet.hiddenRows && it.second !in sheet.hiddenColumns
                        }
                        .mapValues { it.value.text },
                )
            },
            warnings.toList(),
        )
    }

    internal fun exportGrid(book: SheetBook): Result {
        warnings.clear()
        return convert(
            book.sheets.map { grid -> Sheet(grid.name, grid.cells.mapValues { Cell(it.value) }) }
        )
    }

    private fun readSheets(file: File, extension: String): List<Sheet> {
        require(file.length() in 1..MAX_FILE_BYTES) { "Choose a spreadsheet smaller than 20 MB." }
        warnings.clear()
        cellCount = 0
        textCount = 0
        return when (extension.lowercase(Locale.ROOT)) {
            "xlsx" -> readXlsx(file)
            "xls" -> readXls(file)
            "csv" -> readCsv(file)
            else ->
                throw IllegalArgumentException("Choose an .xlsx, .xls or UTF-8 .csv spreadsheet.")
        }
    }

    private fun convert(source: List<Sheet>): Result {
        val sheets =
            source.filter { sheet ->
                sheet.cells.any { (position, cell) ->
                    cell.text.isNotEmpty() &&
                        position.first !in sheet.hiddenRows &&
                        position.second !in sheet.hiddenColumns
                }
            }
        require(sheets.isNotEmpty()) { "This spreadsheet has no visible cell values to import." }
        val wide = sheets.any { sheet -> visibleColumns(sheet).size > 5 }
        val page =
            PageSettings(landscape = wide, fontSize = 10, lineHeight = 1.15, paragraphSpacing = 4)
        val nodes = mutableListOf<JSONObject>()
        var outputCells = 0L
        for (sheet in sheets) {
            val columns = visibleColumns(sheet)
            val rows = visibleRows(sheet)
            outputCells += rows.size.toLong() * columns.size
            require(outputCells <= MAX_OUTPUT_CELLS) {
                "This workbook is too sparse or too large to convert. Keep the used tables within 20,000 cells in total."
            }
            if (columns.size > COLUMNS_PER_PAGE)
                warnings +=
                    "Wide worksheets are split into groups of up to 8 columns for readable A4 pages."
            if (sheet.hiddenRows.isNotEmpty() || sheet.hiddenColumns.isNotEmpty())
                warnings += "Hidden worksheets, rows and columns are not imported."
            if (sheet.merges.any { it.top != it.bottom })
                warnings +=
                    "Vertically merged cells are expanded into ordinary cells so tables can continue across PDF pages."
            for (group in columns.chunked(COLUMNS_PER_PAGE)) {
                if (nodes.isNotEmpty()) nodes += node("pageBreak")
                val title =
                    if (columns.size <= COLUMNS_PER_PAGE) sheet.name
                    else "${sheet.name} · ${columnName(group.first())}–${columnName(group.last())}"
                nodes +=
                    node("heading", listOf(text(title))).put("attrs", JSONObject().put("level", 2))
                nodes += table(sheet, rows, group, page)
                nodes += node("paragraph")
            }
        }
        val content = node("doc", nodes)
        require(content.toString().length <= 8_000_000) {
            "The converted spreadsheet is too large."
        }
        return Result(content, page, warnings.toList())
    }

    private fun visibleRows(sheet: Sheet): List<Int> {
        val values =
            sheet.cells.filter {
                it.value.text.isNotEmpty() &&
                    it.key.second !in sheet.hiddenColumns &&
                    it.key.first !in sheet.hiddenRows
            }
        val first = values.keys.minOfOrNull { it.first } ?: return emptyList()
        val last =
            maxOf(values.keys.maxOf { it.first }, sheet.merges.maxOfOrNull { it.bottom } ?: 0)
        return (first..last).filterNot { it in sheet.hiddenRows }
    }

    private fun visibleColumns(sheet: Sheet): List<Int> {
        val values =
            sheet.cells.filter {
                it.value.text.isNotEmpty() &&
                    it.key.first !in sheet.hiddenRows &&
                    it.key.second !in sheet.hiddenColumns
            }
        val first = values.keys.minOfOrNull { it.second } ?: return emptyList()
        val last =
            maxOf(values.keys.maxOf { it.second }, sheet.merges.maxOfOrNull { it.right } ?: 0)
        return (first..last).filterNot { it in sheet.hiddenColumns }
    }

    private fun table(
        sheet: Sheet,
        rows: List<Int>,
        columns: List<Int>,
        page: PageSettings,
    ): JSONObject {
        val available = ((page.widthMm - page.marginLeft - page.marginRight) * 96 / 25.4).toInt()
        val relative = columns.map { (sheet.widths[it] ?: 80.0).coerceIn(36.0, 500.0) }
        // Reserve the minimum cell width before distributing the rest proportionally.
        val extra = (available - 36 * columns.size).coerceAtLeast(0)
        val widths = relative.map { 36 + (extra * it / relative.sum()).toInt() }
        val merges = sheet.merges.filter { it.top == it.bottom }.associateBy { it.top to it.left }
        val covered = mutableSetOf<Pair<Int, Int>>()
        val tableRows =
            rows.map { row ->
                val cells = mutableListOf<JSONObject>()
                for ((index, column) in columns.withIndex()) {
                    if ((row to column) in covered) continue
                    val merge = merges[row to column]
                    val span =
                        if (merge != null && (merge.left..merge.right).all { it in columns })
                            merge.right - merge.left + 1
                        else 1
                    if (merge != null && span == 1 && merge.right != merge.left)
                        warnings +=
                            "Merged cells crossing a hidden column or page group are expanded into ordinary cells."
                    if (span > 1) for (c in column + 1 until column + span) covered += row to c
                    val cell = sheet.cells[row to column] ?: Cell("")
                    val attrs =
                        JSONObject()
                            .put("colspan", span)
                            .put("rowspan", 1)
                            .put("colwidth", JSONArray(widths.subList(index, index + span)))
                    cell.style.fill?.let { attrs.put("backgroundColor", it) }
                    val paragraphAttrs =
                        JSONObject()
                            .put("textAlign", cell.style.align)
                            .put("lineHeight", 1.15)
                            .put("paragraphSpacing", 2)
                    val paragraph = node("paragraph", lines(cell)).put("attrs", paragraphAttrs)
                    cells += node("tableCell", listOf(paragraph)).put("attrs", attrs)
                }
                node("tableRow", cells)
            }
        return node("table", tableRows)
    }

    private fun lines(cell: Cell): List<JSONObject> = buildList {
        cell.text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEachIndexed { index, line
            ->
            if (index > 0) add(node("hardBreak"))
            if (line.isNotEmpty()) {
                val marks = JSONArray()
                if (cell.style.bold) marks.put(node("bold"))
                if (cell.style.italic) marks.put(node("italic"))
                if (cell.style.underline) marks.put(node("underline"))
                val attrs = JSONObject().put("fontSize", "${cell.style.fontSize}pt")
                cell.style.color?.let { attrs.put("color", it) }
                marks.put(node("textStyle").put("attrs", attrs))
                add(text(line).put("marks", marks))
            }
        }
    }

    private fun addCell(cells: MutableMap<Pair<Int, Int>, Cell>, row: Int, col: Int, cell: Cell) {
        require(row in 0 until MAX_ROWS && col in 0 until MAX_COLUMNS) {
            "Import supports up to 2,000 used rows and 64 columns per worksheet."
        }
        require(++cellCount <= MAX_INPUT_CELLS) {
            "Import supports up to 20,000 cells in one workbook."
        }
        require(cell.text.length <= 32_767) { "A spreadsheet cell contains too much text." }
        textCount += cell.text.length
        require(textCount <= 2_000_000) { "The spreadsheet contains too much text to import." }
        require(cells.put(row to col, cell) == null) {
            "This spreadsheet contains duplicate cells."
        }
    }

    private fun readXlsx(file: File): List<Sheet> {
        val parts = unzip(file)
        require(parts["[Content_Types].xml"] != null) { "This is not a supported .xlsx workbook." }
        val rootRelationships = relationships(parts, "")
        val workbookPart =
            rootRelationships.values
                .firstOrNull { !it.external && it.type.endsWith("/officeDocument") }
                ?.target ?: "xl/workbook.xml"
        val workbook =
            xml(
                parts[workbookPart]
                    ?: throw IllegalArgumentException(
                        "This Excel file is damaged, encrypted or unsupported. Save an unprotected .xlsx copy in Excel."
                    )
            )
        require(workbook.name == "workbook") { "This file is not an Excel workbook." }
        val relations = relationships(parts, workbookPart)
        fun related(kind: String) =
            relations.values
                .firstOrNull { !it.external && it.type.endsWith("/$kind") }
                ?.let { rel -> parts[rel.target]?.let(::xml) }
        val shared =
            related("sharedStrings")
                ?.children
                ?.filter { it.name == "si" }
                ?.map { value ->
                    // Phonetic ruby text is an annotation, not part of the displayed cell value.
                    value.children
                        .filter { it.name == "t" || it.name == "r" }
                        .joinToString("") { child ->
                            if (child.name == "t") child.textValue()
                            else
                                child.children
                                    .filter { it.name == "t" }
                                    .joinToString("") { it.textValue() }
                        }
                }
                .orEmpty()
        require(shared.size <= MAX_INPUT_CELLS * 2) { "This workbook has too many shared strings." }
        val styles = readStyles(related("styles"))
        val date1904 = workbook.child("workbookPr")?.attrs?.get("date1904") in setOf("1", "true")
        val sheetElements =
            workbook.child("sheets")?.children?.filter { it.name == "sheet" }.orEmpty()
        require(sheetElements.size in 1..MAX_SHEETS) {
            "Import supports up to 20 worksheets per workbook."
        }
        if (parts.keys.any { it.endsWith("vbaProject.bin") })
            warnings += "Macros are not imported or run."
        if (
            parts.keys.any {
                it.contains("/charts/") || it.contains("/drawings/") || it.contains("/media/")
            }
        )
            warnings +=
                "Charts, drawings and embedded images are not included; worksheet cell values are imported."
        if (
            relations.values.any { it.external } ||
                parts.keys.any { it.contains("/externalLinks/") }
        )
            warnings += "External links are not opened; only saved cell values are imported."
        val result = mutableListOf<Sheet>()
        for (sheet in sheetElements) {
            if (sheet.attrs["state"] in setOf("hidden", "veryHidden")) {
                warnings += "Hidden worksheets, rows and columns are not imported."
                continue
            }
            val rel =
                relations[sheet.attrs["id"]]
                    ?: throw IllegalArgumentException("A worksheet relationship is missing.")
            if (!rel.type.endsWith("/worksheet")) {
                warnings += "Chart sheets and other non-worksheet tabs are not imported."
                continue
            }
            require(!rel.external) { "An external worksheet cannot be imported." }
            val root =
                xml(parts[rel.target] ?: throw IllegalArgumentException("A worksheet is missing."))
            require(root.name == "worksheet") { "A worksheet is malformed." }
            val cells = linkedMapOf<Pair<Int, Int>, Cell>()
            val hiddenRows = mutableSetOf<Int>()
            val hiddenColumns = mutableSetOf<Int>()
            val widths = mutableMapOf<Int, Double>()
            val columnStyles = mutableMapOf<Int, Int>()
            for (col in root.child("cols")?.children.orEmpty().filter { it.name == "col" }) {
                val from = (col.attrs["min"]?.toIntOrNull() ?: 1) - 1
                val to = (col.attrs["max"]?.toIntOrNull() ?: 1) - 1
                require(from >= 0 && to >= from && to < 16384) { "Invalid worksheet column range." }
                for (c in from..minOf(to, MAX_COLUMNS - 1)) {
                    col.attrs["width"]
                        ?.toDoubleOrNull()
                        ?.takeIf { it.isFinite() }
                        ?.let { widths[c] = it * 7 + 5 }
                    col.attrs["style"]?.toIntOrNull()?.let { columnStyles[c] = it }
                    if (col.attrs["hidden"] in setOf("1", "true")) hiddenColumns += c
                }
            }
            var impliedRow = 0
            for (row in root.child("sheetData")?.children.orEmpty().filter { it.name == "row" }) {
                val rowNumber = row.attrs["r"]?.toIntOrNull()?.minus(1) ?: impliedRow
                require(rowNumber in 0..1_048_575) { "Invalid worksheet row number." }
                impliedRow = rowNumber + 1
                if (row.attrs["hidden"] in setOf("1", "true")) hiddenRows += rowNumber
                var impliedColumn = 0
                for (cell in row.children.filter { it.name == "c" }) {
                    val position =
                        cell.attrs["r"]?.let(::coordinate) ?: (rowNumber to impliedColumn)
                    require(position.first == rowNumber) {
                        "A worksheet has an invalid cell coordinate."
                    }
                    impliedColumn = position.second + 1
                    val value = cell.child("v")?.textValue().orEmpty()
                    val formula = cell.child("f")
                    if (formula != null) {
                        warnings +=
                            "Formulas use their last saved results and are not recalculated. Save in Excel before importing for current values."
                        require(cell.child("v") != null || cell.child("is") != null) {
                            "A formula has no saved result. Recalculate and save this workbook in Excel, then import again."
                        }
                    }
                    val styleIndex =
                        cell.attrs["s"]?.toIntOrNull()
                            ?: row.attrs["s"]?.toIntOrNull()
                            ?: columnStyles[position.second]
                            ?: 0
                    val style =
                        styles.getOrNull(styleIndex)
                            ?: throw IllegalArgumentException("A cell refers to a missing style.")
                    val type = cell.attrs["t"]
                    val content =
                        when (type) {
                            "s" ->
                                shared.getOrNull(value.toIntOrNull() ?: -1)
                                    ?: throw IllegalArgumentException(
                                        "A cell refers to a missing shared string."
                                    )
                            "inlineStr" ->
                                cell
                                    .child("is")
                                    ?.children
                                    ?.filter { it.name == "t" || it.name == "r" }
                                    ?.joinToString("") {
                                        if (it.name == "t") it.textValue()
                                        else it.child("t")?.textValue().orEmpty()
                                    }
                                    .orEmpty()
                            "b" ->
                                if (value == "1") "TRUE"
                                else if (value == "0") "FALSE"
                                else throw IllegalArgumentException("Invalid Boolean cell value.")
                            "str",
                            "e",
                            "d" -> value
                            null,
                            "n" ->
                                if (value.isEmpty()) ""
                                else formatNumber(value, style.numberFormat, date1904)
                            else ->
                                throw IllegalArgumentException("Unsupported Excel cell type: $type")
                        }
                    // Empty formatted cells can extend millions of rows; they are not document
                    // content.
                    if (content.isNotEmpty())
                        addCell(
                            cells,
                            position.first,
                            position.second,
                            Cell(
                                content,
                                style.copy(
                                    align =
                                        if (style.align == "general") {
                                            if (type == null || type == "n") "right"
                                            else if (type == "b") "center" else "left"
                                        } else style.align
                                ),
                            ),
                        )
                }
            }
            val merges =
                root
                    .child("mergeCells")
                    ?.children
                    .orEmpty()
                    .filter { it.name == "mergeCell" }
                    .map { cell ->
                        val refs = cell.attrs["ref"].orEmpty().split(':')
                        require(refs.size in 1..2) { "Invalid merged cell reference." }
                        val start = coordinate(refs.first())
                        val end = coordinate(refs.last())
                        require(
                            end.first >= start.first &&
                                end.second >= start.second &&
                                end.first < MAX_ROWS &&
                                end.second < MAX_COLUMNS
                        ) {
                            "A merged cell extends beyond the import limits."
                        }
                        Merge(start.first, start.second, end.first, end.second)
                    }
            require(merges.size <= 2000) { "This worksheet has too many merged cells." }
            validateMerges(merges)
            if (root.child("conditionalFormatting") != null)
                warnings +=
                    "Conditional formatting, validation rules, filters and print settings are not reproduced."
            result +=
                Sheet(
                    sheet.attrs["name"].orEmpty().ifEmpty { "Worksheet" }.take(200),
                    cells,
                    widths,
                    merges,
                    hiddenRows,
                    hiddenColumns,
                )
        }
        return result
    }

    private fun readStyles(root: Xml?): List<Style> {
        if (root == null) return listOf(Style(align = "general"))
        val formats =
            root.child("numFmts")?.children.orEmpty().associate {
                (it.attrs["numFmtId"]?.toIntOrNull() ?: -1) to it.attrs["formatCode"].orEmpty()
            }
        val fonts = root.child("fonts")?.children.orEmpty()
        val fills = root.child("fills")?.children.orEmpty()
        val styles =
            root.child("cellXfs")?.children.orEmpty().map { xf ->
                val font = fonts.getOrNull(xf.attrs["fontId"]?.toIntOrNull() ?: 0)
                val fill =
                    fills.getOrNull(xf.attrs["fillId"]?.toIntOrNull() ?: 0)?.child("patternFill")
                val align = xf.child("alignment")?.attrs?.get("horizontal") ?: "general"
                val fontSize = font?.child("sz")?.attrs?.get("val")?.toDoubleOrNull()?.toInt() ?: 10
                val id = xf.attrs["numFmtId"]?.toIntOrNull() ?: 0
                Style(
                        bold =
                            font?.child("b")?.attrs?.get("val") !in setOf("0", "false") &&
                                font?.child("b") != null,
                        italic =
                            font?.child("i")?.attrs?.get("val") !in setOf("0", "false") &&
                                font?.child("i") != null,
                        underline =
                            font?.child("u") != null &&
                                font.child("u")?.attrs?.get("val") != "none",
                        color = color(font?.child("color")),
                        fill =
                            if (fill?.attrs?.get("patternType") == "solid")
                                color(fill.child("fgColor"))
                            else null,
                        align =
                            when (align) {
                                "left",
                                "center",
                                "right",
                                "justify",
                                "general" -> align
                                else -> "left"
                            },
                        fontSize = fontSize.coerceIn(8, 24),
                        numberFormat = formats[id] ?: BUILTIN_FORMATS[id] ?: "General",
                    )
                    .also {
                        if (fontSize !in 8..24)
                            warnings +=
                                "Very large or small fonts are fitted to readable table sizes (8–24 pt)."
                    }
            }
        return styles.ifEmpty { listOf(Style(align = "general")) }
    }

    private fun color(element: Xml?): String? {
        if (element == null) return null
        element.attrs["rgb"]?.let { rgb ->
            if (rgb.matches(Regex("[0-9a-fA-F]{8}|[0-9a-fA-F]{6}")))
                return "#${rgb.takeLast(6).uppercase(Locale.ROOT)}"
        }
        val indexed = element.attrs["indexed"]?.toIntOrNull()
        if (indexed != null) return INDEXED_COLORS.getOrNull(indexed)?.let { "#$it" }
        if (element.attrs.containsKey("theme"))
            warnings += "Some theme colors use the app's default text or cell color."
        return null
    }

    private fun readXls(file: File): List<Sheet> {
        require(file.length() <= 8L * 1024 * 1024) {
            "For .xls files, choose a workbook smaller than 8 MB or save it as .xlsx."
        }
        val settings =
            WorkbookSettings().apply {
                setSuppressWarnings(true)
                setGCDisabled(true)
                setLocale(Locale.US)
            }
        val workbook =
            try {
                Workbook.getWorkbook(file, settings)
            } catch (e: Exception) {
                throw IllegalArgumentException(
                    "This .xls file is damaged, encrypted or unsupported. Save an unprotected .xlsx copy in Excel.",
                    e,
                )
            }
        try {
            require(workbook.numberOfSheets in 1..MAX_SHEETS) {
                "Import supports up to 20 worksheets per workbook."
            }
            val sheets = mutableListOf<Sheet>()
            for (index in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheet(index)
                if (sheet.settings.isHidden) {
                    warnings += "Hidden worksheets, rows and columns are not imported."
                    continue
                }
                require(sheet.rows <= MAX_ROWS && sheet.columns <= MAX_COLUMNS) {
                    "Import supports up to 2,000 rows and 64 columns per .xls worksheet. Remove unused formatted rows or save as .xlsx."
                }
                val cells = linkedMapOf<Pair<Int, Int>, Cell>()
                val widths =
                    (0 until sheet.columns).associateWith {
                        sheet.getColumnView(it).size / 256.0 * 7 + 5
                    }
                val hiddenColumns =
                    (0 until sheet.columns).filter { sheet.getColumnView(it).isHidden }.toSet()
                val hiddenRows =
                    (0 until sheet.rows).filter { sheet.getRowView(it).isHidden }.toSet()
                for (row in 0 until sheet.rows) for (column in 0 until sheet.columns) {
                    val source = sheet.getCell(column, row)
                    if (source.type.toString().contains("Formula", true))
                        warnings +=
                            "Formulas use their last saved results and are not recalculated. Save in Excel before importing for current values."
                    val value = source.contents
                    if (value.isEmpty()) continue
                    val format = source.cellFormat
                    val font = format?.font
                    fun rgb(colour: jxl.format.Colour?): String? {
                        if (
                            colour == null ||
                                colour == jxl.format.Colour.AUTOMATIC ||
                                colour == jxl.format.Colour.DEFAULT_BACKGROUND
                        )
                            return null
                        val rgb =
                            (workbook as? jxl.read.biff.WorkbookParser)
                                ?.formattingRecords
                                ?.getColourRGB(colour) ?: colour.defaultRGB
                        return "#%02X%02X%02X".format(Locale.ROOT, rgb.red, rgb.green, rgb.blue)
                    }
                    val alignment = format?.alignment?.description?.lowercase(Locale.ROOT).orEmpty()
                    val align =
                        when {
                            alignment.contains("centre") || alignment.contains("center") -> "center"
                            alignment.contains("right") -> "right"
                            alignment.contains("justif") -> "justify"
                            alignment.contains("general") && source is jxl.NumberCell -> "right"
                            else -> "left"
                        }
                    val style =
                        Style(
                            font?.boldWeight?.let { it >= 700 } ?: false,
                            font?.isItalic ?: false,
                            font?.underlineStyle?.let {
                                it != jxl.format.UnderlineStyle.NO_UNDERLINE
                            } ?: false,
                            rgb(font?.colour),
                            rgb(format?.backgroundColour),
                            align,
                            (font?.pointSize ?: 10).coerceIn(8, 24),
                        )
                    addCell(cells, row, column, Cell(value, style))
                }
                val merges =
                    sheet.mergedCells.map {
                        Merge(
                            it.topLeft.row,
                            it.topLeft.column,
                            it.bottomRight.row,
                            it.bottomRight.column,
                        )
                    }
                require(merges.size <= 2000) { "This worksheet has too many merged cells." }
                validateMerges(merges)
                sheets +=
                    Sheet(sheet.name.take(200), cells, widths, merges, hiddenRows, hiddenColumns)
            }
            warnings +=
                "Charts, drawings, macros and external links are not imported or run; only saved worksheet cells are converted."
            return sheets
        } finally {
            workbook.close()
        }
    }

    private fun readCsv(file: File): List<Sheet> {
        require(file.length() <= 4L * 1024 * 1024) { "Choose a UTF-8 CSV smaller than 4 MB." }
        val decoder =
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        val value =
            try {
                decoder
                    .decode(java.nio.ByteBuffer.wrap(file.readBytes()))
                    .toString()
                    .removePrefix("\uFEFF")
            } catch (e: Exception) {
                throw IllegalArgumentException(
                    "Save the CSV with UTF-8 encoding, then import again.",
                    e,
                )
            }
        require(!value.contains('\u0000')) { "This is not a valid text CSV file." }
        val cells = linkedMapOf<Pair<Int, Int>, Cell>()
        var row = 0
        var column = 0
        var quoted = false
        var afterQuote = false
        var position = 0
        val field = StringBuilder()
        fun commit() {
            if (field.isNotEmpty()) addCell(cells, row, column, Cell(field.toString()))
            field.setLength(0)
            column++
            require(column <= MAX_COLUMNS) { "CSV import supports up to 64 columns." }
            afterQuote = false
        }
        while (position < value.length) {
            val ch = value[position++]
            if (quoted) {
                if (ch == '"') {
                    if (value.getOrNull(position) == '"') {
                        field.append('"')
                        position++
                    } else {
                        quoted = false
                        afterQuote = true
                    }
                } else field.append(ch)
            } else
                when (ch) {
                    '"' -> {
                        require(field.isEmpty() && !afterQuote) {
                            "CSV contains an invalid quoted field."
                        }
                        quoted = true
                    }
                    ',' -> commit()
                    '\n',
                    '\r' -> {
                        commit()
                        if (ch == '\r' && value.getOrNull(position) == '\n') position++
                        row++
                        column = 0
                        require(row <= MAX_ROWS) { "CSV import supports up to 2,000 rows." }
                    }
                    else -> {
                        require(!afterQuote) { "CSV contains text after a closing quote." }
                        field.append(ch)
                    }
                }
            require(field.length <= 32_767) { "A spreadsheet cell contains too much text." }
        }
        require(!quoted) { "CSV contains an unfinished quoted field." }
        if (field.isNotEmpty() || column > 0 || afterQuote) commit()
        return listOf(Sheet("Spreadsheet", cells))
    }

    private fun formatNumber(value: String, format: String, date1904: Boolean): String {
        require(value.length <= 100) { "A numeric cell is too large to import safely." }
        val number =
            value.toBigDecimalOrNull()
                ?: throw IllegalArgumentException("A cell contains an invalid number.")
        require(number.precision() <= 50 && number.scale() in -300..300) {
            "A numeric cell is outside the supported range. Save it as text in Excel, then import again."
        }
        if (format.equals("General", true) || format == "@")
            return number.stripTrailingZeros().toPlainString()
        var code =
            format
                .substringBefore(';')
                .replace(
                    Regex(
                        "\\[(?:Red|Blue|Green|Black|White|Yellow|Magenta|Cyan|[<>=].*?)]",
                        RegexOption.IGNORE_CASE,
                    ),
                    "",
                )
        val lower = code.lowercase(Locale.ROOT).replace(Regex("\"[^\"]*\"|\\\\."), "")
        if (lower.contains("[h]") || lower.contains("[m]") || lower.contains("[s]")) {
            require(
                number.signum() >= 0 &&
                    number.toDouble().isFinite() &&
                    number.toDouble() <= 1_000_000
            ) {
                "A duration value is outside the supported range."
            }
            val seconds =
                number.multiply(BigDecimal(86400)).setScale(0, RoundingMode.HALF_UP).toLong()
            return when {
                lower.contains("[h]") ->
                    "%d:%02d:%02d"
                        .format(Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60)
                lower.contains("[m]") -> "%d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60)
                else -> seconds.toString()
            }
        }
        if (lower.contains(Regex("[yd]")) || lower.contains(Regex("h|s"))) {
            val serial = number.toDouble()
            require(serial.isFinite() && serial in 0.0..2_958_465.99999) {
                "A cell contains an invalid date value."
            }
            val days = number.setScale(0, RoundingMode.FLOOR).toLong()
            val date =
                if (date1904) LocalDate.of(1904, 1, 1).plusDays(days)
                else LocalDate.of(1899, 12, 31).plusDays(if (days >= 60) days - 1 else days)
            val seconds = ((serial - days) * 86400).toLong().coerceIn(0, 86399)
            val dateText = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val time =
                LocalTime.ofSecondOfDay(seconds)
                    .format(
                        DateTimeFormatter.ofPattern(
                            if (lower.contains('s')) "HH:mm:ss" else "HH:mm",
                            Locale.ROOT,
                        )
                    )
            return when {
                lower.contains(Regex("[yd]")) && lower.contains('h') -> "$dateText $time"
                lower.contains('h') || (lower.contains('s') && !lower.contains(Regex("[yd]"))) ->
                    time
                else -> dateText
            }
        }
        if (code.contains(Regex("[?]|\\[h]|\\[m]|\\[s]", RegexOption.IGNORE_CASE))) {
            warnings +=
                "Some custom number formats use the stored numeric value. Dates use YYYY-MM-DD."
            return number.stripTrailingZeros().toPlainString()
        }
        code =
            code.replace(
                Regex(
                    "\\[(?:Red|Blue|Green|Black|White|Yellow|Magenta|Cyan|[<>=].*?)]",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            )
        code = code.replace(Regex("\\[\\$([^]-]*)(?:-[^]]*)?]")) { it.groupValues[1] }
        code = code.replace(Regex("_.|\\*."), "")
        code = code.replace(Regex("\"([^\"]*)\"")) { "'${it.groupValues[1].replace("'", "''")}'" }
        code = code.replace(Regex("\\\\(.)")) { "'${it.groupValues[1]}'" }
        return try {
            require(code.length <= 160 && !code.contains('['))
            DecimalFormat(code.replace("E+", "E"), DecimalFormatSymbols(Locale.US))
                .apply { roundingMode = RoundingMode.HALF_UP }
                .format(number)
        } catch (_: Exception) {
            warnings +=
                "Some custom number formats use the stored numeric value. Dates use YYYY-MM-DD."
            number.stripTrailingZeros().toPlainString()
        }
    }

    private fun validateMerges(merges: List<Merge>) {
        val occupied = mutableSetOf<Pair<Int, Int>>()
        var area = 0L
        for (merge in merges) {
            require(
                merge.top in 0 until MAX_ROWS &&
                    merge.bottom in merge.top until MAX_ROWS &&
                    merge.left in 0 until MAX_COLUMNS &&
                    merge.right in merge.left until MAX_COLUMNS
            ) {
                "A merged cell extends beyond the import limits."
            }
            area += (merge.bottom - merge.top + 1).toLong() * (merge.right - merge.left + 1)
            require(area <= MAX_OUTPUT_CELLS) {
                "The merged cells cover too large an area to import."
            }
            for (row in merge.top..merge.bottom) for (column in merge.left..merge.right) require(
                occupied.add(row to column)
            ) {
                "This worksheet contains overlapping merged cells."
            }
        }
    }

    private fun coordinate(reference: String): Pair<Int, Int> {
        val match =
            Regex("\\$?([A-Za-z]{1,3})\\$?([1-9][0-9]{0,6})").matchEntire(reference)
                ?: throw IllegalArgumentException("A worksheet contains an invalid cell reference.")
        var col = 0
        for (char in match.groupValues[1].uppercase(Locale.ROOT)) col = col * 26 + (char - 'A' + 1)
        val row = match.groupValues[2].toInt() - 1
        require(col in 1..16384 && row < 1_048_576) { "Invalid worksheet cell coordinate." }
        return row to (col - 1)
    }

    private fun columnName(index: Int): String {
        var value = index + 1
        var result = ""
        while (value > 0) {
            value--
            result = ('A' + value % 26) + result
            value /= 26
        }
        return result
    }

    private fun unzip(file: File): Map<String, ByteArray> {
        val parts = linkedMapOf<String, ByteArray>()
        var total = 0L
        var count = 0
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++count <= 2000) { "This workbook contains too many package parts." }
                require(
                    !entry.name.contains('\\') &&
                        !entry.name.startsWith('/') &&
                        entry.name.split('/').none { it == ".." } &&
                        !entry.name.contains('\u0000')
                ) {
                    "Invalid Excel package path."
                }
                if (entry.isDirectory) continue
                require(!parts.containsKey(entry.name)) { "Duplicate Excel package part." }
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16384)
                while (true) {
                    val size = zip.read(buffer)
                    if (size < 0) break
                    total += size
                    require(
                        total <= 32L * 1024 * 1024 && output.size() + size <= 12 * 1024 * 1024
                    ) {
                        "The expanded workbook is too large (32 MB total, 12 MB per part)."
                    }
                    output.write(buffer, 0, size)
                }
                parts[entry.name] = output.toByteArray()
            }
        }
        return parts
    }

    private fun relationships(parts: Map<String, ByteArray>, source: String): Map<String, Rel> {
        val path =
            if (source.isEmpty()) "_rels/.rels"
            else
                source.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" } +
                    "_rels/" +
                    source.substringAfterLast('/') +
                    ".rels"
        return parts[path]
            ?.let(::xml)
            ?.children
            .orEmpty()
            .filter { it.name == "Relationship" }
            .associate { rel ->
                val external = rel.attrs["TargetMode"].equals("External", true)
                val target = rel.attrs["Target"].orEmpty()
                val resolved =
                    if (external) ""
                    else {
                        val uri = URI(target)
                        require(
                            !uri.isAbsolute &&
                                uri.authority == null &&
                                uri.query == null &&
                                uri.fragment == null &&
                                !target.contains('\\')
                        ) {
                            "Invalid Excel package relationship."
                        }
                        val base =
                            if (source.isEmpty()) ""
                            else
                                source.substringBeforeLast('/', "").let {
                                    if (it.isEmpty()) "" else "$it/"
                                }
                        val normalized =
                            URI("/" + base).resolve(uri).normalize().path.removePrefix("/")
                        require(
                            normalized.isNotBlank() && normalized.split('/').none { it == ".." }
                        ) {
                            "Invalid Excel package relationship."
                        }
                        normalized
                    }
                rel.attrs["Id"].orEmpty() to Rel(resolved, rel.attrs["Type"].orEmpty(), external)
            }
    }

    private fun xml(bytes: ByteArray): Xml {
        val stack = java.util.ArrayDeque<Xml>()
        var root: Xml? = null
        var nodes = 0
        var characters = 0
        val handler =
            object : DefaultHandler2() {
                override fun startDTD(name: String?, publicId: String?, systemId: String?) {
                    throw SAXException("DTD is not allowed in Excel files.")
                }

                override fun resolveEntity(publicId: String?, systemId: String?): InputSource {
                    throw SAXException("External entities are not allowed in Excel files.")
                }

                override fun startElement(
                    uri: String?,
                    localName: String,
                    qName: String?,
                    attributes: Attributes,
                ) {
                    require(stack.size < 40 && ++nodes <= 180_000) {
                        "This spreadsheet XML is too complex."
                    }
                    val attrs =
                        (0 until attributes.length).associate {
                            attributes.getLocalName(it).ifEmpty {
                                attributes.getQName(it).substringAfter(':')
                            } to attributes.getValue(it)
                        }
                    val node = Xml(localName.ifEmpty { qName.orEmpty().substringAfter(':') }, attrs)
                    if (stack.isEmpty()) root = node
                    else requireNotNull(stack.peekLast()).children += node
                    stack.addLast(node)
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    characters += length
                    require(characters <= 8_000_000) {
                        "This spreadsheet XML contains too much text."
                    }
                    stack.peekLast()?.text?.append(ch, start, length)
                }

                override fun endElement(uri: String?, localName: String?, qName: String?) {
                    stack.removeLast()
                }
            }
        val factory =
            SAXParserFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                runCatching {
                    setFeature(
                        "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                        false,
                    )
                }
            }
        val reader = factory.newSAXParser().xmlReader
        reader.contentHandler = handler
        reader.entityResolver = handler
        reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
        reader.parse(InputSource(bytes.inputStream()))
        return root
            ?: throw IllegalArgumentException("This spreadsheet contains an empty XML part.")
    }

    private fun node(type: String, content: List<JSONObject>? = null) =
        JSONObject().put("type", type).also {
            if (content != null) it.put("content", JSONArray(content))
        }

    private fun text(value: String) = node("text").put("text", value)

    companion object {
        const val MAX_FILE_BYTES = 20L * 1024 * 1024
        private const val MAX_SHEETS = 20
        private const val MAX_ROWS = 2000
        private const val MAX_COLUMNS = 64
        private const val MAX_INPUT_CELLS = 20_000
        private const val MAX_OUTPUT_CELLS = 20_000
        private const val COLUMNS_PER_PAGE = 8
        private val BUILTIN_FORMATS =
            mapOf(
                0 to "General",
                1 to "0",
                2 to "0.00",
                3 to "#,##0",
                4 to "#,##0.00",
                9 to "0%",
                10 to "0.00%",
                11 to "0.00E+00",
                12 to "# ?/?",
                13 to "# ??/??",
                14 to "mm-dd-yy",
                15 to "d-mmm-yy",
                16 to "d-mmm",
                17 to "mmm-yy",
                18 to "h:mm AM/PM",
                19 to "h:mm:ss AM/PM",
                20 to "h:mm",
                21 to "h:mm:ss",
                22 to "m/d/yy h:mm",
                37 to "#,##0 ;(#,##0)",
                38 to "#,##0 ;[Red](#,##0)",
                39 to "#,##0.00;(#,##0.00)",
                40 to "#,##0.00;[Red](#,##0.00)",
                45 to "mm:ss",
                46 to "[h]:mm:ss",
                47 to "mmss.0",
                49 to "@",
            )
        private val INDEXED_COLORS =
            listOf(
                "000000",
                "FFFFFF",
                "FF0000",
                "00FF00",
                "0000FF",
                "FFFF00",
                "FF00FF",
                "00FFFF",
                "000000",
                "FFFFFF",
                "FF0000",
                "00FF00",
                "0000FF",
                "FFFF00",
                "FF00FF",
                "00FFFF",
                "800000",
                "008000",
                "000080",
                "808000",
                "800080",
                "008080",
                "C0C0C0",
                "808080",
            )
    }
}
