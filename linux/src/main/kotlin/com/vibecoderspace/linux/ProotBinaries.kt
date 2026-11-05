package com.vibecoderspace.linux

import android.content.Context
import android.system.Os
import android.util.Log
import java.io.File

/**
 * PRoot executables supplied by the host application.
 *
 * The library does not bundle PRoot. PRoot is GPLv2+, so keeping the binaries in
 * the app makes the licensing boundary explicit: the library stays permissive,
 * while the app that ships PRoot carries the GPL notice and source offer.
 */
class ProotBinaries private constructor(
    val proot: File,
    val loader: File,
) {

    companion object {
        private const val TAG = "ProotBinaries"
        private const val PROOT_LIB = "libproot.so"
        private const val LOADER_LIB = "libloader.so"

        /**
         * Resolves PRoot from the app's extracted native library directory.
         *
         * Requires `android.packaging.jniLibs.useLegacyPackaging = true` (or
         * `android:extractNativeLibs="true"`) so the `.so` files exist on disk.
         */
        fun fromNativeLibraryDir(context: Context): ProotBinaries? {
            val dir = context.applicationInfo.nativeLibraryDir ?: return null
            return fromDirectory(File(dir))
        }

        fun fromDirectory(dir: File): ProotBinaries? {
            val proot = File(dir, PROOT_LIB)
            val loader = File(dir, LOADER_LIB)
            if (!proot.isFile || !loader.isFile) return null
            return create(proot, loader)
        }

        fun from(proot: File, loader: File): ProotBinaries {
            require(proot.isFile) { "PRoot binary not found: $proot" }
            require(loader.isFile) { "PRoot loader not found: $loader" }
            return create(proot, loader)
        }

        private fun create(proot: File, loader: File): ProotBinaries {
            makeExecutable(proot)
            makeExecutable(loader)
            return ProotBinaries(proot, loader)
        }

        private fun makeExecutable(file: File) {
            file.setReadable(true, false)
            file.setExecutable(true, false)
            runCatching { Os.chmod(file.absolutePath, 493) }
                .onFailure { Log.w(TAG, "chmod failed for $file", it) }
        }
    }
}
