package com.vibecoderspace.linux

/**
 * Runtime-wide settings that are not tied to a distribution.
 *
 * [hostname] is written to the guest's `/etc/hostname` and to `/etc/hosts`.
 * The default is deliberately neutral; apps should pass their own name if they
 * want the guest to show something specific.
 *
 * [dnsServers] are written to `/etc/resolv.conf` when an image is configured.
 */
data class LinuxRuntimeOptions(
    val hostname: String = DEFAULT_HOSTNAME,
    val dnsServers: List<String> = DEFAULT_DNS_SERVERS,
) {
    init {
        require(hostname.isNotBlank()) { "hostname must not be blank" }
        require(hostname.none { it.isWhitespace() }) {
            "hostname must not contain whitespace: $hostname"
        }
        require(dnsServers.isNotEmpty()) { "dnsServers must not be empty" }
        require(dnsServers.all { it.isNotBlank() && it.none(Char::isWhitespace) }) {
            "dnsServers must contain non-blank addresses without whitespace: $dnsServers"
        }
    }

    companion object {
        const val DEFAULT_HOSTNAME: String = "localhost"

        val DEFAULT_DNS_SERVERS: List<String> = listOf("1.1.1.1", "8.8.8.8")
    }
}
