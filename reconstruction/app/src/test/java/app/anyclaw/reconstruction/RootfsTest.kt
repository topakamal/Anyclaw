package app.anyclaw.reconstruction

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
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
        // Deliberately a hand-rolled ServerSocket, not com.sun.net.httpserver:
        // the com.sun HttpServer class is not resolvable when Android Gradle
        // Plugin compiles unit tests, which is what broke CI run 41169932228
        // (compileDebugUnitTestKotlin failed before any test ran).
        val dir = tempDir("rootfs-download-")
        val payload = "rootfs-bytes".toByteArray()
        val serverSocket = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
        val port = serverSocket.localPort
        val serverThread = Thread {
            try {
                serverSocket.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    var line: String?
                    do {
                        line = reader.readLine()
                    } while (line != null && line.isNotEmpty())
                    val out = socket.getOutputStream()
                    out.write(
                        (
                            "HTTP/1.1 200 OK\r\n" +
                                "Content-Length: ${payload.size}\r\n" +
                                "Connection: close\r\n\r\n"
                            ).toByteArray(),
                    )
                    out.write(payload)
                    out.flush()
                }
            } catch (ignored: Exception) {
                // Test teardown closes the socket; nothing to assert here.
            }
        }
        serverThread.start()
        try {
            val url = "http://127.0.0.1:$port/rootfs.zip"
            val dest = File(dir, "rootfs.zip")
            var progressCalls = 0
            RootfsDownloader().download(url, dest) { _, _ -> progressCalls++ }
            assertEquals("rootfs-bytes", dest.readText())
            assertTrue(progressCalls > 0)
        } finally {
            serverThread.join(5000)
            try {
                serverSocket.close()
            } catch (ignored: Exception) {
            }
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
