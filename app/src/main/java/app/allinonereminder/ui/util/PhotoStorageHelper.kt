package app.allinonereminder.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import kotlin.math.max

/**
 * Handles saving and compressing medication/item photos according to §17:
 * "compress to max 1600 px JPEG q80 in filesDir/photos"
 */
object PhotoStorageHelper {

    private const val MAX_DIMENSION = 1600
    private const val JPEG_QUALITY = 80
    private const val PHOTOS_DIR = "photos"

    fun getPhotosDirectory(context: Context): File {
        val dir = File(context.filesDir, PHOTOS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getPhotoFile(context: Context, photoId: String): File {
        return File(getPhotosDirectory(context), "$photoId.jpg")
    }

    suspend fun saveAndCompressPhoto(context: Context, sourceUri: Uri): String = withContext(Dispatchers.IO) {
        val photoId = UUID.randomUUID().toString()
        val targetFile = getPhotoFile(context, photoId)

        context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
                ?: throw IllegalArgumentException("Could not decode image from URI")

            val scaledBitmap = scaleBitmapToMaxDimension(originalBitmap, MAX_DIMENSION)

            FileOutputStream(targetFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }

            if (scaledBitmap != originalBitmap) {
                scaledBitmap.recycle()
            }
            originalBitmap.recycle()
        } ?: throw IllegalArgumentException("Unable to open stream for URI $sourceUri")

        photoId
    }

    suspend fun saveBitmap(context: Context, bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val photoId = UUID.randomUUID().toString()
        val targetFile = getPhotoFile(context, photoId)

        val scaledBitmap = scaleBitmapToMaxDimension(bitmap, MAX_DIMENSION)
        FileOutputStream(targetFile).use { out ->
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }

        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }

        photoId
    }

    private fun scaleBitmapToMaxDimension(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxOriginal = max(width, height)

        if (maxOriginal <= maxDimension) {
            return bitmap
        }

        val scale = maxDimension.toFloat() / maxOriginal
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    fun deletePhoto(context: Context, photoId: String): Boolean {
        val file = getPhotoFile(context, photoId)
        return if (file.exists()) file.delete() else false
    }
}

