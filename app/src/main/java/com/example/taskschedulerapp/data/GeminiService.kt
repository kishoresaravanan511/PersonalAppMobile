package com.example.taskschedulerapp.data

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content

class GeminiService {
    private val apiKey = ""
    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey
    )

    suspend fun getDailyInterviewQuestion(): String {
        val prompt = "Provide one Java Spring Boot developer interview question and its concise answer for a fresher, focusing on personal improvement and core concepts."
        return try {
            val response = model.generateContent(prompt)
            response.text ?: "Could not fetch question today. Focus on Spring Boot Annotations!"
        } catch (e: Exception) {
            "Error fetching question: ${e.message}"
        }
    }

    suspend fun getTechChatResponse(userPrompt: String): String {
        return try {
            val response = model.generateContent(content {
                text("You are BeakAI, a personal improvement assistant and Java Spring Boot expert. " +
                     "Your goal is to help the user prepare for developer interviews and improve their technical skills. " +
                     "Answer this query with a focus on Spring Boot, Java, and professional growth: $userPrompt")
            })
            response.text ?: "I'm here to help with your Spring Boot journey. Ask me anything about Java or interviews!"
        } catch (e: Exception) {
            "Connection error. Please check your internet to talk to BeakAI."
        }
    }
}
