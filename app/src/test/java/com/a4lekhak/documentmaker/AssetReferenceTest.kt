package com.a4lekhak.documentmaker

import com.a4lekhak.documentmaker.data.AssetStore
import com.a4lekhak.documentmaker.domain.Document
import org.junit.Assert.*
import org.junit.Test

class AssetReferenceTest {
    private val id = "11111111-1111-1111-1111-111111111111.png"

    @Test
    fun imageAndTableRoundTripKeepsReferences() {
        val content =
            """{"type":"doc","content":[{"type":"image","attrs":{"assetId":"$id","width":50,"align":"right"}},{"type":"table","content":[{"type":"tableRow","content":[{"type":"tableCell","content":[{"type":"paragraph"}]}]}]}]}"""
        val doc = Document(content = content, assets = AssetStore.references(content))
        val reopened = Document.fromJson(doc.toJson())
        assertEquals(listOf(id), reopened.assets)
        assertEquals(doc.toJson().toString(), reopened.toJson().toString())
    }

    @Test
    fun duplicateImageReferenceIsStoredOnce() {
        val image = """{"type":"image","attrs":{"assetId":"$id"}}"""
        assertEquals(
            listOf(id),
            AssetStore.references("""{"type":"doc","content":[$image,$image]}"""),
        )
    }

    @Test
    fun traversalAndExternalUrlsAreRejected() {
        listOf("../documents/secret.json", "https://example.com/image.png", "/foo.png").forEach {
            bad ->
            assertThrows(IllegalArgumentException::class.java) {
                AssetStore.references(
                    """{"type":"doc","content":[{"type":"image","attrs":{"assetId":"$bad"}}]}"""
                )
            }
        }
    }

    @Test
    fun oldDocumentNeedsNoMigration() {
        assertTrue(Document.fromJson(Document().toJson()).assets.isEmpty())
    }

    @Test
    fun jpegAndEscapedJsonKeysAreSupported() {
        val jpg = id.replace(".png", ".jpg")
        val content = """{"type":"doc","content":[{"type":"image","attrs":{"assetId":"$jpg"}}]}"""
        assertEquals(listOf(jpg), AssetStore.references(content))
        assertEquals(
            listOf(jpg),
            AssetStore.references(content.replace("assetId", "asset" + "\\u0049d")),
        )
        assertThrows(Exception::class.java) { AssetStore.references("{broken json}") }
    }

    @Test
    fun cleanupPreservesSharedRecentAndUnknownFiles() {
        val dir = kotlin.io.path.createTempDirectory("asset-gc").toFile()
        try {
            val old =
                java.io.File(dir, id).apply {
                    writeText("old")
                    setLastModified(1000)
                }
            val shared =
                java.io.File(dir, id.replace("11111111-", "22222222-")).apply {
                    writeText("shared")
                    setLastModified(1000)
                }
            val recent = java.io.File(dir, id.replace(".png", ".jpg")).apply { writeText("new") }
            val unknown =
                java.io.File(dir, "unknown.txt").apply {
                    writeText("keep")
                    setLastModified(1000)
                }
            assertEquals(
                1,
                com.a4lekhak.documentmaker.data.AssetGarbageCollector.collect(
                    dir,
                    setOf(shared.name),
                    2000,
                ),
            )
            assertFalse(old.exists())
            assertTrue(shared.exists() && recent.exists() && unknown.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
