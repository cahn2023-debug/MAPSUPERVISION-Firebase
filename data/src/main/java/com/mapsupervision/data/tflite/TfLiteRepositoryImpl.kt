package com.mapsupervision.data.tflite

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

import com.mapsupervision.ai.core.repository.TfLiteRepository

/**
 * TensorFlow Lite Repository Implementation
 * Handles custom vision models for photo quality check and discrepancy detection
 */
@Singleton
class TfLiteRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : TfLiteRepository {
    private var photoQualityInterpreter: Interpreter? = null
    private var discrepancyInterpreter: Interpreter? = null
    private var imageProcessor: ImageProcessor? = null
    
    // Model input/output dimensions
    private val PHOTO_QUALITY_INPUT_SIZE = 224
    private val PHOTO_QUALITY_OUTPUT_CLASSES = 3 // Blurry, No_Subject, Good_Quality
    
    private val DISCREPANCY_INPUT_SIZE = 10 // Number of features
    private val DISCREPANCY_OUTPUT_CLASSES = 2 // Normal, Abnormal
    
    /**
     * Load photo quality model from assets
     */
    fun loadPhotoQualityModel(modelPath: String = "photo_quality_model.tflite") {
        try {
            val modelBuffer = loadModelFile(modelPath)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            photoQualityInterpreter = Interpreter(modelBuffer, options)
            
            // Setup image processor
            imageProcessor = ImageProcessor.Builder()
                .add(ResizeWithCropOrPadOp(PHOTO_QUALITY_INPUT_SIZE, PHOTO_QUALITY_INPUT_SIZE))
                .add(ResizeOp(PHOTO_QUALITY_INPUT_SIZE, PHOTO_QUALITY_INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                .add(NormalizeOp(0f, 255f))
                .build()
        } catch (e: IOException) {
            throw IllegalStateException("Failed to load photo quality model", e)
        }
    }
    
    /**
     * Load discrepancy detection model from assets
     */
    fun loadDiscrepancyModel(modelPath: String = "discrepancy_model.tflite") {
        try {
            val modelBuffer = loadModelFile(modelPath)
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            discrepancyInterpreter = Interpreter(modelBuffer, options)
        } catch (e: IOException) {
            throw IllegalStateException("Failed to load discrepancy model", e)
        }
    }
    
    /**
     * Check photo quality using TFLite model
     * @param bitmap Photo bitmap to analyze
     * @return Photo quality result with score and classification
     */
    fun checkPhotoQuality(bitmap: Bitmap): PhotoQualityResult {
        val interpreter = photoQualityInterpreter 
            ?: throw IllegalStateException("Photo quality model not loaded")
        
        val processor = imageProcessor 
            ?: throw IllegalStateException("Image processor not initialized")
        
        try {
            // Process image
            val tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(bitmap)
            val processedImage = processor.process(tensorImage)
            
            // Prepare output buffer
            val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, PHOTO_QUALITY_OUTPUT_CLASSES), DataType.FLOAT32)
            
            // Run inference
            interpreter.run(processedImage.buffer, outputBuffer.buffer)
            
            // Get results
            val outputArray = outputBuffer.floatArray
            val classIndex = outputArray.indices.maxByOrNull { outputArray[it] } ?: 0
            val confidence = outputArray[classIndex]
            
            val classification = when (classIndex) {
                0 -> QualityClass.BLURRY
                1 -> QualityClass.NO_SUBJECT
                2 -> QualityClass.GOOD_QUALITY
                else -> QualityClass.GOOD_QUALITY
            }
            
            val score = (confidence * 100).toInt()
            val issues = mutableListOf<String>()
            val shouldRetake = classification != QualityClass.GOOD_QUALITY
            
            when (classification) {
                QualityClass.BLURRY -> issues.add("Ảnh bị mờ, cần chụp lại")
                QualityClass.NO_SUBJECT -> issues.add("Không thấy đối tượng thi công trong ảnh")
                QualityClass.GOOD_QUALITY -> { /* No issues */ }
            }
            
            val recommendation = if (shouldRetake) {
                "Nên chụp lại ảnh để đảm bảo chất lượng"
            } else {
                "Ảnh chất lượng tốt, có thể sử dụng"
            }
            
            return PhotoQualityResult(
                score = score,
                issues = issues,
                recommendation = recommendation,
                shouldRetake = shouldRetake,
                classification = classification,
                confidence = confidence
            )
        } catch (e: Exception) {
            throw IllegalStateException("Failed to run photo quality inference", e)
        }
    }
    
