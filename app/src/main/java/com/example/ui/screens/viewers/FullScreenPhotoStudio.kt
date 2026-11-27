package com.example.ui.screens.viewers

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.engine.PhotoAiStudioEngine
import com.example.domain.model.AiEnhanceUiState
import com.example.domain.model.FileNode
import com.example.domain.model.PhotoAdjustments
import com.example.domain.model.PhotoFilterCategory
import com.example.domain.model.PhotoFilterPreset
import com.example.domain.model.StudioContent
import com.example.ui.LumaFormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PhotoEditPanel {
    AI_ENHANCE, FILTERS, ADJUST, TRANSFORM
}

@Composable
fun FullScreenPhotoStudio(
    studio: StudioContent.ImageStudio,
    isVaultPreview: Boolean,
    siblingPhotos: List<FileNode>,
    aiEnhanceState: AiEnhanceUiState,
    photoEngine: PhotoAiStudioEngine,
    onClose: () -> Unit,
    onOpenSiblingPhoto: (FileNode) -> Unit,
    onRenamePhoto: (FileNode, String) -> Unit,
    onDeletePhoto: (FileNode) -> Unit,
    onSharePhoto: (FileNode) -> Unit,
    onLockToVault: (FileNode) -> Unit,
    onOpenExternal: (FileNode) -> Unit,
    onTriggerAiAutoEnhance: (Bitmap, onApplied: (PhotoAdjustments, String, String) -> Unit) -> Unit,
    onTriggerAiRemaster: (Bitmap, String, onResultBitmap: (Bitmap) -> Unit) -> Unit,
    onSaveEditedBitmap: (FileNode, Bitmap, Boolean) -> Unit,
    onOpenApiKeySettings: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    var showHud by remember { mutableStateOf(true) }
    var isEditMode by remember { mutableStateOf(false) }
    var activeEditPanel by remember { mutableStateOf(PhotoEditPanel.FILTERS) }

    // Zoom & Pan state in View mode
    var scale by remember(studio.file.path) { mutableFloatStateOf(1f) }
    var offset by remember(studio.file.path) { mutableStateOf(Offset.Zero) }

    // Live edit state
    var workingSourceBitmap by remember(studio.file.path, studio.bitmap) { mutableStateOf(studio.bitmap) }
    var selectedFilterCategory by remember { mutableStateOf(PhotoFilterCategory.ALL) }
    var selectedPreset by remember(studio.file.path) { mutableStateOf(PhotoAiStudioEngine.FILTER_PRESETS.first()) }
    var filterIntensity by remember(studio.file.path) { mutableFloatStateOf(1f) }
    var adjustments by remember(studio.file.path) { mutableStateOf(PhotoAdjustments()) }
    var rotationDegrees by remember(studio.file.path) { mutableIntStateOf(0) }
    var flipH by remember(studio.file.path) { mutableStateOf(false) }
    var flipV by remember(studio.file.path) { mutableStateOf(false) }
    var cropAspectRatio by remember(studio.file.path) { mutableStateOf<Float?>(null) }
    var activeAdjustSlider by remember { mutableStateOf("Brightness") }
    var isHoldingCompare by remember { mutableStateOf(false) }
    var aiNoteBanner by remember(studio.file.path) { mutableStateOf<String?>(null) }
    var isSavingEdit by remember { mutableStateOf(false) }

    // Dialog states
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }

    // Thumbnail bitmap for fast 18-filter preview strip
    var filterThumbBitmap by remember(studio.file.path, workingSourceBitmap) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(workingSourceBitmap) {
        val bmp = workingSourceBitmap
        if (bmp != null) {
            filterThumbBitmap = withContext(Dispatchers.Default) {
                val maxThumb = 120
                val ratio = minOf(maxThumb.toFloat() / bmp.width, maxThumb.toFloat() / bmp.height, 1f)
                Bitmap.createScaledBitmap(
                    bmp,
                    (bmp.width * ratio).toInt().coerceAtLeast(32),
                    (bmp.height * ratio).toInt().coerceAtLeast(32),
                    true
                )
            }
        }
    }

    val currentIndex = remember(siblingPhotos, studio.file.path) {
        siblingPhotos.indexOfFirst { it.path == studio.file.path }
    }
    val hasPrev = currentIndex > 0
    val hasNext = currentIndex in 0 until (siblingPhotos.size - 1)

    val hasAnyEdits = selectedPreset.id != "original" ||
        adjustments != PhotoAdjustments() ||
        rotationDegrees % 360 != 0 ||
        flipH || flipV ||
        cropAspectRatio != null ||
        workingSourceBitmap !== studio.bitmap

    val combinedMatrixValues = remember(selectedPreset, filterIntensity, adjustments) {
        PhotoAiStudioEngine.buildCombinedColorMatrixArray(selectedPreset, filterIntensity, adjustments)
    }

    val effectiveVignette = remember(selectedPreset, filterIntensity, adjustments) {
        maxOf(adjustments.vignette, selectedPreset.vignetteStrength * filterIntensity).coerceIn(0f, 0.85f)
    }

    val visiblePresets = remember(selectedFilterCategory) {
        when (selectedFilterCategory) {
            PhotoFilterCategory.ALL -> PhotoAiStudioEngine.FILTER_PRESETS
            PhotoFilterCategory.CINEMATIC -> PhotoAiStudioEngine.FILTER_PRESETS.filter {
                it.id == "original" || it.category == PhotoFilterCategory.CINEMATIC
            }
            PhotoFilterCategory.BEAUTY -> PhotoAiStudioEngine.FILTER_PRESETS.filter {
                it.id == "original" || it.category == PhotoFilterCategory.BEAUTY
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("fullscreen_photo_studio")
    ) {
        // ==================== IMAGE CANVAS ====================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isEditMode) {
                    detectTapGestures(
                        onTap = {
                            if (!isEditMode) showHud = !showHud
                        },
                        onDoubleTap = {
                            if (!isEditMode) {
                                if (scale > 1.1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.6f
                                }
                            }
                        }
                    )
                }
                .pointerInput(isEditMode) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (!isEditMode) {
                            val nextScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = nextScale
                            offset = if (nextScale > 1.02f) offset + pan else Offset.Zero
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val displayBitmap = if (isHoldingCompare) studio.bitmap else workingSourceBitmap
            if (displayBitmap != null) {
                val activeColorFilter = if (isHoldingCompare) {
                    null
                } else {
                    ColorFilter.colorMatrix(ColorMatrix(combinedMatrixValues))
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = if (isEditMode) 64.dp else 0.dp,
                            bottom = if (isEditMode) 248.dp else 0.dp
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = displayBitmap.asImageBitmap(),
                        contentDescription = studio.file.name,
                        contentScale = ContentScale.Fit,
                        colorFilter = activeColorFilter,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale * (if (flipH && !isHoldingCompare) -1f else 1f),
                                scaleY = scale * (if (flipV && !isHoldingCompare) -1f else 1f),
                                rotationZ = if (isHoldingCompare) 0f else rotationDegrees.toFloat(),
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )

                    // Subtle vignette preview overlay
                    if (!isHoldingCompare && effectiveVignette > 0.03f) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Transparent,
                                        Color.Black.copy(alpha = (effectiveVignette * 0.75f).coerceIn(0f, 0.78f))
                                    ),
                                    center = center,
                                    radius = size.maxDimension * 0.65f
                                )
                            )
                        }
                    }

                    // Crop frame visual hint if aspect ratio selected
                    if (isEditMode && cropAspectRatio != null && !isHoldingCompare) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                        ) {
                            Text(
                                text = "Crop active",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            } else {
                Text("Unable to decode image", color = Color.White.copy(alpha = 0.7f))
            }

            // Sibling Gallery Navigation Arrows (in View Mode when HUD visible)
            if (!isEditMode && showHud && scale <= 1.05f) {
                if (hasPrev) {
                    IconButton(
                        onClick = { onOpenSiblingPhoto(siblingPhotos[currentIndex - 1]) },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 8.dp)
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .testTag("photo_prev_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous Photo", tint = Color.White)
                    }
                }
                if (hasNext) {
                    IconButton(
                        onClick = { onOpenSiblingPhoto(siblingPhotos[currentIndex + 1]) },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp)
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .testTag("photo_next_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next Photo", tint = Color.White)
                    }
                }
            }

            // AI Loading Overlay
            val aiLoading = aiEnhanceState as? AiEnhanceUiState.Loading
            if (aiLoading != null || isSavingEdit) {
                Surface(
                    color = Color.Black.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
                        Text(
                            text = if (isSavingEdit) "Saving…" else aiLoading?.label?.ifBlank { "Enhancing…" } ?: "Enhancing…",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        // ==================== TOP OVERLAY BAR ====================
        AnimatedVisibility(
            visible = showHud || isEditMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.76f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = {
                                if (isEditMode) {
                                    isEditMode = false
                                } else {
                                    onClose()
                                }
                            },
                            modifier = Modifier.testTag("photo_close_btn")
                        ) {
                            Icon(
                                imageVector = if (isEditMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isEditMode) "Edit Photo" else studio.file.name,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (isVaultPreview) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "Vault",
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            if (!isEditMode) {
                                val counter = if (currentIndex >= 0 && siblingPhotos.size > 1) {
                                    "${currentIndex + 1}/${siblingPhotos.size} · "
                                } else ""
                                Text(
                                    text = "$counter${studio.widthPx}×${studio.heightPx} · ${LumaFormatUtils.formatBytes(studio.file.sizeBytes)}",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    if (isEditMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (hasAnyEdits) {
                                IconButton(
                                    onClick = { isHoldingCompare = !isHoldingCompare },
                                    modifier = Modifier.testTag("photo_compare_btn")
                                ) {
                                    Icon(
                                        Icons.Default.Compare,
                                        contentDescription = "Compare Original",
                                        tint = if (isHoldingCompare) MaterialTheme.colorScheme.primary else Color.White
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        workingSourceBitmap = studio.bitmap
                                        selectedPreset = PhotoAiStudioEngine.FILTER_PRESETS.first()
                                        filterIntensity = 1f
                                        adjustments = PhotoAdjustments()
                                        rotationDegrees = 0
                                        flipH = false
                                        flipV = false
                                        cropAspectRatio = null
                                        aiNoteBanner = null
                                        isHoldingCompare = false
                                    },
                                    modifier = Modifier.testTag("photo_reset_edits_btn")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Reset Edits", tint = Color.White)
                                }
                            }
                            Button(
                                onClick = { showSaveDialog = true },
                                enabled = hasAnyEdits && !isSavingEdit && aiEnhanceState !is AiEnhanceUiState.Loading,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .testTag("photo_save_edits_btn")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Save")
                            }
                        }
                    } else {
                        Box {
                            IconButton(
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier.testTag("photo_more_btn")
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                if (!isVaultPreview) {
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            showRenameDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Lock to Vault") },
                                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            onLockToVault(studio.file)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Open with…") },
                                        leadingIcon = { Icon(Icons.Default.OpenInNew, null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            onOpenExternal(studio.file)
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Rotate 90°") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.RotateRight, null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        rotationDegrees = (rotationDegrees + 90) % 360
                                        isEditMode = true
                                        activeEditPanel = PhotoEditPanel.TRANSFORM
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Details") },
                                    leadingIcon = { Icon(Icons.Default.Info, null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showInfoDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==================== BOTTOM BAR: VIEW MODE vs. EDIT MODE ====================
        if (!isEditMode) {
            AnimatedVisibility(
                visible = showHud,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PhotoHudAction(
                            icon = Icons.Default.Edit,
                            label = "Edit",
                            tag = "photo_action_edit",
                            onClick = {
                                scale = 1f
                                offset = Offset.Zero
                                activeEditPanel = PhotoEditPanel.FILTERS
                                isEditMode = true
                            }
                        )
                        PhotoHudAction(
                            icon = Icons.Default.AutoAwesome,
                            label = "AI Enhance",
                            tag = "photo_action_ai",
                            onClick = {
                                scale = 1f
                                offset = Offset.Zero
                                activeEditPanel = PhotoEditPanel.AI_ENHANCE
                                isEditMode = true
                            }
                        )
                        if (!isVaultPreview) {
                            PhotoHudAction(
                                icon = Icons.Default.Share,
                                label = "Share",
                                tag = "photo_action_share",
                                onClick = { onSharePhoto(studio.file) }
                            )
                            PhotoHudAction(
                                icon = Icons.Default.DriveFileRenameOutline,
                                label = "Rename",
                                tag = "photo_action_rename",
                                onClick = { showRenameDialog = true }
                            )
                            PhotoHudAction(
                                icon = Icons.Default.Delete,
                                label = "Delete",
                                tag = "photo_action_delete",
                                onClick = { onDeletePhoto(studio.file) }
                            )
                        } else {
                            PhotoHudAction(
                                icon = Icons.Default.Info,
                                label = "Info",
                                tag = "photo_action_info",
                                onClick = { showInfoDialog = true }
                            )
                        }
                    }
                }
            }
        } else {
            // ==================== PHOTO EDITOR WORKBENCH ====================
            Surface(
                color = Color(0xEE101218),
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(top = 12.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // AI feedback note if present
                    aiNoteBanner?.let { note ->
                        Text(
                            text = note,
                            color = Color(0xFF93C5FD),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    (aiEnhanceState as? AiEnhanceUiState.Error)?.let { errState ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = errState.message,
                                color = Color(0xFFFCA5A5),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (errState.showApiKeyShortcut) {
                                TextButton(
                                    onClick = onOpenApiKeySettings,
                                    modifier = Modifier.testTag("photo_ai_set_key_btn")
                                ) {
                                    Text("Set API Key", color = Color(0xFF93C5FD))
                                }
                            }
                        }
                    }

                    // Active Edit Tool Panel
                    when (activeEditPanel) {
                        PhotoEditPanel.AI_ENHANCE -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            workingSourceBitmap?.let { bmp ->
                                                onTriggerAiAutoEnhance(bmp) { aiAdj, filterId, summary ->
                                                    adjustments = aiAdj
                                                    PhotoAiStudioEngine.FILTER_PRESETS.firstOrNull { it.id == filterId }
                                                        ?.let { selectedPreset = it }
                                                    filterIntensity = 0.72f
                                                    aiNoteBanner = summary
                                                }
                                            }
                                        },
                                        enabled = aiEnhanceState !is AiEnhanceUiState.Loading && workingSourceBitmap != null,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("photo_ai_auto_btn")
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Auto Enhance", maxLines = 1)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            workingSourceBitmap?.let { bmp ->
                                                onTriggerAiRemaster(
                                                    bmp,
                                                    "Enhance lighting, dynamic range, clarity, and natural skin tones while preserving the exact subject and composition."
                                                ) { remastered ->
                                                    workingSourceBitmap = remastered
                                                    selectedPreset = PhotoAiStudioEngine.FILTER_PRESETS.first()
                                                    adjustments = PhotoAdjustments()
                                                    aiNoteBanner = "AI Remaster applied"
                                                }
                                            }
                                        },
                                        enabled = aiEnhanceState !is AiEnhanceUiState.Loading && workingSourceBitmap != null,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("photo_ai_remaster_btn")
                                    ) {
                                        Text("AI Remaster", color = Color.White, maxLines = 1)
                                    }
                                }

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val aiStyles = listOf(
                                        "Studio Portrait" to "Apply natural studio portrait lighting, balanced soft skin tones, and crisp eye detail without changing the person's identity.",
                                        "Cinema Grade" to "Apply theatrical film color grading with rich shadows, balanced highlights, and clean natural contrast.",
                                        "Golden Glow" to "Enhance with warm sunset golden-hour lighting and soft luminous highlights.",
                                        "Low-Light Boost" to "Recover shadow detail, reduce digital noise, and balance night exposure cleanly."
                                    )
                                    items(aiStyles) { (title, prompt) ->
                                        FilterChip(
                                            selected = false,
                                            onClick = {
                                                workingSourceBitmap?.let { bmp ->
                                                    onTriggerAiRemaster(bmp, prompt) { remastered ->
                                                        workingSourceBitmap = remastered
                                                        selectedPreset = PhotoAiStudioEngine.FILTER_PRESETS.first()
                                                        adjustments = PhotoAdjustments()
                                                        aiNoteBanner = "Applied $title"
                                                    }
                                                }
                                            },
                                            label = { Text(title, color = Color.White) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                containerColor = Color.White.copy(alpha = 0.1f)
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        PhotoEditPanel.FILTERS -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        PhotoFilterCategory.entries.forEach { cat ->
                                            val sel = selectedFilterCategory == cat
                                            FilterChip(
                                                selected = sel,
                                                onClick = { selectedFilterCategory = cat },
                                                label = {
                                                    Text(
                                                        cat.label,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else Color.White.copy(alpha = 0.85f)
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    containerColor = Color.White.copy(alpha = 0.08f)
                                                )
                                            )
                                        }
                                    }
                                    if (selectedPreset.id != "original") {
                                        Text(
                                            text = "${(filterIntensity * 100).toInt()}%",
                                            color = Color.White.copy(alpha = 0.8f),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }

                                if (selectedPreset.id != "original") {
                                    Slider(
                                        value = filterIntensity,
                                        onValueChange = { filterIntensity = it },
                                        valueRange = 0f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.White,
                                            activeTrackColor = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .height(28.dp)
                                    )
                                }

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.testTag("photo_filter_strip")
                                ) {
                                    items(visiblePresets, key = { it.id }) { preset ->
                                        val isSelected = selectedPreset.id == preset.id
                                        val previewMatrix = remember(preset.id) {
                                            ColorMatrix(preset.matrix)
                                        }
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .width(68.dp)
                                                .clickable {
                                                    selectedPreset = preset
                                                    if (preset.id != "original" && filterIntensity < 0.1f) {
                                                        filterIntensity = 1f
                                                    }
                                                }
                                                .testTag("filter_preset_${preset.id}")
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(58.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .border(
                                                        width = if (isSelected) 2.5.dp else 1.dp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.18f),
                                                        shape = RoundedCornerShape(12.dp)
                                                    )
                                                    .background(Color.White.copy(alpha = 0.08f))
                                            ) {
                                                filterThumbBitmap?.let { thumb ->
                                                    Image(
                                                        bitmap = thumb.asImageBitmap(),
                                                        contentDescription = preset.name,
                                                        contentScale = ContentScale.Crop,
                                                        colorFilter = ColorFilter.colorMatrix(previewMatrix),
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = preset.name,
                                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.72f),
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        PhotoEditPanel.ADJUST -> {
                            val sliderItems = listOf("Brightness", "Contrast", "Saturation", "Warmth", "Clarity", "Smooth", "Vignette")
                            val currentVal = when (activeAdjustSlider) {
                                "Brightness" -> adjustments.brightness
                                "Contrast" -> adjustments.contrast
                                "Saturation" -> adjustments.saturation
                                "Warmth" -> adjustments.warmth
                                "Clarity" -> adjustments.clarity
                                "Smooth" -> adjustments.skinSmooth
                                else -> adjustments.vignette
                            }
                            val range = when (activeAdjustSlider) {
                                "Brightness", "Warmth" -> -0.5f..0.5f
                                "Contrast" -> 0.5f..1.8f
                                "Saturation" -> 0f..2f
                                "Vignette" -> 0f..0.8f
                                else -> 0f..1f
                            }

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(activeAdjustSlider, color = Color.White, style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        text = "${(currentVal * 100).toInt()}",
                                        color = Color.White.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                Slider(
                                    value = currentVal,
                                    onValueChange = { v ->
                                        adjustments = when (activeAdjustSlider) {
                                            "Brightness" -> adjustments.copy(brightness = v)
                                            "Contrast" -> adjustments.copy(contrast = v)
                                            "Saturation" -> adjustments.copy(saturation = v)
                                            "Warmth" -> adjustments.copy(warmth = v)
                                            "Clarity" -> adjustments.copy(clarity = v)
                                            "Smooth" -> adjustments.copy(skinSmooth = v)
                                            else -> adjustments.copy(vignette = v)
                                        }
                                    },
                                    valueRange = range,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(sliderItems) { item ->
                                        val sel = activeAdjustSlider == item
                                        FilterChip(
                                            selected = sel,
                                            onClick = { activeAdjustSlider = item },
                                            label = {
                                                Text(
                                                    item,
                                                    color = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else Color.White.copy(alpha = 0.85f)
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                containerColor = Color.White.copy(alpha = 0.08f)
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        PhotoEditPanel.TRANSFORM -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    OutlinedButton(onClick = { rotationDegrees = (rotationDegrees + 90) % 360 }) {
                                        Icon(Icons.AutoMirrored.Filled.RotateRight, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Rotate", color = Color.White)
                                    }
                                    OutlinedButton(onClick = { flipH = !flipH }) {
                                        Icon(Icons.Default.Flip, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Flip H", color = Color.White)
                                    }
                                    OutlinedButton(onClick = { flipV = !flipV }) {
                                        Text("Flip V", color = Color.White)
                                    }
                                }

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val ratios = listOf(
                                        "Original" to null,
                                        "1:1" to 1f,
                                        "4:3" to (4f / 3f),
                                        "16:9" to (16f / 9f),
                                        "9:16" to (9f / 16f)
                                    )
                                    items(ratios) { (label, ratio) ->
                                        val sel = cropAspectRatio == ratio
                                        FilterChip(
                                            selected = sel,
                                            onClick = { cropAspectRatio = ratio },
                                            label = {
                                                Text(
                                                    label,
                                                    color = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                containerColor = Color.White.copy(alpha = 0.08f)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Editor Mode Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        EditorPanelTab(
                            icon = Icons.Default.AutoAwesome,
                            label = "AI",
                            selected = activeEditPanel == PhotoEditPanel.AI_ENHANCE,
                            onClick = { activeEditPanel = PhotoEditPanel.AI_ENHANCE }
                        )
                        EditorPanelTab(
                            icon = Icons.Default.FilterVintage,
                            label = "Filters",
                            selected = activeEditPanel == PhotoEditPanel.FILTERS,
                            onClick = { activeEditPanel = PhotoEditPanel.FILTERS }
                        )
                        EditorPanelTab(
                            icon = Icons.Default.Tune,
                            label = "Adjust",
                            selected = activeEditPanel == PhotoEditPanel.ADJUST,
                            onClick = { activeEditPanel = PhotoEditPanel.ADJUST }
                        )
                        EditorPanelTab(
                            icon = Icons.Default.Crop,
                            label = "Crop",
                            selected = activeEditPanel == PhotoEditPanel.TRANSFORM,
                            onClick = { activeEditPanel = PhotoEditPanel.TRANSFORM }
                        )
                    }
                }
            }
        }
    }

    // ==================== DIALOGS ====================

    if (showRenameDialog) {
        var newName by remember(studio.file.name) { mutableStateOf(studio.file.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Photo") },
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
                Button(
                    onClick = {
                        if (newName.isNotBlank() && newName != studio.file.name) {
                            onRenamePhoto(studio.file, newName.trim())
                        }
                        showRenameDialog = false
                    }
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Photo Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(studio.file.name, fontWeight = FontWeight.SemiBold)
                    studio.exifSummary.forEach { (k, v) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text(v, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) { Text("Done") }
            }
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Photo") },
            text = {
                Text(
                    if (isVaultPreview) {
                        "Save your edits directly back into the encrypted vault item."
                    } else {
                        "Save as a new edited copy or overwrite the original file."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveDialog = false
                        val baseBitmap = workingSourceBitmap ?: return@Button
                        isSavingEdit = true
                        scope.launch {
                            val rendered = withContext(Dispatchers.Default) {
                                photoEngine.renderEditedBitmap(
                                    source = baseBitmap,
                                    preset = selectedPreset,
                                    filterIntensity = filterIntensity,
                                    adjustments = adjustments,
                                    rotationDegrees = rotationDegrees,
                                    flipHorizontal = flipH,
                                    flipVertical = flipV,
                                    cropAspectRatio = cropAspectRatio
                                )
                            }
                            isSavingEdit = false
                            isEditMode = false
                            onSaveEditedBitmap(studio.file, rendered, isVaultPreview)
                        }
                    }
                ) {
                    Text(if (isVaultPreview) "Save to Vault" else "Save Copy")
                }
            },
            dismissButton = {
                Row {
                    if (!isVaultPreview) {
                        TextButton(
                            onClick = {
                                showSaveDialog = false
                                val baseBitmap = workingSourceBitmap ?: return@TextButton
                                isSavingEdit = true
                                scope.launch {
                                    val rendered = withContext(Dispatchers.Default) {
                                        photoEngine.renderEditedBitmap(
                                            source = baseBitmap,
                                            preset = selectedPreset,
                                            filterIntensity = filterIntensity,
                                            adjustments = adjustments,
                                            rotationDegrees = rotationDegrees,
                                            flipHorizontal = flipH,
                                            flipVertical = flipV,
                                            cropAspectRatio = cropAspectRatio
                                        )
                                    }
                                    isSavingEdit = false
                                    isEditMode = false
                                    onSaveEditedBitmap(studio.file, rendered, true)
                                }
                            }
                        ) { Text("Overwrite") }
                    }
                    TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
private fun PhotoHudAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(tag)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EditorPanelTab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.65f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = tint, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
