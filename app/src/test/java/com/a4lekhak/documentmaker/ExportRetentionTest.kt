package com.a4lekhak.documentmaker

import com.a4lekhak.documentmaker.pdf.ExportRetention
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ExportRetentionTest {
    @Test
    fun keepsLatestRecentAndUnrelatedFiles() {
        val dir = kotlin.io.path.createTempDirectory("pdf-retention").toFile()
        val doc = "11111111-1111-1111-1111-111111111111"
        fun pdf(n: Int, time: Long) =
            File(dir, "$doc-00000000-0000-0000-0000-${n.toString().padStart(12, '0')}.pdf").apply {
                writeText("fixture")
                assertTrue(setLastModified(time))
            }
        try {
            val now = System.currentTimeMillis()
            val old = pdf(1, 1000)
            val latest = pdf(2, now)
            val recent = pdf(3, now - 1000)
            val other =
                File(
                        dir,
                        "22222222-2222-2222-2222-222222222222-00000000-0000-0000-0000-000000000004.pdf",
                    )
                    .apply {
                        writeText("latest other")
                        setLastModified(1000)
                    }
            val unknown =
                File(dir, "important.pdf").apply {
                    writeText("keep")
                    setLastModified(1000)
                }
            val partial = File(dir, "in-progress.partial").apply { writeText("keep") }
            assertEquals(1, ExportRetention.clean(dir, now))
            assertFalse(old.exists())
            assertTrue(listOf(latest, recent, other, unknown, partial).all { it.exists() })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun missingDirectoryIsHarmless() {
        assertEquals(0, ExportRetention.clean(File("nonexistent-retention-test-directory")))
    }
}
