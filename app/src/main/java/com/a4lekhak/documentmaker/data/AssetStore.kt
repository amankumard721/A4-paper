package com.a4lekhak.documentmaker.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.AtomicFile
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Immutable, metadata-free images (JPEG photos, PNG transparency/signatures). Photo Picker grants
 * are needed only during copying.
 */
class AssetStore(private val context: Context) {
    private val directory
        get() = File(context.filesDir, "assets").apply { mkdirs() }

    suspend fun importImage(uri: Uri, signature: Boolean = false): String =
        withContext(Dispatchers.IO) {
            val staging = File.createTempFile("picked-", ".image", context.cacheDir)
            try {
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "The selected photo is unavailable" }
                    FileOutputStream(staging).use { output ->
                        val buffer = ByteArray(65536)
                        var total = 0
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            total += n
                            require(total <= 20 * 1024 * 1024) {
                                "Choose an image smaller than 20 MB"
                            }
                            output.write(buffer, 0, n)
                        }
                    }
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(staging.path, bounds)
                require(bounds.outWidth in 1..32768 && bounds.outHeight in 1..32768) {
                    "Unsupported image dimensions"
                }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
                val bitmap =
                    BitmapFactory.decodeFile(
                        staging.path,
                        BitmapFactory.Options().apply {
                            inSampleSize = sample
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        },
                    ) ?: error("This image could not be decoded")
                try {
                    val exif = ExifInterface(staging)
                    val transform =
                        Matrix().apply {
                            if (exif.isFlipped) postScale(-1f, 1f)
                            postRotate(exif.rotationDegrees.toFloat())
                        }
                    val oriented =
                        Bitmap.createBitmap(
                            bitmap,
                            0,
                            0,
                            bitmap.width,
                            bitmap.height,
                            transform,
                            true,
                        )
                    try {
                        val lossless = signature || oriented.hasAlpha()
                        val format =
                            if (lossless) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                        val id = "${UUID.randomUUID()}.${if (lossless) "png" else "jpg"}"
                        val atomic = AtomicFile(File(directory, id))
                        val output = atomic.startWrite()
                        try {
                            check(oriented.compress(format, if (lossless) 100 else 85, output))
                            atomic.finishWrite(output)
                        } catch (e: Exception) {
                            atomic.failWrite(output)
                            throw e
                        }
                        id
                    } finally {
                        if (oriented !== bitmap) oriented.recycle()
                    }
                } finally {
                    bitmap.recycle()
                }
            } finally {
                staging.delete()
            }
        }

    fun file(id: String): File? {
        if (!NAME.matches(id)) return null
        val candidate = File(directory, id)
        return candidate.takeIf {
            it.isFile && it.canonicalFile.parentFile == directory.canonicalFile
        }
    }

    companion object {
        val NAME =
            Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(?:png|jpg)")

        fun references(content: String): List<String> {
            require(content.length <= 8_000_000) { "Document is too large" }
            return references(JSONObject(content))
        }

        fun references(root: JSONObject): List<String> {
            require(root.getString("type") == "doc") { "Invalid document" }
            val result = linkedSetOf<String>()
            fun walk(node: JSONObject, depth: Int) {
                require(depth < 80) { "Document nesting is too deep" }
                if (node.optString("type") == "image") {
                    val id = node.getJSONObject("attrs").getString("assetId")
                    require(NAME.matches(id)) { "Invalid image reference" }
                    result.add(id)
                }
                node.optJSONArray("content")?.let { nodes ->
                    repeat(nodes.length()) { walk(nodes.getJSONObject(it), depth + 1) }
                }
            }
            walk(root, 0)
            return result.toList()
        }
    }
}
