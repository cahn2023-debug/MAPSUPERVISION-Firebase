package com.mapsupervision.core.logging

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import timber.log.Timber

object AppLogger {
    private const val maxFileSizeBytes = 5 * 1024 * 1024L
    private const val maxFiles = 3

    @Volatile
    private var debugEnabled: Boolean = true
    @Volatile
    private var logDir: File? = null

    fun init(context: Context, debug: Boolean) {
        debugEnabled = debug
        logDir = File(context.filesDir, "logs").apply { mkdirs() }
    }

    fun d(message: String) {
        if (debugEnabled) {
            Timber.d(message)
        }
        append("DEBUG", message, null)
    }

    fun e(throwable: Throwable, message: String) {
        Timber.e(throwable, message)
        append("ERROR", message, throwable)
    }

    @Synchronized
    private fun append(level: String, message: String, throwable: Throwable?) {
        val directory = logDir ?: return
        val logFile = ensureWritableLogFile(directory)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val safeMessage = message.replace('\n', ' ')
        val line = buildString {
            append(timestamp)
            append(" level=")
            append(level)
            append(" message=\"")
            append(safeMessage.replace("\"", "'"))
            append('"')
            if (throwable != null) {
                append(" error=\"")
                append((throwable.message ?: throwable.javaClass.simpleName).replace("\"", "'"))
                append('"')
            }
            append('\n')
        }
        runCatching {
            logFile.appendText(line)
        }
    }

    private fun ensureWritableLogFile(directory: File): File {
        for (index in maxFiles downTo 2) {
            val file = File(directory, "app.$index.log")
            val prev = File(directory, "app.${index - 1}.log")
            if (prev.exists() && prev.length() > maxFileSizeBytes) {
                if (file.exists()) {
                    file.delete()
                }
                prev.renameTo(file)
            }
        }
        return File(directory, "app.1.log")
    }
}
