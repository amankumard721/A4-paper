package com.a4sizepaper.typing.texttopdf.pdf

import java.io.File

/** Latest export per document is retained; older versions have a seven-day sharing grace. */
object ExportRetention {
    private val uuid = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    private val name = Regex("($uuid)-$uuid\\.pdf")
    private val sessionStart = System.currentTimeMillis()

    fun clean(directory: File, now: Long = System.currentTimeMillis()): Int {
        val files =
            directory.listFiles()?.filter {
                name.matches(it.name) &&
                    it.isFile &&
                    it.canonicalFile.parentFile == directory.canonicalFile
            } ?: return 0
        val latest =
            files
                .groupBy { name.matchEntire(it.name)!!.groupValues[1] }
                .values
                .mapNotNull { versions ->
                    versions.maxWithOrNull(compareBy<File> { it.lastModified() }.thenBy { it.name })
                }
                .toSet()
        val cutoff = minOf(now, sessionStart) - 7L * 24 * 60 * 60 * 1000
        return files.count { it !in latest && it.lastModified() < cutoff && it.delete() }
    }
}
