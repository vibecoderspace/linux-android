package com.vibecoderspace.linux

import android.system.Os
import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/** Writes the files shared by the supported Linux images. */
internal object RootfsConfigurator {

    private const val TAG = "RootfsConfigurator"

    fun configure(context: RootfsContext) {
        val sandbox = context.layout.sandboxDir
        val etc = ensureDir(File(sandbox, "etc"))

        writeFile(File(etc, "hostname"), RootfsFiles.hostname(context.hostname))
        writeFile(
            File(etc, "resolv.conf"),
            RootfsFiles.resolvConf(context.dnsServers),
        )
        writeFile(
            File(etc, "hosts"),
            RootfsFiles.hosts(context.hostname),
        )

        appendAndroidGroups(File(etc, "group"))
        ensureDir(File(sandbox, "usr/local/bin"))
        ensureDir(File(sandbox, "tmp"))
    }

    private fun appendAndroidGroups(groupFile: File) {
        val gid = runCatching { Os.getgid() }.getOrDefault(0).toLong()
        val additions = RootfsFiles.androidGroupAdditions(gid)

        val existing = if (groupFile.isFile) {
            runCatching { groupFile.readText(StandardCharsets.UTF_8) }.getOrDefault("")
        } else {
            ""
        }

        val merged = RootfsFiles.mergeGroups(existing, additions)
        runCatching { writeFile(groupFile, merged) }
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
