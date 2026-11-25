package com.example.taskschedulerapp.data

import android.content.Context
import android.util.Log
import com.example.taskschedulerapp.receiver.AlarmScheduler
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TaskRepository(private val taskDao: TaskDao, private val context: Context) {
    private val firebaseUrl = "https://beakai-default-rtdb.asia-southeast1.firebasedatabase.app"
    
    private var database: DatabaseReference? = null
    private var tasksRef: DatabaseReference? = null
    private var promptsRef: DatabaseReference? = null
    private var dsaRef: DatabaseReference? = null
    private var questionsRef: DatabaseReference? = null
    
    private var tasksListener: ValueEventListener? = null
    private var dsaListener: ValueEventListener? = null
    private var questionsListener: ValueEventListener? = null
    
    private var lastTasksRef: DatabaseReference? = null
    private var lastDsaRef: DatabaseReference? = null

    // Track state using simple properties instead of RestrictedApi calls
    private var currentSanitizedEmail: String? = null

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("TaskRepo", "Repository Scope Exception: ${throwable.message}", throwable)
    }
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)
    private val alarmScheduler = AlarmScheduler(context)
    private val auth = FirebaseAuth.getInstance()
    private val syncMutex = Mutex()

    private val currentUserEmailFlow = MutableStateFlow<String?>(null)

    val allTasks: Flow<List<Task>> = currentUserEmailFlow.flatMapLatest { email ->
        taskDao.getAllTasks(email ?: "")
    }

    val dsaPatterns: Flow<List<DsaPattern>> = currentUserEmailFlow.flatMapLatest { email ->
        taskDao.getAllDsaPatterns(email ?: "")
    }

    val allQuestions: Flow<List<Question>> = taskDao.getAllQuestions()

    init {
        setupFirebase()
    }

    private fun setupFirebase() {
        repositoryScope.launch {
            try {
                val dbInstance = FirebaseDatabase.getInstance(firebaseUrl)
                try {
                    dbInstance.setPersistenceEnabled(true)
                } catch (e: Exception) {}
                database = dbInstance.reference
                
                questionsRef = database?.child("daily_questions")
                questionsRef?.keepSynced(true)
                
                checkConnection()
                listenToQuestionChanges()
                
                // Re-trigger user setup if email was already known
                currentUserEmailFlow.value?.let { setUserEmail(it) }
            } catch (e: Exception) {
                Log.e("TaskRepo", "Firebase Init Error: ${e.message}")
            }
        }
    }

    fun setUserEmail(email: String) {
        val emailLower = email.lowercase().trim()
        val sanitizedEmail = emailLower.replace(".", "_").replace("@", "_")

        // Update the flow immediately so Room data starts loading
        if (currentUserEmailFlow.value != emailLower) {
            currentUserEmailFlow.value = emailLower
        }
        
        val db = database ?: return
        
        // Prevent redundant listener setup if same user
        if (currentSanitizedEmail == sanitizedEmail && tasksListener != null) return
        
        currentSanitizedEmail = sanitizedEmail
        removeListeners()

        val userRef = db.child("users").child(sanitizedEmail)
        tasksRef = userRef.child("tasks")
        promptsRef = userRef.child("chat_prompts")
        dsaRef = userRef.child("dsa_patterns")
        
        lastTasksRef = tasksRef
        lastDsaRef = dsaRef
        
        tasksRef?.keepSynced(true)
        dsaRef?.keepSynced(true)
        
        listenToFirebaseChanges()
        listenToDsaChanges()
        syncLocalDataToFirebase()
    }

    private fun removeListeners() {
        tasksListener?.let { lastTasksRef?.removeEventListener(it) }
        dsaListener?.let { lastDsaRef?.removeEventListener(it) }
        tasksListener = null
        dsaListener = null
    }

    suspend fun registerUser(email: String, password: String): Result<Unit> {
        val emailLower = email.lowercase().trim()
        val db = database ?: return Result.failure(Exception("Firebase not initialized"))
        
        val sanitizedEmail = emailLower.replace(".", "_").replace("@", "_")
        val userRef = db.child("users").child(sanitizedEmail)
        val profile = mapOf("email" to emailLower, "password" to password)

        return try {
            try {
                auth.createUserWithEmailAndPassword(emailLower, password).await()
            } catch (e: Exception) {
                Log.w("TaskRepo", "Auth Register Fallback: ${e.message}")
            }
            userRef.child("profile").setValue(profile).await()
            setUserEmail(emailLower)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun authenticateUser(email: String, password: String): Result<Unit> {
        val emailLower = email.lowercase().trim()
        val db = database ?: return Result.failure(Exception("Firebase not initialized"))
        
        return try {
            auth.signInWithEmailAndPassword(emailLower, password).await()
            setUserEmail(emailLower)
            Result.success(Unit)
        } catch (e: Exception) {
            val sanitizedEmail = emailLower.replace(".", "_").replace("@", "_")
            val snapshot = db.child("users").child(sanitizedEmail).child("profile").get().await()
            if (snapshot.exists() && snapshot.child("password").getValue(String::class.java) == password) {
                setUserEmail(emailLower)
                Result.success(Unit)
            } else {
                Result.failure(e)
            }
        }
    }

    private fun syncLocalDataToFirebase() {
        val email = currentUserEmailFlow.value ?: return
        val tRef = tasksRef ?: return
        repositoryScope.launch {
            try {
                val localTasks = taskDao.getAllTasksSync(email)
                localTasks.filter { it.firebaseId == null }.forEach { task ->
                    val newRef = tRef.push()
                    val key = newRef.key
                    if (key != null) {
                        val updatedTask = task.copy(firebaseId = key, lastUpdated = System.currentTimeMillis())
                        newRef.setValue(updatedTask)
                        syncMutex.withLock {
                            taskDao.updateTask(updatedTask)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TaskRepo", "Sync to Firebase Error: ${e.message}")
            }
        }
    }

    private fun checkConnection() {
        database?.database?.getReference(".info/connected")?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                Log.d("TaskRepo", "RTDB Status: ${if(connected) "Connected" else "Offline"}")
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun listenToFirebaseChanges() {
        val email = currentUserEmailFlow.value ?: return
        val tRef = tasksRef ?: return
        
        tasksListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                repositoryScope.launch {
                    syncMutex.withLock {
                        try {
                            val localTasks = taskDao.getAllTasksSync(email)
                            val firebaseKeys = snapshot.children.mapNotNull { it.key }.toSet()

                            snapshot.children.forEach { taskSnapshot ->
                                val firebaseTask = try { 
                                    taskSnapshot.getValue(Task::class.java) 
                                } catch (e: Exception) { null } ?: return@forEach
                                
                                val key = taskSnapshot.key ?: return@forEach
                                val localMatch = localTasks.find { it.firebaseId == key }

                                if (localMatch == null) {
                                    val id = taskDao.insertTask(firebaseTask.copy(id = 0, firebaseId = key, userEmail = email))
                                    alarmScheduler.scheduleAlarm(firebaseTask.copy(id = id.toInt(), userEmail = email))
                                } else if (firebaseTask.lastUpdated > localMatch.lastUpdated) {
                                    val updated = firebaseTask.copy(id = localMatch.id, firebaseId = key, userEmail = email)
                                    taskDao.updateTask(updated)
                                    alarmScheduler.scheduleAlarm(updated)
                                }
                            }

                            localTasks.forEach { localTask ->
                                if (localTask.firebaseId != null && !firebaseKeys.contains(localTask.firebaseId)) {
                                    taskDao.deleteTask(localTask)
                                    alarmScheduler.cancelAlarm(localTask)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("TaskRepo", "Firebase Sync Listener Error: ${e.message}")
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        tRef.addValueEventListener(tasksListener!!)
    }

    private fun listenToDsaChanges() {
        val email = currentUserEmailFlow.value ?: return
        val dRef = dsaRef ?: return
        dsaListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                repositoryScope.launch {
                    try {
                        val patterns = snapshot.children.mapNotNull { 
                            try { it.getValue(DsaPattern::class.java)?.copy(userEmail = email) } catch(e:Exception) { null }
                        }
                        if (patterns.isNotEmpty()) {
                            taskDao.insertDsaPatterns(patterns)
                        }
                    } catch (e: Exception) {}
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        dRef.addValueEventListener(dsaListener!!)
    }

    private fun listenToQuestionChanges() {
        questionsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                repositoryScope.launch {
                    try {
                        val localQuestions = taskDao.getAllQuestionsSync()
                        val newQuestions = snapshot.children.mapNotNull { qSnapshot ->
                            try {
                                val q = qSnapshot.getValue(Question::class.java)
                                if (q != null && localQuestions.none { it.firebaseId == qSnapshot.key }) {
                                    q.copy(firebaseId = qSnapshot.key)
                                } else null
                            } catch(e:Exception) { null }
                        }
                        if (newQuestions.isNotEmpty()) {
                            newQuestions.forEach { taskDao.insertQuestion(it) }
                        }
                    } catch (e: Exception) {}
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        questionsRef?.addValueEventListener(questionsListener!!)
    }

    suspend fun insert(task: Task): Long {
        val email = currentUserEmailFlow.value ?: ""
        val now = System.currentTimeMillis()
        val timestampedTask = task.copy(lastUpdated = now, createdAt = now, userEmail = email)
        val id = taskDao.insertTask(timestampedTask)
        val taskWithId = timestampedTask.copy(id = id.toInt())
        
        tasksRef?.let {
            val firebaseRef = it.push()
            val finalTask = taskWithId.copy(firebaseId = firebaseRef.key)
            firebaseRef.setValue(finalTask)
            syncMutex.withLock {
                taskDao.updateTask(finalTask)
            }
        }
        
        alarmScheduler.scheduleAlarm(taskWithId)
        return id
    }

    suspend fun updateDsaPattern(pattern: DsaPattern) {
        val email = currentUserEmailFlow.value ?: ""
        val updatedPattern = pattern.copy(userEmail = email)
        taskDao.updateDsaPattern(updatedPattern)
        dsaRef?.child(pattern.id)?.setValue(updatedPattern)
    }

    suspend fun logChatPrompt(prompt: String, response: String) {
        promptsRef?.push()?.setValue(mapOf("prompt" to prompt, "response" to response, "timestamp" to ServerValue.TIMESTAMP))
    }

    suspend fun update(task: Task) {
        val email = currentUserEmailFlow.value ?: ""
        val updatedTask = task.copy(lastUpdated = System.currentTimeMillis(), userEmail = email)
        syncMutex.withLock {
            taskDao.updateTask(updatedTask)
        }
        if (!updatedTask.isCompleted) alarmScheduler.scheduleAlarm(updatedTask)
        else alarmScheduler.cancelAlarm(updatedTask)
        updatedTask.firebaseId?.let { tasksRef?.child(it)?.setValue(updatedTask) }
    }

    suspend fun delete(task: Task) {
        syncMutex.withLock {
            taskDao.deleteTask(task)
        }
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
        val email = currentUserEmailFlow.value ?: return
        removeListeners()
        questionsListener?.let { questionsRef?.removeEventListener(it) }
        taskDao.getAllTasksSync(email).forEach { alarmScheduler.cancelAlarm(it) }
        taskDao.deleteAllTasks(email)
        taskDao.deleteAllDsaPatterns(email)
        auth.signOut()
        tasksRef = null
        currentSanitizedEmail = null
        currentUserEmailFlow.value = null
    }
}
