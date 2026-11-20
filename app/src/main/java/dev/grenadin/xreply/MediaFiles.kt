package dev.grenadin.xreply

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File

object MediaFiles {
    const val MAX_IMAGES = 4
    val PICK_TYPES = arrayOf("image/jpeg", "image/png", "image/webp")
    private const val MAX_BYTES = 5_000_000L
    private const val MAX_SIDE = 4096
    private const val KEEP_MS = 10 * 60 * 1000L

    private fun dir(context: Context) = File(context.cacheDir, "media").apply { mkdirs() }

    fun clearOld(context: Context) {
        val cutoff = System.currentTimeMillis() - KEEP_MS
        dir(context).listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    fun mimeOf(file: File) = when (file.extension.lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "image/jpeg"
    }

    fun import(context: Context, uri: Uri): File? = runCatching {
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val ext = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "jpg"
        }
        val out = File(dir(context), "${System.nanoTime()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } } ?: return null
        if (mime == "image/gif" || out.length() <= MAX_BYTES) return out
        val small = shrink(out, File(dir(context), "${System.nanoTime()}.jpg"))
        out.delete()
        small
    }.getOrNull()

    private fun shrink(source: File, target: File): File? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
        var bitmap = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val degrees = when (ExifInterface(source.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees != 0f) {
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
        }
        var quality = 90
        do {
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            quality -= 10
        } while (target.length() > MAX_BYTES && quality >= 50)
        return target
    }
}
