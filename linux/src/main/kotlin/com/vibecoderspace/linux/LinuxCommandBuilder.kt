package com.vibecoderspace.linux

import java.io.File

/**
 * Immutable builder for a guest command.
 *
 * Every setter returns a copy, so a builder can be shared and specialized
 * without affecting the original:
 *
 * ```kotlin
 * val base = LinuxCommandBuilder.of("/usr/bin/apt-get").workingDirectory("/home")
 * val update = base.argument("update")
 * val install = base.argument("install").argument("-y").argument("curl")
 * ```
 *
 * [workingDirectory] is a guest path such as `/home`. Use [logTo] for
 * long-running processes; [LinuxRuntime.run] captures output instead.
 */
class LinuxCommandBuilder private constructor(
    private val argv: List<String>,
    private val workingDirectory: String,
    private val environment: Map<String, String>,
    private val logFile: File?,
    private val redirectErrorStream: Boolean,
    private val extraBinds: List<Bind>,
) {

    init {
        require(argv.isNotEmpty()) { "command must not be empty" }
        require(argv.first().isNotBlank()) { "argv[0] must not be blank" }
        require(workingDirectory.startsWith("/")) {
            "workingDirectory must be an absolute guest path: $workingDirectory"
        }
    }

    fun argument(value: String): LinuxCommandBuilder =
        copy(argv = argv + value)

    fun environment(key: String, value: String): LinuxCommandBuilder {
        requireEnvironmentKey(key)
        return copy(environment = environment + (key to value))
    }

    fun environment(values: Map<String, String>): LinuxCommandBuilder {
        values.keys.forEach(::requireEnvironmentKey)
        return copy(environment = environment + values)
    }

    fun workingDirectory(path: String): LinuxCommandBuilder =
        copy(workingDirectory = path)

    fun logTo(file: File): LinuxCommandBuilder =
        copy(logFile = file)

    fun mergeErrorStream(merge: Boolean): LinuxCommandBuilder =
        copy(redirectErrorStream = merge)

    fun bind(host: String, guest: String? = null): LinuxCommandBuilder {
        require(host.isNotBlank()) { "host path must not be blank" }
        require(guest == null || guest.startsWith("/")) {
            "guest path must be absolute: $guest"
        }
        return copy(extraBinds = extraBinds + Bind(host, guest))
    }

    fun build(): LinuxProcessSpec = LinuxProcessSpec(
        command = argv,
        workingDirectory = workingDirectory,
        environment = environment,
        logFile = logFile,
        redirectErrorStream = redirectErrorStream,
        extraBinds = extraBinds,
    )

    private fun copy(
        argv: List<String> = this.argv,
        workingDirectory: String = this.workingDirectory,
        environment: Map<String, String> = this.environment,
        logFile: File? = this.logFile,
        redirectErrorStream: Boolean = this.redirectErrorStream,
        extraBinds: List<Bind> = this.extraBinds,
    ): LinuxCommandBuilder = LinuxCommandBuilder(
        argv = argv,
        workingDirectory = workingDirectory,
        environment = environment,
        logFile = logFile,
        redirectErrorStream = redirectErrorStream,
        extraBinds = extraBinds,
    )

    private fun requireEnvironmentKey(key: String) {
        require(key.isNotBlank()) { "environment key must not be blank" }
        require('=' !in key) { "environment key must not contain '=': $key" }
    }

    companion object {
        fun of(vararg argv: String): LinuxCommandBuilder = LinuxCommandBuilder(
            argv = argv.toList(),
            workingDirectory = "/home",
            environment = emptyMap(),
            logFile = null,
            redirectErrorStream = true,
            extraBinds = emptyList(),
        )

        /** Runs [script] with `shell -c`. */
        fun shell(script: String, shell: String = "/bin/sh"): LinuxCommandBuilder =
            of(shell, "-c", script)
    }
}
