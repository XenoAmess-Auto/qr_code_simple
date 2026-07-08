package com.xenoamess.qrcodesimple

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.WindowManager
import androidx.fragment.app.DialogFragment

/**
 * 全屏颜色选取对话框。
 * ColorPickerView / Hex 输入 / RGBA 输入 三方双向联动。
 * 通过 [onColorSelected] 回调返回用户最终选定的颜色（含 alpha）。
 */
class ColorPickerDialog : DialogFragment() {

    private var initialColor: Int = Color.BLACK
    var onColorSelected: ((Int) -> Unit)? = null

    /** true 时忽略编辑框变化，防止循环同步 */
    private var updatingFromCode = false

    fun setInitialColor(color: Int): ColorPickerDialog {
        initialColor = color
        return this
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = requireActivity().layoutInflater
            .inflate(R.layout.dialog_color_picker, null)

        val picker = view.findViewById<ColorPickerView>(R.id.colorPicker)
        val preview = view.findViewById<android.view.View>(R.id.viewColorPreview)
        val hexInput = view.findViewById<android.widget.EditText>(R.id.etHexInput)
        val etR = view.findViewById<android.widget.EditText>(R.id.etR)
        val etG = view.findViewById<android.widget.EditText>(R.id.etG)
        val etB = view.findViewById<android.widget.EditText>(R.id.etB)
        val etA = view.findViewById<android.widget.EditText>(R.id.etA)

        picker.setColor(initialColor)
        applyColorToPreview(preview, initialColor)
        updateHexField(hexInput, initialColor)
        updateRgbaFields(etR, etG, etB, etA, initialColor)

        // --- ColorPickerView → Hex / RGBA ---
        picker.onColorChanged = { color ->
            if (!updatingFromCode) {
                applyColorToPreview(preview, color)
                updateHexField(hexInput, color)
                updateRgbaFields(etR, etG, etB, etA, color)
            }
        }

        // --- Hex → ColorPickerView / RGBA ---
        hexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updatingFromCode) return
                val text = s?.toString()?.trim() ?: return
                val parsed = try {
                    if (text.length in 4..9) Color.parseColor(text) else return
                } catch (e: IllegalArgumentException) {
                    return
                }
                if (picker.currentColor != parsed) {
                    syncAll(picker, preview, hexInput, etR, etG, etB, etA, parsed)
                }
            }
        })

        // --- RGBA → ColorPickerView / Hex ---
        val rgbaWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updatingFromCode) return
                val r = etR.text.toString().toIntOrNull()?.coerceIn(0, 255) ?: return
                val g = etG.text.toString().toIntOrNull()?.coerceIn(0, 255) ?: return
                val b = etB.text.toString().toIntOrNull()?.coerceIn(0, 255) ?: return
                val a = etA.text.toString().toIntOrNull()?.coerceIn(0, 255) ?: return
                val color = Color.argb(a, r, g, b)
                if (picker.currentColor != color) {
                    syncAll(picker, preview, hexInput, etR, etG, etB, etA, color)
                }
            }
        }
        etR.addTextChangedListener(rgbaWatcher)
        etG.addTextChangedListener(rgbaWatcher)
        etB.addTextChangedListener(rgbaWatcher)
        etA.addTextChangedListener(rgbaWatcher)

        // --- 关闭 / 确定 ---
        view.findViewById<android.view.View>(R.id.btnClose).setOnClickListener {
            dismiss()
        }
        view.findViewById<android.view.View>(R.id.btnConfirm).setOnClickListener {
            onColorSelected?.invoke(picker.currentColor)
            dismiss()
        }

        val dialog = Dialog(requireContext())
        dialog.setContentView(view)
        return dialog
    }

    private fun syncAll(
        picker: ColorPickerView,
        preview: android.view.View,
        hexInput: android.widget.EditText,
        etR: android.widget.EditText,
        etG: android.widget.EditText,
        etB: android.widget.EditText,
        etA: android.widget.EditText,
        color: Int
    ) {
        updatingFromCode = true
        picker.setColor(color)
        applyColorToPreview(preview, color)
        updateHexField(hexInput, color)
        updateRgbaFields(etR, etG, etB, etA, color)
        updatingFromCode = false
    }

    private fun applyColorToPreview(view: android.view.View, color: Int) {
        val shape = view.background as? GradientDrawable
        if (shape != null) {
            shape.setColor(color)
        } else {
            view.background = ColorDrawable(color)
        }
    }

    private fun updateHexField(field: android.widget.EditText, color: Int) {
        val argb = String.format("#%08X", color)
        val hex = if (argb.startsWith("#FF")) "#" + argb.substring(3) else argb
        field.setText(hex)
        field.setSelection(field.text?.length ?: 0)
    }

    private fun updateRgbaFields(
        etR: android.widget.EditText,
        etG: android.widget.EditText,
        etB: android.widget.EditText,
        etA: android.widget.EditText,
        color: Int
    ) {
        etR.setText(Color.red(color).toString())
        etG.setText(Color.green(color).toString())
        etB.setText(Color.blue(color).toString())
        etA.setText(Color.alpha(color).toString())
    }
}
