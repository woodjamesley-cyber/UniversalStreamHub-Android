package com.universalstreamhub.app.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.ui.text.font.FontWeight
import com.universalstreamhub.app.core.AppGraph
import com.universalstreamhub.app.data.remote.MediaSummaryDto
import com.universalstreamhub.app.ui.theme.HubAccent
import com.universalstreamhub.app.ui.theme.HubBlack
import com.universalstreamhub.app.ui.theme.HubGold
import com.universalstreamhub.app.ui.theme.HubMuted
import com.universalstreamhub.app.ui.theme.HubTheme
import com.universalstreamhub.app.util.formatPlaybackTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Native Media3 ExoPlayer screen. Used only for streams the hub is authorised to
 * play natively (the free public-domain catalog). Licensed provider titles never
 * reach this activity - they are handed off to the provider's own app.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private var exoPlayer: ExoPlayer? = null
    private var media: MediaSummaryDto? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val position = intent.getLongExtra(EXTRA_POSITION, 0L)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val poster = intent.getStringExtra(EXTRA_POSTER)
        val sourceId = intent.getStringExtra(EXTRA_ID).orEmpty()
        val type = intent.getStringExtra(EXTRA_TYPE) ?: "movie"

        if (url.isBlank()) {
            finish()
            return
        }

        media = MediaSummaryDto(id = sourceId, type = type, title = title, posterUrl = poster, playback = "NATIVE")

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 60_000, 1_500, 3_000)
            .build()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this)))
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply {
                setWakeMode(C.WAKE_MODE_NETWORK)
                setMediaItem(MediaItem.fromUri(url), position)
                prepare()
                playWhenReady = true
            }
        exoPlayer = player

        setContent {
            HubTheme {
                PlayerScreen(
                    player = player,
                    title = title,
                    onBack = { finish() },
                    onSaveProgress = { saveProgress(false) },
                )
            }
        }

        lifecycleScope.launch {
            while (true) {
                delay(5_000)
                saveProgress(false)
            }
        }
    }

    private fun saveProgress(completed: Boolean) {
        val player = exoPlayer ?: return
        val current = media ?: return
        if (player.duration <= 0) return
        lifecycleScope.launch {
            AppGraph.container.library.recordProgress(
                media = current,
                positionMs = player.currentPosition,
                durationMs = player.duration,
                completed = completed || player.playbackState == Player.STATE_ENDED,
            )
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && exoPlayer?.isPlaying == true) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build(),
                )
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    }

    override fun onPause() {
        super.onPause()
        saveProgress(false)
    }

    override fun onStop() {
        super.onStop()
        saveProgress(false)
    }

    override fun onDestroy() {
        saveProgress(false)
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_POSTER = "extra_poster"
        private const val EXTRA_ID = "extra_id"
        private const val EXTRA_TYPE = "extra_type"
        private const val EXTRA_POSITION = "extra_position"

        fun start(
            context: Context,
            url: String,
            media: MediaSummaryDto,
            startPositionMs: Long = 0L,
        ) {
            val intent = Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, media.title)
                putExtra(EXTRA_POSTER, media.posterUrl)
                putExtra(EXTRA_ID, media.id)
                putExtra(EXTRA_TYPE, media.type)
                putExtra(EXTRA_POSITION, startPositionMs)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerScreen(
    player: ExoPlayer,
    title: String,
    onBack: () -> Unit,
    onSaveProgress: () -> Unit,
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var buffering by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var speed by remember { mutableStateOf(1f) }
    var showSpeed by remember { mutableStateOf(false) }
    var showSubtitles by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }
    var subtitleTracks by remember { mutableStateOf(listOf<Tracks.Group>()) }
    var audioTracks by remember { mutableStateOf(listOf<Tracks.Group>()) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) duration = player.duration.coerceAtLeast(0)
                if (state == Player.STATE_ENDED) onSaveProgress()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                errorText = error.message ?: "This stream could not be played."
            }

            override fun onTracksChanged(tracks: Tracks) {
                val groups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT || it.type == C.TRACK_TYPE_AUDIO }
                subtitleTracks = groups.filter { it.type == C.TRACK_TYPE_TEXT }
                audioTracks = groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.coerceAtLeast(0)
            delay(500)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(HubBlack)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    setControllerShowTimeoutMs(3_500)
                    keepScreenOn = true
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(Color(0x66000000))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
                Text(
                    "${formatPlaybackTime(position)} / ${formatPlaybackTime(duration)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = HubMuted,
                )
            }
            if (buffering) {
                Text("Buffering", style = MaterialTheme.typography.labelSmall, color = HubGold)
            }
        }

        errorText?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .background(Color(0xCC101218))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Playback problem", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(message, style = MaterialTheme.typography.bodySmall, color = HubMuted)
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = {
                    errorText = null
                    player.prepare()
                    player.play()
                }) { Text("Retry", color = HubAccent) }
                TextButton(onClick = onBack) { Text("Close", color = HubMuted) }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color(0x66000000))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { showSpeed = true }) {
                Icon(Icons.Rounded.Speed, contentDescription = "Playback speed", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { showSubtitles = true }, enabled = subtitleTracks.isNotEmpty()) {
                Icon(
                    Icons.Rounded.Subtitles,
                    contentDescription = "Subtitles",
                    tint = if (subtitleTracks.isEmpty()) HubMuted else Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = { showAudio = true }, enabled = audioTracks.isNotEmpty()) {
                Icon(
                    Icons.Rounded.Audiotrack,
                    contentDescription = "Audio track",
                    tint = if (audioTracks.isEmpty()) HubMuted else Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${speedLabel(speed)}x",
                style = MaterialTheme.typography.labelSmall,
                color = HubMuted,
                fontWeight = FontWeight.Bold,
            )
        }

        DropdownMenu(expanded = showSpeed, onDismissRequest = { showSpeed = false }) {
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                DropdownMenuItem(
                    text = { Text("${speedLabel(value)}x") },
                    onClick = {
                        speed = value
                        player.setPlaybackSpeed(value)
                        showSpeed = false
                    },
                )
            }
        }

        DropdownMenu(expanded = showSubtitles, onDismissRequest = { showSubtitles = false }) {
            DropdownMenuItem(
                text = { Text("Off") },
                onClick = {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                    showSubtitles = false
                },
            )
            subtitleTracks.forEach { group ->
                for (index in 0 until group.length) {
                    val track = group.getTrackFormat(index)
                    val language = track.label ?: track.language ?: "Track ${index + 1}"
                    DropdownMenuItem(
                        text = { Text(language) },
                        onClick = {
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                                .build()
                            showSubtitles = false
                        },
                    )
                }
            }
        }

        DropdownMenu(expanded = showAudio, onDismissRequest = { showAudio = false }) {
            audioTracks.forEach { group ->
                for (index in 0 until group.length) {
                    val track = group.getTrackFormat(index)
                    val language = track.label ?: track.language ?: "Track ${index + 1}"
                    DropdownMenuItem(
                        text = { Text(language) },
                        onClick = {
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                                .build()
                            showAudio = false
                        },
                    )
                }
            }
        }

        if (!isPlaying && errorText == null) {
            Text(
                "Paused",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(16.dp),
            )
        }
    }
    // context is used only for immersive mode setup on older devices
    LaunchedEffect(context) { Unit }
}

private fun speedLabel(value: Float): String = when (value) {
    0.5f -> "0.5"
    0.75f -> "0.75"
    1f -> "1.0"
    1.25f -> "1.25"
    1.5f -> "1.5"
    2f -> "2.0"
    else -> value.toString()
}
