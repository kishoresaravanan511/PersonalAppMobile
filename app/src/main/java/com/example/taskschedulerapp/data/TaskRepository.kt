package com.example.taskschedulerapp.data

import android.content.Context
import android.util.Log
import com.example.taskschedulerapp.receiver.AlarmScheduler
import com.google.firebase.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class TaskRepository(private val taskDao: TaskDao, private val context: Context) {
    // UPDATED for the new BeakAI project in Singapore Region (asia-southeast1)
    private val firebaseUrl = "https://beakai-default-rtdb.asia-southeast1.firebasedatabase.app"
    
    private lateinit var database: DatabaseReference
    private lateinit var tasksRef: DatabaseReference
    private lateinit var promptsRef: DatabaseReference
    private lateinit var dsaRef: DatabaseReference
    private lateinit var questionsRef: DatabaseReference
    
    private val repositoryScope = CoroutineScope(Dispatchers.IO)
    private val alarmScheduler = AlarmScheduler(context)

    val allTasks: Flow<List<Task>> = taskDao.getAllTasks()
    val allQuestions: Flow<List<Question>> = taskDao.getAllQuestions()
    val dsaPatterns: Flow<List<DsaPattern>> = taskDao.getAllDsaPatterns()

    init {
        setupFirebase()
    }

    private fun setupFirebase() {
        try {
            // Using explicit URL to ensure connection to Singapore region
            val dbInstance = FirebaseDatabase.getInstance(firebaseUrl)
            
            try {
                // Must be set once before any database reference is created
                dbInstance.setPersistenceEnabled(true)
            } catch (e: Exception) {
                // Already enabled
            }
            
            database = dbInstance.reference
            tasksRef = database.child("tasks")
            promptsRef = database.child("chat_prompts")
            dsaRef = database.child("dsa_patterns")
            questionsRef = database.child("daily_questions")
            
            // Ensure local nodes stay synced with cloud
            tasksRef.keepSynced(true)
            dsaRef.keepSynced(true)
            questionsRef.keepSynced(true)
            
            checkConnection()
            
            // Connection Handshake: Write to console immediately to confirm write access
            val handshake = mapOf(
                "last_online" to ServerValue.TIMESTAMP,
                "app_version" to "1.0",
                "region" to "singapore",
                "project" to "BeakAI"
            )
            database.child("connection_status").setValue(handshake)
                .addOnSuccessListener { Log.d("TaskRepo", "Handshake Success: Data sent to BeakAI Singapore Console") }
                .addOnFailureListener { e -> Log.e("TaskRepo", "Handshake Failed: ${e.message}") }

            listenToFirebaseChanges()
            listenToDsaChanges()
            listenToQuestionChanges()
            
            // Push existing local data up to Firebase
            syncLocalDataToFirebase()
            
        } catch (e: Exception) {
            Log.e("TaskRepo", "Firebase Init Error: ${e.message}")
        }
    }

    private fun syncLocalDataToFirebase() {
        repositoryScope.launch {
            try {
                val localTasks = taskDao.getAllTasksSync()
                localTasks.forEach { task ->
                    if (task.firebaseId == null) {
                        val newRef = tasksRef.push()
                        val updatedTask = task.copy(firebaseId = newRef.key)
                        newRef.setValue(updatedTask)
                        taskDao.updateTask(updatedTask)
                    } else {
                        tasksRef.child(task.firebaseId).setValue(task)
                    }
                }
                
                val localQuestions = taskDao.getAllQuestionsSync()
                localQuestions.forEach { q ->
                    if (q.firebaseId == null) {
                        val newRef = questionsRef.push()
                        val updatedQ = q.copy(firebaseId = newRef.key)
                        newRef.setValue(updatedQ)
                        taskDao.insertQuestion(updatedQ)
                    } else {
                        questionsRef.child(q.firebaseId).setValue(q)
                    }
                }
            } catch (e: Exception) {
                Log.e("TaskRepo", "Sync to Cloud Error: ${e.message}")
            }
        }
    }

    private fun checkConnection() {
        val connectedRef = FirebaseDatabase.getInstance(firebaseUrl).getReference(".info/connected")
        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    Log.d("TaskRepo", "Connected to BeakAI Singapore Database")
                } else {
                    Log.d("TaskRepo", "Disconnected - Checking network...")
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun listenToFirebaseChanges() {
        tasksRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                repositoryScope.launch {
                    try {
                        val firebaseTasks = mutableListOf<Task>()
                        snapshot.children.forEach { taskSnapshot ->
                            val task = taskSnapshot.getValue(Task::class.java)
                            if (task != null) {
                                firebaseTasks.add(task.copy(firebaseId = taskSnapshot.key))
                            }
                        }
                        
                        val localTasks = taskDao.getAllTasksSync()
                        
                        firebaseTasks.forEach { firebaseTask ->
                            val localMatch = localTasks.find { local -> 
                                local.firebaseId == firebaseTask.firebaseId || 
                                (local.title == firebaseTask.title && local.hour == firebaseTask.hour && local.minute == firebaseTask.minute)
                            }
                            
                            if (localMatch == null) {
                                val id = taskDao.insertTask(firebaseTask)
                                alarmScheduler.scheduleAlarm(firebaseTask.copy(id = id.toInt()))
                            } else {
                                val updatedTask = firebaseTask.copy(id = localMatch.id)
                                if (localMatch.title != updatedTask.title || 
                                    localMatch.hour != updatedTask.hour || 
                                    localMatch.minute != updatedTask.minute ||
                                    localMatch.isCompleted != updatedTask.isCompleted) {
                                    taskDao.updateTask(updatedTask)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("TaskRepo", "Firebase -> Local Sync error: ${e.message}")
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun listenToDsaChanges() {
        dsaRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                repositoryScope.launch {
                    snapshot.children.forEach { dsaSnapshot ->
                        val pattern = dsaSnapshot.getValue(DsaPattern::class.java)
                        if (pattern != null) {
                            taskDao.insertDsaPattern(pattern)
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun listenToQuestionChanges() {
        questionsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                repositoryScope.launch {
                    try {
                        val localQuestions = taskDao.getAllQuestionsSync()
                        snapshot.children.forEach { qSnapshot ->
                            val question = qSnapshot.getValue(Question::class.java)
                            if (question != null) {
                                val firebaseId = qSnapshot.key
                                if (localQuestions.none { it.firebaseId == firebaseId }) {
                                    taskDao.insertQuestion(question.copy(firebaseId = firebaseId))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("TaskRepo", "Question sync error: ${e.message}")
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    suspend fun insert(task: Task): Long {
        val id = taskDao.insertTask(task)
        val taskWithId = task.copy(id = id.toInt())
        try {
            alarmScheduler.scheduleAlarm(taskWithId)
        } catch (e: Exception) {
            Log.e("TaskRepo", "Alarm failed")
        }
        
        repositoryScope.launch {
            val firebaseRef = tasksRef.push()
            val finalTask = taskWithId.copy(firebaseId = firebaseRef.key)
            firebaseRef.setValue(finalTask)
            taskDao.updateTask(finalTask)
        }
        return id
    }

    suspend fun updateDsaPattern(pattern: DsaPattern) {
        taskDao.updateDsaPattern(pattern)
        repositoryScope.launch {
            dsaRef.child(pattern.id).setValue(pattern)
        }
    }

    suspend fun logChatPrompt(prompt: String, response: String) {
        repositoryScope.launch {
            val chatLog = mapOf(
                "prompt" to prompt,
                "response" to response,
                "timestamp" to ServerValue.TIMESTAMP
            )
            promptsRef.push().setValue(chatLog)
        }
    }

    suspend fun update(task: Task) {
        taskDao.updateTask(task)
        if (!task.isCompleted) {
            alarmScheduler.scheduleAlarm(task)
        } else {
            alarmScheduler.cancelAlarm(task)
        }
        
        repositoryScope.launch {
            task.firebaseId?.let {
                tasksRef.child(it).setValue(task)
            }
        }
    }

    suspend fun delete(task: Task) {
        taskDao.deleteTask(task)
        alarmScheduler.cancelAlarm(task)
        repositoryScope.launch {
            task.firebaseId?.let {
                tasksRef.child(it).removeValue()
            }
        }
    }

    suspend fun insertQuestion(question: Question) {
        val id = taskDao.insertQuestion(question)
        repositoryScope.launch {
            val firebaseRef = questionsRef.push()
            val finalQuestion = question.copy(id = id.toInt(), firebaseId = firebaseRef.key)
            firebaseRef.setValue(finalQuestion)
            taskDao.insertQuestion(finalQuestion)
        }
    }

    suspend fun getTaskById(id: Int): Task? = taskDao.getTaskById(id)
}
