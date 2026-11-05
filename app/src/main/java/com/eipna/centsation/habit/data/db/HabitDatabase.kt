package com.eipna.centsation.habit.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.eipna.centsation.habit.data.dao.TrackerDao
import com.eipna.centsation.habit.data.dao.TrackerLogDao
import com.eipna.centsation.habit.data.entity.TrackerEntity
import com.eipna.centsation.habit.data.entity.TrackerLogEntity

@Database(
    entities = [TrackerEntity::class, TrackerLogEntity::class],
    version = 2,
    exportSchema = false
)
abstract class HabitDatabase : RoomDatabase() {

    abstract fun trackerDao(): TrackerDao
    abstract fun trackerLogDao(): TrackerLogDao

    companion object {
        @Volatile
        private var INSTANCE: HabitDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trackers_isActive ON trackers(isActive)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tracker_logs_date ON tracker_logs(date)")
            }
        }

        fun getInstance(context: Context): HabitDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HabitDatabase::class.java,
                    "supercenter_habit.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
