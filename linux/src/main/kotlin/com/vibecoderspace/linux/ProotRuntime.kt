package com.vibecoderspace.linux

import android.content.Context
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration

/** PRoot-backed [LinuxRuntime]. */
class ProotRuntime(
    context: Context,
    private val binaries: ProotBinaries,
    private val profiles: List<RootfsProfile> = DefaultProfiles.all,
    private val options: LinuxRuntimeOptions = LinuxRuntimeOptions(),
) : LinuxRuntime {

    private val appContext: Context = context.applicationContext
    private val installer = RootfsInstaller(appContext, binaries, options)
    private val prootTmpDir: File = File(appContext.cacheDir, "proot-tmp").apply { mkdirs() }

    override fun installedImage(): RootfsImage? = installer.installedImage(profiles)

    override suspend fun ensureInstalled(
        profile: RootfsProfile,
        keepHome: Boolean,
        onProgress: (InstallProgress) -> Unit,
    ): RootfsImage = withContext(Dispatchers.IO) {
        installer.install(profile, keepHome = keepHome, onProgress = onProgress)
    }

    override suspend fun repair(
        image: RootfsImage,
        keepHome: Boolean,
        onProgress: (InstallProgress) -> Unit,
    ): RootfsImage = withContext(Dispatchers.IO) {
        installer.install(
            profile = image.profile,
            keepHome = keepHome,
            force = true,
            onProgress = onProgress,
        )
    }

    override suspend fun uninstall(deleteHome: Boolean) = withContext(Dispatchers.IO) {
        installer.uninstall(deleteHome)
    }

    override fun plan(image: RootfsImage, spec: LinuxProcessSpec): LinuxLaunchPlan {
        val arguments = ProotCommandBuilder.build(binaries, image, spec)
        val environment = ProotEnvironment.build(
            image = image,
            binaries = binaries,
            prootTmpDir = prootTmpDir,
            workingDirectory = spec.workingDirectory,
            nativeLibDir = appContext.applicationInfo.nativeLibraryDir.orEmpty(),
            extra = spec.environment,
        )
        return LinuxLaunchPlan(
            executable = arguments.first(),
            arguments = arguments,
            environment = environment,
            workingDirectory = image.layout.homeDir,
        )
    }

    override fun shellSpec(image: RootfsImage): LinuxProcessSpec = LinuxProcessSpec(
        command = listOf(image.profile.shell) + image.profile.shellArguments(image.layout),
        workingDirectory = "/home",
    )

    override fun launch(image: RootfsImage, spec: LinuxProcessSpec): LinuxProcess {
        val plan = plan(image, spec)

        val builder = ProcessBuilder(plan.arguments)
            .directory(plan.workingDirectory)
            .redirectErrorStream(spec.redirectErrorStream)

        builder.environment().apply {
            clear()
            putAll(plan.environment)
        }

        spec.logFile?.let { logFile ->
            logFile.parentFile?.mkdirs()
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
        }

        return ProotProcess(builder.start())
    }

    override suspend fun run(
        image: RootfsImage,
        spec: LinuxProcessSpec,
        timeout: Duration,
        maxOutputBytes: Int,
    ): CommandResult = withContext(Dispatchers.IO) {
        // run() always captures output; logTo only mirrors the result.
        val captureSpec = if (spec.logFile != null) spec.copy(logFile = null) else spec
        val process = launch(image, captureSpec)
        val reader = async(Dispatchers.IO) { readCapped(process.output, maxOutputBytes) }

        var timedOut = false
        try {
            val deadline = System.nanoTime() + timeout.inWholeNanoseconds
            while (process.isAlive) {
                if (System.nanoTime() >= deadline) {
                    timedOut = true
                    break
                }
                delay(POLL_INTERVAL_MS)
            }
            if (timedOut) {
                process.stop()
            }

            val exitCode = runCatching { process.awaitExit() }.getOrDefault(-1)
            val (output, truncated) = reader.await()
            spec.logFile?.let { appendLog(it, output) }
            CommandResult(
                exitCode = exitCode,
                output = output,
                truncated = truncated,
                timedOut = timedOut,
            )
        } finally {
            if (process.isAlive) {
                process.stop()
            }
            runCatching { reader.cancel() }
        }
    }

    override fun launchShell(image: RootfsImage): LinuxProcess =
        launch(image, shellSpec(image))

    private fun readCapped(input: InputStream, maxBytes: Int): Pair<String, Boolean> {
        require(maxBytes > 0) { "maxOutputBytes must be positive" }
        val builder = StringBuilder()
        var stored = 0
        var truncated = false
        input.use { stream ->
            val buffer = ByteArray(4096)
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                if (stored < maxBytes) {
                    val keep = minOf(read, maxBytes - stored)
                    builder.append(String(buffer, 0, keep, Charsets.UTF_8))
                    stored += keep
                    if (keep < read) truncated = true
                } else {
                    truncated = true
                }
            }
        }
        return builder.toString() to truncated
    }

    private fun appendLog(file: File, text: String) {
        runCatching {
            file.parentFile?.mkdirs()
            file.appendText(text)
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 25L
    }
}
