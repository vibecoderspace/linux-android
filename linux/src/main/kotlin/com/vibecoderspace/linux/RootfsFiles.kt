package com.vibecoderspace.linux

/**
 * Pure text builders for the files written during rootfs configuration.
 *
 * Kept separate from [RootfsConfigurator] so the content can be unit tested
 * without Android.
 */
internal object RootfsFiles {

    fun hostname(hostname: String): String = "$hostname\n"

    fun resolvConf(dnsServers: List<String>): String =
        dnsServers.joinToString(separator = "\n", postfix = "\n") {
            "nameserver $it"
        }

    fun hosts(hostname: String): String =
        "127.0.0.1   localhost.localdomain localhost\n" +
            "127.0.1.1   $hostname\n" +
            "\n" +
            "# IPv6.\n" +
            "::1         localhost.localdomain localhost ip6-localhost ip6-loopback\n" +
            "fe00::0     ip6-localnet\n" +
            "ff00::0     ip6-mcastprefix\n" +
            "ff02::1     ip6-allnodes\n" +
            "ff02::2     ip6-allrouters\n" +
            "ff02::3     ip6-allhosts\n"

    fun androidGroupAdditions(gid: Long): List<String> = listOf(
        "inet:x:3003",
        "everybody:x:9997",
        "android_app:x:20455",
        "android_debug:x:50455",
        "android_cache:x:${10000L + gid}",
        "android_storage:x:${40000L + gid}",
        "android_media:x:${50000L + gid}",
        "android_external_storage:x:1077",
    )

    /**
     * Appends [additions] whose GID is not already present in [existing].
     *
     * Existing lines are preserved verbatim and the result always ends with a
     * newline when anything is written.
     */
    fun mergeGroups(existing: String, additions: List<String>): String {
        val knownGids = existing.lineSequence()
            .mapNotNull { line ->
                val separator = line.lastIndexOf(':')
                if (separator >= 0) line.substring(separator + 1).trim() else null
            }
            .toMutableSet()

        val result = StringBuilder(existing)
        if (result.isNotEmpty() && result.last() != '\n') {
            result.append('\n')
        }
        for (addition in additions) {
            val gid = addition.substringAfterLast(':')
            if (knownGids.add(gid)) {
                result.append(addition).append('\n')
            }
        }
        return result.toString()
    }
}
