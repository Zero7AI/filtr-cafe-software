package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.entity.StoryPhotoMetadata
import com.example.ui.BillingUiState
import java.text.SimpleDateFormat
import java.util.*

private fun compressImageForThermalUpload(context: android.content.Context, uri: Uri): ByteArray? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        if (originalBitmap == null) return null

        // 7:4 aspect ratio crop, 576 dots width (optimal for both 80mm & 58mm thermal printers)
        val targetWidth = 576
        val targetHeight = (targetWidth * 4) / 7

        val targetRatio = 7f / 4f
        val currentRatio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
        val cropW: Int
        val cropH: Int
        if (currentRatio > targetRatio) {
            cropH = originalBitmap.height
            cropW = ((originalBitmap.height * 7) / 4).coerceAtMost(originalBitmap.width)
        } else {
            cropW = originalBitmap.width
            cropH = ((originalBitmap.width * 4) / 7).coerceAtMost(originalBitmap.height)
        }
        val xOff = ((originalBitmap.width - cropW) / 2).coerceAtLeast(0)
        val yOff = ((originalBitmap.height - cropH) / 2).coerceAtLeast(0)
        val cropped = android.graphics.Bitmap.createBitmap(originalBitmap, xOff, yOff, cropW, cropH)
        val scaled = android.graphics.Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true)

        val outStream = java.io.ByteArrayOutputStream()
        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, outStream)
        outStream.toByteArray()
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptDesignScreen(
    state: BillingUiState,
    onBack: () -> Unit = {},
    onUpdateText: (tagline: String, subtitle: String, f1: String, f2: String, f3: String, isStoryEnabled: Boolean) -> Unit,
    onSelectPhoto: (String?) -> Unit,
    onSavePhotoMetadata: (fileName: String, title: String, date: String, caption: String) -> Unit,
    onUploadPhoto: (imageBytes: ByteArray, title: String, date: String, caption: String) -> Unit,
    onDeletePhoto: (fileName: String) -> Unit,
    onRefreshPhotos: () -> Unit,
    onGenerateNote: (title: String, date: String, onResult: (String) -> Unit) -> Unit,
    getPhotoUrl: (String) -> String
) {
    val context = LocalContext.current
    val todayFormatted = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()) }

    // Section 2 Text state
    var tagline by remember(state.receiptTagline) { mutableStateOf(state.receiptTagline) }
    var subtitle by remember(state.receiptHeaderSubtitle) { mutableStateOf(state.receiptHeaderSubtitle) }
    var footer1 by remember(state.receiptFooterLine1) { mutableStateOf(state.receiptFooterLine1) }
    var footer2 by remember(state.receiptFooterLine2) { mutableStateOf(state.receiptFooterLine2) }
    var footer3 by remember(state.receiptFooterLine3) { mutableStateOf(state.receiptFooterLine3) }
    var isStoryEnabled by remember(state.isReceiptStoryEnabled) { mutableStateOf(state.isReceiptStoryEnabled) }

    LaunchedEffect(Unit) {
        onRefreshPhotos()
    }

    // Selected photo state
    val selectedPhotoName = state.selectedStoryPhotoName ?: state.storyPhotoNames.firstOrNull()
    val activeMeta: StoryPhotoMetadata? = selectedPhotoName?.let { name ->
        state.storyPhotoMetadataMap[name] ?: StoryPhotoMetadata(fileName = name, title = "FILTR Story", date = todayFormatted)
    }

    var editTitle by remember(activeMeta?.title) { mutableStateOf(activeMeta?.title ?: state.receiptStoryTitle) }
    var editDate by remember(activeMeta?.date) { mutableStateOf(activeMeta?.date?.ifBlank { todayFormatted } ?: todayFormatted) }
    var editCaption by remember(activeMeta?.caption) { mutableStateOf(activeMeta?.caption ?: state.receiptStoryCaption) }
    var isGeneratingAiNote by remember { mutableStateOf(false) }

    // Upload dialog state
    var showUploadDialog by remember { mutableStateOf(false) }
    var uploadPendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var uploadTitle by remember { mutableStateOf("") }
    var uploadDate by remember { mutableStateOf(todayFormatted) }
    var uploadCaption by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val compressed = compressImageForThermalUpload(context, uri)
                if (compressed != null && compressed.isNotEmpty()) {
                    uploadPendingBytes = compressed
                    uploadTitle = "Behind The Brew"
                    uploadDate = todayFormatted
                    uploadCaption = "Crafted fresh with obsession. Every cup tells a story."
                    showUploadDialog = true
                }
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    // Tab state: 0 = Story & Photos, 1 = Receipt Text, 2 = Thermal Preview
    var selectedSubTab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA))
    ) {
        // Compact, modern header matching app top-bar styling
        Surface(
            color = Color(0xFF09090B),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Top Row: Back button, Title & Subtitle, Print Story switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Bill & Receipt Design",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "Thermal ticket layout & story photo",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Print Story Switch (Compact & guaranteed inside screen bounds)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Story",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = isStoryEnabled,
                            onCheckedChange = {
                                isStoryEnabled = it
                                onUpdateText(tagline, subtitle, footer1, footer2, footer3, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFF59E0B),
                                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.scale(0.72f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // High-contrast Pill Tabs Row (Clean, readable, never dark-on-dark)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PillTabButton(
                        selected = selectedSubTab == 0,
                        icon = Icons.Default.PhotoCamera,
                        label = "Story Photos (${state.storyPhotoNames.size})",
                        onClick = { selectedSubTab = 0 },
                        modifier = Modifier.weight(1.1f)
                    )
                    PillTabButton(
                        selected = selectedSubTab == 1,
                        icon = Icons.Default.EditNote,
                        label = "Bill Text",
                        onClick = { selectedSubTab = 1 },
                        modifier = Modifier.weight(0.9f)
                    )
                    PillTabButton(
                        selected = selectedSubTab == 2,
                        icon = Icons.Default.Preview,
                        label = "Preview",
                        onClick = { selectedSubTab = 2 },
                        modifier = Modifier.weight(0.9f)
                    )
                }
            }
        }

        // Body Content dynamically filling available height without distortion
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedSubTab) {
                0 -> StoryAndPhotosSection(
                    state = state,
                    selectedPhotoName = selectedPhotoName,
                    activeMeta = activeMeta,
                    editTitle = editTitle,
                    editDate = editDate,
                    editCaption = editCaption,
                    todayFormatted = todayFormatted,
                    isGeneratingAiNote = isGeneratingAiNote,
                    onTitleChange = { editTitle = it },
                    onDateChange = { editDate = it },
                    onCaptionChange = { editCaption = it },
                    onSelectPhoto = {
                        onSelectPhoto(it)
                        val m = state.storyPhotoMetadataMap[it]
                        if (m != null) {
                            editTitle = m.title
                            editDate = m.date
                            editCaption = m.caption
                        }
                    },
                    onSaveDetails = {
                        if (selectedPhotoName != null) {
                            onSavePhotoMetadata(selectedPhotoName, editTitle, editDate, editCaption)
                        }
                    },
                    onDeletePhoto = {
                        if (selectedPhotoName != null) {
                            onDeletePhoto(selectedPhotoName)
                        }
                    },
                    onGenerateAi = {
                        isGeneratingAiNote = true
                        onGenerateNote(editTitle, editDate) { note ->
                            editCaption = note
                            isGeneratingAiNote = false
                        }
                    },
                    onPickPhotoToUpload = {
                        filePickerLauncher.launch("image/*")
                    },
                    onRefreshPhotos = onRefreshPhotos,
                    getPhotoUrl = getPhotoUrl
                )
                1 -> ReceiptTextSection(
                    tagline = tagline,
                    subtitle = subtitle,
                    footer1 = footer1,
                    footer2 = footer2,
                    footer3 = footer3,
                    isStoryEnabled = isStoryEnabled,
                    onTaglineChange = { tagline = it },
                    onSubtitleChange = { subtitle = it },
                    onFooter1Change = { footer1 = it },
                    onFooter2Change = { footer2 = it },
                    onFooter3Change = { footer3 = it },
                    onSave = {
                        onUpdateText(tagline, subtitle, footer1, footer2, footer3, isStoryEnabled)
                    }
                )
                2 -> ThermalReceiptPreviewSection(
                    tagline = tagline,
                    subtitle = subtitle,
                    footer1 = footer1,
                    footer2 = footer2,
                    footer3 = footer3,
                    isStoryEnabled = isStoryEnabled,
                    selectedPhotoName = selectedPhotoName,
                    photoTitle = editTitle,
                    photoDate = editDate,
                    photoCaption = editCaption,
                    getPhotoUrl = getPhotoUrl
                )
            }
        }
    }

    // Upload with Per-Photo Metadata Dialog
    if (showUploadDialog && uploadPendingBytes != null) {
        AlertDialog(
            onDismissRequest = {
                showUploadDialog = false
                uploadPendingBytes = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color(0xFFF59E0B))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add New Story Photo", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Set specific topic and date for this thermal print photo:",
                        fontSize = 12.sp,
                        color = Color(0xFF71717A)
                    )

                    OutlinedTextField(
                        value = uploadTitle,
                        onValueChange = { uploadTitle = it },
                        label = { Text("Topic Label / Story Title") },
                        placeholder = { Text("e.g. Behind The Brew") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = uploadDate,
                        onValueChange = { uploadDate = it },
                        label = { Text("Date on Receipt") },
                        placeholder = { Text("e.g. 27 Sep 2026") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = uploadCaption,
                        onValueChange = { uploadCaption = it },
                        label = { Text("Journal Note / Caption") },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Surface(
                        color = Color(0xFFFFFBEB),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Photo auto-cropped to 7:4 ratio for full edge-to-edge thermal printing without side margins.",
                                fontSize = 11.sp,
                                color = Color(0xFF92400E)
                            )
                            uploadPendingBytes?.let { bytes ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "⚡ Compressed file size: ${bytes.size / 1024} KB (optimized for instant thermal print)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF16A34A)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val bytes = uploadPendingBytes
                        if (bytes != null) {
                            onUploadPhoto(bytes, uploadTitle, uploadDate, uploadCaption)
                        }
                        showUploadDialog = false
                        uploadPendingBytes = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF09090B)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Upload & Save", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUploadDialog = false
                    uploadPendingBytes = null
                }) {
                    Text("Cancel", color = Color(0xFF71717A))
                }
            }
        )
    }
}

