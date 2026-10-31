package app.anyclaw.reconstruction

import java.io.File

/**
 * Orchestrates rootfs preparation: resolve a source, download it if
 * needed, extract it, and mark the result ready.
 *
 * The ready marker mirrors the idea of the 2.0.514 APK's
 * getRootfsReadyFile(): a small file inside the rootfs directory whose
 * presence means a previous prepare() completed, so a later run can skip
 * the download and extraction instead of redoing them on every launch.
 * The marker name and contents are this reconstruction's own design.
 */
sealed class PrepareResult {
    /** A previous successful prepare was already present; nothing done. */
    data class AlreadyReady(val rootfsDir: File) : PrepareResult()

    /** Download (if any) and extraction completed in this call. */
    data class Prepared(
        val rootfsDir: File,
        val extractedFiles: Int,
        val archiveFile: File,
    ) : PrepareResult()

    /** Prepare failed; the message explains which step failed. */
    data class Failed(val reason: String) : PrepareResult()
}

class RootfsManager(
    private val downloader: RootfsDownloader = RootfsDownloader(),
    private val extractor: RootfsExtractor = RootfsExtractor(),
) {

    companion object {
        const val READY_MARKER = ".rootfs-ready"
    }

    fun isReady(rootfsDir: File): Boolean =
        rootfsDir.isDirectory && File(rootfsDir, READY_MARKER).isFile

    fun prepare(
        source: RootfsSource,
        downloadDir: File,
        rootfsDir: File,
        onProgress: (message: String) -> Unit = {},
    ): PrepareResult {
        if (isReady(rootfsDir)) {
            return PrepareResult.AlreadyReady(rootfsDir)
        }
        return try {
            val archiveFile: File = when (source) {
                is RootfsSource.Manual -> {
                    if (!source.file.isFile) {
                        return PrepareResult.Failed(
                            "manual rootfs file not found: ${source.file.path}",
                        )
                    }
                    source.file
                }
                else -> {
                    val url = source.downloadUrl()
                        ?: return PrepareResult.Failed("source has no download URL")
                    val dest = File(downloadDir, source.fileName())
                    if (dest.isFile && dest.length() > 0) {
                        onProgress("reusing downloaded archive: ${dest.path}")
                        dest
                    } else {
                        onProgress("downloading: $url")
                        downloader.download(url, dest) { downloaded, total ->
                            if (total > 0) {
                                onProgress("downloaded $downloaded / $total bytes")
                            } else {
                                onProgress("downloaded $downloaded bytes")
                            }
                        }
                    }
                }
            }
            if (!archiveFile.name.endsWith(".zip", ignoreCase = true)) {
                return PrepareResult.Failed(
                    "unsupported archive format (ZIP only in this reconstruction): " +
                        archiveFile.name,
                )
            }
            onProgress("extracting: ${archiveFile.path}")
            rootfsDir.mkdirs()
            val extracted = extractor.extractZip(archiveFile, rootfsDir) { count ->
                onProgress("extracted $count files")
            }
            File(rootfsDir, READY_MARKER).writeText(
                "source=${source.fileName()}\narchive=${archiveFile.path}\n",
            )
            PrepareResult.Prepared(rootfsDir, extracted, archiveFile)
        } catch (e: Exception) {
            PrepareResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Removes a prepared rootfs entirely, for a clean-reinstall test. */
    fun clear(rootfsDir: File): Boolean {
        if (!rootfsDir.exists()) return true
        return rootfsDir.deleteRecursively()
    }
}
