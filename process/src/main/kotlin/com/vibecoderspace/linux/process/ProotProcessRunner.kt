package com.vibecoderspace.linux.process

import android.content.Context
import com.vibecoderspace.linux.ProotBinaries
import com.vibecoderspace.linux.RootfsImage
import java.io.File

/**
 * Starts processes inside an installed root filesystem with PRoot.
 *
 * The runner owns only process creation: it builds the PRoot argv and
 * environment, starts the process, and returns a [RunningProcess]. Root
 * filesystem installation stays in `:linux`.
 */
class ProotProcessRunner(
    context: Context,
    private val binaries: ProotBinaries,
) {

    private val appContext: Context = context.applicationContext
    private val prootTmpDir: File = File(appContext.cacheDir, "proot").apply { mkdirs() }

    /**
     * Starts [spec] inside [image].
     *
     * The caller owns the returned process and must call [RunningProcess.stop]
     * or [RunningProcess.awaitExit] when done.
     */
    fun start(image: RootfsImage, spec: ProcessSpec): RunningProcess {
        val command = ProotCommandBuilder.build(binaries, image, spec)
        val environment = ProotEnvironment.build(binaries, prootTmpDir, spec)

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

        return ProcessBuilderProcess(builder.start())
    }
}
