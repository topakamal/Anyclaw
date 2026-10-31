package app.anyclaw.reconstruction

import java.io.File

/**
 * Where a rootfs archive comes from.
 *
 * The 2.0.514 APK observes three kinds of sources: a Play Asset Delivery
 * rootfs pack as the primary path, a GitHub download fallback, and a
 * user-selected manual file. This reconstruction implements the latter
 * two as data only, plus a plain URL, and does NOT implement Play Asset
 * Delivery (that needs a Play Asset Pack configured for this app).
 *
 * WARNING, carried over from the earlier diagnosis: do not feed this
 * manager an arbitrary Termux bootstrap zip. Only use a rootfs archive
 * supplied or linked by the same release as the app you are rebuilding,
 * and check its hash/checksum when the release provides one. The format
 * of that archive may differ (for example a tarball); this reconstruction
 * extracts ZIP archives only, and will refuse anything else rather than
 * silently extracting the wrong format.
 */
sealed class RootfsSource {
    /** A file already on the device, for example chosen by the user. */
    data class Manual(val file: File) : RootfsSource()

    /** Any direct download URL supplied by the caller. */
    data class Url(val url: String) : RootfsSource()

    /**
     * A GitHub release asset. The asset name is deliberately NOT
     * hard-coded here: it must come from the release page itself, because
     * guessing it would download the wrong file. The base URL below is
     * the fallback base observed in the 2.0.514 APK.
     */
    data class GitHubRelease(
        val assetName: String,
        val tag: String = "rootfs-latest",
        val baseUrl: String =
            "https://github.com/friuns2/anyclaw-rootfs-assets/releases/download",
    ) : RootfsSource() {
        fun url(): String = "$baseUrl/$tag/$assetName"
    }

    fun downloadUrl(): String? = when (this) {
        is Manual -> null
        is Url -> url
        is GitHubRelease -> url()
    }

    fun fileName(): String = when (this) {
        is Manual -> file.name
        is Url -> url.substringAfterLast('/').ifBlank { "rootfs.zip" }
        is GitHubRelease -> assetName
    }
}
