package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.util.AudioRecorderManager
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun VoiceOrderMicButton(
    isListening: Boolean,
    isParsing: Boolean,
    onStartListening: () -> Unit,
    onProcessTranscript: (String) -> Unit,
    onProcessAudio: (ByteArray, String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    var spokenInputText by remember { mutableStateOf("") }
    var isHoldingToRecord by remember { mutableStateOf(false) }
    var recordDurationSeconds by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    val audioRecorderManager = remember { AudioRecorderManager(context) }

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

    // Native Speech Recognizer Result Launcher
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                val textToSend = spokenText.trim()
                spokenInputText = ""
                showDialog = false
                onProcessTranscript(textToSend)
            }
        }
    }

    val launchSpeechToText = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak order e.g. 2 Filter Coffee and 1 Fries for Rahul")
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Speech Recognition is not available on this device", Toast.LENGTH_SHORT).show()
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
                            text = "✨ Gemini 3.5 Flash Lite Parsing Order...",
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
                        audioRecorderManager.startRecording()
                        isHoldingToRecord = true
                        onStartListening()
                    } else {
                        // Stop recording and send audio to Gemini API!
                        isHoldingToRecord = false
                        val audioFile = audioRecorderManager.stopRecording()
                        if (audioFile != null && audioFile.exists() && audioFile.length() > 0) {
                            val bytes = audioFile.readBytes()
                            audioFile.delete()
                            if (bytes.isNotEmpty()) {
                                onProcessAudio(bytes, "audio/m4a")
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

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
                onCancel()
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Voice Order",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "AI Voice Order (Gemini 3.5 Flash Lite)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "💡 Tip: Press & hold the Mic button for 4-5 seconds to speak naturally! Gemini 3.5 Flash Lite will automatically match your coffee SKUs and autofill the order.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isParsing) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Gemini 3.5 Flash Lite is parsing your voice order...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                if (checkMicPermission()) {
                                    launchSpeechToText()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tap to Speak Order", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = spokenInputText,
                            onValueChange = { spokenInputText = it },
                            label = { Text("Spoken / Typed Order") },
                            placeholder = { Text("e.g. 2 Filter Coffee and 1 Fries for Rahul 9876543210") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("voice_transcript_input"),
                            shape = RoundedCornerShape(12.dp),
                            maxLines = 3,
                            trailingIcon = {
                                if (spokenInputText.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            val txt = spokenInputText.trim()
                                            spokenInputText = ""
                                            showDialog = false
                                            onProcessTranscript(txt)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = "Submit",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Or tap a sample FILTR voice order:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        val presets = listOf(
                            "2 Filter Coffee and 1 Peri Peri Fries for Rahul 9876543210",
                            "1 Just Frappe and 1 Bombay Sandwich for Priya",
                            "2 OG Vietnamese and 1 Paneer Sandwich"
                        )

                        presets.forEach { preset ->
                            Surface(
                                onClick = {
                                    spokenInputText = ""
                                    showDialog = false
                                    onProcessTranscript(preset)
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Text(
                                    text = "🎤 \"$preset\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(8.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (spokenInputText.isNotBlank()) {
                            val txt = spokenInputText.trim()
                            spokenInputText = ""
                            showDialog = false
                            onProcessTranscript(txt)
                        }
                    },
                    enabled = spokenInputText.isNotBlank() && !isParsing,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Parse Order")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        spokenInputText = ""
                        showDialog = false
                        onCancel()
                    }
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}
