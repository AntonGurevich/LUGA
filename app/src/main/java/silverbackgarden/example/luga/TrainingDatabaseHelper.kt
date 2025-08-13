package silverbackgarden.example.luga

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues

/**
 * Database helper class for managing training data in SQLite database.
 * This class extends SQLiteOpenHelper to handle database creation, upgrades,
 * and provides methods for CRUD operations on training records.
 * 
 * @param context The application context used to create the database
 */
class TrainingDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        // Database version - increment this when making schema changes
        private const val DATABASE_VERSION = 1
        // Name of the database file
        private const val DATABASE_NAME = "training.db"
        // Name of the table storing training data
        private const val TABLE_NAME = "TrainingData"
        // Column names for the training data table
        private const val COLUMN_ID = "id"           // Primary key, auto-incrementing
        private const val COLUMN_NAME = "name"       // Training session name/description
        private const val COLUMN_DURATION = "duration" // Training duration in minutes
    }

    /**
     * Called when the database is created for the first time.
     * Creates the TrainingData table with the specified schema.
     * 
     * @param db The database instance to create tables in
     */
    override fun onCreate(db: SQLiteDatabase) {
        // SQL statement to create the TrainingData table
        val createTable = ("CREATE TABLE $TABLE_NAME ("
                + "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "$COLUMN_NAME TEXT,"
                + "$COLUMN_DURATION INTEGER)")
        db.execSQL(createTable)
    }

    /**
     * Called when the database needs to be upgraded to a newer version.
     * Currently drops the existing table and recreates it.
     * 
     * @param db The database instance to upgrade
     * @param oldVersion The previous database version
     * @param newVersion The new database version
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Drop existing table and recreate it (simple upgrade strategy)
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    /**
     * Adds a new training record to the database.
     * 
     * @param name The name or description of the training session
     * @param duration The duration of the training session in minutes
     * @return The row ID of the newly inserted record, or -1 if insertion failed
     */
    fun addTraining(name: String, duration: Int): Long {
        val db = this.writableDatabase
        // Create ContentValues object to hold the data to be inserted
        val contentValues = ContentValues().apply {
            put(COLUMN_NAME, name)
            put(COLUMN_DURATION, duration)
        }
        // Insert the training data and return the row ID
        return db.insert(TABLE_NAME, null, contentValues)
    }

    /**
     * Retrieves all training records from the database.
     * 
     * @return A list of Training objects containing all training data
     */
    fun getAllTrainings(): List<Training> {
        val trainingList = ArrayList<Training>()
        val db = this.readableDatabase
        // Query all records from the TrainingData table
        val cursor = db.query(TABLE_NAME, null, null, null, null, null, null)

        // Iterate through the cursor to extract training data
        with(cursor) {
            while (moveToNext()) {
                // Create Training object from cursor data
                val training = Training(
                    getInt(getColumnIndexOrThrow(COLUMN_ID)),
                    getString(getColumnIndexOrThrow(COLUMN_NAME)),
                    getInt(getColumnIndexOrThrow(COLUMN_DURATION))
                )
                trainingList.add(training)
            }
        }
        // Always close the cursor to free resources
        cursor.close()
        return trainingList
    }
}