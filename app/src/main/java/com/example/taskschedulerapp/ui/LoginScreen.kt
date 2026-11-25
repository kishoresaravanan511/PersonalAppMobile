package com.example.taskschedulerapp.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskschedulerapp.R
import com.example.taskschedulerapp.data.AdminConfig
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    viewModel: TaskViewModel,
    onLoginSuccess: (String) -> Unit,
    onBiometricAuth: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSignUp by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Protection against accidental exit
    BackHandler(enabled = true) {
        showExitConfirm = true
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Exit BeakAI") },
            text = { Text("Do you want to close the application?") },
            confirmButton = { 
                Button(onClick = { (context as? Activity)?.finish() }) { Text("Exit") } 
            },
            dismissButton = { 
                TextButton(onClick = { showExitConfirm = false }) { Text("Cancel") } 
            }
        )
    }

    // Fingerprint is only enabled for admin email
    val isFingerprintEnabled = email.lowercase() == AdminConfig.ADMIN_EMAIL

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.applogo),
                contentDescription = "App Logo",
                modifier = Modifier.size(160.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Welcome to BeakAI assistance",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 20.sp
            )

            Text(
                text = if (isSignUp) "Create your account" else "Sign in to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email ID", color = Color.Gray) },
                placeholder = { Text("user@beakai.app") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Email, null, tint = Color.White) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFE65100),
                    unfocusedBorderColor = Color.DarkGray,
                    cursorColor = Color(0xFFE65100)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Lock, null, tint = Color.White) },
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = null, tint = Color.White)
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFE65100),
                    unfocusedBorderColor = Color.DarkGray,
                    cursorColor = Color(0xFFE65100)
                )
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = Color.Red,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = "Please enter credentials"
                        return@Button
                    }

                    if (!email.lowercase().endsWith("@beakai.app")) {
                        errorMessage = "Invalid email format"
                        return@Button
                    }

                    isLoading = true
                    errorMessage = null

                    scope.launch {
                        if (email.lowercase() == AdminConfig.ADMIN_EMAIL) {
                            if (password == AdminConfig.getAdminPassword()) {
                                // Important: Authenticate admin with Firebase Auth to enable sync
                                val authResult = viewModel.authenticateUser(email.lowercase(), password)
                                isLoading = false
                                if (authResult.isSuccess) {
                                    onLoginSuccess(email.lowercase())
                                } else {
                                    // If owner account isn't in Auth yet, try to register it once
                                    val regResult = viewModel.registerUser(email.lowercase(), password)
                                    if (regResult.isSuccess) {
                                        onLoginSuccess(email.lowercase())
                                    } else {
                                        errorMessage = "Admin Sync Auth Failed. Check Firebase permissions."
                                    }
                                }
                            } else {
                                errorMessage = "Invalid Password"
                                isLoading = false
                            }
                            return@launch
                        }

                        if (isSignUp) {
                            val result = viewModel.registerUser(email.lowercase(), password)
                            isLoading = false
                            if (result.isSuccess) {
                                onLoginSuccess(email.lowercase())
                            } else {
                                errorMessage = result.exceptionOrNull()?.message
                            }
                        } else {
                            val result = viewModel.authenticateUser(email.lowercase(), password)
                            isLoading = false
                            if (result.isSuccess) {
                                onLoginSuccess(email.lowercase())
                            } else {
                                errorMessage = result.exceptionOrNull()?.message
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text(if (isSignUp) "REGISTER" else "LOGIN", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                }
            }

            if (!isSignUp && isFingerprintEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onBiometricAuth,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Use Fingerprint", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = { isSignUp = !isSignUp; errorMessage = null }) {
                Text(
                    if (isSignUp) "Already have an account? Login" else "Don't have an account? Sign Up",
                    color = Color.White
                )
            }
        }
    }
}
