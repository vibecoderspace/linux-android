package com.vibecoderspace.linux

import android.content.Context

/**
 * Entry point for running processes inside a Linux root filesystem.
 *
 * The runtime knows about PRoot and Android bind mounts, but nothing about a
 * particular distribution, and nothing about the app that consumes it. OpenCode,
 * shells, installers, and build scripts are all just [LinuxProcessSpec]s.
 */
interface LinuxRuntime {

    /** Images that are currently installed and pass their profile's validation. */
    fun installedImages(): List<RootfsImage>

    /** Downloads and extracts [profile] if needed, then returns the installed image. */
    suspend fun ensureInstalled(
        profile: RootfsProfile,
        onProgress: (InstallProgress) -> Unit = {},
    ): RootfsImage

    /** Starts [spec] inside [image]. The caller owns the returned process. */
    fun launch(image: RootfsImage, spec: LinuxProcessSpec): LinuxProcess

    /** Starts the profile's interactive login shell inside [image]. */
    fun launchShell(image: RootfsImage): LinuxProcess

    companion object {
        fun proot(
            context: Context,
            binaries: ProotBinaries,
            profiles: List<RootfsProfile> = DefaultProfiles.all,
        ): LinuxRuntime = ProotRuntime(context, binaries, profiles)
    }
}
