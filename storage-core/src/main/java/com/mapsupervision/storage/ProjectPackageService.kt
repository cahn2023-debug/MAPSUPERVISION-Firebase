package com.mapsupervision.storage

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectPackageService @Inject constructor(
    private val storageManager: ProjectStorageManager
) {
    fun exportProjectZip(projectSlug: String): File {
        val root = storageManager.projectRoot(projectSlug)
        val exportDir = File(root, "exports").apply { mkdirs() }
        val outFile = File(exportDir, "${projectSlug}_${System.currentTimeMillis()}.zip")

        ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zos ->
            root.walkTopDown()
                .filter { it.isFile && it.absolutePath != outFile.absolutePath && !it.absolutePath.contains("exports/") }
                .forEach { file ->
                    val entryName = file.relativeTo(root).invariantSeparatorsPath
                    zos.putNextEntry(ZipEntry(entryName))
                    BufferedInputStream(FileInputStream(file)).use { input ->
                        input.copyTo(zos)
                    }
                    zos.closeEntry()
                }
        }

        return outFile
    }

    fun importProjectZip(inputStream: InputStream, targetSlug: String) {
        val root = storageManager.projectRoot(targetSlug)
        root.mkdirs()
        resetProjectDatabaseFolder(root)

        ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
            var entry = zis.nextEntry
            val buffer = ByteArray(4096)
            while (entry != null) {
                val relativePath = entry.name.normalizeZipPath()
                if (shouldSkipZipEntry(relativePath)) {
                    zis.closeEntry()
                    entry = zis.nextEntry
                    continue
                }
                val file = File(root, entry.name)
                // Prevent Zip Slip vulnerability
                if (!file.canonicalPath.startsWith(root.canonicalPath + File.separator)) {
                    throw SecurityException("Zip entry is outside of target directory: ${entry.name}")
                }
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        var len = zis.read(buffer)
                        while (len > 0) {
                            fos.write(buffer, 0, len)
                            len = zis.read(buffer)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    fun copyImportedFilesToPrivateStorage(tempDir: File, targetSlug: String) {
        val publicRoot = storageManager.projectRoot(targetSlug)
        resetProjectDatabaseFolder(publicRoot)
        tempDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                val relativePath = file.relativeTo(tempDir).invariantSeparatorsPath
                if (relativePath == "project_metadata.json" || shouldSkipZipEntry(relativePath)) {
                    return@forEach
                }
                val targetFile = File(publicRoot, relativePath)
                targetFile.parentFile?.mkdirs()
                file.copyTo(targetFile, overwrite = true)
            }
        }
    }

    private fun resetProjectDatabaseFolder(root: File) {
        File(root, "db").deleteRecursively()
        File(root, "db").mkdirs()
    }

    private fun shouldSkipZipEntry(relativePath: String): Boolean {
        val normalized = relativePath.normalizeZipPath()
        return normalized == "project_metadata.json" || normalized == "db" || normalized.startsWith("db/")
    }

    private fun String.normalizeZipPath(): String = replace('\\', '/').trimStart('.').trimStart('/')
}
