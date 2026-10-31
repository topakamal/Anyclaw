package app.anyclaw.reconstruction

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotExecutorTest {

    private fun tempDir(prefix: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), prefix + System.nanoTime())
        dir.mkdirs()
        return dir
    }

    /** Fake proot: ignores its proot arguments, evaluates the last argument. */
    private fun fakeEvalProot(dir: File): File {
        val script = File(dir, "fake-proot")
        script.writeText("#!/bin/sh\nfor last; do true; done\neval \"\$last\"\n")
        script.setExecutable(true)
        return script
    }

    /** Fake proot: always exits 0, regardless of the command. */
    private fun fakeAlwaysOkProot(dir: File): File {
        val script = File(dir, "fake-proot-ok")
        script.writeText("#!/bin/sh\nexit 0\n")
        script.setExecutable(true)
        return script
    }

    /** Fake proot: exits 1 only for codex-web-local commands. */
    private fun fakeFailCodexWebLocalProot(dir: File): File {
        val script = File(dir, "fake-proot-fail-cwl")
        script.writeText(
            "#!/bin/sh\nfor last; do true; done\n" +
                "case \"\$last\" in *codex-web-local*) exit 1;; *) exit 0;; esac\n",
        )
        script.setExecutable(true)
        return script
    }

    @Test
    fun buildCommandContainsRootfsAndShellCommand() {
        val rootfs = tempDir("proot-rootfs-")
        try {
            val executor = ProotExecutor(File("/tmp/proot"), rootfs)
            val cmd = executor.buildProotCommand("node --version")
            assertEquals("/tmp/proot", cmd[0])
            assertTrue(cmd.contains("-r"))
            assertTrue(cmd.contains(rootfs.path))
            assertTrue(cmd.contains("/bin/sh"))
            assertTrue(cmd.contains("-c"))
            assertEquals("node --version", cmd.last())
        } finally {
            rootfs.deleteRecursively()
        }
    }

    @Test
    fun missingProotBinaryReturnsMinusOne() {
        val rootfs = tempDir("proot-missing-")
        try {
            val result = ProotExecutor(File(rootfs, "no-such-proot"), rootfs)
                .execute("echo hi")
            assertEquals(-1, result.exitCode)
            assertFalse(result.timedOut)
        } finally {
            rootfs.deleteRecursively()
        }
    }

    @Test
    fun fakeProotRunsCommandAndReturnsOutput() {
        val dir = tempDir("proot-eval-")
        try {
            val fake = fakeEvalProot(dir)
            val result = ProotExecutor(fake, dir).execute("echo hello-proot")
            assertEquals(0, result.exitCode)
            assertFalse(result.timedOut)
            assertEquals("hello-proot", result.output)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun timeoutIsReportedAndNotAPass() {
        val dir = tempDir("proot-timeout-")
        try {
            val fake = fakeEvalProot(dir)
            val result = ProotExecutor(fake, dir).execute("sleep 5", timeoutMs = 500)
            assertTrue(result.timedOut)
            assertEquals(-1, result.exitCode)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun verifyWithProotPassesWhenFakeAlwaysOk() {
        val dir = tempDir("proot-verify-ok-")
        try {
            val fake = fakeAlwaysOkProot(dir)
            val results = SetupVerifier().verifyWithProot(dir, fake)
            assertEquals(SetupStep.values().size, results.size)
            assertTrue(SetupVerifier().allRequiredPassed(results))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun verifyWithProotFailsOnlyCodexWebLocalWhenFakeFailsIt() {
        val dir = tempDir("proot-verify-cwl-")
        try {
            val fake = fakeFailCodexWebLocalProot(dir)
            val results = SetupVerifier().verifyWithProot(dir, fake)
            val cwl = results.first { it.step == SetupStep.CODEX_WEB_LOCAL }
            assertFalse(cwl.passed)
            assertFalse(SetupVerifier().allRequiredPassed(results))
            // Earlier steps still pass through the same fake proot.
            assertTrue(results.first { it.step == SetupStep.NODE }.passed)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun verifyWithProotMissingBinaryFailsAllSteps() {
        val dir = tempDir("proot-verify-missing-")
        try {
            val results = SetupVerifier()
                .verifyWithProot(dir, File(dir, "no-such-proot"))
            assertTrue(results.all { !it.passed })
        } finally {
            dir.deleteRecursively()
        }
    }
}
