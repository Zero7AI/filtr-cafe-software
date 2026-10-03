package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReceiptStoryDialog(
    initialTitle: String,
    initialDate: String,
    initialCaption: String,
    initialImagePath: String,
    isEnabled: Boolean,
    isGeneratingAiNote: Boolean,
    // Multi-photo support
    storyPhotoNames: List<String> = emptyList(),
    isLoadingPhotos: Boolean = false,
    isUploadingPhoto: Boolean = false,
    onUploadPhoto: (ByteArray) -> Unit = {},
    onDeletePhoto: (String) -> Unit = {},
    getPhotoUrl: (String) -> String = { "" },
    onSave: (title: String, date: String, caption: String, imagePath: String, isEnabled: Boolean) -> Unit,
    onGenerateAiNote: (title: String, date: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val todayFormatted = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()) }

    var title by remember { mutableStateOf(initialTitle.ifBlank { "Testing of Hazelnut Iced Coffee" }) }
    var date by remember { mutableStateOf(initialDate.ifBlank { todayFormatted }) }
    var caption by remember { mutableStateOf(initialCaption.ifBlank { "Late nights, endless trials, and pure passion. We spent months perfecting this exact brew ratio for you." }) }
    var imagePath by remember { mutableStateOf(initialImagePath) }
    var enabled by remember { mutableStateOf(isEnabled) }

    // Launcher for uploading to Supabase
    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes != null && bytes.isNotEmpty()) {
                    onUploadPhoto(bytes)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = null,
                    tint = Color(0xFF09090B),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Receipt Story & Photo", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Manage R&D / testing photos for the Behind The Brew section. A random photo is picked each time you print.",
                    fontSize = 12.sp,
                    color = Color(0xFF71717A),
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle Enable/Disable
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF4F4F5), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Print on Receipts", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            if (enabled) "Story card will be printed" else "Story card omitted",
                            fontSize = 11.sp,
                            color = Color(0xFF71717A)
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF16A34A)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // === PHOTO GALLERY ===
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Story Photos (${storyPhotoNames.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    if (isUploadingPhoto) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF2563EB)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Uploading...", fontSize = 11.sp, color = Color(0xFF71717A))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (isLoadingPhotos) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                } else {
                    // Photo grid - use fixed height, not LazyGrid inside scrollable
                    val rows = (storyPhotoNames.size + 3) / 3  // +1 for Add button, ceil division
                    val gridHeight = ((rows.coerceAtLeast(1)) * 95).coerceAtMost(285).dp

                    Column(modifier = Modifier.height(gridHeight)) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Add photo card
                            item {
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F4F5)),
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clickable { uploadLauncher.launch("image/*") }
                                        .border(1.dp, Color(0xFFE4E4E7), RoundedCornerShape(8.dp))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Spacer(modifier = Modifier.weight(1f))
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add Photo",
                                            tint = Color(0xFF2563EB),
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Add",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF2563EB)
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }

                            // Photo thumbnails
                            items(storyPhotoNames) { fileName ->
                                Box {
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .border(1.dp, Color(0xFFE4E4E7), RoundedCornerShape(8.dp))
                                    ) {
                                        AsyncImage(
                                            model = getPhotoUrl(fileName),
                                            contentDescription = fileName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.aspectRatio(1f)
                                        )
                                    }
                                    // Delete button overlay
                                    IconButton(
                                        onClick = { onDeletePhoto(fileName) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(22.dp)
                                            .background(Color(0xCCEF4444), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Delete",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (storyPhotoNames.isEmpty()) {
                        Text(
                            text = "No photos yet. Tap + to add R&D photos.",
                            fontSize = 11.sp,
                            color = Color(0xFFA1A1AA),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Text(
                            text = "A random photo is selected each time you print a receipt.",
                            fontSize = 10.sp,
                            color = Color(0xFF71717A),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Topic / Title") },
                    leadingIcon = { Icon(Icons.Default.Title, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Date Input
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (e.g. 04 Sep 2026)") },
                    leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // AI Generate Button & Note Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Journal Note / Caption", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Button(
                        onClick = { onGenerateAiNote(title, date) },
                        enabled = !isGeneratingAiNote,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        if (isGeneratingAiNote) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AI Note", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    placeholder = { Text("Write a heartfelt note for customers...") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(title.trim(), date.trim(), caption.trim(), imagePath, enabled)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF09090B)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF71717A))
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
