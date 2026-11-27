package com.example.ui.screens.viewers

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.domain.model.FileNode
import com.example.domain.model.StudioContent
import com.example.ui.LumaFormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class VideoAspectMode(val label: String) {
    FIT("Fit"),
    FILL("Fill"),
    WIDE_16_9("16:9")
}

@Composable
fun FullScreenVideoTheater(
    studio: StudioContent.MediaStudio,
    isVaultPreview: Boolean,
    onClose: () -> Unit,
    onRenameFile: (FileNode, String) -> Unit,
    onShareFile: (FileNode) -> Unit,
    onOpenExternal: (FileNode) -> Unit,
    onCaptureFrameBitmap: (FileNode, android.graphics.Bitmap) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val scope = rememberCoroutineScope()

    var showControls by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var aspectMode by remember { mutableStateOf(VideoAspectMode.FIT) }
    var isLandscape by remember { mutableStateOf(false) }

    var positionMs by remember(studio.file.path) { mutableLongStateOf(0L) }
    var durationMs by remember(studio.file.path) { mutableLongStateOf(studio.durationMs.coerceAtLeast(1L)) }
    var videoWidth by remember(studio.file.path) { mutableIntStateOf(studio.widthPx.coerceAtLeast(16)) }
    var videoHeight by remember(studio.file.path) { mutableIntStateOf(studio.heightPx.coerceAtLeast(9)) }
    var playbackError by remember(studio.file.path) { mutableStateOf<String?>(null) }

    // Gesture HUD overlays
    var brightnessLevel by remember { mutableFloatStateOf(0.55f) }
    var volumeLevel by remember {
        mutableFloatStateOf(
            runCatching {
                val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                cur.toFloat() / max.toFloat()
            }.getOrDefault(0.6f)
        )
    }
    var activeHudBanner by remember { mutableStateOf<String?>(null) }
    var activeHudProgress by remember { mutableFloatStateOf(-1f) }
    var seekFlashLabel by remember { mutableStateOf<String?>(null) }

    var showOverflow by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    val mediaPlayer = remember(studio.file.path) { android.media.MediaPlayer() }
    var surfaceReady by remember { mutableStateOf(false) }

    // Hide system bars for true full-screen theater & restore on exit
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { win ->
                val lp = win.attributes
                lp.screenBrightness = -1f
                win.attributes = lp
            }
        }
    }

    // Prepare MediaPlayer
    DisposableEffect(studio.file.path) {
        try {
            if (studio.file.isSafDocument && studio.file.safUriString != null) {
                mediaPlayer.setDataSource(context, Uri.parse(studio.file.safUriString))
            } else {
                mediaPlayer.setDataSource(studio.file.path)
            }
            mediaPlayer.setOnPreparedListener { mp ->
                durationMs = mp.duration.toLong().coerceAtLeast(1L)
                if (mp.videoWidth > 0 && mp.videoHeight > 0) {
                    videoWidth = mp.videoWidth
                    videoHeight = mp.videoHeight
                }
                mp.start()
                isPlaying = true
            }
            mediaPlayer.setOnCompletionListener {
                if (!isLooping) {
                    isPlaying = false
                    showControls = true
                }
            }
            mediaPlayer.setOnErrorListener { _, what, extra ->
                playbackError = "Unable to play video (code $what/$extra)"
                isPlaying = false
                true
            }
            mediaPlayer.prepareAsync()
        } catch (e: Exception) {
            playbackError = e.message ?: "Unable to open video"
        }

        onDispose {
            runCatching { mediaPlayer.stop() }
            runCatching { mediaPlayer.release() }
        }
    }

    // Position ticker
    LaunchedEffect(isPlaying, studio.file.path) {
        while (isPlaying) {
            runCatching {
                positionMs = mediaPlayer.currentPosition.toLong()
            }
            delay(250L)
        }
    }

    // Auto-hide controls after 3.5s while playing
    LaunchedEffect(showControls, isPlaying, isLocked) {
        if (showControls && isPlaying && !isLocked) {
            delay(3500L)
            showControls = false
        }
    }

    // Auto-clear transient seek/HUD badges
    LaunchedEffect(seekFlashLabel) {
        if (seekFlashLabel != null) {
            delay(750L)
            seekFlashLabel = null
        }
    }
    LaunchedEffect(activeHudBanner) {
        if (activeHudBanner != null) {
            delay(1100L)
            activeHudBanner = null
            activeHudProgress = -1f
        }
    }

    val computedRatio = when (aspectMode) {
        VideoAspectMode.WIDE_16_9 -> 16f / 9f
        else -> (videoWidth.toFloat() / videoHeight.coerceAtLeast(1).toFloat()).coerceIn(0.4f, 3f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("fullscreen_video_theater")
    ) {
        // ==================== VIDEO SURFACE ====================
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val surfaceMod = if (aspectMode == VideoAspectMode.FILL) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(computedRatio)
            }
            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                surfaceReady = true
                                runCatching { mediaPlayer.setDisplay(holder) }
                            }
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                runCatching { mediaPlayer.setDisplay(holder) }
                            }
                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                surfaceReady = false
                                runCatching { mediaPlayer.setDisplay(null) }
                            }
                        })
                    }
                },
                modifier = surfaceMod
            )
        }

        // ==================== GESTURE LAYER (TAP, DOUBLE-TAP ±10S, BRIGHTNESS & VOLUME DRAG) ====================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isLocked) {
                    detectTapGestures(
                        onTap = {
                            showControls = !showControls
                        },
                        onDoubleTap = { tapOffset ->
                            if (isLocked) return@detectTapGestures
                            val third = size.width / 3f
                            when {
                                tapOffset.x < third -> {
                                    val next = (positionMs - 10_000L).coerceAtLeast(0L)
                                    runCatching { mediaPlayer.seekTo(next.toInt()) }
                                    positionMs = next
                                    seekFlashLabel = "−10s"
                                }
                                tapOffset.x > third * 2f -> {
                                    val next = (positionMs + 10_000L).coerceAtMost(durationMs)
                                    runCatching { mediaPlayer.seekTo(next.toInt()) }
                                    positionMs = next
                                    seekFlashLabel = "+10s"
                                }
                                else -> {
                                    if (isPlaying) {
                                        runCatching { mediaPlayer.pause() }
                                        isPlaying = false
                                    } else {
                                        runCatching { mediaPlayer.start() }
                                        isPlaying = true
                                    }
                                }
                            }
                        }
                    )
                }
                .pointerInput(isLocked) {
                    detectVerticalDragGestures { change, dragAmount ->
                        if (isLocked) return@detectVerticalDragGestures
                        val delta = -dragAmount / (size.height.coerceAtLeast(300) * 0.75f)
                        if (change.position.x < size.width / 2f) {
                            // Left side -> Screen Brightness
                            brightnessLevel = (brightnessLevel + delta).coerceIn(0.05f, 1f)
                            activity?.window?.let { win ->
                                val lp = win.attributes
                                lp.screenBrightness = brightnessLevel
                                win.attributes = lp
                            }
                            activeHudBanner = "Brightness ${(brightnessLevel * 100).toInt()}%"
                            activeHudProgress = brightnessLevel
                        } else {
                            // Right side -> Media Volume
                            volumeLevel = (volumeLevel + delta).coerceIn(0f, 1f)
                            runCatching {
                                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    (volumeLevel * maxVol).toInt().coerceIn(0, maxVol),
                                    0
                                )
                            }
                            activeHudBanner = "Volume ${(volumeLevel * 100).toInt()}%"
                            activeHudProgress = volumeLevel
                        }
                    }
                }
        )

        // ==================== CENTER GESTURE / SEEK HUD ====================
        if (activeHudBanner != null) {
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Column(
                    modifier = Modifier
                        .width(170.dp)
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (activeHudBanner!!.startsWith("Brightness")) Icons.Default.BrightnessHigh else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Text(activeHudBanner!!, color = Color.White, style = MaterialTheme.typography.labelLarge)
                    if (activeHudProgress >= 0f) {
                        LinearProgressIndicator(
                            progress = { activeHudProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        if (seekFlashLabel != null) {
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                shape = CircleShape,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(
                    text = seekFlashLabel!!,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }

        if (playbackError != null) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(playbackError!!, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    if (!isVaultPreview) {
                        Button(onClick = { onOpenExternal(studio.file) }) {
                            Text("Open in external player")
                        }
                    }
                }
            }
        }

        // ==================== LOCKED UNLOCK BUTTON ====================
        if (isLocked && showControls) {
            IconButton(
                onClick = { isLocked = false },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .size(52.dp)
                    .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                    .testTag("video_unlock_btn")
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = "Unlock Controls", tint = Color.White)
            }
        }

        // ==================== FULL CONTROLS OVERLAY ====================
        AnimatedVisibility(
            visible = showControls && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent))
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onClose, modifier = Modifier.testTag("video_close_btn")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = studio.title.ifBlank { studio.file.name },
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${studio.sampleRateOrResolution}${if (isVaultPreview) " · Vault" else ""}",
                                color = Color.White.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val nextSpeed = when {
                                    playbackSpeed < 0.75f -> 0.75f
                                    playbackSpeed < 1.0f -> 1.0f
                                    playbackSpeed < 1.25f -> 1.25f
                                    playbackSpeed < 1.5f -> 1.5f
                                    playbackSpeed < 2.0f -> 2.0f
                                    else -> 0.5f
                                }
                                playbackSpeed = nextSpeed
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    runCatching {
                                        mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(nextSpeed)
                                    }
                                }
                                activeHudBanner = "${nextSpeed}× Speed"
                            },
                            modifier = Modifier.testTag("video_speed_btn")
                        ) {
                            Text("${playbackSpeed}×", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }

                        IconButton(
                            onClick = {
                                aspectMode = when (aspectMode) {
                                    VideoAspectMode.FIT -> VideoAspectMode.FILL
                                    VideoAspectMode.FILL -> VideoAspectMode.WIDE_16_9
                                    VideoAspectMode.WIDE_16_9 -> VideoAspectMode.FIT
                                }
                                activeHudBanner = "Aspect: ${aspectMode.label}"
                            },
                            modifier = Modifier.testTag("video_aspect_btn")
                        ) {
                            Icon(Icons.Default.AspectRatio, contentDescription = "Aspect Ratio", tint = Color.White)
                        }

                        Box {
                            IconButton(onClick = { showOverflow = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                            }
                            DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                                DropdownMenuItem(
                                    text = { Text("Capture Frame") },
                                    leadingIcon = { Icon(Icons.Default.CameraAlt, null) },
                                    onClick = {
                                        showOverflow = false
                                        scope.launch {
                                            val bmp = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    val r = MediaMetadataRetriever()
                                                    r.setDataSource(studio.file.path)
                                                    val frame = r.getFrameAtTime(positionMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                                    r.release()
                                                    frame
                                                }.getOrNull()
                                            }
                                            if (bmp != null) {
                                                onCaptureFrameBitmap(studio.file, bmp)
                                                activeHudBanner = "Frame saved"
                                            }
                                        }
                                    }
                                )
                                if (!isVaultPreview) {
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                                        onClick = {
                                            showOverflow = false
                                            showRenameDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share") },
                                        leadingIcon = { Icon(Icons.Default.Share, null) },
                                        onClick = {
                                            showOverflow = false
                                            onShareFile(studio.file)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Open with…") },
                                        leadingIcon = { Icon(Icons.Default.OpenInNew, null) },
                                        onClick = {
                                            showOverflow = false
                                            onOpenExternal(studio.file)
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Video Info") },
                                    leadingIcon = { Icon(Icons.Default.Info, null) },
                                    onClick = {
                                        showOverflow = false
                                        showInfoDialog = true
                                    }
                                )
                            }
                        }
                    }
                }

                // Center Transport Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val next = (positionMs - 10_000L).coerceAtLeast(0L)
                            runCatching { mediaPlayer.seekTo(next.toInt()) }
                            positionMs = next
                        },
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(Icons.Default.Replay10, contentDescription = "Rewind 10 seconds", tint = Color.White, modifier = Modifier.size(30.dp))
                    }

                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                runCatching { mediaPlayer.pause() }
                                isPlaying = false
                            } else {
                                runCatching { mediaPlayer.start() }
                                isPlaying = true
                            }
                        },
                        modifier = Modifier
                            .size(68.dp)
                            .background(Color.White.copy(alpha = 0.22f), CircleShape)
                            .testTag("video_play_pause_btn")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val next = (positionMs + 10_000L).coerceAtMost(durationMs)
                            runCatching { mediaPlayer.seekTo(next.toInt()) }
                            positionMs = next
                        },
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(Icons.Default.Forward10, contentDescription = "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }

                // Bottom Scrub Bar & Utility Row
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(LumaFormatUtils.formatDuration(positionMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                        Text(LumaFormatUtils.formatDuration(durationMs), color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                    }

                    Slider(
                        value = positionMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                        onValueChange = { next ->
                            positionMs = next.toLong()
                            runCatching { mediaPlayer.seekTo(next.toInt()) }
                        },
                        valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { isLocked = true },
                            modifier = Modifier.testTag("video_lock_btn")
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock Screen", tint = Color.White)
                        }

                        IconButton(
                            onClick = {
                                isMuted = !isMuted
                                val vol = if (isMuted) 0f else 1f
                                runCatching { mediaPlayer.setVolume(vol, vol) }
                            }
                        ) {
                            Icon(
                                if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Mute",
                                tint = if (isMuted) MaterialTheme.colorScheme.error else Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                isLooping = !isLooping
                                runCatching { mediaPlayer.isLooping = isLooping }
                                activeHudBanner = if (isLooping) "Loop On" else "Loop Off"
                            }
                        ) {
                            Icon(
                                Icons.Default.Repeat,
                                contentDescription = "Loop",
                                tint = if (isLooping) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                isLandscape = !isLandscape
                                activity?.requestedOrientation = if (isLandscape) {
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                }
                            },
                            modifier = Modifier.testTag("video_rotate_btn")
                        ) {
                            Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate Orientation", tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        var newName by remember(studio.file.name) { mutableStateOf(studio.file.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Video") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("File name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank() && newName != studio.file.name) {
                        onRenameFile(studio.file, newName.trim())
                    }
                    showRenameDialog = false
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } }
        )
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Video Info") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(studio.file.name, fontWeight = FontWeight.SemiBold)
                    Text("Resolution: ${studio.sampleRateOrResolution}", style = MaterialTheme.typography.bodySmall)
                    Text("Duration: ${LumaFormatUtils.formatDuration(durationMs)}", style = MaterialTheme.typography.bodySmall)
                    if (studio.bitrateOrChannels.isNotBlank()) {
                        Text("Bitrate: ${studio.bitrateOrChannels}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Size: ${LumaFormatUtils.formatBytes(studio.file.sizeBytes)}", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("Done") } }
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
