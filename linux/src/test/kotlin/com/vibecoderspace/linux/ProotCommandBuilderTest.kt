package com.vibecoderspace.linux

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotCommandBuilderTest {

    @Test
    fun `guest command is appended after proot options`() {
        val fixture = Fixture()
        val image = fixture.image()

        val command = ProotCommandBuilder.build(
            fixture.binaries,
            image,
            LinuxProcessSpec(command = listOf("uname", "-a")),
        )

        assertEquals(fixture.binaries.proot.absolutePath, command.first())
        assertTrue(command.containsAll(listOf("-r", image.rootfsDir.absolutePath)))
        assertTrue(command.containsAll(listOf("-w", "/home")))
        assertEquals(listOf("uname", "-a"), command.takeLast(2))
    }

    @Test
    fun `home and private directories are bound into the guest`() {
        val fixture = Fixture()
        val image = fixture.image()

        val command = ProotCommandBuilder.build(
            fixture.binaries,
            image,
            LinuxProcessSpec(command = listOf("id")),
        )

        assertTrue(
            command.contains("${image.layout.homeDir.absolutePath}:/home"),
        )
        assertTrue(
            command.contains("${image.layout.privateDir.absolutePath}"),
        )
    }

    @Test
    fun `extra binds are added when the host path exists`() {
        val fixture = Fixture()
        val image = fixture.image()
        val shared = File(fixture.root, "shared").apply { mkdirs() }

        val command = ProotCommandBuilder.build(
            fixture.binaries,
            image,
            LinuxProcessSpec(
                command = listOf("id"),
                extraBinds = listOf(Bind(shared.absolutePath, "/shared")),
            ),
        )

        assertTrue(command.contains("${shared.absolutePath}:/shared"))
    }

    private class Fixture {
        val root: File = Files.createTempDirectory("linux-android-test").toFile()
        val localDir = File(root, "local").apply { mkdirs() }
        val privateDir = root
        val layout = RootfsLayout(localDir).apply {
            sandboxDir.mkdirs()
            homeDir.mkdirs()
            binDir.mkdirs()
            statFile.createNewFile()
            vmstatFile.createNewFile()
        }

        val binaries = ProotBinaries.from(
            File(root, "libproot.so").apply { writeText("proot") },
            File(root, "libloader.so").apply { writeText("loader") },
        )

        fun image(): RootfsImage = RootfsImage(DefaultProfiles.ubuntu, layout, 0L)
    }
}
