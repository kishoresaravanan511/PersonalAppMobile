package com.example.taskschedulerapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.database.IgnoreExtraProperties

enum class TaskFrequency {
    DAILY, WEEKLY, MONTHLY, ONCE
}

enum class TaskCategory {
    TASK, MEETING, STUDY, TOPIC, DSA, SYSTEM_DESIGN, FRONTEND, PROJECTS
}

enum class TaskStatus {
    ACTIVE, ACHIEVED, EXPIRED
}

@IgnoreExtraProperties
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val firebaseId: String? = null,
    val userEmail: String = "",
    val title: String = "",
    val description: String = "",
    val priority: Int = 1, // 1: Low, 2: Medium, 3: High
    val frequency: TaskFrequency = TaskFrequency.DAILY,
    val category: TaskCategory = TaskCategory.TASK,
    val hour: Int = 0,
    val minute: Int = 0,
    val dayOfWeek: Int? = null,
    val dayOfMonth: Int? = null,
    val month: Int? = null, // 0-11
    val year: Int? = null,
    val isCompleted: Boolean = false,
    val lastCompletedTimestamp: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = 0L,
    val scheduledTimestamp: Long = 0L,
    val status: TaskStatus = TaskStatus.ACTIVE
)

@IgnoreExtraProperties
@Entity(tableName = "dsa_patterns", primaryKeys = ["id", "userEmail"])
data class DsaPattern(
    val id: String = "",
    val userEmail: String = "",
    val category: String = "",
    val patternName: String = "",
    val isCompleted: Boolean = false
)
