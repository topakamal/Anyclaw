package app.anyclaw.reconstruction

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootfsTest {

    private fun tempDir(prefix: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), prefix + System.nanoTime())
        dir.mkdirs()
        return dir
    }

    private fun writeZip(zipFile: File, entries: Map<String, String>) {
        zipFile.parentFile?.mkdirs()
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }

    @Test
    fun extractorExtractsFilesAndRefusesZipSlip() {
        val dir = tempDir("rootfs-extract-")
        try {
            val zip = File(dir, "rootfs.zip")
            writeZip(zip, mapOf("usr/bin/node" to "node", "a/b.txt" to "hello"))
            val dest = File(dir, "out")
            val count = RootfsExtractor().extractZip(zip, dest)
            assertEquals(2, count)
            assertEquals("hello", File(dest, "a/b.txt").readText())

            val evil = File(dir, "evil.zip")
            writeZip(evil, mapOf("../evil.txt" to "nope"))
            var threw = false
            try {
                RootfsExtractor().extractZip(evil, File(dir, "out2"))
            } catch (e: Exception) {
                threw = true
            }
            assertTrue(threw)
            assertFalse(File(dir, "evil.txt").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun downloaderDownloadsFromLocalHttpServer() {
        val dir = tempDir("rootfs-download-")
        val server = HttpServer.create(InetSocketAddress(0), 0)
        try {
            val payload = "rootfs-bytes".toByteArray()
            server.createContext("/rootfs.zip") { exchange ->
                exchange.sendResponseHeaders(200, payload.size.toLong())
                exchange.responseBody.use { it.write(payload) }
            }
            server.start()
            val url = "http://localhost:${server.address.port}/rootfs.zip"
            val dest = File(dir, "rootfs.zip")
            var progressCalls = 0
            RootfsDownloader().download(url, dest) { _, _ -> progressCalls++ }
            assertEquals("rootfs-bytes", dest.readText())
            assertTrue(progressCalls > 0)
        } finally {
            server.stop(0)
            dir.deleteRecursively()
        }
    }

    @Test
    fun managerPreparesManualZipMarksReadyAndSkipsSecondRun() {
        val dir = tempDir("rootfs-manager-")
        try {
            val zip = File(dir, "manual.zip")
            writeZip(zip, mapOf("usr/local/bin/codex" to "codex"))
            val rootfs = File(dir, "rootfs")
            val manager = RootfsManager()
            assertFalse(manager.isReady(rootfs))
            val first = manager.prepare(
                RootfsSource.Manual(zip),
                File(dir, "downloads"),
                rootfs,
            )
            assertTrue(first is PrepareResult.Prepared)
            assertEquals(1, (first as PrepareResult.Prepared).extractedFiles)
            assertEquals("codex", File(rootfs, "usr/local/bin/codex").readText())
            assertTrue(manager.isReady(rootfs))
            val second = manager.prepare(
                RootfsSource.Manual(zip),
                File(dir, "downloads"),
                rootfs,
            )
            assertTrue(second is PrepareResult.AlreadyReady)
            assertTrue(manager.clear(rootfs))
            assertFalse(manager.isReady(rootfs))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun managerRefusesNonZipArchive() {
        val dir = tempDir("rootfs-nonzip-")
        try {
            val fake = File(dir, "rootfs.tar.gz")
            fake.writeText("not a zip")
            val result = RootfsManager().prepare(
                RootfsSource.Manual(fake),
                File(dir, "downloads"),
                File(dir, "rootfs"),
            )
            assertTrue(result is PrepareResult.Failed)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun githubReleaseUrlUsesObservedBaseAndTag() {
        val source = RootfsSource.GitHubRelease(assetName = "rootfs.zip")
        assertEquals(
            "https://github.com/friuns2/anyclaw-rootfs-assets/releases/download/rootfs-latest/rootfs.zip",
            source.url(),
        )
    }
}
