package com.a4lekhak.documentmaker

import com.a4lekhak.documentmaker.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DocumentTest {
    @Test
    fun documentRoundTripPreservesMixedTextAndSettings() {
        val content =
            Templates.all.first().content(mapOf("Name" to "अमन Kumar", "School" to "विद्यालय"))
        val doc =
            Document(
                title = "हिंदी English",
                content = content,
                page = PageSettings(landscape = true, marginLeft = 20.0),
                exportedAt = 12345,
                exportPages = 4,
                isDraft = true,
            )
        assertEquals(doc, Document.fromJson(doc.toJson()))
        assertTrue(doc.content.contains("अमन Kumar"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun futureSchemaIsRejected() {
        Document.fromJson(Document().toJson().put("schemaVersion", 2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsafeIdIsRejected() {
        Document.fromJson(Document().toJson().put("id", "../escape"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidMarginsAreRejected() {
        PageSettings(marginLeft = -2.0).validate()
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonFiniteMarginIsRejected() {
        PageSettings(marginTop = Double.NaN).validate()
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidFontIsRejected() {
        PageSettings(fontFamily = "untrusted").validate()
    }

    @Test
    fun a4Geometry() {
        assertEquals(210.0, PageSettings().widthMm, 0.0)
        assertEquals(297.0, PageSettings().heightMm, 0.0)
        assertEquals(297.0, PageSettings(landscape = true).widthMm, 0.0)
    }

    @Test
    fun allTemplatesAreStructuredAndEditable() {
        assertEquals(16, Templates.all.size)
        assertEquals(16, Templates.all.map { it.id }.distinct().size)
        Templates.all.forEach {
            assertTrue(it.fields.isNotEmpty())
            assertEquals("doc", JSONObject(it.content()).getString("type"))
            assertFalse(it.content().contains("{{"))
        }
    }

    @Test
    fun templateValuesCannotInjectHtml() {
        val result = Templates.all.first().content(mapOf("Name" to "<script>bad()</script>"))
        val parsed = JSONObject(result)
        assertTrue(parsed.toString().contains("<script>"))
        assertEquals("paragraph", parsed.getJSONArray("content").getJSONObject(0).getString("type"))
    }

    @Test
    fun previewAndCreatedDocumentUseTheSameLiteralValues() {
        Templates.all.forEach { template ->
            val values = template.fields.associateWith { "अमन $1 \\ literal {{value}}" }
            val preview = template.filledParagraphs(values)
            val nodes = JSONObject(template.content(values)).getJSONArray("content")
            assertEquals(preview.size, nodes.length())
            preview.forEachIndexed { i, text ->
                assertEquals(
                    text,
                    nodes
                        .getJSONObject(i)
                        .getJSONArray("content")
                        .getJSONObject(0)
                        .getString("text"),
                )
            }
        }
    }

    @Test
    fun malformedContentIsNeverClassifiedAsDisposableBlank() {
        assertFalse(Document.isBlankJson("{broken"))
        assertFalse(Document.isBlankJson("""{"type":"wrong"}"""))
        assertFalse(Document.isBlankJson("""{"type":"doc","content":[123]}"""))
    }

    @Test
    fun blankClassifierPreservesImagesTablesAndText() {
        assertTrue(Document().isBlankContent())
        listOf(
                """{"type":"image"}""",
                """{"type":"table"}""",
                """{"type":"text","text":"हिन्दी"}""",
            )
            .forEach { assertFalse(Document.isBlankJson("""{"type":"doc","content":[$it]}""")) }
    }
}
