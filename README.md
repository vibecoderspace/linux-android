# linux-android

A small Android library for running Linux userlands with [PRoot](https://github.com/termux/proot).

It is deliberately headless: PRoot plus a root filesystem is enough to run
`opencode serve`, installers, package managers, build scripts, and other
processes. A terminal emulator and PTY are only needed for interactive shells
and TUIs, which are out of scope for this library.

## Features

- Launch any guest command as a normal Android process.
- Install root filesystems on demand, with progress reporting.
- Keep several distributions installed side by side.
- Distro behavior lives in a profile, not in the launch code.
- Host-supplied PRoot binaries, so the library stays Apache-2.0.

## Status

`0.1.1` — usable, API may still change.

## Installation

### JitPack

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.vibecoderspace:linux-android:v0.1.1")
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

// Install Ubuntu 24.04 on first use.
val image = runtime.ensureInstalled(DefaultProfiles.ubuntu) { progress ->
    // progress.stage / progress.fraction
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

An interactive shell is available when the host app also provides a terminal:

```kotlin
val shell = runtime.launchShell(image)
```

### Hostname

The guest hostname is a runtime option, not a distro detail. It is written to
`/etc/hostname` and `/etc/hosts` (as `127.0.1.1`) when an image is installed or
re-configured:

```kotlin
LinuxRuntimeOptions(hostname = "myapp") // default: "localhost"
```

Existing images are re-configured in place on the next `ensureInstalled` call,
so changing the hostname does not trigger a download.

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
    ubuntu-24.04/
      sandbox/        extracted root filesystem
      home/           bind-mounted at /home and /root
      bin/init        profile init script
      stat, vmstat    PRoot /proc overrides
      meta.properties
      .installed
    debian-12/
      ...
```

A legacy single-image install at `<private>/local/sandbox` is adopted in place
for the Ubuntu profile on first use. If a profile's archive URL changes later,
the next `ensureInstalled` call detects the mismatch in `meta.properties` and
reinstalls that image instead of silently keeping the stale one.

## Play Store note

This library only launches processes. If you publish an app on Google Play,
remember that Play restricts downloading executable code at runtime. Shipping
root filesystems and agent binaries through Play Asset Delivery instead of
downloading them on first launch is the safer path.

## License

The library is Apache-2.0. PRoot is GPLv2+ and is distributed by the host app,
not by this library. See [NOTICE](NOTICE).
