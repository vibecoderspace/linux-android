package com.vibecoderspace.linux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LinuxRuntimeOptionsTest {

    @Test
    fun `default hostname is neutral`() {
        assertEquals("localhost", LinuxRuntimeOptions().hostname)
        assertEquals("localhost", LinuxRuntimeOptions.DEFAULT_HOSTNAME)
    }

    @Test
    fun `custom hostname is kept`() {
        assertEquals("openstudio", LinuxRuntimeOptions("openstudio").hostname)
    }

    @Test
    fun `blank and whitespace hostnames are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            LinuxRuntimeOptions("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            LinuxRuntimeOptions("two words")
        }
    }

    @Test
    fun `default dns servers are used when none are given`() {
        assertEquals(
            listOf("1.1.1.1", "8.8.8.8"),
            LinuxRuntimeOptions().dnsServers,
        )
    }

    @Test
    fun `custom dns servers are kept`() {
        assertEquals(
            listOf("9.9.9.9"),
            LinuxRuntimeOptions(dnsServers = listOf("9.9.9.9")).dnsServers,
        )
    }

    @Test
    fun `empty or malformed dns servers are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            LinuxRuntimeOptions(dnsServers = emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            LinuxRuntimeOptions(dnsServers = listOf(" "))
        }
        assertThrows(IllegalArgumentException::class.java) {
            LinuxRuntimeOptions(dnsServers = listOf("two words"))
        }
    }
}
