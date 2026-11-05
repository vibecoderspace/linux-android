package com.vibecoderspace.linux.process

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProcessSpecTest {

    @Test
    fun `defaults target home and merge stderr`() {
        val spec = ProcessSpec(listOf("uname", "-a"))

        assertEquals(listOf("uname", "-a"), spec.command)
        assertEquals("/home", spec.workingDirectory)
        assertEquals(true, spec.redirectErrorStream)
    }

    @Test
    fun `shell wraps the script with bash -lc`() {
        val spec = ProcessSpec.shell("apt-get update")

        assertEquals(listOf("/bin/bash", "-lc", "apt-get update"), spec.command)
    }

    @Test
    fun `shell can use a different interpreter`() {
        val spec = ProcessSpec.shell("echo hi", shell = "/bin/sh")

        assertEquals(listOf("/bin/sh", "-lc", "echo hi"), spec.command)
    }

    @Test
    fun `empty command is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProcessSpec(emptyList())
        }
    }

    @Test
    fun `guest working directory must be absolute`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProcessSpec(listOf("id"), workingDirectory = "home")
        }
    }

    @Test
    fun `invalid environment keys are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProcessSpec(listOf("id"), environment = mapOf("A=B" to "1"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProcessSpec(listOf("id"), environment = mapOf("" to "1"))
        }
    }
}
