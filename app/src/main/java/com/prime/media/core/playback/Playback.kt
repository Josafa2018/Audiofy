package com.prime.media.core.playback

import android.annotation.SuppressLint
import android.app.*
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.media3.common.*
import androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.*
import androidx.media3.session.MediaLibraryService.MediaLibrarySession.Callback
import androidx.media3.session.MediaSession.ControllerInfo
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.prime.media.MainActivity
import com.prime.media.R
import com.prime.media.console.Widget
import com.prime.media.core.db.Playlist
import com.prime.media.core.db.Playlists
import com.prime.media.core.util.Member
import com.primex.preferences.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import org.json.JSONArray
import javax.inject.Inject
import kotlin.random.Random

private const val TAG = "Playback"

/**
 * The name of Global [Playlists]
 */
private val PLAYLIST_FAVOURITE = Playlists.PRIVATE_PLAYLIST_PREFIX + "favourite"

/**
 * The name of Global [Playlists]
 */
private val PLAYLIST_RECENT = Playlists.PRIVATE_PLAYLIST_PREFIX + "recent"

/**
 * The name of Global [Playlists]
 */
private val PLAYLIST_QUEUE = Playlists.PRIVATE_PLAYLIST_PREFIX + "queue"

/**
 * Different roots served by this service.
 */
private const val ROOT_QUEUE = "com.prime.player.queue"

/**
 * A MediaItem impl of [ROOT_QUEUE]
 */
private val BROWSER_ROOT_QUEUE =
    MediaItem.Builder()
        .setMediaId(ROOT_QUEUE)
        .setMediaMetadata(
            MediaMetadata.Builder().setIsBrowsable(true)
                .setIsPlayable(false)
                .build()
        )
        .build()

/*Different keys for saving the state*/
private val PREF_KEY_SHUFFLE_MODE = booleanPreferenceKey("_shuffle", false)
private val PREF_KEY_REPEAT_MODE = intPreferenceKey("_repeat_mode", Player.REPEAT_MODE_OFF)
private val PREF_KEY_INDEX = intPreferenceKey("_index", C.INDEX_UNSET)
private val PREF_KEY_BOOKMARK = longPreferenceKey("_bookmark", C.TIME_UNSET)
private val PREF_KEY_ORDERS = stringPreferenceKey(
    "_orders",
    IntArray(0),
    object : StringSaver<IntArray> {
        override fun restore(value: String): IntArray {
            val arr = JSONArray(value)
            return IntArray(arr.length()) {
                arr.getInt(it)
            }
        }

        override fun save(value: IntArray): String {
            val arr = JSONArray(value)
            return arr.toString()
        }
    }
)
private val PREF_KEY_RECENT_PLAYLIST_LIMIT =
    intPreferenceKey( "_max_recent_size", defaultValue = 50)

/**
 * A simple extension fun that adds items to recent.
 */
private suspend fun Playlists.addToRecent(item: MediaItem, limit: Long) {

    val playlistId =
        get(PLAYLIST_RECENT)?.id ?: insert(Playlist(name = PLAYLIST_RECENT))
    // here two cases arise
    // case 1 the member already exists:
    // in this case we just have to update the order and nothing else
    // case 2 the member needs to be inserted.
    // In both cases the playlist's dateModified needs to be updated.
    val playlist = get(playlistId)!!
    update(playlist = playlist.copy(dateModified = System.currentTimeMillis()))

    val member = get(playlistId, item.requestMetadata.mediaUri.toString())

    when (member != null) {
        // exists
        true -> {
            //localDb.members.update(member.copy(order = 0))
            // updating the member doesn't work as expected.
            // localDb.members.delete(member)
            update(member = member.copy(order = 0))
        }

        else -> {
            // delete above member
            delete(playlistId, limit)
            insert(Member(item, playlistId, 0))
        }
    }
}

/**
 * An extension fun that saves [items] to queue replacing the old ones.
 */
private suspend fun Playlists.save(items: List<MediaItem>) {
    val id = get(PLAYLIST_QUEUE)?.id ?: insert(
        Playlist(name = PLAYLIST_QUEUE)
    )

    // delete all old
    delete(id, 0)
    var order = 0
    val members = items.map { Member(it, id, order++) }
    insert(members)
}

/**
 * The Playback Service class using media3.
 */
@AndroidEntryPoint
class Playback : MediaLibraryService(), Callback, Player.Listener {
    companion object {

        /**
         * The name of Global [Playlists]
         */
        @JvmField
        val PLAYLIST_FAVOURITE = com.prime.media.core.playback.PLAYLIST_FAVOURITE

        /**
         * The name of Global [Playlists]
         */
        @JvmField
        val PLAYLIST_RECENT = com.prime.media.core.playback.PLAYLIST_RECENT

        /**
         * The name of Global [Playlists]
         */
        @JvmField
        val PLAYLIST_QUEUE = com.prime.media.core.playback.PLAYLIST_QUEUE

        /**
         * The root of the playing queue
         */
        const val ROOT_QUEUE = com.prime.media.core.playback.ROOT_QUEUE

        @JvmField
        val PREF_KEY_RECENT_PLAYLIST_LIMIT = com.prime.media.core.playback.PREF_KEY_RECENT_PLAYLIST_LIMIT
    }

