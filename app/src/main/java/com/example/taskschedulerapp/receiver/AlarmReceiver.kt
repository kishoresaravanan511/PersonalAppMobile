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
import com.example.taskschedulerapp.R
import com.example.taskschedulerapp.data.AppDatabase
import com.example.taskschedulerapp.data.TaskFrequency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra("TASK_ID", -1)
        val taskTitle = intent.getStringExtra("TASK_TITLE") ?: "Job Preparation Task"
        
        Log.d("BeakAI_Alarm", "Alarm triggered for: $taskTitle")

        // 1. Start Alarm Service to play the motivational sound
        val serviceIntent = Intent(context, AlarmService::class.java)
        context.startForegroundService(serviceIntent)

        // 2. Show High-Priority Heads-up Notification (Popup)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "beakai_urgent_channel"
        
        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId,
                "BeakAI Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                setSound(null, null) 
                setShowBadge(true)
                // This is crucial for the "Popup" behavior (Heads-up notification)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
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

        // SPECIFIC MESSAGE REQUESTED BY USER
        val contentMessage = "It's time for your scheduled goal: $taskTitle. Stay focused and complete this task to stay on track!"

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("BeakAI Focus Alert!")
            .setContentText(contentMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentMessage))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true) // Makes it pop up over other apps
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Alarm", stopPendingIntent)
            .setAutoCancel(true)
            .setOngoing(true)
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
