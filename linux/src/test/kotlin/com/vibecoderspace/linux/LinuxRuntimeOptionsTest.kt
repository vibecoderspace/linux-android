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
}
