package com.mapsupervision.data.mlkit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mapsupervision.domain.service.PhotoDailyLogDataResult
import com.mapsupervision.domain.service.PhotoMaterialDataResult
import com.mapsupervision.domain.service.PhotoOcrService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * ML Kit Scanner Service.
 *
 * Provides OCR and barcode scanning for photo workflows.
 */
@Singleton
class MlKitScannerService @Inject constructor(
    @param:ApplicationContext private val context: Context
) : PhotoOcrService {
    private val textRecognizer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val barcodeScanner by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        BarcodeScanning.getClient()
    }

    suspend fun extractTextFromImage(imageUri: String): OcrResult = withContext(Dispatchers.IO) {
        val bitmap = loadBitmap(imageUri) ?: return@withContext OcrResult(
            success = false,
            text = "",
            lines = emptyList(),
            error = "Failed to load bitmap"
        )

        runCatching {
            val visionText = textRecognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitResult()
            val lines = visionText.textBlocks
                .flatMap { block -> block.lines }
                .map { it.text.trim() }
                .filter { it.isNotBlank() }
            val rawText = visionText.text.trim()
            if (lines.isEmpty() && rawText.isBlank()) {
                OcrResult(
                    success = false,
                    text = "",
                    lines = emptyList(),
                    error = "No text detected"
                )
            } else {
                OcrResult(
                    success = true,
                    text = rawText,
                    lines = lines.ifEmpty { rawText.lines().map { it.trim() }.filter { it.isNotBlank() } },
                    error = null
                )
            }
        }.getOrElse { error ->
            OcrResult(
                success = false,
                text = "",
                lines = emptyList(),
                error = error.message ?: "Unknown error"
            )
        }
    }

    suspend fun scanBarcode(imageUri: String): BarcodeScanResult = withContext(Dispatchers.IO) {
        val bitmap = loadBitmap(imageUri) ?: return@withContext BarcodeScanResult(
            success = false,
            barcodes = emptyList(),
            error = "Failed to load bitmap"
        )

        runCatching {
            val barcodes = barcodeScanner.process(InputImage.fromBitmap(bitmap, 0)).awaitResult()
            BarcodeScanResult(
                success = true,
                barcodes = barcodes.map { it.toBarcodeData() },
                error = null
            )
        }.getOrElse { error ->
            BarcodeScanResult(
                success = false,
                barcodes = emptyList(),
                error = error.message ?: "Unknown error"
            )
        }
    }

    override suspend fun extractMaterialData(imageUri: String): PhotoMaterialDataResult {
        val ocrResult = extractTextFromImage(imageUri)
        if (!ocrResult.success) {
            return PhotoMaterialDataResult(
                success = false,
                materialName = null,
                quantity = null,
                unit = null,
                error = ocrResult.error
            )
        }
        return parseMaterialData(ocrResult.text, ocrResult.lines)
    }

    override suspend fun extractDailyLogData(imageUri: String): PhotoDailyLogDataResult {
        val ocrResult = extractTextFromImage(imageUri)
        if (!ocrResult.success) {
            return PhotoDailyLogDataResult(
                success = false,
                workItem = null,
                manpower = null,
                note = null,
                error = ocrResult.error
            )
        }
        return parseDailyLogData(ocrResult.text, ocrResult.lines)
    }

    private fun loadBitmap(imageUri: String, maxDimensionPx: Int = 1600): Bitmap? {
        val source = resolveSource(imageUri) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }

        openInputStream(source)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimensionPx, maxDimensionPx)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return openInputStream(source)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    }

    private fun resolveSource(imageUri: String): ImageSource? {
        val uri = runCatching { Uri.parse(imageUri) }.getOrNull()
        if (uri != null && uri.scheme.equals("content", ignoreCase = true)) {
            return ImageSource.Content(uri)
        }
        if (uri != null && uri.scheme.equals("file", ignoreCase = true)) {
            return uri.path?.let { ImageSource.File(java.io.File(it)) }
        }
        val file = java.io.File(imageUri)
        return if (file.exists()) ImageSource.File(file) else uri?.let { ImageSource.Content(it) }
    }

    private fun openInputStream(source: ImageSource): InputStream? {
        return when (source) {
            is ImageSource.Content -> context.contentResolver.openInputStream(source.uri)
            is ImageSource.File -> FileInputStream(source.file)
        }
    }

    fun close() {
        // Singleton service keeps ML Kit clients alive for app lifetime.
    }

    private sealed interface ImageSource {
        data class Content(val uri: Uri) : ImageSource
        data class File(val file: java.io.File) : ImageSource
    }
}

internal suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result ->
        if (cont.isActive) cont.resume(result)
    }
    addOnFailureListener { error ->
        if (cont.isActive) cont.resumeWithException(error)
    }
}

