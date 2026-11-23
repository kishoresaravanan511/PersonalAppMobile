package com.example.taskschedulerapp.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.taskschedulerapp.data.AppDatabase
import com.example.taskschedulerapp.data.GeminiService
import com.example.taskschedulerapp.data.Question
import com.example.taskschedulerapp.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class DailyQuestionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val database = AppDatabase.getDatabase(context)
        val repository = TaskRepository(database.taskDao(), context)
        val geminiService = GeminiService()
        
        CoroutineScope(Dispatchers.IO).launch {
            val questionAndAnswer = geminiService.getDailyInterviewQuestion()
            
            // Parse Q&A (Simple split for demo, usually Gemini returns structured text)
            val parts = questionAndAnswer.split("Answer:", ignoreCase = true)
            val qText = parts.getOrNull(0)?.replace("Question:", "", true)?.trim() ?: "Daily Topic"
            val aText = parts.getOrNull(1)?.trim() ?: questionAndAnswer
            
            // Save to DB so user can see it in the app
            repository.insertQuestion(Question(question = qText, answer = aText))
            
            showNotification(context, qText)
        }
    }

    private fun showNotification(context: Context, content: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "daily_question_channel"
        
        val channel = NotificationChannel(
            channelId,
            "Daily Interview Questions",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)
        
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Daily Spring Boot Question")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
            
        notificationManager.notify(1001, notification)
    }

    companion object {
        fun scheduleDailyNotification(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val intent = Intent(context, DailyQuestionReceiver::class.java)
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 6)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DATE, 1)
                }
            }

            alarmManager.setRepeating(
                android.app.AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                android.app.AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        }
    }
}
