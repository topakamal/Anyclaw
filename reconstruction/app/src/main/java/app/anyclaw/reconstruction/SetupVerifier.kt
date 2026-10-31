package app.anyclaw.reconstruction

import java.io.File

/**
 * Simplified, original re-implementation of the setup verification idea
 * observed in the 2.0.514 APK.
 *
 * The original verify() runs, in order, shell commands inside proot such
 * as `node --version`, `codex --version`, and finally:
 *
 *   codex-web-local --help || node /usr/local/lib/node_modules/codexui-android/dist-cli/index.js --help
 *
 * and throws "codex-web-local validation failed" when that last command
 * exits non-zero, after the earlier steps have passed.
 *
 * This class cannot run those commands without a real rootfs, node and
 * codexui install, so it checks the equivalent file presence under
 * [rootfsDir] instead. The codex-web-local step passes when either the
 * legacy shim or the codexui-android dist-cli file exists, mirroring the
 * `||` in the original command. A missing rootfs therefore fails early
 * and visibly, instead of pretending the full install is present.
 */
class SetupVerifier {

    fun verify(rootfsDir: File): List<StepResult> {
        val results = ArrayList<StepResult>()

        results += StepResult(
            SetupStep.PROOT,
            rootfsDir.isDirectory,
            if (rootfsDir.isDirectory) "rootfs dir found: ${rootfsDir.path}"
            else "rootfs dir not found: ${rootfsDir.path}",
        )
        results += StepResult(
            SetupStep.NODE,
            File(rootfsDir, "usr/bin/node").exists() ||
                File(rootfsDir, "usr/local/bin/node").exists(),
            "node binary under rootfs usr/bin or usr/local/bin",
        )
        results += StepResult(
            SetupStep.OPENCLAW,
            File(rootfsDir, "usr/local/bin/openclaw").exists(),
            "openclaw binary under rootfs usr/local/bin",
        )
        results += StepResult(
            SetupStep.CODEX_CLI,
            File(rootfsDir, "usr/local/bin/codex").exists(),
            "codex binary under rootfs usr/local/bin",
        )

        val shim = File(rootfsDir, "usr/local/bin/codex-web-local")
        val distCli = File(
            rootfsDir,
            "usr/local/lib/node_modules/codexui-android/dist-cli/index.js",
        )
        val codexWebLocalOk = shim.exists() || distCli.exists()
        results += StepResult(
            SetupStep.CODEX_WEB_LOCAL,
            codexWebLocalOk,
            if (codexWebLocalOk) {
                "found: " + if (distCli.exists()) distCli.path else shim.path
            } else {
                // Same user-visible wording as the original failure, in a
                // reconstruction context: neither --help target exists.
                "codex-web-local validation failed: " +
                    "neither ${shim.path} nor ${distCli.path} exists"
            },
        )

        results += StepResult(
            SetupStep.OPENSSH,
            File(rootfsDir, "usr/sbin/sshd").exists(),
            "sshd under rootfs usr/sbin",
        )
        results += StepResult(
            SetupStep.CHROMIUM,
            File(rootfsDir, "usr/bin/chromium").exists(),
            "Chromium is optional: missing only produces a warning in the original",
        )

        return results
    }

    /**
     * Run the same steps as real commands inside proot, using the exact
     * command strings observed in SetupManager.verify(). This is the
     * first path in the reconstruction that actually executes something,
     * rather than only checking file presence.
     *
     * If the proot binary is missing, every step is reported as failed
     * with that reason, instead of silently falling back to file checks:
     * callers that want the file-check path should call [verify] instead.
     * A timed-out command is a failure, not a pass.
     */
    fun verifyWithProot(
        rootfsDir: File,
        prootBinary: File,
        timeoutMs: Long = 30_000,
    ): List<StepResult> {
        if (!prootBinary.isFile) {
            val reason = "proot binary not found: ${prootBinary.path}"
            return SetupStep.values().map { StepResult(it, false, reason) }
        }
        val executor = ProotExecutor(prootBinary, rootfsDir)
        fun run(step: SetupStep, command: String): StepResult {
            val result = executor.execute(command, timeoutMs)
            val passed = !result.timedOut && result.exitCode == 0
            val detail = when {
                result.timedOut -> "timed out after ${timeoutMs}ms: $command"
                passed -> "exit 0: $command"
                else -> "exit ${result.exitCode}: $command"
            }
            return StepResult(step, passed, detail)
        }
        val results = ArrayList<StepResult>()
        results += StepResult(
            SetupStep.PROOT,
            rootfsDir.isDirectory,
            if (rootfsDir.isDirectory) "rootfs dir found: ${rootfsDir.path}"
            else "rootfs dir not found: ${rootfsDir.path}",
        )
        results += run(SetupStep.NODE, "node --version")
        results += run(
            SetupStep.OPENCLAW,
            "openclaw --version 2>/dev/null || openclaw --help 2>&1 | head -1",
        )
        results += run(SetupStep.CODEX_CLI, "codex --version >/dev/null 2>&1")
        results += run(
            SetupStep.CODEX_WEB_LOCAL,
            "codex-web-local --help >/dev/null 2>&1 || " +
                "node /usr/local/lib/node_modules/codexui-android/dist-cli/index.js --help >/dev/null 2>&1",
        )
        results += run(SetupStep.OPENSSH, "test -x /usr/sbin/sshd")
        results += run(SetupStep.CHROMIUM, "test -x /usr/bin/chromium")
        return results
    }

    /** Overall setup result: every non-optional step must pass. */
    fun allRequiredPassed(results: List<StepResult>): Boolean =
        results.filter { !it.step.optional }.all { it.passed }
}
