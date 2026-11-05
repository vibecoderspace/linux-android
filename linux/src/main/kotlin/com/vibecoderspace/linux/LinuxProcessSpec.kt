package com.vibecoderspace.linux

import java.io.File

/** An additional host-to-guest bind mount. */
data class Bind(
    val host: String,
    val guest: String? = null,
)

/**
 * A process to start inside an installed root filesystem.
 *
 * [command] is the guest argv. [workingDirectory] is a guest path such as
 * `/home`, not a host path.
 */
data class LinuxProcessSpec(
    val command: List<String>,
    val workingDirectory: String = "/home",
    val environment: Map<String, String> = emptyMap(),
    val redirectErrorStream: Boolean = true,
    val logFile: File? = null,
    val extraBinds: List<Bind> = emptyList(),
) {
    init {
        require(command.isNotEmpty()) { "command must not be empty" }
    }
}
