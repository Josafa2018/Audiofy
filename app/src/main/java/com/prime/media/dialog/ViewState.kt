package com.prime.media.dialog

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.media3.common.MediaItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@Stable
interface PlayingQueue {
    /**
     * The playing queue.
     */
    val queue: Flow<List<MediaItem>>
    val shuffle: Boolean
    /**
     * Play the track of the queue at [position]
     */
    fun playTrackAt(position: Int)

    /**
     * Play the track of the queue identified by the [uri]
     */
    fun playTrack(uri: Uri)

    /**
     * Remove the track from the queue identified by [key].
     */
    fun remove(key: Uri)

    /**
     * Toggles the shuffle
     */
    fun toggleShuffle()
}