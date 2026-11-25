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

    suspend fun registerUser(email: String, password: String): Result<Unit> {
        return repository.registerUser(email, password)
    }

    suspend fun authenticateUser(email: String, password: String): Result<Unit> {
        return repository.authenticateUser(email, password)
    }

    fun addTask(
        context: Context,
        title: String,
        description: String,
        priority: Int,
        frequency: TaskFrequency,
        category: TaskCategory,
        hour: Int,
        minute: Int,
        dayOfMonth: Int? = null,
        dayOfWeek: Int? = null,
        month: Int? = null,
        year: Int? = null,
        scheduledTimestamp: Long = 0L
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
                dayOfMonth = dayOfMonth,
                dayOfWeek = dayOfWeek,
                month = month,
                year = year,
                scheduledTimestamp = scheduledTimestamp,
                lastUpdated = System.currentTimeMillis(),
                status = TaskStatus.ACTIVE
            )
            repository.insert(task)
        }
    }

    fun setTaskCompletion(task: Task, completed: Boolean) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val updatedTask = task.copy(
                isCompleted = completed,
                status = if (completed) TaskStatus.ACHIEVED else TaskStatus.ACTIVE,
                lastCompletedTimestamp = if (completed) now else task.lastCompletedTimestamp,
                lastUpdated = now
            )
            repository.update(updatedTask)
        }
    }

    fun deleteTask(context: Context, task: Task) {
        viewModelScope.launch {
            repository.delete(task)
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
            
            val response = geminiService.getTechChatResponse(prompt)
            repository.logChatPrompt(prompt, response)
            
            val updatedMessages = _chatMessages.value.toMutableList()
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
