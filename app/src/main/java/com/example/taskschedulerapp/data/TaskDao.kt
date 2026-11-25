package com.example.taskschedulerapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    // Task Operations
    @Query("SELECT * FROM tasks WHERE userEmail = :email ORDER BY hour, minute")
    fun getAllTasks(email: String): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE userEmail = :email")
    suspend fun getAllTasksSync(email: String): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<Task>)

    @Update
    suspend fun updateTask(task: Task)

    @Update
    suspend fun updateTasks(tasks: List<Task>)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): Task?
    
    @Query("DELETE FROM tasks WHERE userEmail = :email")
    suspend fun deleteAllTasks(email: String)

    // Question Operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestion(question: Question): Long

    @Query("SELECT * FROM daily_questions ORDER BY timestamp DESC")
    fun getAllQuestions(): Flow<List<Question>>

    @Query("SELECT * FROM daily_questions")
    suspend fun getAllQuestionsSync(): List<Question>

    // DSA Pattern Operations
    @Query("SELECT * FROM dsa_patterns WHERE userEmail = :email")
    fun getAllDsaPatterns(email: String): Flow<List<DsaPattern>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDsaPattern(pattern: DsaPattern)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDsaPatterns(patterns: List<DsaPattern>)

    @Update
    suspend fun updateDsaPattern(pattern: DsaPattern)

    @Query("DELETE FROM dsa_patterns WHERE userEmail = :email")
    suspend fun deleteAllDsaPatterns(email: String)
}
