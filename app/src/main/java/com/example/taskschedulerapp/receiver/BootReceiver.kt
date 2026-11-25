package com.example.taskschedulerapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.taskschedulerapp.data.AppDatabase
import com.example.taskschedulerapp.data.SessionManager
import com.example.taskschedulerapp.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val sessionManager = SessionManager(context)
            val userEmail = sessionManager.getUserEmail()
            
            if (userEmail != null) {
                val database = AppDatabase.getDatabase(context)
                val repository = TaskRepository(database.taskDao(), context)
                repository.setUserEmail(userEmail)
                val scheduler = AlarmScheduler(context)
                
                CoroutineScope(Dispatchers.IO).launch {
                    val tasks = repository.allTasks.first()
                    tasks.forEach { task ->
                        if (!task.isCompleted) {
                            scheduler.scheduleAlarm(task)
                        }
                    }
                }
            }
        }
    }
}
