package com.mapsupervision.storage

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class ProjectStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val pathsPref by lazy { context.getSharedPreferences("project_custom_paths", Context.MODE_PRIVATE) }

    fun setCustomPath(projectSlug: String, path: String) {
        pathsPref.edit().putString(projectSlug, path).apply()
    }

    fun getCustomPath(projectSlug: String): String? {
        return pathsPref.getString(projectSlug, null)
    }

    fun clearCustomPath(projectSlug: String) {
        pathsPref.edit().remove(projectSlug).apply()
    }

    companion object {
        internal val mediaFolderChildren = listOf(
            "db",
            "Media/Node",
            "Media/Route"
        )
    }

    /**
     * Returns the public project root under Downloads/MapSupervision/Projects/<slug>.
     * Falls back to internal filesDir if external storage is not available.
     */
    open fun projectRoot(projectSlug: String): File {
        val root = projectRootDirectory(projectSlug)
        mediaFolderChildren.forEach { child ->
            File(root, child).mkdirs()
        }
        return root
    }

    open fun projectRootDirectory(projectSlug: String): File {
        val customPath = getCustomPath(projectSlug)
        return if (!customPath.isNullOrBlank()) {
            File(customPath)
        } else {
            val base = publicBaseDir()
            File(base, "Projects/$projectSlug")
        }
    }

    /**
     * Returns the app-private project root under context.filesDir/Projects/<slug>.
     */
    open fun privateProjectRoot(projectSlug: String): File {
        val root = privateProjectRootDirectory(projectSlug)
        mediaFolderChildren.forEach { child ->
            File(root, child).mkdirs()
        }
        return root
    }

    open fun privateProjectRootDirectory(projectSlug: String): File {
        val customPath = getCustomPath(projectSlug)
        return if (!customPath.isNullOrBlank()) {
            File(customPath)
        } else {
            val base = File(context.filesDir, "MapSupervision")
            File(base, "Projects/$projectSlug")
        }
    }

    open fun scopedProjectDbRootDirectory(projectSlug: String): File {
        val base = File(context.filesDir, "MapSupervision")
        return File(base, "ScopedProjects/$projectSlug")
    }

    open fun scopedProjectDbFile(projectSlug: String): File {
        val root = scopedProjectDbRootDirectory(projectSlug)
        File(root, "db").mkdirs()
        return File(root, "db/project.sqlite")
    }

    fun isScopedProjectDbPath(path: String): Boolean {
        if (path.isBlank()) return false
        val scopedRoot = File(context.filesDir, "MapSupervision").absolutePath
        return File(path).absolutePath.startsWith(scopedRoot)
    }

    fun projectDbFile(projectSlug: String): File = File(projectRoot(projectSlug), "db/project.sqlite")
    fun importsDir(projectSlug: String): File = File(projectRoot(projectSlug), "imports")
    fun photosDir(projectSlug: String): File = File(projectRoot(projectSlug), "photos")
    fun thumbsDir(projectSlug: String): File = File(projectRoot(projectSlug), "thumbs")
    fun reportsDir(projectSlug: String): File = File(projectRoot(projectSlug), "reports")
    fun exportsDir(projectSlug: String): File = File(projectRoot(projectSlug), "exports")

    fun sanitizeFolderName(rawName: String): String = sanitizeSegment(rawName, "Unnamed")

    fun sanitizeSegment(str: String, default: String): String {
        if (str.isBlank()) return default
        val normalized = Normalizer.normalize(str, Normalizer.Form.NFC)
        val replacedSpaces = normalized.replace(Regex("\\s+"), "-")
        val clean = replacedSpaces.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "")
        val sanitized = clean.trim('-', '_')
        val finalStr = if (sanitized.isBlank()) default else sanitized
        return if (finalStr.length > 80) finalStr.take(80).trim('-', '_') else finalStr
    }

    fun generateUniqueFile(directory: File, baseName: String, extension: String): File {
        val ext = extension.removePrefix(".")
        var file = File(directory, "$baseName.$ext")
        var counter = 1
        while (file.exists()) {
            file = File(directory, "${baseName}_$counter.$ext")
            counter++
        }
        return file
    }

    fun resolveObjectFolder(projectSlug: String, isRoute: Boolean, objectCode: String): File {
        val category = if (isRoute) "Media/Route" else "Media/Node"
        val sanitizedObject = sanitizeSegment(objectCode, "Unnamed")
        return File(File(projectRoot(projectSlug), category), sanitizedObject).apply { mkdirs() }
    }

    fun prepareImportedProjectStorage(projectSlug: String, projectId: String) {
        val customPath = getCustomPath(projectSlug)
        clearCustomPath(projectSlug)

        val legacyRoots = linkedSetOf<File>()
        customPath?.takeIf { it.isNotBlank() }?.let { legacyRoots += File(it) }
        legacyRoots += privateProjectRootDirectory(projectSlug)
        legacyRoots += privateProjectRootDirectory(projectId)
        legacyRoots += File(publicBaseDirDirectory(), "Projects/$projectId")

        legacyRoots.forEach { legacyRoot ->
            File(legacyRoot, "db").deleteRecursively()
        }
    }

    fun buildMediaFileName(capturedAt: Long, locationLabel: String?, note: String?, extension: String): String {
        val timeStr = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(capturedAt))
        val loc = sanitizeSegment(locationLabel.orEmpty(), "KhongRoDiaDiem")
        val comment = sanitizeSegment(note.orEmpty(), "KhongGhiChu")
        val ext = extension.removePrefix(".")
        return "${timeStr}_${loc}_$comment.$ext"
    }


    /**
     * Returns Downloads/MapSupervision, creating it if needed.
     * Falls back to internal filesDir/MapSupervision on failure.
     */
    fun publicBaseDir(): File {
        val dir = publicBaseDirDirectory()
        return if (dir.mkdirs() || dir.exists()) dir
        else File(context.filesDir, "MapSupervision").also { it.mkdirs() }
    }

    fun publicBaseDirDirectory(): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloads, "MapSupervision")
    }

    /**
     * Notifies the system MediaScanner so the file appears in gallery/file manager immediately.
     */
    open fun scanFile(file: File) {
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
    }
}
