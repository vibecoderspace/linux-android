package com.vibecoderspace.linux.profile

import com.vibecoderspace.linux.RootfsProfile
import com.vibecoderspace.linux.RootfsSource

/**
 * Ubuntu 24.04 base image.
 *
 * The archive is Canonical's official Ubuntu Base root filesystem, so the
 * library does not depend on a third-party repackaging.
 */
class UbuntuProfile(
    override val source: RootfsSource = RootfsSource.Remote(ROOTFS_URL, ROOTFS_SHA256),
) : RootfsProfile {

    override val id: String = "ubuntu-24.04"

    override val displayName: String = "Ubuntu 24.04"

    override val requiredPaths: List<String> = listOf("bin/sh", "bin/bash", "usr/bin/env")

    companion object {
        const val ROOTFS_URL: String =
            "https://cdimage.ubuntu.com/ubuntu-base/releases/noble/release/" +
                "ubuntu-base-24.04.4-base-arm64.tar.gz"

        /** From the release's `SHA256SUMS` file. */
        const val ROOTFS_SHA256: String =
            "04207713ece899c3740823d33690441ad3a7f0ded1101aca744e2b0f37ac7ff2"
    }
}
