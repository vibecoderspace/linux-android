package com.vibecoderspace.linux

import java.io.File

/** An installed root filesystem that processes can be launched in. */
data class RootfsImage(
    val profile: RootfsProfile,
    val layout: RootfsLayout,
    val installedAt: Long,
) {
    val id: String get() = profile.id
    val rootfsDir: File get() = layout.sandboxDir
    val homeDir: File get() = layout.homeDir
}
