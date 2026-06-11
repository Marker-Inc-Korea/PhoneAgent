package ai.markr.phoneagent.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RunHistoryDao {
    @Insert
    suspend fun insert(record: RunRecord): Long

    @Query("SELECT * FROM run_records ORDER BY createdAt DESC LIMIT 50")
    fun recent(): Flow<List<RunRecord>>

    @Query("DELETE FROM run_records")
    suspend fun clear()
}
