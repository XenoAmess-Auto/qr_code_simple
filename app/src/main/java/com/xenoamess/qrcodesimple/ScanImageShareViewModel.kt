package com.xenoamess.qrcodesimple

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

internal class ScanImageShareViewModel(application: Application) : AndroidViewModel(application) {

    private val mutableResult = MutableLiveData<ScanImageProcessor.SharedMediaResult>()
    val result: LiveData<ScanImageProcessor.SharedMediaResult> = mutableResult

    private var started = false
    private var consumed = false
    private var operation: ScanImageProcessor.SharedMediaOperation? = null

    @Synchronized
    fun start(uri: Uri, mimeType: String?): Boolean {
        if (started) return false
        started = true
        operation = ScanImageProcessor.prepareSharedMedia(getApplication(), uri, mimeType) {
            mutableResult.value = it
        }
        return true
    }

    @Synchronized
    fun consume(value: ScanImageProcessor.SharedMediaResult): Boolean {
        if (consumed || mutableResult.value != value) return false
        consumed = true
        return true
    }

    override fun onCleared() {
        if (!consumed) {
            operation?.cancel()
            (mutableResult.value as? ScanImageProcessor.SharedMediaResult.Ready)?.let {
                ScanImageProcessor.deleteOwnedSharedMedia(
                    getApplication(),
                    it.uri,
                    it.ownsTempFile,
                    it.leaseToken
                )
            }
        }
    }
}
