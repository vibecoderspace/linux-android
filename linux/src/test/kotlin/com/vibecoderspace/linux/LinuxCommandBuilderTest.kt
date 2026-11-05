package com.vibecoderspace.linux

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxCommandBuilderTest {

    @Test
    fun `of keeps argv order`() {
        val spec = LinuxCommandBuilder.of("uname", "-a").build()

        assertEquals(listOf("uname", "-a"), spec.command)
        assertEquals("/home", spec.workingDirectory)
        assertTrue(spec.environment.isEmpty())
        assertNull(spec.logFile)
    }

    @Test
    fun `shell wraps the script with sh -c`() {
        val spec = LinuxCommandBuilder.shell("apt-get update").build()

        assertEquals(listOf("/bin/sh", "-c", "apt-get update"), spec.command)
    }

    @Test
    fun `shell can use a different interpreter`() {
        val spec = LinuxCommandBuilder
            .shell("echo hi", shell = "/bin/bash")
            .build()

        assertEquals(listOf("/bin/bash", "-c", "echo hi"), spec.command)
    }

    @Test
    fun `builders are immutable`() {
        val base = LinuxCommandBuilder.of("apt-get")
        val update = base.argument("update")
        val install = base.argument("install")

        assertEquals(listOf("apt-get"), base.build().command)
        assertEquals(listOf("apt-get", "update"), update.build().command)
        assertEquals(listOf("apt-get", "install"), install.build().command)
    }

    @Test
    fun `environment is merged and overridden`() {
        val spec = LinuxCommandBuilder
            .of("env")
            .environment("A", "1")
            .environment(mapOf("B" to "2", "A" to "3"))
            .build()

        assertEquals(mapOf("A" to "3", "B" to "2"), spec.environment)
    }

    @Test
    fun `working directory log and binds map to the spec`() {
        val log = File("/tmp/install.log")
        val spec = LinuxCommandBuilder
            .of("id")
            .workingDirectory("/opt")
            .logTo(log)
            .mergeErrorStream(false)
            .bind("/sdcard", "/shared")
            .build()

        assertEquals("/opt", spec.workingDirectory)
        assertEquals(log, spec.logFile)
        assertEquals(false, spec.redirectErrorStream)
        assertEquals(listOf(Bind("/sdcard", "/shared")), spec.extraBinds)
    }

    @Test
    fun `empty argv is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            LinuxCommandBuilder.of()
        }
    }

    @Test
    fun `guest working directory must be absolute`() {
        assertThrows(IllegalArgumentException::class.java) {
            LinuxCommandBuilder.of("id").workingDirectory("home")
        }
    }

    @Test
    fun `invalid environment keys are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            LinuxCommandBuilder.of("id").environment("A=B", "1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            LinuxCommandBuilder.of("id").environment("", "1")
        }
    }
}
