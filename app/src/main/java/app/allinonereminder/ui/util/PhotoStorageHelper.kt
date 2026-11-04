package app.allinonereminder.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
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
 * Uses inSampleSize decoding and EXIF orientation normalization to prevent OOM.
 */
object PhotoStorageHelper {

    const val MAX_DIMENSION = 1600
    const val JPEG_QUALITY = 80
    const val PHOTOS_DIR = "photos"

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

    fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        var currentWidth = options.outWidth
        var currentHeight = options.outHeight
        while (currentWidth / 2 >= reqWidth || currentHeight / 2 >= reqHeight) {
            inSampleSize *= 2
            currentWidth /= 2
            currentHeight /= 2
        }
        return inSampleSize
    }

    fun applyExifOrientation(inputStream: InputStream, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(inputStream)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                else -> return bitmap
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            bitmap
        }
    }

    suspend fun saveAndCompressPhoto(context: Context, sourceUri: Uri): String = withContext(Dispatchers.IO) {
        val photoId = UUID.randomUUID().toString()
        val targetFile = getPhotoFile(context, photoId)

        // 1. Decode bounds only with inJustDecodeBounds to compute inSampleSize
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, boundsOptions)
        } ?: throw IllegalArgumentException("Unable to open stream for URI $sourceUri")

        // 2. Compute inSampleSize targeted to MAX_DIMENSION
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(boundsOptions, MAX_DIMENSION, MAX_DIMENSION)
            inJustDecodeBounds = false
        }

        val sampledBitmap = context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, decodeOptions)
        } ?: throw IllegalArgumentException("Unable to decode stream for URI $sourceUri")

        // 3. Read and apply EXIF orientation
        val orientedBitmap = context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            applyExifOrientation(inputStream, sampledBitmap)
        } ?: sampledBitmap

        // 4. Scale to exact max dimension if still exceeding 1600px
        val finalBitmap = scaleBitmapToMaxDimension(orientedBitmap, MAX_DIMENSION)

        FileOutputStream(targetFile).use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }

        if (finalBitmap != orientedBitmap && finalBitmap != sampledBitmap) {
            finalBitmap.recycle()
        }
        if (orientedBitmap != sampledBitmap) {
            orientedBitmap.recycle()
        }
        sampledBitmap.recycle()

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

    fun scaleBitmapToMaxDimension(bitmap: Bitmap, maxDimension: Int): Bitmap {
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
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }
}
