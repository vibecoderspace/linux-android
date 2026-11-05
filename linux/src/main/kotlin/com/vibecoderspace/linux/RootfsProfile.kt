package com.vibecoderspace.linux

import android.content.Context
import java.io.File

/** Everything a profile needs while configuring a freshly extracted image. */
data class RootfsContext(
    val appContext: Context,
    val layout: RootfsLayout,
    val profile: RootfsProfile,
)

/**
 * Describes one Linux distribution.
 *
 * Profiles hold the distro-specific parts of the runtime: the archive source,
 * the login shell, the guest PATH and locale, the init script, and any files
 * that must be written after extraction. PRoot argv construction and process
 * launching stay generic and never inspect the profile.
 */
interface RootfsProfile {

    /** Stable identifier, also used as the image directory name. */
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

    /** Arguments appended to [shell] for an interactive login shell. */
    fun shellArguments(layout: RootfsLayout): List<String>

    /** Contents of the init script written to `<image>/bin/init`. */
    fun initScript(): String

    /** Guest-level environment for processes started in this image. */
    fun environment(layout: RootfsLayout): Map<String, String>

    /** Writes distro-specific files after extraction. */
    fun configure(context: RootfsContext) {
        RootfsConfigurator.configure(context, initScript())
    }

    /** Returns true when [rootfsDir] contains the paths this profile needs. */
    fun validate(rootfsDir: File): Boolean =
        requiredPaths.all { File(rootfsDir, it).exists() }
}
