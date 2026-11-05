package com.vibecoderspace.linux

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootfsLayoutTest {

    @Test
    fun `each profile gets its own image directory`() {
        val localDir = Files.createTempDirectory("linux-android-layout").toFile()
        val ubuntu = RootfsLayout(localDir, "ubuntu-24.04")
        val debian = RootfsLayout(localDir, "debian-12")

        assertEquals(File(localDir, "rootfs/ubuntu-24.04"), ubuntu.imageDir)
        assertEquals(File(localDir, "rootfs/debian-12"), debian.imageDir)
        assertEquals(File(localDir, "rootfs/ubuntu-24.04/sandbox"), ubuntu.sandboxDir)
        assertEquals(File(localDir, "rootfs/ubuntu-24.04/home"), ubuntu.homeDir)
        assertEquals(localDir.parentFile, ubuntu.privateDir)
    }

    @Test
    fun `installed requires both the marker and the sandbox`() {
        val localDir = Files.createTempDirectory("linux-android-installed").toFile()
        val layout = RootfsLayout(localDir, "ubuntu-24.04")

        assertFalse(layout.isInstalled())

        layout.sandboxDir.mkdirs()
        layout.markerFile.writeText("")

        assertTrue(layout.isInstalled())
    }
}
