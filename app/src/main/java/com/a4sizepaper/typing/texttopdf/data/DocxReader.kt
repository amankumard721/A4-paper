package com.a4sizepaper.typing.texttopdf.data

import com.a4sizepaper.typing.texttopdf.domain.PageSettings
import java.io.File
import java.net.URI
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory
import org.json.JSONArray
import org.json.JSONObject
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.ext.DefaultHandler2

/**
 * Bounded, offline DOCX reader. Relationships are package-local; no external content is fetched.
 */
class DocxReader {
    data class Result(
        val content: JSONObject,
        val page: PageSettings,
        val images: Map<String, ByteArray>,
        val warnings: List<String>,
    )

    private data class Xml(
        val name: String,
        val attrs: Map<String, String>,
        val children: MutableList<Xml> = mutableListOf(),
        val text: StringBuilder = StringBuilder(),
    ) {
        fun child(name: String) = children.firstOrNull { it.name == name }

        fun value(name: String) = child(name)?.attrs?.get("val")

        fun descendants(name: String): List<Xml> =
            children.flatMap {
                (if (it.name == name) listOf(it) else emptyList()) + it.descendants(name)
            }
    }

    private data class Rel(val target: String, val type: String, val external: Boolean)

    private val warnings = linkedSetOf<String>()
    private val images = linkedMapOf<String, ByteArray>()
    private lateinit var parts: Map<String, ByteArray>
    private lateinit var relationships: Map<String, Rel>
    private var styles = emptyMap<String, Xml>()
    private var numbering = emptyMap<String, Xml>()
    private var nums = emptyMap<String, Xml>()
    private var defaults: Xml? = null
    private var contentWidth = 680.0

    fun read(file: File): Result {
        warnings.clear()
        images.clear()
        require(file.length() <= 20L * 1024 * 1024) { "Choose a Word document smaller than 20 MB." }
        parts = unzip(file)
        val rootRels = rels("_rels/.rels", "")
        val main =
            rootRels.values
                .firstOrNull { it.type.endsWith("/officeDocument") && !it.external }
                ?.target ?: "word/document.xml"
        val document =
            xml(
                parts[main]
                    ?: error(
                        "This is not a supported .docx document. For .doc or password-protected files, save an unprotected .docx copy in Word."
                    )
            )
        require(document.name == "document") { "This file is not a Word document." }
        val body = document.child("body") ?: error("Word document has no document body.")
        val dir = main.substringBeforeLast('/', "")
        relationships =
            rels(
                (if (dir.isEmpty()) "" else "$dir/") +
                    "_rels/" +
                    main.substringAfterLast('/') +
                    ".rels",
                main,
            )
        fun related(kind: String): Xml? =
            relationships.values
                .firstOrNull { !it.external && it.type.endsWith("/$kind") }
                ?.let { parts[it.target]?.let(::xml) }
        val styleXml = related("styles")
        styles =
            styleXml
                ?.children
                ?.filter { it.name == "style" }
                ?.associateBy { it.attrs["styleId"].orEmpty() } ?: emptyMap()
        defaults = styleXml?.child("docDefaults")
        val numberingXml = related("numbering")
        numbering =
            numberingXml
                ?.children
                ?.filter { it.name == "abstractNum" }
                ?.associateBy { it.attrs["abstractNumId"].orEmpty() } ?: emptyMap()
        nums =
            numberingXml
                ?.children
                ?.filter { it.name == "num" }
                ?.associateBy { it.attrs["numId"].orEmpty() } ?: emptyMap()
        val page = pageSettings(body)
        contentWidth = (page.widthMm - page.marginLeft - page.marginRight) * 96 / 25.4
        if (
            relationships.values.any {
                it.type.endsWith("/header") ||
                    it.type.endsWith("/footer") ||
                    it.type.endsWith("/footnotes") ||
                    it.type.endsWith("/endnotes")
            }
        )
            warnings += "Headers, footers and footnotes are not imported."
        if (body.descendants("sectPr").size > 1)
            warnings += "Multiple sections use one A4 page setup."
        val content = node("doc", blocks(body).ifEmpty { listOf(node("paragraph")) })
        require(content.toString().length <= 8_000_000) { "The converted document is too large." }
        return Result(content, page, images.toMap(), warnings.toList())
    }

