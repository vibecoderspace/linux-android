package com.vibecoderspace.linux.process

import com.vibecoderspace.linux.DefaultProfiles
import com.vibecoderspace.linux.ProotBinaries
import com.vibecoderspace.linux.RootfsImage
import com.vibecoderspace.linux.RootfsLayout
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotCommandBuilderTest {

    @Test
    fun `builds a complete command`() {
        val fixture = Fixture()

        val args = ProotCommandBuilder.build(
            fixture.binaries,
            fixture.image,
            ProcessSpec(listOf("/bin/echo", "hello")),
        )

        assertEquals(fixture.binaries.proot.absolutePath, args.first())
        assertTrue(args.containsAll(listOf("-r", fixture.layout.sandboxDir.absolutePath)))
        assertTrue(args.containsAll(listOf("-w", "/home")))
        assertEquals(listOf("/bin/echo", "hello"), args.takeLast(2))
    }

    @Test
    fun `binds the rootfs home and private directory`() {
        val fixture = Fixture()

        val args = ProotCommandBuilder.build(
            fixture.binaries,
            fixture.image,
            ProcessSpec(listOf("/bin/true")),
        )

        val binds = args.zipWithNext()
            .filter { it.first == "-b" }
            .map { it.second }

        assertTrue(
            binds.contains("${fixture.layout.homeDir.absolutePath}:/home"),
        )
        assertTrue(
            binds.contains("${fixture.layout.homeDir.absolutePath}:/root"),
        )
        assertTrue(binds.contains(fixture.layout.privateDir.absolutePath))
    }

    @Test
    fun `guest command is last`() {
        val fixture = Fixture()

        val args = ProotCommandBuilder.build(
            fixture.binaries,
            fixture.image,
            ProcessSpec(listOf("/bin/bash", "-lc", "uname -a")),
        )

        assertEquals(
            listOf("/bin/bash", "-lc", "uname -a"),
            args.takeLast(3),
        )
    }

    private class Fixture {
        private val root = Files.createTempDirectory("linux-android-process").toFile()
        val layout = RootfsLayout(File(root, "local")).apply {
            sandboxDir.mkdirs()
            File(sandboxDir, "tmp").mkdirs()
            homeDir.mkdirs()
        }
        val binaries: ProotBinaries
        val image: RootfsImage

        init {
            val proot = File(root, "libproot.so").apply { writeText("") }
            val loader = File(root, "libloader.so").apply { writeText("") }
            binaries = ProotBinaries.from(proot, loader)
            image = RootfsImage(DefaultProfiles.ubuntu, layout, installedAt = 0L)
        }
    }
}
