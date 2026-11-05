package com.vibecoderspace.linux

import java.io.InputStream
import java.util.concurrent.TimeUnit

/** A running process inside a root filesystem. */
interface LinuxProcess {
    val pid: Long
    val isAlive: Boolean

    /** Standard output (and standard error when merged). Empty when redirected to a file. */
    val output: InputStream

    fun stop()

    fun awaitExit(): Int
}

internal class ProotProcess(
    private val process: Process,
) : LinuxProcess {

    override val pid: Long get() = process.pid()

    override val isAlive: Boolean get() = process.isAlive

    override val output: InputStream get() = process.inputStream

    override fun stop() {
        if (!process.isAlive) return
        process.destroy()
        runCatching {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }
        }
    }

    override fun awaitExit(): Int = process.waitFor()
}
