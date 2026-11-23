package com.example.taskschedulerapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
@Entity(tableName = "daily_questions")
data class Question(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val firebaseId: String? = null,
    val question: String = "",
    val answer: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
