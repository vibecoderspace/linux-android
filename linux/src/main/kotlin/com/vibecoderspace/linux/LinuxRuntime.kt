package com.vibecoderspace.linux

import android.content.Context

/**
 * Provisions the single Linux root filesystem used by the host app.
 *
 * The runtime knows about archives, PRoot-based extraction, and Android bind
 * paths. It does not launch processes: the host app owns the terminal, PRoot
 * argv, environment, and process lifecycle.
 *
 * Exactly one image is installed at a time. Installing a different profile
 * replaces it; user data in the image's home directory is kept by default.
 */
interface LinuxRuntime {

    /** The installed image, or null when nothing usable is present. */
    fun installedImage(): RootfsImage?

    /**
     * Downloads and extracts [profile] if needed, then returns the installed image.
     *
     * @param keepHome keep `home/` when an existing image is replaced.
     */
    suspend fun ensureInstalled(
        profile: RootfsProfile,
        keepHome: Boolean = true,
        onProgress: (InstallProgress) -> Unit = {},
    ): RootfsImage

    /** Wipes and reinstalls [image]'s profile, keeping `home/` by default. */
    suspend fun repair(
        image: RootfsImage,
        keepHome: Boolean = true,
        onProgress: (InstallProgress) -> Unit = {},
    ): RootfsImage

    /** Removes the installed image. Cached archives are left in place. */
    suspend fun uninstall(deleteHome: Boolean = true)

    companion object {
        fun proot(
            context: Context,
            binaries: ProotBinaries,
            profiles: List<RootfsProfile> = DefaultProfiles.all,
            options: LinuxRuntimeOptions = LinuxRuntimeOptions(),
        ): LinuxRuntime = ProotRuntime(context, binaries, profiles, options)
    }
}
