package com.example.ui.screens.viewers

import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.FileNode
import com.example.domain.model.StudioContent
import com.example.ui.LumaFormatUtils
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 18 Distinct Procedural Audio VFX Visualizers — one is picked randomly on every track open.
 */
enum class AudioVfxPreset(val title: String) {
    NEON_SPECTRUM_BARS("Neon Bars"),
    RADIAL_SUNBURST("Sunburst"),
    LIQUID_AURORA("Aurora"),
    MIRROR_OSCILLOSCOPE("Mirror Wave"),
    STARFIELD_WARP("Star Warp"),
    CYBER_GRID("Cyber Grid"),
    PLASMA_RINGS("Plasma Rings"),
    DNA_HELIX("DNA Helix"),
    PARTICLE_FOUNTAIN("Fountain"),
    HEX_PULSE_MATRIX("Hex Matrix"),
    FIRE_EQUALIZER("Ember EQ"),
    LIQUID_MERCURY_ORB("Mercury Orb"),
    LASER_TUNNEL("Laser Tunnel"),
    KALEIDOSCOPE_BLOOM("Kaleidoscope"),
    SOUNDWAVE_RIBBON("Silk Ribbon"),
    SONAR_RADAR("Sonar Pulse"),
    MATRIX_RAIN("Pulse Rain"),
    ORBITAL_NEBULA("Nebula")
}

private enum class RepeatMode { OFF, ALL, ONE }

