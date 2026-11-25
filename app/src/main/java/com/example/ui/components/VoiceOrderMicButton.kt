package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.util.AudioRecorderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Suppress("UNUSED_PARAMETER")
@Composable
fun VoiceOrderMicButton(
    isListening: Boolean,
    isParsing: Boolean,
    onStartListening: () -> Unit,
    onProcessTranscript: (String) -> Unit,
    onProcessAudio: (ByteArray, String, Long) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isHoldingToRecord by remember { mutableStateOf(false) }
    var recordDurationSeconds by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentIsHolding by rememberUpdatedState(isHoldingToRecord)

    val audioRecorderManager = remember { AudioRecorderManager(context) }

    DisposableEffect(Unit) {
        onDispose {
            if (currentIsHolding) {
                onCancel()
            }
            audioRecorderManager.cancelAndRelease()
        }
    }

    // Timer while holding down mic button
    LaunchedEffect(isHoldingToRecord) {
        if (isHoldingToRecord) {
            recordDurationSeconds = 0
            while (isHoldingToRecord) {
                delay(1000)
                recordDurationSeconds++
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Microphone permission is required for voice ordering", Toast.LENGTH_SHORT).show()
        }
    }

    val checkMicPermission = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        hasPermission
    }

    // Pulsing animation for FAB
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        horizontalAlignment = Alignment.End,
        modifier = modifier
    ) {
        // Holding-to-record live status bubble above FAB
        AnimatedVisibility(
            visible = isHoldingToRecord || isParsing,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                color = if (isParsing) Color(0xFF09090B) else Color(0xFFDC2626),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .padding(bottom = 12.dp, end = 4.dp)
                    .testTag("voice_recording_status_bubble")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isParsing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "🎙️ Deepgram AI Parsing Order...",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "🔴 RECORDING AUDIO... (${String.format(Locale.US, "%02d", recordDurationSeconds)}s)",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Tap mic again to send recording to AI",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        Box(
            contentAlignment = Alignment.Center
        ) {
            // TAP TO RECORD & TAP AGAIN TO SEND
            FloatingActionButton(
                onClick = {
                    if (!checkMicPermission()) return@FloatingActionButton

                    if (!isHoldingToRecord) {
                        // Start recording audio
                        val recordedFile = audioRecorderManager.startRecording()
                        if (recordedFile != null) {
                            isHoldingToRecord = true
                            onStartListening()
                        } else {
                            Toast.makeText(
                                context,
                                "Unable to start microphone recording",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        // Stop recording and send audio to AI parser off Main thread
                        isHoldingToRecord = false
                        val audioFile = audioRecorderManager.stopRecording()
                        val durationMs = audioRecorderManager.lastRecordingDurationMs
                        if (audioFile != null) {
                            scope.launch {
                                val bytes = withContext(Dispatchers.IO) {
                                    try {
                                        if (audioFile.exists() && audioFile.length() > 0) {
                                            audioFile.readBytes()
                                        } else {
                                            null
                                        }
                                    } catch (_: Exception) {
                                        null
                                    } finally {
                                        audioFile.delete()
                                    }
                                }
                                if (bytes != null && bytes.isNotEmpty()) {
                                    onProcessAudio(bytes, "audio/m4a", durationMs)
                                }
                            }
                        }
                    }
                },
                containerColor = if (isHoldingToRecord) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(8.dp),
                modifier = Modifier
                    .size(68.dp)
                    .scale(if (isHoldingToRecord || isListening) pulseScale else 1.0f)
                    .testTag("ai_mic_fab")
            ) {
                Icon(
                    imageVector = if (isHoldingToRecord) Icons.Default.GraphicEq else Icons.Default.Mic,
                    contentDescription = "Tap to Record / Tap to Send Voice Order",
                    modifier = Modifier.size(34.dp)
                )
            }
        }
    }
}
