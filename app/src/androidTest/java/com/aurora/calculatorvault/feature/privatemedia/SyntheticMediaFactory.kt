package com.aurora.calculatorvault.feature.privatemedia

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

/**
 * Creates synthetic MediaStore images/videos for private-album instrumentation.
 * Never touches user personal media; tags display names with [TEST_PREFIX].
 */
object SyntheticMediaFactory {
    const val TEST_PREFIX = "CV_I334B_"

    fun createJpegUri(context: Context, seed: Int): Uri {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb((seed * 40) % 256, 80, 160))
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 18f
        }
        canvas.drawText("T$seed", 8f, 36f, paint)
        val name = "${TEST_PREFIX}img_$seed.jpg"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CalculatorVaultTest")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Failed to insert synthetic image")
        resolver.openOutputStream(uri)?.use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out))
        } ?: error("Failed to write synthetic image")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        bitmap.recycle()
        return uri
    }

    fun createVideoUri(context: Context, seed: Int): Uri {
        // Minimal non-empty MP4 container bytes are not required for copy; importer accepts video/* mime.
        // Write a tiny file with video/mp4 MIME so ContentResolver.getType returns video/mp4.
        val name = "${TEST_PREFIX}vid_$seed.mp4"
        val cache = File(context.cacheDir, name)
        FileOutputStream(cache).use { out ->
            // ftyp + free box stub — enough for openInputStream copy; metadata may be empty.
            out.write(
                byteArrayOf(
                    0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70,
                    0x69, 0x73, 0x6F, 0x6D, 0x00, 0x00, 0x00, 0x01,
                    0x69, 0x73, 0x6F, 0x6D, 0x61, 0x76, 0x63, 0x31,
                    0x00, 0x00, 0x00, 0x08, 0x66, 0x72, 0x65, 0x65,
                ),
            )
            // Pad so length > 0 for importer checks
            out.write(ByteArray(256) { (seed + it).toByte() })
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/CalculatorVaultTest")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Failed to insert synthetic video")
        resolver.openOutputStream(uri)?.use { out ->
            cache.inputStream().use { input -> input.copyTo(out) }
        } ?: error("Failed to write synthetic video")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        cache.delete()
        return uri
    }

    fun deleteUri(context: Context, uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }
}
