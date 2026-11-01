package com.sayem.videoconverter.conversion

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Clock
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.EncoderSelector
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.TransformationRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import com.google.common.collect.ImmutableList
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ConversionRequest(
    val source: Uri,
    val width: Int,
    val height: Int
)

/** Uses Media3 Transformer for device-native H.264/AAC transcoding and publishes directly to Gallery. */
@OptIn(UnstableApi::class)
class Media3VideoConverter(context: Context) {
    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver
    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun convert(
        request: ConversionRequest,
        onProgress: (Int) -> Unit,
        onSavingToGallery: () -> Unit
    ): Uri =
        suspendCancellableCoroutine { continuation ->
            val displayName = "Sayem_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}_${UUID.randomUUID().toString().take(8)}.mp4"
            val terminal = AtomicBoolean(false)
            var requestedOutputHeight = 0
            var transformer: Transformer? = null
            var progressRunnable: Runnable? = null
            var outputFile: File? = null
            var outputUri: Uri? = null
            val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            fun cleanupOutput() {
                ioScope.coroutineContext.cancelChildren()
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching { outputUri?.let { contentResolver.delete(it, null, null) } }
                    outputFile?.delete()
                    ioScope.cancel()
                }
            }

            fun cleanupAfterFailure() {
                runCatching { outputUri?.let { contentResolver.delete(it, null, null) } }
                outputFile?.delete()
            }

            continuation.invokeOnCancellation {
                mainHandler.post {
                    terminal.set(true)
                    progressRunnable?.let(mainHandler::removeCallbacks)
                    transformer?.cancel()
                    cleanupOutput()
                }
            }

