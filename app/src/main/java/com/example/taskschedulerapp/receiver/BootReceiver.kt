package com.example.taskschedulerapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.taskschedulerapp.data.AppDatabase
import com.example.taskschedulerapp.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val database = AppDatabase.getDatabase(context)
            val repository = TaskRepository(database.taskDao(), context)
            val scheduler = AlarmScheduler(context)
            
            CoroutineScope(Dispatchers.IO).launch {
                val tasks = repository.allTasks.first()
                tasks.forEach { task ->
                    scheduler.scheduleAlarm(task)
                }
            }
        }
    }
}
