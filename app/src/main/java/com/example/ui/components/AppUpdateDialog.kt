package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.remote.AppUpdateInfo
import com.example.data.remote.AppUpdateStatus
import com.example.ui.theme.AmberAccentSecondary
import com.example.ui.theme.CoffeeBrownPrimary
import java.io.File
import java.util.Locale

@Composable
fun AppUpdateDialog(
    status: AppUpdateStatus,
    onStartDownload: (AppUpdateInfo) -> Unit,
    onInstallApk: (File) -> Unit,
    onRetryCheck: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            // Do not dismiss during active download to prevent interrupted download state
            if (status !is AppUpdateStatus.Downloading) {
                onDismiss()
            }
        },
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(CoffeeBrownPrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (status) {
                        is AppUpdateStatus.ReadyToInstall -> Icons.Default.CheckCircle
                        is AppUpdateStatus.Error -> Icons.Default.ErrorOutline
                        else -> Icons.Default.SystemUpdate
                    },
                    contentDescription = "App Update",
                    tint = when (status) {
                        is AppUpdateStatus.ReadyToInstall -> Color(0xFF10B981)
                        is AppUpdateStatus.Error -> MaterialTheme.colorScheme.error
                        else -> CoffeeBrownPrimary
                    },
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                text = when (status) {
                    is AppUpdateStatus.Available -> "Update Available 🚀"
                    is AppUpdateStatus.Downloading -> "Downloading Update..."
                    is AppUpdateStatus.ReadyToInstall -> "Ready to Install"
                    is AppUpdateStatus.Error -> "Update Failed"
                    else -> "Software Update"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = CoffeeBrownPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                when (status) {
                    is AppUpdateStatus.Available -> {
                        val update = status.updateInfo

                        // Version badge comparison
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Current", fontSize = 11.sp, color = Color.Gray)
                                Text("v${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = AmberAccentSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Latest", fontSize = 11.sp, color = Color(0xFF10B981))
                                Text("v${update.versionName}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF10B981))
                            }
                        }

                        if (update.apkSizeBytes > 0) {
                            val sizeMb = String.format(Locale.US, "%.1f", update.apkSizeBytes / (1024f * 1024f))
                            Text(
                                text = "Size: ~$sizeMb MB",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "What's New:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = CoffeeBrownPrimary
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Release notes scrollable area
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ) {
                            Text(
                                text = update.releaseNotes.ifBlank { "Minor bug fixes and performance improvements." },
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                modifier = Modifier
                                    .padding(10.dp)
                                    .verticalScroll(rememberScrollState()),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    is AppUpdateStatus.Downloading -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { status.progressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = CoffeeBrownPrimary,
                            trackColor = CoffeeBrownPrimary.copy(alpha = 0.2f)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        val downloadedMb = String.format(Locale.US, "%.1f", status.bytesDownloaded / (1024f * 1024f))
                        val totalMb = if (status.totalBytes > 0) {
                            String.format(Locale.US, "%.1f", status.totalBytes / (1024f * 1024f))
                        } else {
                            "?"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${status.progressPercent}%",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = CoffeeBrownPrimary
                            )
                            Text(
                                text = "$downloadedMb MB / $totalMb MB",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    is AppUpdateStatus.ReadyToInstall -> {
                        Text(
                            text = "The latest update (v${status.updateInfo.versionName}) has been downloaded successfully.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap Install to complete the update.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF10B981)
                        )
                    }

                    is AppUpdateStatus.Error -> {
                        Text(
                            text = status.message,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    else -> {}
                }
            }
        },
        confirmButton = {
            when (status) {
                is AppUpdateStatus.Available -> {
                    Button(
                        onClick = { onStartDownload(status.updateInfo) },
                        colors = ButtonDefaults.buttonColors(containerColor = CoffeeBrownPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Update Now")
                    }
                }

                is AppUpdateStatus.Downloading -> {
                    // Cannot confirm while downloading
                }

                is AppUpdateStatus.ReadyToInstall -> {
                    Button(
                        onClick = { onInstallApk(status.apkFile) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Install Now")
                    }
                }

                is AppUpdateStatus.Error -> {
                    Button(
                        onClick = onRetryCheck,
                        colors = ButtonDefaults.buttonColors(containerColor = CoffeeBrownPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retry")
                    }
                }

                else -> {}
            }
        },
        dismissButton = {
            if (status !is AppUpdateStatus.Downloading) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = if (status is AppUpdateStatus.Available) "Later" else "Close",
                        color = Color.Gray
                    )
                }
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
