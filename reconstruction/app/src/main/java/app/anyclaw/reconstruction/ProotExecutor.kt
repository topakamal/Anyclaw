package app.anyclaw.reconstruction

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Result of running one command inside proot, mirroring the shape
 * observed in the 2.0.514 APK's ProotManager.CommandResult:
 * an exit code, trimmed combined output, and a timed-out flag.
 * A timed-out or unstarted run reports exitCode -1, as in the original.
 */
data class CommandResult(
    val exitCode: Int,
    val output: String,
    val timedOut: Boolean,
)

/**
 * Original design for running shell commands inside a rootfs via proot.
 *
 * This is NOT copied code from the 2.0.514 APK: the exact argument list
 * of its buildProotCommand() was not dumped in the earlier analysis, so
 * this class uses a straightforward, documented invocation instead:
 *
 *   <proot> -r <rootfs> -w /root /bin/sh -c "<command>"
 *
 * with HOME=/root, PATH=/usr/local/bin:/usr/bin:/bin and LANG=C.UTF-8,
 * plus any caller-supplied extra environment. Those three variables match
 * the environment observed in the original's execute paths.
 *
 * Differences from the original that callers should know:
 * - The original SetupManager.executeInProot() waits without a timeout.
 *   This executor always applies a timeout (default 30 seconds) and
 *   forcibly destroys the process on timeout, so a hanging --help or
 *   --version check cannot hang the UI forever.
 * - No proot binary or rootfs is bundled with this reconstruction.
 *   If [prootBinary] does not exist, [execute] returns exitCode -1
 *   instead of throwing, so the UI can report "proot not available".
 */
class ProotExecutor(
    private val prootBinary: File,
    private val rootfsDir: File,
) {

    fun buildProotCommand(command: String): List<String> = listOf(
        prootBinary.path,
        "-r", rootfsDir.path,
        "-w", "/root",
        "/bin/sh", "-c", command,
    )

    fun buildEnvironment(extraEnv: Map<String, String> = emptyMap()): Map<String, String> {
        val env = linkedMapOf(
            "HOME" to "/root",
            "PATH" to "/usr/local/bin:/usr/bin:/bin",
            "LANG" to "C.UTF-8",
        )
        env.putAll(extraEnv)
        return env
    }

    fun execute(
        command: String,
        timeoutMs: Long = 30_000,
        extraEnv: Map<String, String> = emptyMap(),
    ): CommandResult {
        if (!prootBinary.isFile) {
            return CommandResult(-1, "proot binary not found: ${prootBinary.path}", false)
        }
        return try {
            val processBuilder = ProcessBuilder(buildProotCommand(command))
            processBuilder.redirectErrorStream(true)
            processBuilder.environment().putAll(buildEnvironment(extraEnv))
            val process = processBuilder.start()
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                // Drain what is available so a later run is not blocked;
                // the timed-out flag is what callers must check first.
                CommandResult(-1, "", true)
            } else {
                val output = process.inputStream.bufferedReader().readText().trim()
                CommandResult(process.exitValue(), output, false)
            }
        } catch (e: Exception) {
            CommandResult(-1, "failed to run command: ${e.message}", false)
        }
    }
}
