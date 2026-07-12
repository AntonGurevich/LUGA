package silverbackgarden.example.luga.offline

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single day's activity value that failed to sync to Supabase, queued for retry
 * on the next worker run. Mirrors iOS's OfflineQueueItem Core Data entity
 * (id, uid, date, activityType, value, createdAt).
 */
@Entity(tableName = "offline_queue_item")
data class OfflineQueueItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val date: String,
    val activityType: String, // "steps" | "cycling" | "swimming"
    val value: Double,
    val createdAt: Long = System.currentTimeMillis()
)
