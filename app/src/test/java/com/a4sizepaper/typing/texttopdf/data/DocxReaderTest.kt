package com.a4sizepaper.typing.texttopdf.data

import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocxReaderTest {
    @get:Rule val temporary = TemporaryFolder()
    private val namespace = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private val relation = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private fun archive(
        body: String,
        extras: Map<String, ByteArray> = emptyMap(),
        raw: Boolean = false,
    ): File {
        val file = temporary.newFile("${System.nanoTime()}.docx")
        ZipOutputStream(file.outputStream()).use { zip ->
            val document =
                if (raw) body
                else
                    """<w:document xmlns:w="$namespace" xmlns:r="$relation" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"><w:body>$body</w:body></w:document>"""
            for ((name, bytes) in mapOf("word/document.xml" to document.toByteArray()) + extras) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    private fun text(value: String) = "<w:p><w:r><w:t>$value</w:t></w:r></w:p>"

    private fun rels(vararg entries: Pair<String, String>): ByteArray =
        ("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                entries.joinToString("") { (kind, target) ->
                    "<Relationship Id=\"$kind\" Type=\"$relation/$kind\" Target=\"$target\"/>"
                } +
                "</Relationships>")
            .toByteArray()

    @Test
    fun importsStylesUnicodeListsTablesAndImage() {
        val body =
            """
            <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Word → PDF नमस्ते</w:t></w:r></w:p>
            <w:p><w:r><w:rPr><w:b/><w:i/><w:u w:val="single"/><w:color w:val="2266AA"/><w:sz w:val="28"/></w:rPr><w:t>Formatted text</w:t></w:r><w:r><w:br/><w:t>Second line</w:t></w:r></w:p>
            <w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>First item</w:t></w:r></w:p>
            <w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>Second item</w:t></w:r></w:p>
            <w:tbl><w:tblGrid><w:gridCol w:w="2000"/><w:gridCol w:w="3000"/></w:tblGrid><w:tr><w:tc><w:tcPr><w:vMerge w:val="restart"/></w:tcPr>${text("Merged")}</w:tc><w:tc>${text("A")}</w:tc></w:tr><w:tr><w:tc><w:tcPr><w:vMerge/></w:tcPr>${text("")}</w:tc><w:tc>${text("B")}</w:tc></w:tr></w:tbl>
            <w:p><w:r><w:drawing><wp:inline><wp:extent cx="1905000" cy="952500"/><a:blip r:embed="image"/></wp:inline></w:drawing></w:r></w:p>
            <w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="850" w:right="850" w:bottom="850" w:left="850"/></w:sectPr>
        """
                .trimIndent()
        val style =
            """<w:styles xmlns:w="$namespace"><w:style w:styleId="Base"><w:rPr><w:b/></w:rPr></w:style><w:style w:styleId="Heading1"><w:basedOn w:val="Base"/><w:pPr><w:outlineLvl w:val="0"/></w:pPr></w:style></w:styles>"""
        val num =
            """<w:numbering xmlns:w="$namespace"><w:abstractNum w:abstractNumId="0"><w:lvl w:ilvl="0"><w:start w:val="3"/><w:numFmt w:val="decimal"/></w:lvl></w:abstractNum><w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num></w:numbering>"""
        val png =
            Base64.getDecoder()
                .decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII="
                )
        val result =
            DocxReader()
                .read(
                    archive(
                        body,
                        mapOf(
                            "word/_rels/document.xml.rels" to
                                rels(
                                    "styles" to "styles.xml",
                                    "numbering" to "numbering.xml",
                                    "image" to "media/image.png",
                                ),
                            "word/styles.xml" to style.toByteArray(),
                            "word/numbering.xml" to num.toByteArray(),
                            "word/media/image.png" to png,
                        ),
                    )
                )
        val nodes = result.content.getJSONArray("content")
        assertEquals("heading", nodes.getJSONObject(0).getString("type"))
        assertTrue(nodes.getJSONObject(0).toString().contains("नमस्ते"))
        assertTrue(nodes.getJSONObject(0).toString().contains("bold"))
        assertTrue(nodes.getJSONObject(1).toString().contains("#2266AA"))
        assertEquals(3, nodes.getJSONObject(2).getJSONObject("attrs").getInt("start"))
        assertEquals(2, nodes.getJSONObject(2).getJSONArray("content").length())
        val table = nodes.getJSONObject(3).getJSONArray("content")
        assertEquals(
            2,
            table
                .getJSONObject(0)
                .getJSONArray("content")
                .getJSONObject(0)
                .getJSONObject("attrs")
                .getInt("rowspan"),
        )
        assertEquals(1, table.getJSONObject(1).getJSONArray("content").length())
        assertArrayEquals(png, result.images["word/media/image.png"])
        assertTrue(result.warnings.isEmpty())
        val image = nodes.getJSONObject(4).getJSONObject("attrs")
        image.remove("sourcePart")
        image.put("assetId", "11111111-1111-1111-1111-111111111111.png")
        File("build/docx-fixture.json").apply {
            parentFile.mkdirs()
            writeText(
                JSONObject()
                    .put("schemaVersion", 1)
                    .put("page", result.page.toJson())
                    .put("content", result.content)
                    .toString()
            )
        }
    }

    @Test
    fun rejectsDtdBeforeExpansion() {
        val doc =
            """<!DOCTYPE document [<!ENTITY attack SYSTEM "file:///private-data">]><document><body>&attack;</body></document>"""
        assertThrows(Exception::class.java) { DocxReader().read(archive(doc, raw = true)) }
    }

    @Test
    fun rejectsPackageTraversal() {
        assertThrows(IllegalArgumentException::class.java) {
            DocxReader().read(archive(text("safe"), mapOf("../outside.xml" to byteArrayOf(1))))
        }
    }

    @Test
    fun rejectsOversizedInflatedPackage() {
        assertThrows(IllegalArgumentException::class.java) {
            DocxReader()
                .read(
                    archive(text("safe"), mapOf("word/media/bomb" to ByteArray(21 * 1024 * 1024)))
                )
        }
    }

    @Test
    fun doesNotReadExternalImages() {
        val relationships =
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="image" Type="$relation/image" Target="https://example.com/private-image" TargetMode="External"/></Relationships>"""
        val result =
            DocxReader()
                .read(
                    archive(
                        "<w:p><w:r><w:drawing><a:blip r:embed=\"image\"/></w:drawing></w:r></w:p>",
                        mapOf("word/_rels/document.xml.rels" to relationships.toByteArray()),
                    )
                )
        assertTrue(result.images.isEmpty())
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun rejectsLegacyOrEncryptedContainer() {
        val file = temporary.newFile()
        file.writeBytes(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte()))
        assertThrows(Exception::class.java) { DocxReader().read(file) }
    }

    @Test
    fun keepsExplicitPageBreakAndLandscape() {
        val result =
            DocxReader()
                .read(
                    archive(
                        text("Before") +
                            "<w:p><w:r><w:br w:type=\"page\"/><w:t>After</w:t></w:r></w:p><w:sectPr><w:pgSz w:orient=\"landscape\" w:w=\"16838\" w:h=\"11906\"/></w:sectPr>"
                    )
                )
        assertTrue(result.page.landscape)
        assertTrue(result.content.toString().contains("pageBreak"))
    }
}
