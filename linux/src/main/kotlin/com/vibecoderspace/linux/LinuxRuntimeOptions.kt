package com.vibecoderspace.linux

/**
 * Runtime-wide settings that are not tied to a distribution.
 *
 * [hostname] is written to the guest's `/etc/hostname` and to `/etc/hosts`.
 * The default is deliberately neutral; apps should pass their own name if they
 * want the shell prompt to show something specific.
 */
data class LinuxRuntimeOptions(
    val hostname: String = DEFAULT_HOSTNAME,
) {
    init {
        require(hostname.isNotBlank()) { "hostname must not be blank" }
        require(hostname.none { it.isWhitespace() }) {
            "hostname must not contain whitespace: $hostname"
        }
    }

    companion object {
        const val DEFAULT_HOSTNAME: String = "localhost"
    }
}
