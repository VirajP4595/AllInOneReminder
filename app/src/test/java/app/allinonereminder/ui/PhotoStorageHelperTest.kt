package app.allinonereminder.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import app.allinonereminder.ui.util.PhotoStorageHelper
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PhotoStorageHelperTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun calculateInSampleSize_downsamplesHighResolutionImages() {
        val options = BitmapFactory.Options().apply {
            outWidth = 4000
            outHeight = 3000
        }

        val inSampleSize = PhotoStorageHelper.calculateInSampleSize(options, 1600, 1600)
        // With 4000x3000, 4000/2 = 2000 > 1600, 4000/4 = 1000 <= 1600, so inSampleSize is at least 2
        assertThat(inSampleSize).isAtLeast(2)
    }

    @Test
    fun scaleBitmapToMaxDimension_scalesDownOversizedBitmaps() {
        val largeBitmap = Bitmap.createBitmap(2400, 1800, Bitmap.Config.ARGB_8888)
        val scaled = PhotoStorageHelper.scaleBitmapToMaxDimension(largeBitmap, 1600)

        assertThat(scaled.width).isAtMost(1600)
        assertThat(scaled.height).isAtMost(1600)
        assertThat(scaled.width).isEqualTo(1600)
        assertThat(scaled.height).isEqualTo(1200)
    }

    @Test
    fun scaleBitmapToMaxDimension_leavesSmallBitmapsUnchanged() {
        val smallBitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val result = PhotoStorageHelper.scaleBitmapToMaxDimension(smallBitmap, 1600)

        assertThat(result).isSameInstanceAs(smallBitmap)
    }

    @Test
    fun saveBitmap_compressesAndSavesToDisk() = runBlocking {
        val bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val photoId = PhotoStorageHelper.saveBitmap(context, bitmap)

        val file = PhotoStorageHelper.getPhotoFile(context, photoId)
        assertThat(file.exists()).isTrue()
        assertThat(file.length()).isGreaterThan(0L)

        // Cleanup
        val deleted = PhotoStorageHelper.deletePhoto(context, photoId)
        assertThat(deleted).isTrue()
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun applyExifOrientation_rotatesBitmapBasedOnExif() {
        val original = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val tempFile = java.io.File(context.cacheDir, "test_exif.jpg")
        val fos = java.io.FileOutputStream(tempFile)
        original.compress(Bitmap.CompressFormat.JPEG, 90, fos)
        fos.close()

        val exif = android.media.ExifInterface(tempFile.absolutePath)
        exif.setAttribute(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_ROTATE_90.toString())
        exif.saveAttributes()

        val rotated = tempFile.inputStream().use { stream ->
            PhotoStorageHelper.applyExifOrientation(stream, original)
        }

        assertThat(rotated.width).isEqualTo(100)
        assertThat(rotated.height).isEqualTo(200)
        tempFile.delete()
    }
}
