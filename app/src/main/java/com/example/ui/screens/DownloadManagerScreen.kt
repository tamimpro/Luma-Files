package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.DownloadTaskEntity
import com.example.domain.engine.DownloadPreset
import com.example.domain.model.MediaStreamVariant
import com.example.domain.model.ResolvedMediaManifest
import com.example.ui.LumaFormatUtils
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldVault
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun DownloadManagerScreen(
    tasks: List<DownloadTaskEntity>,
    presets: List<DownloadPreset>,
    isAdvancedMode: Boolean,
    isResolvingMediaUrl: Boolean,
    resolvedManifest: ResolvedMediaManifest?,
    selectedStreamVariantId: String?,
    onInspectUrl: (String) -> Unit,
    onSelectStreamVariant: (String) -> Unit,
    onStartSmartDownload: (url: String, customFileName: String, threads: Int, expectedSha256: String) -> Unit,
    onPauseTask: (Long) -> Unit,
    onResumeTask: (Long) -> Unit,
    onRecoverQueue: () -> Unit,
    onDeleteTask: (DownloadTaskEntity) -> Unit,
    onOpenCompletedFile: (DownloadTaskEntity) -> Unit
) {
    var urlInput by remember { mutableStateOf("") }
    var fileNameInput by remember { mutableStateOf("") }
    var expectedSha256Input by remember { mutableStateOf("") }
    var selectedThreads by remember { mutableIntStateOf(4) }

    LaunchedEffect(resolvedManifest) {
        if (resolvedManifest != null && fileNameInput.isBlank()) {
            fileNameInput = resolvedManifest.suggestedFileName
        }
    }

    val activeSpeedTotal = remember(tasks) {
        tasks.filter { it.status == "DOWNLOADING" || it.status.startsWith("MUXING") }
            .sumOf { it.speedBytesPerSec }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("download_manager_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Live Engine Header Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isAdvancedMode) Icons.Default.Tune else Icons.Default.Bolt,
                            contentDescription = "Persistent Downloader",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isAdvancedMode) {
                                    "Advanced Stream & Multi-Thread Engine"
                                } else {
                                    "Smart Lossless Media & File Downloader"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isAdvancedMode) {
                                    "Manual Codec/Bitrate/Resolution • RFC-7233 Byte-Range Resume • Lossless Muxer"
                                } else {
                                    "Auto-Detects Format • Highest Original Quality • Synced Audio+Video • No Watermark"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.25f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = LumaFormatUtils.formatSpeed(activeSpeedTotal),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }

        // 2. Download Composer Card (Adapts automatically between Default Mode and Advanced Mode)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isAdvancedMode) {
                                    "Advanced Stream Inspector & Queue Composer"
                                } else {
                                    "Download Video, Audio, or Direct File"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Supports YouTube, TikTok, Instagram, Facebook, X, Vimeo & Direct HTTP/HTTPS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isAdvancedMode) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            }
                        ) {
                            Text(
                                text = if (isAdvancedMode) "ADVANCED MODE" else "DEFAULT AUTO",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isAdvancedMode) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        placeholder = {
                            Text("Paste video link (YouTube, TikTok, Instagram, FB) or direct file URL...")
                        },
                        label = { Text("Source URL") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("download_url_input")
                    )

                    if (!isAdvancedMode) {
                        // DEFAULT MODE: Streamlined, user-friendly automatic highest-quality experience
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HighQuality,
                                    contentDescription = null,
                                    tint = EmeraldVault,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Automatic Lossless Stream & Format Selection",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Automatically selects highest original resolution without watermark + best audio stream and merges them with zero re-encoding.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = fileNameInput,
                            onValueChange = { fileNameInput = it },
                            placeholder = { Text("Leave blank to auto-detect title & extension") },
                            label = { Text("Custom Filename (Optional)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("download_filename_input")
                        )

                        Button(
                            onClick = {
                                if (urlInput.isNotBlank()) {
                                    onStartSmartDownload(urlInput.trim(), fileNameInput.trim(), 4, "")
                                }
                            },
                            enabled = urlInput.isNotBlank() && !isResolvingMediaUrl,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("start_download_btn")
                        ) {
                            if (isResolvingMediaUrl) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Resolving Best Lossless Stream...")
                            } else {
                                Icon(Icons.Default.CloudDownload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download Highest Quality (Auto Audio+Video)")
                            }
                        }
                    } else {
                        // ADVANCED MODE: Full Stream Inspector, Manual Resolution/Codec/Bitrate/Audio Selection, Threads 1..8, SHA-256
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (urlInput.isNotBlank()) {
                                        onInspectUrl(urlInput.trim())
                                    }
                                },
                                enabled = urlInput.isNotBlank() && !isResolvingMediaUrl,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("inspect_streams_btn")
                            ) {
                                if (isResolvingMediaUrl) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Inspecting...")
                                } else {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Inspect Streams & Codecs")
                                }
                            }

                            OutlinedButton(
                                onClick = onRecoverQueue,
                                modifier = Modifier.testTag("recover_queue_btn")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Recover Queue")
                            }
                        }

                        // Resolved Stream Manifest & Manual Variant Picker
                        if (resolvedManifest != null) {
                            ResolvedManifestInspectorCard(
                                manifest = resolvedManifest,
                                selectedVariantId = selectedStreamVariantId,
                                onSelectVariant = { variant ->
                                    onSelectStreamVariant(variant.variantId)
                                }
                            )
                        }

                        OutlinedTextField(
                            value = fileNameInput,
                            onValueChange = { fileNameInput = it },
                            placeholder = { Text("Auto-detected from stream manifest or Content-Disposition") },
                            label = { Text("Target Output Filename") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("download_filename_input")
                        )

                        OutlinedTextField(
                            value = expectedSha256Input,
                            onValueChange = { expectedSha256Input = it.trim() },
                            placeholder = { Text("Optional 64-char hex SHA-256 digest for strict verification") },
                            label = { Text("Expected SHA-256 Checksum (Optional)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("download_sha256_input")
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Parallel RFC-7233 Byte-Range Threads:",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(1, 2, 4, 6, 8).forEach { count ->
                                    FilterChip(
                                        selected = selectedThreads == count,
                                        onClick = { selectedThreads = count },
                                        label = { Text("${count}x Threads") }
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (urlInput.isNotBlank()) {
                                    onStartSmartDownload(
                                        urlInput.trim(),
                                        fileNameInput.trim(),
                                        selectedThreads,
                                        expectedSha256Input
                                    )
                                }
                            },
                            enabled = urlInput.isNotBlank() && !isResolvingMediaUrl,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("start_download_btn")
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start ${selectedThreads}-Thread Resumable Download")
                        }
                    }
                }
            }
        }

        // 3. Verifiable Benchmark & Stream Presets (Shown in Advanced Mode or when queue is empty)
        if (isAdvancedMode || tasks.isEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "VERIFIABLE STREAM & MULTI-THREAD PRESETS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    presets.forEach { preset ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    urlInput = preset.url
                                    fileNameInput = preset.suggestedFileName
                                    selectedThreads = preset.recommendedThreads
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preset.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = preset.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        onStartSmartDownload(
                                            preset.url,
                                            preset.suggestedFileName,
                                            preset.recommendedThreads,
                                            ""
                                        )
                                    }
                                ) {
                                    Text("Download")
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Persistent Download Queue & Completed Transfers
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PERSISTENT DOWNLOAD QUEUE (${tasks.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Auto-Resume & Checkpoint Recovery Active",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldVault
                )
            }
        }

        if (tasks.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "No downloads",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "Download Queue is Empty",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Paste a video or file URL above to start a persistent, resumable download.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(tasks, key = { it.id }) { task ->
                DownloadTaskCard(
                    task = task,
                    onPause = { onPauseTask(task.id) },
                    onResume = { onResumeTask(task.id) },
                    onDelete = { onDeleteTask(task) },
                    onOpen = { onOpenCompletedFile(task) }
                )
            }
        }
    }
}

