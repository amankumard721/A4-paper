package com.a4sizepaper.typing.texttopdf.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream

/**
 * Only scanner Activity results may use private cache files. External imports stay content-only.
 */
internal object ScanSource {
    const val MAX_PAGES = 50

    fun privateCacheFile(cacheDirectory: File, uri: Uri): File? {
        if (uri.scheme == "content") return null
        require(
            uri.scheme == "file" &&
                uri.authority.isNullOrEmpty() &&
                uri.query == null &&
                uri.fragment == null
        ) {
            "The scanner returned an unsupported file. Please scan again."
        }
        val root = cacheDirectory.canonicalFile.toPath()
        val candidate = File(requireNotNull(uri.path)).canonicalFile
        require(
            candidate.toPath().startsWith(root) && candidate.toPath() != root && candidate.isFile
        ) {
            "The scanner returned a file outside its private cache. Please scan again."
        }
        return candidate
    }

    fun open(context: Context, uri: Uri): InputStream {
        val file = privateCacheFile(context.cacheDir, uri)
        return file?.inputStream()
            ?: requireNotNull(context.contentResolver.openInputStream(uri)) {
                "The scanned PDF is no longer available. Please scan again."
            }
    }

    fun validatePages(expected: Int, actual: Int) {
        require(expected in 1..MAX_PAGES && actual == expected) {
            "The scanned page count is invalid. Please scan a batch of 1–50 pages again."
        }
    }
}
