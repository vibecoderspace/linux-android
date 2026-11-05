package com.vibecoderspace.linux

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootfsLayoutTest {

    @Test
    fun `single image lives directly under rootfs`() {
        val localDir = Files.createTempDirectory("linux-android-layout").toFile()
        val layout = RootfsLayout(localDir)

        assertEquals(File(localDir, "rootfs"), layout.rootfsDir)
        assertEquals(File(localDir, "rootfs/sandbox"), layout.sandboxDir)
        assertEquals(File(localDir, "rootfs/home"), layout.homeDir)
        assertEquals(File(localDir, "rootfs/meta.properties"), layout.metadataFile)
        assertEquals(File(localDir, "rootfs/.installed"), layout.markerFile)
        assertEquals(File(localDir, "rootfs/.install.lock"), layout.lockFile)
        assertEquals(File(localDir, "rootfs/.failed"), layout.failedFile)
        assertEquals(localDir.parentFile, layout.privateDir)
    }

    @Test
    fun `installed requires both the marker and the sandbox`() {
        val localDir = Files.createTempDirectory("linux-android-installed").toFile()
        val layout = RootfsLayout(localDir)

        assertFalse(layout.isInstalled())

        layout.sandboxDir.mkdirs()
        layout.markerFile.writeText("")

        assertTrue(layout.isInstalled())
    }
}
