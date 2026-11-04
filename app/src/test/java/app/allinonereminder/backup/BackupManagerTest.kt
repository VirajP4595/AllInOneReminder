package app.allinonereminder.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.allinonereminder.alarm.AlarmCoordinator
import app.allinonereminder.data.local.AppDatabase
import app.allinonereminder.data.local.ChangeLogWriter
import app.allinonereminder.data.local.entity.*
import app.allinonereminder.data.repository.*
import app.allinonereminder.model.*
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class BackupManagerTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var coordinator: AlarmCoordinator
    private lateinit var backupManager: BackupManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val changeLogWriter = ChangeLogWriter(db.changeLogDao())
        val profileRepo = ProfileRepository(db.profileDao(), db.mealAnchorDao(), changeLogWriter)
        val itemRepo = ItemRepository(db.itemDao(), db.itemPhotoDao(), changeLogWriter)
        val scheduleRepo = ScheduleRepository(db.scheduleDao(), changeLogWriter)
        val mealAnchorRepo = MealAnchorRepository(db.mealAnchorDao(), changeLogWriter)
        val mealSessionRepo = MealSessionRepository(db.mealSessionDao())
        val occurrenceRepo = OccurrenceRepository(db.occurrenceDao())
        val settingsRepo = SettingsRepository(context.dataStore)

        coordinator = AlarmCoordinator(
            context = context,
            itemRepository = itemRepo,
            scheduleRepository = scheduleRepo,
            mealAnchorRepository = mealAnchorRepo,
            mealSessionRepository = mealSessionRepo,
            occurrenceRepository = occurrenceRepo,
            settingsRepository = settingsRepo
        )

        backupManager = BackupManager(context, db, coordinator)
    }

    @After
    fun teardown() {
        db.close()
        // Clean up test photos
        File(context.filesDir, "photos").deleteRecursively()
    }

    @Test
    fun backupAndRestore_roundTrip_restoresAllEntitiesAndPhotos() = runBlocking {
        val now = 1700000000000L
        val passphrase = "SecureFamilyPassword2026!".toCharArray()

        // 1. Populate database with records
        val profile = ProfileEntity(
            id = "prof-1",
            name = "Grandma",
            colorArgb = 0xFFE91E63.toInt(),
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )
        db.profileDao().insert(profile)

        val item = ItemEntity(
            id = "item-1",
            profileId = "prof-1",
            type = ItemType.MEDICINE,
            name = "Blood Pressure Med",
            dosageAmount = "20",
            dosageUnit = "mg",
            criticality = Criticality.NORMAL,
            instructions = "Take after breakfast",
            graceMinutes = 60,
            reRingMinutes = 5,
            minGapMinutes = 240,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = "Asia/Kolkata",
            startDate = "2026-01-01",
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )
        db.itemDao().insert(item)

        val schedule = ScheduleEntity(
            id = "sched-1",
            itemId = "item-1",
            anchorType = ScheduleAnchorType.MEAL,
            minuteOfDay = null,
            meal = MealType.BREAKFAST,
            relation = MealRelation.AFTER,
            offsetMinutes = 30,
            recurrence = RecurrenceType.DAILY,
            intervalN = null,
            weekdayMask = null,
            anchorDate = "2026-01-01",
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )
        db.scheduleDao().insert(schedule)

        val anchor = MealAnchorEntity(
            id = "anchor-1",
            profileId = "prof-1",
            dayType = DayType.WEEKDAY,
            meal = MealType.BREAKFAST,
            minuteOfDay = 8 * 60,
            updatedAt = now
        )
        db.mealAnchorDao().insert(anchor)

        val occurrence = OccurrenceEntity(
            id = "occ-1",
            itemId = "item-1",
            scheduleId = "sched-1",
            profileId = "prof-1",
            plannedAt = now + 10000L,
            dueAt = now + 10000L,
            status = OccurrenceStatus.TAKEN,
            ringCount = 1,
            actedAt = now + 12000L,
            skipReason = null,
            note = "Taken on time",
            deviceId = "dev-test",
            updatedAt = now + 12000L
        )
        db.occurrenceDao().upsertAll(listOf(occurrence))

        val session = MealSessionEntity("sess-1", "prof-1", MealType.BREAKFAST, "2026-01-01", now, now + 1800000L)
        db.mealSessionDao().insert(session)

        val changeLog = ChangeLogEntity("log-1", "item", "item-1", "created", null, "Blood Pressure Med", now, "dev-test")
        db.changeLogDao().insert(changeLog)

        // 2. Create a photo file
        val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
        val testPhotoFile = File(photosDir, "med_photo.jpg")
        testPhotoFile.writeBytes("SAMPLE_JPEG_BINARY_DATA".toByteArray())

        val photoEntity = ItemPhotoEntity("photo-1", "item-1", testPhotoFile.absolutePath, 0, now, null)
        db.itemPhotoDao().insert(photoEntity)

        // 3. Export encrypted backup
        val backupBaos = ByteArrayOutputStream()
        backupManager.createBackup(backupBaos, passphrase)
        val encryptedBytes = backupBaos.toByteArray()

        assertThat(encryptedBytes.size).isGreaterThan(BackupManager.MAGIC_BYTES.size + 4 + BackupManager.SALT_LENGTH_BYTES + BackupManager.IV_LENGTH_BYTES)

        // Check header magic bytes
        val magicInFile = encryptedBytes.sliceArray(0..3)
        assertThat(magicInFile).isEqualTo(BackupManager.MAGIC_BYTES)

        // 4. Wipe local state: clear DB and delete photo file
        db.changeLogDao().deleteAll()
        db.occurrenceDao().deleteAll()
        db.scheduleDao().deleteAll()
        db.itemPhotoDao().deleteAll()
        db.itemDao().deleteAll()
        db.mealAnchorDao().deleteAll()
        db.mealSessionDao().deleteAll()
        db.profileDao().deleteAll()
        testPhotoFile.delete()

        assertThat(db.profileDao().getAllSync()).isEmpty()
        assertThat(db.itemDao().getAllSync()).isEmpty()
        assertThat(testPhotoFile.exists()).isFalse()

        // 5. Restore from backup
        val restoreBais = ByteArrayInputStream(encryptedBytes)
        val preview = backupManager.restoreBackup(restoreBais, passphrase)

        // 6. Verify restored counts and data integrity
        assertThat(preview.profilesCount).isEqualTo(1)
        assertThat(preview.itemsCount).isEqualTo(1)
        assertThat(preview.schedulesCount).isEqualTo(1)
        assertThat(preview.historyCount).isEqualTo(1)
        assertThat(preview.photosCount).isEqualTo(1)

        val restoredProfiles = db.profileDao().getAllSync()
        assertThat(restoredProfiles).hasSize(1)
        assertThat(restoredProfiles[0].name).isEqualTo("Grandma")

        val restoredItems = db.itemDao().getAllSync()
        assertThat(restoredItems).hasSize(1)
        assertThat(restoredItems[0].name).isEqualTo("Blood Pressure Med")
        assertThat(restoredItems[0].dosageAmount).isEqualTo("20")

        val restoredSchedules = db.scheduleDao().getAllSync()
        assertThat(restoredSchedules).hasSize(1)
        assertThat(restoredSchedules[0].relation).isEqualTo(MealRelation.AFTER)

        val restoredOccs = db.occurrenceDao().getAllSync()
        // Contains the restored historical occurrence plus newly materialized occurrences from reschedule()
        val restoredHistoricOcc = restoredOccs.firstOrNull { it.id == "occ-1" }
        assertThat(restoredHistoricOcc).isNotNull()
        assertThat(restoredHistoricOcc!!.status).isEqualTo(OccurrenceStatus.TAKEN)
        assertThat(restoredHistoricOcc.actedAt).isEqualTo(now + 12000L)

        // Verify photo restored
        assertThat(testPhotoFile.exists()).isTrue()
        assertThat(testPhotoFile.readText()).isEqualTo("SAMPLE_JPEG_BINARY_DATA")
    }

    @Test
    fun restore_failsWithIncorrectPassphrase() {
        runBlocking {
            val passphrase = "CorrectPassword123".toCharArray()
            val wrongPassphrase = "WrongPassword999".toCharArray()

            val profile = ProfileEntity(id = "prof-1", name = "Mom", colorArgb = 0xFF00FF, createdAt = 1000L, updatedAt = 1000L, deletedAt = null)
            db.profileDao().insert(profile)

            val backupBaos = ByteArrayOutputStream()
            backupManager.createBackup(backupBaos, passphrase)
            val encryptedBytes = backupBaos.toByteArray()

            assertThrows(InvalidBackupPassphraseException::class.java) {
                runBlocking {
                    backupManager.restoreBackup(ByteArrayInputStream(encryptedBytes), wrongPassphrase)
                }
            }
        }
    }

    @Test
    fun restore_failsWithCorruptedFile() {
        runBlocking {
            val passphrase = "Passphrase123".toCharArray()
            val profile = ProfileEntity(id = "prof-1", name = "Mom", colorArgb = 0xFF00FF, createdAt = 1000L, updatedAt = 1000L, deletedAt = null)
            db.profileDao().insert(profile)

            val backupBaos = ByteArrayOutputStream()
            backupManager.createBackup(backupBaos, passphrase)
            val encryptedBytes = backupBaos.toByteArray()

            // Corrupt the ciphertext payload
            encryptedBytes[encryptedBytes.size - 5] = (encryptedBytes[encryptedBytes.size - 5] + 1).toByte()

            assertThrows(InvalidBackupPassphraseException::class.java) {
                runBlocking {
                    backupManager.restoreBackup(ByteArrayInputStream(encryptedBytes), passphrase)
                }
            }
        }
    }

    @Test
    fun inspectBackup_returnsAccuratePreviewCountsWithoutModifyingDb() = runBlocking {
        val passphrase = "MyTestPassword".toCharArray()
        val now = System.currentTimeMillis()

        db.profileDao().insert(ProfileEntity(id = "prof-1", name = "Alice", colorArgb = 0xFF0000, createdAt = now, updatedAt = now, deletedAt = null))
        db.profileDao().insert(ProfileEntity(id = "prof-2", name = "Bob", colorArgb = 0x00FF00, createdAt = now, updatedAt = now, deletedAt = null))

        val item1 = ItemEntity(
            id = "i-1", profileId = "prof-1", type = ItemType.MEDICINE, name = "Med A",
            dosageAmount = "1", dosageUnit = "pill", instructions = null, criticality = Criticality.NORMAL,
            graceMinutes = 60, reRingMinutes = 5, minGapMinutes = null,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK, homeZoneId = "UTC", startDate = "2026-01-01",
            createdAt = now, updatedAt = now, deletedAt = null
        )
        val item2 = ItemEntity(
            id = "i-2", profileId = "prof-2", type = ItemType.MEDICINE, name = "Med B",
            dosageAmount = "2", dosageUnit = "capsule", instructions = null, criticality = Criticality.NORMAL,
            graceMinutes = 60, reRingMinutes = 5, minGapMinutes = null,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK, homeZoneId = "UTC", startDate = "2026-01-01",
            createdAt = now, updatedAt = now, deletedAt = null
        )
        db.itemDao().insert(item1)
        db.itemDao().insert(item2)

        val backupBaos = ByteArrayOutputStream()
        backupManager.createBackup(backupBaos, passphrase)
        val encryptedBytes = backupBaos.toByteArray()

        val preview = backupManager.inspectBackup(ByteArrayInputStream(encryptedBytes), passphrase)
        assertThat(preview.profilesCount).isEqualTo(2)
        assertThat(preview.itemsCount).isEqualTo(2)
    }
}

