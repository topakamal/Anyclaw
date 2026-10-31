package app.anyclaw.reconstruction

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupVerifierTest {

    private fun tempDir(prefix: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), prefix + System.nanoTime())
        dir.mkdirs()
        return dir
    }

    private fun touch(file: File) {
        file.parentFile?.mkdirs()
        file.writeText("test")
    }

    @Test
    fun emptyRootfsFailsCodexWebLocal() {
        val rootfs = tempDir("empty-rootfs-")
        try {
            val results = SetupVerifier().verify(rootfs)
            val step = results.first { it.step == SetupStep.CODEX_WEB_LOCAL }
            assertFalse(step.passed)
            assertTrue(step.detail.contains("codex-web-local validation failed"))
        } finally {
            rootfs.deleteRecursively()
        }
    }

    @Test
    fun distCliPassesCodexWebLocalAndValidatorWithExpress() {
        val rootfs = tempDir("full-rootfs-")
        try {
            touch(File(rootfs, "usr/local/lib/node_modules/codexui-android/dist-cli/index.js"))
            touch(File(rootfs, "usr/local/lib/node_modules/express/lib/request.js"))
            val results = SetupVerifier().verify(rootfs)
            assertTrue(results.first { it.step == SetupStep.CODEX_WEB_LOCAL }.passed)
            assertTrue(CodexuiValidator().validate(rootfs))
        } finally {
            rootfs.deleteRecursively()
        }
    }

    @Test
    fun validatorFailsWithoutExpress() {
        val rootfs = tempDir("no-express-")
        try {
            touch(File(rootfs, "usr/local/lib/node_modules/codexui-android/dist-cli/index.js"))
            assertFalse(CodexuiValidator().validate(rootfs))
        } finally {
            rootfs.deleteRecursively()
        }
    }
}
