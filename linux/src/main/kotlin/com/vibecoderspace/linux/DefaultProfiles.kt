package com.vibecoderspace.linux

import com.vibecoderspace.linux.profile.UbuntuProfile

/** Profiles shipped with the library. */
object DefaultProfiles {
    val ubuntu: RootfsProfile = UbuntuProfile()

    val all: List<RootfsProfile> = listOf(ubuntu)
}
