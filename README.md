# linux-android

Two small Android modules for running Linux userlands with
[PRoot](https://github.com/termux/proot):

- `:linux` provisions a root filesystem: download, verify, extract, configure,
  and validate one image.
- `:process` starts processes inside an installed image: PRoot argv,
  environment, and `ProcessBuilder` lifecycle.

The host app still owns the terminal UI and any PTY it needs.

## Features

- Install one root filesystem on demand, with progress reporting.
- Replace the image when the profile or archive changes; keep user data.
- Serialized installs, disk checks, download retries, and cached archives.
- Metadata schema with profile, source, checksum, and config version.
- Install, reinstall, and delete without touching `home/` unless asked.
- Start guest commands with a complete PRoot argv and environment.
- Host-supplied PRoot binaries, so the libraries stay Apache-2.0.

## Status

`0.2.0` — in progress, not published yet.

## Installation

### JitPack

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.vibecoderspace:linux-android:v0.2.0")
    implementation("com.github.vibecoderspace:linux-android-process:v0.2.0")
}
```

### Composite build

```kotlin
// settings.gradle.kts
includeBuild("../linux-android") {
    dependencySubstitution {
        substitute(module("com.github.vibecoderspace:linux-android"))
            .using(project(":linux"))
        substitute(module("com.github.vibecoderspace:linux-android-process"))
            .using(project(":process"))
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

### Root filesystem

```kotlin
val binaries = ProotBinaries.fromNativeLibraryDir(context)
    ?: error("PRoot binaries are missing")

val runtime = LinuxRuntime.proot(
    context = context,
    binaries = binaries,
)

// Reuse the installed image or install Ubuntu 24.04 on first use.
val image = runtime.installedImage()
    ?: runtime.install(DefaultProfiles.ubuntu) { progress ->
        // progress.stage / progress.fraction / progress.message
    }

println(image.profile.displayName)
println(image.layout.sandboxDir)
println(image.layout.homeDir)
```

The returned `RootfsImage` exposes the layout the host app needs:

```text
image.rootfsDir                 extracted root filesystem
image.layout.sandboxDir         same tree, as a File
image.layout.homeDir            user data, kept on reinstall
```

### Starting a process

`:process` builds the PRoot argv and environment, then starts the process:

```kotlin
val runner = ProotProcessRunner(context, binaries)

val process = runner.start(
    image,
    ProcessSpec(listOf("/bin/bash", "-lc", "uname -a")),
)

println(process.output.bufferedReader().readText())
val exitCode = process.awaitExit()
```

Long-running services can write straight to a log file:

```kotlin
val server = runner.start(
    image,
    ProcessSpec(
        command = listOf("/home/.opencode/bin/opencode", "serve", "--port", "4097"),
        logFile = File(context.cacheDir, "opencode.log"),
    ),
)

server.stop()
```

`ProcessSpec` accepts a guest working directory, extra environment variables,
a log file, and stderr merging. The runner binds the image's `home/` at `/home`
and `/root`, the app's private directory at its host path, and the usual
Android system paths.

### Hostname and DNS

Provisioning writes fixed values into the image:

```text
/etc/hostname     localhost
/etc/hosts        localhost + IPv6 entries
/etc/resolv.conf  nameserver 1.1.1.1
                  nameserver 8.8.8.8
```

There are no runtime options for these. If the host app needs a different
hostname or resolver, it can edit the files in `image.layout.sandboxDir` after
installation.

### One image at a time

There is exactly one installed image. Installing a different profile replaces
the current one:

```kotlin
// A different profile replaces sandbox/ and metadata, keeping home/.
val image = runtime.install(MyProfile())
```

Recovery and removal:

```kotlin
// Wipe and reinstall the current profile. keepHome defaults to true.
val reinstalled = runtime.reinstall(keepHome = true)

// Remove the image and its home directory. Cached archives stay.
runtime.delete()
```

Installation is serialized: concurrent `install` calls run one at a time. A
failed install never writes the `.installed` marker, so the next call retries
cleanly. A corrupt cached archive is deleted and downloaded again.

## Profiles

| Profile | ID | Source |
|---|---|---|
| `DefaultProfiles.ubuntu` | `ubuntu-24.04` | Ubuntu Base 24.04.4 (cdimage.ubuntu.com) |

A custom profile only needs to describe the archive and the paths it must
contain:

```kotlin
class MyProfile : RootfsProfile {
    override val id = "my-distro"
    override val displayName = "My distro"
    override val source = RootfsSource.Imported(File("/sdcard/my-rootfs.tar.gz"))
    override val requiredPaths = listOf("bin/sh")
}
```

Override `configure(context)` when the distribution needs extra files written
after extraction.

## Storage layout

```text
<private>/local/
  rootfs/
    sandbox/          extracted root filesystem        (-r target)
    home/             user data                        (kept on reinstall)
    meta.properties   schema, profile, source, checksum, config version
    .installed        commit marker, written last
    .install.lock     install serialization
    .failed           last failure, if any
```

There is no per-profile directory and no legacy location. If the profile or its
archive URL changes, the next `install` call replaces `sandbox/` in place and
leaves `home/` alone.

## Play Store note

This library only provisions root filesystems. If you publish an app on Google
Play, remember that Play restricts downloading executable code at runtime.
Shipping root filesystems and agent binaries through Play Asset Delivery instead
of downloading them on first launch is the safer path.

## License

The library is Apache-2.0. PRoot is GPLv2+ and is distributed by the host app,
not by this library. See [NOTICE](NOTICE).
