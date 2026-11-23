package com.example.taskschedulerapp.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.taskschedulerapp.data.Task
import com.example.taskschedulerapp.data.TaskFrequency
import java.util.*

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAlarm(task: Task) {
        if (task.isCompleted) return

        val now = Calendar.getInstance()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, task.hour)
            set(Calendar.MINUTE, task.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            when (task.frequency) {
                TaskFrequency.WEEKLY -> task.dayOfWeek?.let { set(Calendar.DAY_OF_WEEK, it) }
                TaskFrequency.MONTHLY -> task.dayOfMonth?.let { set(Calendar.DAY_OF_MONTH, it) }
                else -> {}
            }
        }

        // If the calculated time is in the past
        if (calendar.before(now)) {
            if (task.frequency == TaskFrequency.ONCE) {
                Log.d("AlarmScheduler", "Skipping past ONCE task: ${task.title}")
                return
            } else {
                // Move to the next valid occurrence for repeating tasks
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
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(calendar.timeInMillis, pendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d("AlarmScheduler", "Scheduled precise alarm for ${task.title} at ${calendar.time}")
        } catch (e: SecurityException) {
            Log.e("AlarmScheduler", "Permission issue: ${e.message}")
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }
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
    }
}