            val listener = object : Transformer.Listener {
                override fun onFallbackApplied(
                    composition: Composition,
                    originalTransformationRequest: TransformationRequest,
                    fallbackTransformationRequest: TransformationRequest
                ) {
                    val codecChanged = fallbackTransformationRequest.videoMimeType != MimeTypes.VIDEO_H264 ||
                        fallbackTransformationRequest.audioMimeType != MimeTypes.AUDIO_AAC
                    val resolutionChanged = fallbackTransformationRequest.outputHeight != C.LENGTH_UNSET &&
                        fallbackTransformationRequest.outputHeight != requestedOutputHeight
                    if ((codecChanged || resolutionChanged) && terminal.compareAndSet(false, true)) {
                        progressRunnable?.let(mainHandler::removeCallbacks)
                        transformer?.cancel()
                        cleanupOutput()
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("This device cannot produce the required Full HD H.264/AAC output.")
                            )
                        }
                    }
                }

                override fun onCompleted(composition: Composition, exportResult: androidx.media3.transformer.ExportResult) {
                    if (!terminal.compareAndSet(false, true)) return
                    progressRunnable?.let(mainHandler::removeCallbacks)
                    ioScope.launch {
                        try {
                            withContext(Dispatchers.Main.immediate) {
                                if (continuation.isActive) onSavingToGallery()
                            }
                            val file = checkNotNull(outputFile) { "The converted video file is missing." }
                            val uri = saveToGallery(file, displayName)
                            outputUri = uri
                            file.delete()
                            withContext(Dispatchers.Main.immediate) {
                                if (continuation.isActive) {
                                    onProgress(100)
                                    continuation.resume(uri)
                                }
                            }
                            ioScope.cancel()
                        } catch (error: Exception) {
                            cleanupAfterFailure()
                            withContext(Dispatchers.Main.immediate) {
                                if (continuation.isActive) continuation.resumeWithException(error)
                            }
                            ioScope.cancel()
                        }
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: androidx.media3.transformer.ExportResult,
                    exportException: androidx.media3.transformer.ExportException
                ) {
                    if (!terminal.compareAndSet(false, true)) return
                    progressRunnable?.let(mainHandler::removeCallbacks)
                    outputFile?.delete()
                    cleanupOutput()
                    if (continuation.isActive) {
                        continuation.resumeWithException(IllegalStateException(friendlyError(exportException), exportException))
                    }
                }
            }

            try {
                require(request.width > 0 && request.height > 0) { "Could not determine the video's dimensions." }
                val outputWidth = if (request.height > request.width) PORTRAIT_WIDTH else LANDSCAPE_WIDTH
                val outputHeight = if (request.height > request.width) PORTRAIT_HEIGHT else LANDSCAPE_HEIGHT
                requestedOutputHeight = outputHeight
                val editedItemBuilder = EditedMediaItem.Builder(MediaItem.fromUri(request.source))
                if (request.width != outputWidth || request.height != outputHeight) {
                    editedItemBuilder.setEffects(
                        Effects(
                            emptyList(),
                            listOf(
                                Presentation.createForWidthAndHeight(
                                    outputWidth,
                                    outputHeight,
                                    Presentation.LAYOUT_SCALE_TO_FIT
                                )
                            )
                        )
                    )
                }
                val editedMediaItem = editedItemBuilder.build()
                val sequence = EditedMediaItemSequence.Builder(
                    setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO)
                ).addItem(editedMediaItem).build()
                val composition = Composition.Builder(sequence).build()
                val progressHolder = ProgressHolder()
                val outputDirectory = appContext.externalCacheDir ?: appContext.cacheDir
                outputFile = File(outputDirectory, "sayem-convert-${UUID.randomUUID()}.mp4")
                transformer = Transformer.Builder(appContext)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setAssetLoaderFactory(
                        DefaultAssetLoaderFactory(
                            appContext,
                            DefaultDecoderFactory.Builder(appContext)
                                .setEnableDecoderFallback(true)
                                .build(),
                            Clock.DEFAULT,
                            null
                        )
                    )
                    .setEncoderFactory(
                        DefaultEncoderFactory.Builder(appContext)
                            .setVideoEncoderSelector(allH264Encoders())
                            .build()
                    )
                    .addListener(listener)
                    .build()

                val currentTransformer = transformer!!
                progressRunnable = object : Runnable {
                    override fun run() {
                        if (terminal.get() || !continuation.isActive) return
                        val state = currentTransformer.getProgress(progressHolder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(progressHolder.progress.coerceIn(0, 99))
                        }
                        mainHandler.postDelayed(this, PROGRESS_INTERVAL_MS)
                    }
                }

                currentTransformer.start(composition, outputFile!!.absolutePath)
                mainHandler.post(progressRunnable!!)
            } catch (error: Throwable) {
                if (terminal.compareAndSet(false, true)) {
                    cleanupOutput()
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        }

    private fun createOutputValues(displayName: String): ContentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.TITLE, displayName.removeSuffix(".mp4"))
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Sayem Video Converter")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        } else {
            val directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "Sayem Video Converter"
            )
            if (!directory.exists() && !directory.mkdirs()) {
                throw IllegalStateException("Could not create the Gallery output folder.")
            }
            put(MediaStore.MediaColumns.DATA, File(directory, displayName).absolutePath)
        }
    }

    private suspend fun saveToGallery(file: File, displayName: String): Uri {
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, createOutputValues(displayName))
            ?: throw IllegalStateException("Could not create a Gallery video entry.")
        try {
            val output = contentResolver.openOutputStream(uri, "w")
                ?: throw IllegalStateException("Could not open the Gallery video for writing.")
            val coroutineContext = currentCoroutineContext()
            file.inputStream().buffered().use { input ->
                output.buffered().use { destination ->
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        destination.write(buffer, 0, count)
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null
                )
            }
            return uri
        } catch (error: Exception) {
            contentResolver.delete(uri, null, null)
            throw error
        }
    }

    /** Keep hardware codecs first, but include platform software encoders as a fallback. */
    private fun allH264Encoders(): EncoderSelector = EncoderSelector { mimeType ->
        val candidates = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.mapNotNull { codec ->
            if (!codec.isEncoder) return@mapNotNull null
            if (codec.supportedTypes.none { it.equals(mimeType, ignoreCase = true) }) return@mapNotNull null
            val softwareOnly = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                codec.isSoftwareOnly
            } else {
                codec.name.startsWith("OMX.google.", ignoreCase = true) ||
                    codec.name.startsWith("c2.android.", ignoreCase = true)
            }
            softwareOnly to codec
        }
        val orderedEncoders: List<MediaCodecInfo> = candidates
            .sortedBy { it.first }
            .map { it.second }
        ImmutableList.copyOf<MediaCodecInfo>(orderedEncoders)
    }

    private fun friendlyError(error: Throwable): String {
        val detail = generateSequence(error) { it.cause }
            .mapNotNull { it.localizedMessage }
            .joinToString(" | ")
        return when {
            detail.contains("VideoDecoder", ignoreCase = true) ||
                detail.contains("video/hevc", ignoreCase = true) ||
                detail.contains("decoder", ignoreCase = true) ->
                "This phone could not decode the source HEVC/H.265 video. Media3 tried the available decoder fallbacks, but none could handle this file."
            detail.contains("encoder", ignoreCase = true) ->
                "Android's H.264 encoder failed: ${detail.take(MAX_ERROR_DETAIL_LENGTH)}"
            detail.contains("codec", ignoreCase = true) ->
                "Android's media codec failed: ${detail.take(MAX_ERROR_DETAIL_LENGTH)}"
            detail.contains("space", ignoreCase = true) || detail.contains("storage", ignoreCase = true) ->
                "There is not enough storage space to save the converted video."
            else -> "Conversion failed. The video format may not be supported by this device."
        }
    }

    private companion object {
        const val LANDSCAPE_WIDTH = 1920
        const val LANDSCAPE_HEIGHT = 1080
        const val PORTRAIT_WIDTH = 1080
        const val PORTRAIT_HEIGHT = 1920
        const val PROGRESS_INTERVAL_MS = 400L
        const val COPY_BUFFER_SIZE = 128 * 1024
        const val MAX_ERROR_DETAIL_LENGTH = 180
    }
}
