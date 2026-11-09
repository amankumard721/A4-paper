package com.a4sizepaper.typing.texttopdf.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.a4sizepaper.typing.texttopdf.domain.Document
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

class WordImporter(private val context: Context) {
    data class Imported(val document: Document, val warnings: List<String>)

    suspend fun import(uri: Uri): Imported =
        withContext(Dispatchers.IO) {
            require(uri.scheme == "content") { "Choose the Word file using the document picker." }
            val name =
                context.contentResolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    ?: "Word document.docx"
            require(!name.endsWith(".doc", true)) {
                "Old .doc files are not supported yet. In Word, save a .docx copy and open it here."
            }
            val staging = File.createTempFile("word-import-", ".docx", context.cacheDir)
            val assetStore = AssetStore(context)
            val created = mutableListOf<String>()
            try {
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "The selected Word file is unavailable." }
                    staging.outputStream().use { output ->
                        val buffer = ByteArray(16384)
                        var total = 0
                        while (true) {
                            ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            total += n
                            require(total <= 20 * 1024 * 1024) {
                                "Choose a Word document smaller than 20 MB."
                            }
                            output.write(buffer, 0, n)
                        }
                    }
                }
                ensureActive()
                val parsed = DocxReader().read(staging)
                val warnings = parsed.warnings.toMutableSet()
                val assetIds = mutableMapOf<String, String>()
                for ((part, bytes) in parsed.images) {
                    ensureActive()
                    val imageFile = File.createTempFile("word-image-", ".image", context.cacheDir)
                    try {
                        imageFile.writeBytes(bytes)
                        val id = assetStore.importImage(Uri.fromFile(imageFile))
                        created += id
                        assetIds[part] = id
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: IllegalArgumentException) {
                        warnings += "An unsupported image was replaced with a placeholder."
                    } finally {
                        imageFile.delete()
                    }
                }
                fun replaceImages(node: JSONObject) {
                    if (node.optString("type") == "image") {
                        val attrs = node.getJSONObject("attrs")
                        val id = assetIds[attrs.getString("sourcePart")]
                        attrs.remove("sourcePart")
                        if (id == null) {
                            node.put("type", "paragraph")
                            node.remove("attrs")
                            node.put(
                                "content",
                                org.json
                                    .JSONArray()
                                    .put(
                                        JSONObject()
                                            .put("type", "text")
                                            .put("text", "[Image could not be imported]")
                                    ),
                            )
                        } else {
                            attrs.put("assetId", id)
                            attrs.put("src", "/document-assets/$id")
                        }
                    }
                    node.optJSONArray("content")?.let { children ->
                        repeat(children.length()) { replaceImages(children.getJSONObject(it)) }
                    }
                }
                replaceImages(parsed.content)
                val document =
                    Document(
                        title =
                            name
                                .substringBeforeLast('.')
                                .trim()
                                .ifEmpty { "Imported Word document" }
                                .take(200),
                        content = parsed.content.toString(),
                        page = parsed.page,
                        assets = created.toList(),
                    )
                Imported(document, warnings.toList())
            } catch (e: Exception) {
                created.forEach { assetStore.file(it)?.delete() }
                throw e
            } finally {
                staging.delete()
            }
        }
}