@Composable
fun FullScreenAudioVfxPlayer(
    studio: StudioContent.MediaStudio,
    isVaultPreview: Boolean,
    siblingAudioFiles: List<FileNode>,
    onClose: () -> Unit,
    onOpenSiblingAudio: (FileNode) -> Unit,
    onRenameFile: (FileNode, String) -> Unit,
    onShareFile: (FileNode) -> Unit,
    onOpenExternal: (FileNode) -> Unit
) {
    val context = LocalContext.current

    // Randomly select 1 of the 18 VFX presets every time a track opens!
    var activeVfxIndex by remember(studio.file.path) {
        mutableIntStateOf(Random.nextInt(AudioVfxPreset.entries.size))
    }
    var autoCycleVfx by remember { mutableStateOf(false) }

    var isPlaying by remember(studio.file.path) { mutableStateOf(false) }
    var positionMs by remember(studio.file.path) { mutableLongStateOf(0L) }
    var durationMs by remember(studio.file.path) { mutableLongStateOf(studio.durationMs.coerceAtLeast(1L)) }
    var repeatMode by remember { mutableStateOf(RepeatMode.OFF) }
    var isShuffle by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var sleepTimerMinutes by remember { mutableIntStateOf(0) }
    var sleepRemainingMs by remember { mutableLongStateOf(0L) }

    // Equalizer & Bass Boost state
    var showEqDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    var eqBands by remember { mutableStateOf(List(5) { 0f }) } // -1f .. +1f
    var bassBoostStrength by remember { mutableFloatStateOf(0f) } // 0f .. 1f
    var selectedEqPreset by remember { mutableStateOf("Flat") }

    // Real-time audio spectrum bands (32 bins in 0f..1f)
    val liveBins = remember { FloatArray(32) }
    var liveVisualizerCaptured by remember { mutableStateOf(false) }
    var animTimeSec by remember { mutableFloatStateOf(0f) }
    var frameTick by remember { mutableIntStateOf(0) }

    val mediaPlayer = remember(studio.file.path) { MediaPlayer() }
    var equalizerRef by remember(studio.file.path) { mutableStateOf<Equalizer?>(null) }
    var bassBoostRef by remember(studio.file.path) { mutableStateOf<BassBoost?>(null) }

    val currentIdx = remember(siblingAudioFiles, studio.file.path) {
        siblingAudioFiles.indexOfFirst { it.path == studio.file.path }
    }

    fun playNextTrack() {
        if (siblingAudioFiles.size <= 1) return
        val next = if (isShuffle) {
            siblingAudioFiles.filterNot { it.path == studio.file.path }.randomOrNull()
        } else if (currentIdx in 0 until siblingAudioFiles.lastIndex) {
            siblingAudioFiles[currentIdx + 1]
        } else if (repeatMode == RepeatMode.ALL) {
            siblingAudioFiles.firstOrNull()
        } else null
        if (next != null) onOpenSiblingAudio(next)
    }

    fun playPrevTrack() {
        if (positionMs > 4000L) {
            runCatching { mediaPlayer.seekTo(0) }
            positionMs = 0L
            return
        }
        if (siblingAudioFiles.size <= 1) return
        val prev = if (currentIdx > 0) siblingAudioFiles[currentIdx - 1] else siblingAudioFiles.lastOrNull()
        if (prev != null) onOpenSiblingAudio(prev)
    }

    // Prepare MediaPlayer + AudioFX (Equalizer, BassBoost, Visualizer)
    DisposableEffect(studio.file.path) {
        var visualizer: Visualizer? = null
        try {
            if (studio.file.isSafDocument && studio.file.safUriString != null) {
                mediaPlayer.setDataSource(context, Uri.parse(studio.file.safUriString))
            } else {
                mediaPlayer.setDataSource(studio.file.path)
            }
            mediaPlayer.setOnPreparedListener { mp ->
                durationMs = mp.duration.toLong().coerceAtLeast(1L)
                mp.start()
                isPlaying = true

                val sessionId = mp.audioSessionId
                runCatching {
                    equalizerRef = Equalizer(0, sessionId).apply { enabled = true }
                }
                runCatching {
                    bassBoostRef = BassBoost(0, sessionId).apply { enabled = true }
                }
                // Best-effort hardware FFT capture; smoothly falls back to beat-synced synthesis if RECORD_AUDIO not held
                runCatching {
                    val vis = Visualizer(sessionId)
                    vis.captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(256)
                    vis.setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, sr: Int) = Unit
                            override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, sr: Int) {
                                if (fft == null || fft.size < 64) return
                                liveVisualizerCaptured = true
                                val binsCount = 32
                                for (i in 0 until binsCount) {
                                    val idx = (i * 2 + 2).coerceAtMost(fft.size - 2)
                                    val re = fft[idx].toFloat()
                                    val im = fft[idx + 1].toFloat()
                                    val mag = (hypot(re, im) / 80f).coerceIn(0f, 1f)
                                    liveBins[i] = liveBins[i] * 0.55f + mag * 0.45f
                                }
                            }
                        },
                        Visualizer.getMaxCaptureRate() / 2,
                        false,
                        true
                    )
                    vis.enabled = true
                    visualizer = vis
                }
            }
            mediaPlayer.setOnCompletionListener {
                when (repeatMode) {
                    RepeatMode.ONE -> {
                        runCatching {
                            mediaPlayer.seekTo(0)
                            mediaPlayer.start()
                        }
                    }
                    else -> {
                        if (siblingAudioFiles.size > 1 && (repeatMode == RepeatMode.ALL || currentIdx < siblingAudioFiles.lastIndex || isShuffle)) {
                            playNextTrack()
                        } else {
                            isPlaying = false
                        }
                    }
                }
            }
            mediaPlayer.prepareAsync()
        } catch (_: Exception) {
            isPlaying = false
        }

        onDispose {
            runCatching { visualizer?.enabled = false; visualizer?.release() }
            runCatching { equalizerRef?.release() }
            runCatching { bassBoostRef?.release() }
            runCatching { mediaPlayer.stop() }
            runCatching { mediaPlayer.release() }
        }
    }

    // Apply EQ band updates to hardware Equalizer
    LaunchedEffect(eqBands, equalizerRef) {
        val eq = equalizerRef ?: return@LaunchedEffect
        runCatching {
            val bandsCount = eq.numberOfBands.toInt().coerceAtMost(eqBands.size)
            val range = eq.bandLevelRange
            val minLevel = range[0].toFloat()
            val maxLevel = range[1].toFloat()
            for (b in 0 until bandsCount) {
                val v = eqBands[b].coerceIn(-1f, 1f)
                val level = if (v >= 0f) (v * maxLevel).toInt() else (-v * minLevel).toInt()
                eq.setBandLevel(b.toShort(), level.toShort())
            }
        }
    }

    // Apply Bass Boost updates
    LaunchedEffect(bassBoostStrength, bassBoostRef) {
        val bb = bassBoostRef ?: return@LaunchedEffect
        runCatching {
            bb.setStrength((bassBoostStrength.coerceIn(0f, 1f) * 1000f).toInt().toShort())
        }
    }

    // 60fps VFX Animation & Energy Synthesis Clock
    LaunchedEffect(isPlaying) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos == 0L) lastNanos = nanos
                val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastNanos = nanos
                if (isPlaying) {
                    animTimeSec += dt
                    if (!liveVisualizerCaptured) {
                        // Organic multi-harmonic beat-reactive energy synthesis
                        val beatPulse = (0.5f + 0.5f * sin(animTimeSec * 4.2f)) * (0.65f + 0.35f * sin(animTimeSec * 1.9f))
                        for (i in 0 until 32) {
                            val freqWeight = 1f - (i / 38f)
                            val wave1 = sin(animTimeSec * (2.6f + i * 0.24f) + i * 0.45f)
                            val wave2 = cos(animTimeSec * (4.8f - i * 0.11f) + i * 0.9f)
                            val target = ((0.22f + 0.55f * beatPulse * freqWeight + 0.23f * abs(wave1 * wave2))).coerceIn(0.08f, 1f)
                            liveBins[i] = liveBins[i] * 0.72f + target * 0.28f
                        }
                    }
                    frameTick++
                } else {
                    for (i in 0 until 32) {
                        liveBins[i] *= 0.92f
                    }
                    frameTick++
                }
            }
        }
    }

    // Position ticker + Sleep Timer + Auto-Cycle VFX
    LaunchedEffect(isPlaying, studio.file.path, sleepTimerMinutes, autoCycleVfx) {
        var cycleCounter = 0
        while (true) {
            if (isPlaying) {
                runCatching { positionMs = mediaPlayer.currentPosition.toLong() }
                if (sleepRemainingMs > 0L) {
                    sleepRemainingMs = (sleepRemainingMs - 500L).coerceAtLeast(0L)
                    if (sleepRemainingMs == 0L) {
                        sleepTimerMinutes = 0
                        runCatching { mediaPlayer.pause() }
                        isPlaying = false
                    }
                }
                if (autoCycleVfx) {
                    cycleCounter++
                    if (cycleCounter >= 24) { // ~12 seconds
                        cycleCounter = 0
                        activeVfxIndex = (activeVfxIndex + 1) % AudioVfxPreset.entries.size
                    }
                }
            }
            delay(500L)
        }
    }

    val activePreset = AudioVfxPreset.entries[activeVfxIndex]
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070912))
            .testTag("fullscreen_audio_vfx_player")
    ) {
        // ==================== FULL-SCREEN 60FPS PROCEDURAL VFX CANVAS ====================
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    activeVfxIndex = (activeVfxIndex + 1) % AudioVfxPreset.entries.size
                }
                .testTag("audio_vfx_canvas")
        ) {
            // Read frameTick so Compose invalidates draw on every frame
            val tickGuard = frameTick
            if (tickGuard >= 0) {
                drawAudioVfxScene(
                    preset = activePreset,
                    bins = liveBins,
                    timeSec = animTimeSec,
                    primary = primaryColor,
                    secondary = secondaryColor,
                    tertiary = tertiaryColor
                )
            }
        }

        // Subtle readability scrim at top & bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.65f),
                            0.25f to Color.Transparent,
                            0.55f to Color.Black.copy(alpha = 0.25f),
                            1.0f to Color.Black.copy(alpha = 0.90f)
                        )
                    )
                )
        )

        // ==================== FOREGROUND PLAYER LAYOUT ====================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.testTag("audio_close_btn")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                // Current VFX Badge (Tap for Random VFX)
                Surface(
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .clickable {
                            var next = Random.nextInt(AudioVfxPreset.entries.size)
                            if (next == activeVfxIndex) next = (next + 1) % AudioVfxPreset.entries.size
                            activeVfxIndex = next
                        }
                        .testTag("audio_random_vfx_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Random VFX", tint = Color.White, modifier = Modifier.size(15.dp))
                        Text(
                            text = "VFX · ${activePreset.title}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showEqDialog = true },
                        modifier = Modifier.testTag("audio_eq_btn")
                    ) {
                        Icon(Icons.Default.Equalizer, contentDescription = "Equalizer", tint = Color.White)
                    }
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                        }
                        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                            DropdownMenuItem(
                                text = { Text(if (autoCycleVfx) "Auto-Cycle VFX: On" else "Auto-Cycle VFX: Off") },
                                leadingIcon = { Icon(Icons.Default.GraphicEq, null) },
                                onClick = {
                                    autoCycleVfx = !autoCycleVfx
                                    showOverflow = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (sleepRemainingMs > 0L) "Sleep Timer (${(sleepRemainingMs / 60000L) + 1}m)" else "Sleep Timer") },
                                leadingIcon = { Icon(Icons.Default.Timer, null) },
                                onClick = {
                                    showOverflow = false
                                    showSleepDialog = true
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
                                text = { Text("Track Info") },
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

            // Center Floating Album Art Medallion
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                val bassEnergy = (liveBins[0] + liveBins[1] + liveBins[2]) / 3f
                val pulseSize = (132f + bassEnergy * 14f).dp
                Box(
                    modifier = Modifier
                        .size(pulseSize)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.48f))
                        .border(2.dp, Color.White.copy(alpha = 0.25f + bassEnergy * 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (studio.albumArtBitmap != null) {
                        Image(
                            bitmap = studio.albumArtBitmap.asImageBitmap(),
                            contentDescription = "Album Art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Audio Track",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(52.dp)
                        )
                    }
                }
            }

            // Bottom Track Info, VFX Selector, Scrubber & Transport Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // VFX Preset Carousel (All 18 VFX)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("audio_vfx_carousel")
                ) {
                    itemsIndexed(AudioVfxPreset.entries) { idx, preset ->
                        val selected = idx == activeVfxIndex
                        FilterChip(
                            selected = selected,
                            onClick = { activeVfxIndex = idx },
                            label = {
                                Text(
                                    preset.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else Color.White.copy(alpha = 0.8f)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }
                }

                // Title & Artist
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Text(
                        text = studio.title.ifBlank { studio.file.name.substringBeforeLast('.') },
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = buildString {
                        if (studio.artist.isNotBlank()) append(studio.artist)
                        else append(studio.formatHeaderInfo)
                        if (studio.bitrateOrChannels.isNotBlank()) {
                            append(" · ")
                            append(studio.bitrateOrChannels)
                        }
                        if (isVaultPreview) append(" · Vault")
                    }
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Timeline Scrubber
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
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
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(LumaFormatUtils.formatDuration(positionMs), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${playbackSpeed}×",
                                color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.clickable {
                                    playbackSpeed = when {
                                        playbackSpeed < 1f -> 1f
                                        playbackSpeed < 1.25f -> 1.25f
                                        playbackSpeed < 1.5f -> 1.5f
                                        playbackSpeed < 2f -> 2f
                                        else -> 0.75f
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        runCatching {
                                            mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(playbackSpeed)
                                        }
                                    }
                                }
                            )
                            Text(LumaFormatUtils.formatDuration(durationMs), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Transport Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { isShuffle = !isShuffle }) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffle) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
                        )
                    }

                    IconButton(
                        onClick = { playPrevTrack() },
                        modifier = Modifier.size(50.dp)
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous Track", tint = Color.White, modifier = Modifier.size(34.dp))
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
                            .size(66.dp)
                            .background(Color.White, CircleShape)
                            .testTag("audio_play_pause_btn")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    IconButton(
                        onClick = { playNextTrack() },
                        modifier = Modifier.size(50.dp)
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next Track", tint = Color.White, modifier = Modifier.size(34.dp))
                    }

                    IconButton(
                        onClick = {
                            repeatMode = when (repeatMode) {
                                RepeatMode.OFF -> RepeatMode.ALL
                                RepeatMode.ALL -> RepeatMode.ONE
                                RepeatMode.ONE -> RepeatMode.OFF
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = "Repeat Mode",
                            tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }

    // ==================== 5-BAND EQUALIZER + BASS BOOST DIALOG ====================
    if (showEqDialog) {
        val bandLabels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
        val presets = mapOf(
            "Flat" to listOf(0f, 0f, 0f, 0f, 0f),
            "Bass" to listOf(0.8f, 0.5f, 0f, 0.1f, 0.2f),
            "Vocal" to listOf(-0.2f, 0.1f, 0.65f, 0.55f, 0.15f),
            "Rock" to listOf(0.55f, 0.2f, -0.15f, 0.45f, 0.65f),
            "Jazz" to listOf(0.35f, 0.15f, -0.1f, 0.25f, 0.4f)
        )
        AlertDialog(
            onDismissRequest = { showEqDialog = false },
            title = { Text("Equalizer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(presets.keys.toList()) { _, presetName ->
                            FilterChip(
                                selected = selectedEqPreset == presetName,
                                onClick = {
                                    selectedEqPreset = presetName
                                    presets[presetName]?.let { eqBands = it }
                                },
                                label = { Text(presetName) }
                            )
                        }
                    }
                    bandLabels.forEachIndexed { idx, freq ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(freq, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(48.dp))
                            Slider(
                                value = eqBands[idx],
                                onValueChange = { v ->
                                    selectedEqPreset = "Custom"
                                    eqBands = eqBands.toMutableList().also { it[idx] = v }
                                },
                                valueRange = -1f..1f,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Bass", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
                        Slider(
                            value = bassBoostStrength,
                            onValueChange = { bassBoostStrength = it },
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showEqDialog = false }) { Text("Done") } }
        )
    }

    if (showSleepDialog) {
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text("Sleep Timer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 60 to "60 min").forEach { (mins, label) ->
                        FilterChip(
                            selected = sleepTimerMinutes == mins,
                            onClick = {
                                sleepTimerMinutes = mins
                                sleepRemainingMs = mins * 60_000L
                                showSleepDialog = false
                            },
                            label = { Text(label) }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSleepDialog = false }) { Text("Close") } }
        )
    }

    if (showRenameDialog) {
        var newName by remember(studio.file.name) { mutableStateOf(studio.file.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Audio") },
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
            title = { Text("Track Info") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(studio.title.ifBlank { studio.file.name }, fontWeight = FontWeight.SemiBold)
                    if (studio.artist.isNotBlank()) Text("Artist: ${studio.artist}", style = MaterialTheme.typography.bodySmall)
                    if (studio.album.isNotBlank()) Text("Album: ${studio.album}", style = MaterialTheme.typography.bodySmall)
                    Text("Format: ${studio.formatHeaderInfo}", style = MaterialTheme.typography.bodySmall)
                    Text("Quality: ${studio.sampleRateOrResolution}", style = MaterialTheme.typography.bodySmall)
                    if (studio.bitrateOrChannels.isNotBlank()) Text("Bitrate: ${studio.bitrateOrChannels}", style = MaterialTheme.typography.bodySmall)
                    Text("Size: ${LumaFormatUtils.formatBytes(studio.file.sizeBytes)}", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("Done") } }
        )
    }
}

// ==================== 18 DISTINCT PROCEDURAL CANVAS VFX RENDERERS ====================

private fun DrawScope.drawAudioVfxScene(
    preset: AudioVfxPreset,
    bins: FloatArray,
    timeSec: Float,
    primary: Color,
    secondary: Color,
    tertiary: Color
) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h * 0.43f
    val minDim = min(w, h)
    val bass = (bins[0] + bins[1] + bins[2] + bins[3]) / 4f
    val mid = (bins[8] + bins[10] + bins[12] + bins[14]) / 4f

    when (preset) {
        AudioVfxPreset.NEON_SPECTRUM_BARS -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0A0F24), Color(0xFF05070E))))
            val count = 32
            val barW = w / (count * 1.45f)
            val gap = (w - barW * count) / (count + 1)
            for (i in 0 until count) {
                val amp = bins[i].coerceIn(0.05f, 1f)
                val barH = amp * h * 0.42f
                val x = gap + i * (barW + gap)
                val hue = ((i * 8f + timeSec * 40f) % 360f)
                val color = Color.hsv(hue, 0.85f, 1f)
                drawRoundRect(
                    color = color.copy(alpha = 0.88f),
                    topLeft = Offset(x, cy - barH / 2f),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                )
            }
        }

        AudioVfxPreset.RADIAL_SUNBURST -> {
            drawRect(Brush.radialGradient(listOf(Color(0xFF1F1038), Color(0xFF060711)), center = Offset(cx, cy), radius = minDim * 0.85f))
            val rays = 64
            val baseR = minDim * 0.20f
            for (i in 0 until rays) {
                val angle = (i.toFloat() / rays) * (2f * PI.toFloat()) + timeSec * 0.35f
                val bin = bins[i % 32]
                val len = baseR + bin * minDim * 0.28f
                val p1 = Offset(cx + cos(angle) * baseR, cy + sin(angle) * baseR)
                val p2 = Offset(cx + cos(angle) * len, cy + sin(angle) * len)
                val col = Color.hsv((i * 5.6f + timeSec * 45f) % 360f, 0.9f, 1f)
                drawLine(col, p1, p2, strokeWidth = 5f, cap = StrokeCap.Round)
            }
        }

        AudioVfxPreset.LIQUID_AURORA -> {
            drawRect(Color(0xFF060B18))
            for (layer in 0 until 5) {
                val path = Path()
                val baseY = h * (0.25f + layer * 0.11f)
                path.moveTo(0f, baseY)
                for (step in 0..24) {
                    val frac = step / 24f
                    val x = frac * w
                    val amp = bins[(step + layer * 4) % 32] * 95f
                    val y = baseY + sin(frac * 6f + timeSec * (1.2f + layer * 0.35f) + layer) * (34f + amp)
                    path.lineTo(x, y)
                }
                path.lineTo(w, h)
                path.lineTo(0f, h)
                path.close()
                val hue = (155f + layer * 38f + timeSec * 16f) % 360f
                drawPath(path, Color.hsv(hue, 0.8f, 0.95f, alpha = 0.26f))
            }
        }

        AudioVfxPreset.MIRROR_OSCILLOSCOPE -> {
            drawRect(Color(0xFF070A16))
            val topPath = Path()
            val botPath = Path()
            for (i in 0 until 64) {
                val frac = i / 63f
                val x = frac * w
                val amp = bins[i % 32] * h * 0.22f * sin(frac * PI.toFloat())
                val wave = sin(frac * 14f + timeSec * 5f) * amp
                if (i == 0) {
                    topPath.moveTo(x, cy - abs(wave))
                    botPath.moveTo(x, cy + abs(wave))
                } else {
                    topPath.lineTo(x, cy - abs(wave))
                    botPath.lineTo(x, cy + abs(wave))
                }
            }
            drawPath(topPath, primary, style = Stroke(width = 6f, cap = StrokeCap.Round))
            drawPath(botPath, tertiary, style = Stroke(width = 6f, cap = StrokeCap.Round))
        }

        AudioVfxPreset.STARFIELD_WARP -> {
            drawRect(Color(0xFF04060E))
            val stars = 72
            for (i in 0 until stars) {
                val angle = (i * 2.39996f)
                val speed = (0.35f + (i % 7) * 0.14f + bass * 0.9f)
                val depth = ((timeSec * speed + i * 0.17f) % 1f)
                val r1 = depth * minDim * 0.68f
                val r2 = (depth + 0.06f + bass * 0.08f) * minDim * 0.68f
                val col = Color.hsv((200f + i * 3f) % 360f, 0.6f, 1f, alpha = depth.coerceIn(0.15f, 1f))
                drawLine(
                    color = col,
                    start = Offset(cx + cos(angle) * r1, cy + sin(angle) * r1),
                    end = Offset(cx + cos(angle) * r2, cy + sin(angle) * r2),
                    strokeWidth = 2f + depth * 4f,
                    cap = StrokeCap.Round
                )
            }
        }

        AudioVfxPreset.CYBER_GRID -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF14072B), Color(0xFF050712))))
            val horizonY = h * 0.48f
            val gridColor = Color(0xFF38BDF8)
            for (i in -8..8) {
                val topX = cx + i * (w * 0.06f)
                val botX = cx + i * (w * 0.24f)
                drawLine(gridColor.copy(alpha = 0.45f), Offset(topX, horizonY), Offset(botX, h), strokeWidth = 2.5f)
            }
            for (r in 0..9) {
                val prog = ((r / 10f + timeSec * (0.4f + bass * 0.5f)) % 1f)
                val y = horizonY + prog * prog * (h - horizonY)
                drawLine(Color(0xFFF472B6).copy(alpha = prog), Offset(0f, y), Offset(w, y), strokeWidth = 2.5f)
            }
            drawCircle(
                brush = Brush.verticalGradient(listOf(Color(0xFFFDE047), Color(0xFFEC4899))),
                radius = minDim * (0.16f + bass * 0.05f),
                center = Offset(cx, horizonY - minDim * 0.08f),
                alpha = 0.55f
            )
        }

        AudioVfxPreset.PLASMA_RINGS -> {
            drawRect(Color(0xFF090617))
            for (r in 1..9) {
                val energy = bins[(r * 3) % 32]
                val radius = minDim * (0.09f + r * 0.052f) + energy * 24f
                val hue = (r * 36f + timeSec * 55f) % 360f
                drawCircle(
                    color = Color.hsv(hue, 0.85f, 1f, alpha = (0.75f - r * 0.06f).coerceAtLeast(0.15f)),
                    radius = radius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 3f + energy * 8f)
                )
            }
        }

        AudioVfxPreset.DNA_HELIX -> {
            drawRect(Color(0xFF06101B))
            val steps = 28
            for (i in 0 until steps) {
                val frac = i.toFloat() / (steps - 1)
                val y = h * 0.14f + frac * h * 0.62f
                val phase = frac * 7.5f + timeSec * 2.8f
                val spread = minDim * (0.18f + bins[i % 32] * 0.18f)
                val x1 = cx + sin(phase) * spread
                val x2 = cx - sin(phase) * spread
                drawLine(Color.White.copy(alpha = 0.25f), Offset(x1, y), Offset(x2, y), strokeWidth = 2.5f)
                drawCircle(Color(0xFF38BDF8), radius = 6f + bins[i % 32] * 7f, center = Offset(x1, y))
                drawCircle(Color(0xFFA855F7), radius = 6f + bins[(i + 8) % 32] * 7f, center = Offset(x2, y))
            }
        }

        AudioVfxPreset.PARTICLE_FOUNTAIN -> {
            drawRect(Color(0xFF070812))
            for (i in 0 until 48) {
                val seed = i * 1.618f
                val t = ((timeSec * (0.55f + (i % 5) * 0.12f) + seed) % 1f)
                val angle = -PI.toFloat() / 2f + sin(seed * 4f) * 0.85f
                val speed = (0.35f + bins[i % 32] * 0.7f) * h * 0.65f
                val px = cx + cos(angle) * speed * t
                val py = h * 0.78f + sin(angle) * speed * t + 320f * t * t
                val col = Color.hsv((i * 7f + timeSec * 60f) % 360f, 0.9f, 1f, alpha = (1f - t).coerceIn(0f, 1f))
                drawCircle(col, radius = (1f - t) * (6f + bass * 8f), center = Offset(px, py))
            }
        }

        AudioVfxPreset.HEX_PULSE_MATRIX -> {
            drawRect(Color(0xFF050A14))
            val cols = 7
            val rows = 8
            val cellW = w / cols
            val cellH = (h * 0.72f) / rows
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val idx = (r * cols + c) % 32
                    val energy = bins[idx]
                    val x = (c + 0.5f) * cellW + if (r % 2 == 1) cellW * 0.35f else 0f
                    val y = h * 0.12f + (r + 0.5f) * cellH
                    val radius = (min(cellW, cellH) * 0.24f) * (0.45f + energy * 0.9f)
                    val hex = Path()
                    for (v in 0..5) {
                        val a = (v * 60f) * PI.toFloat() / 180f
                        val vx = x + cos(a) * radius
                        val vy = y + sin(a) * radius
                        if (v == 0) hex.moveTo(vx, vy) else hex.lineTo(vx, vy)
                    }
                    hex.close()
                    val hue = (180f + idx * 5f + timeSec * 30f) % 360f
                    drawPath(hex, Color.hsv(hue, 0.85f, 1f, alpha = 0.25f + energy * 0.65f), style = Stroke(width = 3f))
                }
            }
        }

        AudioVfxPreset.FIRE_EQUALIZER -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF1A0705), Color(0xFF090404))))
            val count = 24
            val colW = w / count
            for (i in 0 until count) {
                val amp = bins[i % 32]
                val flameH = amp * h * 0.58f
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFFEF08A), Color(0xFFF97316), Color(0xFFDC2626), Color.Transparent),
                        startY = h * 0.78f - flameH,
                        endY = h * 0.82f
                    ),
                    topLeft = Offset(i * colW + 4f, h * 0.78f - flameH),
                    size = Size((colW - 8f).coerceAtLeast(4f), flameH + 20f)
                )
            }
        }

        AudioVfxPreset.LIQUID_MERCURY_ORB -> {
            drawRect(Color(0xFF080A12))
            val blob = Path()
            val points = 48
            val baseR = minDim * (0.24f + bass * 0.08f)
            for (i in 0..points) {
                val a = (i.toFloat() / points) * 2f * PI.toFloat()
                val wobble = sin(a * 5f + timeSec * 3.5f) * (16f + mid * 28f) +
                    cos(a * 3f - timeSec * 2.4f) * (12f + bins[i % 32] * 24f)
                val r = baseR + wobble
                val px = cx + cos(a) * r
                val py = cy + sin(a) * r
                if (i == 0) blob.moveTo(px, py) else blob.lineTo(px, py)
            }
            blob.close()
            drawPath(
                blob,
                Brush.radialGradient(
                    listOf(Color(0xFFE0F2FE), primary, Color(0xFF1E1B4B)),
                    center = Offset(cx - 24f, cy - 24f),
                    radius = baseR * 1.4f
                )
            )
        }

        AudioVfxPreset.LASER_TUNNEL -> {
            drawRect(Color(0xFF04050C))
            for (layer in 0..10) {
                val prog = ((layer / 11f + timeSec * (0.45f + bass * 0.55f)) % 1f)
                val side = minDim * prog * 1.1f
                val hue = (280f + layer * 22f + timeSec * 50f) % 360f
                drawRoundRect(
                    color = Color.hsv(hue, 0.9f, 1f, alpha = prog),
                    topLeft = Offset(cx - side / 2f, cy - side / 2f),
                    size = Size(side, side),
                    cornerRadius = CornerRadius(18f * prog, 18f * prog),
                    style = Stroke(width = 2f + prog * 5f)
                )
            }
        }

        AudioVfxPreset.KALEIDOSCOPE_BLOOM -> {
            drawRect(Color(0xFF0B061A))
            val petals = 12
            for (p in 0 until petals) {
                val baseAngle = (p.toFloat() / petals) * 2f * PI.toFloat() + timeSec * 0.45f
                val energy = bins[(p * 2) % 32]
                val dist = minDim * (0.14f + energy * 0.24f)
                val px = cx + cos(baseAngle) * dist
                val py = cy + sin(baseAngle) * dist
                val col = Color.hsv((p * 30f + timeSec * 40f) % 360f, 0.85f, 1f, alpha = 0.65f)
                drawCircle(col, radius = minDim * (0.06f + energy * 0.08f), center = Offset(px, py), style = Stroke(width = 4f))
            }
        }

        AudioVfxPreset.SOUNDWAVE_RIBBON -> {
            drawRect(Color(0xFF060814))
            for (strand in 0 until 6) {
                val p = Path()
                for (s in 0..40) {
                    val frac = s / 40f
                    val x = frac * w
                    val amp = bins[s % 32] * 110f
                    val y = cy + sin(frac * 8f + timeSec * (2.2f + strand * 0.4f) + strand * 0.7f) * (30f + amp)
                    if (s == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                val hue = (190f + strand * 28f + timeSec * 25f) % 360f
                drawPath(p, Color.hsv(hue, 0.85f, 1f, alpha = 0.75f), style = Stroke(width = 4f, cap = StrokeCap.Round))
            }
        }

        AudioVfxPreset.SONAR_RADAR -> {
            drawRect(Color(0xFF04120F))
            val green = Color(0xFF10B981)
            for (ring in 1..5) {
                drawCircle(green.copy(alpha = 0.25f), radius = minDim * ring * 0.095f, center = Offset(cx, cy), style = Stroke(2f))
            }
            val sweepAngle = (timeSec * 1.8f) % (2f * PI.toFloat())
            val sweepR = minDim * 0.48f
            drawLine(green, Offset(cx, cy), Offset(cx + cos(sweepAngle) * sweepR, cy + sin(sweepAngle) * sweepR), strokeWidth = 5f)
            for (i in 0 until 16) {
                val e = bins[i * 2]
                if (e > 0.35f) {
                    val a = i * 0.42f + timeSec * 0.1f
                    val d = minDim * (0.12f + (i % 5) * 0.075f)
                    drawCircle(Color(0xFF34D399).copy(alpha = e), radius = 4f + e * 9f, center = Offset(cx + cos(a) * d, cy + sin(a) * d))
                }
            }
        }

        AudioVfxPreset.MATRIX_RAIN -> {
            drawRect(Color(0xFF030C08))
            val cols = 18
            val colW = w / cols
            for (c in 0 until cols) {
                val energy = bins[c % 32]
                val speed = 0.3f + (c % 5) * 0.16f + energy * 0.5f
                val headY = ((timeSec * speed + c * 0.23f) % 1.2f) * h
                for (drop in 0 until 8) {
                    val dy = headY - drop * 26f
                    val alpha = (1f - drop / 8f) * (0.4f + energy * 0.6f)
                    drawRoundRect(
                        color = Color(0xFF22C55E).copy(alpha = alpha.coerceIn(0f, 1f)),
                        topLeft = Offset(c * colW + colW * 0.3f, dy),
                        size = Size(colW * 0.38f, 16f),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
            }
        }

        AudioVfxPreset.ORBITAL_NEBULA -> {
            drawRect(Brush.radialGradient(listOf(Color(0xFF1A103D), Color(0xFF05060E)), center = Offset(cx, cy), radius = minDim * 0.85f))
            for (orbit in 1..6) {
                val e = bins[(orbit * 4) % 32]
                val r = minDim * (0.11f + orbit * 0.058f)
                drawCircle(Color.White.copy(alpha = 0.12f), radius = r, center = Offset(cx, cy), style = Stroke(1.5f))
                val angle = timeSec * (0.6f + orbit * 0.22f) * (if (orbit % 2 == 0) 1f else -1f)
                val ox = cx + cos(angle) * r
                val oy = cy + sin(angle) * r
                val col = Color.hsv((orbit * 52f + timeSec * 30f) % 360f, 0.85f, 1f)
                drawCircle(col.copy(alpha = 0.35f), radius = 14f + e * 18f, center = Offset(ox, oy))
                drawCircle(col, radius = 6f + e * 7f, center = Offset(ox, oy))
            }
        }
    }
}
