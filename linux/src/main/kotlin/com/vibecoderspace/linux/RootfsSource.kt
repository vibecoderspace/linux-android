package com.vibecoderspace.linux

import java.io.File

/** Where a root filesystem archive comes from. */
sealed interface RootfsSource {

    /** Human-readable source description, used in metadata and error messages. */
    val label: String

    /** A remote tarball downloaded on first install. */
    data class Remote(
        val url: String,
        val sha256: String? = null,
    ) : RootfsSource {
        override val label: String get() = url
    }

    /** An archive bundled in the host app's assets. */
    data class Bundled(
        val assetPath: String,
    ) : RootfsSource {
        override val label: String get() = "asset:$assetPath"
    }

    /** An archive supplied by the host app, for example from a document picker. */
    data class Imported(
        val file: File,
    ) : RootfsSource {
        override val label: String get() = file.absolutePath
    }
}
