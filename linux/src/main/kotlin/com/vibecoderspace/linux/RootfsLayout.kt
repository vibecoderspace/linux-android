package com.vibecoderspace.linux

import android.content.Context
import java.io.File

/**
 * Directory layout for one installed image.
 *
 * Everything for a profile lives under `<private>/local/rootfs/<profile-id>/`,
 * which allows several distributions to be installed side by side.
 */
class RootfsLayout(
    val localDir: File,
    val profileId: String,
) {

    val imagesDir: File = File(localDir, "rootfs")
    val imageDir: File = File(imagesDir, profileId)
    val sandboxDir: File = File(imageDir, "sandbox")
    val homeDir: File = File(imageDir, "home")
    val binDir: File = File(imageDir, "bin")
    val initScript: File = File(binDir, "init")
    val statFile: File = File(imageDir, "stat")
    val vmstatFile: File = File(imageDir, "vmstat")
    val metadataFile: File = File(imageDir, "meta.properties")
    val markerFile: File = File(imageDir, ".installed")

    /** `/data/data/<package>` (or `/data/user/0/<package>`). */
    val privateDir: File = localDir.parentFile ?: localDir

    /** Legacy single-image location used by older builds. */
    val legacySandboxDir: File = File(localDir, "sandbox")
    val legacyHomeDir: File = File(localDir, "home")
    val legacyBinDir: File = File(localDir, "bin")
    val legacyMarkerFile: File = File(localDir, ".terminal_setup_ok_DO_NOT_REMOVE")

    fun isInstalled(): Boolean = markerFile.isFile && sandboxDir.isDirectory

    companion object {
        fun localDir(context: Context): File {
            val files = context.filesDir
            return File(files.parentFile ?: files, "local")
        }

        fun forProfile(context: Context, profileId: String): RootfsLayout =
            RootfsLayout(localDir(context), profileId)
    }
}
