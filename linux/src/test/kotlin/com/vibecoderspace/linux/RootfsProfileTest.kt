package com.vibecoderspace.linux

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RootfsProfileTest {

    @Test
    fun `ubuntu validates an image with sh, bash and env`() {
        val rootfs = Files.createTempDirectory("linux-android-rootfs").toFile()
        File(rootfs, "bin").mkdirs()
        File(rootfs, "usr/bin").mkdirs()
        File(rootfs, "bin/sh").writeText("")
        File(rootfs, "bin/bash").writeText("")
        File(rootfs, "usr/bin/env").writeText("")

        assertTrue(DefaultProfiles.ubuntu.validate(rootfs))
        assertTrue(DefaultProfiles.debian.validate(rootfs))
    }

    @Test
    fun `debian requires bash`() {
        val rootfs = Files.createTempDirectory("linux-android-debian").toFile()
        File(rootfs, "bin").mkdirs()
        File(rootfs, "bin/sh").writeText("")
        File(rootfs, "bin/bash").writeText("")

        assertTrue(DefaultProfiles.debian.validate(rootfs))
    }

    @Test
    fun `shell arguments use the profile init script`() {
        val localDir = Files.createTempDirectory("linux-android-layout").toFile()
        val layout = RootfsLayout(localDir, DefaultProfiles.ubuntu.id)

        val arguments = DefaultProfiles.ubuntu.shellArguments(layout)

        assertEquals(listOf("--rcfile", layout.initScript.absolutePath, "-i"), arguments)
    }

    @Test
    fun `profiles have stable ids and display names`() {
        assertEquals("ubuntu-24.04", DefaultProfiles.ubuntu.id)
        assertEquals("debian-12", DefaultProfiles.debian.id)
        assertEquals("Ubuntu 24.04", DefaultProfiles.ubuntu.displayName)
        assertEquals("Debian 12", DefaultProfiles.debian.displayName)
    }

    @Test
    fun `ubuntu uses the official base archive with a pinned checksum`() {
        val source = DefaultProfiles.ubuntu.source
        assertTrue(source is RootfsSource.Remote)
        source as RootfsSource.Remote

        assertEquals(
            "https://cdimage.ubuntu.com/ubuntu-base/releases/noble/release/" +
                "ubuntu-base-24.04.4-base-arm64.tar.gz",
            source.url,
        )
        assertEquals(64, source.sha256?.length)
    }
}
