package app.allinonereminder.data.di

import android.content.Context
import androidx.room.Room
import app.allinonereminder.data.local.AppDatabase
import app.allinonereminder.data.local.ChangeLogWriter
import app.allinonereminder.data.local.dao.ChangeLogDao
import app.allinonereminder.data.local.dao.ItemDao
import app.allinonereminder.data.local.dao.ItemPhotoDao
import app.allinonereminder.data.local.dao.MealAnchorDao
import app.allinonereminder.data.local.dao.MealSessionDao
import app.allinonereminder.data.local.dao.OccurrenceDao
import app.allinonereminder.data.local.dao.ProfileDao
import app.allinonereminder.data.local.dao.ScheduleDao
import app.allinonereminder.data.repository.ChangeLogRepository
import app.allinonereminder.data.repository.ItemRepository
import app.allinonereminder.data.repository.MealAnchorRepository
import app.allinonereminder.data.repository.MealSessionRepository
import app.allinonereminder.data.repository.OccurrenceRepository
import app.allinonereminder.data.repository.ProfileRepository
import app.allinonereminder.data.repository.ScheduleRepository
import app.allinonereminder.data.repository.SettingsRepository
import app.allinonereminder.data.repository.dataStore
import app.allinonereminder.data.security.DatabaseKeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabaseKeyManager(
        @ApplicationContext context: Context
    ): DatabaseKeyManager {
        return DatabaseKeyManager(context)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        keyManager: DatabaseKeyManager
    ): AppDatabase {
        val passphrase = keyManager.getOrCreatePassphrase()
        val factory = SupportFactory(passphrase)
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "allinonereminder.db"
        )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideProfileDao(db: AppDatabase): ProfileDao = db.profileDao()

    @Provides
    fun provideMealAnchorDao(db: AppDatabase): MealAnchorDao = db.mealAnchorDao()

    @Provides
    fun provideItemDao(db: AppDatabase): ItemDao = db.itemDao()

    @Provides
    fun provideItemPhotoDao(db: AppDatabase): ItemPhotoDao = db.itemPhotoDao()

    @Provides
    fun provideScheduleDao(db: AppDatabase): ScheduleDao = db.scheduleDao()

    @Provides
    fun provideOccurrenceDao(db: AppDatabase): OccurrenceDao = db.occurrenceDao()

    @Provides
    fun provideMealSessionDao(db: AppDatabase): MealSessionDao = db.mealSessionDao()

    @Provides
    fun provideChangeLogDao(db: AppDatabase): ChangeLogDao = db.changeLogDao()

    @Provides
    @Singleton
    fun provideChangeLogWriter(dao: ChangeLogDao): ChangeLogWriter = ChangeLogWriter(dao)

    @Provides
    @Singleton
    fun provideProfileRepository(
        profileDao: ProfileDao,
        mealAnchorDao: MealAnchorDao,
        changeLogWriter: ChangeLogWriter
    ): ProfileRepository = ProfileRepository(profileDao, mealAnchorDao, changeLogWriter)

    @Provides
    @Singleton
    fun provideMealAnchorRepository(
        mealAnchorDao: MealAnchorDao,
        changeLogWriter: ChangeLogWriter
    ): MealAnchorRepository = MealAnchorRepository(mealAnchorDao, changeLogWriter)

    @Provides
    @Singleton
    fun provideItemRepository(
        itemDao: ItemDao,
        itemPhotoDao: ItemPhotoDao,
        changeLogWriter: ChangeLogWriter
    ): ItemRepository = ItemRepository(itemDao, itemPhotoDao, changeLogWriter)

    @Provides
    @Singleton
    fun provideScheduleRepository(
        scheduleDao: ScheduleDao,
        changeLogWriter: ChangeLogWriter
    ): ScheduleRepository = ScheduleRepository(scheduleDao, changeLogWriter)

    @Provides
    @Singleton
    fun provideOccurrenceRepository(
        occurrenceDao: OccurrenceDao
    ): OccurrenceRepository = OccurrenceRepository(occurrenceDao)

    @Provides
    @Singleton
    fun provideMealSessionRepository(
        mealSessionDao: MealSessionDao
    ): MealSessionRepository = MealSessionRepository(mealSessionDao)

    @Provides
    @Singleton
    fun provideChangeLogRepository(
        changeLogDao: ChangeLogDao
    ): ChangeLogRepository = ChangeLogRepository(changeLogDao)

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context
    ): SettingsRepository = SettingsRepository(context.dataStore)
}