    /**
     * The pending intent for the underlying activity.
     */
    private val activity by lazy {
        // create the activity intent and set with session
        TaskStackBuilder.create(this).run {
            addNextIntent(Intent(this@Playback, MainActivity::class.java))
            val immutableFlag = if (Build.VERSION.SDK_INT >= 23) FLAG_IMMUTABLE else 0
            getPendingIntent(0, immutableFlag or FLAG_UPDATE_CURRENT)
        }
    }
    private val player: Player by lazy {
        ExoPlayer.Builder(this).setAudioAttributes(
            // set audio attributes to it
            AudioAttributes.Builder().setContentType(AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA).build(), true
        ).setHandleAudioBecomingNoisy(true).build()
    }
    private val session: MediaLibrarySession by lazy {
        runBlocking { onRestoreSavedState() }
        player.addListener(this)
        MediaLibrarySession.Builder(this, player, this).setSessionActivity(activity).build()
    }

    // This helps in implement the state of this service using persistent storage .
    @Inject
    lateinit var preferences: Preferences

    @Inject
    lateinit var playlists: Playlists

    /**
     * Restore the saved state of this service.
     */
    @SuppressLint("UnsafeOptInUsageError")
    private suspend fun onRestoreSavedState() {
        with(player) {
            shuffleModeEnabled = preferences.value(PREF_KEY_SHUFFLE_MODE)
            repeatMode = preferences.value(PREF_KEY_REPEAT_MODE)
            setMediaItems(playlists.getMembers(PLAYLIST_QUEUE).map(Playlist.Member::toMediaSource))
            // set saved shuffled.
            (this as ExoPlayer).setShuffleOrder(
                DefaultShuffleOrder(preferences.value(PREF_KEY_ORDERS), Random.nextLong())
            )

            // seek to current position
            val index = preferences.value(PREF_KEY_INDEX)
            if (index != C.INDEX_UNSET) seekTo(index, preferences.value(PREF_KEY_BOOKMARK))
        }
    }

    // here return the session
    override fun onGetSession(controllerInfo: ControllerInfo) = session

    // return the items to add to player
    override fun onAddMediaItems(
        mediaSession: MediaSession, controller: ControllerInfo, mediaItems: MutableList<MediaItem>
    ): ListenableFuture<List<MediaItem>> =
        Futures.immediateFuture(mediaItems.map(MediaItem::toMediaSource))

    // FixMe: Don't currently know how this affects.
    @SuppressLint("UnsafeOptInUsageError")
    override fun onGetLibraryRoot(
        session: MediaLibrarySession, browser: ControllerInfo, params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(LibraryResult.ofItem(BROWSER_ROOT_QUEUE, params))

    // return the individual media item pointed out by the [mediaId]
    override fun onGetItem(
        session: MediaLibrarySession, browser: ControllerInfo, mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val item = player.mediaItems.find { it.mediaId == mediaId }
        val result = if (item == null) LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
        else LibraryResult.ofItem(item, /* params = */ null)
        return Futures.immediateFuture(result)
    }

    //TODO: Find how can i return he playing queue with upcoming items only.
    override fun onSubscribe(
        session: MediaLibrarySession,
        browser: ControllerInfo,
        parentId: String,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        val children = when (parentId) {
            ROOT_QUEUE -> player.queue
            else -> return Futures.immediateFuture(
                LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            )
        }
        session.notifyChildrenChanged(browser, parentId, children.size, params)
        return Futures.immediateFuture(LibraryResult.ofVoid())
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        //TODO(Maybe add support for paging.)
        val children = when (parentId) {
            ROOT_QUEUE -> player.queue
            else -> return Futures.immediateFuture(
                LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            )
        }
        return Futures.immediateFuture(LibraryResult.ofItemList(children, params))
    }

    // release resources
    // cancel scope etc.
    override fun onDestroy() {
        player.release()
        session.release()
        super.onDestroy()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // save current index in preference
        preferences[PREF_KEY_INDEX] = player.currentMediaItemIndex
        if (mediaItem != null) {
            val limit = preferences.value(PREF_KEY_RECENT_PLAYLIST_LIMIT)
            GlobalScope.launch { playlists.addToRecent(mediaItem, limit.toLong()) }
            session.notifyChildrenChanged(ROOT_QUEUE, 0, null)
        }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        preferences[PREF_KEY_SHUFFLE_MODE] = shuffleModeEnabled
        session.notifyChildrenChanged(ROOT_QUEUE, 0, null)
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        preferences[PREF_KEY_REPEAT_MODE] = repeatMode
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        // construct list and update.
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
            GlobalScope.launch(Dispatchers.Main) { playlists.save(player.mediaItems) }
            preferences[PREF_KEY_ORDERS] = player.orders
            session.notifyChildrenChanged(ROOT_QUEUE, 0, null)
        }
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        super.onUpdateNotification(session, startInForegroundRequired)
        // send intent for updating the widget also,
        val intent = Intent(this, Widget::class.java)
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
        val ids = AppWidgetManager.getInstance(application)
            .getAppWidgetIds(ComponentName(application, Widget::class.java))
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        sendBroadcast(intent)
    }

    override fun onPlayerError(error: PlaybackException) {
        // make a simple toast
        Toast.makeText(this, getString(R.string.unplayable_file), Toast.LENGTH_SHORT).show()
        //player.seekToNextMediaItem()
    }
}