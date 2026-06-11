package ai.markr.phoneagent.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "run_records")
data class RunRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val task: String,
    val outcome: String,
    val answer: String,
    val stepCount: Int,
    val createdAt: Long,
)
