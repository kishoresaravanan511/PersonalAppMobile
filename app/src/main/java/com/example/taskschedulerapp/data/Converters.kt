package com.example.taskschedulerapp.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromFrequency(value: TaskFrequency): String {
        return value.name
    }

    @TypeConverter
    fun toFrequency(value: String): TaskFrequency {
        return TaskFrequency.valueOf(value)
    }

    @TypeConverter
    fun fromCategory(value: TaskCategory): String {
        return value.name
    }

    @TypeConverter
    fun toCategory(value: String): TaskCategory {
        return TaskCategory.valueOf(value)
    }

    @TypeConverter
    fun fromStatus(value: TaskStatus): String {
        return value.name
    }

    @TypeConverter
    fun toStatus(value: String): TaskStatus {
        return TaskStatus.valueOf(value)
    }
}
