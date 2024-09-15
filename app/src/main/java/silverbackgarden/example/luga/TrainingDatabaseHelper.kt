package silverbackgarden.example.luga

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues


class TrainingDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "training.db"
        private const val TABLE_NAME = "TrainingData"
        private const val COLUMN_ID = "id"
        private const val COLUMN_NAME = "name"
        private const val COLUMN_DURATION = "duration"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = ("CREATE TABLE $TABLE_NAME ("
                + "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "$COLUMN_NAME TEXT,"
                + "$COLUMN_DURATION INTEGER)")
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun addTraining(name: String, duration: Int): Long {
        val db = this.writableDatabase
        val contentValues = ContentValues().apply {
            put(COLUMN_NAME, name)
            put(COLUMN_DURATION, duration)
        }
        return db.insert(TABLE_NAME, null, contentValues)
    }

    fun getAllTrainings(): List<Training> {
        val trainingList = ArrayList<Training>()
        val db = this.readableDatabase
        val cursor = db.query(TABLE_NAME, null, null, null, null, null, null)

        with(cursor) {
            while (moveToNext()) {
                val training = Training(
                    getInt(getColumnIndexOrThrow(COLUMN_ID)),
                    getString(getColumnIndexOrThrow(COLUMN_NAME)),
                    getInt(getColumnIndexOrThrow(COLUMN_DURATION))
                )
                trainingList.add(training)
            }
        }
        cursor.close()
        return trainingList
    }


}