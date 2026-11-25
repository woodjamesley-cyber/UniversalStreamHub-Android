package com.universalstreamhub.app

import com.universalstreamhub.app.data.remote.MediaSummaryDto
import com.universalstreamhub.app.data.remote.NativeSourceDto
import com.universalstreamhub.app.data.remote.PlaybackOptionsDto
import com.universalstreamhub.app.data.remote.ProviderLinkDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogModelTest {

    @Test
    fun `open catalog titles are flagged as free and natively playable`() {
        val media = MediaSummaryDto(
            id = "open-night-of-the-living-dead",
            source = "open",
            title = "Night of the Living Dead",
            playback = "NATIVE",
        )
        assertTrue(media.isOpen)
        assertTrue(media.hasNativePlayback)
    }

    @Test
    fun `licensed titles default to provider handoff and no native playback`() {
        val media = MediaSummaryDto(id = "tmdb-155", title = "The Dark Knight")
        assertEquals("PROVIDER_APP", media.playback)
        assertFalse(media.isOpen)
        assertFalse(media.hasNativePlayback)
        assertFalse(media.downloadable)
    }

    @Test
    fun `downloads are opt-in per source`() {
        val source = NativeSourceDto(id = "ia-1", label = "480p MP4", url = "https://archive.org/x.mp4")
        assertFalse(source.downloadAllowed)
        val downloadable = source.copy(downloadAllowed = true, license = "Public Domain")
        assertTrue(downloadable.downloadAllowed)
        assertEquals("Public Domain", downloadable.license)
    }

    @Test
    fun `playback options separate native sources from provider apps`() {
        val options = PlaybackOptionsDto(
            native = listOf(NativeSourceDto(id = "a", url = "https://archive.org/a.mp4", downloadAllowed = true)),
            providerApps = listOf(ProviderLinkDto(name = "Netflix", packageName = "com.netflix.mediaclient", webUrl = "https://www.netflix.com")),
        )
        assertEquals(1, options.native.size)
        assertEquals("com.netflix.mediaclient", options.providerApps.single().packageName)
    }
}