@Composable
private fun ResolvedManifestInspectorCard(
    manifest: ResolvedMediaManifest,
    selectedVariantId: String?,
    onSelectVariant: (MediaStreamVariant) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("resolved_manifest_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${manifest.platformName} • ${manifest.title}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (manifest.creatorAttribution.isNotBlank()) {
                        Text(
                            text = "Creator Attribution Preserved: ${manifest.creatorAttribution}",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldVault
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = "Watermark-Free Verified",
                    tint = EmeraldVault,
                    modifier = Modifier.size(20.dp)
                )
            }

            HorizontalDivider()

            Text(
                text = "Select Resolution, Codec, Bitrate & Audio Stream:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )

            manifest.variants.forEach { variant ->
                val isSelected = variant.variantId == selectedVariantId
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectVariant(variant) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = variant.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            if (variant.isWatermarkFree) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = EmeraldVault.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "NO WATERMARK",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldVault,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Video: ${variant.videoCodec} (${variant.widthPx}×${variant.heightPx} @ ${variant.frameRateFps}fps) • Audio: ${variant.audioCodec} (${variant.audioBitrateKbps} kbps) • ${if (variant.requiresAudioVideoMux) "Lossless Audio+Video Mux" else "Progressive Synced Stream"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTaskEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit
) {
    val progress = if (task.totalBytes > 0) {
        (task.downloadedBytes.toFloat() / task.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else if (task.status == "COMPLETED") 1f else 0f

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = task.url,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (task.status) {
                        "DOWNLOADING" -> {
                            IconButton(
                                onClick = onPause,
                                modifier = Modifier.testTag("pause_download_${task.id}")
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = "Pause Download")
                            }
                        }
                        "PAUSED", "FAILED", "QUEUED" -> {
                            IconButton(
                                onClick = onResume,
                                modifier = Modifier.testTag("resume_download_${task.id}")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Resume Download")
                            }
                        }
                        "COMPLETED" -> {
                            Button(
                                onClick = onOpen,
                                modifier = Modifier.testTag("open_downloaded_${task.id}")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open")
                            }
                        }
                    }

                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Task",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Segmented Multi-Thread Visual Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val segments = task.threadCount.coerceIn(1, 8)
                for (i in 0 until segments) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = if (task.status == "COMPLETED") EmeraldVault else ElectricCyan,
                        modifier = Modifier
                            .weight(1f)
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${LumaFormatUtils.formatBytes(task.downloadedBytes)} / ${if (task.totalBytes > 0) LumaFormatUtils.formatBytes(task.totalBytes) else "Stream"} • ${task.threadCount}T",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when (task.status) {
                        "DOWNLOADING" -> LumaFormatUtils.formatSpeed(task.speedBytesPerSec)
                        "COMPLETED" -> "COMPLETED (${LumaFormatUtils.formatSpeed(task.speedBytesPerSec)} avg)"
                        else -> task.status
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = when (task.status) {
                        "COMPLETED" -> EmeraldVault
                        "FAILED" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }

            if (task.sha256Checksum.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldVault,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SHA-256: ${task.sha256Checksum}",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (task.errorExactReason.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = task.errorExactReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}
