package com.example.ui.screens

import android.media.MediaPlayer
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.domain.model.FileNode
import com.example.domain.model.StudioContent
import com.example.ui.LumaFormatUtils
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.delay

@Composable
fun StudioEditorModal(
    studioContent: StudioContent,
    onClose: () -> Unit,
    onSaveText: (FileNode, String, String) -> Unit,
    onSaveDocx: (FileNode, List<String>) -> Unit,
    onSavePdfNotes: (FileNode, String) -> Unit,
    onNavigatePdfPage: (FileNode, Int) -> Unit,
    onExtractZip: (FileNode) -> Unit,
    onAddZipEntry: (FileNode, String, String) -> Unit,
    onSwitchToHex: (FileNode, Long) -> Unit,
    onPatchHexByte: (FileNode, Long, String, Long) -> Unit,
    onOpenExternal: (FileNode) -> Unit
) {
    BackHandler { onClose() }

    val activeNode = when (studioContent) {
        is StudioContent.TextCodeEditor -> studioContent.file
        is StudioContent.DocxEditor -> studioContent.file
        is StudioContent.PdfViewer -> studioContent.file
        is StudioContent.ZipArchiveManager -> studioContent.file
        is StudioContent.MediaStudio -> studioContent.file
        is StudioContent.ImageStudio -> studioContent.file
        is StudioContent.HexBinaryStudio -> studioContent.file
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("luma_studio_modal"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Studio Toolbar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("close_studio_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close Studio"
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeNode.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Text(
                            text = "Luma Studio (${activeNode.category.badgeText}) • ${LumaFormatUtils.formatBytes(activeNode.sizeBytes)} • 100% Lossless",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (studioContent !is StudioContent.HexBinaryStudio) {
                        IconButton(
                            onClick = { onSwitchToHex(activeNode, 0L) },
                            modifier = Modifier.testTag("switch_to_hex_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = "Inspect Raw Hex",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    IconButton(
                        onClick = { onOpenExternal(activeNode) },
                        modifier = Modifier.testTag("studio_open_external_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open with External App",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Body Content per Format
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (studioContent) {
                    is StudioContent.TextCodeEditor -> TextAndMarkdownStudioPane(
                        content = studioContent,
                        onSave = { updatedText, encoding ->
                            onSaveText(studioContent.file, updatedText, encoding)
                        }
                    )

                    is StudioContent.DocxEditor -> DocxStudioPane(
                        content = studioContent,
                        onSave = { paragraphs ->
                            onSaveDocx(studioContent.file, paragraphs)
                        }
                    )

                    is StudioContent.PdfViewer -> PdfStudioPane(
                        content = studioContent,
                        onChangePage = { newIdx -> onNavigatePdfPage(studioContent.file, newIdx) },
                        onSaveNotes = { notes -> onSavePdfNotes(studioContent.file, notes) }
                    )

                    is StudioContent.ZipArchiveManager -> ZipStudioPane(
                        content = studioContent,
                        onExtractAll = { onExtractZip(studioContent.file) },
                        onAddEntry = { name, body -> onAddZipEntry(studioContent.file, name, body) }
                    )

                    is StudioContent.MediaStudio -> MediaStudioPane(content = studioContent)

                    is StudioContent.ImageStudio -> ImageStudioPane(content = studioContent)

                    is StudioContent.HexBinaryStudio -> HexBinaryStudioPane(
                        content = studioContent,
                        onChangeWindow = { nextOffset -> onSwitchToHex(studioContent.file, nextOffset) },
                        onPatchByte = { offset, hexVal ->
                            onPatchHexByte(studioContent.file, offset, hexVal, studioContent.windowOffset)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TextAndMarkdownStudioPane(
    content: StudioContent.TextCodeEditor,
    onSave: (String, String) -> Unit
) {
    var editedText by remember(content.file.path, content.textContent) {
        mutableStateOf(content.textContent)
    }
    var encoding by remember(content.encoding) { mutableStateOf(content.encoding) }
    var showMarkdownPreview by remember { mutableStateOf(false) }
    var showFindReplace by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }

    val lineCount = remember(editedText) { editedText.count { it == '\n' } + 1 }

    Column(modifier = Modifier.fillMaxSize()) {
        // Editor Sub-toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (content.isMarkdown) {
                    FilterChip(
                        selected = showMarkdownPreview,
                        onClick = { showMarkdownPreview = !showMarkdownPreview },
                        label = { Text(if (showMarkdownPreview) "Edit Source" else "Markdown Preview") },
                        leadingIcon = {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }
                FilterChip(
                    selected = showFindReplace,
                    onClick = { showFindReplace = !showFindReplace },
                    label = { Text("Find & Replace") },
                    leadingIcon = {
                        Icon(Icons.Default.FindReplace, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }

            Button(
                onClick = { onSave(editedText, encoding) },
                modifier = Modifier.testTag("save_text_studio_btn")
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Lossless")
            }
        }

        if (showFindReplace) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = findQuery,
                    onValueChange = { findQuery = it },
                    label = { Text("Find") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = replaceQuery,
                    onValueChange = { replaceQuery = it },
                    label = { Text("Replace") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (findQuery.isNotEmpty()) {
                            editedText = editedText.replace(findQuery, replaceQuery)
                        }
                    }
                ) {
                    Text("All")
                }
            }
        }

        // Info Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Lines: $lineCount • Chars: ${editedText.length} • Encoding: $encoding",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (content.isTruncatedPreview) {
                Text(
                    text = "512KB Stream Window Active",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        HorizontalDivider()

        if (showMarkdownPreview && content.isMarkdown) {
            // Rendered Markdown Preview
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                editedText.lines().forEach { line ->
                    when {
                        line.startsWith("# ") -> Text(
                            text = line.removePrefix("# "),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        line.startsWith("## ") -> Text(
                            text = line.removePrefix("## "),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        line.startsWith("- ") -> Row {
                            Text("• ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(line.removePrefix("- "), style = MaterialTheme.typography.bodyLarge)
                        }
                        else -> Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else {
            // Line-numbered Code & Text Editor
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .width(36.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    for (i in 1..lineCount.coerceAtMost(999)) {
                        Text(
                            text = "$i",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    }
                }
                BasicTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    textStyle = TextStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("studio_text_editor_field")
                )
            }
        }
    }
}

@Composable
private fun DocxStudioPane(
    content: StudioContent.DocxEditor,
    onSave: (List<String>) -> Unit
) {
    val paragraphs = remember(content.file.path, content.paragraphs) {
        mutableStateListOf(*content.paragraphs.toTypedArray())
    }
    var newParagraphText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
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
                        text = "Word OOXML (.docx) Archive Studio",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${content.xmlEntriesCount} XML Parts • ${content.embeddedMediaCount} Media Streams • Direct word/document.xml Editor",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Button(
                    onClick = { onSave(paragraphs.toList()) },
                    modifier = Modifier.testTag("save_docx_btn")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save .DOCX")
                }
            }
        }

        // Add paragraph row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newParagraphText,
                onValueChange = { newParagraphText = it },
                label = { Text("Add New Paragraph (<w:p>)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (newParagraphText.isNotBlank()) {
                        paragraphs.add(newParagraphText.trim())
                        newParagraphText = ""
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Paragraph")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(paragraphs) { index, para ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Paragraph #${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (paragraphs.size > 1) {
                                IconButton(
                                    onClick = { paragraphs.removeAt(index) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove paragraph",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = para,
                            onValueChange = { paragraphs[index] = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfStudioPane(
    content: StudioContent.PdfViewer,
    onChangePage: (Int) -> Unit,
    onSaveNotes: (String) -> Unit
) {
    var notes by remember(content.annotationNotes) { mutableStateOf(content.annotationNotes) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Page Navigation Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { onChangePage(content.currentPageIndex - 1) },
                enabled = content.currentPageIndex > 0
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Page")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Prev")
            }

            Text(
                text = "Page ${content.currentPageIndex + 1} of ${content.pageCount} (Native High-DPI)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedButton(
                onClick = { onChangePage(content.currentPageIndex + 1) },
                enabled = content.currentPageIndex < content.pageCount - 1
            ) {
                Text("Next")
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Page")
            }
        }

        // Rendered PDF Page Bitmap
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (content.renderedPageBitmap != null) {
                Image(
                    bitmap = content.renderedPageBitmap.asImageBitmap(),
                    contentDescription = "PDF Page ${content.currentPageIndex + 1}",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Unable to render PDF bitmap.")
                }
            }
        }

        // PDF Annotation & Notes Companion Editor
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                    Text(
                        text = "PDF Companion Annotations & Notes",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Button(onClick = { onSaveNotes(notes) }) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Notes")
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("Add review notes or annotations for this PDF...") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ZipStudioPane(
    content: StudioContent.ZipArchiveManager,
    onExtractAll: () -> Unit,
    onAddEntry: (String, String) -> Unit
) {
    var newEntryName by remember { mutableStateOf("notes/added_in_luma.txt") }
    var newEntryContent by remember { mutableStateOf("Added losslessly by Luma Files ZIP Studio.") }
    var showAddEntryForm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                    Column {
                        Text(
                            text = "${content.entries.size} Archive Entries",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Uncompressed: ${LumaFormatUtils.formatBytes(content.totalUncompressedBytes)} • Saved ${content.compressionRatioPercent}%",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showAddEntryForm = !showAddEntryForm }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pack File")
                        }
                        Button(
                            onClick = onExtractAll,
                            modifier = Modifier.testTag("extract_zip_btn")
                        ) {
                            Icon(Icons.Default.Unarchive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Extract All")
                        }
                    }
                }

                if (showAddEntryForm) {
                    HorizontalDivider()
                    OutlinedTextField(
                        value = newEntryName,
                        onValueChange = { newEntryName = it },
                        label = { Text("Entry Path inside ZIP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newEntryContent,
                        onValueChange = { newEntryContent = it },
                        label = { Text("Entry Text Content") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (newEntryName.isNotBlank()) {
                                onAddEntry(newEntryName.trim(), newEntryContent)
                                showAddEntryForm = false
                            }
                        }
                    ) {
                        Text("Commit Entry & Repack ZIP")
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(content.entries) { entry ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Archive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Raw: ${LumaFormatUtils.formatBytes(entry.uncompressedSize)} -> Compressed: ${LumaFormatUtils.formatBytes(entry.compressedSize)} • CRC32: 0x${entry.crc32Hex}",
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
private fun MediaStudioPane(content: StudioContent.MediaStudio) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosMs by remember { mutableIntStateOf(0) }
    var totalDurationMs by remember { mutableIntStateOf(content.durationMs.toInt().coerceAtLeast(2000)) }
    val mediaPlayerRef = remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(content.file.path) {
        if (!content.isVideo) {
            runCatching {
                val mp = MediaPlayer().apply {
                    setDataSource(content.file.path)
                    prepare()
                    totalDurationMs = duration.coerceAtLeast(1000)
                    setOnCompletionListener {
                        isPlaying = false
                        currentPosMs = 0
                    }
                }
                mediaPlayerRef.value = mp
            }
        }
        onDispose {
            runCatching {
                mediaPlayerRef.value?.stop()
                mediaPlayerRef.value?.release()
            }
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val mp = mediaPlayerRef.value
            if (mp != null && mp.isPlaying) {
                currentPosMs = mp.currentPosition
            }
            delay(150)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (content.isVideo) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoPath(content.file.path)
                                setOnPreparedListener { mp ->
                                    totalDurationMs = mp.duration.coerceAtLeast(1000)
                                    start()
                                    isPlaying = true
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Audiotrack,
                        contentDescription = "Lossless Audio",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                }

                Text(
                    text = content.file.name,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "${content.sampleRateOrResolution} • ${content.bitrateOrChannels}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = content.formatHeaderInfo,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                val progress = if (totalDurationMs > 0) {
                    (currentPosMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )

                if (!content.isVideo) {
                    Button(
                        onClick = {
                            val mp = mediaPlayerRef.value
                            if (mp != null) {
                                if (mp.isPlaying) {
                                    mp.pause()
                                    isPlaying = false
                                } else {
                                    mp.start()
                                    isPlaying = true
                                }
                            }
                        },
                        modifier = Modifier.testTag("audio_play_pause_btn")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isPlaying) "Pause Lossless Stream" else "Play Lossless Audio")
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageStudioPane(content: StudioContent.ImageStudio) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (content.bitmap != null) {
                Image(
                    bitmap = content.bitmap.asImageBitmap(),
                    contentDescription = content.file.name,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Unable to decode image preview.")
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Lossless Bitmap & EXIF Telemetry",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                content.exifSummary.forEach { (k, v) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = v, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun HexBinaryStudioPane(
    content: StudioContent.HexBinaryStudio,
    onChangeWindow: (Long) -> Unit,
    onPatchByte: (Long, String) -> Unit
) {
    var patchOffsetHex by remember { mutableStateOf("%X".format(content.windowOffset)) }
    var patchValueHex by remember { mutableStateOf("4C") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Hash & Window Telemetry Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "MD5: ${content.md5Hash}",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "SHA-256: ${content.sha256Hash}",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onChangeWindow((content.windowOffset - 2048).coerceAtLeast(0L)) },
                        enabled = content.windowOffset > 0
                    ) {
                        Text("Prev 2KB")
                    }
                    Text(
                        text = "Offset 0x${"%08X".format(content.windowOffset)} / ${content.totalSize} B",
                        style = MaterialTheme.typography.labelMedium
                    )
                    OutlinedButton(
                        onClick = { onChangeWindow(content.windowOffset + 2048) },
                        enabled = content.windowOffset + content.windowSize < content.totalSize
                    ) {
                        Text("Next 2KB")
                    }
                }
            }
        }

        // Direct Single-Byte Hex Patch Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = patchOffsetHex,
                onValueChange = { patchOffsetHex = it },
                label = { Text("Byte Offset (Hex)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = patchValueHex,
                onValueChange = { if (it.length <= 2) patchValueHex = it },
                label = { Text("New Byte (00-FF)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val offsetLong = patchOffsetHex.trim().removePrefix("0x").toLongOrNull(16)
                    if (offsetLong != null && patchValueHex.isNotBlank()) {
                        onPatchByte(offsetLong, patchValueHex)
                    }
                }
            ) {
                Text("Patch Byte")
            }
        }

        // Hex & ASCII Grid
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(content.rows) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = row.offsetHex,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = row.byteValuesHex.joinToString(" "),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "| ${row.asciiPreview}",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}
