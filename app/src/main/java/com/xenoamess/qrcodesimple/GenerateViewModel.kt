package com.xenoamess.qrcodesimple

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

class GenerateViewModel : ViewModel() {
    private val _previewState = MutableStateFlow<GeneratePreviewState>(GeneratePreviewState.Empty)
    val previewState: StateFlow<GeneratePreviewState> = _previewState.asStateFlow()
    private var previewJob: Job? = null
    private var requestId = 0L

    fun preview(request: GenerateRequest) {
        previewJob?.cancel()
        val id = ++requestId
        _previewState.value = GeneratePreviewState.Loading(request)
        previewJob = viewModelScope.launch {
            delay(PREVIEW_DEBOUNCE_MS)
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    AdvancedBarcodeGenerator.generateStyled(
                        request.content, request.format, PREVIEW_SIZE, PREVIEW_SIZE,
                        AdvancedBarcodeGenerator.sanitize(request.style, request.format)
                    )
                }
            }
            // Encoders are not cooperative; dispose a result that completed after cancellation.
            if (id != requestId) {
                result.getOrNull()?.recycle()
                return@launch
            }
            val bitmap = result.getOrNull()
            _previewState.value = when {
                bitmap != null -> GeneratePreviewState.Ready(request, bitmap)
                result.isFailure -> GeneratePreviewState.Failed(request, result.exceptionOrNull()?.message)
                else -> GeneratePreviewState.Failed(request, null)
            }
        }
    }

    fun invalidate() {
        previewJob?.cancel()
        requestId++
        _previewState.value = GeneratePreviewState.Empty
    }

    override fun onCleared() {
        previewJob?.cancel()
        (_previewState.value as? GeneratePreviewState.Ready)?.bitmap
            ?.takeUnless { it.isRecycled }
            ?.recycle()
    }

    private companion object {
        const val PREVIEW_DEBOUNCE_MS = 180L
        const val PREVIEW_SIZE = 512
    }
}
