package com.vibecoderspace.linux

/**
 * Progress reported while a root filesystem is being provisioned.
 *
 * [fraction] is in `0f..1f` while downloading and `-1f` when the step has no
 * meaningful progress value.
 */
data class InstallProgress(
    val stage: Stage,
    val fraction: Float = -1f,
) {
    enum class Stage {
        DOWNLOADING,
        EXTRACTING,
        CONFIGURING,
    }
}
