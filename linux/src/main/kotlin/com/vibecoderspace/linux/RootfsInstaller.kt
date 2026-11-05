package com.vibecoderspace.linux

import android.content.Context
import android.os.StatFs
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.channels.FileLock
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Downloads, extracts, and configures the single installed root filesystem.
 *
 * There is one image at [RootfsLayout.rootfsDir]. A different profile or a
 * different archive source replaces it; there is no per-profile directory and
 * no legacy adoption path. User data in [RootfsLayout.homeDir] survives a
 * replacement unless the caller asks to delete it.
 */
class RootfsInstaller(
    context: Context,
    private val binaries: ProotBinaries? = null,
    private val options: LinuxRuntimeOptions = LinuxRuntimeOptions(),
) {

    private val appContext: Context = context.applicationContext
    private val layout = RootfsLayout(RootfsLayout.localDir(appContext))
    private val monitor = Any()

    /** The installed image, or null when nothing usable is present. */
    fun installedImage(profiles: List<RootfsProfile>): RootfsImage? {
        if (!layout.isInstalled()) return null
        val metadata = RootfsMetadata.read(layout.metadataFile) ?: return null
        if (metadata.schema != RootfsMetadata.SCHEMA) return null
        val profile = profiles.firstOrNull { it.id == metadata.id } ?: return null
        if (!profile.validate(layout.sandboxDir)) return null
        return RootfsImage(profile, layout, metadata.installedAt)
    }

    /**
     * Installs [profile], replacing the current image when the profile, source,
     * schema, or configuration changed.
     *
     * @param keepHome keep `home/` when replacing the image.
     * @param force    wipe and reinstall even when the current image is valid.
     */
    fun install(
        profile: RootfsProfile,
        keepHome: Boolean = true,
        force: Boolean = false,
        onProgress: (InstallProgress) -> Unit = {},
    ): RootfsImage = withInstallLock {
        onProgress(InstallProgress(InstallProgress.Stage.CHECKING))
        val metadata = RootfsMetadata.read(layout.metadataFile)

        when (decideInstall(layout, profile, metadata, force)) {
            InstallDecision.USE_EXISTING -> {
                val installed = checkNotNull(metadata)
                onProgress(InstallProgress(InstallProgress.Stage.READY, 1f))
                RootfsImage(profile, layout, installed.installedAt)
            }

            InstallDecision.RECONFIGURE -> {
                val installed = checkNotNull(metadata)
                onProgress(InstallProgress(InstallProgress.Stage.CONFIGURING))
                configure(profile)
                val updated = installed.copy(
                    schema = RootfsMetadata.SCHEMA,
                    configVersion = profile.configVersion,
                    libraryVersion = RootfsMetadata.LIBRARY_VERSION,
                )
                updated.write(layout.metadataFile)
                onProgress(InstallProgress(InstallProgress.Stage.READY, 1f))
                RootfsImage(profile, layout, updated.installedAt)
            }

            InstallDecision.REINSTALL -> reinstall(profile, keepHome, onProgress)
        }
    }

    /** Removes the image. Cached archives are left in the cache directory. */
    fun uninstall(deleteHome: Boolean = true) = withInstallLock {
        deleteRecursively(layout.sandboxDir)
        layout.markerFile.delete()
        layout.metadataFile.delete()
        layout.failedFile.delete()
        if (deleteHome) {
            deleteRecursively(layout.homeDir)
        }
        if (deleteHome) {
            layout.rootfsDir.delete()
        }
        Unit
    }

    // ---------------------------------------------------------------- install

    private fun reinstall(
        profile: RootfsProfile,
        keepHome: Boolean,
        onProgress: (InstallProgress) -> Unit,
    ): RootfsImage {
        prepare()
        checkSpace(estimateArchiveBytes(profile) + ESTIMATED_EXTRACTED_BYTES)

        val archive = obtainArchive(profile, onProgress)
        try {
            checkSpace(archive.length() + ESTIMATED_EXTRACTED_BYTES)

            // Commit point starts here: once the marker is gone the image is
            // no longer considered installed, even if extraction fails.
            layout.markerFile.delete()
            layout.metadataFile.delete()
            layout.failedFile.delete()
            if (!keepHome) {
                deleteRecursively(layout.homeDir)
            }

            prepareSandbox()
            if (profile.source is RootfsSource.Imported) {
                validateArchive(archive)
            }

            onProgress(InstallProgress(InstallProgress.Stage.EXTRACTING))
            if (!extract(archive)) {
                throw InstallException.Extraction(
                    "Could not extract ${profile.displayName}",
                )
            }
            if (!profile.validate(layout.sandboxDir)) {
                throw InstallException.Validation(
                    "${profile.displayName} is missing " +
                        profile.requiredPaths.joinToString(),
                )
            }

            onProgress(InstallProgress(InstallProgress.Stage.CONFIGURING))
            configure(profile)

            val installedAt = System.currentTimeMillis()
            RootfsMetadata(
                schema = RootfsMetadata.SCHEMA,
                id = profile.id,
                displayName = profile.displayName,
                source = profile.source.label,
                sha256 = profile.source.sha256,
                installedAt = installedAt,
                configVersion = profile.configVersion,
                libraryVersion = RootfsMetadata.LIBRARY_VERSION,
            ).write(layout.metadataFile)
            RootfsConfigurator.createEmptyFile(layout.markerFile)
            layout.failedFile.delete()

            onProgress(InstallProgress(InstallProgress.Stage.READY, 1f))
            Log.i(TAG, "${profile.displayName} installed at ${layout.sandboxDir}")
            return RootfsImage(profile, layout, installedAt)
        } catch (error: Throwable) {
            writeFailure(error)
            throw error
        } finally {
            if (profile.source !is RootfsSource.Imported) {
                archive.delete()
            }
        }
    }

    private fun configure(profile: RootfsProfile) {
        profile.configure(
            RootfsContext(
                appContext = appContext,
                layout = layout,
                profile = profile,
                hostname = options.hostname,
                dnsServers = options.dnsServers,
            ),
        )
    }

    private fun prepare() {
        RootfsConfigurator.ensureDir(layout.rootfsDir)
        RootfsConfigurator.ensureDir(layout.homeDir)
    }

    private fun prepareSandbox() {
        deleteRecursively(layout.sandboxDir)
        RootfsConfigurator.ensureDir(layout.sandboxDir)
        val tmp = RootfsConfigurator.ensureDir(File(layout.sandboxDir, "tmp"))
        RootfsConfigurator.chmod(tmp, 1023)
    }

    private fun writeFailure(error: Throwable) {
        runCatching {
            layout.failedFile.writeText(
                "${error::class.java.name}: ${error.message}\n",
            )
        }
    }

    // ------------------------------------------------------------------ space

    private fun estimateArchiveBytes(profile: RootfsProfile): Long =
        when (val source = profile.source) {
            is RootfsSource.Imported -> source.file.length()
            is RootfsSource.Bundled ->
                runCatching { appContext.assets.openFd(source.assetPath).length }
                    .getOrDefault(ESTIMATED_ARCHIVE_BYTES)
            is RootfsSource.Remote ->
                cacheFile(profile).takeIf { it.isFile }?.length()
                    ?: ESTIMATED_ARCHIVE_BYTES
        }

    private fun checkSpace(requiredBytes: Long) {
        val available = runCatching {
            StatFs(layout.localDir.absolutePath).availableBytes
        }.getOrDefault(Long.MAX_VALUE)
        if (available < requiredBytes) {
            throw InstallException.InsufficientSpace(requiredBytes, available)
        }
    }

    // ------------------------------------------------------------------ archive

    private fun cacheFile(profile: RootfsProfile): File {
        val suffix = profile.source.sha256?.take(12) ?: "archive"
        return File(
            File(appContext.cacheDir, CACHE_DIR),
            "${profile.id}-$suffix.tar.gz",
        )
    }

    private fun obtainArchive(
        profile: RootfsProfile,
        onProgress: (InstallProgress) -> Unit,
    ): File = when (val source = profile.source) {
        is RootfsSource.Remote -> {
            val cached = cacheFile(profile)
            if (cached.isFile && cached.length() > MIN_ARCHIVE_BYTES &&
                verify(cached, source.sha256)
            ) {
                cached
            } else {
                cached.delete()
                download(source, cached, onProgress)
                cached
            }
        }

        is RootfsSource.Bundled -> {
            val cached = cacheFile(profile)
            if (cached.isFile && cached.length() > MIN_ARCHIVE_BYTES) {
                cached
            } else {
                copyAsset(source.assetPath, cached)
                cached
            }
        }

        is RootfsSource.Imported -> {
            if (!source.file.isFile) {
                throw InstallException.Network(
                    "Rootfs archive not found: ${source.file}",
                )
            }
            source.file
        }
    }

    private fun download(
        source: RootfsSource.Remote,
        target: File,
        onProgress: (InstallProgress) -> Unit,
    ) {
        target.parentFile?.let { RootfsConfigurator.ensureDir(it) }

        var lastError: Throwable? = null
        for (attempt in 1..MAX_DOWNLOAD_ATTEMPTS) {
            try {
                downloadOnce(source, target, onProgress)
                onProgress(InstallProgress(InstallProgress.Stage.VERIFYING))
                if (verify(target, source.sha256)) {
                    return
                }
                target.delete()
                throw InstallException.Checksum(
                    "SHA-256 mismatch for ${target.name}",
                )
            } catch (error: InstallException.Checksum) {
                lastError = error
                // One re-download is enough; a second identical failure means
                // the published checksum is wrong.
                if (attempt >= 2) break
            } catch (error: IOException) {
                lastError = error
                target.delete()
                if (attempt < MAX_DOWNLOAD_ATTEMPTS) {
                    runCatching { Thread.sleep(500L * attempt) }
                }
            }
        }
        throw lastError ?: InstallException.Network(
            "Could not download ${source.url}",
        )
    }

    private fun downloadOnce(
        source: RootfsSource.Remote,
        target: File,
        onProgress: (InstallProgress) -> Unit,
    ) {
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

    private fun verify(file: File, expectedSha256: String?): Boolean {
        if (expectedSha256.isNullOrBlank()) return true
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
        return actual.equals(expectedSha256, ignoreCase = true)
    }

    // ---------------------------------------------------------------- extraction

    private fun extract(archive: File): Boolean {
        val proot = binaries
        if (proot != null && extractWithProot(proot, archive)) {
            return true
        }
        Log.w(TAG, "Falling back to the system tar")
        return extractWithTar(archive)
    }

    private fun extractWithProot(
        proot: ProotBinaries,
        archive: File,
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

    private fun extractWithTar(archive: File): Boolean {
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

    /**
     * Rejects archives that would write outside the sandbox.
     *
     * Only imported archives are checked; remote and bundled archives are
     * pinned by SHA-256 and produced by the profile author.
     */
    private fun validateArchive(archive: File) {
        if (!File("/system/bin/tar").isFile) return
        val outcome = runProcessCapture(
            command = listOf(
                "/system/bin/sh",
                "-c",
                "/system/bin/tar -tf " + shellQuote(archive.absolutePath),
            ),
            workingDir = File(appContext.cacheDir, "tar-list"),
            prootLoader = null,
            maxOutputBytes = 4 * 1024 * 1024,
        )
        if (outcome.exitCode != 0) {
            throw InstallException.Extraction(
                "Could not inspect ${archive.name}: ${outcome.output}",
            )
        }
        for (line in outcome.output.lineSequence()) {
            val entry = line.trim()
            if (entry.isEmpty()) continue
            if (entry.startsWith("/") || entry.split('/').any { it == ".." }) {
                throw InstallException.Extraction("Unsafe archive entry: $entry")
            }
        }
    }

    // ------------------------------------------------------------------ process

    private fun runProcess(
        command: List<String>,
        workingDir: File,
        prootLoader: String?,
        label: String,
    ): Boolean = try {
        val outcome = runProcessCapture(command, workingDir, prootLoader)
        if (outcome.exitCode != 0) {
            Log.e(TAG, "$label failed with exit code ${outcome.exitCode}: ${outcome.output}")
            false
        } else {
            Log.i(TAG, "$label finished successfully")
            true
        }
    } catch (error: Exception) {
        Log.e(TAG, "$label failed", error)
        false
    }

    private fun runProcessCapture(
        command: List<String>,
        workingDir: File,
        prootLoader: String?,
        maxOutputBytes: Int = 16 * 1024,
    ): ProcessOutcome {
        RootfsConfigurator.ensureDir(workingDir)
        val builder = ProcessBuilder(command)
            .directory(workingDir)
            .redirectErrorStream(true)
        builder.environment().apply {
            put("TMPDIR", workingDir.absolutePath)
            put("PROOT_TMP_DIR", workingDir.absolutePath)
            if (prootLoader != null) put("PROOT_LOADER", prootLoader)
        }

        val process = builder.start()
        val output = readStream(process.inputStream, maxOutputBytes)
        val exitCode = process.waitFor()
        return ProcessOutcome(exitCode, output)
    }

    private data class ProcessOutcome(val exitCode: Int, val output: String)

    private fun readStream(input: InputStream, maxBytes: Int): String {
        val builder = StringBuilder()
        var stored = 0
        input.use { stream ->
            val buffer = ByteArray(4096)
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                if (stored < maxBytes) {
                    val keep = minOf(read, maxBytes - stored)
                    builder.append(String(buffer, 0, keep, StandardCharsets.UTF_8))
                    stored += keep
                }
            }
        }
        return builder.toString().trim()
    }

    // --------------------------------------------------------------------- lock

    /**
     * Serializes installs across coroutines and, when the filesystem supports
     * it, across processes. The file lock is best-effort; the in-process
     * monitor always applies.
     */
    private fun <T> withInstallLock(block: () -> T): T = synchronized(monitor) {
        RootfsConfigurator.ensureDir(layout.rootfsDir)
        var handle: RandomAccessFile? = null
        var lock: FileLock? = null
        try {
            handle = runCatching { RandomAccessFile(layout.lockFile, "rw") }.getOrNull()
            lock = runCatching { handle?.channel?.lock() }.getOrNull()
            block()
        } finally {
            runCatching { lock?.release() }
            runCatching { handle?.close() }
        }
    }

    // --------------------------------------------------------------------- utils

    private fun deleteRecursively(file: File) {
        if (runCatching { java.nio.file.Files.isSymbolicLink(file.toPath()) }
                .getOrDefault(false)
        ) {
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
        const val MAX_DOWNLOAD_ATTEMPTS = 3

        /** Used only when the archive size cannot be known before downloading. */
        const val ESTIMATED_ARCHIVE_BYTES = 64L * 1024L * 1024L

        /** Conservative upper bound for the unpacked base image. */
        const val ESTIMATED_EXTRACTED_BYTES = 160L * 1024L * 1024L
    }
}

internal enum class InstallDecision {
    USE_EXISTING,
    RECONFIGURE,
    REINSTALL,
}

/**
 * Decides whether the current image can be reused, only needs its
 * configuration refreshed, or must be replaced.
 */
internal fun decideInstall(
    layout: RootfsLayout,
    profile: RootfsProfile,
    metadata: RootfsMetadata?,
    force: Boolean = false,
): InstallDecision {
    if (force) return InstallDecision.REINSTALL
    if (!layout.isInstalled()) return InstallDecision.REINSTALL
    if (metadata == null) return InstallDecision.REINSTALL
    if (metadata.schema != RootfsMetadata.SCHEMA) return InstallDecision.REINSTALL
    if (!profile.validate(layout.sandboxDir)) return InstallDecision.REINSTALL
    if (metadata.id != profile.id) return InstallDecision.REINSTALL
    if (metadata.source != profile.source.label) return InstallDecision.REINSTALL
    if (metadata.sha256 != profile.source.sha256) return InstallDecision.REINSTALL
    if (metadata.configVersion != profile.configVersion) return InstallDecision.RECONFIGURE
    return InstallDecision.USE_EXISTING
}
