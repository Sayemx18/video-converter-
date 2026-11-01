package com.sayem.videoconverter.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns

class VideoMetadataReader(private val context: Context) {
    fun read(uri: Uri): VideoInfo {
        val values = MediaMetadataRetriever().useRetriever(uri)
        val nameAndSize = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0).orEmpty() to cursor.getLong(1).coerceAtLeast(0L)
                } else "Video" to 0L
            } ?: ("Video" to 0L)
        return VideoInfo(
            uri = uri,
            name = nameAndSize.first.ifBlank { "Video" },
            width = values.first,
            height = values.second,
            durationMs = values.third,
            sizeBytes = nameAndSize.second
        )
    }

    private fun MediaMetadataRetriever.useRetriever(uri: Uri): Triple<Int, Int, Long> = try {
        setDataSource(context, uri)
        Triple(
            extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
            extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
            extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        )
    } finally {
        release()
    }
}
