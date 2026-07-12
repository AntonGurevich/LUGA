package silverbackgarden.example.luga.offline

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OfflineQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<OfflineQueueItem>)

    @Query("SELECT * FROM offline_queue_item WHERE uid = :uid AND activityType = :activityType")
    suspend fun getByActivityType(uid: String, activityType: String): List<OfflineQueueItem>

    @Query("SELECT * FROM offline_queue_item WHERE uid = :uid")
    suspend fun getAllForUser(uid: String): List<OfflineQueueItem>

    @Query("SELECT COUNT(*) FROM offline_queue_item WHERE uid = :uid")
    suspend fun countForUser(uid: String): Int

    @Delete
    suspend fun delete(items: List<OfflineQueueItem>)
}
