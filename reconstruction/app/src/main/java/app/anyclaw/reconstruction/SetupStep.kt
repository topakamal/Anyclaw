package app.anyclaw.reconstruction

/**
 * Setup steps, in the same order observed in the 2.0.514 APK's
 * SetupManager.verify() sequence (from static DEX analysis, not from
 * copied source):
 *
 * proot -> Node.js -> OpenClaw -> Codex CLI -> codex-web-local ->
 * OpenSSH server, with Chromium as a warning-only (non-fatal) check.
 *
 * This reconstruction does not execute a real proot environment. It checks
 * for the files a completed rootfs install is expected to contain under
 * a supplied rootfs directory, so the result is honest about what it can
 * and cannot prove on a device without that rootfs.
 */
enum class SetupStep(val label: String, val optional: Boolean = false) {
    PROOT("Checking proot..."),
    NODE("Checking Node.js..."),
    OPENCLAW("Checking OpenClaw..."),
    CODEX_CLI("Checking Codex CLI..."),
    CODEX_WEB_LOCAL("Checking codex-web-local..."),
    OPENSSH("Checking OpenSSH server..."),
    CHROMIUM("Checking Chromium...", optional = true),
}

data class StepResult(
    val step: SetupStep,
    val passed: Boolean,
    val detail: String,
)
