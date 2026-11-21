package com.metrolist.android.playback

import com.metrolist.shared.deviceconnect.DcRepeatMode
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.playback.QueuePlaybackRequest
import com.metrolist.shared.settings.AutomotiveSongPlaybackMode
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomotiveQueuePolicyTest {
    private val seed = SongItem("seed", "Seed", null, emptyList(), null)

    @Test
    fun onlySelectedSongPersistsTheLockAndOnlyRadioResetsRemoteRepeat() {
        val selected = automotivePlaybackIntent(AutomotiveSongPlaybackMode.SINGLE_SONG)
        assertTrue(selected.selectedSongOnly)
        assertFalse(selected.radioSeed)
        assertNull(selected.remoteRepeatMode)

        val radio = automotivePlaybackIntent(AutomotiveSongPlaybackMode.RADIO)
        assertFalse(radio.selectedSongOnly)
        assertTrue(radio.radioSeed)
        assertEquals(DcRepeatMode.OFF, radio.remoteRepeatMode)

        val queue = automotivePlaybackIntent(AutomotiveSongPlaybackMode.QUEUE)
        assertFalse(queue.selectedSongOnly)
        assertFalse(queue.radioSeed)
        assertNull(queue.remoteRepeatMode)
    }

    @Test
    fun radioTransitionFollowsTheNewestSeedRequest() =
        runTest {
            val requests =
                MutableSharedFlow<QueuePlaybackRequest>(
                    replay = 1,
                    extraBufferCapacity = 2,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST,
                )
            val currentSong = MutableStateFlow<SongItem?>(null)
            val isPlaying = MutableStateFlow(false)
            val transitionPending = MutableStateFlow(false)
            var played: SongItem? = null

            requests.tryEmit(QueuePlaybackRequest(1L, seed))
            isPlaying.value = true
            currentSong.value = seed
            requests.tryEmit(QueuePlaybackRequest(2L, seed))

            awaitAutomotiveRadioSeed(
                song = seed,
                initialRequestId = 0L,
                requests = requests,
                currentSong = currentSong,
                isPlaying = isPlaying,
                transitionPending = transitionPending,
                latestRequestId = { 2L },
                isCurrentSession = { true },
                playMix = { played = it },
            )

            assertEquals(seed, played)
        }

    @Test
    fun radioTransitionRejectsAnUnrelatedSelection() =
        runTest {
            val requests =
                MutableSharedFlow<QueuePlaybackRequest>(
                    replay = 1,
                    extraBufferCapacity = 2,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST,
                )
            val currentSong = MutableStateFlow<SongItem?>(seed)
            val isPlaying = MutableStateFlow(true)
            val transitionPending = MutableStateFlow(false)
            val other = seed.copy(id = "other")
            var played: SongItem? = null

            requests.tryEmit(QueuePlaybackRequest(1L, other))

            awaitAutomotiveRadioSeed(
                song = seed,
                initialRequestId = 0L,
                requests = requests,
                currentSong = currentSong,
                isPlaying = isPlaying,
                transitionPending = transitionPending,
                latestRequestId = { 1L },
                isCurrentSession = { true },
                playMix = { played = it },
            )

            assertNull(played)
        }
}
