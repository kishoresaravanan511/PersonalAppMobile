package com.example.taskschedulerapp.data

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig

/**
 * Service to handle AI interactions using Google's Gemini.
 * The API key is provided dynamically by the user via the app's UI.
 * Specialized as a Technical Mentor to generate detailed, actionable solutions.
 */
class GeminiService {
    
    private var model: GenerativeModel? = null
    private var currentApiKey: String = ""
    private var currentModelName: String = ""

    /**
     * Re-initializes the model when the API key or model name changes.
     */
    private fun initializeModel(apiKey: String, modelName: String) {
        val resolvedModel = modelName.ifBlank { "gemini-2.0-flash" }
        if (apiKey == currentApiKey && resolvedModel == currentModelName && model != null) return
        
        try {
            if (apiKey.isNotBlank()) {
                val config = generationConfig {
                    temperature = 0.7f
                    topK = 40
                    topP = 0.95f
                    maxOutputTokens = 2048
                }

                model = GenerativeModel(
                    modelName = resolvedModel,
                    apiKey = apiKey,
                    generationConfig = config,
                    systemInstruction = content {
                        text("You are BeakAI, an expert technical mentor. Your mission is to provide clear, actionable, and detailed solutions to technical queries. Focus on Java, Spring Boot, DSA, and System Design. Provide code snippets, architectural patterns, and step-by-step logic. Always be professional, helpful, and focused on solving the user's technical problem.")
                    }
                )
                currentApiKey = apiKey
                currentModelName = resolvedModel
            } else {
                model = null
                currentApiKey = ""
                currentModelName = ""
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "Failed to initialize GenerativeModel: ${e.message}")
            model = null
        }
    }

    /**
     * Fetches a daily interview question using the provided API key.
     */
    suspend fun getDailyInterviewQuestion(apiKey: String, modelName: String = "gemini-2.0-flash"): String {
        initializeModel(apiKey, modelName)
        val currentModel = model
        if (currentModel == null) {
            return "BeakAI Info: API Key missing or invalid. Please provide your key in the AI Assistant settings."
        }
        
        val prompt = "Provide one Java Spring Boot developer interview question and its concise answer for a fresher. Format as Question: [Q] Answer: [A]"
        return try {
            val response = currentModel.generateContent(prompt)
            response.text ?: "BeakAI was unable to generate a question. Please check back later."
        } catch (e: Exception) {
            Log.e("GeminiService", "Question Fetch Error: ${e.message}", e)
            "Connection error: ${e.localizedMessage}. Verify your internet and API key permissions."
        }
    }

    /**
     * Generates solutions based on user's query using the provided dynamic API key.
     */
    suspend fun getTechChatResponse(apiKey: String, modelName: String, userPrompt: String): String {
        initializeModel(apiKey, modelName)
        val currentModel = model
        if (currentModel == null) {
            return "BeakAI Chat: I need an API key to process your query. Please provide it in the AI Assistant settings."
        }

        return try {
            // The Persona is handled by systemInstruction; the prompt focuses on the query analysis.
            val response = currentModel.generateContent("Analyze the following technical query and provide a detailed, actionable solution: $userPrompt")
            val responseText = response.text
            if (responseText.isNullOrBlank()) {
                "BeakAI is analyzing. Please refine your technical question."
            } else {
                responseText
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "Chat Error Log: ${e.message}", e)
            "BeakAI Connection Error: ${e.localizedMessage}. Please ensure your internet is active and the API key is valid."
        }
    }
}
