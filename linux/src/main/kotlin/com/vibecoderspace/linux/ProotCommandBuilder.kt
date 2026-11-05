package com.vibecoderspace.linux

import java.io.File

/**
 * Builds the PRoot argv for a guest command.
 *
 * This is distribution-agnostic: only the rootfs location, the home directory,
 * and the Android bind mounts are involved. The guest command comes from
 * [LinuxProcessSpec], so Ubuntu, Debian, Alpine, and custom images all use the
 * same path.
 */
object ProotCommandBuilder {

    fun build(
        binaries: ProotBinaries,
        image: RootfsImage,
        spec: LinuxProcessSpec,
    ): List<String> {
        val args = ArrayList<String>()
        // ProcessBuilder uses the first element as argv[0], so PRoot's own
        // program name must not be repeated here.
        args.add(binaries.proot.absolutePath)
        args.add("--kill-on-exit")
        args.add("-w")
        args.add(spec.workingDirectory)

        addAndroidBinds(args, image)
        for (bind in spec.extraBinds) {
            addBind(args, bind.host, bind.guest)
        }

        args.add("-r")
        args.add(image.rootfsDir.absolutePath)
        args.add("-0")
        args.add("--link2symlink")
        args.add("--sysvipc")
        args.add("-L")
        args.addAll(spec.command)
        return args
    }

    private fun addAndroidBinds(args: MutableList<String>, image: RootfsImage) {
        addBind(args, "/sdcard", null)
        addBind(args, "/storage", null)
        addBind(args, "/dev", null)
        addBind(args, "/data", null)
        addBind(args, "/dev/urandom", "/dev/random")
        addBind(args, "/proc", null)
        addBind(args, image.layout.homeDir.absolutePath, "/home")
        addBind(args, image.layout.homeDir.absolutePath, "/root")
        addBind(args, image.layout.privateDir.absolutePath, null)
        addBind(args, image.layout.statFile.absolutePath, "/proc/stat")
        addBind(args, image.layout.vmstatFile.absolutePath, "/proc/vmstat")
        addBind(args, "/proc/self/fd", "/dev/fd")
        addBind(args, "/proc/self/fd/0", "/dev/stdin")
        addBind(args, "/proc/self/fd/1", "/dev/stdout")
        addBind(args, "/proc/self/fd/2", "/dev/stderr")
        addBind(args, "/sys", null)
        addBind(args, File(image.rootfsDir, "tmp").absolutePath, "/dev/shm")

        addBind(args, "/system", null)
        addBind(args, "/system_ext", null)
        addBind(args, "/product", null)
        addBind(args, "/odm", null)
        addBind(args, "/apex", null)
        addBind(args, "/vendor", null)
        addBind(args, "/linkerconfig/ld.config.txt", null)
        addBind(args, "/linkerconfig/com.android.art/ld.config.txt", null)
        addBind(args, "/plat_property_contexts", "/property_contexts")
    }

    private fun addBind(args: MutableList<String>, host: String, guest: String?) {
        if (!File(host).exists()) return
        args.add("-b")
        args.add(if (guest == null) host else "$host:$guest")
    }
}
