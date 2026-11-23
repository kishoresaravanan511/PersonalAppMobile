package com.example.taskschedulerapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    // Task Operations
    @Query("SELECT * FROM tasks ORDER BY hour, minute")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksSync(): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): Task?
    
    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()

    // Question Operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestion(question: Question): Long

    @Query("SELECT * FROM daily_questions ORDER BY timestamp DESC")
    fun getAllQuestions(): Flow<List<Question>>

    @Query("SELECT * FROM daily_questions")
    suspend fun getAllQuestionsSync(): List<Question>

    // DSA Pattern Operations
    @Query("SELECT * FROM dsa_patterns")
    fun getAllDsaPatterns(): Flow<List<DsaPattern>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDsaPattern(pattern: DsaPattern)

    @Update
    suspend fun updateDsaPattern(pattern: DsaPattern)
}
