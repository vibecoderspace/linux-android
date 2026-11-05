package com.vibecoderspace.linux

import android.system.Os
import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.HashSet

/** Writes the files shared by Debian-style images. */
internal object RootfsConfigurator {

    private const val TAG = "RootfsConfigurator"

    fun configure(context: RootfsContext, initScript: String) {
        val sandbox = context.layout.sandboxDir
        val etc = ensureDir(File(sandbox, "etc"))

        writeFile(File(etc, "hostname"), context.hostname + "\n")
        writeFile(
            File(etc, "resolv.conf"),
            "nameserver 8.8.8.8\nnameserver 8.8.4.4\nnameserver 1.1.1.1\n",
        )
        writeFile(
            File(etc, "hosts"),
            "127.0.0.1   localhost.localdomain localhost\n" +
                "127.0.1.1   ${context.hostname}\n" +
                "\n" +
                "# IPv6.\n" +
                "::1         localhost.localdomain localhost ip6-localhost ip6-loopback\n" +
                "fe00::0     ip6-localnet\n" +
                "ff00::0     ip6-mcastprefix\n" +
                "ff02::1     ip6-allnodes\n" +
                "ff02::2     ip6-allrouters\n" +
                "ff02::3     ip6-allhosts\n",
        )

        appendAndroidGroups(File(etc, "group"))
        ensureDir(File(sandbox, "usr/local/bin"))
        ensureDir(File(sandbox, "tmp"))

        writeFile(context.layout.initScript, initScript)
        chmod(context.layout.initScript, 493)

        createEmptyFile(context.layout.statFile)
        createEmptyFile(context.layout.vmstatFile)
    }

    private fun appendAndroidGroups(groupFile: File) {
        val gid = runCatching { Os.getgid() }.getOrDefault(0).toLong()

        val additions = arrayOf(
            "inet:x:3003",
            "everybody:x:9997",
            "android_app:x:20455",
            "android_debug:x:50455",
            "android_cache:x:" + (10000L + gid),
            "android_storage:x:" + (40000L + gid),
            "android_media:x:" + (50000L + gid),
            "android_external_storage:x:1077",
        )

        val existing = if (groupFile.isFile) {
            runCatching { groupFile.readText(StandardCharsets.UTF_8) }.getOrDefault("")
        } else {
            ""
        }

        val knownGids = HashSet<String>()
        for (line in existing.split("\n")) {
            val separator = line.lastIndexOf(':')
            if (separator >= 0) {
                knownGids.add(line.substring(separator + 1).trim())
            }
        }

        val result = StringBuilder(existing)
        if (result.isNotEmpty() && result.last() != '\n') {
            result.append('\n')
        }
        for (addition in additions) {
            val value = addition.substring(addition.lastIndexOf(':') + 1)
            if (knownGids.add(value)) {
                result.append(addition).append('\n')
            }
        }
        runCatching { writeFile(groupFile, result.toString()) }
            .onFailure { Log.w(TAG, "Could not update ${groupFile.absolutePath}", it) }
    }

    internal fun ensureDir(dir: File): File {
        if (!dir.isDirectory && !dir.mkdirs()) {
            Log.w(TAG, "Failed to create ${dir.absolutePath}")
        }
        return dir
    }

    internal fun createEmptyFile(file: File): File {
        file.parentFile?.let { ensureDir(it) }
        if (!file.exists() && !file.createNewFile()) {
            throw IOException("Could not create ${file.absolutePath}")
        }
        return file
    }

    internal fun writeFile(file: File, content: String) {
        file.parentFile?.let { ensureDir(it) }
        // Delete first: some rootfs entries (for example /etc/resolv.conf) can be
        // symlinks, and following them would write outside the container.
        if (runCatching { Files.isSymbolicLink(file.toPath()) }.getOrDefault(false)) {
            file.delete()
        }
        if (file.exists() && !file.delete()) {
            throw IOException("Could not replace ${file.absolutePath}")
        }
        file.outputStream().use { out ->
            out.write(content.toByteArray(StandardCharsets.UTF_8))
        }
    }

    internal fun chmod(file: File, mode: Int) {
        runCatching { Os.chmod(file.absolutePath, mode) }
            .onFailure { Log.w(TAG, "chmod ${Integer.toOctalString(mode)} failed for $file", it) }
    }
}