internal fun parseMaterialData(text: String, lines: List<String>): PhotoMaterialDataResult {
    val cleanedLines = lines.map { it.trim() }.filter { it.isNotBlank() }
    val quantityMatch = quantityRegex.find(text.replace(',', '.'))
    val quantity = quantityMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    val unit = quantityMatch?.groupValues?.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }

    val materialLine = cleanedLines.firstOrNull { line ->
        line.any { ch -> ch.isLetter() } && !normalizeForMatch(line).contains("nhan cong")
    }.orEmpty()
    val materialName = materialLine
        .replace(quantityRegex, " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .takeIf { it.isNotBlank() }

    val success = materialName != null || quantity != null || unit != null
    return PhotoMaterialDataResult(
        success = success,
        materialName = materialName,
        quantity = quantity,
        unit = unit,
        error = if (success) null else "Unable to extract material data"
    )
}

internal fun parseDailyLogData(text: String, lines: List<String>): PhotoDailyLogDataResult {
    val cleanedLines = lines.map { it.trim() }.filter { it.isNotBlank() }
    val normalizedText = normalizeForMatch(text.replace('\n', ' ').replace(Regex("\\s+"), " ").trim())

    val workItem = extractLabeledValue(cleanedLines, listOf("cong viec", "hang muc", "work item", "work"))
        ?: cleanedLines.firstOrNull { it.any { ch -> ch.isLetter() } }

    val manpower = manpowerRegex.find(normalizedText)?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: cleanedLines.firstOrNull { normalizeForMatch(it).contains("nguoi") }?.let { line ->
            manpowerRegex.find(normalizeForMatch(line))?.groupValues?.getOrNull(1)?.toIntOrNull()
        }

    val note = extractLabeledValue(cleanedLines, listOf("ghi chu", "note", "remark", "nhan xet"))
        ?: cleanedLines.drop(1).joinToString(" ").takeIf { it.isNotBlank() }

    val success = workItem != null || manpower != null || note != null
    return PhotoDailyLogDataResult(
        success = success,
        workItem = workItem,
        manpower = manpower,
        note = note,
        error = if (success) null else "Unable to extract daily log data"
    )
}

private fun extractLabeledValue(lines: List<String>, labels: List<String>): String? {
    for (line in lines) {
        val lower = normalizeForMatch(line)
        val matchedLabel = labels.firstOrNull { lower.startsWith(it) || lower.contains("$it:") }
        if (matchedLabel != null) {
            return line
                .replace(Regex("(?i)^${Regex.escape(matchedLabel)}\\s*[:\\-]?\\s*"), "")
                .trim()
                .takeIf { it.isNotBlank() }
        }
    }
    return null
}

private fun Barcode.toBarcodeData(): BarcodeData {
    return BarcodeData(
        format = formatName(format),
        rawValue = rawValue.orEmpty(),
        displayValue = displayValue.orEmpty(),
        valueType = valueType
    )
}

private fun formatName(format: Int): String {
    return when (format) {
        Barcode.FORMAT_CODE_128 -> "CODE_128"
        Barcode.FORMAT_CODE_39 -> "CODE_39"
        Barcode.FORMAT_CODE_93 -> "CODE_93"
        Barcode.FORMAT_CODABAR -> "CODABAR"
        Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
        Barcode.FORMAT_EAN_13 -> "EAN_13"
        Barcode.FORMAT_EAN_8 -> "EAN_8"
        Barcode.FORMAT_ITF -> "ITF"
        Barcode.FORMAT_QR_CODE -> "QR_CODE"
        Barcode.FORMAT_UPC_A -> "UPC_A"
        Barcode.FORMAT_UPC_E -> "UPC_E"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_AZTEC -> "AZTEC"
        else -> "UNKNOWN"
    }
}

private val quantityRegex = Regex("""(\d+(?:[.,]\d+)?)\s*([A-Za-z%]+)?""")
private val manpowerRegex = Regex("""(?i)(?:nhan cong|manpower|nguoi|person|pax)\D*(\d+)""")

private fun calculateInSampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
    var inSampleSize = 1
    if (srcHeight > reqHeight || srcWidth > reqWidth) {
        while (srcHeight / inSampleSize >= reqHeight && srcWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize.coerceAtLeast(1)
}

private fun normalizeForMatch(text: String): String {
    val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
    return normalized.replace(Regex("\\p{Mn}+"), "")
        .replace('đ', 'd')
        .replace('Đ', 'd')
        .lowercase()
}

data class OcrResult(
    val success: Boolean,
    val text: String,
    val lines: List<String>,
    val error: String?
)

data class BarcodeScanResult(
    val success: Boolean,
    val barcodes: List<BarcodeData>,
    val error: String?
)

data class BarcodeData(
    val format: String,
    val rawValue: String,
    val displayValue: String,
    val valueType: Int
)
