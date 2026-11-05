package com.vibecoderspace.linux

import android.content.Context

/**
 * Provisions the single Linux root filesystem used by the host app.
 *
 * The runtime knows about archives, PRoot-based extraction, and Android bind
 * paths. It does not launch processes: the host app owns the terminal, PRoot
 * argv, environment, and process lifecycle.
 *
 * Exactly one image is installed at a time. [install] reuses a valid image or
 * replaces it when the profile or archive changed; [reinstall] always wipes the
 * sandbox; [delete] removes the image and its home directory.
 */
interface LinuxRuntime {

    /** The installed image, or null when nothing usable is present. */
    fun installedImage(): RootfsImage?

    /**
     * Installs [profile] if no usable image is present.
     *
     * When an image is already installed it is reused as-is, re-configured in
     * place if the profile's configuration changed, or replaced if the profile
     * or archive changed.
     */
    suspend fun install(
        profile: RootfsProfile,
        onProgress: (InstallProgress) -> Unit = {},
    ): RootfsImage

    /**
     * Wipes and reinstalls the currently installed profile.
     *
     * @param keepHome keep `home/` when the sandbox is replaced.
     */
    suspend fun reinstall(
        keepHome: Boolean = true,
        onProgress: (InstallProgress) -> Unit = {},
    ): RootfsImage

    /** Removes the installed image, including `home/`. Cached archives stay. */
    suspend fun delete()

    companion object {
        fun proot(
            context: Context,
            binaries: ProotBinaries,
            profiles: List<RootfsProfile> = DefaultProfiles.all,
        ): LinuxRuntime = ProotRuntime(context, binaries, profiles)
    }
}
