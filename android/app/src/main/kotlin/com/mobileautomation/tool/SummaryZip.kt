package com.mobileautomation.tool

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Packs a finished run's evidence (text report, screen recording, failed-step
 * screenshots) into one ZIP and puts a copy in public Downloads.
 *
 * Only files inside the tool's own storage are accepted, so the zip can hold
 * nothing but run output.
 *
 * Screenshots go in as JPEGs [SHOT_WIDTH] wide under the same name with a
 * `.jpg` extension: the full-size PNGs were about 15 MB of a run's zip. The
 * PNGs in the app's own folder are not touched.
 */
object SummaryZip {

    const val FOLDER = "Downloads/Automation Tool Reports"

    private const val SHOT_WIDTH = 720
    private const val SHOT_QUALITY = 80

    data class Entry(val path: String, val name: String)

    data class Outcome(
        val zipPath: String?,
        val folder: String?,
        val error: String?,
    ) {
        fun toMap(): Map<String, Any?> = mapOf(
            "zipPath" to zipPath,
            "folder" to folder,
            "error" to error,
        )
    }

    fun build(context: Context, zipFile: File, displayName: String, entries: List<Entry>): Outcome {
        val roots = listOfNotNull(context.getExternalFilesDir(null), context.filesDir)
            .map { it.canonicalFile }
        val usable = entries.filter { entry ->
            val file = File(entry.path)
            file.isFile && roots.any { file.canonicalFile.startsWith(it) } && safeName(entry.name)
        }
        if (usable.isEmpty()) return Outcome(null, null, "there was nothing to put in the zip")

        val temp = File(zipFile.parentFile, zipFile.name + ".tmp")
        try {
            zipFile.parentFile?.mkdirs()
            ZipOutputStream(temp.outputStream().buffered()).use { zip ->
                // The video is already compressed; do not spend time on it.
                zip.setLevel(Deflater.BEST_SPEED)
                for (entry in usable) {
                    val shot = if (entry.name.endsWith(".png", ignoreCase = true)) smallJpeg(entry.path) else null
                    if (shot != null) {
                        zip.putNextEntry(ZipEntry(entry.name.dropLast(4) + ".jpg"))
                        zip.write(shot)
                    } else {
                        zip.putNextEntry(ZipEntry(entry.name))
                        FileInputStream(entry.path).use { it.copyTo(zip) }
                    }
                    zip.closeEntry()
                }
            }
            zipFile.delete()
            if (!temp.renameTo(zipFile)) throw IllegalStateException("could not move the finished zip into place")
        } catch (t: Throwable) {
            temp.delete()
            return Outcome(null, null, "the zip could not be written (${t.javaClass.simpleName}: ${t.message ?: "no detail"})")
        }

        val published = publish(context, zipFile, displayName)
        return if (published == null) {
            Outcome(zipFile.absolutePath, FOLDER, null)
        } else {
            Outcome(zipFile.absolutePath, null, "the zip was made but could not be copied to Downloads ($published)")
        }
    }

    /** [path] as a JPEG at most [SHOT_WIDTH] wide, or null to keep the original file. */
    private fun smallJpeg(path: String): ByteArray? {
        val source = runCatching { BitmapFactory.decodeFile(path) }.getOrNull() ?: return null
        return try {
            val scaled = if (source.width > SHOT_WIDTH) {
                Bitmap.createScaledBitmap(source, SHOT_WIDTH, source.height * SHOT_WIDTH / source.width, true)
            } else {
                source
            }
            val out = ByteArrayOutputStream()
            val ok = scaled.compress(Bitmap.CompressFormat.JPEG, SHOT_QUALITY, out)
            if (scaled !== source) scaled.recycle()
            if (ok) out.toByteArray() else null
        } catch (_: Throwable) {
            null
        } finally {
            source.recycle()
        }
    }

    /** Null on success, otherwise the reason. Replaces an earlier copy of the same name. */
    private fun publish(context: Context, source: File, displayName: String): String? {
        val resolver = context.contentResolver
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/Automation Tool Reports"
        try {
            resolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf(displayName, "$relativePath%"),
            )
        } catch (_: Throwable) {
            // Not ours to delete, or nothing there: a numbered copy is acceptable.
        }

        var destination: android.net.Uri? = null
        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            destination = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return "MediaStore refused the destination"
            resolver.openOutputStream(destination, "w").use { output ->
                requireNotNull(output) { "the destination could not be opened" }
                FileInputStream(source).use { it.copyTo(output) }
            }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            if (resolver.update(destination, done, null, null) <= 0) return "the copy could not be published"
            return null
        } catch (t: Throwable) {
            destination?.let { runCatching { resolver.delete(it, null, null) } }
            return "${t.javaClass.simpleName}: ${t.message ?: "no detail"}"
        }
    }

    private fun safeName(name: String): Boolean =
        name.isNotBlank() && !name.startsWith("/") && name.split('/').none { it == ".." || it.isEmpty() }
}
