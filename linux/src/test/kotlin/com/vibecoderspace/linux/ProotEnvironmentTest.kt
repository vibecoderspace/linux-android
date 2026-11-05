package com.vibecoderspace.linux

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotEnvironmentTest {

    @Test
    fun `profile and runtime values are merged`() {
        val root = Files.createTempDirectory("linux-android-env").toFile()
        val localDir = File(root, "local").apply { mkdirs() }
        val layout = RootfsLayout(localDir).apply {
            sandboxDir.mkdirs()
            homeDir.mkdirs()
            binDir.mkdirs()
        }
        val binaries = ProotBinaries.from(
            File(root, "libproot.so").apply { writeText("proot") },
            File(root, "libloader.so").apply { writeText("loader") },
        )
        val image = RootfsImage(DefaultProfiles.ubuntu, layout, 0L)

        val env = ProotEnvironment.build(
            image = image,
            binaries = binaries,
            prootTmpDir = File(root, "tmp").apply { mkdirs() },
            workingDirectory = "/home",
            nativeLibDir = "/native",
            extra = mapOf("CUSTOM" to "yes"),
        )

        assertEquals("/home", env["HOME"])
        assertEquals("/bin/bash", env["SHELL"])
        assertEquals(binaries.loader.absolutePath, env["PROOT_LOADER"])
        assertEquals(binaries.proot.absolutePath, env["PROOT"])
        assertEquals("/home", env["WKDIR"])
        assertEquals("yes", env["CUSTOM"])
        assertTrue(env.getValue("PATH").contains(layout.binDir.absolutePath))
    }
}
