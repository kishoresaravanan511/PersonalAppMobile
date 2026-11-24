package com.example.taskschedulerapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.taskschedulerapp.data.AppDatabase
import com.example.taskschedulerapp.data.SessionManager
import com.example.taskschedulerapp.data.TaskRepository
import com.example.taskschedulerapp.receiver.DailyQuestionReceiver
import com.example.taskschedulerapp.ui.LoginScreen
import com.example.taskschedulerapp.ui.TaskScreen
import com.example.taskschedulerapp.ui.TaskViewModel
import com.example.taskschedulerapp.ui.TaskViewModelFactory
import com.example.taskschedulerapp.ui.theme.TaskSchedulerAppTheme
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

class MainActivity : FragmentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val sessionManager = SessionManager(applicationContext)
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = TaskRepository(database.taskDao(), applicationContext)
        val factory = TaskViewModelFactory(repository)
        
        DailyQuestionReceiver.scheduleDailyNotification(applicationContext)
        askNotificationPermission()
        
        enableEdgeToEdge()
        setContent {
            var isDarkTheme by remember { mutableStateOf(sessionManager.isDarkTheme()) }
            var currentUserEmail by remember { mutableStateOf(sessionManager.getUserEmail()) }
            val scope = rememberCoroutineScope()

            TaskSchedulerAppTheme(darkTheme = isDarkTheme) {
                val viewModel: TaskViewModel = viewModel(factory = factory)
                
                if (currentUserEmail == null) {
                    LoginScreen(
                        viewModel = viewModel,
                        onLoginSuccess = { email ->
                            sessionManager.saveUserEmail(email)
                            currentUserEmail = email
                        },
                        onBiometricAuth = {
                            showBiometricPrompt(onSuccess = {
                                val lastEmail = sessionManager.getUserEmail() ?: "admin@beakai.app"
                                sessionManager.saveUserEmail(lastEmail)
                                currentUserEmail = lastEmail
                            })
                        }
                    )
                } else {
                    LaunchedEffect(currentUserEmail) {
                        repository.setUserEmail(currentUserEmail!!)
                    }
                    
                    TaskScreen(
                        viewModel = viewModel,
                        userEmail = currentUserEmail,
                        isDarkTheme = isDarkTheme,
                        onThemeToggle = {
                            isDarkTheme = !isDarkTheme
                            sessionManager.saveTheme(isDarkTheme)
                        },
                        onLogout = {
                            scope.launch {
                                repository.clearLocalData()
                                sessionManager.logout()
                                currentUserEmail = null
                            }
                        }
                    )
                }
            }
        }
    }

    private fun showBiometricPrompt(onSuccess: () -> Unit) {
        val executor: Executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(applicationContext, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                    Toast.makeText(applicationContext, "BeakAI Authenticated!", Toast.LENGTH_SHORT).show()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(applicationContext, "Authentication failed", Toast.LENGTH_SHORT).show()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("BeakAI Biometric Login")
            .setSubtitle("Use your fingerprint to access BeakAI assistance")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
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
