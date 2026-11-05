# linux-android

A small Android library that provisions Linux root filesystems with
[PRoot](https://github.com/termux/proot)-based extraction.

It is deliberately limited to setup: download, verify, extract, configure, and
validate one image. The host app owns the terminal, PRoot argv, environment,
and process lifecycle.

## Features

- Install one root filesystem on demand, with progress reporting.
- Replace the image when the profile or archive changes; keep user data.
- Serialized installs, disk checks, download retries, and cached archives.
- Metadata schema with profile, source, checksum, and config version.
- Install, reinstall, and delete without touching `home/` unless asked.
- Distro behavior lives in a profile, not in the installer.
- Host-supplied PRoot binaries, so the library stays Apache-2.0.

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

The library does not launch the shell or any other process. The app builds the
PRoot argv and environment it needs, then starts the process itself.

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

Existing images are re-configured in place on the next `install` call, so
changing either option does not trigger a download.

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
