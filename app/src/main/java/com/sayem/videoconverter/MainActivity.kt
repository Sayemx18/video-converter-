package com.sayem.videoconverter

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sayem.videoconverter.data.VideoInfo
import com.sayem.videoconverter.data.VideoMetadataReader
import com.sayem.videoconverter.conversion.ConversionRequest
import com.sayem.videoconverter.conversion.Media3VideoConverter
import com.sayem.videoconverter.conversion.ResolutionPreset
import com.sayem.videoconverter.ui.theme.AppTheme
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val metadataReader = VideoMetadataReader(this)
        val converter = Media3VideoConverter(this)
        setContent {
            AppTheme {
                var video by remember { mutableStateOf<VideoInfo?>(null) }
                var pickerError by remember { mutableStateOf<String?>(null) }
                var statusMessage by remember { mutableStateOf<String?>(null) }
                var statusIsSuccess by remember { mutableStateOf(false) }
                var isConverting by remember { mutableStateOf(false) }
                var isSavingToGallery by remember { mutableStateOf(false) }
                var progress by remember { mutableStateOf<Int?>(null) }
                var conversionJob by remember { mutableStateOf<Job?>(null) }
                var resolutionPreset by remember { mutableStateOf(ResolutionPreset.FULL_HD) }
                val scope = rememberCoroutineScope()

                fun startConversion(selected: VideoInfo, preset: ResolutionPreset = resolutionPreset) {
                    if (isConverting) return
                    isConverting = true
                    progress = null
                    isSavingToGallery = false
                    statusMessage = "Preparing video…"
                    statusIsSuccess = false
                    conversionJob = scope.launch {
                        try {
                            converter.convert(
                                request = ConversionRequest(selected.uri, selected.width, selected.height, preset),
                                onProgress = { value -> progress = value },
                                onSavingToGallery = { isSavingToGallery = true }
                            )
                            statusMessage = "Converted video saved to your Gallery."
                            statusIsSuccess = true
                        } catch (_: CancellationException) {
                            statusMessage = "Conversion cancelled."
                            statusIsSuccess = false
                        } catch (error: Exception) {
                            statusMessage = error.localizedMessage ?: "Conversion failed. Please try another video."
                            statusIsSuccess = false
                        } finally {
                            isConverting = false
                            isSavingToGallery = false
                            conversionJob = null
                        }
                    }
                }

                val legacyStoragePermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) video?.let(::startConversion)
                    else {
                        statusMessage = "Storage permission is needed to save videos on Android 8 and 9."
                        statusIsSuccess = false
                    }
                }
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) {
                        runCatching {
                            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        video = runCatching { metadataReader.read(uri) }
                            .onFailure { pickerError = "Could not read this video's details." }
                            .getOrNull()
                        if (video != null) pickerError = null
                        statusMessage = null
                    }
                }

                HomeScreen(
                    video = video,
                    message = pickerError ?: statusMessage,
                    messageIsSuccess = pickerError == null && statusIsSuccess,
                    isConverting = isConverting,
                    isSavingToGallery = isSavingToGallery,
                    progress = progress,
                    resolutionPreset = resolutionPreset,
                    onResolutionPresetChange = { resolutionPreset = it },
                    onSelect = { if (!isConverting) picker.launch(arrayOf("video/*")) },
                    onConvert = {
                        video?.let { selected ->
                            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                            ) {
                                legacyStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else startConversion(selected)
                        }
                    },
                    onCancel = { conversionJob?.cancel() }
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun HomeScreen(
    video: VideoInfo?,
    message: String?,
    messageIsSuccess: Boolean,
    isConverting: Boolean,
    isSavingToGallery: Boolean,
    progress: Int?,
    resolutionPreset: ResolutionPreset,
    onResolutionPresetChange: (ResolutionPreset) -> Unit,
    onSelect: () -> Unit,
    onConvert: () -> Unit,
    onCancel: () -> Unit
) {
    val (targetWidth, targetHeight) = resolutionPreset.dimensions(video?.let { it.height > it.width } ?: false)
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0B0D12)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF1C2830), modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Movie, null, tint = Color(0xFF64E6B0), modifier = Modifier.padding(12.dp))
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text("VIDEO CODEC", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("CONVERTER", color = Color(0xFF8B929E), fontSize = 11.sp, letterSpacing = 1.4.sp)
                }
            }
            Spacer(Modifier.height(42.dp))
            Text("Make every frame\nlook its best.", color = Color.White, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
            Text("Choose the perfect size for every video.", color = Color(0xFF9BA2AE), modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.height(28.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141820))
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    if (video == null) {
                        Icon(Icons.Rounded.VideoFile, null, tint = Color(0xFF64E6B0), modifier = Modifier.size(38.dp))
                        Text("Choose a video", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
                        Text("Select a video from your device to see its details.", color = Color(0xFF9BA2AE), fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                    } else {
                        VideoDetails(video)
                    }
                    if (message != null) Text(message, color = if (messageIsSuccess) Color(0xFF64E6B0) else Color(0xFFFF8A80), modifier = Modifier.padding(top = 12.dp))
                    Button(
                        onClick = onSelect,
                        enabled = !isConverting,
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(52.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64E6B0), contentColor = Color(0xFF082017))
                    ) { Text(if (video == null) "Select video" else "Choose another video", fontWeight = FontWeight.Bold) }
                    if (video != null) {
                        if (isConverting) {
                            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (isSavingToGallery) "Saving to Gallery" else "Converting", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                if (!isSavingToGallery) Text(progress?.let { "$it%" } ?: "Preparing…", color = Color(0xFF64E6B0), fontSize = 13.sp)
                            }
                            if (progress == null || isSavingToGallery) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp), color = Color(0xFF64E6B0), trackColor = Color(0xFF29313A))
                            else LinearProgressIndicator({ progress.coerceIn(0, 100) / 100f }, Modifier.fillMaxWidth().padding(top = 10.dp), color = Color(0xFF64E6B0), trackColor = Color(0xFF29313A))
                            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(48.dp), shape = RoundedCornerShape(15.dp)) {
                                Text("Cancel conversion", color = Color(0xFFFF8A80))
                            }
                        } else {
                            Button(
                                onClick = onConvert,
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp),
                                shape = RoundedCornerShape(15.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64E6B0), contentColor = Color(0xFF082017))
                            ) { Text("Convert to ${resolutionPreset.label} ${resolutionPreset.quality}", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF141820))) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("OUTPUT PRESET", color = Color(0xFF8B929E), fontSize = 11.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ResolutionPreset.entries.forEach { preset ->
                            val selected = resolutionPreset == preset
                            Card(
                                modifier = Modifier.weight(1f).clickable(enabled = !isConverting) { onResolutionPresetChange(preset) },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, if (selected) Color(0xFF64E6B0) else Color(0xFF29313A)),
                                colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF1C302B) else Color(0xFF1B2029))
                            ) {
                                Column(Modifier.fillMaxWidth().padding(vertical = 13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(preset.label, color = if (selected) Color(0xFF64E6B0) else Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(preset.quality, color = Color(0xFF9BA2AE), fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                                }
                            }
                        }
                    }
                    Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ArrowDownward, null, tint = Color(0xFF64E6B0))
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text("${resolutionPreset.quality} · ${targetWidth} × ${targetHeight}", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Aspect ratio preserved · H.264 AVC · AAC audio", color = Color(0xFF9BA2AE), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        if (video != null) Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF64E6B0))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("PROCESSED ON YOUR DEVICE", color = Color(0xFF777F8C), fontSize = 10.sp, letterSpacing = 1.1.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@androidx.compose.runtime.Composable
private fun VideoDetails(video: VideoInfo) {
    Text("READY TO CONVERT", color = Color(0xFF64E6B0), fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
    Text(video.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("${video.width} × ${video.height}", color = Color(0xFFADB4BF), fontSize = 12.sp)
        Text(formatDuration(video.durationMs), color = Color(0xFFADB4BF), fontSize = 12.sp)
        Text(formatSize(video.sizeBytes), color = Color(0xFFADB4BF), fontSize = 12.sp)
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
}

private fun formatSize(bytes: Long): String = if (bytes < 1024L * 1024L) "%.0f KB".format(bytes / 1024f) else "%.1f MB".format(bytes / (1024f * 1024f))
