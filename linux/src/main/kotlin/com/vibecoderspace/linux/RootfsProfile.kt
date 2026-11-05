package com.vibecoderspace.linux

import android.content.Context
import java.io.File

/** Everything a profile needs while configuring a freshly extracted image. */
data class RootfsContext(
    val appContext: Context,
    val layout: RootfsLayout,
    val profile: RootfsProfile,
    val hostname: String = LinuxRuntimeOptions.DEFAULT_HOSTNAME,
    val dnsServers: List<String> = LinuxRuntimeOptions.DEFAULT_DNS_SERVERS,
)

/**
 * Describes one Linux distribution.
 *
 * Profiles hold the distro-specific parts of provisioning: the archive source,
 * the login shell, the guest PATH and locale, the init script, and any files
 * that must be written after extraction. The library only writes these into the
 * image; the host app reads them when it builds its PRoot argv.
 */
interface RootfsProfile {

    /** Stable identifier recorded in the install metadata. */
    val id: String

    val displayName: String

    val source: RootfsSource

    /** Absolute guest path of the login shell, for example `/bin/bash`. */
    val shell: String

    /** Default guest PATH for this distribution. */
    val guestPath: String

    /** Locale exported to the guest, for example `C.UTF-8`. */
    val locale: String

    /** Paths that must exist before the image is considered usable. */
    val requiredPaths: List<String>

    /**
     * Version of the files written by [configure].
     *
     * Bump this when hostname, DNS, init-script, or group handling changes. An
     * installed image with a different value is reconfigured in place without
     * downloading the archive again.
     */
    val configVersion: Int get() = 1

    /** Arguments the host app can append to [shell] for an interactive login shell. */
    fun shellArguments(layout: RootfsLayout): List<String>

    /** Contents of the init script written to `<image>/bin/init`. */
    fun initScript(): String

    /** Guest-level environment the host app can use for this image. */
    fun environment(layout: RootfsLayout): Map<String, String>

    /** Writes distro-specific files after extraction. */
    fun configure(context: RootfsContext) {
        RootfsConfigurator.configure(context, initScript())
    }

    /** Returns true when [rootfsDir] contains the paths this profile needs. */
    fun validate(rootfsDir: File): Boolean =
        requiredPaths.all { File(rootfsDir, it).exists() }
}
