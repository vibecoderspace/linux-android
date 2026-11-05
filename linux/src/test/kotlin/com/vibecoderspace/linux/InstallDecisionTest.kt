package com.vibecoderspace.linux

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class InstallDecisionTest {

    @Test
    fun `missing image reinstalls`() {
        val fixture = Fixture()
        assertEquals(
            InstallDecision.REINSTALL,
            decideInstall(fixture.layout, fixture.profile, null),
        )
    }

    @Test
    fun `matching image is reused`() {
        val fixture = Fixture(installed = true)
        assertEquals(
            InstallDecision.USE_EXISTING,
            decideInstall(fixture.layout, fixture.profile, fixture.metadata()),
        )
    }

    @Test
    fun `configuration change only reconfigures`() {
        val fixture = Fixture(installed = true)
        assertEquals(
            InstallDecision.RECONFIGURE,
            decideInstall(
                fixture.layout,
                fixture.profile.copy(configVersion = 2),
                fixture.metadata(),
            ),
        )
    }

    @Test
    fun `different profile reinstalls`() {
        val fixture = Fixture(installed = true)
        val other = fixture.profile.copy(id = "other-distro")
        assertEquals(
            InstallDecision.REINSTALL,
            decideInstall(fixture.layout, other, fixture.metadata()),
        )
    }

    @Test
    fun `different source reinstalls`() {
        val fixture = Fixture(installed = true)
        val other = fixture.profile.copy(
            source = RootfsSource.Remote("https://example.test/other.tar.gz", "abc123"),
        )
        assertEquals(
            InstallDecision.REINSTALL,
            decideInstall(fixture.layout, other, fixture.metadata()),
        )
    }

    @Test
    fun `different checksum reinstalls`() {
        val fixture = Fixture(installed = true)
        val other = fixture.profile.copy(
            source = RootfsSource.Remote("https://example.test/rootfs.tar.gz", "def456"),
        )
        assertEquals(
            InstallDecision.REINSTALL,
            decideInstall(fixture.layout, other, fixture.metadata()),
        )
    }

    @Test
    fun `old schema reinstalls`() {
        val fixture = Fixture(installed = true)
        assertEquals(
            InstallDecision.REINSTALL,
            decideInstall(
                fixture.layout,
                fixture.profile,
                fixture.metadata().copy(schema = RootfsMetadata.SCHEMA - 1),
            ),
        )
    }

    @Test
    fun `invalid sandbox reinstalls`() {
        val fixture = Fixture(installed = true, requiredPath = "bin/bash")
        assertEquals(
            InstallDecision.REINSTALL,
            decideInstall(fixture.layout, fixture.profile, fixture.metadata()),
        )
    }

    @Test
    fun `force always reinstalls`() {
        val fixture = Fixture(installed = true)
        assertEquals(
            InstallDecision.REINSTALL,
            decideInstall(fixture.layout, fixture.profile, fixture.metadata(), force = true),
        )
    }

    private class Fixture(
        installed: Boolean = false,
        requiredPath: String? = null,
    ) {
        val layout = RootfsLayout(
            Files.createTempDirectory("linux-android-decision").toFile(),
        )
        val profile = TestProfile(
            requiredPaths = listOfNotNull(requiredPath),
        )

        init {
            if (installed) {
                layout.sandboxDir.mkdirs()
                layout.markerFile.writeText("")
            }
        }

        fun metadata(): RootfsMetadata = RootfsMetadata(
            schema = RootfsMetadata.SCHEMA,
            id = profile.id,
            displayName = profile.displayName,
            source = profile.source.label,
            sha256 = profile.source.sha256,
            installedAt = 42L,
            configVersion = profile.configVersion,
            libraryVersion = RootfsMetadata.LIBRARY_VERSION,
        )
    }

    private data class TestProfile(
        override val id: String = "ubuntu-24.04",
        override val displayName: String = "Ubuntu 24.04",
        override val source: RootfsSource = RootfsSource.Remote(
            "https://example.test/rootfs.tar.gz",
            "abc123",
        ),
        override val requiredPaths: List<String> = emptyList(),
        override val configVersion: Int = 1,
    ) : RootfsProfile
}