    /**
     * Check for data discrepancies using TFLite model
     * @param features Array of features (distance, contractor match, etc.)
     * @return Discrepancy detection result
     */
    private fun checkDiscrepancyInternal(features: FloatArray): DiscrepancyResult {
        val interpreter = discrepancyInterpreter 
            ?: throw IllegalStateException("Discrepancy model not loaded")
        
        try {
            // Prepare input buffer
            val inputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, DISCREPANCY_INPUT_SIZE), DataType.FLOAT32)
            inputBuffer.loadArray(features)
            
            // Prepare output buffer
            val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, DISCREPANCY_OUTPUT_CLASSES), DataType.FLOAT32)
            
            // Run inference
            interpreter.run(inputBuffer.buffer, outputBuffer.buffer)
            
            // Get results
            val outputArray = outputBuffer.floatArray
            val abnormalProbability = outputArray[1]
            val isAbnormal = abnormalProbability > 0.5f
            
            val issues = if (isAbnormal) {
                listOf("Phát hiện dữ liệu bất thường cần kiểm tra")
            } else {
                emptyList()
            }
            
            val actions = if (isAbnormal) {
                listOf("Rà soát thủ công dữ liệu nhập vào", "Kiểm tra lại nguồn dữ liệu")
            } else {
                listOf("Dữ liệu bình thường, có thể tiếp tục")
            }
            
            return DiscrepancyResult(
                issues = issues,
                recommendedActions = actions,
                isAbnormal = isAbnormal,
                confidence = abnormalProbability
            )
        } catch (e: Exception) {
            throw IllegalStateException("Failed to run discrepancy inference", e)
        }
    }
    
    /**
     * Load model file from assets
     */
    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    
    override fun checkPhotoQuality(filePath: String): com.mapsupervision.ai.core.PhotoQualityResult {
        val bitmap = android.graphics.BitmapFactory.decodeFile(filePath)
            ?: throw IllegalArgumentException("Failed to decode image from path: $filePath")
        val localResult = checkPhotoQuality(bitmap)
        return com.mapsupervision.ai.core.PhotoQualityResult(
            score = localResult.score,
            issues = localResult.issues,
            recommendation = localResult.recommendation,
            shouldRetake = localResult.shouldRetake
        )
    }

    override fun checkDiscrepancy(features: FloatArray): com.mapsupervision.ai.core.DiscrepancyResult {
        val localResult = checkDiscrepancyInternal(features)
        return com.mapsupervision.ai.core.DiscrepancyResult(
            issues = localResult.issues,
            recommendedActions = localResult.recommendedActions
        )
    }

    /**
     * Close interpreters to free resources
     */
    fun close() {
        photoQualityInterpreter?.close()
        discrepancyInterpreter?.close()
        photoQualityInterpreter = null
        discrepancyInterpreter = null
    }
    
    /**
     * Check if models are loaded
     */
    override fun isPhotoQualityModelLoaded(): Boolean = photoQualityInterpreter != null
    override fun isDiscrepancyModelLoaded(): Boolean = discrepancyInterpreter != null
}

/**
 * Photo Quality Result
 */
data class PhotoQualityResult(
    val score: Int,
    val issues: List<String>,
    val recommendation: String,
    val shouldRetake: Boolean,
    val classification: QualityClass,
    val confidence: Float
)

/**
 * Discrepancy Result
 */
data class DiscrepancyResult(
    val issues: List<String>,
    val recommendedActions: List<String>,
    val isAbnormal: Boolean,
    val confidence: Float
)

/**
 * Quality Classification
 */
enum class QualityClass {
    BLURRY,
    NO_SUBJECT,
    GOOD_QUALITY
}
