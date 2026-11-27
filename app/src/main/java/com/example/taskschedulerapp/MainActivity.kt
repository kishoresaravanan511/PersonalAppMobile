package com.example.taskschedulerapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val sessionManager = SessionManager(applicationContext)
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = TaskRepository(database.taskDao(), applicationContext)
        val factory = TaskViewModelFactory(repository, sessionManager)
        
        DailyQuestionReceiver.scheduleDailyNotification(applicationContext)
        
        enableEdgeToEdge()
        setContent {
            var isDarkTheme by remember { mutableStateOf(sessionManager.isDarkTheme()) }
            var currentUserEmail by remember { mutableStateOf(sessionManager.getUserEmail()) }
            val scope = rememberCoroutineScope()

            // Safe permission request after UI is initialized
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= 33) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        // Using ActivityCompat directly with a safe request code (101) to avoid the 16-bit limit crash
                        ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                    }
                }
            }

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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notifications are disabled. Task alerts won't work.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showBiometricPrompt(onSuccess: () -> Unit) {
        val executor: Executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(applicationContext, "Auth Error: $errString", Toast.LENGTH_SHORT).show()
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
}
