package app.allinonereminder.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import java.security.KeyStore
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Automatic weekly encrypted backup (issue #27): the user picks a folder once (SAF tree, which
 * also offers cloud providers such as Google Drive without any network code in this app) and a
 * backup passphrase. A weekly job writes the normal .airbackup file there and keeps the newest 4.
 *
 * The passphrase is needed again for every backup, so it is stored encrypted with a key that never
 * leaves the Android Keystore. It is never logged or shown.
 */
object AutoBackup {
    const val FILE_PREFIX = "AllInOneReminder-auto-"
    const val FILE_SUFFIX = ".airbackup"
    const val KEEP_FILES = 4

    private const val PREFS = "auto_backup"
    private const val KEY_FOLDER = "folder_uri"
    private const val KEY_PASS = "passphrase_enc"
    private const val KEY_LAST_SUCCESS = "last_success_at"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEYSTORE_ALIAS = "aior_auto_backup_passphrase"
    private const val WEEKLY_WORK = "auto_backup_weekly"
    private const val NOW_WORK = "auto_backup_now"
    private const val CHANNEL_ID = "backup_status"
    private const val NOTIFICATION_ID = 7301

    data class Status(
        val enabled: Boolean,
        val folderName: String?,
        val lastSuccessAt: Long?,
        val lastError: String?
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun status(context: Context): Status {
        val p = prefs(context)
        val folder = p.getString(KEY_FOLDER, null)
        return Status(
            enabled = folder != null && p.getString(KEY_PASS, null) != null,
            folderName = folder?.let { folderDisplayName(Uri.parse(it)) },
            lastSuccessAt = p.getLong(KEY_LAST_SUCCESS, 0L).takeIf { it > 0L },
            lastError = p.getString(KEY_LAST_ERROR, null)
        )
    }

    /** Turns automatic backup on for [folder] (a tree URI from ACTION_OPEN_DOCUMENT_TREE). */
    fun enable(context: Context, folder: Uri, passphrase: CharArray) {
        context.contentResolver.takePersistableUriPermission(
            folder,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        prefs(context).edit()
            .putString(KEY_FOLDER, folder.toString())
            .putString(KEY_PASS, encrypt(passphrase))
            .remove(KEY_LAST_ERROR)
            .apply()
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WEEKLY_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        runNow(context)
    }

    fun disable(context: Context) {
        val folder = prefs(context).getString(KEY_FOLDER, null)
        WorkManager.getInstance(context).cancelUniqueWork(WEEKLY_WORK)
        prefs(context).edit().clear().apply()
        runCatching { deleteKey() }
        if (folder != null) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(folder),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
    }

    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AutoBackupWorker>().build()
        )
    }

    /** Writes one backup into the folder and deletes the oldest automatic files beyond [KEEP_FILES]. */
    internal suspend fun backUp(context: Context, backupManager: BackupManager) {
        val p = prefs(context)
        val folder = p.getString(KEY_FOLDER, null)?.let { Uri.parse(it) }
            ?: throw IllegalStateException("No backup folder chosen")
        val passphrase = p.getString(KEY_PASS, null)?.let { decrypt(it) }
            ?: throw IllegalStateException("Backup passphrase missing")
        try {
            val resolver = context.contentResolver
            val treeDoc = DocumentsContract.buildDocumentUriUsingTree(
                folder, DocumentsContract.getTreeDocumentId(folder)
            )
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))
            val name = "$FILE_PREFIX$stamp$FILE_SUFFIX"
            val file = DocumentsContract.createDocument(resolver, treeDoc, "application/octet-stream", name)
                ?: throw IllegalStateException("Could not create a file in the backup folder")
            try {
                resolver.openOutputStream(file)?.use { out -> backupManager.createBackup(out, passphrase) }
                    ?: throw IllegalStateException("Could not write to the backup folder")
            } catch (e: Exception) {
                runCatching { DocumentsContract.deleteDocument(resolver, file) }
                throw e
            }
            rotate(context, folder)
        } finally {
            passphrase.fill('\u0000')
        }
    }

    private fun rotate(context: Context, folder: Uri) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            folder, DocumentsContract.getTreeDocumentId(folder)
        )
        val found = mutableListOf<Pair<String, String>>() // name to documentId
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                if (name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX)) found += name to id
            }
        }
        // Names carry a sortable timestamp, so newest sort last.
        found.sortedByDescending { it.first }.drop(KEEP_FILES).forEach { (_, id) ->
            runCatching {
                DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(folder, id))
            }
        }
    }

    internal fun recordSuccess(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_SUCCESS, System.currentTimeMillis()).remove(KEY_LAST_ERROR).apply()
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** Saves a plain reason (never the passphrase) and tells the user, since backups now stop. */
    internal fun recordFailure(context: Context, reason: String) {
        prefs(context).edit().putString(KEY_LAST_ERROR, reason).apply()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Backup status", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pending = open?.let {
            android.app.PendingIntent.getActivity(
                context, NOTIFICATION_ID, it,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Automatic backup failed")
            .setContentText("$reason. Open Settings > Automatic weekly backup.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    private fun folderDisplayName(tree: Uri): String =
        runCatching { DocumentsContract.getTreeDocumentId(tree).substringAfterLast(':').ifBlank { "Chosen folder" } }
            .getOrDefault("Chosen folder")

    // --- Keystore wrapping of the passphrase ---

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun deleteKey() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEYSTORE_ALIAS)
    }

    private fun encrypt(passphrase: CharArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = String(passphrase).toByteArray(Charsets.UTF_8)
        val ct = cipher.doFinal(bytes)
        bytes.fill(0)
        return Base64.encodeToString(cipher.iv + ct, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): CharArray {
        val all = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        val bytes = cipher.doFinal(all.copyOfRange(12, all.size))
        val chars = String(bytes, Charsets.UTF_8).toCharArray()
        bytes.fill(0)
        return chars
    }
}

class AutoBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun backupManager(): BackupManager
    }

    override suspend fun doWork(): Result {
        return try {
            val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
            AutoBackup.backUp(applicationContext, deps.backupManager())
            AutoBackup.recordSuccess(applicationContext)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            // Folder access was revoked (or the folder was removed): retrying won't help.
            AutoBackup.recordFailure(applicationContext, "The app can no longer write to the backup folder")
            Result.failure()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                AutoBackup.recordFailure(applicationContext, "The backup could not be written")
                Result.failure()
            }
        }
    }
}
