package app.anyclaw.reconstruction

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
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
    }
}
