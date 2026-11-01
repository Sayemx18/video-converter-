package com.sayem.videoconverter.conversion

import android.net.Uri
import kotlinx.coroutines.flow.Flow

data class ConversionRequest(
    val source: Uri,
    val outputWidth: Int,
    val outputHeight: Int,
    val codec: String = "video/avc"
)

sealed interface ConversionState {
    data class Progress(val percent: Int) : ConversionState
    data class Completed(val output: Uri) : ConversionState
    data class Failed(val reason: String) : ConversionState
}

/** Implement with a maintained encoder backend (for example MediaCodec or a supported FFmpeg integration). */
interface VideoConverter {
    fun convert(request: ConversionRequest): Flow<ConversionState>
}

/** Placeholder boundary: intentionally provides no conversion and emits no pretend progress. */
class UnavailableVideoConverter : VideoConverter {
    override fun convert(request: ConversionRequest): Flow<ConversionState> = kotlinx.coroutines.flow.flow {
        emit(ConversionState.Failed("Video conversion is not available yet."))
    }
}
