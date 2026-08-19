package com.xenoamess.qrcodesimple

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateViewModelTest {
    @Test
    fun `exports are serialized until the active operation finishes`() {
        val viewModel = GenerateViewModel(SavedStateHandle())
        val firstId = viewModel.beginExport()

        assertNotNull(firstId)
        assertNull(viewModel.beginExport())

        viewModel.completeExport(checkNotNull(firstId))

        assertTrue(viewModel.exportState.value is GenerateExportState.Completed)
        assertNotNull(viewModel.beginExport())
    }

    @Test
    fun `cancelled export returns to idle`() {
        val viewModel = GenerateViewModel(SavedStateHandle())
        val exportId = checkNotNull(viewModel.beginExport())

        viewModel.cancelExport(exportId)

        assertTrue(viewModel.exportState.value is GenerateExportState.Idle)
    }
}
