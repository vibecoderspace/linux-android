package com.vibecoderspace.linux

import java.io.File
import java.util.TreeMap

/** Builds the host environment for a PRoot process. */
object ProotEnvironment {

    fun build(
        image: RootfsImage,
        binaries: ProotBinaries,
        prootTmpDir: File,
        workingDirectory: String,
        nativeLibDir: String,
        extra: Map<String, String> = emptyMap(),
    ): Map<String, String> {
        val env = TreeMap<String, String>()
        env.putAll(System.getenv())
        env.putAll(image.profile.environment(image.layout))

        env["PROOT"] = binaries.proot.absolutePath
        env["PROOT_LOADER"] = binaries.loader.absolutePath
        env["PROOT_TMP_DIR"] = prootTmpDir.absolutePath
        env["LOCAL"] = image.layout.localDir.absolutePath
        env["EXT_HOME"] = image.layout.homeDir.absolutePath
        env["PRIVATE_DIR"] = image.layout.privateDir.absolutePath
        env["NATIVE_LIB_DIR"] = nativeLibDir
        env["WKDIR"] = workingDirectory
        env["SANDBOX"] = "true"

        env.putAll(extra)
        return env
    }
}
