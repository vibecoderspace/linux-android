package com.vibecoderspace.linux

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** PRoot-backed [LinuxRuntime]. */
class ProotRuntime(
    context: Context,
    private val binaries: ProotBinaries,
    private val profiles: List<RootfsProfile> = DefaultProfiles.all,
) : LinuxRuntime {

    private val appContext: Context = context.applicationContext
    private val installer = RootfsInstaller(appContext, binaries)
    private val prootTmpDir: File = File(appContext.cacheDir, "proot-tmp").apply { mkdirs() }

    override fun installedImages(): List<RootfsImage> = installer.installedImages(profiles)

    override suspend fun ensureInstalled(
        profile: RootfsProfile,
        onProgress: (InstallProgress) -> Unit,
    ): RootfsImage = withContext(Dispatchers.IO) {
        installer.install(profile, onProgress)
    }

    override fun launch(image: RootfsImage, spec: LinuxProcessSpec): LinuxProcess {
        val command = ProotCommandBuilder.build(binaries, image, spec)
        val environment = ProotEnvironment.build(
            image = image,
            binaries = binaries,
            prootTmpDir = prootTmpDir,
            workingDirectory = spec.workingDirectory,
            nativeLibDir = appContext.applicationInfo.nativeLibraryDir.orEmpty(),
            extra = spec.environment,
        )

        val builder = ProcessBuilder(command)
            .directory(image.layout.homeDir)
            .redirectErrorStream(spec.redirectErrorStream)

        builder.environment().apply {
            clear()
            putAll(environment)
        }

        spec.logFile?.let { logFile ->
            logFile.parentFile?.mkdirs()
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
        }

        return ProotProcess(builder.start())
    }

    override fun launchShell(image: RootfsImage): LinuxProcess {
        val command = listOf(image.profile.shell) + image.profile.shellArguments(image.layout)
        return launch(
            image,
            LinuxProcessSpec(
                command = command,
                workingDirectory = "/home",
            ),
        )
    }
}
