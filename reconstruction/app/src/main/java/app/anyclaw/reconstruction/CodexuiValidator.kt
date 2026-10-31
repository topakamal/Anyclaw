package app.anyclaw.reconstruction

import java.io.File

/**
 * Simplified, original re-implementation of the start-time check observed
 * in the 2.0.514 APK as ProcessManager.validateInstalledCodexuiLatest().
 *
 * The original, inside proot with a 20-second timeout, is:
 *
 *   pkg="/usr/local/lib/node_modules/codexui-android"
 *   [ -f "$pkg/dist-cli/index.js" ]
 *   NODE_PATH="/usr/local/lib/node_modules" node -e "require('express/lib/request')"
 *
 * This reconstruction cannot run node without a node binary, so it uses
 * file presence as an explicit proxy: dist-cli must exist and an express
 * request module must be resolvable under the same node_modules root.
 * Callers must not present a `true` here as proof that node can actually
 * load express; that proof still requires a real node + rootfs run.
 *
 * The repair path observed in the original (not executed here) is
 * documented in [describeRepair]: stage a fresh
 * `pnpm add codexui-android@latest` install, validate it in staging,
 * then swap it into the live root with a backup/restore on failure.
 */
class CodexuiValidator {

    fun validate(rootfsDir: File): Boolean {
        val nodeModules = File(rootfsDir, "usr/local/lib/node_modules")
        val distCli = File(nodeModules, "codexui-android/dist-cli/index.js")
        if (!distCli.isFile) return false
        val expressRequest = File(nodeModules, "express/lib/request.js")
        val bundledExpress = File(
            nodeModules,
            "codexui-android/node_modules/express/lib/request.js",
        )
        return expressRequest.isFile || bundledExpress.isFile
    }

    fun describeRepair(): String = """
        Repair (observed, not executed by this reconstruction):
        1. pnpm add codexui-android@latest --prefer-offline into a staging dir
        2. In staging, check dist-cli/index.js exists and
           node -e "require('express/lib/request')" succeeds
        3. Back up the live package, copy the staged node_modules over it,
           restore the backup if the copy fails
        4. If repair still fails, the original aborts the start with
           "Repair failed; aborting latest start"
    """.trimIndent()
}
