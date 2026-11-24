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
    
    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey
    )

    private fun isKeyValid(): Boolean {
        // Return true as long as the key is not blank.
        return apiKey.isNotBlank()
    }

    suspend fun getDailyInterviewQuestion(): String {
        if (!isKeyValid()) {
            return "BeakAI Info: API Key missing. Please configure your key in GeminiService.kt."
        }
        
        val prompt = "Provide one Java Spring Boot developer interview question and its concise answer for a fresher."
        return try {
            val response = model.generateContent(prompt)
            response.text ?: "BeakAI was unable to generate a question. Please check back later."
        } catch (e: Exception) {
            Log.e("GeminiService", "Question Fetch Error: ${e.message}", e)
            "Connection error: ${e.localizedMessage}. Verify your internet and API key permissions."
        }
    }

    suspend fun getTechChatResponse(userPrompt: String): String {
        if (!isKeyValid()) {
            return "BeakAI Chat: Please add your authenticated Google API key in GeminiService.kt."
        }

        return try {
            val response = model.generateContent("You are BeakAI, a technical mentor. Answer this technical query: $userPrompt")
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
