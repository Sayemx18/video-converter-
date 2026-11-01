package com.sayem.videoconverter.data

data class VideoInfo(
    val uri: android.net.Uri,
    val name: String,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val durationMs: Long,
    val sizeBytes: Long
)
