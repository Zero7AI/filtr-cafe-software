package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.ChecklistTaskEntity
import com.example.ui.ChecklistItemUiModel
import com.example.ui.ChecklistOverallStats
import com.example.ui.ChecklistSectionUiModel
import com.example.ui.theme.ShadcnBlack
import com.example.ui.theme.ShadcnBorder
import com.example.ui.theme.ShadcnMutedBg
import com.example.ui.theme.ShadcnMutedText
import com.example.ui.theme.ShadcnWhite
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChecklistScreen(
    sections: List<ChecklistSectionUiModel>,
    stats: ChecklistOverallStats,
    onToggleTask: (String) -> Unit,
    onToggleSection: (String) -> Unit,
    onOpenAddTask: () -> Unit,
    onRefresh: () -> Unit,
    showAddDialog: Boolean,
    onDismissAddDialog: () -> Unit,
    onAddTask: (title: String, section: String, notes: String) -> Unit,
    onDeleteTask: (ChecklistTaskEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row: Title, Date & Add Task Button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Daily Check List",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = ShadcnBlack
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = ShadcnMutedText,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stats.formattedDate,
                                style = MaterialTheme.typography.bodySmall,
                                color = ShadcnMutedText
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier
                                .size(40.dp)
                                .background(ShadcnWhite, RoundedCornerShape(8.dp))
                                .border(1.dp, ShadcnBorder, RoundedCornerShape(8.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync Checklist",
                                tint = ShadcnBlack,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Button(
                            onClick = onOpenAddTask,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ShadcnBlack,
                                contentColor = ShadcnWhite
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Add Task",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Dark Hero Progress Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ShadcnBlack),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "TODAY'S PROGRESS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    color = Color(0xFFA1A1AA)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${stats.completedCount} of ${stats.totalCount} Tasks Done",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = ShadcnWhite
                                )
                            }

                            // Big Percentage Display
                            Text(
                                text = "${stats.percentage}%",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (stats.percentage == 100 && stats.totalCount > 0) StatusGreen else ShadcnWhite
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Progress Bar
                        LinearProgressIndicator(
                            progress = { if (stats.totalCount == 0) 0f else stats.completedCount.toFloat() / stats.totalCount.toFloat() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (stats.percentage == 100) StatusGreen else Color(0xFFFFFFFF),
                            trackColor = Color(0xFF27272A),
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Stats Badges Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF14532D).copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, Color(0xFF16A34A).copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(StatusGreen, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${stats.completedCount} Completed",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF86EFAC)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (stats.pendingCount > 0) Color(0xFF78350F).copy(alpha = 0.4f) else Color(0xFF27272A),
                                border = BorderStroke(
                                    1.dp,
                                    if (stats.pendingCount > 0) Color(0xFFD97706).copy(alpha = 0.6f) else Color(0xFF3F3F46)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(
                                                if (stats.pendingCount > 0) StatusAmber else Color(0xFFA1A1AA),
                                                CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${stats.pendingCount} Pending",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (stats.pendingCount > 0) Color(0xFFFDE68A) else Color(0xFFA1A1AA)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section Cards
            items(sections, key = { it.sectionId }) { section ->
                ChecklistSectionCard(
                    section = section,
                    onToggleSection = { onToggleSection(section.sectionId) },
                    onToggleTask = onToggleTask,
                    onDeleteTask = onDeleteTask
                )
            }

            // Bottom Spacing for Drawer & Scrolling
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Add Task Dialog
        if (showAddDialog) {
            AddChecklistTaskDialog(
                onDismiss = onDismissAddDialog,
                onAdd = onAddTask
            )
        }
    }
}

@Composable
fun ChecklistSectionCard(
    section: ChecklistSectionUiModel,
    onToggleSection: () -> Unit,
    onToggleTask: (String) -> Unit,
    onDeleteTask: (ChecklistTaskEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ShadcnWhite),
        border = BorderStroke(1.dp, ShadcnBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Section Header (Clickable to collapse/expand)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleSection() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = section.sectionName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ShadcnBlack
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(${section.completedCount}/${section.totalCount})",
                        style = MaterialTheme.typography.bodySmall,
                        color = ShadcnMutedText,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status Badge: Completed vs Pending
                    if (section.isAllCompleted) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFDCFCE7),
                            border = BorderStroke(1.dp, Color(0xFF86EFAC))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = StatusGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Completed",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFFEF3C7),
                            border = BorderStroke(1.dp, Color(0xFFFDE68A))
                        ) {
                            Text(
                                text = "Pending (${section.pendingCount} left)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Expand / Collapse Chevron
                    Icon(
                        imageVector = if (section.isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (section.isExpanded) "Collapse" else "Expand",
                        tint = ShadcnMutedText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Collapsible Content
            AnimatedVisibility(
                visible = section.isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = Color(0xFFF4F4F5), thickness = 1.dp)

                    if (section.items.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No tasks in this section yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ShadcnMutedText
                            )
                        }
                    } else {
                        section.items.forEachIndexed { index, item ->
                            ChecklistTaskRow(
                                item = item,
                                onToggle = { onToggleTask(item.task.id) },
                                onDelete = { onDeleteTask(item.task) }
                            )
                            if (index < section.items.size - 1) {
                                HorizontalDivider(
                                    color = Color(0xFFF4F4F5),
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(start = 52.dp)
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
fun ChecklistTaskRow(
    item: ChecklistItemUiModel,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Minimalist 1-Tap Toggle Circle
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .then(
                    if (item.isCompleted) {
                        Modifier.background(StatusGreen)
                    } else {
                        Modifier
                            .background(Color(0xFFFAFAFA))
                            .border(1.5.dp, Color(0xFFD4D4D8), CircleShape)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (item.isCompleted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completed",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Task Content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.task.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (item.isCompleted) FontWeight.Normal else FontWeight.Medium,
                color = if (item.isCompleted) Color(0xFF71717A) else ShadcnBlack
            )

            if (item.task.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.task.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFA1A1AA),
                    fontSize = 12.sp
                )
            }

            if (item.isCompleted && item.completedTimestamp > 0L) {
                Spacer(modifier = Modifier.height(2.dp))
                val timeStr = remember(item.completedTimestamp) {
                    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(item.completedTimestamp))
                }
                Text(
                    text = "Done at $timeStr by ${item.completedBy}",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Custom Task Delete Button
        if (!item.task.isDefault) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete custom task",
                    tint = Color(0xFFA1A1AA),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun AddChecklistTaskDialog(
    onDismiss: () -> Unit,
    onAdd: (title: String, section: String, notes: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedSection by remember { mutableStateOf("01_OPENING") }
    var notes by remember { mutableStateOf("") }

    val sections = listOf(
        "01_OPENING" to "Opening",
        "02_CLEANING" to "Hygiene & Cleaning",
        "03_ROUTINE" to "Routine Checks",
        "04_CLOSING" to "Closing",
        "05_GENERAL" to "General"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Checklist Task",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = ShadcnBlack
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Description *") },
                    placeholder = { Text("e.g. Wipe all patio chairs & tables") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column {
                    Text(
                        text = "Assign Section",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ShadcnMutedText
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(sections) { (secKey, secLabel) ->
                            FilterChip(
                                selected = selectedSection == secKey,
                                onClick = { selectedSection = secKey },
                                label = { Text(secLabel, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ShadcnBlack,
                                    selectedLabelColor = ShadcnWhite
                                )
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Target Time (Optional)") },
                    placeholder = { Text("e.g. Check before 11:30 AM") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onAdd(title.trim(), selectedSection, notes.trim())
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ShadcnBlack,
                    contentColor = ShadcnWhite
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Add Task", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancel", color = ShadcnBlack)
            }
        },
        containerColor = ShadcnWhite,
        shape = RoundedCornerShape(16.dp)
    )
}
