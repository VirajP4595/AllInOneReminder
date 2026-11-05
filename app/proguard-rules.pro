# Proguard rules for AllInOneReminder

# Room keep rules
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# Hilt keep rules
-keep class * extends androidx.hilt.lifecycle.ViewModelAssistedFactory { *; }
-keep interface dagger.hilt.** { *; }
-keep class * implements dagger.hilt.** { *; }

# SQLCipher keep rules
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**

# Kotlin Coroutines
-keep class kotlinx.coroutines.** { *; }
