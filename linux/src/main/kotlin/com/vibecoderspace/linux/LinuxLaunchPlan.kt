package com.vibecoderspace.linux

import java.io.File

/**
 * A fully resolved PRoot invocation.
 *
 * Both process runners consume the same plan:
 *
 * - [java.lang.ProcessBuilder] uses [arguments], [workingDirectory], and
 *   [environment] for headless processes.
 * - A PTY runner (for example the termux terminal emulator) uses [executable],
 *   [arguments], [workingDirectory], and [environment] for interactive sessions.
 *
 * [workingDirectory] is a host path. The guest working directory is already
 * encoded in [arguments] through PRoot's `-w` option.
 */
data class LinuxLaunchPlan(
    val executable: String,
    val arguments: List<String>,
    val environment: Map<String, String>,
    val workingDirectory: File,
)