@Composable
private fun PillTabButton(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color.White else Color.White.copy(alpha = 0.12f),
        contentColor = if (selected) Color(0xFF09090B) else Color.White.copy(alpha = 0.85f),
        modifier = modifier.height(34.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = if (selected) Color(0xFF09090B) else Color.White.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StoryAndPhotosSection(
    state: BillingUiState,
    selectedPhotoName: String?,
    activeMeta: StoryPhotoMetadata?,
    editTitle: String,
    editDate: String,
    editCaption: String,
    todayFormatted: String,
    isGeneratingAiNote: Boolean,
    onTitleChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onCaptionChange: (String) -> Unit,
    onSelectPhoto: (String) -> Unit,
    onSaveDetails: () -> Unit,
    onDeletePhoto: () -> Unit,
    onGenerateAi: () -> Unit,
    onPickPhotoToUpload: () -> Unit,
    onRefreshPhotos: () -> Unit,
    getPhotoUrl: (String) -> String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Photo Gallery Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFFE4E4E7))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Header: Title with weight(1f) to ensure Add Photo button is NEVER clipped
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Story Photos Gallery",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF09090B)
                        )
                        Text(
                            text = "Tap a photo to set for receipt printing",
                            fontSize = 11.sp,
                            color = Color(0xFF71717A)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onRefreshPhotos,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh photos",
                                tint = Color(0xFF09090B),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Button(
                            onClick = onPickPhotoToUpload,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF09090B)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Photo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (state.isLoadingStoryPhotos) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF09090B), modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                } else if (state.storyPhotoNames.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(Color(0xFFF4F4F5), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFFE4E4E7), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFFA1A1AA), modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("No story photos uploaded yet", color = Color(0xFF71717A), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Tap '+ Add Photo' to upload customer story photos", color = Color(0xFFA1A1AA), fontSize = 11.sp)
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(state.storyPhotoNames) { fileName ->
                            val isSelected = fileName == selectedPhotoName
                            val meta = state.storyPhotoMetadataMap[fileName]
                            val label = meta?.title?.ifBlank { "Behind The Brew" } ?: "Behind The Brew"

                            Card(
                                modifier = Modifier
                                    .width(150.dp)
                                    .clickable { onSelectPhoto(fileName) },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0xFFFEF3C7) else Color(0xFFFFFFFF)
                                ),
                                border = if (isSelected) BorderStroke(2.dp, Color(0xFFD97706))
                                else BorderStroke(1.dp, Color(0xFFE4E4E7))
                            ) {
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .height(86.dp)
                                            .fillMaxWidth()
                                    ) {
                                        AsyncImage(
                                            model = getPhotoUrl(fileName),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        if (isSelected) {
                                            Surface(
                                                color = Color(0xFFD97706),
                                                shape = RoundedCornerShape(bottomStart = 8.dp),
                                                modifier = Modifier.align(Alignment.TopEnd)
                                            ) {
                                                Text(
                                                    "ACTIVE",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = Color(0xFF09090B)
                                        )
                                        Text(
                                            text = meta?.date ?: todayFormatted,
                                            fontSize = 10.sp,
                                            color = Color(0xFF71717A)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Selected Photo Details Card (Per-photo topic, date, caption editor)
        if (selectedPhotoName != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFE4E4E7))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Header Row with thumbnail and delete icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(70.dp)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black)
                            ) {
                                AsyncImage(
                                    model = getPhotoUrl(selectedPhotoName),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = "Selected Photo Settings",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF09090B)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = Color(0xFF16A34A),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            "7:4 Thermal",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("576 dots edge-to-edge", fontSize = 10.sp, color = Color(0xFF71717A))
                                }
                            }
                        }

                        IconButton(
                            onClick = onDeletePhoto,
                            modifier = Modifier.size(34.dp),
                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFFDC2626))
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete photo", modifier = Modifier.size(20.dp))
                        }
                    }

                    // Topic / Title Label Input
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = onTitleChange,
                        label = { Text("Topic Label / Story Title") },
                        placeholder = { Text("e.g. Behind The Brew") },
                        leadingIcon = { Icon(Icons.Default.Title, contentDescription = null, tint = Color(0xFF09090B), modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Date Input with Quick "Today" action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = editDate,
                            onValueChange = onDateChange,
                            label = { Text("Date on Receipt") },
                            placeholder = { Text("e.g. 27 Sep 2026") },
                            leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color(0xFF09090B), modifier = Modifier.size(18.dp)) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { onDateChange(todayFormatted) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text("Today", fontSize = 12.sp, color = Color(0xFF09090B), fontWeight = FontWeight.Bold)
                        }
                    }

                    // Journal Caption Note Input with prominent AI generate button
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Journal Note / Caption", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF09090B))
                            TextButton(
                                onClick = onGenerateAi,
                                enabled = !isGeneratingAiNote,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                if (isGeneratingAiNote) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFFD97706))
                                } else {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFD97706))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("✨ AI Write Note", fontSize = 12.sp, color = Color(0xFFD97706), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = editCaption,
                            onValueChange = onCaptionChange,
                            placeholder = { Text("Story behind this photo printed on customer receipt...") },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Save Button
                    Button(
                        onClick = onSaveDetails,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF09090B)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Photo Details to Cloud", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    }
                }
            }
        }

        // Bottom space so content is never covered by bottom navigation bar
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun ReceiptTextSection(
    tagline: String,
    subtitle: String,
    footer1: String,
    footer2: String,
    footer3: String,
    isStoryEnabled: Boolean,
    onTaglineChange: (String) -> Unit,
    onSubtitleChange: (String) -> Unit,
    onFooter1Change: (String) -> Unit,
    onFooter2Change: (String) -> Unit,
    onFooter3Change: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Tagline & Brand Texts Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFFE4E4E7))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text(
                        text = "Receipt Header & Tagline",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF09090B)
                    )
                    Text(
                        text = "Customize words printed beneath 'FILTR' on customer receipts",
                        fontSize = 11.sp,
                        color = Color(0xFF71717A)
                    )
                }

                // Tagline Input with amber quote styling
                Surface(
                    color = Color(0xFFFFFBEB),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FormatQuote, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Store Tagline (Prominent Motto)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Printed directly beneath 'FILTR' on customer tickets.",
                            fontSize = 11.sp,
                            color = Color(0xFFB45309)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = tagline,
                            onValueChange = onTaglineChange,
                            label = { Text("Tagline Phrase") },
                            placeholder = { Text("e.g. We are working for you <3") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Subtitle Input
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = onSubtitleChange,
                    label = { Text("Header Subtitle") },
                    placeholder = { Text("e.g. where coffee meets vibe") },
                    leadingIcon = { Icon(Icons.Default.Coffee, contentDescription = null, tint = Color(0xFF09090B), modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Footer lines Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFFE4E4E7))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text(
                        text = "Receipt Footer Notes",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF09090B)
                    )
                    Text(
                        text = "Printed at the bottom of the receipt ticket",
                        fontSize = 11.sp,
                        color = Color(0xFF71717A)
                    )
                }

                OutlinedTextField(
                    value = footer1,
                    onValueChange = onFooter1Change,
                    label = { Text("Footer Line 1 (Thank You Note)") },
                    placeholder = { Text("e.g. Thank you for hanging out with us!") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = footer2,
                    onValueChange = onFooter2Change,
                    label = { Text("Footer Line 2 (Social / Instagram)") },
                    placeholder = { Text("e.g. Tag your cup on Instagram @filtr.coffeee") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = footer3,
                    onValueChange = onFooter3Change,
                    label = { Text("Footer Line 3 (Brand Motto)") },
                    placeholder = { Text("e.g. Made fresh - Crafted with obsession") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(2.dp))

                Button(
                    onClick = onSave,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF09090B)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Bill Text Changes to Cloud", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                }
            }
        }

        // Bottom space for navigation bar
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun ThermalReceiptPreviewSection(
    tagline: String,
    subtitle: String,
    footer1: String,
    footer2: String,
    footer3: String,
    isStoryEnabled: Boolean,
    selectedPhotoName: String?,
    photoTitle: String,
    photoDate: String,
    photoCaption: String,
    getPhotoUrl: (String) -> String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Thermal Paper Simulation",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF09090B)
        )
        Text(
            text = "Accurate preview of 384-dot ESC/POS thermal ticket",
            fontSize = 11.sp,
            color = Color(0xFF71717A)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Thermal Paper Card
        Surface(
            color = Color(0xFFFFFDF8),
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, Color(0xFFE6E0D4)),
            modifier = Modifier.width(330.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Text(
                    text = "================================",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color.DarkGray
                )
                Text(
                    text = "FILTR",
                    fontWeight = FontWeight.Black,
                    fontSize = 26.sp,
                    letterSpacing = 2.sp,
                    color = Color.Black
                )
                if (tagline.isNotBlank()) {
                    Text(
                        text = tagline,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        color = Color.DarkGray,
                        textAlign = TextAlign.Center
                    )
                }

                Text(
                    text = "--------------------------------",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color.DarkGray
                )

                // Token info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Token: #1001", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Black)
                    Text("FLT-20260904-1001", fontSize = 11.sp, color = Color.DarkGray)
                }

                Text(
                    text = "--------------------------------",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color.DarkGray
                )

                // Sample items
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("1x Hazelnut Iced Coffee", fontSize = 12.sp, color = Color.Black)
                    Text("Rs 160.00", fontSize = 12.sp, color = Color.Black)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("1x Classic Cold Coffee", fontSize = 12.sp, color = Color.Black)
                    Text("Rs 120.00", fontSize = 12.sp, color = Color.Black)
                }

                Text(
                    text = "--------------------------------",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color.DarkGray
                )

                // Total
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("TOTAL AMOUNT", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color.Black)
                    Text("Rs 280.00", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.Black)
                }

                // Story & Photo Section
                if (isStoryEnabled && selectedPhotoName != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "================================",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color.DarkGray
                    )

                    // Edge-to-edge 7:4 thermal photo preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(Color.Black)
                    ) {
                        AsyncImage(
                            model = getPhotoUrl(selectedPhotoName),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (photoTitle.isNotBlank()) {
                        Text(
                            text = photoTitle.uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.Black,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (photoDate.isNotBlank()) {
                        Text(
                            text = photoDate,
                            fontSize = 10.sp,
                            color = Color.DarkGray,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (photoCaption.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "\"$photoCaption\"",
                            fontSize = 10.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = Color.Black,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "--------------------------------",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color.DarkGray
                )

                // Footers
                if (footer1.isNotBlank()) {
                    Text(text = footer1, fontSize = 10.sp, color = Color.DarkGray, textAlign = TextAlign.Center)
                }
                if (footer2.isNotBlank()) {
                    Text(text = footer2, fontSize = 10.sp, color = Color.DarkGray, textAlign = TextAlign.Center)
                }
                if (footer3.isNotBlank()) {
                    Text(text = footer3, fontSize = 9.sp, color = Color.Gray, textAlign = TextAlign.Center)
                }

                Text(
                    text = "================================",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color.DarkGray
                )
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}
