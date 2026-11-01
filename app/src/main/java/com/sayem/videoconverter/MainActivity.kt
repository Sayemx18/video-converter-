package com.sayem.videoconverter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sayem.videoconverter.data.VideoInfo
import com.sayem.videoconverter.data.VideoMetadataReader
import com.sayem.videoconverter.ui.theme.AppTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val metadataReader = VideoMetadataReader(this)
        setContent {
            AppTheme {
                var video by remember { mutableStateOf<VideoInfo?>(null) }
                var pickerError by remember { mutableStateOf<String?>(null) }
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) {
                        runCatching {
                            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        video = runCatching { metadataReader.read(uri) }
                            .onFailure { pickerError = "Could not read this video's details." }
                            .getOrNull()
                        if (video != null) pickerError = null
                    }
                }

                HomeScreen(
                    video = video,
                    message = pickerError,
                    onSelect = { picker.launch(arrayOf("video/*")) }
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun HomeScreen(video: VideoInfo?, message: String?, onSelect: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0B0D12)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF1C2830), modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Movie, null, tint = Color(0xFF64E6B0), modifier = Modifier.padding(12.dp))
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text("SAYEM", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("VIDEO CONVERTER", color = Color(0xFF8B929E), fontSize = 11.sp, letterSpacing = 1.4.sp)
                }
            }
            Spacer(Modifier.height(42.dp))
            Text("Make every frame\nlook its best.", color = Color.White, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
            Text("Convert your videos to crisp, shareable Full HD.", color = Color(0xFF9BA2AE), modifier = Modifier.padding(top = 12.dp))
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
                    if (message != null) Text(message, color = Color(0xFFFF8A80), modifier = Modifier.padding(top = 12.dp))
                    Button(
                        onClick = onSelect,
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(52.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64E6B0), contentColor = Color(0xFF082017))
                    ) { Text(if (video == null) "Select video" else "Choose another video", fontWeight = FontWeight.Bold) }
                }
            }

            Spacer(Modifier.height(18.dp))
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF141820))) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("OUTPUT PRESET", color = Color(0xFF8B929E), fontSize = 11.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ArrowDownward, null, tint = Color(0xFF64E6B0))
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            val target = video?.let { if (it.height > it.width) "1080 × 1920" else "1920 × 1080" } ?: "1920 × 1080 / 1080 × 1920"
                            Text("Full HD · $target", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Aspect ratio preserved · H.264 AVC", color = Color(0xFF9BA2AE), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF64E6B0))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text("CONVERSION ENGINE NOT CONNECTED", color = Color(0xFF777F8C), fontSize = 10.sp, letterSpacing = 1.1.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("The interface is ready. Video encoding will be added in a later step.", color = Color(0xFF626A76), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp).align(Alignment.CenterHorizontally))
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
