package com.example.taskschedulerapp.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerapp.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(private val repository: TaskRepository) : ViewModel() {

    private val geminiService = GeminiService()

    val tasks: StateFlow<List<Task>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val questions: StateFlow<List<Question>> = repository.allQuestions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dsaPatterns: StateFlow<List<DsaPattern>> = repository.dsaPatterns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _chatMessages = MutableStateFlow<List<Pair<String, Boolean>>>(emptyList())
    val chatMessages: StateFlow<List<Pair<String, Boolean>>> = _chatMessages.asStateFlow()

    fun addTask(
        context: Context,
        title: String,
        description: String,
        priority: Int,
        frequency: TaskFrequency,
        category: TaskCategory,
        hour: Int,
        minute: Int,
        dayOfWeek: Int? = null,
        dayOfMonth: Int? = null
    ) {
        viewModelScope.launch {
            val task = Task(
                title = title,
                description = description,
                priority = priority,
                frequency = frequency,
                category = category,
                hour = hour,
                minute = minute,
                dayOfWeek = dayOfWeek,
                dayOfMonth = dayOfMonth
            )
            repository.insert(task)
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            repository.update(task)
        }
    }

    fun deleteTask(context: Context, task: Task) {
        viewModelScope.launch {
            repository.delete(task)
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            repository.update(
                task.copy(
                    isCompleted = !task.isCompleted,
                    lastCompletedTimestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun toggleDsaPattern(id: String, category: String, patternName: String) {
        viewModelScope.launch {
            val current = dsaPatterns.value.find { it.id == id }
            val updated = DsaPattern(
                id = id,
                category = category,
                patternName = patternName,
                isCompleted = !(current?.isCompleted ?: false)
            )
            repository.updateDsaPattern(updated)
        }
    }

    fun askGemini(prompt: String) {
        viewModelScope.launch {
            val currentMessages = _chatMessages.value.toMutableList()
            currentMessages.add(prompt to true)
            _chatMessages.value = currentMessages
            
            // Show thinking state placeholder
            currentMessages.add("Thinking..." to false)
            _chatMessages.value = currentMessages
            
            val response = geminiService.getTechChatResponse(prompt)
            
            // Log to Firebase for real-time monitoring
            repository.logChatPrompt(prompt, response)
            
            val updatedMessages = _chatMessages.value.toMutableList()
            if (updatedMessages.isNotEmpty()) updatedMessages.removeAt(updatedMessages.size - 1)
            updatedMessages.add(response to false)
            _chatMessages.value = updatedMessages
        }
    }
}

class TaskViewModelFactory(private val repository: TaskRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TaskViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
