package com.vibecoderspace.linux.process

import com.vibecoderspace.linux.ProotBinaries
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class ProotEnvironmentTest {

    @Test
    fun `includes proot loader and tmp dir`() {
        val fixture = Fixture()
        val environment = ProotEnvironment.build(
            fixture.binaries,
            fixture.tmpDir,
            ProcessSpec(listOf("/bin/true")),
        )

        assertEquals(fixture.binaries.loader.absolutePath, environment["PROOT_LOADER"])
        assertEquals(fixture.tmpDir.absolutePath, environment["PROOT_TMP_DIR"])
        assertEquals("/home", environment["HOME"])
        assertEquals("/bin/bash", environment["SHELL"])
        assertEquals("root", environment["USER"])
    }

    @Test
    fun `spec environment overrides defaults`() {
        val fixture = Fixture()
        val environment = ProotEnvironment.build(
            fixture.binaries,
            fixture.tmpDir,
            ProcessSpec(
                command = listOf("/bin/true"),
                environment = mapOf("HOME" to "/root", "CUSTOM" to "1"),
            ),
        )

        assertEquals("/root", environment["HOME"])
        assertEquals("1", environment["CUSTOM"])
    }

    private class Fixture {
        private val root = Files.createTempDirectory("linux-android-env").toFile()
        val tmpDir: File = File(root, "proot").apply { mkdirs() }
        val binaries: ProotBinaries

        init {
            val proot = File(root, "libproot.so").apply { writeText("") }
            val loader = File(root, "libloader.so").apply { writeText("") }
            binaries = ProotBinaries.from(proot, loader)
        }
    }
}
