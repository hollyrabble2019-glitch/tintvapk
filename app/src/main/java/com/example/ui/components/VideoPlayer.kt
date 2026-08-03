package com.example.ui.components

import android.app.Activity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.PlayerView
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import com.example.data.ChannelEntity
import com.example.ui.theme.LightTurquoise
import kotlinx.coroutines.delay
import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import android.webkit.WebChromeClient
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants

data class StreamInfo(
    val name: String,
    val player: String,
    val url: String
)

@Composable
fun VideoPlayer(
    channel: ChannelEntity,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    categoryChannels: List<ChannelEntity> = emptyList(),
    liveMatches: List<ChannelEntity> = emptyList()
) {
    var currentChannel by remember(channel) { mutableStateOf(channel) }

    val isLiveMatch = remember(currentChannel) {
        currentChannel.country.equals("Live Matches", ignoreCase = true) ||
                currentChannel.category.equals("Live Matches", ignoreCase = true)
    }

    val streamList = remember(currentChannel.streamsJson) {
        parseStreamList(currentChannel.streamsJson)
    }

    var selectedStreamIndex by remember {
        mutableStateOf(if (isLiveMatch && streamList.isNotEmpty()) 0 else -1)
    }

    var allStreamsFailed by remember { mutableStateOf(false) }
    var isPlaybackReady by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(currentChannel) {
        selectedStreamIndex = if (isLiveMatch && streamList.isNotEmpty()) 0 else -1
        allStreamsFailed = false
        isPlaybackReady = false
        isPlaying = false
    }

    val handleStreamSelect = { index: Int ->
        selectedStreamIndex = index
        allStreamsFailed = false
        isPlaybackReady = false
        isPlaying = false
    }

    val playlist = remember(categoryChannels, liveMatches, currentChannel) {
        when {
            categoryChannels.isNotEmpty() -> categoryChannels
            liveMatches.isNotEmpty() -> liveMatches
            else -> listOf(currentChannel)
        }
    }

    val currentChannelIndex = remember(currentChannel, playlist) {
        playlist.indexOfFirst { it.id == currentChannel.id }
    }

    val nextChannelIndex = remember(currentChannelIndex, playlist) {
        if (currentChannelIndex != -1 && playlist.isNotEmpty()) {
            if (currentChannelIndex == playlist.size - 1) 0 else currentChannelIndex + 1
        } else -1
    }

    val prevChannelIndex = remember(currentChannelIndex, playlist) {
        if (currentChannelIndex != -1 && playlist.isNotEmpty()) {
            if (currentChannelIndex == 0) playlist.size - 1 else currentChannelIndex - 1
        } else -1
    }

    var showChannelOverlay by remember { mutableStateOf(false) }

    LaunchedEffect(currentChannel) {
        showChannelOverlay = true
        delay(2000)
        showChannelOverlay = false
    }

    val topFocusRequester = remember { FocusRequester() }

    LaunchedEffect(currentChannel) {
        try { topFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(topFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (nextChannelIndex != -1) {
                                currentChannel = playlist[nextChannelIndex]
                                true
                            } else false
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (prevChannelIndex != -1) {
                                currentChannel = playlist[prevChannelIndex]
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        AnimatedContent(
            targetState = currentChannel,
            transitionSpec = {
                fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) togetherWith
                        fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
            },
            label = "channel_transition",
            modifier = Modifier.fillMaxSize()
        ) { activeChannel ->

            val activeIsLiveMatch = remember(activeChannel) {
                activeChannel.country.equals("Live Matches", ignoreCase = true) ||
                        activeChannel.category.equals("Live Matches", ignoreCase = true)
            }

            val activeStreamList = remember(activeChannel.streamsJson) {
                parseStreamList(activeChannel.streamsJson)
            }

            // ==================== LIVE MATCH ====================
            if (activeIsLiveMatch) {
                val activeStream = if (selectedStreamIndex >= 0 && selectedStreamIndex < activeStreamList.size) {
                    activeStreamList[selectedStreamIndex]
                } else null

                RenderPlayer(
                    stream = activeStream,
                    channel = activeChannel,
                    onBack = onBack,
                    isLiveMatch = true,
                    streamList = activeStreamList,
                    selectedStreamIndex = selectedStreamIndex,
                    onStreamSelect = handleStreamSelect,
                    allStreamsFailed = allStreamsFailed,
                    onPlaybackReady = { isPlaybackReady = true },
                    onPlayingStateChanged = { isPlaying = it },
                    onPlaybackError = { allStreamsFailed = true }
                )
            }
            // ==================== NORMAL CATEGORY ====================
            else {
                var selectedStream by remember { mutableStateOf<StreamInfo?>(null) }

                if (activeStreamList.isNotEmpty() && selectedStream == null) {
                    ServerSelectionDialog(
                        channelName = activeChannel.name,
                        streams = activeStreamList,
                        onSelect = { selectedStream = it },
                        onBack = onBack
                    )
                } else {
                    val streamToPlay = selectedStream ?: activeStreamList.firstOrNull()

                    RenderPlayer(
                        stream = streamToPlay,
                        channel = activeChannel,
                        onBack = {
                            if (activeStreamList.size > 1) selectedStream = null
                            else onBack()
                        },
                        isLiveMatch = false,
                        streamList = activeStreamList,
                        selectedStreamIndex = activeStreamList.indexOf(streamToPlay).coerceAtLeast(0),
                        onStreamSelect = { index ->
                            selectedStream = activeStreamList.getOrNull(index)
                        },
                        allStreamsFailed = allStreamsFailed,
                        onPlaybackReady = { isPlaybackReady = true },
                        onPlayingStateChanged = { isPlaying = it },
                        onPlaybackError = { allStreamsFailed = true }
                    )
                }
            }
        }

        ChannelInfoOverlay(
            channel = currentChannel,
            channelIndex = currentChannelIndex,
            totalChannels = playlist.size,
            visible = showChannelOverlay
        )
    }
}

// ============================================================
// RENDER PLAYER (القلب ديال كلشي)
// ============================================================

@Composable
private fun RenderPlayer(
    stream: StreamInfo?,
    channel: ChannelEntity,
    onBack: () -> Unit,
    isLiveMatch: Boolean,
    streamList: List<StreamInfo>,
    selectedStreamIndex: Int,
    onStreamSelect: (Int) -> Unit,
    allStreamsFailed: Boolean,
    onPlaybackReady: () -> Unit,
    onPlayingStateChanged: (Boolean) -> Unit,
    onPlaybackError: () -> Unit
) {
    when (getStreamType(stream)) {
        "youtube" -> {
            val videoId = extractYoutubeVideoId(stream?.url ?: "") ?: ""
            if (videoId.isNotBlank()) {
                YouTubePlayerComposable(
                    videoId = videoId,
                    channelName = channel.name,
                    onBack = onBack,
                    isLiveMatch = isLiveMatch,
                    streamList = streamList,
                    selectedStreamIndex = selectedStreamIndex,
                    onStreamSelect = onStreamSelect,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                IframePlayer(
                    url = stream?.url ?: "",
                    channelName = channel.name,
                    onBack = onBack,
                    isLiveMatch = isLiveMatch,
                    streamList = streamList,
                    selectedStreamIndex = selectedStreamIndex,
                    onStreamSelect = onStreamSelect,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        "iframe" -> {
            IframePlayer(
                url = stream?.url ?: "",
                channelName = channel.name,
                onBack = onBack,
                isLiveMatch = isLiveMatch,
                streamList = streamList,
                selectedStreamIndex = selectedStreamIndex,
                onStreamSelect = onStreamSelect,
                modifier = Modifier.fillMaxSize()
            )
        }

        else -> {
            val resolvedChannel = if (stream != null) {
                channel.copy(streamUrl = stream.url)
            } else {
                channel
            }

            ExoVideoPlayer(
                channel = resolvedChannel,
                onBack = onBack,
                isLiveMatch = isLiveMatch,
                streamList = streamList,
                selectedStreamIndex = selectedStreamIndex,
                onStreamSelect = onStreamSelect,
                allStreamsFailed = allStreamsFailed,
                onPlaybackReady = onPlaybackReady,
                onPlayingStateChanged = onPlayingStateChanged,
                onPlaybackError = onPlaybackError,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ============================================================
// HELPER FUNCTIONS
// ============================================================

private fun parseStreamList(streamsJson: String?): List<StreamInfo> {
    val list = mutableListOf<StreamInfo>()
    if (streamsJson.isNullOrEmpty()) return list

    try {
        val jsonArray = org.json.JSONArray(streamsJson)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            if (obj.has("enabled") && !obj.optBoolean("enabled", true)) continue

            val sName = obj.optString("name", obj.optString("title", "Server ${i + 1}"))
            val sPlayer = obj.optString("player", obj.optString("type", obj.optString("format", "")))
            val sUrl = obj.optString("url", obj.optString("link", obj.optString("src", "")))

            if (sUrl.isNotBlank()) {
                list.add(StreamInfo(name = sName, player = sPlayer, url = sUrl))
            }
        }
    } catch (_: Exception) {}
    return list
}

private fun getStreamType(stream: StreamInfo?): String {
    if (stream == null) return "unknown"

    val player = stream.player.lowercase().trim()
    val url = stream.url.lowercase().trim()

    // YouTube
    if (url.contains("youtube.com") || url.contains("youtu.be") ||
        player == "youtube" ||
        (url.length in 10..12 && !url.contains("/") && !url.contains("http"))) {
        return "youtube"
    }

    // iframe (Dailymotion + أي player)
    if (player in listOf("iframe", "embed", "web", "html", "webview", "dailymotion") ||
        url.contains("dailymotion.com") ||
        url.contains("player.html") ||
        url.contains("player.php") ||
        url.contains("/embed/") ||
        url.contains("vimeo.com") ||
        url.contains("player.vimeo") ||
        url.contains("ok.ru") ||
        url.contains("streamable.com") ||
        url.startsWith("<iframe")) {
        return "iframe"
    }

    // m3u8
    if (player in listOf("m3u8", "hls") ||
        url.contains(".m3u8") || url.contains("format=m3u8") || url.contains("type=m3u8")) {
        return "m3u8"
    }

    // ts
    if (player in listOf("ts", "mpegts") ||
        url.contains(".ts") || url.contains("format=ts") || url.contains("type=ts")) {
        return "ts"
    }

    return "exo"
}

private fun extractYoutubeVideoId(url: String): String? {
    val trimmed = url.trim()
    if (trimmed.length in 10..12 && !trimmed.contains("/") && !trimmed.contains("http") && !trimmed.contains(".")) {
        return trimmed
    }
    return when {
        "v=" in trimmed -> trimmed.substringAfter("v=").substringBefore("&").substringBefore("#")
        "youtu.be/" in trimmed -> trimmed.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
        "/embed/" in trimmed -> trimmed.substringAfter("/embed/").substringBefore("?").substringBefore("&")
        "/shorts/" in trimmed -> trimmed.substringAfter("/shorts/").substringBefore("?").substringBefore("&")
        else -> null
    }?.takeIf { it.isNotBlank() }
}

private fun isIframeStream(stream: StreamInfo?): Boolean {
    val type = getStreamType(stream)
    return type == "iframe" || type == "youtube"
}

private fun formatIframeUrl(inputUrl: String): String {
    val trimmed = inputUrl.trim()
    if (trimmed.contains("<iframe", ignoreCase = true)) {
        val srcRegex = """src=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
        val match = srcRegex.find(trimmed)
        if (match != null) return match.groupValues[1]
    }
    if (trimmed.contains("youtube.com/watch", ignoreCase = true) || trimmed.contains("youtu.be/", ignoreCase = true)) {
        val videoId = extractYoutubeVideoId(trimmed)
        if (!videoId.isNullOrBlank()) {
            return "https://www.youtube.com/embed/$videoId?autoplay=1&fs=1&enablejsapi=1"
        }
    }
    return trimmed
}

private fun loadIframeContent(webView: android.webkit.WebView, inputUrl: String) {
    val trimmed = inputUrl.trim()
    if (trimmed.startsWith("<") || trimmed.contains("<iframe", ignoreCase = true)) {
        val htmlDoc = if (!trimmed.lowercase().contains("<html>")) {
            """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <meta name="referrer" content="strict-origin-when-cross-origin">
                <style>
                    * { margin:0; padding:0; box-sizing:border-box; }
                    body, html { width:100%; height:100%; background:#000; overflow:hidden; }
                    iframe { width:100% !important; height:100% !important; border:none !important; }
                </style>
            </head>
            <body>$trimmed</body>
            </html>
            """.trimIndent()
        } else trimmed
        webView.loadDataWithBaseURL("https://www.dailymotion.com", htmlDoc, "text/html", "UTF-8", null)
    } else if (trimmed.isNotBlank()) {
        webView.loadUrl(trimmed)
    }
}

// ============================================================
// YOUTUBE PLAYER
// ============================================================

@Composable
fun YouTubePlayerComposable(
    videoId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    channelName: String = "",
    isLiveMatch: Boolean = false,
    streamList: List<StreamInfo> = emptyList(),
    selectedStreamIndex: Int = -1,
    onStreamSelect: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasError by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (!hasError) {
            AndroidView(
                factory = { ctx ->
                    YouTubePlayerView(ctx).apply {
                        lifecycleOwner.lifecycle.addObserver(this)
                        enableAutomaticInitialization = false
                        val options = IFramePlayerOptions.Builder()
                            .controls(1).fullscreen(1).autoplay(1).rel(0).build()
                        initialize(object : AbstractYouTubePlayerListener() {
                            override fun onReady(youTubePlayer: YouTubePlayer) {
                                youTubePlayer.loadVideo(videoId, 0f)
                            }
                            override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                                hasError = true
                            }
                        }, options)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { it.release() }
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Warning, null, tint = Color.Red, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text("Vidéo non disponible dans l'application", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("vnd.youtube:$videoId"))
                            intent.setPackage("com.google.android.youtube")
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=$videoId")))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Regarder sur YouTube", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Top Bar
        PlayerTopBar(
            channelName = channelName,
            onBack = onBack,
            isLiveMatch = isLiveMatch,
            streamList = streamList,
            selectedStreamIndex = selectedStreamIndex,
            onStreamSelect = onStreamSelect
        )
    }
}

// ============================================================
// EXOPLAYER
// ============================================================

@OptIn(UnstableApi::class)
@Composable
fun ExoVideoPlayer(
    channel: ChannelEntity,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isLiveMatch: Boolean = false,
    streamList: List<StreamInfo> = emptyList(),
    selectedStreamIndex: Int = -1,
    onStreamSelect: (Int) -> Unit = {},
    allStreamsFailed: Boolean = false,
    onPlaybackReady: () -> Unit = {},
    onPlayingStateChanged: (Boolean) -> Unit = {},
    onPlaybackError: () -> Unit = {},
    onControlsVisibilityChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { try { focusRequester.requestFocus() } catch (_: Exception) {} }

    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    var isBuffering by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var retryCount by remember { mutableStateOf(0) }

    val exoPlayer = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2500, 15000, 1500, 2000).build()
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setAllowVideoNonSeamlessAdaptiveness(true))
        }
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .build().apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }

    LaunchedEffect(channel.streamUrl, retryCount) {
        hasError = false
        isBuffering = true
        try {
            var resolvedUrl = channel.streamUrl
            if (resolvedUrl.startsWith("bein_id:")) {
                val streamId = resolvedUrl.substringAfter("bein_id:")
                val prefs = context.getSharedPreferences("app_config", android.content.Context.MODE_PRIVATE)
                val server = prefs.getString("server", "http://t77ert.top:8080")?.trim() ?: "http://t77ert.top:8080"
                val username = prefs.getString("username", "190449942528177")?.trim() ?: "190449942528177"
                val password = prefs.getString("password", "UCoJgCCNnaK87n8")?.trim() ?: "UCoJgCCNnaK87n8"
                val cleanServer = if (server.endsWith("/")) server.dropLast(1) else server
                resolvedUrl = "$cleanServer/live/$username/$password/$streamId.m3u8"
            }

            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)

            val mediaItemBuilder = MediaItem.Builder().setUri(resolvedUrl)
            val lower = resolvedUrl.lowercase()
            if (lower.contains("m3u8")) mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            else if (lower.contains(".ts")) mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP2T)

            val extractorsFactory = DefaultExtractorsFactory()
                .setTsExtractorFlags(
                    DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                            DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
                )

            val mediaSource = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
                .createMediaSource(mediaItemBuilder.build())

            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
        } catch (e: Exception) {
            hasError = true
            isBuffering = false
            onPlaybackError()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                isPlaying = exoPlayer.isPlaying
                onPlayingStateChanged(exoPlayer.isPlaying)
                if (state == Player.STATE_READY) {
                    hasError = false
                    retryCount = 0
                    onPlaybackReady()
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                hasError = true
                isBuffering = false
                onPlaybackError()
                if (retryCount < 2) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ retryCount++ }, 4000)
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                onPlayingStateChanged(playing)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(5000)
            showControls = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { showControls = true; false }
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                showControls = !showControls
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LightTurquoise, modifier = Modifier.size(56.dp))
            }
        }

        if (hasError || (isLiveMatch && allStreamsFailed)) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.85f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Channel unavailable", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { retryCount++ }, colors = ButtonDefaults.buttonColors(containerColor = LightTurquoise)) {
                        Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f))) {
                PlayerTopBar(
                    channelName = channel.name,
                    onBack = onBack,
                    isLiveMatch = isLiveMatch,
                    streamList = streamList,
                    selectedStreamIndex = selectedStreamIndex,
                    onStreamSelect = onStreamSelect
                )
            }
        }
    }
}

// ============================================================
// IFRAME PLAYER
// ============================================================

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun IframePlayer(
    url: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    channelName: String = "",
    isLiveMatch: Boolean = false,
    streamList: List<StreamInfo> = emptyList(),
    selectedStreamIndex: Int = -1,
    onStreamSelect: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<android.webkit.WebView?>(null) }
    val formattedUrl = remember(url) { formatIframeUrl(url) }

    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    webViewRef = this
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    }
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                            isLoading = false
                        }
                        override fun onReceivedError(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                            if (request?.isForMainFrame == true) {
                                isError = true
                                isLoading = false
                            }
                        }
                    }
                    loadIframeContent(this, formattedUrl)
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = {
                it.stopLoading()
                it.destroy()
            }
        )

        if (isLoading) {
            CircularProgressIndicator(color = LightTurquoise, modifier = Modifier.align(Alignment.Center))
        }

        if (isError) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Warning, null, tint = Color.Red, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("Failed to load stream", color = Color.White)
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    isError = false
                    isLoading = true
                    webViewRef?.loadUrl(formattedUrl)
                }, colors = ButtonDefaults.buttonColors(containerColor = LightTurquoise)) {
                    Text("Retry", color = Color.Black)
                }
            }
        }

        PlayerTopBar(
            channelName = channelName,
            onBack = onBack,
            isLiveMatch = isLiveMatch,
            streamList = streamList,
            selectedStreamIndex = selectedStreamIndex,
            onStreamSelect = onStreamSelect
        )
    }
}

// ============================================================
// TOP BAR مشترك
// ============================================================

@Composable
private fun PlayerTopBar(
    channelName: String,
    onBack: () -> Unit,
    isLiveMatch: Boolean,
    streamList: List<StreamInfo>,
    selectedStreamIndex: Int,
    onStreamSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(0.85f), Color.Transparent)))
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.background(Color.Black.copy(0.6f), CircleShape)
        ) {
            Icon(Icons.Default.ArrowBack, null, tint = Color.White)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(channelName, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            if (isLiveMatch && streamList.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    streamList.forEachIndexed { index, _ ->
                        val isSelected = index == selectedStreamIndex
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(if (isSelected) LightTurquoise else Color.White.copy(0.15f), CircleShape)
                                .clickable { onStreamSelect(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${index + 1}", color = if (isSelected) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// SERVER SELECTION DIALOG
// ============================================================

@Composable
fun ServerSelectionDialog(
    channelName: String,
    streams: List<StreamInfo>,
    onSelect: (StreamInfo) -> Unit,
    onBack: () -> Unit
) {
    val focusRequesters = remember { List(streams.size) { FocusRequester() } }

    LaunchedEffect(Unit) {
        if (focusRequesters.isNotEmpty()) {
            try { focusRequesters[0].requestFocus() } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.9f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onBack() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth(0.85f)
                .padding(24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SELECT SERVER", color = LightTurquoise, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(channelName, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(24.dp))

                streams.forEachIndexed { index, stream ->
                    var isFocused by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .focusRequester(focusRequesters[index])
                            .onFocusChanged { isFocused = it.isFocused }
                            .focusable()
                            .clickable { onSelect(stream) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isFocused) LightTurquoise.copy(0.15f) else Color.White.copy(0.05f)
                        )
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stream.name, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(
                                stream.player.ifBlank { getStreamType(stream) }.uppercase(),
                                color = Color.White,
                                fontSize = 10.sp,
                                modifier = Modifier
                                    .background(
                                        if (isIframeStream(stream)) Color(0xFFD32F2F) else Color(0xFF388E3C),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth(0.6f)) {
                    Text("Cancel")
                }
            }
        }
    }
}

// ============================================================
// CHANNEL INFO OVERLAY
// ============================================================

@Composable
fun ChannelInfoOverlay(
    channel: ChannelEntity,
    channelIndex: Int,
    totalChannels: Int,
    visible: Boolean
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 }
    ) {
        Box(Modifier.fillMaxSize().padding(bottom = 48.dp, start = 32.dp), contentAlignment = Alignment.BottomStart) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(0.85f)),
                border = BorderStroke(1.dp, LightTurquoise.copy(0.6f)),
                modifier = Modifier.width(340.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!channel.logo.isNullOrEmpty()) {
                        AsyncImage(
                            model = channel.logo,
                            contentDescription = null,
                            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(Icons.Default.PlayArrow, null, tint = LightTurquoise, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(channel.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("Channel ${channelIndex + 1} of $totalChannels", color = LightTurquoise, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}