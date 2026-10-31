package app.anyclaw.reconstruction

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * Extracts a ZIP rootfs archive into [destDir].
 *
 * Safety: this is the zip-slip check. Every entry's canonical path must
 * stay inside [destDir]'s canonical path; absolute entries and entries
 * containing `..` that escape the destination are refused with an
 * IOException instead of writing outside the rootfs directory.
 *
 * Only ZIP is supported. A tarball or any other format must be rejected
 * by the caller (or converted first); this extractor does not guess
 * formats from a file extension.
 */
class RootfsExtractor {

    fun extractZip(
        zipFile: File,
        destDir: File,
        onProgress: (extractedFiles: Int) -> Unit = {},
    ): Int {
        if (!zipFile.isFile) {
            throw IOException("archive not found: ${zipFile.path}")
        }
        destDir.mkdirs()
        val destCanonical = destDir.canonicalPath
        var extracted = 0
        zipFile.inputStream().use { fileInput ->
            ZipInputStream(fileInput).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    val target = File(destDir, name)
                    val targetCanonical = target.canonicalPath
                    if (targetCanonical != destCanonical &&
                        !targetCanonical.startsWith(destCanonical + File.separator)
                    ) {
                        throw IOException("refusing zip entry outside destDir: $name")
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { output ->
                            zip.copyTo(output)
                        }
                        extracted++
                        onProgress(extracted)
                    }
                    zip.closeEntry()
                }
            }
        }
        return extracted
    }
}
