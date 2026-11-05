package com.vibecoderspace.linux

import android.content.Context
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Entry point for running processes inside a Linux root filesystem.
 *
 * The runtime knows about PRoot and Android bind mounts, but nothing about a
 * particular distribution, and nothing about the app that consumes it. OpenCode,
 * shells, installers, and build scripts are all just [LinuxProcessSpec]s.
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

    /** Removes the installed image. */
    suspend fun uninstall(deleteHome: Boolean = true)

    /**
     * Resolves [spec] into a complete PRoot invocation.
     *
     * Use this when a different runner owns the process, such as a PTY-backed
     * terminal session. Headless callers should use [command] or [run].
     */
    fun plan(image: RootfsImage, spec: LinuxProcessSpec): LinuxLaunchPlan

    /** The profile's interactive login shell as a spec. */
    fun shellSpec(image: RootfsImage): LinuxProcessSpec

    /** Starts [spec] inside [image]. The caller owns the returned process. */
    fun launch(image: RootfsImage, spec: LinuxProcessSpec): LinuxProcess

    /** Starts [spec] inside [image]. Alias for [launch] that reads better in setup code. */
    fun command(image: RootfsImage, spec: LinuxProcessSpec): LinuxProcess =
        launch(image, spec)

    /** Builds and starts [builder] inside [image]. */
    fun command(image: RootfsImage, builder: LinuxCommandBuilder): LinuxProcess =
        launch(image, builder.build())

    /**
     * Runs [spec] to completion and captures its output.
     *
     * The process is killed when [timeout] expires. A non-zero exit code is
     * returned in [CommandResult.exitCode] and is not thrown.
     */
    suspend fun run(
        image: RootfsImage,
        spec: LinuxProcessSpec,
        timeout: Duration = DEFAULT_TIMEOUT,
        maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES,
    ): CommandResult

    /** Builds and runs [builder] to completion. */
    suspend fun run(
        image: RootfsImage,
        builder: LinuxCommandBuilder,
        timeout: Duration = DEFAULT_TIMEOUT,
        maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES,
    ): CommandResult = run(image, builder.build(), timeout, maxOutputBytes)

    /** Starts the profile's interactive login shell inside [image]. */
    fun launchShell(image: RootfsImage): LinuxProcess

    companion object {
        val DEFAULT_TIMEOUT: Duration = 10.minutes

        const val DEFAULT_MAX_OUTPUT_BYTES: Int = 256 * 1024

        fun proot(
            context: Context,
            binaries: ProotBinaries,
            profiles: List<RootfsProfile> = DefaultProfiles.all,
            options: LinuxRuntimeOptions = LinuxRuntimeOptions(),
        ): LinuxRuntime = ProotRuntime(context, binaries, profiles, options)
    }
}
