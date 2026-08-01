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
import androidx.compose.ui.platform.LocalView
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
import androidx.media3.exoplayer.hls.HlsMediaSource
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
import androidx.compose.ui.zIndex
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.graphics.Bitmap
import androidx.compose.ui.unit.sp

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
    val context = LocalContext.current
    
    // We maintain a local currentChannel state initialized to the starting channel
    var currentChannel by remember(channel) { mutableStateOf(channel) }

    // Check if this channel is a Live Match
    val isLiveMatch = remember(currentChannel) {
        currentChannel.country.equals("Live Matches", ignoreCase = true) || 
        currentChannel.category.equals("Live Matches", ignoreCase = true)
    }

    // Parse the stream list if streamsJson is available
    val streamList = remember(currentChannel.streamsJson) {
        val list = mutableListOf<StreamInfo>()
        if (!currentChannel.streamsJson.isNullOrEmpty()) {
            try {
                val jsonArray = org.json.JSONArray(currentChannel.streamsJson)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        StreamInfo(
                            name = obj.optString("name", "Server ${i + 1}"),
                            player = obj.optString("player", "m3u8"),
                            url = obj.optString("url", "")
                        )
                    )
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        list
    }

    // State for selected stream index
    // If it's a Live Match and we have streams, automatically select index 0 immediately!
    var selectedStreamIndex by remember {
        mutableStateOf(if (isLiveMatch && streamList.isNotEmpty()) 0 else -1)
    }

    // Whether automatic switching is active (stops if user manually switches)
    var isAutoSwitchingActive by remember {
        mutableStateOf(isLiveMatch && streamList.isNotEmpty())
    }

    // Whether all streams have failed
    var allStreamsFailed by remember {
        mutableStateOf(false)
    }

    // Track state of ExoPlayer / Web playback
    var isPlaybackReady by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }

    // Automatically reset state when currentChannel changes
    LaunchedEffect(currentChannel) {
        selectedStreamIndex = if (isLiveMatch && streamList.isNotEmpty()) 0 else -1
        isAutoSwitchingActive = isLiveMatch && streamList.isNotEmpty()
        allStreamsFailed = false
        isPlaybackReady = false
        isPlaying = false
    }

    // Playback check timer (no automatic server switching; user manually changes servers if one fails)
    LaunchedEffect(selectedStreamIndex, currentChannel) {
        if (selectedStreamIndex >= 0 && selectedStreamIndex < streamList.size) {
            val currentStream = streamList[selectedStreamIndex]
            if (currentStream.player.lowercase() != "iframe") {
                delay(25000)
                if (!isPlaybackReady && !isPlaying) {
                    allStreamsFailed = true
                }
            }
        }
    }

    // Fallback error handler (marks current stream as failed for manual server selection)
    val handlePlaybackError = {
        allStreamsFailed = true
    }

    // Handler when user manually selects a stream
    val handleStreamSelect = { index: Int ->
        isAutoSwitchingActive = false // Stop automatic switching
        selectedStreamIndex = index
        allStreamsFailed = false
        isPlaybackReady = false
        isPlaying = false
    }

    // Define playlist combining categoryChannels and liveMatches
    val playlist = remember(categoryChannels, liveMatches, currentChannel) {
        if (categoryChannels.isNotEmpty()) {
            categoryChannels
        } else if (liveMatches.isNotEmpty()) {
            liveMatches
        } else {
            listOf(currentChannel)
        }
    }

    val currentChannelIndex = remember(currentChannel, playlist) {
        playlist.indexOfFirst { it.id == currentChannel.id }
    }

    // Calculate next/previous channel indices with wrap-around
    val nextChannelIndex = remember(currentChannelIndex, playlist) {
        if (currentChannelIndex != -1 && playlist.isNotEmpty()) {
            if (currentChannelIndex == playlist.size - 1) 0 else currentChannelIndex + 1
        } else {
            -1
        }
    }

    val prevChannelIndex = remember(currentChannelIndex, playlist) {
        if (currentChannelIndex != -1 && playlist.isNotEmpty()) {
            if (currentChannelIndex == 0) playlist.size - 1 else currentChannelIndex - 1
        } else {
            -1
        }
    }

    // State for skip count to prevent infinite loop of dead streams
    var skipCount by remember { mutableStateOf(0) }

    LaunchedEffect(isPlaybackReady) {
        if (isPlaybackReady) {
            skipCount = 0
        }
    }

    val handleChannelPlaybackError = {
        allStreamsFailed = true
    }

    // Controls visibility tracking state
    var isControlsVisible by remember { mutableStateOf(false) }

    // Overlay visibility state
    var showChannelOverlay by remember { mutableStateOf(false) }

    LaunchedEffect(currentChannel) {
        showChannelOverlay = true
        delay(2000)
        showChannelOverlay = false
    }

    val topFocusRequester = remember { FocusRequester() }

    LaunchedEffect(currentChannel) {
        try {
            topFocusRequester.requestFocus()
        } catch (e: Exception) {}
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(topFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                    val keyCode = keyEvent.nativeKeyEvent.keyCode
                    if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (nextChannelIndex != -1) {
                            currentChannel = playlist[nextChannelIndex]
                            true
                        } else {
                            false
                        }
                    } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (prevChannelIndex != -1) {
                            currentChannel = playlist[prevChannelIndex]
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
    ) {
        AnimatedContent(
            targetState = currentChannel,
            transitionSpec = {
                fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) togetherWith fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
            },
            label = "match_transition",
            modifier = Modifier.fillMaxSize()
        ) { activeChannel ->
            // Parse streamList for the active channel
            val activeIsLiveMatch = remember(activeChannel) {
                activeChannel.country.equals("Live Matches", ignoreCase = true) || 
                activeChannel.category.equals("Live Matches", ignoreCase = true)
            }

            val activeStreamList = remember(activeChannel.streamsJson) {
                val list = mutableListOf<StreamInfo>()
                if (!activeChannel.streamsJson.isNullOrEmpty()) {
                    try {
                        val jsonArray = org.json.JSONArray(activeChannel.streamsJson)
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            list.add(
                                StreamInfo(
                                    name = obj.optString("name", "Server ${i + 1}"),
                                    player = obj.optString("player", "m3u8"),
                                    url = obj.optString("url", "")
                                )
                            )
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
                list
            }

            // Determine what to display based on the selected/active stream
            if (activeIsLiveMatch) {
                // Live match mode: play immediately without dialog
                val activeStream = if (selectedStreamIndex >= 0 && selectedStreamIndex < activeStreamList.size) {
                    activeStreamList[selectedStreamIndex]
                } else {
                    null
                }

                if (activeStream?.player?.lowercase() == "iframe") {
                    IframePlayer(
                        url = activeStream.url,
                        channelName = activeChannel.name,
                        onBack = onBack,
                        isLiveMatch = true,
                        streamList = activeStreamList,
                        selectedStreamIndex = selectedStreamIndex,
                        onStreamSelect = handleStreamSelect,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val resolvedChannel = remember(activeChannel, activeStream) {
                        if (activeStream != null) {
                            activeChannel.copy(streamUrl = activeStream.url)
                        } else {
                            activeChannel
                        }
                    }
                    ExoVideoPlayer(
                        channel = resolvedChannel,
                        onBack = onBack,
                        isLiveMatch = true,
                        streamList = activeStreamList,
                        selectedStreamIndex = selectedStreamIndex,
                        onStreamSelect = handleStreamSelect,
                        allStreamsFailed = allStreamsFailed,
                        onPlaybackReady = { isPlaybackReady = true },
                        onPlayingStateChanged = { isPlaying = it },
                        onPlaybackError = handleChannelPlaybackError,
                        onControlsVisibilityChanged = { isControlsVisible = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                // Non-live-match: standard behavior with ServerSelectionDialog if streams exist
                var selectedStream by remember { mutableStateOf<StreamInfo?>(null) }

                if (activeStreamList.isNotEmpty() && selectedStream == null) {
                    ServerSelectionDialog(
                        channelName = activeChannel.name,
                        streams = activeStreamList,
                        onSelect = { selectedStream = it },
                        onBack = onBack
                    )
                } else {
                    if (selectedStream?.player?.lowercase() == "iframe") {
                        IframePlayer(
                            url = selectedStream!!.url,
                            channelName = activeChannel.name,
                            onBack = {
                                if (activeStreamList.size > 1) {
                                    selectedStream = null
                                } else {
                                    onBack()
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val resolvedChannel = remember(activeChannel, selectedStream) {
                            if (selectedStream != null) {
                                activeChannel.copy(streamUrl = selectedStream!!.url)
                            } else {
                                activeChannel
                            }
                        }
                        ExoVideoPlayer(
                            channel = resolvedChannel,
                            onBack = {
                                if (activeStreamList.size > 1) {
                                    selectedStream = null
                                } else {
                                    onBack()
                                }
                            },
                            onPlaybackReady = { isPlaybackReady = true },
                            onPlayingStateChanged = { isPlaying = it },
                            onPlaybackError = handleChannelPlaybackError,
                            onControlsVisibilityChanged = { isControlsVisible = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // Channel Info Overlay (shows up for 2 seconds when currentChannel changes)
        ChannelInfoOverlay(
            channel = currentChannel,
            channelIndex = currentChannelIndex,
            totalChannels = playlist.size,
            visible = showChannelOverlay
        )
    }
}

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
    
    // Request focus on player box so it receives TV key events immediately
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            // Safe fallback
        }
    }
    
    // Keep screen on
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var isBuffering by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showControls by remember { mutableStateOf(true) }

    LaunchedEffect(showControls) {
        onControlsVisibilityChanged(showControls)
    }
    var retryCount by remember { mutableStateOf(0) }

    // Initialize AndroidX Media3 ExoPlayer optimized for live TV, low latency buffering, and adaptive bitrate
    val exoPlayer = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 2500,
                /* maxBufferMs = */ 15000,
                /* bufferForPlaybackMs = */ 1500,
                /* bufferForPlaybackAfterRebufferMs = */ 2000
            )
            .build()
            
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setAllowVideoNonSeamlessAdaptiveness(true)
            )
        }

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .build().apply {
                val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build()
                setAudioAttributes(audioAttributes, true)
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }

    // Set Up HLS / MPEG-TS / Adaptive Stream automatically using DefaultMediaSourceFactory
    LaunchedEffect(channel.streamUrl, retryCount) {
        hasError = false
        isBuffering = true
        errorMessage = null
        
        try {
            var resolvedUrl = if (channel.streamUrl.startsWith("bein_id:")) {
                val streamId = channel.streamUrl.substringAfter("bein_id:")
                val prefs = context.getSharedPreferences("app_config", android.content.Context.MODE_PRIVATE)
                val server = prefs.getString("server", "http://t77ert.top:8080")?.trim() ?: "http://t77ert.top:8080"
                val username = prefs.getString("username", "190449942528177")?.trim() ?: "190449942528177"
                val password = prefs.getString("password", "UCoJgCCNnaK87n8")?.trim() ?: "UCoJgCCNnaK87n8"
                val cleanServer = if (server.endsWith("/")) server.dropLast(1) else server
                "${cleanServer}/live/${username}/${password}/${streamId}.m3u8"
            } else {
                channel.streamUrl
            }

            // Clean up double slashes in path (e.g. http://host:80//streaming/ -> http://host:80/streaming/)
            if (resolvedUrl.startsWith("http://") || resolvedUrl.startsWith("https://")) {
                val scheme = if (resolvedUrl.startsWith("https://")) "https://" else "http://"
                val pathAndQuery = resolvedUrl.substring(scheme.length)
                val parts = pathAndQuery.split("?", limit = 2)
                val cleanPath = parts[0].replace(Regex("(?<!:)/{2,}"), "/")
                resolvedUrl = if (parts.size > 1) "$scheme$cleanPath?${parts[1]}" else "$scheme$cleanPath"
            }

            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)

            val mediaItemBuilder = MediaItem.Builder().setUri(resolvedUrl)
            val lowerFullUrl = resolvedUrl.lowercase()
            
            if (lowerFullUrl.contains("m3u8") || lowerFullUrl.contains("extension=m3u8") || lowerFullUrl.contains("format=m3u8") || lowerFullUrl.contains("type=m3u8")) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            } else if (lowerFullUrl.contains(".ts") || lowerFullUrl.contains("extension=ts") || lowerFullUrl.contains("format=ts") || lowerFullUrl.contains("type=ts") || lowerFullUrl.contains("clients_live.php")) {
                mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP2T)
            }

            val mediaItem = mediaItemBuilder.build()

            val extractorsFactory = DefaultExtractorsFactory()
                .setTsExtractorFlags(
                    DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
                )

            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
            val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)

            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
        } catch (e: Exception) {
            hasError = true
            isBuffering = false
            errorMessage = e.localizedMessage ?: "Source Initialization Failed"
            onPlaybackError()
        }
    }

    // Monitor Playback States
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
                errorMessage = "Stream Error: Channel Offline"
                onPlaybackError()
                
                // Auto-retry once after 4 seconds
                if (retryCount < 2) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        retryCount++
                    }, 4000)
                }
            }

            override fun onIsPlayingChanged(isPlayingChange: Boolean) {
                isPlaying = isPlayingChange
                onPlayingStateChanged(isPlayingChange)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Auto fade controls after 5 seconds
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
            .onKeyEvent {
                showControls = true
                false
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showControls = !showControls
            }
    ) {
        // ExoPlayer Canvas Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading/Buffering overlay
        if (isBuffering) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = LightTurquoise,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Buffering Stream...",
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Error State Overlay
        if (hasError || (isLiveMatch && allStreamsFailed)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "This channel is currently unavailable. Please choose another channel.",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { retryCount++ },
                        colors = ButtonDefaults.buttonColors(containerColor = LightTurquoise, contentColor = Color.Black),
                        modifier = Modifier.testTag("retry_stream_button")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Retry")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry Connection", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Custom Overlay Controls (Fades in and out)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .testTag("player_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = channel.name,
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (isLiveMatch && streamList.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                streamList.forEachIndexed { index, _ ->
                                    val isSelected = index == selectedStreamIndex
                                    val streamFocusRequester = remember { FocusRequester() }
                                    var isFocused by remember { mutableStateOf(false) }
                                    val buttonScale by animateFloatAsState(if (isFocused) 1.15f else 1.0f)
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .scale(buttonScale)
                                            .background(
                                                color = if (isSelected) LightTurquoise else if (isFocused) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f),
                                                shape = CircleShape
                                            )
                                            .border(
                                                width = if (isFocused) 2.dp else 1.dp,
                                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.3f),
                                                shape = CircleShape
                                            )
                                            .focusRequester(streamFocusRequester)
                                            .onFocusChanged { isFocused = it.isFocused }
                                            .focusable()
                                            .clickable {
                                                onStreamSelect(index)
                                            }
                                            .testTag("stream_select_button_$index"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            color = if (isSelected) Color.Black else Color.White,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Center Play/Pause Indicator
                IconButton(
                    onClick = {
                        if (isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    },
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .align(Alignment.Center)
                        .focusable()
                        .testTag("player_play_pause_button")
                ) {
                    val iconRes = if (isPlaying) {
                        android.R.drawable.ic_media_pause
                    } else {
                        android.R.drawable.ic_media_play
                    }
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = "Play/Pause",
                        tint = LightTurquoise,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Bottom Status Overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "• LIVE",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Hardware Accelerated HLS Player",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun ServerSelectionDialog(
    channelName: String,
    streams: List<StreamInfo>,
    onSelect: (StreamInfo) -> Unit,
    onBack: () -> Unit
) {
    val focusRequesters = remember { List(streams.size) { FocusRequester() } }
    
    // Auto-focus the first server button on open
    LaunchedEffect(Unit) {
        if (focusRequesters.isNotEmpty()) {
            try {
                focusRequesters[0].requestFocus()
            } catch (e: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onBack
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth(0.85f)
                .padding(24.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // Consume click
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Text(
                    text = "🔴 LIVE MATCH SOURCE",
                    color = LightTurquoise,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    text = channelName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // List of servers
                streams.forEachIndexed { index, stream ->
                    var isFocused by remember { mutableStateOf(false) }
                    val borderStroke = if (isFocused) {
                        BorderStroke(2.dp, LightTurquoise)
                    } else {
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                    }
                    val containerColor = if (isFocused) {
                        LightTurquoise.copy(alpha = 0.12f)
                    } else {
                        Color.White.copy(alpha = 0.04f)
                    }
                    val scale by animateFloatAsState(targetValue = if (isFocused) 1.04f else 1.0f, label = "button_scale")

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .scale(scale)
                            .focusRequester(focusRequesters[index])
                            .onFocusChanged { isFocused = it.isFocused }
                            .focusable()
                            .clickable { onSelect(stream) }
                            .testTag("server_item_$index"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = containerColor),
                        border = borderStroke
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = if (isFocused) LightTurquoise else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stream.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            // Player badge
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (stream.player.lowercase() == "iframe") Color(0xFFD32F2F) else Color(0xFF388E3C)
                            ) {
                                Text(
                                    text = stream.player.uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Cancel button
                var isCancelFocused by remember { mutableStateOf(false) }
                val cancelScale by animateFloatAsState(targetValue = if (isCancelFocused) 1.04f else 1.0f, label = "cancel_scale")
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCancelFocused) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .scale(cancelScale)
                        .onFocusChanged { isCancelFocused = it.isFocused }
                        .focusable()
                        .testTag("server_dialog_cancel_button")
                ) {
                    Text("Cancel / Back", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

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
    
    // Keep screen on while playing video
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var customViewCallbackRef by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var customViewRef by remember { mutableStateOf<android.view.View?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                        
                        // Ads blocking settings
                        setSupportMultipleWindows(false)
                        javaScriptCanOpenWindowsAutomatically = false
                    }

                    // WebView client for handling redirects and block ad requests
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: android.webkit.WebView?,
                            request: android.webkit.WebResourceRequest?
                        ): Boolean {
                            val reqUrl = request?.url?.toString() ?: return false
                            // Only allow HTTP/HTTPS protocol urls
                            if (!reqUrl.startsWith("http://") && !reqUrl.startsWith("https://")) {
                                // Block custom schemes like intent:, market:, tel:, whatsapp:
                                return true
                              }
                              // Allow page navigation but keep inside WebView
                              view?.loadUrl(reqUrl)
                              return true
                          }
 
                          override fun onPageStarted(
                              view: android.webkit.WebView?,
                              url: String?,
                              favicon: android.graphics.Bitmap?
                          ) {
                              super.onPageStarted(view, url, favicon)
                              // Inject JS to block popups/window.open
                              view?.evaluateJavascript(
                                  """
                                  window.open = function() { return null; };
                                  Object.defineProperty(window, 'open', {
                                      value: function() { return null; },
                                      writable: false,
                                      configurable: false
                                  });
                                  """.trimIndent(),
                                  null
                              )
                          }
 
                          override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                              super.onPageFinished(view, url)
                              // Inject JS to block popups/window.open again in case it got reset
                              view?.evaluateJavascript(
                                  """
                                  window.open = function() { return null; };
                                  Object.defineProperty(window, 'open', {
                                      value: function() { return null; },
                                      writable: false,
                                      configurable: false
                                  });
                                  """.trimIndent(),
                                  null
                              )
                          }
                      }
 
                      // WebChrome client for fullscreen support
                      webChromeClient = object : android.webkit.WebChromeClient() {
                          override fun onShowCustomView(
                              view: android.view.View?,
                              callback: CustomViewCallback?
                          ) {
                              super.onShowCustomView(view, callback)
                              if (customViewRef != null) {
                                  callback?.onCustomViewHidden()
                                  return
                              }
                              customViewRef = view
                              customViewCallbackRef = callback
                              val decor = activity?.window?.decorView as? ViewGroup
                              decor?.addView(
                                  view,
                                  ViewGroup.LayoutParams(
                                      ViewGroup.LayoutParams.MATCH_PARENT,
                                      ViewGroup.LayoutParams.MATCH_PARENT
                                  )
                              )
                          }
 
                          override fun onHideCustomView() {
                              super.onHideCustomView()
                              val decor = activity?.window?.decorView as? ViewGroup
                              customViewRef?.let { decor?.removeView(it) }
                              customViewRef = null
                              customViewCallbackRef?.onCustomViewHidden()
                              customViewCallbackRef = null
                          }
                      }
 
                      // Handle TV back key and regular back key
                      setOnKeyListener { _, keyCode, event ->
                          if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                              if (canGoBack()) {
                                  goBack()
                                  true
                              } else {
                                  false
                              }
                          } else {
                              false
                          }
                      }
 
                      // Support TV Focus/Navigation
                      isFocusable = true
                      isFocusableInTouchMode = true
                      requestFocus()
                      
                      loadUrl(url)
                  }
              },
              update = { webView ->
                  if (webView.url != url) {
                      webView.loadUrl(url)
                  }
              },
              modifier = Modifier.fillMaxSize(),
              onRelease = { webView ->
                  webView.stopLoading()
                  webView.clearHistory()
                  webView.clearCache(false)
                  webView.destroy()
              }
          )

          // Translucent Top Bar Overlay with Back button and Server controls
          Row(
              modifier = Modifier
                  .fillMaxWidth()
                  .align(Alignment.TopStart)
                  .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                  .padding(24.dp),
              verticalAlignment = Alignment.CenterVertically
          ) {
              IconButton(
                  onClick = onBack,
                  modifier = Modifier
                      .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                      .testTag("player_back_button")
              ) {
                  Icon(
                      imageVector = Icons.Default.ArrowBack,
                      contentDescription = "Back",
                      tint = Color.White
                  )
              }
              Spacer(modifier = Modifier.width(16.dp))
              Column {
                  Text(
                      text = if (channelName.isNotEmpty()) channelName else "Live Stream",
                      color = Color.White,
                      style = MaterialTheme.typography.titleLarge,
                      fontWeight = FontWeight.Bold
                  )
                  if (isLiveMatch && streamList.isNotEmpty()) {
                      Spacer(modifier = Modifier.height(8.dp))
                      Row(
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(8.dp)
                      ) {
                          streamList.forEachIndexed { index, _ ->
                              val isSelected = index == selectedStreamIndex
                              val streamFocusRequester = remember { FocusRequester() }
                              var isFocused by remember { mutableStateOf(false) }
                              val buttonScale by animateFloatAsState(if (isFocused) 1.15f else 1.0f)
                              
                              Box(
                                  modifier = Modifier
                                      .size(36.dp)
                                      .scale(buttonScale)
                                      .background(
                                          color = if (isSelected) LightTurquoise else if (isFocused) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f),
                                          shape = CircleShape
                                      )
                                      .border(
                                          width = if (isFocused) 2.dp else 1.dp,
                                          color = if (isSelected) Color.White else Color.White.copy(alpha = 0.3f),
                                          shape = CircleShape
                                      )
                                      .focusRequester(streamFocusRequester)
                                      .onFocusChanged { isFocused = it.isFocused }
                                      .focusable()
                                      .clickable {
                                          onStreamSelect(index)
                                      }
                                      .testTag("stream_select_button_$index"),
                                  contentAlignment = Alignment.Center
                              ) {
                                  Text(
                                      text = "${index + 1}",
                                      color = if (isSelected) Color.Black else Color.White,
                                      fontWeight = FontWeight.Bold,
                                      style = MaterialTheme.typography.bodyMedium
                                  )
                              }
                          }
                      }
                  }
              }
          }
      }
  
    // Cleanup full-screen views on dispose
    DisposableEffect(Unit) {
        onDispose {
            val decor = activity?.window?.decorView as? ViewGroup
            customViewRef?.let { decor?.removeView(it) }
            customViewCallbackRef?.onCustomViewHidden()
        }
    }
}

@Composable
fun ChannelInfoOverlay(
    channel: ChannelEntity,
    channelIndex: Int,
    totalChannels: Int,
    visible: Boolean
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(300)) + slideOutVertically(targetOffsetY = { it / 2 })
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 48.dp, start = 32.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.85f),
                    contentColor = Color.White
                ),
                border = BorderStroke(1.2.dp, LightTurquoise.copy(alpha = 0.6f)),
                modifier = Modifier
                    .width(340.dp)
                    .wrapContentHeight()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Channel Logo
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!channel.logo.isNullOrEmpty()) {
                            AsyncImage(
                                model = channel.logo,
                                contentDescription = "${channel.name} Logo",
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Channel logo fallback",
                                tint = LightTurquoise,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Channel Name & Number
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = channel.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Channel ${channelIndex + 1} of $totalChannels",
                            color = LightTurquoise,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
