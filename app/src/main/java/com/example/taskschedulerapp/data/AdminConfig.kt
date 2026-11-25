package com.example.taskschedulerapp.data

/**
 * Configuration for Admin access using the external password provider.
 */
object AdminConfig {
    const val ADMIN_EMAIL = "admin@beakai.app"
    
    fun getAdminPassword(): String {
        return AdminPasswordProvider.ADMIN_PASSWORD
    }
}
