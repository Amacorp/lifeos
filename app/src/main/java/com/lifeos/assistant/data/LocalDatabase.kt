package com.lifeos.assistant.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * LocalDatabase - Room database for storing reminders, notes, and app data
 * 
 * Features:
 * - Reminders storage
 * - Notes storage
 * - Settings persistence
 * - WAL mode for better performance
 */
@Database(
    entities = [Reminder::class, Note::class],
    version = 1,
    exportSchema = false
)
abstract class LocalDatabase : RoomDatabase() {

    abstract fun reminderDao(): ReminderDao
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: LocalDatabase? = null

        fun getDatabase(context: Context): LocalDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LocalDatabase::class.java,
                    "lifeos_database"
                )
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING) // WAL mode for better performance
                .fallbackToDestructiveMigration() // Simplified for demo
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * Reminder entity
 */
@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    
    @ColumnInfo(name = "content")
    val content: String,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    
    @ColumnInfo(name = "trigger_at")
    val triggerAt: Long,
    
    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false
)

/**
 * Note entity
 */
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    
    @ColumnInfo(name = "content")
    val content: String,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = createdAt,
    
    @ColumnInfo(name = "category")
    val category: String = "general"
)

/**
 * Reminder DAO
 */
@Dao
interface ReminderDao {
    
    @Query("SELECT * FROM reminders ORDER BY created_at DESC")
    fun getAllRemindersFlow(): Flow<List<Reminder>>
    
    @Query("SELECT * FROM reminders ORDER BY created_at DESC")
    suspend fun getAllReminders(): List<Reminder>
    
    @Query("SELECT * FROM reminders WHERE is_completed = 0 ORDER BY trigger_at ASC")
    suspend fun getPendingReminders(): List<Reminder>
    
    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getReminderById(id: String): Reminder?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: Reminder)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reminders: List<Reminder>)
    
    @Update
    suspend fun update(reminder: Reminder)
    
    @Delete
    suspend fun delete(reminder: Reminder)
    
    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: String)
    
    @Query("DELETE FROM reminders WHERE is_completed = 1")
    suspend fun deleteCompletedReminders()
    
    @Query("DELETE FROM reminders")
    suspend fun deleteAllReminders()
    
    @Query("SELECT COUNT(*) FROM reminders WHERE is_completed = 0")
    suspend fun getPendingReminderCount(): Int
    
    @Query("UPDATE reminders SET is_completed = 1 WHERE id = :id")
    suspend fun markAsCompleted(id: String)
}

/**
 * Note DAO
 */
@Dao
interface NoteDao {
    
    @Query("SELECT * FROM notes ORDER BY created_at DESC")
    fun getAllNotesFlow(): Flow<List<Note>>
    
    @Query("SELECT * FROM notes ORDER BY created_at DESC")
    suspend fun getAllNotes(): List<Note>
    
    @Query("SELECT * FROM notes WHERE category = :category ORDER BY created_at DESC")
    suspend fun getNotesByCategory(category: String): List<Note>
    
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: String): Note?
    
    @Query("SELECT * FROM notes WHERE content LIKE '%' || :query || '%' ORDER BY created_at DESC")
    suspend fun searchNotes(query: String): List<Note>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<Note>)
    
    @Update
    suspend fun update(note: Note)
    
    @Delete
    suspend fun delete(note: Note)
    
    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)
    
    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()
    
    @Query("SELECT COUNT(*) FROM notes")
    suspend fun getNoteCount(): Int
    
    @Query("SELECT DISTINCT category FROM notes")
    suspend fun getAllCategories(): List<String>
}