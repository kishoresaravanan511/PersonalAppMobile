package com.example.taskschedulerapp.data

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel

/**
 * Service to handle AI interactions using Google's Gemini.
 * Note: Uses the API Key provided by the user.
 */
class GeminiService {
    /**
     * BEAKAI UPDATED: Using the provided authenticated Google API key.
     */
    private val apiKey = "".trim()
    
    private var model: GenerativeModel? = null

    init {
        try {
            if (apiKey.isNotBlank()) {
                model = GenerativeModel(
                    modelName = "gemini-1.5-flash",
                    apiKey = apiKey
                )
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "Failed to initialize GenerativeModel: ${e.message}")
        }
    }

    private fun isKeyValid(): Boolean {
        return apiKey.isNotBlank() && model != null
    }

    suspend fun getDailyInterviewQuestion(): String {
        val currentModel = model
        if (!isKeyValid() || currentModel == null) {
            return "BeakAI Info: API Key missing or invalid. Please configure your key in GeminiService.kt."
        }
        
        val prompt = "Provide one Java Spring Boot developer interview question and its concise answer for a fresher."
        return try {
            val response = currentModel.generateContent(prompt)
            response.text ?: "BeakAI was unable to generate a question. Please check back later."
        } catch (e: Exception) {
            Log.e("GeminiService", "Question Fetch Error: ${e.message}", e)
            "Connection error: ${e.localizedMessage}. Verify your internet and API key permissions."
        }
    }

    suspend fun getTechChatResponse(userPrompt: String): String {
        val currentModel = model
        if (!isKeyValid() || currentModel == null) {
            return "BeakAI Chat: Please add your authenticated Google API key in GeminiService.kt."
        }

        return try {
            val response = currentModel.generateContent("You are BeakAI, a technical mentor. Answer this technical query: $userPrompt")
            val responseText = response.text
            if (responseText.isNullOrBlank()) {
                "BeakAI is listening. Please ask your technical question again."
            } else {
                responseText
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "Chat Error Log: ${e.message}", e)
            "BeakAI Connection Error: ${e.localizedMessage}. Please ensure your internet is active."
        }
    }
}
