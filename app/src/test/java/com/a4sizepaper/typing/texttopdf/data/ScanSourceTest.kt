package com.a4sizepaper.typing.texttopdf.data

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class ScanSourceTest {
    private lateinit var context: Application

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun acceptsActualSdkPrivateCacheFileAndReadsBytes() {
        val output = File.createTempFile("scan-", ".pdf", context.cacheDir)
        try {
            output.writeText("%PDF-scanner")
            val uri = Uri.fromFile(output)
            assertEquals(output.canonicalFile, ScanSource.privateCacheFile(context.cacheDir, uri))
            assertEquals(
                "%PDF-scanner",
                ScanSource.open(context, uri).bufferedReader().use { it.readText() },
            )
        } finally {
            output.delete()
        }
    }

    @Test
    fun rejectsFilesOutsideCacheTraversalAuthoritiesAndUnsupportedSchemes() {
        val outside = File.createTempFile("document-", ".pdf", context.filesDir)
        try {
            val rejected =
                listOf(
                    Uri.fromFile(outside),
                    Uri.parse("file://${context.cacheDir.path}/../files/${outside.name}"),
                    Uri.parse("file://untrusted.example${context.cacheDir.path}/document.pdf"),
                    Uri.parse("https://example.test/document.pdf"),
                    Uri.parse("file://${context.cacheDir.path}?query=1"),
                )
            for (uri in rejected) assertThrows(IllegalArgumentException::class.java) {
                ScanSource.privateCacheFile(context.cacheDir, uri)
            }
            assertThrows(IllegalArgumentException::class.java) {
                ScanSource.privateCacheFile(context.cacheDir, Uri.fromFile(context.cacheDir))
            }
        } finally {
            outside.delete()
        }
    }

    @Test
    fun symlinkEscapeIsNotTrustedWhenHostSupportsSymlinks() {
        val outside = File.createTempFile("outside-", ".pdf", context.filesDir)
        val link = File(context.cacheDir, "scan-link-${System.nanoTime()}.pdf")
        try {
            val made =
                runCatching {
                        Files.createSymbolicLink(link.toPath(), outside.toPath())
                        true
                    }
                    .getOrDefault(false)
            assumeTrue("Host cannot create test symlinks", made)
            assertThrows(IllegalArgumentException::class.java) {
                ScanSource.privateCacheFile(context.cacheDir, Uri.fromFile(link))
            }
        } finally {
            link.delete()
            outside.delete()
        }
    }

    @Test
    fun contentResultRemainsContentAndExternalFileImportsStayRejected() {
        assertNull(
            ScanSource.privateCacheFile(
                context.cacheDir,
                Uri.parse("content://scanner.provider/result.pdf"),
            )
        )
        val importer = PdfImporter(context)
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                importer.import(Uri.fromFile(File(context.cacheDir, "untrusted.pdf")))
            }
        }
    }

    @Test
    fun validatesActualPageCountAndBatchBounds() {
        ScanSource.validatePages(1, 1)
        ScanSource.validatePages(50, 50)
        for ((expected, actual) in listOf(0 to 0, 51 to 51, 1 to 2, 2 to 1, 1 to 0)) {
            assertThrows(IllegalArgumentException::class.java) {
                ScanSource.validatePages(expected, actual)
            }
        }
    }
}
