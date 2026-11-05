package com.vibecoderspace.linux

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** PRoot-backed [LinuxRuntime] that installs and configures root filesystems. */
class ProotRuntime(
    context: Context,
    binaries: ProotBinaries,
    profiles: List<RootfsProfile> = DefaultProfiles.all,
    options: LinuxRuntimeOptions = LinuxRuntimeOptions(),
) : LinuxRuntime {

    private val installer = RootfsInstaller(context.applicationContext, binaries, options)
    private val profiles: List<RootfsProfile> = profiles

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
}
