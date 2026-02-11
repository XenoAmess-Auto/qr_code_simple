package com.xenoamess.qrcodesimple

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer

/**
 * 离线 QR 码增强模型
 * 使用 TensorFlow Lite 模型提升低质量图片的识别率
 */
object QREnhancementModel {

    private const val TAG = "QREnhancement"
    private const val MODEL_FILE = "qr_enhance_model.tflite"
    private const val INPUT_SIZE = 256
    private const val NUM_CHANNELS = 1
    private const val FLOAT_TYPE_SIZE = 4

    private var interpreter: Interpreter? = null
    private var isModelLoaded = false

    /**
     * 初始化模型
     */
    fun initialize(context: Context): Boolean {
        return try {
            val model = FileUtil.loadMappedFile(context, MODEL_FILE)
            interpreter = createInterpreter(model)
            isModelLoaded = true
            Log.i(TAG, "Model loaded successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
            isModelLoaded = false
            false
        }
    }

    /**
     * 检查模型是否已加载
     */
    fun isLoaded(): Boolean = isModelLoaded

    /**
     * 增强图片
     * @param inputBitmap 输入的低质量图片
     * @return 增强后的图片
     */
    suspend fun enhance(inputBitmap: Bitmap): Bitmap? = withContext(Dispatchers.Default) {
        if (!isModelLoaded) {
            Log.w(TAG, "Model not loaded")
            return@withContext null
        }

        try {
            // 预处理
            val inputBuffer = preprocess(inputBitmap)

            // 创建输出缓冲区
            val outputBuffer = ByteBuffer.allocateDirect(
                1 * INPUT_SIZE * INPUT_SIZE * NUM_CHANNELS * FLOAT_TYPE_SIZE
            ).apply {
                order(ByteOrder.nativeOrder())
            }

            // 运行推理
            interpreter?.run(inputBuffer, outputBuffer)

            // 后处理
            postprocess(outputBuffer, inputBitmap.width, inputBitmap.height)
        } catch (e: Exception) {
            Log.e(TAG, "Enhancement failed", e)
            null
        }
    }

    /**
     * 预处理图片
     */
    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        // 缩放至模型输入尺寸
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

        val inputBuffer = ByteBuffer.allocateDirect(
            1 * INPUT_SIZE * INPUT_SIZE * NUM_CHANNELS * FLOAT_TYPE_SIZE
        ).apply {
            order(ByteOrder.nativeOrder())
        }

        // 转换为灰度并归一化
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaledBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            val gray = ((pixel shr 16 and 0xFF) * 0.299f +
                    (pixel shr 8 and 0xFF) * 0.587f +
                    (pixel and 0xFF) * 0.114f) / 255f
            inputBuffer.putFloat(gray)
        }

        scaledBitmap.recycle()
        inputBuffer.rewind()
        return inputBuffer
    }

    /**
     * 后处理输出
     */
    private fun postprocess(outputBuffer: ByteBuffer, originalWidth: Int, originalHeight: Int): Bitmap {
        outputBuffer.rewind()

        val outputPixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        for (i in 0 until INPUT_SIZE * INPUT_SIZE) {
            val gray = (outputBuffer.float * 255).toInt().coerceIn(0, 255)
            outputPixels[i] = 0xFF000000.toInt() or (gray shl 16) or (gray shl 8) or gray
        }

        val outputBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        outputBitmap.setPixels(outputPixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        // 缩放回原尺寸
        return Bitmap.createScaledBitmap(outputBitmap, originalWidth, originalHeight, true)
    }

    /**
     * 创建解释器
     */
    private fun createInterpreter(model: MappedByteBuffer): Interpreter {
        val options = Interpreter.Options().apply {
            setNumThreads(4)
            useXNNPACK = true
        }
        return Interpreter(model, options)
    }

    /**
     * 释放资源
     */
    fun close() {
        interpreter?.close()
        interpreter = null
        isModelLoaded = false
        Log.i(TAG, "Model closed")
    }
}

/**
 * 模型管理器
 * 处理模型的下载、更新等
 */
object ModelManager {

    private const val PREFS_NAME = "model_manager"
    private const val KEY_MODEL_VERSION = "model_version"
    private const val CURRENT_MODEL_VERSION = "1.0.0"

    /**
     * 检查是否需要更新模型
     */
    fun shouldUpdateModel(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedVersion = prefs.getString(KEY_MODEL_VERSION, "")
        return savedVersion != CURRENT_MODEL_VERSION
    }

    /**
     * 保存模型版本
     */
    fun saveModelVersion(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODEL_VERSION, CURRENT_MODEL_VERSION)
            .apply()
    }

    /**
     * 获取模型文件路径
     */
    fun getModelPath(context: Context): String {
        return "${context.filesDir}/$CURRENT_MODEL_VERSION"
    }
}
