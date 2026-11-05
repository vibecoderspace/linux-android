package com.vibecoderspace.linux.profile

import com.vibecoderspace.linux.RootfsLayout
import com.vibecoderspace.linux.RootfsProfile
import com.vibecoderspace.linux.RootfsSource

/**
 * Ubuntu 24.04 base image.
 *
 * The default archive is the same Xed-Editor base image used by OpenStudio.
 */
class UbuntuProfile(
    override val source: RootfsSource = RootfsSource.Remote(ROOTFS_URL),
) : RootfsProfile {

    override val id: String = "ubuntu-24.04"

    override val displayName: String = "Ubuntu 24.04"

    override val shell: String = "/bin/bash"

    override val guestPath: String =
        "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

    override val locale: String = "C.UTF-8"

    override val requiredPaths: List<String> = listOf("bin/sh", "usr/bin/env")

    override fun shellArguments(layout: RootfsLayout): List<String> =
        listOf("--rcfile", layout.initScript.absolutePath, "-i")

    override fun initScript(): String = InitScripts.bash()

    override fun environment(layout: RootfsLayout): Map<String, String> =
        rootfsEnvironment(layout, shell, guestPath, locale)

    companion object {
        const val ROOTFS_URL: String =
            "https://github.com/Xed-Editor/Karbon-PackagesX/releases/download/ubuntu/" +
                "ubuntu-base-24.04.3-base-arm64.tar.gz"
    }
}
