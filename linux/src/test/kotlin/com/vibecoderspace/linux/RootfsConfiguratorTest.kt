package com.vibecoderspace.linux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootfsConfiguratorTest {

    @Test
    fun `hostname is localhost`() {
        assertEquals("localhost\n", RootfsConfigurator.hostname())
    }

    @Test
    fun `resolv conf lists every server`() {
        assertEquals(
            "nameserver 1.1.1.1\nnameserver 9.9.9.9\n",
            RootfsConfigurator.resolvConf(listOf("1.1.1.1", "9.9.9.9")),
        )
    }

    @Test
    fun `hosts maps localhost once`() {
        val hosts = RootfsConfigurator.hosts()

        assertTrue(hosts.contains("127.0.0.1   localhost\n"))
        assertTrue(hosts.contains("::1         localhost"))
        assertFalse(hosts.contains("localhost.localdomain"))
        assertFalse(hosts.contains("127.0.1.1"))
        assertTrue(hosts.endsWith("\n"))
    }

    @Test
    fun `android groups derive ids from the app gid`() {
        val groups = RootfsConfigurator.androidGroupAdditions(gid = 7)

        assertTrue(groups.contains("android_cache:x:10007"))
        assertTrue(groups.contains("android_storage:x:40007"))
        assertTrue(groups.contains("android_media:x:50007"))
        assertTrue(groups.contains("inet:x:3003"))
        assertTrue(groups.contains("everybody:x:9997"))
    }

    @Test
    fun `merge groups appends only missing gids`() {
        val existing = "root:x:0:\nandroid_cache:x:10007\n"
        val additions = listOf(
            "android_cache:x:10007",
            "android_storage:x:40007",
        )

        val merged = RootfsConfigurator.mergeGroups(existing, additions)

        assertEquals(1, merged.split("\n").count { it == "android_cache:x:10007" })
        assertTrue(merged.contains("android_storage:x:40007"))
        assertTrue(merged.startsWith("root:x:0:\n"))
    }

    @Test
    fun `merge groups adds a separator when the existing file has none`() {
        val merged = RootfsConfigurator.mergeGroups("root:x:0:", listOf("inet:x:3003"))

        assertEquals("root:x:0:\ninet:x:3003\n", merged)
    }

    @Test
    fun `merge groups leaves an already complete file unchanged`() {
        val existing = "root:x:0:\ninet:x:3003\n"
        val merged = RootfsConfigurator.mergeGroups(existing, listOf("inet:x:3003"))

        assertEquals(existing, merged)
        assertFalse(merged.contains("inet:x:3003\ninet:x:3003"))
    }
}
