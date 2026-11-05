package com.vibecoderspace.linux

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** PRoot-backed [LinuxRuntime] that installs and configures root filesystems. */
class ProotRuntime(
    context: Context,
    binaries: ProotBinaries,
    private val profiles: List<RootfsProfile> = DefaultProfiles.all,
) : LinuxRuntime {

    private val installer = RootfsInstaller(context.applicationContext, binaries)

    override fun installedImage(): RootfsImage? = installer.installedImage(profiles)

    override suspend fun install(
        profile: RootfsProfile,
        onProgress: (InstallProgress) -> Unit,
    ): RootfsImage = withContext(Dispatchers.IO) {
        installer.install(profile, onProgress = onProgress)
    }

    override suspend fun reinstall(
        keepHome: Boolean,
        onProgress: (InstallProgress) -> Unit,
    ): RootfsImage = withContext(Dispatchers.IO) {
        val image = installer.installedImage(profiles)
            ?: throw IllegalStateException("No image is installed")
        installer.install(
            profile = image.profile,
            keepHome = keepHome,
            force = true,
            onProgress = onProgress,
        )
    }

    override suspend fun delete() = withContext(Dispatchers.IO) {
        installer.uninstall(deleteHome = true)
    }
}
