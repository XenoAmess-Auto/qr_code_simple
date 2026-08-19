package com.xenoamess.qrcodesimple

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import androidx.fragment.app.DialogFragment

/**
 * 全屏颜色选取对话框。
 * ColorPickerView / Hex 输入 / RGBA 输入 三方双向联动。
 * 通过 Fragment Result 或 [onColorSelected] 回调返回用户最终选定的颜色（含 alpha）。
 */
class ColorPickerDialog : DialogFragment() {

    var onColorSelected: ((Int) -> Unit)? = null
    private var initialColor: Int = Color.BLACK
    private var currentColor: Int = Color.BLACK

    /** true 时忽略编辑框变化，防止循环同步 */
    private var updatingFromCode = false

    fun setInitialColor(color: Int): ColorPickerDialog {
        initialColor = color
        arguments = (arguments ?: Bundle()).apply { putInt(ARG_INITIAL_COLOR, color) }
        return this
    }

    fun setResultTarget(requestKey: String, requestId: Int = 0): ColorPickerDialog {
        arguments = (arguments ?: Bundle()).apply {
            putString(ARG_RESULT_KEY, requestKey)
            putInt(ARG_REQUEST_ID, requestId)
        }
        return this
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
        currentColor = if (savedInstanceState?.containsKey(STATE_CURRENT_COLOR) == true) {
            savedInstanceState.getInt(STATE_CURRENT_COLOR)
        } else {
            arguments?.getInt(ARG_INITIAL_COLOR, initialColor) ?: initialColor
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.dialog_color_picker, container, false)

        val picker = view.findViewById<ColorPickerView>(R.id.colorPicker)
        val preview = view.findViewById<View>(R.id.viewColorPreview)
        val hexInput = view.findViewById<EditText>(R.id.etHexInput)
        val etR = view.findViewById<EditText>(R.id.etR)
        val etG = view.findViewById<EditText>(R.id.etG)
        val etB = view.findViewById<EditText>(R.id.etB)
        val etA = view.findViewById<EditText>(R.id.etA)

        picker.setColor(currentColor)
        applyColorToPreview(preview, currentColor)
        updateHexField(hexInput, currentColor)
        updateRgbaFields(etR, etG, etB, etA, currentColor)

        // --- ColorPickerView → Hex / RGBA ---
        picker.onColorChanged = { color ->
            currentColor = color
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
        view.findViewById<View>(R.id.btnClose).setOnClickListener { dismiss() }
        view.findViewById<View>(R.id.btnConfirm).setOnClickListener {
            val color = picker.currentColor
            arguments?.getString(ARG_RESULT_KEY)?.let { requestKey ->
                parentFragmentManager.setFragmentResult(
                    requestKey,
                    Bundle().apply {
                        putInt(RESULT_COLOR, color)
                        putInt(RESULT_REQUEST_ID, arguments?.getInt(ARG_REQUEST_ID) ?: 0)
                    }
                )
            }
            onColorSelected?.invoke(color)
            dismiss()
        }

        return view
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_CURRENT_COLOR, currentColor)
        super.onSaveInstanceState(outState)
    }

    private fun syncAll(
        picker: ColorPickerView,
        preview: View,
        hexInput: EditText,
        etR: EditText,
        etG: EditText,
        etB: EditText,
        etA: EditText,
        color: Int
    ) {
        updatingFromCode = true
        currentColor = color
        picker.setColor(color)
        applyColorToPreview(preview, color)
        updateHexField(hexInput, color)
        updateRgbaFields(etR, etG, etB, etA, color)
        updatingFromCode = false
    }

    private fun applyColorToPreview(view: View, color: Int) {
        val shape = view.background as? GradientDrawable
        if (shape != null) {
            shape.setColor(color)
        } else {
            view.background = ColorDrawable(color)
        }
    }

    private fun updateHexField(field: EditText, color: Int) {
        val argb = String.format("#%08X", color)
        val hex = if (argb.startsWith("#FF")) "#" + argb.substring(3) else argb
        field.setText(hex)
        field.setSelection(field.text?.length ?: 0)
    }

    private fun updateRgbaFields(etR: EditText, etG: EditText, etB: EditText, etA: EditText, color: Int) {
        if (etR.hasFocus()) {
            // 保留用户正在输入的字段，只移动光标到末尾避免跳回开头
            etR.setSelection(etR.text?.length ?: 0)
        } else {
            etR.setText(Color.red(color).toString())
        }
        if (etG.hasFocus()) {
            etG.setSelection(etG.text?.length ?: 0)
        } else {
            etG.setText(Color.green(color).toString())
        }
        if (etB.hasFocus()) {
            etB.setSelection(etB.text?.length ?: 0)
        } else {
            etB.setText(Color.blue(color).toString())
        }
        if (etA.hasFocus()) {
            etA.setSelection(etA.text?.length ?: 0)
        } else {
            etA.setText(Color.alpha(color).toString())
        }
    }

    companion object {
        const val RESULT_COLOR = "color"
        const val RESULT_REQUEST_ID = "request_id"
        private const val ARG_INITIAL_COLOR = "initial_color"
        private const val ARG_RESULT_KEY = "result_key"
        private const val ARG_REQUEST_ID = "request_id"
        private const val STATE_CURRENT_COLOR = "current_color"
    }
}
