package com.vibecoderspace.linux

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Properties

/**
 * Downloads, extracts, and configures root filesystem images.
 *
 * Every image lives under its own directory, so Ubuntu, Debian, and future
 * distributions can coexist. A legacy single-image install from earlier
 * OpenStudio builds is adopted in place for the Ubuntu profile.
 */
class RootfsInstaller(
    context: Context,
    private val binaries: ProotBinaries? = null,
) {

    private val appContext: Context = context.applicationContext

    fun installedImages(profiles: List<RootfsProfile>): List<RootfsImage> {
        val imagesDir = File(RootfsLayout.localDir(appContext), "rootfs")
        val dirs = imagesDir.listFiles() ?: return emptyList()
        return dirs.asSequence()
            .filter { it.isDirectory }
            .mapNotNull { dir ->
                val profile = profiles.firstOrNull { it.id == dir.name } ?: return@mapNotNull null
                val layout = RootfsLayout(RootfsLayout.localDir(appContext), profile.id)
                if (!layout.isInstalled() || !profile.validate(layout.sandboxDir)) {
                    return@mapNotNull null
                }
                RootfsImage(profile, layout, readInstalledAt(layout))
            }
            .sortedBy { it.profile.displayName }
            .toList()
    }

    fun install(
        profile: RootfsProfile,
        onProgress: (InstallProgress) -> Unit = {},
    ): RootfsImage {
        val layout = RootfsLayout(RootfsLayout.localDir(appContext), profile.id)

        if (layout.isInstalled() && profile.validate(layout.sandboxDir)) {
            return RootfsImage(profile, layout, readInstalledAt(layout))
        }

        migrateLegacy(profile, layout)
        if (layout.isInstalled() && profile.validate(layout.sandboxDir)) {
            return RootfsImage(profile, layout, readInstalledAt(layout))
        }

        prepare(layout)

        val archive = obtainArchive(profile, onProgress)
        val cleanupArchive = profile.source !is RootfsSource.Imported
        try {
            onProgress(InstallProgress(InstallProgress.Stage.EXTRACTING, -1f))
            if (layout.sandboxDir.exists()) {
                deleteRecursively(layout.sandboxDir)
            }
            RootfsConfigurator.ensureDir(layout.sandboxDir)
            RootfsConfigurator.ensureDir(File(layout.sandboxDir, "tmp"))
            RootfsConfigurator.chmod(File(layout.sandboxDir, "tmp"), 1023)

            if (!extract(archive, layout)) {
                throw IOException("Could not extract ${profile.displayName}")
            }
            if (!profile.validate(layout.sandboxDir)) {
                throw IOException(
                    "${profile.displayName} is missing ${profile.requiredPaths.joinToString()}",
                )
            }

            onProgress(InstallProgress(InstallProgress.Stage.CONFIGURING, -1f))
            profile.configure(RootfsContext(appContext, layout, profile))
            writeMetadata(layout, profile, System.currentTimeMillis())
            RootfsConfigurator.createEmptyFile(layout.markerFile)
            onProgress(InstallProgress(InstallProgress.Stage.CONFIGURING, 1f))
            Log.i(TAG, "${profile.displayName} installed at ${layout.sandboxDir}")
            return RootfsImage(profile, layout, System.currentTimeMillis())
        } finally {
            if (cleanupArchive) {
                archive.delete()
            }
        }
    }

    // ------------------------------------------------------------------ layout

    private fun prepare(layout: RootfsLayout) {
        RootfsConfigurator.ensureDir(layout.imageDir)
        RootfsConfigurator.ensureDir(layout.homeDir)
        RootfsConfigurator.ensureDir(layout.binDir)
        RootfsConfigurator.createEmptyFile(layout.statFile)
        RootfsConfigurator.createEmptyFile(layout.vmstatFile)
    }

    private fun migrateLegacy(profile: RootfsProfile, layout: RootfsLayout) {
        if (profile.id != LEGACY_UBUNTU_ID) return
        if (layout.markerFile.exists()) return
        if (!layout.legacyMarkerFile.isFile || !layout.legacySandboxDir.isDirectory) return

        RootfsConfigurator.ensureDir(layout.imageDir)
        if (!layout.legacySandboxDir.renameTo(layout.sandboxDir)) {
            Log.w(TAG, "Could not adopt the legacy rootfs; a fresh image will be installed")
            return
        }
        layout.legacyHomeDir.renameTo(layout.homeDir)
        layout.legacyBinDir.renameTo(layout.binDir)

        writeMetadata(layout, profile, layout.markerFile.lastModified())
        RootfsConfigurator.createEmptyFile(layout.markerFile)
        Log.i(TAG, "Adopted the legacy Ubuntu rootfs at ${layout.sandboxDir}")
    }

    // ------------------------------------------------------------------ archive

    private fun obtainArchive(
        profile: RootfsProfile,
        onProgress: (InstallProgress) -> Unit,
    ): File {
        val cached = File(File(appContext.cacheDir, CACHE_DIR), "${profile.id}.tar.gz")

        return when (val source = profile.source) {
            is RootfsSource.Remote -> {
                if (cached.isFile && cached.length() > MIN_ARCHIVE_BYTES) {
                    verify(cached, source.sha256)
                    cached
                } else {
                    download(source, cached, onProgress)
                    cached
                }
            }

            is RootfsSource.Bundled -> {
                if (cached.isFile && cached.length() > MIN_ARCHIVE_BYTES) {
                    verify(cached, null)
                } else {
                    copyAsset(source.assetPath, cached)
                }
                cached
            }

            is RootfsSource.Imported -> {
                if (!source.file.isFile) {
                    throw IOException("Rootfs archive not found: ${source.file}")
                }
                source.file
            }
        }
    }

    private fun download(
        source: RootfsSource.Remote,
        target: File,
        onProgress: (InstallProgress) -> Unit,
    ) {
        target.parentFile?.let { RootfsConfigurator.ensureDir(it) }
        val part = File(target.absolutePath + ".part")
        part.delete()

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(source.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "linux-android")
                setRequestProperty("Accept", "application/octet-stream")
            }

            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                throw IOException("Server returned HTTP $status for ${source.url}")
            }

            val total = connection.contentLengthLong
            var copied = 0L
            var lastReport = 0L
            onProgress(
                InstallProgress(
                    InstallProgress.Stage.DOWNLOADING,
                    if (total > 0) 0f else -1f,
                ),
            )

            connection.inputStream.use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (copied - lastReport >= 512 * 1024) {
                            lastReport = copied
                            onProgress(
                                InstallProgress(
                                    InstallProgress.Stage.DOWNLOADING,
                                    if (total > 0) copied.toFloat() / total else -1f,
                                ),
                            )
                        }
                    }
                    output.flush()
                }
            }

            if (total > 0 && copied != total) {
                throw IOException("Incomplete download ($copied of $total bytes)")
            }
            if (target.exists() && !target.delete()) {
                throw IOException("Could not replace $target")
            }
            if (!part.renameTo(target)) {
                throw IOException("Could not finalize $target")
            }
            verify(target, source.sha256)
            onProgress(InstallProgress(InstallProgress.Stage.DOWNLOADING, 1f))
            Log.i(TAG, "Downloaded $copied bytes to $target")
        } finally {
            connection?.disconnect()
            part.delete()
        }
    }

    private fun copyAsset(assetPath: String, target: File) {
        target.parentFile?.let { RootfsConfigurator.ensureDir(it) }
        val part = File(target.absolutePath + ".part")
        part.delete()
        try {
            appContext.assets.open(assetPath).use { input ->
                FileOutputStream(part).use { output -> input.copyTo(output) }
            }
            if (target.exists() && !target.delete()) {
                throw IOException("Could not replace $target")
            }
            if (!part.renameTo(target)) {
                throw IOException("Could not finalize $target")
            }
        } finally {
            part.delete()
        }
    }

    private fun verify(file: File, expectedSha256: String?) {
        if (expectedSha256.isNullOrBlank()) return
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(expectedSha256, ignoreCase = true)) {
            file.delete()
            throw IOException("SHA-256 mismatch for ${file.name}")
        }
    }

    // ---------------------------------------------------------------- extraction

    private fun extract(archive: File, layout: RootfsLayout): Boolean {
        val proot = binaries
        if (proot != null && extractWithProot(proot, archive, layout)) {
            return true
        }
        Log.w(TAG, "Falling back to the system tar")
        return extractWithTar(archive, layout)
    }

    private fun extractWithProot(
        proot: ProotBinaries,
        archive: File,
        layout: RootfsLayout,
    ): Boolean {
        val tmp = RootfsConfigurator.ensureDir(File(appContext.cacheDir, "proot-extract"))

        val args = ArrayList<String>()
        args.add(proot.proot.absolutePath)
        args.add("--kill-on-exit")
        args.add("-0")
        args.add("--link2symlink")
        args.add("--sysvipc")
        args.add("-L")
        args.add("-r")
        args.add("/")
        args.add("-w")
        args.add("/")

        for (path in listOf(
            "/dev", "/proc", "/sys", "/system", "/system_ext", "/product", "/odm",
            "/apex", "/vendor", "/data", "/storage", "/sdcard",
        )) {
            addBind(args, path, null)
        }
        addBind(args, "/linkerconfig/ld.config.txt", null)
        addBind(args, "/linkerconfig/com.android.art/ld.config.txt", null)
        addBind(args, "/plat_property_contexts", "/property_contexts")
        addBind(args, "/dev/urandom", "/dev/random")
        addBind(args, layout.privateDir.absolutePath, null)

        args.add("/system/bin/sh")
        args.add("-c")
        args.add(
            "cd " + shellQuote(layout.sandboxDir.absolutePath) +
                " && /system/bin/tar -xf " + shellQuote(archive.absolutePath),
        )

        return runProcess(args, tmp, proot.loader.absolutePath, "PRoot extraction")
    }

    private fun extractWithTar(archive: File, layout: RootfsLayout): Boolean {
        if (!File("/system/bin/tar").isFile) {
            Log.e(TAG, "No /system/bin/tar available")
            return false
        }
        val args = listOf(
            "/system/bin/sh",
            "-c",
            "cd " + shellQuote(layout.sandboxDir.absolutePath) +
                " && /system/bin/tar -xf " + shellQuote(archive.absolutePath),
        )
        return runProcess(args, File(appContext.cacheDir, "tar-extract"), null, "tar extraction")
    }

    private fun runProcess(
        command: List<String>,
        workingDir: File,
        prootLoader: String?,
        label: String,
    ): Boolean {
        RootfsConfigurator.ensureDir(workingDir)
        return try {
            val builder = ProcessBuilder(command)
                .directory(workingDir)
                .redirectErrorStream(true)
            builder.environment().apply {
                put("TMPDIR", workingDir.absolutePath)
                put("PROOT_TMP_DIR", workingDir.absolutePath)
                if (prootLoader != null) put("PROOT_LOADER", prootLoader)
            }

            val process = builder.start()
            val output = readStream(process.inputStream)
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                Log.e(TAG, "$label failed with exit code $exitCode: $output")
                false
            } else {
                Log.i(TAG, "$label finished successfully")
                true
            }
        } catch (error: Exception) {
            Log.e(TAG, "$label failed", error)
            false
        }
    }

    // ------------------------------------------------------------------ metadata

    private fun writeMetadata(layout: RootfsLayout, profile: RootfsProfile, installedAt: Long) {
        val properties = Properties()
        properties["id"] = profile.id
        properties["displayName"] = profile.displayName
        properties["source"] = profile.source.label
        properties["installedAt"] = installedAt.toString()
        layout.metadataFile.outputStream().use { properties.store(it, "linux-android rootfs") }
    }

    private fun readInstalledAt(layout: RootfsLayout): Long {
        val properties = Properties()
        runCatching {
            layout.metadataFile.inputStream().use { properties.load(it) }
        }
        return properties.getProperty("installedAt")?.toLongOrNull()
            ?: layout.markerFile.lastModified()
    }

    // --------------------------------------------------------------------- utils

    private fun readStream(input: InputStream): String {
        val builder = StringBuilder()
        input.use { stream ->
            val buffer = ByteArray(4096)
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                if (builder.length < 16 * 1024) {
                    builder.append(String(buffer, 0, read, StandardCharsets.UTF_8))
                }
            }
        }
        return builder.toString().trim()
    }

    private fun deleteRecursively(file: File) {
        if (runCatching { java.nio.file.Files.isSymbolicLink(file.toPath()) }.getOrDefault(false)) {
            file.delete()
            return
        }
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        file.delete()
    }

    private fun addBind(args: MutableList<String>, host: String, guest: String?) {
        if (!File(host).exists()) return
        args.add("-b")
        args.add(if (guest == null) host else "$host:$guest")
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private companion object {
        const val TAG = "RootfsInstaller"
        const val CACHE_DIR = "linux-android"
        const val MIN_ARCHIVE_BYTES = 1L * 1024L * 1024L
        const val LEGACY_UBUNTU_ID = "ubuntu-24.04"
    }
}
