package com.example.taskschedulerapp.receiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.taskschedulerapp.MainActivity
import com.example.taskschedulerapp.R
import com.example.taskschedulerapp.data.AppDatabase
import com.example.taskschedulerapp.data.TaskFrequency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra("TASK_ID", -1)
        val taskTitle = intent.getStringExtra("TASK_TITLE") ?: "Scheduled Task"
        val isReminder = intent.getBooleanExtra("IS_REMINDER", false)
        
        Log.d("TaskApp_Alarm", "Alarm triggered for: $taskTitle, Reminder: $isReminder")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = if (isReminder) "task_reminder_channel" else "task_alarm_channel"
        
        if (notificationManager.getNotificationChannel(channelId) == null) {
            val importance = if (isReminder) NotificationManager.IMPORTANCE_DEFAULT else NotificationManager.IMPORTANCE_HIGH
            val name = if (isReminder) "Task Reminders" else "Task Alarms"
            val channel = NotificationChannel(channelId, name, importance).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                if (!isReminder) {
                    setSound(null, null) 
                    enableLights(true)
                }
            }
            notificationManager.createNotificationChannel(channel)
        }

        if (isReminder) {
            // Show a simple reminder notification 5 minutes before
            val contentMessage = "Upcoming: '$taskTitle' starts in 5 minutes."
            val notification = NotificationCompat.Builder(context, channelId)
                .setContentTitle("Task Starting Soon")
                .setContentText(contentMessage)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(taskId + 10000, notification)
            
            // Allow a short vibration for reminder
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            return
        }

        // --- Handle Main Alarm ---
        
        // 1. Start Alarm Service to play the sound
        val serviceIntent = Intent(context, AlarmService::class.java)
        context.startForegroundService(serviceIntent)

        // 2. Show High-Priority Heads-up Notification
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

        val contentMessage = "Time for: $taskTitle"

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Task Alert")
            .setContentText(contentMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentMessage))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setAutoCancel(true)
            .setOngoing(true)
            .setVibrate(longArrayOf(0, 500, 500, 500))
            .build()
            
        notificationManager.notify(taskId, notification)

        // 3. Reschedule if repeating
        if (taskId != -1) {
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getDatabase(context)
                val task = db.taskDao().getTaskById(taskId)
                if (task != null && task.frequency != TaskFrequency.ONCE) {
                    AlarmScheduler(context).scheduleAlarm(task)
                }
            }
        }
    }
}
