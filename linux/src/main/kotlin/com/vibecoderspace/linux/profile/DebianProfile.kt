package com.vibecoderspace.linux.profile

import com.vibecoderspace.linux.RootfsLayout
import com.vibecoderspace.linux.RootfsProfile
import com.vibecoderspace.linux.RootfsSource

/**
 * Debian 12 (bookworm) slim image.
 *
 * The archive is the arm64 slim rootfs published by the debuerreotype project.
 */
class DebianProfile(
    override val source: RootfsSource = RootfsSource.Remote(ROOTFS_URL),
) : RootfsProfile {

    override val id: String = "debian-12"

    override val displayName: String = "Debian 12"

    override val shell: String = "/bin/bash"

    override val guestPath: String =
        "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

    override val locale: String = "C.UTF-8"

    override val requiredPaths: List<String> = listOf("bin/sh", "bin/bash")

    override fun shellArguments(layout: RootfsLayout): List<String> =
        listOf("--rcfile", layout.initScript.absolutePath, "-i")

    override fun initScript(): String = InitScripts.bash()

    override fun environment(layout: RootfsLayout): Map<String, String> =
        rootfsEnvironment(layout, shell, guestPath, locale)

    companion object {
        const val ROOTFS_URL: String =
            "https://raw.githubusercontent.com/debuerreotype/docker-debian-artifacts/" +
                "dist-arm64v8/bookworm/slim/oci/blobs/rootfs.tar.gz"
    }
}
