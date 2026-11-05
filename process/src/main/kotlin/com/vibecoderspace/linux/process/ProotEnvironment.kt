package com.vibecoderspace.linux.process

import com.vibecoderspace.linux.ProotBinaries
import java.io.File

/** Builds the complete environment for a PRoot process. */
internal object ProotEnvironment {

    private const val GUEST_PATH =
        "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

    fun build(
        binaries: ProotBinaries,
        prootTmpDir: File,
        spec: ProcessSpec,
    ): Map<String, String> {
        val environment = HashMap(System.getenv())

        environment["HOME"] = "/home"
        environment["PATH"] = GUEST_PATH
        environment["SHELL"] = "/bin/bash"
        environment["TERM"] = "xterm-256color"
        environment["COLORTERM"] = "truecolor"
        environment["LANG"] = "C.UTF-8"
        environment["LC_ALL"] = "C.UTF-8"
        environment["TMPDIR"] = "/tmp"
        environment["USER"] = "root"
        environment["LOGNAME"] = "root"
        environment["PROOT_LOADER"] = binaries.loader.absolutePath
        environment["PROOT_TMP_DIR"] = prootTmpDir.absolutePath

        environment.putAll(spec.environment)
        return environment
    }
}
