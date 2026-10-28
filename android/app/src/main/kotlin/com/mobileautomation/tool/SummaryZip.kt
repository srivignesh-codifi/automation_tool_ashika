package com.mobileautomation.tool

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Packs a finished run's evidence (text report, screen recording, failed-step
 * screenshots) into one ZIP in the app's private reports directory.
 *
 * Only files inside the tool's own storage are accepted, so the zip can hold
 * nothing but run output.
 *
 * Screenshots go in as JPEGs [SHOT_WIDTH] wide under the same name with a
 * `.jpg` extension: the full-size PNGs were about 15 MB of a run's zip. The
 * PNGs in the app's own folder are not touched.
 */
object SummaryZip {

    // 540 px at 70 keeps a phone screen's prices readable at about half the bytes of 720 px at 80 (some 40 KB a
    // screenshot instead of 80 KB).
    private const val SHOT_WIDTH = 540
    private const val SHOT_QUALITY = 70

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

    fun build(context: Context, zipFile: File, entries: List<Entry>): Outcome {
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
                for (entry in usable) {
                    // The text reports shrink several times over; the video and the JPEGs are compressed already, so
                    // they are stored as they are rather than spending time on them for nothing.
                    val text = entry.name.endsWith(".txt", true) || entry.name.endsWith(".html", true) || entry.name.endsWith(".json", true)
                    zip.setLevel(if (text) Deflater.BEST_COMPRESSION else Deflater.NO_COMPRESSION)
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

        return Outcome(zipFile.absolutePath, null, null)
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

    private fun safeName(name: String): Boolean =
        name.isNotBlank() && !name.startsWith("/") && name.split('/').none { it == ".." || it.isEmpty() }
}
