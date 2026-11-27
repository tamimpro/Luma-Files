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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.domain.model.DocxParagraphNode
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
    onFormatStructuredText: (FileNode, String, String) -> Unit,
    onSaveDocx: (FileNode, List<String>) -> Unit,
    onSaveCsvGrid: (FileNode, Char, List<String>, List<List<String>>) -> Unit,
    onSelectSqliteTable: (FileNode, String) -> Unit,
    onExecuteSqliteQuery: (FileNode, String, String) -> Unit,
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
        is StudioContent.CsvGridStudio -> studioContent.file
        is StudioContent.SqliteStudio -> studioContent.file
        is StudioContent.ApkInspectorStudio -> studioContent.file
    }

    val supportsEditMode = when (studioContent) {
        is StudioContent.TextCodeEditor,
        is StudioContent.DocxEditor,
        is StudioContent.CsvGridStudio,
        is StudioContent.HexBinaryStudio,
        is StudioContent.PdfViewer,
        is StudioContent.ZipArchiveManager -> true
        else -> false
    }

    // View-First File Opening: Always open in View Mode first (isEditMode = false)
    var isEditMode by remember(activeNode.path, studioContent::class) {
        mutableStateOf(false)
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
                        .padding(horizontal = 10.dp, vertical = 8.dp),
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isEditMode) {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                }
                            ) {
                                Text(
                                    text = if (isEditMode) "EDIT MODE" else "VIEW MODE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isEditMode) {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "${activeNode.category.badgeText} • ${LumaFormatUtils.formatBytes(activeNode.sizeBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (supportsEditMode) {
                        FilterChip(
                            selected = isEditMode,
                            onClick = { isEditMode = !isEditMode },
                            label = {
                                Text(
                                    text = if (isEditMode) "Viewing" else "Edit",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isEditMode) Icons.Default.Visibility else Icons.Default.Edit,
                                    contentDescription = if (isEditMode) "Switch to View Mode" else "Switch to Edit Mode",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("studio_toggle_edit_mode_btn")
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
                        isEditMode = isEditMode,
                        onEnableEditMode = { isEditMode = true },
                        onSave = { updatedText, encoding ->
                            onSaveText(studioContent.file, updatedText, encoding)
                        },
                        onFormatStructured = { rawText, encoding ->
                            onFormatStructuredText(studioContent.file, rawText, encoding)
                        }
                    )

                    is StudioContent.DocxEditor -> DocxStudioPane(
                        content = studioContent,
                        isEditMode = isEditMode,
                        onEnableEditMode = { isEditMode = true },
                        onSave = { paragraphs ->
                            onSaveDocx(studioContent.file, paragraphs)
                        }
                    )

                    is StudioContent.CsvGridStudio -> CsvGridStudioPane(
                        content = studioContent,
                        isEditMode = isEditMode,
                        onEnableEditMode = { isEditMode = true },
                        onSave = { delimiter, headers, rows ->
                            onSaveCsvGrid(studioContent.file, delimiter, headers, rows)
                        }
                    )

                    is StudioContent.SqliteStudio -> SqliteStudioPane(
                        content = studioContent,
                        onSelectTable = { table ->
                            onSelectSqliteTable(studioContent.file, table)
                        },
                        onExecuteSql = { sql ->
                            onExecuteSqliteQuery(studioContent.file, studioContent.selectedTable, sql)
                        }
                    )

                    is StudioContent.ApkInspectorStudio -> ApkInspectorStudioPane(
                        content = studioContent
                    )

                    is StudioContent.PdfViewer -> PdfStudioPane(
                        content = studioContent,
                        isEditMode = isEditMode,
                        onEnableEditMode = { isEditMode = true },
                        onChangePage = { newIdx -> onNavigatePdfPage(studioContent.file, newIdx) },
                        onSaveNotes = { notes -> onSavePdfNotes(studioContent.file, notes) }
                    )

                    is StudioContent.ZipArchiveManager -> ZipStudioPane(
                        content = studioContent,
                        isEditMode = isEditMode,
                        onEnableEditMode = { isEditMode = true },
                        onExtractAll = { onExtractZip(studioContent.file) },
                        onAddEntry = { name, body -> onAddZipEntry(studioContent.file, name, body) }
                    )

                    is StudioContent.MediaStudio -> MediaStudioPane(content = studioContent)

                    is StudioContent.ImageStudio -> ImageStudioPane(content = studioContent)

                    is StudioContent.HexBinaryStudio -> HexBinaryStudioPane(
                        content = studioContent,
                        isEditMode = isEditMode,
                        onEnableEditMode = { isEditMode = true },
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
    isEditMode: Boolean,
    onEnableEditMode: () -> Unit,
    onSave: (String, String) -> Unit,
    onFormatStructured: (String, String) -> Unit
) {
    var editedText by remember(content.file.path, content.textContent) {
        mutableStateOf(content.textContent)
    }
    var encoding by remember(content.encoding) { mutableStateOf(content.encoding) }
    var showMarkdownRenderedInView by remember(content.isMarkdown) { mutableStateOf(content.isMarkdown) }
    var showFindReplace by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }

    val lineCount = remember(editedText) { editedText.count { it == '\n' } + 1 }

    Column(modifier = Modifier.fillMaxSize()) {
        // Sub-toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (content.isMarkdown && !isEditMode) {
                    FilterChip(
                        selected = showMarkdownRenderedInView,
                        onClick = { showMarkdownRenderedInView = !showMarkdownRenderedInView },
                        label = { Text(if (showMarkdownRenderedInView) "Raw Source" else "Rendered Markdown") },
                        leadingIcon = {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }
                if (content.isStructuredData) {
                    FilterChip(
                        selected = true,
                        onClick = { onFormatStructured(editedText, encoding) },
                        label = { Text("Format & Validate ${content.file.extension.uppercase()}") },
                        leadingIcon = {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.testTag("format_structured_text_btn")
                    )
                }
                if (isEditMode) {
                    FilterChip(
                        selected = showFindReplace,
                        onClick = { showFindReplace = !showFindReplace },
                        label = { Text("Find & Replace") },
                        leadingIcon = {
                            Icon(Icons.Default.FindReplace, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (!isEditMode) {
                Button(
                    onClick = onEnableEditMode,
                    modifier = Modifier.testTag("enable_text_edit_mode_btn")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit File")
                }
            } else {
                Button(
                    onClick = { onSave(editedText, encoding) },
                    modifier = Modifier.testTag("save_text_studio_btn")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Lossless")
                }
            }
        }

        if (content.validationMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = content.validationMessage,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        if (isEditMode && showFindReplace) {
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
                text = "${if (isEditMode) "Editing" else "Read-Only View"} • Lines: $lineCount • Chars: ${editedText.length} • Encoding: $encoding",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (content.isTruncatedPreview) {
                Text(
                    text = "512KB Stream Window",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        HorizontalDivider()

        if (!isEditMode && showMarkdownRenderedInView && content.isMarkdown) {
            SelectionContainer {
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
            }
        } else if (!isEditMode) {
            // Read-Only View Mode with Line Numbers
            SelectionContainer {
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
                    Text(
                        text = editedText,
                        style = TextStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("studio_text_readonly_view")
                    )
                }
            }
        } else {
            // Active Edit Mode
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
    isEditMode: Boolean,
    onEnableEditMode: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    val paragraphNodes = remember(content.file.path, content.paragraphNodes) {
        mutableStateListOf(*content.paragraphNodes.toTypedArray())
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
                        text = "Word OOXML (.docx) Document Studio",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${content.xmlEntriesCount} XML Parts • ${content.preservedStylesCount} Styles Preserved",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (!isEditMode) {
                    Button(
                        onClick = onEnableEditMode,
                        modifier = Modifier.testTag("enable_docx_edit_btn")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit .DOCX")
                    }
                } else {
                    Button(
                        onClick = { onSave(paragraphNodes.map { it.text }) },
                        modifier = Modifier.testTag("save_docx_btn")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save .DOCX")
                    }
                }
            }
        }

        if (isEditMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newParagraphText,
                    onValueChange = { newParagraphText = it },
                    label = { Text("Append New OOXML Paragraph (<w:p>)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (newParagraphText.isNotBlank()) {
                            paragraphNodes.add(
                                DocxParagraphNode(
                                    index = paragraphNodes.size,
                                    text = newParagraphText.trim(),
                                    styleId = "BodyText",
                                    hasRunFormatting = false
                                )
                            )
                            newParagraphText = ""
                        }
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Paragraph")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(paragraphNodes) { index, node ->
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
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Paragraph #${index + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = node.styleId,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (isEditMode && paragraphNodes.size > 1) {
                                IconButton(
                                    onClick = { paragraphNodes.removeAt(index) },
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
                        if (!isEditMode) {
                            SelectionContainer {
                                Text(
                                    text = node.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = node.text,
                                onValueChange = { updated ->
                                    paragraphNodes[index] = node.copy(text = updated)
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

@Composable
private fun CsvGridStudioPane(
    content: StudioContent.CsvGridStudio,
    isEditMode: Boolean,
    onEnableEditMode: () -> Unit,
    onSave: (Char, List<String>, List<List<String>>) -> Unit
) {
    val headers = remember(content.file.path, content.headers) {
        mutableStateListOf(*content.headers.toTypedArray())
    }
    val rows = remember(content.file.path, content.rows) {
        mutableStateListOf(*content.rows.map { it.toMutableList() }.toTypedArray())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.TableChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "CSV / TSV Spreadsheet Studio",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${rows.size} Rows × ${headers.size} Columns • Delimiter: '${if (content.delimiter == '\t') "TAB" else content.delimiter.toString()}'",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                if (!isEditMode) {
                    Button(
                        onClick = onEnableEditMode,
                        modifier = Modifier.testTag("enable_csv_edit_btn")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit Grid")
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                rows.add(MutableList(headers.size) { "" })
                            },
                            modifier = Modifier.testTag("csv_add_row_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Row")
                        }
                        Button(
                            onClick = {
                                onSave(content.delimiter, headers.toList(), rows.map { it.toList() })
                            },
                            modifier = Modifier.testTag("save_csv_grid_btn")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save Grid")
                        }
                    }
                }
            }
        }

        val horizontalScroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(horizontalScroll)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
                    )
                    .padding(vertical = 8.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.width(42.dp), contentAlignment = Alignment.Center) {
                    Text("#", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                headers.forEachIndexed { colIndex, colName ->
                    if (!isEditMode) {
                        Text(
                            text = colName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .width(150.dp)
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    } else {
                        OutlinedTextField(
                            value = colName,
                            onValueChange = { headers[colIndex] = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.width(150.dp)
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(rows) { rowIndex, rowCells ->
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.width(42.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "${rowIndex + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        for (colIndex in headers.indices) {
                            val cellVal = rowCells.getOrElse(colIndex) { "" }
                            if (!isEditMode) {
                                Text(
                                    text = cellVal,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .width(150.dp)
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            } else {
                                OutlinedTextField(
                                    value = cellVal,
                                    onValueChange = { updated ->
                                        val copy = rowCells.toMutableList()
                                        while (copy.size <= colIndex) copy.add("")
                                        copy[colIndex] = updated
                                        rows[rowIndex] = copy
                                    },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 12.sp
                                    ),
                                    modifier = Modifier.width(150.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SqliteStudioPane(
    content: StudioContent.SqliteStudio,
    onSelectTable: (String) -> Unit,
    onExecuteSql: (String) -> Unit
) {
    var sqlQuery by remember(content.selectedTable) {
        mutableStateOf("SELECT * FROM `${content.selectedTable}` LIMIT 100")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "SQLite Database Table & Query Engine",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${content.tableNames.size} Tables • Active: '${content.selectedTable}' (${content.totalRowsInTable} total rows)",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // Table Selector Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    content.tableNames.forEach { table ->
                        FilterChip(
                            selected = table == content.selectedTable,
                            onClick = { onSelectTable(table) },
                            label = { Text(table) }
                        )
                    }
                }

                // SQL Query Input Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = sqlQuery,
                        onValueChange = { nextSql -> sqlQuery = nextSql },
                        label = { Text("SQL Query (SELECT / INSERT / UPDATE)") },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sqlite_query_input")
                    )
                    Button(
                        onClick = { onExecuteSql(sqlQuery) },
                        modifier = Modifier.testTag("execute_sqlite_query_btn")
                    ) {
                        Text("Run SQL")
                    }
                }
            }
        }

        // Results Grid
        val horizontalScroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(horizontalScroll)
        ) {
            Row(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content.columns.forEach { col ->
                    Text(
                        text = col,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.width(140.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(content.rows) { idx, row ->
                    Row(
                        modifier = Modifier
                            .background(
                                if (idx % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { cell ->
                            Text(
                                text = cell,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(140.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApkInspectorStudioPane(
    content: StudioContent.ApkInspectorStudio
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Android,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = content.file.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Package: ${content.packageName}",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Version: ${content.versionName} (Code ${content.versionCode}) • Min SDK ${content.minSdk} / Target SDK ${content.targetSdk}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "DEX & Cryptographic Signature Telemetry",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "DEX Bytecode Files: ${content.dexFilesCount} • Total ZIP Entries: ${content.totalEntriesCount}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "SHA-256 Digest:\n${content.sha256Signature}",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Declared Android Permissions (${content.permissions.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (content.permissions.isEmpty()) {
                        Text("No manifest permissions declared.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        content.permissions.forEach { perm ->
                            Text(
                                text = "• $perm",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        if (content.activities.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Exported & Internal Activities (${content.activities.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        content.activities.forEach { act ->
                            Text(
                                text = "• $act",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfStudioPane(
    content: StudioContent.PdfViewer,
    isEditMode: Boolean,
    onEnableEditMode: () -> Unit,
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
                text = "Page ${content.currentPageIndex + 1} of ${content.pageCount}",
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

        // PDF Annotation & Notes Companion
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
                        text = "PDF Companion Notes",
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (!isEditMode) {
                        OutlinedButton(onClick = onEnableEditMode) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit Notes")
                        }
                    } else {
                        Button(onClick = { onSaveNotes(notes) }) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Notes")
                        }
                    }
                }
                if (!isEditMode) {
                    Text(
                        text = notes.ifBlank { "No notes attached. Tap 'Edit Notes' to add annotations." },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
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
}

@Composable
private fun ZipStudioPane(
    content: StudioContent.ZipArchiveManager,
    isEditMode: Boolean,
    onEnableEditMode: () -> Unit,
    onExtractAll: () -> Unit,
    onAddEntry: (String, String) -> Unit
) {
    var newEntryName by remember { mutableStateOf("") }
    var newEntryContent by remember { mutableStateOf("") }

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
                    Column(modifier = Modifier.weight(1f)) {
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
                        if (!isEditMode) {
                            OutlinedButton(onClick = onEnableEditMode) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Entry")
                            }
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

                if (isEditMode) {
                    HorizontalDivider()
                    OutlinedTextField(
                        value = newEntryName,
                        onValueChange = { newEntryName = it },
                        placeholder = { Text("e.g. docs/notes.txt") },
                        label = { Text("Relative Entry Path inside ZIP") },
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
                                newEntryName = ""
                                newEntryContent = ""
                            }
                        },
                        enabled = newEntryName.isNotBlank()
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
    isEditMode: Boolean,
    onEnableEditMode: () -> Unit,
    onChangeWindow: (Long) -> Unit,
    onPatchByte: (Long, String) -> Unit
) {
    var patchOffsetHex by remember(content.windowOffset) { mutableStateOf("%X".format(content.windowOffset)) }
    var patchValueHex by remember { mutableStateOf("00") }

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
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
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
                    }
                    if (!isEditMode) {
                        OutlinedButton(onClick = onEnableEditMode) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit Bytes")
                        }
                    }
                }
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

        // Direct Single-Byte Hex Patch Bar (Only shown in Edit Mode)
        if (isEditMode) {
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
                    },
                    modifier = Modifier.testTag("patch_hex_byte_btn")
                ) {
                    Text("Patch Byte")
                }
            }
        }

        // Hex Rows Table
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
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = row.byteValuesHex.joinToString(" "),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "| ${row.asciiPreview}",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}
