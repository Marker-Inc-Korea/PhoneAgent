package ai.markr.phoneagent.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [RunRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun runHistoryDao(): RunHistoryDao
}
