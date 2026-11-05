package app.allinonereminder.backup

import android.content.Context
import androidx.room.withTransaction
import app.allinonereminder.alarm.AlarmCoordinator
import app.allinonereminder.data.local.AppDatabase
import app.allinonereminder.data.local.entity.*
import app.allinonereminder.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

data class BackupPreview(
    val profilesCount: Int,
    val itemsCount: Int,
    val schedulesCount: Int,
    val historyCount: Int,
    val photosCount: Int,
    val exportedAt: Long
)

class InvalidBackupPassphraseException(message: String) : Exception(message)
class InvalidBackupFileException(message: String) : Exception(message)

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val alarmCoordinator: AlarmCoordinator
) {
    companion object {
        val MAGIC_BYTES = byteArrayOf('A'.code.toByte(), 'I'.code.toByte(), 'R'.code.toByte(), '1'.code.toByte())
        const val VERSION = 1
        const val PBKDF2_ITERATIONS = 310_000
        const val KEY_LENGTH_BITS = 256
        const val SALT_LENGTH_BYTES = 16
        const val IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
        const val MAX_BACKUP_SIZE_BYTES = 50 * 1024 * 1024 // 50MB safety cap per §18 review C3
    }

    suspend fun createBackup(outputStream: OutputStream, passphrase: CharArray) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        // 1. Gather all database records
        val profiles = db.profileDao().getAllSync()
        val items = db.itemDao().getAllSync()
        val itemPhotos = db.itemPhotoDao().getAllSync()
        val schedules = db.scheduleDao().getAllSync()
        val mealAnchors = db.mealAnchorDao().getAllSync()
        val occurrences = db.occurrenceDao().getAllSync()
        val mealSessions = db.mealSessionDao().getAllSync()
        val changeLogs = db.changeLogDao().getAllSync()

        val rootJson = JSONObject().apply {
            put("version", VERSION)
            put("exportedAt", now)
            put("profiles", serializeProfiles(profiles))
            put("items", serializeItems(items))
            put("itemPhotos", serializeItemPhotos(itemPhotos))
            put("schedules", serializeSchedules(schedules))
            put("mealAnchors", serializeMealAnchors(mealAnchors))
            put("occurrences", serializeOccurrences(occurrences))
            put("mealSessions", serializeMealSessions(mealSessions))
            put("changeLogs", serializeChangeLogs(changeLogs))
        }

        // 2. Build ZIP archive with backup.json and photos
        val zipBaos = ByteArrayOutputStream()
        ZipOutputStream(zipBaos).use { zos ->
            // Add backup.json
            val jsonBytes = rootJson.toString().toByteArray(Charsets.UTF_8)
            zos.putNextEntry(ZipEntry("backup.json"))
            zos.write(jsonBytes)
            zos.closeEntry()

            // Add photos from context.filesDir/photos
            val photosDir = File(context.filesDir, "photos")
            if (photosDir.exists() && photosDir.isDirectory) {
                photosDir.listFiles()?.filter { it.isFile }?.forEach { photoFile ->
                    zos.putNextEntry(ZipEntry("photos/${photoFile.name}"))
                    photoFile.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }

        val unencryptedZipBytes = zipBaos.toByteArray()

        // 3. Encrypt archive with AES-256-GCM using PBKDF2 derived key
        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val secretKey = deriveKey(passphrase, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(unencryptedZipBytes)

        // 4. Write header: MAGIC (4B) + VERSION (4B) + SALT (16B) + IV (12B) + CIPHERTEXT
        outputStream.write(MAGIC_BYTES)
        val versionBytes = ByteBuffer.allocate(4).putInt(VERSION).array()
        outputStream.write(versionBytes)
        outputStream.write(salt)
        outputStream.write(iv)
        outputStream.write(ciphertext)
        outputStream.flush()
    }

    suspend fun inspectBackup(inputStream: InputStream, passphrase: CharArray): BackupPreview = withContext(Dispatchers.IO) {
        val (rootJson, photoCount) = decryptArchive(inputStream, passphrase)
        parsePreview(rootJson, photoCount)
    }

    suspend fun restoreBackup(inputStream: InputStream, passphrase: CharArray): BackupPreview = withContext(Dispatchers.IO) {
        val decryptedZipBytes = decryptArchiveBytes(inputStream, passphrase)

        var rootJson: JSONObject? = null
        val photosDir = File(context.filesDir, "photos")
        if (!photosDir.exists()) {
            photosDir.mkdirs()
        }

        // Extract photos to temp dir first per §18 review B5(d)
        val tempPhotosDir = File(context.cacheDir, "backup_restore_temp_${System.currentTimeMillis()}")
        tempPhotosDir.mkdirs()

        try {
            var photoCount = 0

            ZipInputStream(ByteArrayInputStream(decryptedZipBytes)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "backup.json") {
                        val jsonStr = zis.bufferedReader(Charsets.UTF_8).readText()
                        rootJson = JSONObject(jsonStr)
                    } else if (entry.name.startsWith("photos/") && !entry.isDirectory) {
                        val fileName = entry.name.removePrefix("photos/")
                        // Zip Slip Protection per §18 review B5(a): reject names containing /, \, or ..
                        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
                            throw InvalidBackupFileException("Zip Slip detected: invalid file name in archive: $fileName")
                        }
                        val tempFile = File(tempPhotosDir, fileName)
                        if (!tempFile.canonicalPath.startsWith(tempPhotosDir.canonicalPath + File.separator)) {
                            throw InvalidBackupFileException("Zip Slip detected: target outside photo dir: ${tempFile.canonicalPath}")
                        }
                        FileOutputStream(tempFile).use { fos ->
                            zis.copyTo(fos)
                        }
                        photoCount++
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (rootJson == null) {
                throw InvalidBackupFileException("Archive is missing backup.json")
            }

            val json = rootJson!!
            val profiles = deserializeProfiles(json.optJSONArray("profiles"))
            val items = deserializeItems(json.optJSONArray("items"))
            val itemPhotos = deserializeItemPhotos(json.optJSONArray("itemPhotos"))
            val schedules = deserializeSchedules(json.optJSONArray("schedules"))
            val mealAnchors = deserializeMealAnchors(json.optJSONArray("mealAnchors"))
            val occurrences = deserializeOccurrences(json.optJSONArray("occurrences"))
            val mealSessions = deserializeMealSessions(json.optJSONArray("mealSessions"))
            val changeLogs = deserializeChangeLogs(json.optJSONArray("changeLogs"))

            // Atomically replace all database tables
            db.withTransaction {
                db.changeLogDao().deleteAll()
                db.occurrenceDao().deleteAll()
                db.scheduleDao().deleteAll()
                db.itemPhotoDao().deleteAll()
                db.itemDao().deleteAll()
                db.mealAnchorDao().deleteAll()
                db.mealSessionDao().deleteAll()
                db.profileDao().deleteAll()

                db.profileDao().insertAll(profiles)
                db.mealAnchorDao().insertAll(mealAnchors)
                db.itemDao().insertAll(items)
                db.itemPhotoDao().insertAll(itemPhotos)
                db.scheduleDao().insertAll(schedules)
                db.occurrenceDao().upsertAll(occurrences)
                db.mealSessionDao().insertAll(mealSessions)
                db.changeLogDao().insertAll(changeLogs)
            }

            // Only swap in photos AFTER DB transaction succeeds
            tempPhotosDir.listFiles()?.filter { it.isFile }?.forEach { tempPhoto ->
                val targetFile = File(photosDir, tempPhoto.name)
                tempPhoto.copyTo(targetFile, overwrite = true)
            }

            // Delete unreferenced photos after restore
            val referencedFileNames = itemPhotos.map { File(it.filePath).name }.toSet()
            photosDir.listFiles()?.filter { it.isFile }?.forEach { photoFile ->
                if (photoFile.name !in referencedFileNames) {
                    photoFile.delete()
                }
            }

            // Reschedule alarms immediately with restored dataset
            alarmCoordinator.reschedule("restore_from_backup")

            parsePreview(json, photoCount)
        } finally {
            tempPhotosDir.deleteRecursively()
        }
    }

    private fun decryptArchiveBytes(inputStream: InputStream, passphrase: CharArray): ByteArray {
        val dis = DataInputStream(inputStream)
        val magic = ByteArray(4)
        try {
            dis.readFully(magic)
        } catch (e: EOFException) {
            throw InvalidBackupFileException("Invalid backup format: header truncated")
        }
        if (!magic.contentEquals(MAGIC_BYTES)) {
            throw InvalidBackupFileException("Invalid backup format: header signature mismatch")
        }

        val version = try {
            dis.readInt()
        } catch (e: EOFException) {
            throw InvalidBackupFileException("Invalid backup format: truncated version")
        }
        if (version != VERSION) {
            throw InvalidBackupFileException("Unsupported backup version: $version")
        }

        val salt = ByteArray(SALT_LENGTH_BYTES)
        try {
            dis.readFully(salt)
        } catch (e: EOFException) {
            throw InvalidBackupFileException("Invalid backup format: truncated salt")
        }

        val iv = ByteArray(IV_LENGTH_BYTES)
        try {
            dis.readFully(iv)
        } catch (e: EOFException) {
            throw InvalidBackupFileException("Invalid backup format: truncated IV")
        }

        val baos = ByteArrayOutputStream()
        val buffer = ByteArray(65536)
        var totalBytes = 0
        var bytesRead: Int
        while (dis.read(buffer).also { bytesRead = it } != -1) {
            totalBytes += bytesRead
            if (totalBytes > MAX_BACKUP_SIZE_BYTES) {
                throw InvalidBackupFileException("Backup archive exceeds maximum supported size (50MB)")
            }
            baos.write(buffer, 0, bytesRead)
        }
        val ciphertext = baos.toByteArray()
        if (ciphertext.isEmpty()) {
            throw InvalidBackupFileException("Backup payload is empty")
        }

        val secretKey = deriveKey(passphrase, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (_: AEADBadTagException) {
            throw InvalidBackupPassphraseException("Incorrect passphrase or corrupted backup file")
        } catch (e: Exception) {
            throw InvalidBackupFileException("Decryption error: ${e.message}")
        }
    }

    internal fun decryptArchive(inputStream: InputStream, passphrase: CharArray): Pair<JSONObject, Int> {
        val decryptedZipBytes = decryptArchiveBytes(inputStream, passphrase)
        var rootJson: JSONObject? = null
        var photoCount = 0

        ZipInputStream(ByteArrayInputStream(decryptedZipBytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (entry.name == "backup.json") {
                    val jsonStr = zis.bufferedReader(Charsets.UTF_8).readText()
                    rootJson = JSONObject(jsonStr)
                } else if (entry.name.startsWith("photos/") && !entry.isDirectory) {
                    photoCount++
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        if (rootJson == null) {
            throw InvalidBackupFileException("Archive is missing backup.json")
        }
        return Pair(rootJson!!, photoCount)
    }

    private fun parsePreview(json: JSONObject, photoCount: Int): BackupPreview {
        return BackupPreview(
            profilesCount = json.optJSONArray("profiles")?.length() ?: 0,
            itemsCount = json.optJSONArray("items")?.length() ?: 0,
            schedulesCount = json.optJSONArray("schedules")?.length() ?: 0,
            historyCount = json.optJSONArray("occurrences")?.length() ?: 0,
            photosCount = photoCount,
            exportedAt = json.optLong("exportedAt", System.currentTimeMillis())
        )
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    // --- JSON Serialization Helpers ---

    private fun serializeProfiles(list: List<ProfileEntity>): JSONArray = JSONArray().apply {
        list.forEach { p ->
            put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("colorArgb", p.colorArgb)
                put("createdAt", p.createdAt)
                put("updatedAt", p.updatedAt)
                putOpt("deletedAt", p.deletedAt)
            })
        }
    }

    private fun deserializeProfiles(array: JSONArray?): List<ProfileEntity> {
        if (array == null) return emptyList()
        val list = mutableListOf<ProfileEntity>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list.add(
                ProfileEntity(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    colorArgb = o.getInt("colorArgb"),
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt"),
                    deletedAt = if (o.isNull("deletedAt")) null else o.getLong("deletedAt")
                )
            )
        }
        return list
    }

    private fun serializeItems(list: List<ItemEntity>): JSONArray = JSONArray().apply {
        list.forEach { item ->
            put(JSONObject().apply {
                put("id", item.id)
                put("profileId", item.profileId)
                put("type", item.type.name)
                put("name", item.name)
                put("dosageAmount", item.dosageAmount)
                put("dosageUnit", item.dosageUnit)
                put("criticality", item.criticality.name)
                putOpt("instructions", item.instructions)
                put("graceMinutes", item.graceMinutes)
                put("reRingMinutes", item.reRingMinutes)
                putOpt("minGapMinutes", item.minGapMinutes)
                put("timePolicy", item.timePolicy.name)
                put("homeZoneId", item.homeZoneId)
                put("startDate", item.startDate)
                put("createdAt", item.createdAt)
                put("updatedAt", item.updatedAt)
                putOpt("deletedAt", item.deletedAt)
            })
        }
    }

    private fun deserializeItems(array: JSONArray?): List<ItemEntity> {
        if (array == null) return emptyList()
        val list = mutableListOf<ItemEntity>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list.add(
                ItemEntity(
                    id = o.getString("id"),
                    profileId = o.getString("profileId"),
                    type = ItemType.valueOf(o.getString("type")),
                    name = o.getString("name"),
                    dosageAmount = o.getString("dosageAmount"),
                    dosageUnit = o.getString("dosageUnit"),
                    criticality = Criticality.valueOf(o.getString("criticality")),
                    instructions = if (o.isNull("instructions")) null else o.getString("instructions"),
                    graceMinutes = o.getInt("graceMinutes"),
                    reRingMinutes = o.getInt("reRingMinutes"),
                    minGapMinutes = if (o.isNull("minGapMinutes")) null else o.getInt("minGapMinutes"),
                    timePolicy = TimePolicy.valueOf(o.getString("timePolicy")),
                    homeZoneId = o.getString("homeZoneId"),
                    startDate = o.getString("startDate"),
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt"),
                    deletedAt = if (o.isNull("deletedAt")) null else o.getLong("deletedAt")
                )
            )
        }
        return list
    }

    private fun serializeItemPhotos(list: List<ItemPhotoEntity>): JSONArray = JSONArray().apply {
        list.forEach { photo ->
            put(JSONObject().apply {
                put("id", photo.id)
                put("itemId", photo.itemId)
                // Store path relative to filesDir per §18 review B5(e)
                val relPath = "photos/" + File(photo.filePath).name
                put("filePath", relPath)
                put("sortOrder", photo.sortOrder)
                put("createdAt", photo.createdAt)
                putOpt("deletedAt", photo.deletedAt)
            })
        }
    }

    private fun deserializeItemPhotos(array: JSONArray?): List<ItemPhotoEntity> {
        if (array == null) return emptyList()
        val list = mutableListOf<ItemPhotoEntity>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val rawPath = o.getString("filePath")
            // Resolve relative path against current device filesDir
            val resolvedPath = if (rawPath.startsWith("photos/")) {
                File(context.filesDir, rawPath).absolutePath
            } else {
                rawPath
            }
            list.add(
                ItemPhotoEntity(
                    id = o.getString("id"),
                    itemId = o.getString("itemId"),
                    filePath = resolvedPath,
                    sortOrder = o.getInt("sortOrder"),
                    createdAt = o.getLong("createdAt"),
                    deletedAt = if (o.isNull("deletedAt")) null else o.getLong("deletedAt")
                )
            )
        }
        return list
    }

    private fun serializeSchedules(list: List<ScheduleEntity>): JSONArray = JSONArray().apply {
        list.forEach { s ->
            put(JSONObject().apply {
                put("id", s.id)
                put("itemId", s.itemId)
                put("anchorType", s.anchorType.name)
                putOpt("minuteOfDay", s.minuteOfDay)
                putOpt("meal", s.meal?.name)
                putOpt("relation", s.relation?.name)
                putOpt("offsetMinutes", s.offsetMinutes)
                put("recurrence", s.recurrence.name)
                putOpt("weekdayMask", s.weekdayMask)
                putOpt("intervalN", s.intervalN)
                put("anchorDate", s.anchorDate)
                putOpt("onceDate", s.onceDate)
                put("createdAt", s.createdAt)
                put("updatedAt", s.updatedAt)
                putOpt("deletedAt", s.deletedAt)
            })
        }
    }

    private fun deserializeSchedules(array: JSONArray?): List<ScheduleEntity> {
        if (array == null) return emptyList()
        val list = mutableListOf<ScheduleEntity>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list.add(
                ScheduleEntity(
                    id = o.getString("id"),
                    itemId = o.getString("itemId"),
                    anchorType = ScheduleAnchorType.valueOf(o.getString("anchorType")),
                    minuteOfDay = if (o.isNull("minuteOfDay")) null else o.getInt("minuteOfDay"),
                    meal = if (o.isNull("meal")) null else MealType.valueOf(o.getString("meal")),
                    relation = if (o.isNull("relation")) null else MealRelation.valueOf(o.getString("relation")),
                    offsetMinutes = if (o.isNull("offsetMinutes")) null else o.getInt("offsetMinutes"),
                    recurrence = RecurrenceType.valueOf(o.getString("recurrence")),
                    weekdayMask = if (o.isNull("weekdayMask")) null else o.getInt("weekdayMask"),
                    intervalN = if (o.isNull("intervalN")) null else o.getInt("intervalN"),
                    anchorDate = o.getString("anchorDate"),
                    onceDate = if (o.isNull("onceDate")) null else o.getString("onceDate"),
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt"),
                    deletedAt = if (o.isNull("deletedAt")) null else o.getLong("deletedAt")
                )
            )
        }
        return list
    }

    private fun serializeMealAnchors(list: List<MealAnchorEntity>): JSONArray = JSONArray().apply {
        list.forEach { a ->
            put(JSONObject().apply {
                put("id", a.id)
                put("profileId", a.profileId)
                put("dayType", a.dayType.name)
                put("meal", a.meal.name)
                put("minuteOfDay", a.minuteOfDay)
                put("updatedAt", a.updatedAt)
            })
        }
    }

    private fun deserializeMealAnchors(array: JSONArray?): List<MealAnchorEntity> {
        if (array == null) return emptyList()
        val list = mutableListOf<MealAnchorEntity>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list.add(
                MealAnchorEntity(
                    id = o.getString("id"),
                    profileId = o.getString("profileId"),
                    dayType = DayType.valueOf(o.getString("dayType")),
                    meal = MealType.valueOf(o.getString("meal")),
                    minuteOfDay = o.getInt("minuteOfDay"),
                    updatedAt = o.getLong("updatedAt")
                )
            )
        }
        return list
    }

    private fun serializeOccurrences(list: List<OccurrenceEntity>): JSONArray = JSONArray().apply {
        list.forEach { occ ->
            put(JSONObject().apply {
                put("id", occ.id)
                put("itemId", occ.itemId)
                put("scheduleId", occ.scheduleId)
                put("profileId", occ.profileId)
                put("plannedAt", occ.plannedAt)
                put("dueAt", occ.dueAt)
                put("remindAt", occ.remindAt)
                put("status", occ.status.name)
                put("ringCount", occ.ringCount)
                putOpt("actedAt", occ.actedAt)
                putOpt("skipReason", occ.skipReason)
                putOpt("note", occ.note)
                put("deviceId", occ.deviceId)
                put("updatedAt", occ.updatedAt)
            })
        }
    }

    private fun deserializeOccurrences(array: JSONArray?): List<OccurrenceEntity> {
        if (array == null) return emptyList()
        val list = mutableListOf<OccurrenceEntity>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list.add(
                OccurrenceEntity(
                    id = o.getString("id"),
                    itemId = o.getString("itemId"),
                    scheduleId = o.getString("scheduleId"),
                    profileId = o.getString("profileId"),
                    plannedAt = o.getLong("plannedAt"),
                    dueAt = o.getLong("dueAt"),
                    remindAt = o.optLong("remindAt", o.getLong("dueAt")),
                    status = OccurrenceStatus.valueOf(o.getString("status")),
                    ringCount = o.optInt("ringCount", 0),
                    actedAt = if (o.isNull("actedAt")) null else o.getLong("actedAt"),
                    skipReason = if (o.isNull("skipReason")) null else o.getString("skipReason"),
                    note = if (o.isNull("note")) null else o.getString("note"),
                    deviceId = o.getString("deviceId"),
                    updatedAt = o.getLong("updatedAt")
                )
            )
        }
        return list
    }

    private fun serializeMealSessions(list: List<MealSessionEntity>): JSONArray = JSONArray().apply {
        list.forEach { m ->
            put(JSONObject().apply {
                put("id", m.id)
                put("profileId", m.profileId)
                put("meal", m.meal.name)
                put("date", m.date)
                put("startedAt", m.startedAt)
                putOpt("endedAt", m.endedAt)
            })
        }
    }

    private fun deserializeMealSessions(array: JSONArray?): List<MealSessionEntity> {
        if (array == null) return emptyList()
        val list = mutableListOf<MealSessionEntity>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list.add(
                MealSessionEntity(
                    id = o.getString("id"),
                    profileId = o.getString("profileId"),
                    meal = MealType.valueOf(o.getString("meal")),
                    date = o.getString("date"),
                    startedAt = o.getLong("startedAt"),
                    endedAt = if (o.isNull("endedAt")) null else o.getLong("endedAt")
                )
            )
        }
        return list
    }

    private fun serializeChangeLogs(list: List<ChangeLogEntity>): JSONArray = JSONArray().apply {
        list.forEach { c ->
            put(JSONObject().apply {
                put("id", c.id)
                put("entityType", c.entityType)
                put("entityId", c.entityId)
                put("field", c.field)
                putOpt("oldValue", c.oldValue)
                putOpt("newValue", c.newValue)
                put("changedAt", c.changedAt)
                put("deviceId", c.deviceId)
            })
        }
    }

    private fun deserializeChangeLogs(array: JSONArray?): List<ChangeLogEntity> {
        if (array == null) return emptyList()
        val list = mutableListOf<ChangeLogEntity>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list.add(
                ChangeLogEntity(
                    id = o.getString("id"),
                    entityType = o.getString("entityType"),
                    entityId = o.getString("entityId"),
                    field = o.getString("field"),
                    oldValue = if (o.isNull("oldValue")) null else o.getString("oldValue"),
                    newValue = if (o.isNull("newValue")) null else o.getString("newValue"),
                    changedAt = o.getLong("changedAt"),
                    deviceId = o.getString("deviceId")
                )
            )
        }
        return list
    }
}

