package com.a4sizepaper.typing.texttopdf.data

import java.io.File

/** Called only after a complete, successful startup scan of all persistent snapshots. */
object AssetGarbageCollector {
    fun collect(directory: File, retained: Set<String>, olderThan: Long): Int {
        var deleted = 0
        directory.listFiles()?.forEach { file ->
            if (
                AssetStore.NAME.matches(file.name) &&
                    file.isFile &&
                    file.canonicalFile.parentFile == directory.canonicalFile &&
                    file.name !in retained &&
                    file.lastModified() < olderThan &&
                    file.delete()
            ) {
                deleted++
            }
        }
        return deleted
    }
}
