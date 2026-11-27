package com.example.ui.screens.viewers

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.FileNode
import com.example.domain.model.StudioContent
import com.example.domain.model.ZipEntryPreview
import com.example.ui.LumaFormatUtils
import kotlinx.coroutines.launch

// ==================== SHARED CLEAN VIEWER HEADER ====================

@Composable
fun ProViewerTopBar(
    title: String,
    subtitle: String,
    isVaultPreview: Boolean,
    isEditMode: Boolean? = null,
    onToggleEditMode: (() -> Unit)? = null,
    onClose: () -> Unit,
    onShare: (() -> Unit)? = null,
    onOpenExternal: (() -> Unit)? = null,
    onSwitchHex: (() -> Unit)? = null,
    extraActions: @Composable () -> Unit = {}
) {
    var showOverflow by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(onClick = onClose, modifier = Modifier.testTag("viewer_back_btn")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isVaultPreview) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Vault",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                extraActions()
                if (isEditMode != null && onToggleEditMode != null) {
                    IconButton(
                        onClick = onToggleEditMode,
                        modifier = Modifier.testTag("viewer_toggle_edit_btn")
                    ) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Default.Visibility else Icons.Default.Edit,
                            contentDescription = if (isEditMode) "Read Mode" else "Edit Mode",
                            tint = if (isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                if (!isVaultPreview && (onShare != null || onOpenExternal != null || onSwitchHex != null)) {
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                            if (onShare != null) {
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    leadingIcon = { Icon(Icons.Default.Share, null) },
                                    onClick = { showOverflow = false; onShare() }
                                )
                            }
                            if (onOpenExternal != null) {
                                DropdownMenuItem(
                                    text = { Text("Open with…") },
                                    leadingIcon = { Icon(Icons.Default.OpenInNew, null) },
                                    onClick = { showOverflow = false; onOpenExternal() }
                                )
                            }
                            if (onSwitchHex != null) {
                                DropdownMenuItem(
                                    text = { Text("Inspect Hex") },
                                    leadingIcon = { Icon(Icons.Default.Memory, null) },
                                    onClick = { showOverflow = false; onSwitchHex() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== 1. CONTINUOUS MULTI-PAGE PDF READER ====================

private enum class PdfReadingTheme(val label: String) {
    DAY("Day"), SEPIA("Sepia"), NIGHT("Night")
}

@Composable
fun ProPdfReaderView(
    studio: StudioContent.PdfViewer,
    isVaultPreview: Boolean,
    onRenderPageBitmap: suspend (FileNode, Int) -> Bitmap?,
    onSaveNotes: (FileNode, String) -> Unit,
    onClose: () -> Unit,
    onShare: (FileNode) -> Unit,
    onOpenExternal: (FileNode) -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var readingTheme by remember { mutableStateOf(PdfReadingTheme.DAY) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var isNotesMode by remember { mutableStateOf(false) }
    var notesText by remember(studio.file.path, studio.annotationNotes) { mutableStateOf(studio.annotationNotes) }

    val pageCache = remember(studio.file.path) { mutableStateMapOf<Int, Bitmap>() }
    LaunchedEffect(studio.file.path, studio.renderedPageBitmap) {
        studio.renderedPageBitmap?.let { pageCache[studio.currentPageIndex] = it }
    }

    val currentPageVisible = (listState.firstVisibleItemIndex + 1).coerceIn(1, studio.pageCount.coerceAtLeast(1))

    val pageColorFilter = remember(readingTheme) {
        when (readingTheme) {
            PdfReadingTheme.DAY -> null
            PdfReadingTheme.SEPIA -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        0.96f, 0f, 0f, 0f, 10f,
                        0f, 0.90f, 0f, 0f, 6f,
                        0f, 0f, 0.78f, 0f, -6f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            PdfReadingTheme.NIGHT -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -0.92f, 0f, 0f, 0f, 238f,
                        0f, -0.92f, 0f, 0f, 238f,
                        0f, 0f, -0.92f, 0f, 238f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
    }

    val canvasBg = when (readingTheme) {
        PdfReadingTheme.DAY -> MaterialTheme.colorScheme.surfaceContainerHighest
        PdfReadingTheme.SEPIA -> Color(0xFFE7D9BE)
        PdfReadingTheme.NIGHT -> Color(0xFF0E1015)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .testTag("pro_pdf_reader")
    ) {
        ProViewerTopBar(
            title = studio.file.name,
            subtitle = "Page $currentPageVisible of ${studio.pageCount} · ${LumaFormatUtils.formatBytes(studio.file.sizeBytes)}",
            isVaultPreview = isVaultPreview,
            isEditMode = isNotesMode,
            onToggleEditMode = { isNotesMode = !isNotesMode },
            onClose = onClose,
            onShare = { onShare(studio.file) },
            onOpenExternal = { onOpenExternal(studio.file) }
        )

        // Reading Theme & Zoom Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PdfReadingTheme.entries.forEach { t ->
                    FilterChip(
                        selected = readingTheme == t,
                        onClick = { readingTheme = t },
                        label = { Text(t.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            if (zoomScale > 1.05f) {
                TextButton(onClick = { zoomScale = 1f }) {
                    Text("${(zoomScale * 100).toInt()}% · Reset")
                }
            }
        }

        if (isNotesMode) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Document Notes") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                Button(
                    onClick = {
                        onSaveNotes(studio.file, notesText)
                        isNotesMode = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save Notes")
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(canvasBg)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            zoomScale = (zoomScale * zoom).coerceIn(1f, 3f)
                        }
                    }
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(scaleX = zoomScale, scaleY = zoomScale)
                ) {
                    items(studio.pageCount) { pageIdx ->
                        val bmp = pageCache[pageIdx]
                        LaunchedEffect(studio.file.path, pageIdx) {
                            if (!pageCache.containsKey(pageIdx)) {
                                // Keep cache lean (<= 10 pages)
                                if (pageCache.size > 10) {
                                    val farthest = pageCache.keys.maxByOrNull { kotlin.math.abs(it - pageIdx) }
                                    if (farthest != null) pageCache.remove(farthest)
                                }
                                onRenderPageBitmap(studio.file, pageIdx)?.let { rendered ->
                                    pageCache[pageIdx] = rendered
                                }
                            }
                        }

                        Card(
                            shape = RoundedCornerShape(10.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (readingTheme == PdfReadingTheme.NIGHT) Color(0xFF151820) else Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Page ${pageIdx + 1}",
                                        contentScale = ContentScale.FillWidth,
                                        colorFilter = pageColorFilter,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(420.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                    }
                                }
                                Surface(
                                    color = Color.Black.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "${pageIdx + 1}/${studio.pageCount}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (studio.pageCount > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$currentPageVisible", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = (currentPageVisible - 1).toFloat(),
                        onValueChange = { target ->
                            scope.launch { listState.scrollToItem(target.toInt().coerceIn(0, studio.pageCount - 1)) }
                        },
                        valueRange = 0f..(studio.pageCount - 1).toFloat(),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                    )
                    Text("${studio.pageCount}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ==================== 2. DOCX READER & LOSSLESS DOM EDITOR ====================

@Composable
fun ProDocxStudioView(
    studio: StudioContent.DocxEditor,
    isVaultPreview: Boolean,
    onSaveDocx: (FileNode, List<String>) -> Unit,
    onClose: () -> Unit,
    onShare: (FileNode) -> Unit,
    onOpenExternal: (FileNode) -> Unit
) {
    var isEditMode by remember { mutableStateOf(false) }
    var readerTheme by remember { mutableStateOf(PdfReadingTheme.DAY) }
    var fontSizeSp by remember { mutableFloatStateOf(16f) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    var editableParagraphs by remember(studio.file.path, studio.paragraphNodes) {
        mutableStateOf(studio.paragraphNodes.map { it.text })
    }

    val wordCount = remember(editableParagraphs) {
        editableParagraphs.sumOf { p -> p.trim().split(Regex("\\s+")).count { it.isNotBlank() } }
    }
    val readMinutes = (wordCount / 200).coerceAtLeast(1)

    val paperBg = when (readerTheme) {
        PdfReadingTheme.DAY -> MaterialTheme.colorScheme.surface
        PdfReadingTheme.SEPIA -> Color(0xFFF6EFE0)
        PdfReadingTheme.NIGHT -> Color(0xFF12151E)
    }
    val paperInk = when (readerTheme) {
        PdfReadingTheme.DAY -> MaterialTheme.colorScheme.onSurface
        PdfReadingTheme.SEPIA -> Color(0xFF2B2218)
        PdfReadingTheme.NIGHT -> Color(0xFFE5E7EB)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .testTag("pro_docx_studio")
    ) {
        ProViewerTopBar(
            title = studio.file.name,
            subtitle = "$wordCount words · ~${readMinutes}m read",
            isVaultPreview = isVaultPreview,
            isEditMode = isEditMode,
            onToggleEditMode = { isEditMode = !isEditMode },
            onClose = onClose,
            onShare = { onShare(studio.file) },
            onOpenExternal = { onOpenExternal(studio.file) },
            extraActions = {
                if (isEditMode) {
                    IconButton(onClick = { onSaveDocx(studio.file, editableParagraphs); isEditMode = false }) {
                        Icon(Icons.Default.Save, contentDescription = "Save Document", tint = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
            }
        )

        if (showSearch && !isEditMode) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Find in document…") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        if (!isEditMode) {
            // Reader controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PdfReadingTheme.entries.forEach { t ->
                        FilterChip(
                            selected = readerTheme == t,
                            onClick = { readerTheme = t },
                            label = { Text(t.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { fontSizeSp = (fontSizeSp - 2f).coerceAtLeast(12f) }) { Text("A−") }
                    TextButton(onClick = { fontSizeSp = (fontSizeSp + 2f).coerceAtMost(26f) }) { Text("A+") }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(paperBg),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(studio.paragraphNodes) { idx, node ->
                    val text = editableParagraphs.getOrElse(idx) { node.text }
                    if (searchQuery.isBlank() || text.contains(searchQuery, ignoreCase = true)) {
                        val isHeading = node.styleId.contains("Heading", ignoreCase = true) ||
                            node.styleId.contains("Title", ignoreCase = true)
                        Text(
                            text = text,
                            color = paperInk,
                            fontSize = if (isHeading) (fontSizeSp + 4f).sp else fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.55f).sp,
                            fontWeight = if (isHeading) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(editableParagraphs) { idx, text ->
                    OutlinedTextField(
                        value = text,
                        onValueChange = { updated ->
                            editableParagraphs = editableParagraphs.toMutableList().also { it[idx] = updated }
                        },
                        label = { Text("Paragraph ${idx + 1}") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { editableParagraphs = editableParagraphs + "" },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add Paragraph")
                    }
                }
            }
        }
    }
}

// ==================== 3. CODE, TEXT & MARKDOWN STUDIO ====================

@Composable
fun ProCodeAndTextStudioView(
    studio: StudioContent.TextCodeEditor,
    isVaultPreview: Boolean,
    onSaveText: (FileNode, String, String) -> Unit,
    onFormatStructured: (FileNode, String, String) -> Unit,
    onSwitchHex: (FileNode) -> Unit,
    onClose: () -> Unit,
    onShare: (FileNode) -> Unit,
    onOpenExternal: (FileNode) -> Unit
) {
    var isEditMode by remember { mutableStateOf(false) }
    var textValue by remember(studio.file.path, studio.textContent) { mutableStateOf(studio.textContent) }
    var wordWrap by remember { mutableStateOf(true) }
    var fontSizeSp by remember { mutableFloatStateOf(13.5f) }
    var showFindBar by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }

    val ext = studio.file.extension.lowercase()
    val lines = remember(textValue) { textValue.lines() }
    val matchCount = remember(textValue, findQuery) {
        if (findQuery.isBlank()) 0 else Regex(Regex.escape(findQuery), RegexOption.IGNORE_CASE).findAll(textValue).count()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .testTag("pro_code_text_studio")
    ) {
        ProViewerTopBar(
            title = studio.file.name,
            subtitle = "${lines.size} lines · ${studio.encoding} · ${LumaFormatUtils.formatBytes(studio.totalFileBytes)}",
            isVaultPreview = isVaultPreview,
            isEditMode = isEditMode,
            onToggleEditMode = { isEditMode = !isEditMode },
            onClose = onClose,
            onShare = { onShare(studio.file) },
            onOpenExternal = { onOpenExternal(studio.file) },
            onSwitchHex = { onSwitchHex(studio.file) },
            extraActions = {
                IconButton(onClick = { showFindBar = !showFindBar }) {
                    Icon(Icons.Default.FindReplace, contentDescription = "Find")
                }
                if (isEditMode) {
                    IconButton(onClick = { onSaveText(studio.file, textValue, studio.encoding) }) {
                        Icon(Icons.Default.Save, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        )

        // Compact utility strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = wordWrap,
                    onClick = { wordWrap = !wordWrap },
                    label = { Text("Wrap", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.WrapText, null, modifier = Modifier.size(14.dp)) }
                )
                if (studio.isStructuredData) {
                    FilterChip(
                        selected = false,
                        onClick = { onFormatStructured(studio.file, textValue, studio.encoding) },
                        label = { Text("Format", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.Code, null, modifier = Modifier.size(14.dp)) }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { fontSizeSp = (fontSizeSp - 1.5f).coerceAtLeast(10f) }) { Text("A−") }
                TextButton(onClick = { fontSizeSp = (fontSizeSp + 1.5f).coerceAtMost(22f) }) { Text("A+") }
            }
        }

        if (showFindBar) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = findQuery,
                            onValueChange = { findQuery = it },
                            placeholder = { Text("Find…") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        if (findQuery.isNotBlank()) {
                            Text("$matchCount", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (isEditMode) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = replaceQuery,
                                onValueChange = { replaceQuery = it },
                                placeholder = { Text("Replace with…") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    if (findQuery.isNotBlank()) {
                                        textValue = textValue.replace(findQuery, replaceQuery, ignoreCase = false)
                                    }
                                }
                            ) { Text("Replace All") }
                        }
                    }
                }
            }
        }

        if (!isEditMode && studio.isMarkdown) {
            // Rendered Markdown Reader Mode
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(lines) { line ->
                    when {
                        line.startsWith("### ") -> Text(line.removePrefix("### "), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        line.startsWith("## ") -> Text(line.removePrefix("## "), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        line.startsWith("# ") -> Text(line.removePrefix("# "), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        line.startsWith("- ") || line.startsWith("* ") -> Text("•  ${line.drop(2)}", fontSize = (fontSizeSp + 1f).sp)
                        line.startsWith("```") -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        else -> Text(line, fontSize = (fontSizeSp + 1f).sp, lineHeight = (fontSizeSp * 1.5f).sp)
                    }
                }
            }
        } else if (!isEditMode) {
            // Syntax-Highlighted Read Mode with Line Numbers
            val hScroll = rememberScrollState()
            val kwColor = MaterialTheme.colorScheme.primary
            val strColor = MaterialTheme.colorScheme.tertiary
            val commentColor = MaterialTheme.colorScheme.onSurfaceVariant
            val numColor = Color(0xFFF59E0B)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(lines) { idx, line ->
                    val highlighted = remember(line, ext, findQuery) {
                        highlightCodeLine(line, ext, findQuery, kwColor, strColor, commentColor, numColor)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .let { if (!wordWrap) it.horizontalScroll(hScroll) else it }
                            .padding(horizontal = 10.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = "${idx + 1}".padStart(4),
                            fontFamily = FontFamily.Monospace,
                            fontSize = (fontSizeSp - 1.5f).sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.width(38.dp)
                        )
                        Text(
                            text = highlighted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSizeSp.sp,
                            softWrap = wordWrap
                        )
                    }
                }
            }
        } else {
            // Clean Monospace Editor
            val vScroll = rememberScrollState()
            val hScroll = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(14.dp)
                    .verticalScroll(vScroll)
                    .let { if (!wordWrap) it.horizontalScroll(hScroll) else it }
            ) {
                BasicTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSizeSp.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = (fontSizeSp * 1.45f).sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private fun highlightCodeLine(
    line: String,
    ext: String,
    findQuery: String,
    kwColor: Color,
    strColor: Color,
    commentColor: Color,
    numColor: Color
): AnnotatedString {
    val trimmed = line.trimStart()
    if (trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("--") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = commentColor)) { append(line) }
        }
    }
    val keywords = setOf(
        "fun", "val", "var", "class", "object", "interface", "return", "if", "else", "when", "for", "while",
        "import", "package", "private", "public", "suspend", "data", "sealed", "override", "true", "false", "null",
        "def", "from", "async", "await", "function", "const", "let", "export", "default", "SELECT", "FROM", "WHERE", "INSERT", "UPDATE"
    )
    return buildAnnotatedString {
        val tokenRegex = Regex(""""[^"]*"|'[^']*'|\b\d+(\.\d+)?\b|\b[A-Za-z_][A-Za-z0-9_]*\b""")
        var lastIdx = 0
        tokenRegex.findAll(line).forEach { match ->
            if (match.range.first > lastIdx) {
                append(line.substring(lastIdx, match.range.first))
            }
            val token = match.value
            when {
                token.startsWith("\"") || token.startsWith("'") ->
                    withStyle(SpanStyle(color = strColor)) { append(token) }
                token.first().isDigit() ->
                    withStyle(SpanStyle(color = numColor)) { append(token) }
                token in keywords || (ext == "json" && line.indexOf(':', match.range.last) == match.range.last + 1) ->
                    withStyle(SpanStyle(color = kwColor, fontWeight = FontWeight.SemiBold)) { append(token) }
                else -> append(token)
            }
            lastIdx = match.range.last + 1
        }
        if (lastIdx < line.length) append(line.substring(lastIdx))

        if (findQuery.isNotBlank()) {
            Regex(Regex.escape(findQuery), RegexOption.IGNORE_CASE).findAll(line).forEach { m ->
                addStyle(SpanStyle(background = Color(0x66FACC15)), m.range.first, m.range.last + 1)
            }
        }
    }
}

// ==================== 4. CSV / TSV SPREADSHEET VIEWER & EDITOR ====================

@Composable
fun ProCsvSpreadsheetView(
    studio: StudioContent.CsvGridStudio,
    isVaultPreview: Boolean,
    onSaveCsv: (FileNode, Char, List<String>, List<List<String>>) -> Unit,
    onClose: () -> Unit,
    onShare: (FileNode) -> Unit,
    onOpenExternal: (FileNode) -> Unit
) {
    var isEditMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortColIdx by remember { mutableIntStateOf(-1) }
    var sortAscending by remember { mutableStateOf(true) }
    var selectedStatsCol by remember { mutableIntStateOf(-1) }

    var editableRows by remember(studio.file.path, studio.rows) { mutableStateOf(studio.rows) }
    val hScroll = rememberScrollState()

    val displayedRows = remember(editableRows, searchQuery, sortColIdx, sortAscending) {
        val filtered = if (searchQuery.isBlank()) editableRows else {
            editableRows.filter { row -> row.any { it.contains(searchQuery, ignoreCase = true) } }
        }
        if (sortColIdx < 0) filtered else {
            val sorted = filtered.sortedWith { a, b ->
                val av = a.getOrElse(sortColIdx) { "" }
                val bv = b.getOrElse(sortColIdx) { "" }
                val an = av.toDoubleOrNull()
                val bn = bv.toDoubleOrNull()
                if (an != null && bn != null) an.compareTo(bn) else av.compareTo(bv, ignoreCase = true)
            }
            if (sortAscending) sorted else sorted.reversed()
        }
    }

    val colStats = remember(displayedRows, selectedStatsCol) {
        if (selectedStatsCol < 0) null else {
            val nums = displayedRows.mapNotNull { it.getOrNull(selectedStatsCol)?.trim()?.toDoubleOrNull() }
            if (nums.isEmpty()) "Count: ${displayedRows.size}" else {
                val sum = nums.sum()
                val avg = sum / nums.size
                "Count: ${nums.size} · Sum: ${"%.2f".format(sum)} · Avg: ${"%.2f".format(avg)} · Min: ${nums.min()} · Max: ${nums.max()}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .testTag("pro_csv_spreadsheet")
    ) {
        ProViewerTopBar(
            title = studio.file.name,
            subtitle = "${displayedRows.size} rows · ${studio.headers.size} columns",
            isVaultPreview = isVaultPreview,
            isEditMode = isEditMode,
            onToggleEditMode = { isEditMode = !isEditMode },
            onClose = onClose,
            onShare = { onShare(studio.file) },
            onOpenExternal = { onOpenExternal(studio.file) },
            extraActions = {
                if (isEditMode) {
                    IconButton(onClick = { onSaveCsv(studio.file, studio.delimiter, studio.headers, editableRows); isEditMode = false }) {
                        Icon(Icons.Default.Save, contentDescription = "Save CSV", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Filter rows…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )

        colStats?.let { stats ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "${studio.headers.getOrElse(selectedStatsCol) { "" }}: $stats",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // Frozen Sticky Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .horizontalScroll(hScroll)
                .padding(vertical = 8.dp)
        ) {
            Text(
                "#",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .width(42.dp)
                    .padding(horizontal = 8.dp)
            )
            studio.headers.forEachIndexed { idx, header ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .width(136.dp)
                        .clickable {
                            selectedStatsCol = idx
                            if (sortColIdx == idx) sortAscending = !sortAscending
                            else {
                                sortColIdx = idx
                                sortAscending = true
                            }
                        }
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = header,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (sortColIdx == idx) {
                        Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        HorizontalDivider()

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(displayedRows) { rowIdx, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (rowIdx % 2 == 0) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceContainerLowest
                        )
                        .horizontalScroll(hScroll)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${rowIdx + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .width(42.dp)
                            .padding(horizontal = 8.dp)
                    )
                    studio.headers.indices.forEach { colIdx ->
                        val cell = row.getOrElse(colIdx) { "" }
                        if (isEditMode && searchQuery.isBlank() && sortColIdx < 0) {
                            OutlinedTextField(
                                value = cell,
                                onValueChange = { updated ->
                                    editableRows = editableRows.toMutableList().also { all ->
                                        val mutableRow = all[rowIdx].toMutableList()
                                        while (mutableRow.size <= colIdx) mutableRow.add("")
                                        mutableRow[colIdx] = updated
                                        all[rowIdx] = mutableRow
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .width(136.dp)
                                    .padding(horizontal = 4.dp)
                            )
                        } else {
                            Text(
                                text = cell,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .width(136.dp)
                                    .padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

// ==================== 5. ZIP ARCHIVE EXPLORER + IN-MEMORY ENTRY PREVIEW ====================

@Composable
fun ProZipArchiveView(
    studio: StudioContent.ZipArchiveManager,
    isVaultPreview: Boolean,
    activeEntryPreview: ZipEntryPreview?,
    onPreviewEntry: (FileNode, String) -> Unit,
    onDismissEntryPreview: () -> Unit,
    onExtractSingleEntry: (FileNode, String) -> Unit,
    onExtractAll: (FileNode) -> Unit,
    onAddEntry: (FileNode, String, String) -> Unit,
    onClose: () -> Unit,
    onShare: (FileNode) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    val filtered = remember(studio.entries, searchQuery) {
        if (searchQuery.isBlank()) studio.entries
        else studio.entries.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .testTag("pro_zip_explorer")
    ) {
        ProViewerTopBar(
            title = studio.file.name,
            subtitle = "${studio.entries.size} items · ${LumaFormatUtils.formatBytes(studio.totalUncompressedBytes)} (${studio.compressionRatioPercent}% saved)",
            isVaultPreview = isVaultPreview,
            onClose = onClose,
            onShare = { onShare(studio.file) },
            extraActions = {
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Entry")
                }
                IconButton(onClick = { onExtractAll(studio.file) }) {
                    Icon(Icons.Default.Unarchive, contentDescription = "Extract All")
                }
            }
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search inside archive…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(filtered, key = { it.name }) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            if (!entry.isDirectory) onPreviewEntry(studio.file, entry.name)
                        }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!entry.isDirectory) {
                            Text(
                                text = LumaFormatUtils.formatBytes(entry.uncompressedSize),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (!entry.isDirectory) {
                        IconButton(onClick = { onExtractSingleEntry(studio.file, entry.name) }) {
                            Icon(Icons.Default.Download, contentDescription = "Extract File", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    // In-Memory Entry Preview Modal
    activeEntryPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = onDismissEntryPreview,
            title = { Text(preview.entryName.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                ) {
                    when {
                        preview.imageBitmap != null -> {
                            Image(
                                bitmap = preview.imageBitmap.asImageBitmap(),
                                contentDescription = preview.entryName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        preview.textContent != null -> {
                            Text(
                                text = preview.textContent,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                        else -> {
                            Text(
                                "Binary file (${LumaFormatUtils.formatBytes(preview.sizeBytes)}). Extract it to open.",
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onExtractSingleEntry(studio.file, preview.entryName)
                    onDismissEntryPreview()
                }) { Text("Extract") }
            },
            dismissButton = {
                TextButton(onClick = onDismissEntryPreview) { Text("Close") }
            }
        )
    }

    if (showAddDialog) {
        var entryName by remember { mutableStateOf("note.txt") }
        var entryBody by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add File to Archive") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = entryName, onValueChange = { entryName = it }, label = { Text("Entry path") }, singleLine = true)
                    OutlinedTextField(value = entryBody, onValueChange = { entryBody = it }, label = { Text("Content") }, minLines = 4)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (entryName.isNotBlank()) onAddEntry(studio.file, entryName.trim(), entryBody)
                    showAddDialog = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }
}

// ==================== 6. SQLITE DATABASE INSPECTOR & RUNNER ====================

@Composable
fun ProSqliteStudioView(
    studio: StudioContent.SqliteStudio,
    isVaultPreview: Boolean,
    onSelectTable: (FileNode, String) -> Unit,
    onExecuteSql: (FileNode, String, String) -> Unit,
    onClose: () -> Unit
) {
    var sqlQuery by remember(studio.selectedTable) {
        mutableStateOf("SELECT * FROM \"${studio.selectedTable}\" LIMIT 100;")
    }
    val hScroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .testTag("pro_sqlite_studio")
    ) {
        ProViewerTopBar(
            title = studio.file.name,
            subtitle = "${studio.tableNames.size} tables · ${studio.totalRowsInTable} rows",
            isVaultPreview = isVaultPreview,
            onClose = onClose
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(studio.tableNames) { tbl ->
                FilterChip(
                    selected = tbl == studio.selectedTable,
                    onClick = { onSelectTable(studio.file, tbl) },
                    label = { Text(tbl) }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = sqlQuery,
                onValueChange = { sqlQuery = it },
                singleLine = true,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { onExecuteSql(studio.file, studio.selectedTable, sqlQuery) }) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Run SQL")
            }
        }

        studio.customQueryResultMessage?.let { msg ->
            Text(
                msg,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }

        // Columns Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .horizontalScroll(hScroll)
                .padding(vertical = 8.dp)
        ) {
            studio.columns.forEach { col ->
                Text(
                    col,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .width(130.dp)
                        .padding(horizontal = 8.dp)
                )
            }
        }
        HorizontalDivider()

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(studio.rows) { idx, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (idx % 2 == 0) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceContainerLowest
                        )
                        .horizontalScroll(hScroll)
                        .padding(vertical = 8.dp)
                ) {
                    row.forEach { v ->
                        Text(
                            v,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .width(130.dp)
                                .padding(horizontal = 8.dp)
                        )
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

// ==================== 7. APK INSPECTOR WITH APP ICON & INSTALL ====================

@Composable
fun ProApkInspectorView(
    studio: StudioContent.ApkInspectorStudio,
    isVaultPreview: Boolean,
    onClose: () -> Unit,
    onShare: (FileNode) -> Unit,
    onInstallApk: (FileNode) -> Unit
) {
    val dangerousKeywords = listOf("CAMERA", "RECORD_AUDIO", "LOCATION", "CONTACTS", "SMS", "STORAGE", "CALL_PHONE", "READ_PHONE")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .testTag("pro_apk_inspector")
    ) {
        ProViewerTopBar(
            title = studio.appLabel.ifBlank { studio.file.name },
            subtitle = "v${studio.versionName} (${studio.versionCode})",
            isVaultPreview = isVaultPreview,
            onClose = onClose,
            onShare = { onShare(studio.file) }
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            if (studio.appIconBitmap != null) {
                                Image(
                                    bitmap = studio.appIconBitmap.asImageBitmap(),
                                    contentDescription = studio.appLabel,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Default.Android, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(34.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(studio.appLabel.ifBlank { studio.file.name }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(studio.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "SDK ${studio.minSdk}–${studio.targetSdk} · ${LumaFormatUtils.formatBytes(studio.file.sizeBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (!isVaultPreview) {
                item {
                    Button(
                        onClick = { onInstallApk(studio.file) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("apk_install_btn")
                    ) {
                        Icon(Icons.Default.InstallMobile, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Install App")
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Package Summary", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text("Activities: ${studio.activities.size} · DEX: ${studio.dexFilesCount} · Entries: ${studio.totalEntriesCount}", style = MaterialTheme.typography.bodySmall)
                        Text("SHA-256: ${studio.sha256Signature.take(24)}…", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Text(
                    "Permissions (${studio.permissions.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            items(studio.permissions) { perm ->
                val isSensitive = dangerousKeywords.any { perm.contains(it, ignoreCase = true) }
                Surface(
                    color = if (isSensitive) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = perm.substringAfterLast('.'),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSensitive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

// ==================== 8. HEX BINARY INSPECTOR & PATCHER ====================

@Composable
fun ProHexBinaryView(
    studio: StudioContent.HexBinaryStudio,
    isVaultPreview: Boolean,
    onJumpOffset: (FileNode, Long) -> Unit,
    onPatchByte: (FileNode, Long, String, Long) -> Unit,
    onClose: () -> Unit
) {
    var isEditMode by remember { mutableStateOf(false) }
    var jumpHex by remember { mutableStateOf("%X".format(studio.windowOffset)) }
    var patchOffsetHex by remember { mutableStateOf("%X".format(studio.windowOffset)) }
    var patchByteHex by remember { mutableStateOf("00") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .testTag("pro_hex_studio")
    ) {
        ProViewerTopBar(
            title = studio.file.name,
            subtitle = "Offset 0x${"%08X".format(studio.windowOffset)} · ${LumaFormatUtils.formatBytes(studio.totalSize)}",
            isVaultPreview = isVaultPreview,
            isEditMode = isEditMode,
            onToggleEditMode = { isEditMode = !isEditMode },
            onClose = onClose
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = jumpHex,
                onValueChange = { jumpHex = it },
                label = { Text("Hex Offset") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                val off = jumpHex.trim().removePrefix("0x").toLongOrNull(16) ?: 0L
                onJumpOffset(studio.file, off)
            }) { Text("Go") }
        }

        if (isEditMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
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
                    value = patchByteHex,
                    onValueChange = { patchByteHex = it.take(2) },
                    label = { Text("Byte") },
                    singleLine = true,
                    modifier = Modifier.width(84.dp)
                )
                Button(onClick = {
                    val off = patchOffsetHex.trim().removePrefix("0x").toLongOrNull(16)
                    if (off != null && patchByteHex.isNotBlank()) {
                        onPatchByte(studio.file, off, patchByteHex, studio.windowOffset)
                    }
                }) { Text("Patch") }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            items(studio.rows) { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        row.offsetHex,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(66.dp)
                    )
                    Text(
                        row.byteValuesHex.joinToString(" "),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        row.asciiPreview,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
