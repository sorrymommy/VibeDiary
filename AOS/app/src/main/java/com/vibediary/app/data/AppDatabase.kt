package com.vibediary.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries WHERE deleted = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE id = :id")
    fun observe(id: String): Flow<Entry?>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun get(id: String): Entry?

    @Query("SELECT * FROM entries WHERE dirty = 1")
    suspend fun dirty(): List<Entry>

    @Upsert
    suspend fun upsert(entry: Entry)

    /** Clears the dirty flag only if nothing changed while the upload was in flight. */
    @Query("UPDATE entries SET dirty = 0, driveJsonId = :driveId WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markSynced(id: String, updatedAt: Long, driveId: String)
}

class Converters {
    @TypeConverter
    fun tagsToString(tags: List<String>): String = tags.joinToString(SEP)

    @TypeConverter
    fun stringToTags(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split(SEP)

    private companion object {
        const val SEP = "\u001F"
    }
}

@Database(entities = [Entry::class], version = 2, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao

    companion object {
        /** v2: user-editable place name. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN stationLabel TEXT")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "vibediary.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
