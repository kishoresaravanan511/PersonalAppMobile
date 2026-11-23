package com.example.taskschedulerapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.taskschedulerapp.data.AppDatabase
import com.example.taskschedulerapp.data.TaskRepository
import com.example.taskschedulerapp.receiver.DailyQuestionReceiver
import com.example.taskschedulerapp.ui.TaskScreen
import com.example.taskschedulerapp.ui.TaskViewModel
import com.example.taskschedulerapp.ui.TaskViewModelFactory
import com.example.taskschedulerapp.ui.theme.TaskSchedulerAppTheme

class MainActivity : ComponentActivity() {

    // Launcher for notification permission (Android 13+)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize dependencies
        val database = AppDatabase.getDatabase(applicationContext)
        
        // TaskRepository requires context for Firebase sync and Alarm scheduling logic
        val repository = TaskRepository(database.taskDao(), applicationContext)
        val factory = TaskViewModelFactory(repository)
        
        // Schedule Daily Interview Question at 6 AM
        DailyQuestionReceiver.scheduleDailyNotification(applicationContext)
        
        // Request notification permissions for Android 13+
        askNotificationPermission()
        
        enableEdgeToEdge()
        setContent {
            TaskSchedulerAppTheme {
                // Initialize ViewModel with custom factory
                val viewModel: TaskViewModel = viewModel(factory = factory)
                // Show the main Task Planner screen with Daily/Weekly/Monthly planning
                TaskScreen(viewModel = viewModel)
            }
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
