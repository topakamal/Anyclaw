package app.anyclaw.reconstruction

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * Minimal setup screen for the reconstruction.
 *
 * It runs [SetupVerifier] against <app files>/rootfs, which will not
 * exist on a fresh install, so the first run is expected to fail at the
 * proot step. That is deliberate: the screen shows exactly which step
 * fails and why, instead of showing a single percentage and a generic
 * failure, which was the confusing part of the original 92% screen.
 *
 * Rootfs can be provided two ways, mirroring the 2.0.514 setup screen:
 * a ZIP downloaded from a URL, or a ZIP file picked from the device
 * ("Select rootfs file" in 2.0.514). In both cases only a ZIP from the
 * same release should be used; [RootfsManager] refuses non-ZIP archives.
 */
class MainActivity : AppCompatActivity() {

    private val verifier = SetupVerifier()
    private val validator = CodexuiValidator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusView = findViewById<TextView>(R.id.status_text)
        val progress = findViewById<ProgressBar>(R.id.progress_bar)
        val button = findViewById<Button>(R.id.verify_button)
        val prepareButton = findViewById<Button>(R.id.prepare_rootfs_button)
        val pickButton = findViewById<Button>(R.id.pick_rootfs_button)
        val actionButtons = listOf(button, prepareButton, pickButton)

        statusView.text = getString(
            R.string.intro,
            File(filesDir, "rootfs").path,
        )

        button.setOnClickListener {
            button.isEnabled = false
            progress.visibility = View.VISIBLE
            statusView.text = getString(R.string.running)
            Thread {
                val rootfsDir = File(filesDir, "rootfs")
                val prootBinary = File(filesDir, "proot")
                // If a proot binary has been placed in app files, run the
                // real commands inside it; otherwise fall back to the
                // file-presence checks. No proot binary is bundled, so a
                // fresh install still takes the file-check path.
                val mode = if (prootBinary.isFile) "proot" else "file-check"
                val results = if (prootBinary.isFile) {
                    verifier.verifyWithProot(rootfsDir, prootBinary)
                } else {
                    verifier.verify(rootfsDir)
                }
                val codexuiValid = validator.validate(rootfsDir)
                val report = buildString {
                    append("mode: $mode\n\n")
                    for (result in results) {
                        val mark = if (result.passed) "OK  " else "FAIL"
                        append(mark)
                        append(" - ")
                        append(result.step.label)
                        append("\n    ")
                        append(result.detail)
                        append("\n")
                    }
                    append("\ncodexui-android start check: ")
                    append(if (codexuiValid) "valid (file proxy)" else "invalid")
                    append("\n")
                    append(
                        if (verifier.allRequiredPassed(results)) {
                            "All required checks passed!"
                        } else {
                            "Setup incomplete: install a rootfs first. " +
                                "This reconstruction does not bundle one."
                        },
                    )
                }
                runOnUiThread {
                    progress.visibility = View.GONE
                    statusView.text = report
                    button.isEnabled = true
                }
            }.start()
        }

        val urlInput = findViewById<EditText>(R.id.rootfs_url_input)
        prepareButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isBlank()) {
                statusView.text = getString(R.string.rootfs_url_empty)
                return@setOnClickListener
            }
            runPrepare(
                RootfsSource.Url(url),
                actionButtons,
                statusView,
                progress,
            )
        }

        val pickLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) {
                statusView.text = getString(R.string.rootfs_pick_cancelled)
                return@registerForActivityResult
            }
            val rootfsDir = File(filesDir, "rootfs")
            if (RootfsManager().isReady(rootfsDir)) {
                statusView.text = getString(
                    R.string.rootfs_already_ready,
                    rootfsDir.path,
                )
                return@registerForActivityResult
            }
            actionButtons.forEach { it.isEnabled = false }
            progress.visibility = View.VISIBLE
            statusView.text = getString(R.string.copying_rootfs)
            Thread {
                // Copy first: the picked Uri is only valid while this
                // activity holds the grant, and RootfsManager works on
                // plain files, not content Uris.
                val copied = copyPickedFile(uri)
                runOnUiThread {
                    progress.visibility = View.GONE
                    actionButtons.forEach { it.isEnabled = true }
                    if (copied == null) {
                        statusView.text = getString(R.string.rootfs_copy_failed)
                    } else {
                        runPrepare(
                            RootfsSource.Manual(copied),
                            actionButtons,
                            statusView,
                            progress,
                        )
                    }
                }
            }.start()
        }
        pickButton.setOnClickListener {
            pickLauncher.launch(arrayOf("application/zip"))
        }
    }

    /**
     * Runs [RootfsManager.prepare] on a background thread and reports the
     * result in the status view. Buttons are disabled for the duration
     * so a second prepare cannot run against a half-extracted rootfs.
     */
    private fun runPrepare(
        source: RootfsSource,
        buttons: List<Button>,
        statusView: TextView,
        progress: ProgressBar,
    ) {
        buttons.forEach { it.isEnabled = false }
        progress.visibility = View.VISIBLE
        statusView.text = getString(R.string.preparing_rootfs)
        Thread {
            val manager = RootfsManager()
            val rootfsDir = File(filesDir, "rootfs")
            val downloadDir = File(filesDir, "downloads")
            val result = manager.prepare(
                source,
                downloadDir,
                rootfsDir,
            ) { message ->
                runOnUiThread { statusView.text = message }
            }
            val report = when (result) {
                is PrepareResult.AlreadyReady ->
                    "Rootfs sudah siap, tidak diunduh ulang:\n${result.rootfsDir.path}"
                is PrepareResult.Prepared ->
                    "Rootfs siap:\n${result.rootfsDir.path}\n" +
                        "file diekstrak: ${result.extractedFiles}\n" +
                        "arsip: ${result.archiveFile.path}\n" +
                        "Selanjutnya tekan Jalankan Verifikasi."
                is PrepareResult.Failed ->
                    "Gagal menyiapkan rootfs:\n${result.reason}"
            }
            runOnUiThread {
                progress.visibility = View.GONE
                statusView.text = report
                buttons.forEach { it.isEnabled = true }
            }
        }.start()
    }

    /**
     * Copies the document picked by the user into app-private downloads.
     * Returns null if the stream cannot be read or is empty. The file
     * name is sanitized to a single file name (no path separators) and
     * otherwise preserved, so [RootfsManager]'s ZIP-only check still
     * applies to what the user actually picked.
     */
    private fun copyPickedFile(uri: Uri): File? {
        val displayName = contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.takeIf { it.isNotBlank() } ?: "rootfs-manual.zip"
        val safeName = displayName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .ifBlank { "rootfs-manual.zip" }
        val dest = File(File(filesDir, "downloads"), safeName)
        return try {
            dest.parentFile?.mkdirs()
            contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            if (dest.isFile && dest.length() > 0) dest else null
        } catch (e: Exception) {
            null
        }
    }
}
