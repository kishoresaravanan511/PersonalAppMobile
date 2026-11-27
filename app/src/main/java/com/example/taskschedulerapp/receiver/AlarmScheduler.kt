package com.example.taskschedulerapp.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.taskschedulerapp.data.Task
import com.example.taskschedulerapp.data.TaskFrequency
import java.util.*

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAlarm(task: Task): Long {
        // Only skip scheduling for completed one-time tasks.
        if (task.frequency == TaskFrequency.ONCE && task.isCompleted) return 0L

        val now = Calendar.getInstance()
        val calendar = Calendar.getInstance().apply {
            // Source of truth for time components
            set(Calendar.HOUR_OF_DAY, task.hour)
            set(Calendar.MINUTE, task.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // Start date components
            task.year?.let { set(Calendar.YEAR, it) }
            task.month?.let { set(Calendar.MONTH, it) }
            task.dayOfMonth?.let { set(Calendar.DAY_OF_MONTH, it) }

            if (task.frequency == TaskFrequency.WEEKLY) {
                task.dayOfWeek?.let { set(Calendar.DAY_OF_WEEK, it) }
            }
        }

        // For repeating tasks, if the time is in the past, find the next occurrence in the future
        if (calendar.before(now)) {
            if (task.frequency == TaskFrequency.ONCE) {
                Log.d("AlarmScheduler", "Skipping past ONCE task: ${task.title}")
                return 0L
            } else {
                // Find next valid occurrence
                while (calendar.before(now)) {
                    when (task.frequency) {
                        TaskFrequency.DAILY -> calendar.add(Calendar.DATE, 1)
                        TaskFrequency.WEEKLY -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
                        TaskFrequency.MONTHLY -> calendar.add(Calendar.MONTH, 1)
                        else -> break
                    }
                }
            }
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("TASK_TITLE", task.title)
            putExtra("TASK_ID", task.id)
            putExtra("USER_EMAIL", task.userEmail)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    val alarmClockInfo = AlarmManager.AlarmClockInfo(calendar.timeInMillis, pendingIntent)
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                    Log.d("AlarmScheduler", "Scheduled Accurate Alarm for ${task.title} at ${calendar.time}")
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                    Log.d("AlarmScheduler", "Scheduled Inexact Alarm for ${task.title} at ${calendar.time}")
                }
            } else {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(calendar.timeInMillis, pendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            }
        } catch (e: Exception) {
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } catch (e2: Exception) {
                Log.e("AlarmScheduler", "Failed to schedule alarm: ${e2.message}")
            }
        }
        return calendar.timeInMillis
    }

    fun cancelAlarm(task: Task) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d("AlarmScheduler", "Cancelled Alarm for ${task.title}")
    }
}
