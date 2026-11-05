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
 * Profiles hold only the parts needed to provision a root filesystem: the
 * archive source, the paths that must exist after extraction, and any distro
 * files that must be written. Launching processes is the host app's job.
 */
interface RootfsProfile {

    /** Stable identifier recorded in the install metadata. */
    val id: String

    val displayName: String

    val source: RootfsSource

    /** Paths that must exist before the image is considered usable. */
    val requiredPaths: List<String>

    /**
     * Version of the files written by [configure].
     *
     * Bump this when the files written after extraction change. An installed
     * image with a different value is reconfigured in place without downloading
     * the archive again.
     */
    val configVersion: Int get() = 1

    /** Writes distro-specific files after extraction. */
    fun configure(context: RootfsContext) {
        RootfsConfigurator.configure(context)
    }

    /** Returns true when [rootfsDir] contains the paths this profile needs. */
    fun validate(rootfsDir: File): Boolean =
        requiredPaths.all { File(rootfsDir, it).exists() }
}
