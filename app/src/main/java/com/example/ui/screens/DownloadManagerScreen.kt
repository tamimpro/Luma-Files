package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.domain.model.ResolvedMediaManifest
import com.example.domain.model.SniffedWebMedia
import com.example.ui.LumaFormatUtils

@Composable
fun DownloadManagerScreen(
    tasks: List<DownloadTaskEntity>,
    isAdvancedMode: Boolean,
    isResolvingMediaUrl: Boolean,
    resolvedManifest: ResolvedMediaManifest?,
    selectedStreamVariantId: String,
    sniffedWebMedia: List<SniffedWebMedia>,
    onInspectUrl: (String) -> Unit,
    onSelectStreamVariant: (String) -> Unit,
    onStartSmartDownload: (url: String, fileName: String, threads: Int, expectedSha256: String) -> Unit,
    onPauseTask: (Long) -> Unit,
    onResumeTask: (Long) -> Unit,
    onRecoverQueue: () -> Unit,
    onDeleteTask: (DownloadTaskEntity) -> Unit,
    onOpenCompletedFile: (DownloadTaskEntity) -> Unit,
    onMediaDetectedInBrowser: (SniffedWebMedia) -> Unit,
    onClearDetectedMedia: () -> Unit,
    onDownloadSniffedMedia: (SniffedWebMedia) -> Unit
) {
    var activeSegment by remember { mutableIntStateOf(0) } // 0 = Downloads, 1 = Browser Sniffer
    var browserSeedUrl by remember { mutableStateOf("https://m.youtube.com") }

    var urlInput by remember { mutableStateOf("") }
    var customFileName by remember { mutableStateOf("") }
    var expectedSha by remember { mutableStateOf("") }
    var selectedThreads by remember { mutableIntStateOf(4) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("download_manager_screen")
    ) {
        // Clean 2-Way Segmented Switch: Downloads | Browser
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SegmentedButton(
                selected = activeSegment == 0,
                onClick = { activeSegment = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = { Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp)) },
                label = { Text("Downloads (${tasks.size})") },
                modifier = Modifier.testTag("dl_segment_downloads")
            )
            SegmentedButton(
                selected = activeSegment == 1,
                onClick = { activeSegment = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = { Icon(Icons.Default.Language, null, modifier = Modifier.size(16.dp)) },
                label = {
                    val cnt = sniffedWebMedia.size
                    Text(if (cnt > 0) "Browser ($cnt)" else "Browser")
                },
                modifier = Modifier.testTag("dl_segment_browser")
            )
        }

        if (activeSegment == 1) {
            WebSnifferBrowserPane(
                initialUrl = browserSeedUrl,
                detectedMedia = sniffedWebMedia,
                onMediaDetected = onMediaDetectedInBrowser,
                onClearDetected = onClearDetectedMedia,
                onDownloadSniffedItem = { item ->
                    onDownloadSniffedMedia(item)
                    activeSegment = 0
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Smart URL Input Card
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                placeholder = { Text("Paste video or file link…") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dl_url_input")
                            )

                            if (isAdvancedMode) {
                                OutlinedTextField(
                                    value = customFileName,
                                    onValueChange = { customFileName = it },
                                    placeholder = { Text("Custom file name (optional)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Threads", style = MaterialTheme.typography.labelMedium)
                                    listOf(1, 4, 8).forEach { t ->
                                        FilterChip(
                                            selected = selectedThreads == t,
                                            onClick = { selectedThreads = t },
                                            label = { Text("${t}×") }
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = expectedSha,
                                    onValueChange = { expectedSha = it },
                                    placeholder = { Text("Expected SHA-256 (optional)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { if (urlInput.isNotBlank()) onInspectUrl(urlInput) },
                                    enabled = urlInput.isNotBlank() && !isResolvingMediaUrl,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("dl_detect_btn")
                                ) {
                                    if (isResolvingMediaUrl) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Detect")
                                    }
                                }
                                Button(
                                    onClick = {
                                        if (urlInput.isNotBlank()) {
                                            onStartSmartDownload(urlInput, customFileName, selectedThreads, expectedSha)
                                            urlInput = ""
                                            customFileName = ""
                                            expectedSha = ""
                                        }
                                    },
                                    enabled = urlInput.isNotBlank() && !isResolvingMediaUrl,
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .testTag("dl_start_btn")
                                ) {
                                    Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Download")
                                }
                            }

                            // Resolved Manifest Preview + Variant Picker (or Browser Sniffer Handoff)
                            resolvedManifest?.let { manifest ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "${manifest.platformName} · ${manifest.title}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        if (manifest.requiresBrowserSniffer || manifest.variants.isEmpty()) {
                                            Text(
                                                text = "Play this link in Browser to capture the stream.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Button(
                                                onClick = {
                                                    browserSeedUrl = manifest.sourceUrl
                                                    activeSegment = 1
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.Language, null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Open in Browser")
                                            }
                                        } else {
                                            manifest.variants.forEach { variant ->
                                                val sel = variant.variantId == selectedStreamVariantId
                                                FilterChip(
                                                    selected = sel,
                                                    onClick = { onSelectStreamVariant(variant.variantId) },
                                                    label = {
                                                        val audioTag = if (variant.requiresAudioVideoMux) " + Audio" else ""
                                                        val sizeTag = if (variant.estimatedSizeBytes > 0) " · ${LumaFormatUtils.formatBytes(variant.estimatedSizeBytes)}" else ""
                                                        Text(
                                                            "${variant.resolutionLabel} · ${variant.containerExtension.uppercase()}$audioTag$sizeTag",
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Queue Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Queue",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(onClick = onRecoverQueue) {
                            Icon(Icons.Default.Refresh, contentDescription = "Resume Pending")
                        }
                    }
                }

                if (tasks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "No downloads yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(tasks, key = { it.id }) { task ->
                        DownloadTaskRowCard(
                            task = task,
                            onPause = { onPauseTask(task.id) },
                            onResume = { onResumeTask(task.id) },
                            onOpen = { onOpenCompletedFile(task) },
                            onDelete = { onDeleteTask(task) }
                        )
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun DownloadTaskRowCard(
    task: DownloadTaskEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val progress = if (task.totalBytes > 0L) {
        (task.downloadedBytes.toFloat() / task.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else if (task.status == "COMPLETED") 1f else 0f

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = task.status == "COMPLETED", onClick = onOpen)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.fileName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val meta = buildString {
                        append(task.status.lowercase().replaceFirstChar { it.uppercase() })
                        if (task.totalBytes > 0) {
                            append(" · ${LumaFormatUtils.formatBytes(task.downloadedBytes)} / ${LumaFormatUtils.formatBytes(task.totalBytes)}")
                        } else if (task.downloadedBytes > 0) {
                            append(" · ${LumaFormatUtils.formatBytes(task.downloadedBytes)}")
                        }
                        if (task.speedBytesPerSec > 0 && task.status == "DOWNLOADING") {
                            append(" · ${LumaFormatUtils.formatBytes(task.speedBytesPerSec)}/s")
                        }
                    }
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (task.status == "FAILED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (task.status) {
                        "DOWNLOADING", "MUXING", "QUEUED" -> {
                            IconButton(onClick = onPause) {
                                Icon(Icons.Default.Pause, contentDescription = "Pause")
                            }
                        }
                        "PAUSED", "FAILED" -> {
                            IconButton(onClick = onResume) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                            }
                        }
                        "COMPLETED" -> {
                            IconButton(onClick = onOpen) {
                                Icon(Icons.Default.OpenInNew, contentDescription = "Open", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (task.status == "DOWNLOADING" || task.status == "MUXING" || task.status == "PAUSED") {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }

            if (task.status == "FAILED" && task.errorExactReason.isNotBlank()) {
                Text(
                    text = task.errorExactReason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
