package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.domain.engine.DownloadPreset
import com.example.ui.LumaFormatUtils
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldVault
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun DownloadManagerScreen(
    tasks: List<DownloadTaskEntity>,
    presets: List<DownloadPreset>,
    onStartDownload: (url: String, fileName: String, threads: Int) -> Unit,
    onPauseTask: (Long) -> Unit,
    onResumeTask: (Long) -> Unit,
    onDeleteTask: (DownloadTaskEntity) -> Unit,
    onOpenCompletedFile: (DownloadTaskEntity) -> Unit
) {
    var urlInput by remember { mutableStateOf(presets.firstOrNull()?.url ?: "") }
    var fileNameInput by remember { mutableStateOf(presets.firstOrNull()?.suggestedFileName ?: "") }
    var selectedThreads by remember { mutableIntStateOf(4) }

    val activeSpeedTotal = remember(tasks) {
        tasks.filter { it.status == "DOWNLOADING" }.sumOf { it.speedBytesPerSec }
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
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Super Fast Downloader",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Super-Fast Multi-Thread Engine",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Parallel RFC-7233 Byte-Range Streams • Auto SHA-256 Check",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }

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

        // 2. New Download Composer Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "New High-Speed Download Task",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Direct File URL (HTTP / HTTPS / Luma Benchmark)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("download_url_input")
                    )

                    OutlinedTextField(
                        value = fileNameInput,
                        onValueChange = { fileNameInput = it },
                        label = { Text("Save As File Name (in Luma_Workspace/Downloads)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("download_filename_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Parallel Threads:",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1, 2, 4).forEach { count ->
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
                                onStartDownload(urlInput, fileNameInput, selectedThreads)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("start_download_btn")
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start $selectedThreads-Thread Lossless Download")
                    }
                }
            }
        }

        // 3. One-Tap Benchmark & Public File Presets
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ONE-TAP VERIFIABLE BENCHMARK & DOCUMENT PRESETS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                presets.forEach { preset ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
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
                                    onStartDownload(preset.url, preset.suggestedFileName, preset.recommendedThreads)
                                }
                            ) {
                                Text("Run (${preset.recommendedThreads}T)")
                            }
                        }
                    }
                }
            }
        }

        // 4. Active & Completed Download Queue
        item {
            Text(
                text = "DOWNLOAD QUEUE & COMPLETED TRANSFERS (${tasks.size})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                            text = "No Active or Completed Downloads",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Tap any preset above or enter a direct file URL to test multi-threaded downloading.",
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
                            IconButton(onClick = onPause) {
                                Icon(Icons.Default.Pause, contentDescription = "Pause Download")
                            }
                        }
                        "PAUSED", "FAILED" -> {
                            IconButton(onClick = onResume) {
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
                                Text("Open File")
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
                val segments = task.threadCount.coerceIn(1, 4)
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
                    text = "${LumaFormatUtils.formatBytes(task.downloadedBytes)} / ${if (task.totalBytes > 0) LumaFormatUtils.formatBytes(task.totalBytes) else "Stream"} • ${task.threadCount} Threads",
                    style = MaterialTheme.typography.labelMedium
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
                            text = "Exact Cause: ${task.errorExactReason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}
