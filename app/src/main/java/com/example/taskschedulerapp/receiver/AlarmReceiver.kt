package com.example.taskschedulerapp.receiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.taskschedulerapp.MainActivity
import com.example.taskschedulerapp.data.AppDatabase
import com.example.taskschedulerapp.data.TaskFrequency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra("TASK_ID", -1)
        if (taskId == -1) return

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            val task = db.taskDao().getTaskById(taskId)
            
            if (task != null && !task.isCompleted) {
                val now = System.currentTimeMillis()
                val scheduledTime = task.scheduledTimestamp
                
                // BUG FIX: "previous day tasks will notify on next day"
                // If the alarm is triggered more than 5 minutes late (e.g. catch-up after reboot), 
                // and it's not supposed to fire now, skip it.
                // We allow a small window (5 mins) for system delays.
                if (now - scheduledTime > 300000) { // 5 minutes threshold
                    Log.d("AlarmReceiver", "Skipping stale alarm for: ${task.title}. Scheduled: $scheduledTime, Now: $now")
                    
                    // Still need to reschedule repeating tasks if they missed their window
                    if (task.frequency != TaskFrequency.ONCE) {
                        AlarmScheduler(context).scheduleAlarm(task)
                    }
                    return@launch
                }

                launch(Dispatchers.Main) {
                    showNotification(context, task.id, task.title)
                }
                
                // Reschedule if repeating
                if (task.frequency != TaskFrequency.ONCE) {
                    AlarmScheduler(context).scheduleAlarm(task)
                }
            }
        }
    }

    private fun showNotification(context: Context, taskId: Int, taskTitle: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "task_alarm_channel"
        
        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(channelId, "Task Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val serviceIntent = Intent(context, AlarmService::class.java)
        context.startForegroundService(serviceIntent)

        val stopIntent = Intent(context, AlarmService::class.java).apply {
            action = "STOP_ALARM"
        }
        val stopPendingIntent = PendingIntent.getService(
            context, taskId + 1000, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, taskId, fullScreenIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Task Alert")
            .setContentText("Time for: $taskTitle")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()
            
        notificationManager.notify(taskId, notification)
    }
}
