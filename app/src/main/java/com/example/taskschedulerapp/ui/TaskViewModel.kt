package com.example.taskschedulerapp.ui

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

class TaskViewModel(private val repository: TaskRepository, private val sessionManager: SessionManager) : ViewModel() {

    private val geminiService = GeminiService()

    val tasks: StateFlow<List<Task>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val questions: StateFlow<List<Question>> = repository.allQuestions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dsaPatterns: StateFlow<List<DsaPattern>> = repository.dsaPatterns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _chatMessages = MutableStateFlow<List<Pair<String, Boolean>>>(emptyList())
    val chatMessages: StateFlow<List<Pair<String, Boolean>>> = _chatMessages.asStateFlow()

    private val _geminiApiKey = MutableStateFlow(sessionManager.getGeminiApiKey() ?: "")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _geminiModelName = MutableStateFlow(sessionManager.getGeminiModelName() ?: "gemini-2.0-flash")
    val geminiModelName: StateFlow<String> = _geminiModelName.asStateFlow()

    fun updateGeminiSettings(key: String, modelName: String) {
        val resolvedModel = modelName.ifBlank { "gemini-2.0-flash" }
        sessionManager.saveGeminiApiKey(key)
        sessionManager.saveGeminiModelName(resolvedModel)
        _geminiApiKey.value = key
        _geminiModelName.value = resolvedModel
    }

    suspend fun registerUser(email: String, password: String): Result<Unit> {
        return repository.registerUser(email, password)
    }

    suspend fun authenticateUser(email: String, password: String): Result<Unit> {
        return repository.authenticateUser(email, password)
    }

    fun addTask(task: Task) {
        viewModelScope.launch {
            repository.insert(task)
        }
    }

    fun addTask(
        title: String,
        description: String,
        aiAssistance: String = "",
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
                aiAssistance = aiAssistance,
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

    fun toggleTaskCompletion(taskId: Int, completed: Boolean) {
        viewModelScope.launch {
            val task = tasks.value.find { it.id == taskId } ?: return@launch
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

    fun setTaskCompletion(task: Task, completed: Boolean) {
        if (task.status != TaskStatus.ACTIVE) return
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

    fun deleteTask(task: Task) {
        if (task.status != TaskStatus.ACTIVE) return
        viewModelScope.launch {
            repository.delete(task)
        }
    }

    fun requestTaskAssistance(name: String, description: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val response = geminiService.getTechChatResponse(
                _geminiApiKey.value,
                _geminiModelName.value,
                "Give a short, practical assistance plan for this scheduled item.\nName: $name\nDescription: $description"
            )
            onResult(response)
        }
    }

    fun toggleDsaPattern(id: String, category: String, patternName: String) {
        viewModelScope.launch {
            val current = dsaPatterns.value.find { it.id == id }
            val updated = DsaPattern(
                id = id,
                userEmail = "",
                category = category,
                patternName = patternName,
                isCompleted = !(current?.isCompleted ?: false)
            )
            repository.updateDsaPattern(updated)
        }
    }

    fun askGemini(prompt: String) {
        viewModelScope.launch {
            val apiKey = _geminiApiKey.value
            val currentMessages = _chatMessages.value.toMutableList()
            currentMessages.add(prompt to true)
            _chatMessages.value = currentMessages
            
            val response = geminiService.getTechChatResponse(apiKey, _geminiModelName.value, prompt)
            repository.logChatPrompt(prompt, response)
            
            val updatedMessages = _chatMessages.value.toMutableList()
            updatedMessages.add(response to false)
            _chatMessages.value = updatedMessages
        }
    }
}

class TaskViewModelFactory(private val repository: TaskRepository, private val sessionManager: SessionManager) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TaskViewModel(repository, sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