    private fun unzip(file: File): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        var total = 0
        var count = 0
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++count <= 1500) { "This Word document contains too many parts." }
                require(
                    !entry.name.contains('\\') &&
                        !entry.name.startsWith('/') &&
                        entry.name.split('/').none { it == ".." }
                ) {
                    "Invalid Word package path."
                }
                if (entry.isDirectory) continue
                require(!result.containsKey(entry.name)) { "Duplicate Word package part." }
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16384)
                while (true) {
                    val n = zip.read(buffer)
                    if (n < 0) break
                    total += n
                    require(total <= 32 * 1024 * 1024 && output.size() + n <= 20 * 1024 * 1024) {
                        "The expanded Word document is too large (32 MB limit)."
                    }
                    output.write(buffer, 0, n)
                }
                result[entry.name] = output.toByteArray()
            }
        }
        return result
    }

    private fun xml(bytes: ByteArray): Xml {
        require(bytes.size <= 4 * 1024 * 1024) { "This Word document has an oversized XML part." }
        val stack = java.util.ArrayDeque<Xml>()
        var root: Xml? = null
        var nodes = 0
        var textLength = 0
        val handler =
            object : DefaultHandler2() {
                override fun startDTD(name: String?, publicId: String?, systemId: String?) {
                    throw SAXException("DTD is not allowed in Word documents.")
                }

                override fun resolveEntity(publicId: String?, systemId: String?): InputSource {
                    throw SAXException("External entities are not allowed.")
                }

                override fun startElement(
                    uri: String?,
                    localName: String,
                    qName: String?,
                    attributes: Attributes,
                ) {
                    require(stack.size < 64 && ++nodes <= 100_000) {
                        "Word document is too complex."
                    }
                    val item =
                        Xml(
                            localName,
                            (0 until attributes.length).associate {
                                attributes.getLocalName(it) to attributes.getValue(it)
                            },
                        )
                    if (stack.isEmpty()) root = item else requireNotNull(stack.peekLast()).children += item
                    stack.addLast(item)
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    textLength += length
                    require(textLength <= 2_000_000) { "Word document contains too much text." }
                    stack.peekLast()?.text?.append(ch, start, length)
                }

                override fun endElement(uri: String?, localName: String?, qName: String?) {
                    stack.removeLast()
                }
            }
        val reader =
            SAXParserFactory.newInstance()
                .apply { isNamespaceAware = true }
                .newSAXParser()
                .xmlReader
        reader.contentHandler = handler
        reader.entityResolver = handler
        reader.errorHandler = handler
        // Fail closed if a parser cannot reject a DTD before expanding it.
        reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
        reader.parse(InputSource(bytes.inputStream()))
        return requireNotNull(root) { "Empty Word XML part." }
    }

    private fun rels(path: String, base: String): Map<String, Rel> =
        parts[path]
            ?.let(::xml)
            ?.children
            ?.filter { it.name == "Relationship" }
            ?.associate { r ->
                val external = r.attrs["TargetMode"] == "External"
                val target = r.attrs["Target"].orEmpty()
                val resolved =
                    if (external) ""
                    else {
                        val uri = URI("/$base").resolve(target).normalize()
                        require(
                            uri.scheme == null &&
                                uri.host == null &&
                                uri.query == null &&
                                uri.fragment == null &&
                                !uri.path.contains('\\') &&
                                uri.path.split('/').none { it == ".." }
                        ) {
                            "Invalid relationship in Word document."
                        }
                        uri.path.removePrefix("/")
                    }
                r.attrs["Id"].orEmpty() to Rel(resolved, r.attrs["Type"].orEmpty(), external)
            } ?: emptyMap()

    private fun pageSettings(body: Xml): PageSettings {
        val section = body.descendants("sectPr").lastOrNull()
        val size = section?.child("pgSz")
        val landscape = size?.attrs?.get("orient") == "landscape"
        val margins = section?.child("pgMar")
        fun margin(key: String) =
            ((margins?.attrs?.get(key)?.toDoubleOrNull() ?: 850.0) * 25.4 / 1440).coerceIn(
                5.0,
                60.0,
            )
        if (size != null) {
            val width = size.attrs["w"]?.toDoubleOrNull()?.times(25.4 / 1440)
            val height = size.attrs["h"]?.toDoubleOrNull()?.times(25.4 / 1440)
            if (
                width != null &&
                    height != null &&
                    (kotlin.math.abs(width - if (landscape) 297 else 210) > 2 ||
                        kotlin.math.abs(height - if (landscape) 210 else 297) > 2)
            )
                warnings += "The original paper size has been fitted to A4."
        }
        if (section?.child("cols")?.attrs?.get("num")?.toIntOrNull()?.let { it > 1 } == true)
            warnings += "Multi-column text is imported as a single column."
        return PageSettings(
                landscape,
                margin("top"),
                margin("right"),
                margin("bottom"),
                margin("left"),
            )
            .validate()
    }

    private fun styleChain(id: String?, seen: Set<String> = emptySet()): List<Xml> {
        if (id == null || id in seen || seen.size >= 16) return emptyList()
        val style = styles[id] ?: return emptyList()
        return styleChain(style.value("basedOn"), seen + id) + style
    }

    private fun properties(elements: List<Xml?>): Map<String, Xml> =
        elements.filterNotNull().flatMap { it.children }.associateBy { it.name }

    private fun enabled(p: Xml?) =
        p != null && p.attrs["val"] !in listOf("0", "false", "off", "none")

    private fun paragraphProps(p: Xml): Map<String, Xml> =
        properties(
            listOf(defaults?.child("pPrDefault")?.child("pPr")) +
                styleChain(p.child("pPr")?.value("pStyle")).map { it.child("pPr") } +
                p.child("pPr")
        )

    private fun blocks(parent: Xml): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        var lastNum: String? = null
        var lastList: JSONObject? = null
        for (part in parent.children) when (part.name) {
            "p" -> {
                val props = paragraphProps(part)
                val numPr = props["numPr"]
                val numId = numPr?.value("numId")?.takeUnless { it == "0" }
                val paragraphs = paragraph(part, props)
                if (
                    numId != null &&
                        paragraphs.all { it.optString("type") in listOf("paragraph", "heading") }
                ) {
                    val level = numPr?.value("ilvl") ?: "0"
                    val definition =
                        numbering[nums[numId]?.value("abstractNumId")]?.children?.firstOrNull {
                            it.name == "lvl" && it.attrs["ilvl"] == level
                        }
                    val format = definition?.value("numFmt") ?: "decimal"
                    if (level != "0")
                        warnings += "Nested lists are flattened; numbering may differ."
                    if (format !in listOf("bullet", "decimal"))
                        warnings += "Special numbering styles use decimal numbers."
                    val key = "$numId:$level:$format"
                    if (lastNum != key) {
                        lastList =
                            node(
                                if (format == "bullet") "bulletList" else "orderedList",
                                emptyList(),
                            )
                        if (format != "bullet")
                            lastList.put(
                                "attrs",
                                JSONObject()
                                    .put(
                                        "start",
                                        definition?.value("start")?.toIntOrNull()?.coerceAtLeast(1)
                                            ?: 1,
                                    ),
                            )
                        result += lastList
                        lastNum = key
                    }
                    paragraphs.forEach { it.put("type", "paragraph") }
                    lastList!!.getJSONArray("content").put(node("listItem", paragraphs))
                } else {
                    lastNum = null
                    lastList = null
                    result += paragraphs
                }
            }
            "tbl" -> {
                lastNum = null
                lastList = null
                result += table(part)
            }
            "sdt" -> {
                lastNum = null
                lastList = null
                part.child("sdtContent")?.let { result += blocks(it) }
            }
            "ins",
            "customXml" -> {
                lastNum = null
                result += blocks(part)
            }
            "altChunk" -> warnings += "Embedded documents are not imported."
        }
        return result
    }

    private fun paragraph(p: Xml, props: Map<String, Xml>): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        var inline = mutableListOf<JSONObject>()
        val attrs = JSONObject()
        val alignment = props["jc"]?.attrs?.get("val")
        attrs.put(
            "textAlign",
            when (alignment) {
                "both",
                "distribute" -> "justify"
                "center",
                "right" -> alignment
                else -> "left"
            },
        )
        props["spacing"]?.let { spacing ->
            spacing.attrs["after"]?.toDoubleOrNull()?.let {
                attrs.put("paragraphSpacing", "${(it / 20).coerceIn(0.0, 60.0)}pt")
            }
            if (spacing.attrs["lineRule"] in listOf(null, "auto"))
                spacing.attrs["line"]?.toDoubleOrNull()?.let {
                    attrs.put("lineHeight", (it / 240).coerceIn(1.0, 2.5))
                }
        }
        val heading =
            props["outlineLvl"]?.attrs?.get("val")?.toIntOrNull()?.plus(1)
                ?: Regex("(?i)heading([1-6])")
                    .matchEntire(p.child("pPr")?.value("pStyle").orEmpty())
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
        if (heading != null && heading in 1..6) attrs.put("level", heading)
        fun flush(force: Boolean = false) {
            if (inline.isNotEmpty() || force)
                result +=
                    node(if (attrs.has("level")) "heading" else "paragraph", inline)
                        .put("attrs", JSONObject(attrs.toString()))
            inline = mutableListOf()
        }
        if (enabled(props["pageBreakBefore"])) result += node("pageBreak")
        val inherited =
            listOf(defaults?.child("rPrDefault")?.child("rPr")) +
                styleChain(p.child("pPr")?.value("pStyle")).map { it.child("rPr") }
        fun walk(container: Xml) {
            for (part in container.children) when (part.name) {
                "r" -> {
                    val rp =
                        properties(
                            inherited +
                                styleChain(part.child("rPr")?.value("rStyle")).map {
                                    it.child("rPr")
                                } +
                                part.child("rPr")
                        )
                    val marks = JSONArray()
                    if (enabled(rp["b"] ?: rp["bCs"])) marks.put(node("bold"))
                    if (enabled(rp["i"] ?: rp["iCs"])) marks.put(node("italic"))
                    if (enabled(rp["u"])) marks.put(node("underline"))
                    if (enabled(rp["strike"])) marks.put(node("strike"))
                    val textStyle = JSONObject()
                    rp["color"]
                        ?.attrs
                        ?.get("val")
                        ?.takeIf { Regex("[0-9a-fA-F]{6}").matches(it) }
                        ?.let { textStyle.put("color", "#$it") }
                    (rp["sz"] ?: rp["szCs"])?.attrs?.get("val")?.toDoubleOrNull()?.let {
                        textStyle.put("fontSize", "${(it / 2).coerceIn(8.0, 72.0)}pt")
                    }
                    rp["rFonts"]?.let { fonts ->
                        val name = (fonts.attrs["ascii"] ?: fonts.attrs["hAnsi"] ?: "").lowercase()
                        val family =
                            when {
                                "courier" in name || "mono" in name || "consolas" in name ->
                                    "Monospace"
                                "times" in name || "cambria" in name || "serif" in name ->
                                    "Noto Serif"
                                else -> "Noto Sans"
                            }
                        textStyle.put("fontFamily", family)
                    }
                    if (textStyle.length() > 0) marks.put(node("textStyle").put("attrs", textStyle))
                    for (item in part.children) when (item.name) {
                        "t" ->
                            if (item.text.isNotEmpty())
                                inline +=
                                    node("text").put("text", item.text.toString()).also {
                                        if (marks.length() > 0) it.put("marks", marks)
                                    }
                        "tab" -> inline += node("text").put("text", "    ")
                        "br",
                        "cr" ->
                            if (item.attrs["type"] == "page") {
                                flush()
                                result += node("pageBreak")
                            } else inline += node("hardBreak")
                        "drawing",
                        "pict" -> {
                            flush()
                            image(item)?.let { result += it }
                        }
                        "object" -> warnings += "Embedded objects are not imported."
                        "footnoteReference",
                        "endnoteReference" ->
                            warnings += "Headers, footers and footnotes are not imported."
                    }
                }
                "hyperlink",
                "ins",
                "smartTag",
                "fldSimple" -> walk(part)
                "sdt" -> part.child("sdtContent")?.let(::walk)
                "oMath",
                "oMathPara" -> warnings += "Equations are not imported."
            }
        }
        walk(p)
        flush(result.isEmpty())
        return result
    }

    private fun image(drawing: Xml): JSONObject? {
        val id =
            drawing.descendants("blip").firstOrNull()?.attrs?.get("embed")
                ?: drawing.descendants("imagedata").firstOrNull()?.attrs?.get("id")
        val rel = relationships[id]
        if (rel == null || rel.external || !rel.type.endsWith("/image")) {
            warnings += "A linked image or drawing could not be imported."
            return null
        }
        val data = parts[rel.target]
        if (data == null) {
            warnings += "A missing image could not be imported."
            return null
        }
        require(images.size < 100 || images.containsKey(rel.target)) {
            "Import supports up to 100 images."
        }
        images[rel.target] = data
        val extent = drawing.descendants("extent").firstOrNull()
        val pixels = extent?.attrs?.get("cx")?.toDoubleOrNull()?.div(9525)
        if (drawing.descendants("anchor").isNotEmpty())
            warnings += "Floating pictures are placed between paragraphs."
        return node("image")
            .put(
                "attrs",
                JSONObject()
                    .put("sourcePart", rel.target)
                    .put(
                        "width",
                        ((pixels ?: contentWidth) / contentWidth * 100).toInt().coerceIn(10, 100),
                    )
                    .put("align", "left")
                    .put(
                        "alt",
                        drawing.descendants("docPr").firstOrNull()?.attrs?.get("descr")
                            ?: "Imported Word image",
                    ),
            )
    }

    private fun table(tbl: Xml): JSONObject {
        val rows = mutableListOf<JSONObject>()
        val grid =
            tbl.child("tblGrid")
                ?.children
                ?.map { (it.attrs["w"]?.toDoubleOrNull() ?: 720.0) / 15.0 }
                .orEmpty()
        val active = mutableMapOf<Int, JSONObject>()
        val sourceRows = tbl.children.filter { it.name == "tr" }
        require(sourceRows.size <= 1000) { "A table has too many rows." }
        for (row in sourceRows) {
            val cells = mutableListOf<JSONObject>()
            var col = 0
            val header = enabled(row.child("trPr")?.child("tblHeader"))
            for (cell in row.children.filter { it.name == "tc" }) {
                val pr = cell.child("tcPr")
                val span = (pr?.value("gridSpan")?.toIntOrNull() ?: 1).coerceIn(1, 30)
                require(col + span <= 30) { "Import supports tables with up to 30 columns." }
                val merge = pr?.child("vMerge")
                val previous = active[col]
                if (
                    merge != null &&
                        merge.attrs["val"] != "restart" &&
                        previous != null &&
                        previous.getJSONObject("attrs").getInt("colspan") == span
                ) {
                    val attrs = previous.getJSONObject("attrs")
                    attrs.put("rowspan", attrs.getInt("rowspan") + 1)
                    col += span
                    continue
                }
                val content = blocks(cell).toMutableList()
                if (content.isEmpty()) content += node("paragraph")
                val attrs = JSONObject().put("colspan", span).put("rowspan", 1)
                if (grid.size >= col + span) {
                    val scale = minOf(1.0, contentWidth / grid.sum().coerceAtLeast(1.0))
                    attrs.put(
                        "colwidth",
                        JSONArray(
                            grid.subList(col, col + span).map {
                                (it * scale).toInt().coerceAtLeast(36)
                            }
                        ),
                    )
                }
                val next =
                    node(if (header) "tableHeader" else "tableCell", content).put("attrs", attrs)
                cells += next
                for (c in col until col + span) active.remove(c)
                if (merge?.attrs?.get("val") == "restart") active[col] = next
                col += span
            }
            // A row covered entirely by rowspans is valid in ProseMirror tables.
            rows += node("tableRow", cells)
        }
        if (rows.isEmpty()) return node("paragraph")
        return node("table", rows)
    }

    private fun node(type: String, content: List<JSONObject>? = null) =
        JSONObject().put("type", type).also {
            if (content != null) it.put("content", JSONArray(content))
        }
}
