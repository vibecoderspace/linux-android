package com.vibecoderspace.linux

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RootfsMetadataTest {

    @Test
    fun `metadata round-trips`() {
        val file = File(
            Files.createTempDirectory("linux-android-meta").toFile(),
            "meta.properties",
        )
        val metadata = RootfsMetadata(
            schema = RootfsMetadata.SCHEMA,
            id = "ubuntu-24.04",
            displayName = "Ubuntu 24.04",
            source = "https://example.test/ubuntu.tar.gz",
            sha256 = "abc123",
            installedAt = 1_700_000_000_000L,
            configVersion = 3,
            libraryVersion = RootfsMetadata.LIBRARY_VERSION,
        )

        metadata.write(file)
        val read = RootfsMetadata.read(file)

        assertEquals(metadata, read)
    }

    @Test
    fun `missing metadata is null`() {
        val file = File(
            Files.createTempDirectory("linux-android-meta").toFile(),
            "meta.properties",
        )
        assertNull(RootfsMetadata.read(file))
    }

    @Test
    fun `metadata without a schema is treated as stale`() {
        val file = File(
            Files.createTempDirectory("linux-android-meta").toFile(),
            "meta.properties",
        )
        file.writeText(
            """
            id=ubuntu-24.04
            source=old
            installedAt=1
            """.trimIndent(),
        )

        val read = RootfsMetadata.read(file)

        assertEquals(0, read?.schema)
        assertEquals("ubuntu-24.04", read?.id)
        assertTrue(read!!.sha256 == null)
    }

    @Test
    fun `write replaces an existing file atomically`() {
        val file = File(
            Files.createTempDirectory("linux-android-meta").toFile(),
            "meta.properties",
        )
        RootfsMetadata(
            schema = 2, id = "a", displayName = "A", source = "s", sha256 = null,
            installedAt = 1, configVersion = 1, libraryVersion = "0.2.0",
        ).write(file)
        RootfsMetadata(
            schema = 2, id = "b", displayName = "B", source = "s", sha256 = null,
            installedAt = 2, configVersion = 1, libraryVersion = "0.2.0",
        ).write(file)

        assertEquals("b", RootfsMetadata.read(file)?.id)
    }
}
