package com.xenoamess.qrcodesimple

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A complete, immutable generation input. Never reuse a preview for a different request. */
data class GenerateRequest(
    val content: String,
    val format: BarcodeFormat,
    val style: AdvancedBarcodeGenerator.StyleConfig
)

sealed interface GeneratePreviewState {
    data object Empty : GeneratePreviewState
    data class Loading(val request: GenerateRequest) : GeneratePreviewState
    data class Ready(val request: GenerateRequest, val bitmap: Bitmap) : GeneratePreviewState
    data class Invalid(val request: GenerateRequest, val message: String) : GeneratePreviewState
    data class Failed(val request: GenerateRequest, val message: String?) : GeneratePreviewState
}

sealed interface GenerateExportState {
    data object Idle : GenerateExportState
    data class Running(val id: Long) : GenerateExportState
    data class Completed(val id: Long) : GenerateExportState
    data class Failed(val id: Long, val message: String?) : GenerateExportState
}

internal enum class PendingImageType {
    FOREGROUND,
    BACKGROUND
}

internal enum class RasterSaveFormat {
    PNG,
    JPEG,
    WEBP
}

internal data class PendingRasterSave(
    val size: Int,
    val format: RasterSaveFormat
)

class GenerateViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _previewState = MutableStateFlow<GeneratePreviewState>(GeneratePreviewState.Empty)
    val previewState: StateFlow<GeneratePreviewState> = _previewState.asStateFlow()
    private val _exportState = MutableStateFlow<GenerateExportState>(GenerateExportState.Idle)
    val exportState: StateFlow<GenerateExportState> = _exportState.asStateFlow()
    private var previewJob: Job? = null
    private var requestId = 0L
    private var exportId = 0L
    private var previewBitmap: Bitmap? = null

    fun latestRequest(): GenerateRequest? = when (val state = _previewState.value) {
        GeneratePreviewState.Empty -> null
        is GeneratePreviewState.Loading -> state.request
        is GeneratePreviewState.Ready -> state.request
        is GeneratePreviewState.Invalid -> state.request
        is GeneratePreviewState.Failed -> state.request
    }

    internal fun beginImageCrop(type: PendingImageType) {
        savedStateHandle[PENDING_IMAGE_TYPE] = type.name
    }

    internal fun consumePendingImageType(): PendingImageType? {
        val value = savedStateHandle.get<String>(PENDING_IMAGE_TYPE)
        savedStateHandle[PENDING_IMAGE_TYPE] = null
        return value?.let { name -> PendingImageType.entries.firstOrNull { it.name == name } }
    }

    fun clearPendingImageType() {
        savedStateHandle[PENDING_IMAGE_TYPE] = null
    }

    fun setPendingSvgPath(path: String) {
        savedStateHandle[PENDING_SVG_PATH] = path
    }

    fun consumePendingSvgPath(): String? {
        val path = savedStateHandle.get<String>(PENDING_SVG_PATH)
        savedStateHandle[PENDING_SVG_PATH] = null
        return path
    }

    internal fun setPendingRasterSave(size: Int, format: RasterSaveFormat) {
        savedStateHandle[PENDING_RASTER_SIZE] = size
        savedStateHandle[PENDING_RASTER_FORMAT] = format.name
    }

    internal fun consumePendingRasterSave(): PendingRasterSave? {
        val size = savedStateHandle.get<Int>(PENDING_RASTER_SIZE)
        val formatName = savedStateHandle.get<String>(PENDING_RASTER_FORMAT)
        savedStateHandle[PENDING_RASTER_SIZE] = null
        savedStateHandle[PENDING_RASTER_FORMAT] = null
        val format = formatName?.let { name -> RasterSaveFormat.entries.firstOrNull { it.name == name } }
        return if (size != null && format != null) PendingRasterSave(size, format) else null
    }

    fun preview(request: GenerateRequest) {
        previewJob?.cancel()
        val id = ++requestId
        clearPreviewBitmap()
        _previewState.value = GeneratePreviewState.Loading(request)
        previewJob = viewModelScope.launch {
            delay(PREVIEW_DEBOUNCE_MS)
            var generated: Bitmap? = null
            var failure: Throwable? = null
            try {
                withContext(Dispatchers.Default) {
                    try {
                        generated = AdvancedBarcodeGenerator.generateStyled(
                            request.content, request.format, PREVIEW_SIZE, PREVIEW_SIZE,
                            AdvancedBarcodeGenerator.sanitize(request.style, request.format)
                        )
                    } catch (throwable: Throwable) {
                        failure = throwable
                    }
                }
                ensureActive()
                if (id != requestId) return@launch
                val bitmap = generated
                _previewState.value = when {
                    bitmap != null -> {
                        previewBitmap = bitmap
                        generated = null
                        GeneratePreviewState.Ready(request, bitmap)
                    }
                    failure != null -> GeneratePreviewState.Failed(request, failure?.message)
                    else -> GeneratePreviewState.Failed(request, null)
                }
            } finally {
                generated?.takeUnless { it.isRecycled }?.recycle()
            }
        }
    }

    fun invalidate() {
        previewJob?.cancel()
        requestId++
        clearPreviewBitmap()
        _previewState.value = GeneratePreviewState.Empty
    }

    fun beginExport(): Long? {
        if (_exportState.value is GenerateExportState.Running) return null
        val id = ++exportId
        _exportState.value = GenerateExportState.Running(id)
        return id
    }

    fun completeExport(id: Long) {
        if ((_exportState.value as? GenerateExportState.Running)?.id == id) {
            _exportState.value = GenerateExportState.Completed(id)
        }
    }

    fun failExport(id: Long, message: String?) {
        if ((_exportState.value as? GenerateExportState.Running)?.id == id) {
            _exportState.value = GenerateExportState.Failed(id, message)
        }
    }

    fun cancelExport(id: Long) {
        if ((_exportState.value as? GenerateExportState.Running)?.id == id) {
            _exportState.value = GenerateExportState.Idle
        }
    }

    private fun clearPreviewBitmap() {
        previewBitmap?.takeUnless { it.isRecycled }?.recycle()
        previewBitmap = null
    }

    override fun onCleared() {
        previewJob?.cancel()
        clearPreviewBitmap()
    }

    private companion object {
        const val PREVIEW_DEBOUNCE_MS = 180L
        const val PREVIEW_SIZE = 512
        const val PENDING_IMAGE_TYPE = "pending_image_type"
        const val PENDING_SVG_PATH = "pending_svg_path"
        const val PENDING_RASTER_SIZE = "pending_raster_size"
        const val PENDING_RASTER_FORMAT = "pending_raster_format"
    }
}
