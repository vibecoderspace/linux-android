package com.vibecoderspace.linux

/** The result of a one-shot [LinuxRuntime.run] call. */
data class CommandResult(
    val exitCode: Int,
    val output: String,
    val truncated: Boolean = false,
    val timedOut: Boolean = false,
) {
    val isSuccess: Boolean get() = exitCode == 0 && !timedOut
}
