package com.xenoamess.qrcodesimple

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 色谱式颜色选取对话框。通过 [onColorSelected] 回调返回用户最终选定的颜色。
 */
class ColorPickerDialog : DialogFragment() {

    private var initialColor: Int = Color.BLACK
    var onColorSelected: ((Int) -> Unit)? = null

    fun setInitialColor(color: Int): ColorPickerDialog {
        initialColor = color
        return this
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = requireActivity().layoutInflater
            .inflate(R.layout.dialog_color_picker, null)

        val picker = view.findViewById<ColorPickerView>(R.id.colorPicker)
        val preview = view.findViewById<android.view.View>(R.id.viewColorPreview)
        val hexInput = view.findViewById<android.widget.EditText>(R.id.etHexInput)

        picker.setColor(initialColor)
        preview.background = ColorDrawable(initialColor)
        hexInput.setText(colorToHex(initialColor))

        picker.onColorChanged = { color ->
            preview.background = ColorDrawable(color)
            hexInput.removeTextChangedListener(hexWatcher)
            hexInput.setText(colorToHex(color))
            hexInput.setSelection(hexInput.text?.length ?: 0)
            hexInput.addTextChangedListener(hexWatcher)
        }

        hexInput.addTextChangedListener(hexWatcher.apply {
            attach(picker, preview, hexInput)
        })

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.custom_color)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onColorSelected?.invoke(picker.currentColor)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    private val hexWatcher = object : TextWatcher {
        private var picker: ColorPickerView? = null
        private var preview: android.view.View? = null
        private var input: android.widget.EditText? = null

        fun attach(p: ColorPickerView, v: android.view.View, e: android.widget.EditText) {
            picker = p
            preview = v
            input = e
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            val text = s?.toString()?.trim() ?: return
            val parsed = try {
                if (text.length in 4..9) Color.parseColor(text) else return
            } catch (e: IllegalArgumentException) {
                return
            }
            val p = picker ?: return
            val v = preview ?: return
            // 避免递归：仅在颜色确实变化时同步
            if (p.currentColor != parsed) {
                p.setColor(parsed)
                v.background = ColorDrawable(parsed)
            }
        }
    }

    private fun colorToHex(color: Int): String {
        val argb = String.format("#%08X", color)
        // 不透明时返回 #RRGGBB，否则返回 8 位
        return if (argb.startsWith("#FF")) "#" + argb.substring(3) else argb
    }
}
