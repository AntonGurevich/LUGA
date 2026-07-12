package silverbackgarden.example.luga.offline

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [OfflineQueueItem::class], version = 1, exportSchema = false)
abstract class OfflineQueueDatabase : RoomDatabase() {
    abstract fun offlineQueueDao(): OfflineQueueDao

    companion object {
        @Volatile private var instance: OfflineQueueDatabase? = null

        fun getInstance(context: Context): OfflineQueueDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OfflineQueueDatabase::class.java,
                    "offline_queue.db"
                ).build().also { instance = it }
            }
        }
    }
}
