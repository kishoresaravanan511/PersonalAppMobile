package com.example.taskschedulerapp.data

import android.content.Context
import android.util.Log
import com.example.taskschedulerapp.receiver.AlarmScheduler
import com.google.firebase.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class TaskRepository(private val taskDao: TaskDao, private val context: Context) {
    private val firebaseUrl = "https://beakai-default-rtdb.asia-southeast1.firebasedatabase.app"
    
    private lateinit var database: DatabaseReference
    private var tasksRef: DatabaseReference? = null
    private var promptsRef: DatabaseReference? = null
    private var dsaRef: DatabaseReference? = null
    private var questionsRef: DatabaseReference? = null
    
    private val repositoryScope = CoroutineScope(Dispatchers.IO)
    private val alarmScheduler = AlarmScheduler(context)

    val allTasks: Flow<List<Task>> = taskDao.getAllTasks()
    val allQuestions: Flow<List<Question>> = taskDao.getAllQuestions()
    val dsaPatterns: Flow<List<DsaPattern>> = taskDao.getAllDsaPatterns()

    private var currentUserEmail: String? = null

    init {
        setupFirebase()
    }

    private fun setupFirebase() {
        try {
            val dbInstance = FirebaseDatabase.getInstance(firebaseUrl)
            try {
                dbInstance.setPersistenceEnabled(true)
            } catch (e: Exception) {}
            database = dbInstance.reference
            
            // Global questions node (common for all users)
            questionsRef = database.child("daily_questions")
            questionsRef?.keepSynced(true)
            
            checkConnection()
            listenToQuestionChanges()
        } catch (e: Exception) {
            Log.e("TaskRepo", "Firebase Init Error: ${e.message}")
        }
    }

    /**
     * Set the current user and initialize user-specific data nodes.
     * This creates separation of tasks, study, and meetings per user.
     */
    fun setUserEmail(email: String) {
        if (currentUserEmail == email) return
        currentUserEmail = email
        val sanitizedEmail = email.replace(".", "_").replace("@", "_")
        val userRef = database.child("users").child(sanitizedEmail)
        
        // Data isolation logic: Each user gets their own tasks/prompts/dsa nodes
        tasksRef = userRef.child("tasks")
        promptsRef = userRef.child("chat_prompts")
        dsaRef = userRef.child("dsa_patterns")
        
        tasksRef?.keepSynced(true)
        dsaRef?.keepSynced(true)
        
        listenToFirebaseChanges()
        listenToDsaChanges()
        syncLocalDataToFirebase()
    }

    /**
     * Custom Registration using Realtime Database (Replaced Firebase Auth)
     */
    suspend fun registerUser(email: String, password: String): Result<Unit> {
        return try {
            val sanitizedEmail = email.replace(".", "_").replace("@", "_")
            val userRef = database.child("users").child(sanitizedEmail)
            
            val snapshot = userRef.child("profile").get().await()
            if (snapshot.exists()) {
                return Result.failure(Exception("User already exists with this BeakAI ID"))
            }
            
            val profile = mapOf("email" to email, "password" to password)
            userRef.child("profile").setValue(profile).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Custom Authentication using Realtime Database (Manual check)
     */
    suspend fun authenticateUser(email: String, password: String): Result<Unit> {
        return try {
            val sanitizedEmail = email.replace(".", "_").replace("@", "_")
            val userRef = database.child("users").child(sanitizedEmail)
            
            val snapshot = userRef.child("profile").get().await()
            if (!snapshot.exists()) {
                return Result.failure(Exception("Account not found. Please register first."))
            }
            
            val storedPassword = snapshot.child("password").getValue(String::class.java)
            if (storedPassword == password) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Incorrect BeakAI password"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun syncLocalDataToFirebase() {
        val tRef = tasksRef ?: return
        repositoryScope.launch {
            try {
                val localTasks = taskDao.getAllTasksSync()
                localTasks.forEach { task ->
                    if (task.firebaseId == null) {
                        val newRef = tRef.push()
                        val updatedTask = task.copy(firebaseId = newRef.key)
                        newRef.setValue(updatedTask)
                        taskDao.updateTask(updatedTask)
                    } else {
                        tRef.child(task.firebaseId).setValue(task)
                    }
                }
            } catch (e: Exception) {
                Log.e("TaskRepo", "Sync Error: ${e.message}")
            }
        }
    }

    private fun checkConnection() {
        database.database.getReference(".info/connected").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                Log.d("TaskRepo", if (connected) "Connected" else "Disconnected")
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun listenToFirebaseChanges() {
        val tRef = tasksRef ?: return
        tRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                repositoryScope.launch {
                    try {
                        snapshot.children.forEach { taskSnapshot ->
                            val task = taskSnapshot.getValue(Task::class.java)
                            if (task != null) {
                                val firebaseTask = task.copy(firebaseId = taskSnapshot.key)
                                val localTasks = taskDao.getAllTasksSync()
                                val localMatch = localTasks.find { it.firebaseId == firebaseTask.firebaseId }
                                if (localMatch == null) {
                                    val id = taskDao.insertTask(firebaseTask)
                                    alarmScheduler.scheduleAlarm(firebaseTask.copy(id = id.toInt()))
                                } else {
                                    taskDao.updateTask(firebaseTask.copy(id = localMatch.id))
                                }
                            }
                        }
                    } catch (e: Exception) {}
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun listenToDsaChanges() {
        val dRef = dsaRef ?: return
        dRef.addValueEventListener(object : ValueEventListener {
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
        questionsRef?.addValueEventListener(object : ValueEventListener {
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
                    } catch (e: Exception) {}
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    suspend fun insert(task: Task): Long {
        val id = taskDao.insertTask(task)
        val taskWithId = task.copy(id = id.toInt())
        tasksRef?.let {
            val firebaseRef = it.push()
            val finalTask = taskWithId.copy(firebaseId = firebaseRef.key)
            firebaseRef.setValue(finalTask)
            taskDao.updateTask(finalTask)
        }
        alarmScheduler.scheduleAlarm(taskWithId)
        return id
    }

    suspend fun updateDsaPattern(pattern: DsaPattern) {
        taskDao.updateDsaPattern(pattern)
        dsaRef?.child(pattern.id)?.setValue(pattern)
    }

    suspend fun logChatPrompt(prompt: String, response: String) {
        promptsRef?.push()?.setValue(mapOf(
            "prompt" to prompt,
            "response" to response,
            "timestamp" to ServerValue.TIMESTAMP
        ))
    }

    suspend fun update(task: Task) {
        taskDao.updateTask(task)
        if (!task.isCompleted) alarmScheduler.scheduleAlarm(task)
        else alarmScheduler.cancelAlarm(task)
        task.firebaseId?.let { tasksRef?.child(it)?.setValue(task) }
    }

    suspend fun delete(task: Task) {
        taskDao.deleteTask(task)
        alarmScheduler.cancelAlarm(task)
        task.firebaseId?.let { tasksRef?.child(it)?.removeValue() }
    }

    suspend fun insertQuestion(question: Question) {
        val id = taskDao.insertQuestion(question)
        questionsRef?.push()?.let {
            val finalQuestion = question.copy(id = id.toInt(), firebaseId = it.key)
            it.setValue(finalQuestion)
            taskDao.insertQuestion(finalQuestion)
        }
    }

    suspend fun getTaskById(id: Int): Task? = taskDao.getTaskById(id)

    suspend fun clearLocalData() {
        taskDao.deleteAllTasks()
        taskDao.deleteAllDsaPatterns()
    }
}
