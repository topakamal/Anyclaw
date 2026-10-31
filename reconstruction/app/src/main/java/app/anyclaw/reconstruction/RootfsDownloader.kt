package app.anyclaw.reconstruction

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a rootfs archive over HTTP(S) to [destFile].
 *
 * - Follows redirects (HttpURLConnection default).
 * - Fails on any non-200 response instead of saving an error page as if
 *   it were a rootfs archive.
 * - Reports progress in bytes; [onProgress] is called with
 *   (downloaded, totalOrMinusOne) after each chunk.
 * - Timeouts are explicit: 30s to connect, 60s to read, so a stalled
 *   download cannot hang setup forever.
 */
class RootfsDownloader {

    fun download(
        url: String,
        destFile: File,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): File {
        destFile.parentFile?.mkdirs()
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = true
            connection.connect()
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("download failed: HTTP $code for $url")
            }
            val total = connection.contentLengthLong
            var downloaded = 0L
            connection.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }
            return destFile
        } finally {
            connection.disconnect()
        }
    }
}
