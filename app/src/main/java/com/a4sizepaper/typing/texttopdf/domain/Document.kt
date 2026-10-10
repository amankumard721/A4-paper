package com.a4sizepaper.typing.texttopdf.domain

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class PageSettings(
    val landscape: Boolean = false,
    val marginTop: Double = 15.0,
    val marginRight: Double = 15.0,
    val marginBottom: Double = 15.0,
    val marginLeft: Double = 15.0,
    val fontFamily: String = "Noto Sans",
    val fontSize: Int = 12,
    val lineHeight: Double = 1.5,
    val paragraphSpacing: Int = 8,
) {
    val widthMm
        get() = if (landscape) 297.0 else 210.0

    val heightMm
        get() = if (landscape) 210.0 else 297.0

    fun validate(): PageSettings {
        require(
            listOf(marginTop, marginRight, marginBottom, marginLeft).all {
                it.isFinite() && it in 5.0..60.0
            }
        ) {
            "Margins must be 5–60 mm"
        }
        require(
            widthMm - marginLeft - marginRight >= 60 && heightMm - marginTop - marginBottom >= 60
        )
        require(
            fontSize in 8..36 &&
                lineHeight.isFinite() &&
                lineHeight in 1.0..2.5 &&
                paragraphSpacing in 0..30
        )
        require(fontFamily in FONTS)
        return this
    }

    fun toJson() =
        JSONObject()
            .put("landscape", landscape)
            .put("marginTop", marginTop)
            .put("marginRight", marginRight)
            .put("marginBottom", marginBottom)
            .put("marginLeft", marginLeft)
            .put("fontFamily", fontFamily)
            .put("fontSize", fontSize)
            .put("lineHeight", lineHeight)
            .put("paragraphSpacing", paragraphSpacing)

    companion object {
        val FONTS = listOf("Noto Sans", "Noto Serif", "Monospace")

        fun fromJson(j: JSONObject) =
            PageSettings(
                    j.optBoolean("landscape"),
                    j.optDouble("marginTop", 15.0),
                    j.optDouble("marginRight", 15.0),
                    j.optDouble("marginBottom", 15.0),
                    j.optDouble("marginLeft", 15.0),
                    j.optString("fontFamily", "Noto Sans"),
                    j.optInt("fontSize", 12),
                    j.optDouble("lineHeight", 1.5),
                    j.optInt("paragraphSpacing", 8),
                )
                .validate()
    }
}

data class Document(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Untitled document",
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = createdAt,
    val content: String = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\"}]}",
    val page: PageSettings = PageSettings(),
    val schemaVersion: Int = 1,
    val assets: List<String> = emptyList(),
    val exportedAt: Long? = null,
    val exportPages: Int? = null,
    val isDraft: Boolean = false,
) {
    fun isBlankContent(): Boolean = isBlankJson(content)

    fun hasDefaultTitle() =
        title.isBlank() || title == "Untitled document" || title == "नया दस्तावेज़"

    fun toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("title", title)
            .put("createdAt", createdAt)
            .put("modifiedAt", modifiedAt)
            .put("schemaVersion", schemaVersion)
            .put("content", JSONObject(content))
            .put("page", page.toJson())
            .put("assets", JSONArray(assets))
            .put("exportedAt", exportedAt)
            .put("exportPages", exportPages)
            .put("isDraft", isDraft)

    companion object {
        fun isBlankJson(json: String): Boolean =
            runCatching { isBlankJson(JSONObject(json)) }.getOrDefault(false)

        fun isBlankJson(root: JSONObject): Boolean {
            require(root.getString("type") == "doc")
            fun meaningful(node: JSONObject, depth: Int): Boolean {
                require(depth < 80) { "Document nesting is too deep" }
                val type = node.getString("type")
                if (type == "image" || type == "table") return true
                if (type == "text" && node.getString("text").isNotBlank()) return true
                val children = node.optJSONArray("content") ?: return false
                return (0 until children.length()).any {
                    meaningful(children.getJSONObject(it), depth + 1)
                }
            }
            return !meaningful(root, 0)
        }

        fun fromJson(j: JSONObject): Document {
            require(j.getInt("schemaVersion") == 1) {
                "This document needs a newer version of A4 Paper Typing"
            }
            val id = j.getString("id")
            UUID.fromString(id)
            require(j.getJSONObject("content").getString("type") == "doc")
            return Document(
                id,
                j.getString("title"),
                j.getLong("createdAt"),
                j.getLong("modifiedAt"),
                j.getJSONObject("content").toString(),
                PageSettings.fromJson(j.getJSONObject("page")),
                1,
                j.optJSONArray("assets")?.let { a -> List(a.length()) { a.getString(it) } }
                    ?: emptyList(),
                if (j.isNull("exportedAt")) null else j.getLong("exportedAt"),
                if (j.isNull("exportPages")) null else j.getInt("exportPages"),
                j.optBoolean("isDraft"),
            )
        }
    }
}

data class Template(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val paragraphs: List<String>,
) {
    companion object {
        // Android's ICU regex parser rejects bare closing braces accepted by desktop Java.
        private val placeholder = Regex("\\{\\{([^}]+)\\}\\}")
    }

    val fields: List<String>
        get() =
            placeholder
                .findAll(paragraphs.joinToString("\n"))
                .map { it.groupValues[1] }
                .distinct()
                .toList()

    fun filledParagraphs(values: Map<String, String> = emptyMap()): List<String> =
        paragraphs.map { text ->
            placeholder.replace(text) {
                values[it.groupValues[1]]?.takeIf(String::isNotBlank) ?: "[${it.groupValues[1]}]"
            }
        }

    fun content(values: Map<String, String> = emptyMap()): String {
        val nodes =
            filledParagraphs(values).mapIndexed { index, filled ->
                JSONObject()
                    .put("type", "paragraph")
                    .put(
                        "content",
                        JSONArray()
                            .put(
                                JSONObject().put("type", "text").put("text", filled).apply {
                                    if (index == 0)
                                        put(
                                            "marks",
                                            JSONArray().put(JSONObject().put("type", "bold")),
                                        )
                                }
                            ),
                    )
            }
        return JSONObject().put("type", "doc").put("content", JSONArray(nodes)).toString()
    }
}
