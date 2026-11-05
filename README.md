# linux-android

A small Android library for running Linux userlands with [PRoot](https://github.com/termux/proot).

It is deliberately headless: PRoot plus a root filesystem is enough to run
`opencode serve`, installers, package managers, build scripts, and other
processes. A terminal emulator and PTY are only needed for interactive shells
and TUIs, which are out of scope for this library.

## Features

- Launch any guest command as a normal Android process.
- Install one root filesystem on demand, with progress reporting.
- Replace the image when the profile or archive changes; keep user data.
- Fluent command builder, one-shot `run()`, and a `plan()` for PTY runners.
- Distro behavior lives in a profile, not in the launch code.
- Host-supplied PRoot binaries, so the library stays Apache-2.0.

## Status

`0.2.0` — usable, API may still change.

## Installation

### JitPack

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.vibecoderspace:linux-android:v0.2.0")
}
```

### Composite build

```kotlin
// settings.gradle.kts
includeBuild("../linux-android") {
    dependencySubstitution {
        substitute(module("com.github.vibecoderspace:linux-android"))
            .using(project(":linux"))
    }
}
```

## PRoot binaries are supplied by the app

The library does **not** contain `libproot.so` or `libloader.so`. PRoot is
GPLv2+, so the binary lives in the host app. That keeps the library permissive
and makes the licensing boundary explicit.

Add both files to your app:

```text
app/src/main/jniLibs/arm64-v8a/libproot.so
app/src/main/jniLibs/arm64-v8a/libloader.so
```

Enable legacy packaging so the files exist on disk:

```kotlin
android {
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}
```

## Usage

```kotlin
val binaries = ProotBinaries.fromNativeLibraryDir(context)
    ?: error("PRoot binaries are missing")

val runtime = LinuxRuntime.proot(
    context = context,
    binaries = binaries,
    options = LinuxRuntimeOptions(hostname = "myapp"),
)

// Reuse the installed image or install Ubuntu 24.04 on first use.
val image = runtime.installedImage()
    ?: runtime.ensureInstalled(DefaultProfiles.ubuntu) { progress ->
        // progress.stage / progress.fraction / progress.message
    }

// Run a guest process.
val process = runtime.launch(
    image,
    LinuxProcessSpec(
        command = listOf("uname", "-a"),
        workingDirectory = "/home",
    ),
)
println(process.output.bufferedReader().readText())
val exitCode = process.awaitExit()
```

Long-running services can write straight to a log file:

```kotlin
val server = runtime.launch(
    image,
    LinuxProcessSpec(
        command = listOf("opencode", "serve", "--port", "4097"),
        workingDirectory = "/home",
        logFile = File(context.cacheDir, "opencode.log"),
    ),
)

server.stop()
```

### Commands

`LinuxCommandBuilder` describes a guest command once and can be reused:

```kotlin
val base = LinuxCommandBuilder
    .of("/usr/bin/apt-get")
    .workingDirectory("/home")

// One-shot: capture output, exit code, and timeout.
val update = runtime.run(image, base.argument("update"))
if (!update.isSuccess) error(update.output)

// Long-running: the caller owns the process.
val server = runtime.command(
    image,
    LinuxCommandBuilder
        .of("/home/.opencode/bin/opencode", "serve", "--port", "4097")
        .logTo(File(context.cacheDir, "opencode.log")),
)
```

`run()` kills the process after `timeout` (10 minutes by default), caps output
at 256 KB, and returns:

```kotlin
CommandResult(
    exitCode = 0,
    output = "...",
    truncated = false,
    timedOut = false,
)
```

A non-zero exit code is returned, not thrown.

### PTY sessions

`plan()` resolves a spec into the exact PRoot invocation. Use it when a terminal
emulator owns the process:

```kotlin
val plan = runtime.plan(image, runtime.shellSpec(image))

val session = TerminalSession(
    plan.executable,                              // libproot.so
    plan.workingDirectory.absolutePath,           // host cwd
    plan.arguments.toTypedArray(),                // full argv
    plan.environment.map { "${it.key}=${it.value}" }.toTypedArray(),
    transcriptRows,
    client,
)
```

The same plan drives `command()` and `run()`, so PTY tabs and headless jobs use
identical PRoot flags, binds, and environment.

An interactive shell is available when the host app also provides a terminal:

```kotlin
val shell = runtime.launchShell(image)
```

### Hostname and DNS

The guest hostname and DNS servers are runtime options, not distro details.
They are written to `/etc/hostname`, `/etc/hosts`, and `/etc/resolv.conf` when an
image is installed or re-configured:

```kotlin
LinuxRuntimeOptions(
    hostname = "myapp",                  // default: "localhost"
    dnsServers = listOf("1.1.1.1"),      // default: 1.1.1.1, 8.8.8.8
)
```

Existing images are re-configured in place on the next `ensureInstalled` call,
so changing either option does not trigger a download.

### One image at a time

There is exactly one installed image. Installing a different profile replaces
the current one:

```kotlin
// Ubuntu -> Debian: sandbox, init script and metadata are replaced.
val image = runtime.ensureInstalled(DefaultProfiles.debian, keepHome = true)
```

`keepHome = true` (the default) preserves `/home` and `/root`. Pass `false` to
reset user data as well.

Recovery and removal:

```kotlin
// Reinstall the same profile without touching home/.
val repaired = runtime.repair(image)

// Remove the image. Cached archives stay in the cache directory.
runtime.uninstall(deleteHome = true)
```

Installation is serialized: concurrent `ensureInstalled` calls run one at a
time. A failed install never writes the `.installed` marker, so the next call
retries cleanly. A corrupt cached archive is deleted and downloaded again.

## Profiles

| Profile | ID | Shell | Source |
|---|---|---|---|
| `DefaultProfiles.ubuntu` | `ubuntu-24.04` | `/bin/bash` | Ubuntu Base 24.04.4 (cdimage.ubuntu.com) |
| `DefaultProfiles.debian` | `debian-12` | `/bin/bash` | Debian bookworm slim rootfs |

A custom profile only needs to describe the distribution:

```kotlin
class MyProfile : RootfsProfile {
    override val id = "my-distro"
    override val displayName = "My distro"
    override val source = RootfsSource.Imported(File("/sdcard/my-rootfs.tar.gz"))
    override val shell = "/bin/sh"
    override val guestPath = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
    override val locale = "C.UTF-8"
    override val requiredPaths = listOf("bin/sh")

    override fun shellArguments(layout: RootfsLayout) = listOf("-l")
    override fun initScript() = "export PS1='$ '\n"
    override fun environment(layout: RootfsLayout) =
        mapOf("HOME" to "/home", "PATH" to guestPath)
}
```

## Storage layout

```text
<private>/local/
  rootfs/
    sandbox/          extracted root filesystem        (-r target)
    home/             bind-mounted at /home and /root  (kept on replace)
    bin/init          profile init script
    stat, vmstat      PRoot /proc overrides
    meta.properties   schema, profile, source, checksum, config version
    .installed        commit marker, written last
    .install.lock     install serialization
    .failed           last failure, if any
```

There is no per-profile directory and no legacy location. If the profile or its
archive URL changes, the next `ensureInstalled` call replaces `sandbox/` in
place and leaves `home/` alone.

## Play Store note

This library only launches processes. If you publish an app on Google Play,
remember that Play restricts downloading executable code at runtime. Shipping
root filesystems and agent binaries through Play Asset Delivery instead of
downloading them on first launch is the safer path.

## License

The library is Apache-2.0. PRoot is GPLv2+ and is distributed by the host app,
not by this library. See [NOTICE](NOTICE).
