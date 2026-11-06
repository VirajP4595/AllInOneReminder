package app.allinonereminder.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.allinonereminder.model.AppointmentAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object AttachmentStorageHelper {

    const val ATTACHMENTS_DIR = "attachments"

    fun getAttachmentsDirectory(context: Context): File {
        val dir = File(context.filesDir, ATTACHMENTS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getAttachmentFile(context: Context, fileName: String): File {
        return File(getAttachmentsDirectory(context), fileName)
    }

    suspend fun saveAttachment(
        context: Context,
        appointmentId: String,
        sourceUri: Uri
    ): AppointmentAttachment = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(sourceUri) ?: "application/octet-stream"
        val extension = when {
            mimeType.contains("pdf", ignoreCase = true) -> ".pdf"
            mimeType.contains("png", ignoreCase = true) -> ".png"
            mimeType.contains("jpeg", ignoreCase = true) || mimeType.contains("jpg", ignoreCase = true) -> ".jpg"
            else -> ".bin"
        }

        val id = UUID.randomUUID().toString()
        val fileName = "$id$extension"
        val targetFile = getAttachmentFile(context, fileName)

        contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("Could not open input stream for $sourceUri")

        AppointmentAttachment(
            id = id,
            appointmentId = appointmentId,
            filePath = fileName,
            mimeType = mimeType,
            createdAt = System.currentTimeMillis()
        )
    }

    fun getUriForAttachment(context: Context, attachment: AppointmentAttachment): Uri {
        val file = getAttachmentFile(context, attachment.filePath)
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    fun openAttachment(context: Context, attachment: AppointmentAttachment) {
        try {
            val uri = getUriForAttachment(context, attachment)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, attachment.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // In case no app handler is installed
        }
    }

    fun deleteAttachmentFile(context: Context, fileName: String): Boolean {
        val file = getAttachmentFile(context, fileName)
        return if (file.exists()) {
            file.delete()
        } else false
    }
}

