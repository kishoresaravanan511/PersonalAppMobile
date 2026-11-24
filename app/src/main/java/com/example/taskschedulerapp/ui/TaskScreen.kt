package com.example.taskschedulerapp.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskschedulerapp.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskScreen(
    viewModel: TaskViewModel,
    userEmail: String?,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onLogout: () -> Unit
) {
    val tasks by viewModel.tasks.collectAsState()
    val questions by viewModel.questions.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val dsaPatterns by viewModel.dsaPatterns.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    var showDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<TaskCategory?>(TaskCategory.TASK) }
    var showQuestionsHistory by remember { mutableStateOf(false) }
    var showChatbot by remember { mutableStateOf(false) }
    var showSystemDesign by remember { mutableStateOf(false) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    
    var lastRefreshTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }

    // Auto-refresh task statuses every 30 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(30000)
            lastRefreshTime = System.currentTimeMillis()
        }
    }

    BackHandler(enabled = true) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            showChatbot -> showChatbot = false
            showQuestionsHistory -> showQuestionsHistory = false
            showSystemDesign -> showSystemDesign = false
            else -> showExitConfirm = true
        }
    }

    val freqTabs = listOf("Daily", "Weekly", "Monthly", "Once")
    
    val updatedTasks = remember(tasks, lastRefreshTime) {
        val now = Calendar.getInstance()
        tasks.map { task ->
            val taskTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, task.hour)
                set(Calendar.MINUTE, task.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                
                when (task.frequency) {
                    TaskFrequency.WEEKLY -> task.dayOfWeek?.let { set(Calendar.DAY_OF_WEEK, it) }
                    TaskFrequency.MONTHLY -> task.dayOfMonth?.let { set(Calendar.DAY_OF_MONTH, it) }
                    else -> {}
                }
            }

            // Calendar-based check for completion in current cycle
            val isAchievedInPeriod = if (task.isCompleted) {
                val lastComp = Calendar.getInstance().apply { timeInMillis = task.lastCompletedTimestamp }
                when (task.frequency) {
                    TaskFrequency.DAILY -> {
                        lastComp.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                        lastComp.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
                    }
                    TaskFrequency.WEEKLY -> {
                        lastComp.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                        lastComp.get(Calendar.WEEK_OF_YEAR) == now.get(Calendar.WEEK_OF_YEAR)
                    }
                    TaskFrequency.MONTHLY -> {
                        lastComp.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                        lastComp.get(Calendar.MONTH) == now.get(Calendar.MONTH)
                    }
                    TaskFrequency.ONCE -> true
                }
            } else false

            val hasTimePassed = taskTime.before(now)
            
            val unambiguousStatus = when {
                isAchievedInPeriod -> TaskStatus.ACHIEVED
                hasTimePassed -> TaskStatus.EXPIRED
                else -> TaskStatus.ACTIVE
            }
            task.copy(status = unambiguousStatus)
        }
    }

    val filteredTasks = updatedTasks.filter { 
        it.category == selectedCategory && 
        (if (selectedCategory == TaskCategory.DSA || selectedCategory == TaskCategory.PROJECTS || selectedCategory == TaskCategory.SYSTEM_DESIGN) true else
        it.frequency == when(selectedTabIndex) {
            0 -> TaskFrequency.DAILY
            1 -> TaskFrequency.WEEKLY
            2 -> TaskFrequency.MONTHLY
            else -> TaskFrequency.ONCE
        })
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                Spacer(Modifier.height(12.dp))
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "BeakAI assist",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFFE65100)
                    )
                    Text(
                        "Your optimistic partner for learning and growth.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                        lineHeight = 16.sp,
                        color = Color.Gray
                    )
                }
                HorizontalDivider()
                
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn {
                        item {
                            Text("TASK, STUDY, MEETING", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(16.dp), color = Color.Gray)
                        }
                        items(listOf(TaskCategory.TASK, TaskCategory.STUDY, TaskCategory.MEETING)) { category ->
                            NavigationDrawerItem(
                                label = { Text(category.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium) },
                                selected = selectedCategory == category && !showQuestionsHistory && !showChatbot && !showSystemDesign,
                                onClick = { 
                                    selectedCategory = category
                                    showQuestionsHistory = false
                                    showChatbot = false
                                    showSystemDesign = false
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }
                        
                        item {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                userEmail?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(bottom = 4.dp, start = 12.dp)
                                    )
                                }
                                NavigationDrawerItem(
                                    label = { Text("Logout", style = MaterialTheme.typography.bodyMedium) },
                                    selected = false,
                                    icon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Logout") },
                                    onClick = {
                                        showLogoutConfirm = true
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(0.dp)
                                )
                            }
                        }

                        item {
                            Text("DSA, SYSTEM DESIGN CONCEPTS, DAILY QUESTIONS", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(16.dp), color = Color.Gray)
                        }
                        
                        item {
                            NavigationDrawerItem(
                                label = { Text("DSA", style = MaterialTheme.typography.bodyMedium) },
                                selected = selectedCategory == TaskCategory.DSA,
                                icon = { Icon(Icons.Default.Code, null) },
                                onClick = { 
                                    selectedCategory = TaskCategory.DSA
                                    showQuestionsHistory = false
                                    showChatbot = false
                                    showSystemDesign = false
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }

                        item {
                            NavigationDrawerItem(
                                label = { Text("System Design Concepts", style = MaterialTheme.typography.bodyMedium) },
                                selected = showSystemDesign,
                                icon = { Icon(Icons.Default.Layers, null) },
                                onClick = { 
                                    showSystemDesign = true
                                    showQuestionsHistory = false
                                    showChatbot = false
                                    selectedCategory = null
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }
                        
                        item {
                            NavigationDrawerItem(
                                label = { Text("Daily Questions", style = MaterialTheme.typography.bodyMedium) },
                                selected = showQuestionsHistory,
                                icon = { Icon(Icons.Default.School, null) },
                                onClick = { 
                                    showQuestionsHistory = true
                                    showChatbot = false
                                    showSystemDesign = false
                                    selectedCategory = null
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }

                        if (userEmail == AdminConfig.ADMIN_EMAIL) {
                            item {
                                var biometricEnabled by remember { mutableStateOf(true) }
                                Text("SECURITY", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(16.dp), color = Color.Gray)
                                NavigationDrawerItem(
                                    label = { Text("Fingerprint Login", style = MaterialTheme.typography.bodyMedium) },
                                    selected = false,
                                    icon = { Icon(Icons.Default.Fingerprint, null) },
                                    badge = { Switch(checked = biometricEnabled, onCheckedChange = { biometricEnabled = it }) },
                                    onClick = { },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer)) {
                    TopAppBar(
                        title = { 
                            Text(when {
                                showChatbot -> "BeakAI assist"
                                showQuestionsHistory -> "Daily Questions"
                                showSystemDesign -> "Design Concepts"
                                selectedCategory == TaskCategory.DSA -> "DSA Study"
                                else -> selectedCategory?.name?.replace("_", " ") ?: "BeakAI"
                            }, style = MaterialTheme.typography.titleLarge) 
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, null)
                            }
                        },
                        actions = {
                            IconButton(onClick = onThemeToggle) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Appearance"
                                )
                            }
                            IconButton(onClick = { 
                                lastRefreshTime = System.currentTimeMillis() 
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                        }
                    )
                    if (!showQuestionsHistory && !showChatbot && !showSystemDesign && selectedCategory != TaskCategory.DSA && selectedCategory != TaskCategory.PROJECTS) {
                        ScrollableTabRow(selectedTabIndex = selectedTabIndex) {
                            freqTabs.forEachIndexed { index, title ->
                                Tab(selected = selectedTabIndex == index, onClick = { selectedTabIndex = index }, text = { Text(title, style = MaterialTheme.typography.bodyMedium) })
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FloatingActionButton(
                        onClick = { 
                            showChatbot = !showChatbot
                            showQuestionsHistory = false
                            showSystemDesign = false
                        },
                        containerColor = if (showChatbot) Color.Red else Color(0xFF00BFA5),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(if (showChatbot) Icons.Default.Close else Icons.Default.Face5, null, tint = Color.White)
                    }

                    if (!showChatbot && !showQuestionsHistory && !showSystemDesign && selectedCategory != TaskCategory.DSA && selectedCategory != TaskCategory.PROJECTS) {
                        FloatingActionButton(onClick = { showDialog = true }, containerColor = Color(0xFFFF9800)) {
                            Icon(Icons.Default.Add, null, tint = Color.White)
                        }
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                when {
                    showChatbot -> GeminiChatScreen(viewModel, chatMessages)
                    showQuestionsHistory -> QuestionsList(questions)
                    showSystemDesign -> SystemDesignModuleView(viewModel, dsaPatterns)
                    selectedCategory == TaskCategory.DSA -> DsaModuleView(viewModel, dsaPatterns)
                    else -> TaskList(filteredTasks, viewModel)
                }
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Confirm Logout", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Are you sure you want to log out from BeakAI?", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                Button(onClick = { onLogout(); showLogoutConfirm = false }) {
                    Text("Logout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Exit BeakAI", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Do you want to close the application intentionally?", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                Button(onClick = { (context as? Activity)?.finish() }) {
                    Text("Exit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDialog) {
        AddTaskDialog(
            initialFrequency = when (selectedTabIndex) {
                0 -> TaskFrequency.DAILY
                1 -> TaskFrequency.WEEKLY
                2 -> TaskFrequency.MONTHLY
                else -> TaskFrequency.ONCE
            },
            initialCategory = selectedCategory ?: TaskCategory.TASK,
            onDismiss = { showDialog = false },
            onConfirm = { title, desc, prio, freq, cat, h, m, dow, dom ->
                viewModel.addTask(context, title, desc, prio, freq, cat, h, m, dow, dom)
                showDialog = false
                // App remains open after scheduling a new task
            }
        )
    }
}

@Composable
fun SystemDesignModuleView(viewModel: TaskViewModel, savedPatterns: List<DsaPattern>) {
    val systemDesignContent = mapOf(
        "Design Basics" to listOf(
            "Architectures" to listOf("Microservices", "Monolithic", "Serverless", "Event-Driven Architecture"),
            "Scalability" to listOf("Load Balancers", "Horizontal vs Vertical Scaling", "Caching", "Database Sharding"),
            "Communication" to listOf("REST vs GraphQL", "gRPC", "Message Queues", "WebSockets")
        ),
        "Code Design" to listOf(
            "Creational" to listOf("Singleton", "Factory", "Builder", "Prototype"),
            "Structural" to listOf("Adapter", "Decorator", "Facade", "Proxy"),
            "Behavioral" to listOf("Strategy", "Observer", "Command", "State"),
            "SOLID" to listOf("S: Single Responsibility", "O: Open-Closed", "L: Liskov Substitution", "I: Interface Segregation", "D: Dependency Inversion")
        )
    )

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        systemDesignContent.forEach { (mainCategory, subItems) ->
            item {
                Text(mainCategory, style = MaterialTheme.typography.headlineSmall, color = Color(0xFF00796B), modifier = Modifier.padding(vertical = 12.dp))
            }
            subItems.forEach { (group, patterns) ->
                item {
                    Text(group, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.primary)
                }
                items(patterns) { pattern ->
                    val patternId = "SD_${group}_$pattern"
                    val isChecked = savedPatterns.find { it.id == patternId }?.isCompleted ?: false
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            viewModel.toggleDsaPattern(patternId, group, pattern)
                        }.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { viewModel.toggleDsaPattern(patternId, group, pattern) },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00BFA5))
                        )
                        Text(pattern, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun DsaModuleView(viewModel: TaskViewModel, savedPatterns: List<DsaPattern>) {
    val dsaContent = mapOf(
        "Basic Code" to listOf(
            "Arrays" to listOf("Two Pointers", "Sliding Window", "Prefix Sum", "Kadane's Algorithm"),
            "Sorting" to listOf("Binary Search", "Quick Sort", "Merge Sort", "Heap Sort"),
            "Lists" to listOf("Reverse List", "Cycle Check", "Fast & Slow Pointers")
        ),
        "Advanced Code" to listOf(
            "Trees" to listOf("DFS", "BFS", "Lowest Ancestor", "BST Check"),
            "Graphs" to listOf("Dijkstra", "Topo Sort", "DSU", "Floyd Warshall"),
            "Dynamic" to listOf("Knapsack", "Subsequence", "LIS", "Climbing Stairs")
        )
    )

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        dsaContent.forEach { (mainCategory, subItems) ->
            item {
                Text(mainCategory, style = MaterialTheme.typography.headlineSmall, color = Color(0xFFE65100), modifier = Modifier.padding(vertical = 12.dp))
            }
            subItems.forEach { (structure, patterns) ->
                item {
                    Text(structure, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.primary)
                }
                items(patterns) { pattern ->
                    val patternId = "DSA_${structure}_$pattern"
                    val isChecked = savedPatterns.find { it.id == patternId }?.isCompleted ?: false
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            viewModel.toggleDsaPattern(patternId, structure, pattern)
                        }.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { viewModel.toggleDsaPattern(patternId, structure, pattern) },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF4CAF50))
                        )
                        Text(pattern, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun TaskList(tasks: List<Task>, viewModel: TaskViewModel) {
    val context = LocalContext.current
    if (tasks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No tasks yet. Tap '+' to add one.", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            item { 
                Text("Your Schedule", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp), color = Color(0xFFE65100)) 
            }
            items(tasks, key = { it.id }) { task ->
                TaskItem(
                    task = task,
                    onCheckedChange = { 
                        if (task.status != TaskStatus.ACHIEVED) {
                            viewModel.setTaskCompletion(task, true)
                        }
                    },
                    onDelete = { viewModel.deleteTask(context, task) }
                )
            }
        }
    }
}

@Composable
fun GeminiChatScreen(viewModel: TaskViewModel, chatMessages: List<Pair<String, Boolean>>) {
    var message by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size)
        }
    }
    
    Column(modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { ChatBubble("Hello! I am BeakAI assist. How can I help you today?", false) }
                items(chatMessages) { (msg, isUser) -> ChatBubble(msg, isUser) }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Surface(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            tonalElevation = 8.dp,
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask BeakAI...", style = MaterialTheme.typography.bodyMedium) },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent
                    ),
                    maxLines = 4
                )
                Spacer(Modifier.width(8.dp))
                FloatingActionButton(
                    onClick = { 
                        if (message.isNotBlank()) { 
                            viewModel.askGemini(message)
                            message = "" 
                        } 
                    },
                    containerColor = Color(0xFFFF9800),
                    modifier = Modifier.size(44.dp),
                    elevation = FloatingActionButtonDefaults.elevation(0.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: String, isUser: Boolean) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            color = if (isUser) Color(0xFFFF9800) else Color(0xFFEEEEEE),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 0.dp,
                bottomEnd = if (isUser) 0.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                message, 
                modifier = Modifier.padding(12.dp), 
                color = if (isUser) Color.White else Color.Black,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun TaskItem(task: Task, onCheckedChange: (Boolean) -> Unit, onDelete: () -> Unit) {
    val statusColor = when (task.status) {
        TaskStatus.ACHIEVED -> Color(0xFF4CAF50) // Green
        TaskStatus.EXPIRED -> Color(0xFFF44336) // Red
        TaskStatus.ACTIVE -> Color(0xFF2196F3) // Blue
    }
    
    val statusLabel = when (task.status) {
        TaskStatus.ACHIEVED -> "Task Completed"
        TaskStatus.EXPIRED -> "Expired"
        TaskStatus.ACTIVE -> "Active"
    }

    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, task.hour)
        set(Calendar.MINUTE, task.minute)
    }
    val formattedTime = timeFormat.format(calendar.time)

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth()
                .clickable(enabled = task.status != TaskStatus.ACHIEVED) {
                    onCheckedChange(true)
                }, 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.status == TaskStatus.ACHIEVED, 
                onCheckedChange = null,
                enabled = task.status != TaskStatus.ACHIEVED,
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF4CAF50),
                    uncheckedColor = if (task.status == TaskStatus.EXPIRED) Color(0xFFF44336) else Color(0xFF2196F3),
                    disabledCheckedColor = Color(0xFF4CAF50),
                    disabledUncheckedColor = Color.Gray.copy(alpha = 0.4f)
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (task.status == TaskStatus.ACHIEVED) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = statusLabel, 
                        style = MaterialTheme.typography.labelSmall, 
                        color = statusColor
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
fun QuestionsList(questions: List<Question>) {
    if (questions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No questions saved.", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(questions) { q ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(q.question, style = MaterialTheme.typography.titleMedium, color = Color(0xFFE65100))
                        Spacer(Modifier.height(8.dp))
                        Text(q.answer, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTaskDialog(
    initialFrequency: TaskFrequency,
    initialCategory: TaskCategory,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, TaskFrequency, TaskCategory, Int, Int, Int?, Int?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var hour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MINUTE)) }
    var frequency by remember { mutableStateOf(initialFrequency) }
    var category by remember { mutableStateOf(initialCategory) }
    
    var showTimePicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = false)

    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val displayCalendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    
    val timeRemaining = remember(hour, minute) {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) {
                add(Calendar.DATE, 1)
            }
        }
        val diff = target.timeInMillis - now.timeInMillis
        val hours = diff / (1000 * 60 * 60)
        val minutes = (diff / (1000 * 60)) % 60
        if (hours > 0) "$hours hr $minutes min remaining" else "$minutes min remaining"
    }

    if (showTimePicker) {
        DatePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    hour = timePickerState.hour
                    minute = timePickerState.minute
                    showTimePicker = false
                }) { Text("Set Time") }
            }
        ) {
            TimePicker(state = timePickerState, modifier = Modifier.padding(16.dp))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Premium Task Schedule", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title, 
                    onValueChange = { title = it }, 
                    label = { Text("Task Name", style = MaterialTheme.typography.bodyMedium) },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(TaskCategory.TASK, TaskCategory.STUDY, TaskCategory.MEETING).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }
                
                OutlinedCard(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessTime, null, tint = Color(0xFFE65100))
                            Spacer(Modifier.width(12.dp))
                            Text("Alarm: ${timeFormat.format(displayCalendar.time)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = timeRemaining,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(start = 36.dp, top = 4.dp)
                        )
                    }
                }
                
                Text("Repetition", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskFrequency.entries.forEach { f ->
                        FilterChip(
                            selected = frequency == f, 
                            onClick = { frequency = f }, 
                            label = { Text(f.name.lowercase(), style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, "", 1, frequency, category, hour, minute, null, null) },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
            ) {
                Text("Schedule Alarm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
