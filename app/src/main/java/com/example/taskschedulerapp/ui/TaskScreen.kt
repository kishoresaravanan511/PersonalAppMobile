package com.example.taskschedulerapp.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskschedulerapp.data.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskScreen(viewModel: TaskViewModel) {
    val tasks by viewModel.tasks.collectAsState()
    val questions by viewModel.questions.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val dsaPatterns by viewModel.dsaPatterns.collectAsState()
    
    var showDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<TaskCategory?>(TaskCategory.TASK) }
    var showQuestionsHistory by remember { mutableStateOf(false) }
    var showChatbot by remember { mutableStateOf(false) }
    var showSystemDesign by remember { mutableStateOf(false) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val freqTabs = listOf("Daily", "Weekly", "Monthly", "Once")
    
    val updatedTasks = remember(tasks) {
        tasks.map { task ->
            val now = Calendar.getInstance()
            val taskTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, task.hour)
                set(Calendar.MINUTE, task.minute)
                set(Calendar.SECOND, 0)
            }
            when {
                task.isCompleted -> task.copy(status = TaskStatus.ACHIEVED)
                taskTime.before(now) && task.frequency == TaskFrequency.ONCE -> task.copy(status = TaskStatus.EXPIRED)
                else -> task.copy(status = TaskStatus.ACTIVE)
            }
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
                        "BeakAI Mastery",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFFE65100),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Your optimistic AI partner for Java excellence and interview success.",
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
                            Text("CORE TRACKER", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(16.dp), color = Color.Gray)
                        }
                        items(listOf(TaskCategory.TASK, TaskCategory.MEETING, TaskCategory.STUDY)) { category ->
                            NavigationDrawerItem(
                                label = { Text(category.name.lowercase().replaceFirstChar { it.uppercase() }) },
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
                            Text("INTERVIEW MASTERY", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(16.dp), color = Color.Gray)
                        }
                        
                        item {
                            NavigationDrawerItem(
                                label = { Text("DSA Patterns") },
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
                                label = { Text("System Design (HLD/LLD)") },
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
                                label = { Text("Interview Q&A") },
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
                    }
                }

                // Cloud sync is active in backend (removed UI label as requested)
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
                                showChatbot -> "BeakAI Smart Assistant"
                                showQuestionsHistory -> "Interview Prep"
                                showSystemDesign -> "System Design Roadmap"
                                selectedCategory == TaskCategory.DSA -> "DSA Pattern Mastery"
                                else -> selectedCategory?.name?.replace("_", " ") ?: "BeakAI"
                            }, fontWeight = FontWeight.Bold) 
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, null)
                            }
                        }
                    )
                    if (!showQuestionsHistory && !showChatbot && !showSystemDesign && selectedCategory != TaskCategory.DSA && selectedCategory != TaskCategory.PROJECTS) {
                        ScrollableTabRow(selectedTabIndex = selectedTabIndex) {
                            freqTabs.forEachIndexed { index, title ->
                                Tab(selected = selectedTabIndex == index, onClick = { selectedTabIndex = index }, text = { Text(title) })
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
                        // NEW OPTIMISTIC ICON FOR CHATBOT
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
            }
        )
    }
}

@Composable
fun SystemDesignModuleView(viewModel: TaskViewModel, savedPatterns: List<DsaPattern>) {
    val systemDesignContent = mapOf(
        "High Level Design (HLD)" to listOf(
            "Architectures" to listOf("Microservices", "Monolithic", "Serverless", "Event-Driven Architecture"),
            "Scalability" to listOf("Load Balancers", "Horizontal vs Vertical Scaling", "Caching (Redis/Memcached)", "Database Sharding"),
            "Communication" to listOf("REST vs GraphQL", "gRPC", "Message Queues (Kafka/RabbitMQ)", "WebSockets")
        ),
        "Low Level Design (LLD)" to listOf(
            "Creational Patterns" to listOf("Singleton", "Factory", "Builder", "Prototype"),
            "Structural Patterns" to listOf("Adapter", "Decorator", "Facade", "Proxy"),
            "Behavioral Patterns" to listOf("Strategy", "Observer", "Command", "State"),
            "SOLID Principles" to listOf("S: Single Responsibility", "O: Open-Closed", "L: Liskov Substitution", "I: Interface Segregation", "D: Dependency Inversion")
        )
    )

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        systemDesignContent.forEach { (mainCategory, subItems) ->
            item {
                Text(mainCategory, style = MaterialTheme.typography.headlineSmall, color = Color(0xFF00796B), modifier = Modifier.padding(vertical = 12.dp))
            }
            subItems.forEach { (group, patterns) ->
                item {
                    Text(group, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.primary)
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
        "Standard Patterns" to listOf(
            "Arrays & Hashing" to listOf("Two Pointers", "Sliding Window", "Prefix Sum", "Kadane's Algorithm"),
            "Sorting & Searching" to listOf("Binary Search", "Quick Sort", "Merge Sort", "Heap Sort"),
            "Linked Lists" to listOf("Reverse Linked List", "Cycle Detection", "Fast & Slow Pointers")
        ),
        "Advanced DSA" to listOf(
            "Trees" to listOf("DFS (Pre/In/Post)", "BFS (Level Order)", "Lowest Common Ancestor", "BST Validation"),
            "Graphs" to listOf("Dijkstra's Algorithm", "Topological Sort", "Disjoint Set Union (DSU)", "Floyd Warshall"),
            "DP Mastery" to listOf("0/1 Knapsack", "Longest Common Subsequence", "Longest Increasing Subsequence", "Climbing Stairs")
        )
    )

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        dsaContent.forEach { (mainCategory, subItems) ->
            item {
                Text(mainCategory, style = MaterialTheme.typography.headlineSmall, color = Color(0xFFE65100), modifier = Modifier.padding(vertical = 12.dp))
            }
            subItems.forEach { (structure, patterns) ->
                item {
                    Text(structure, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.primary)
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
            Text("No goals scheduled. Tap '+' to start.", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            item { 
                Text("Your Schedule", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp), color = Color(0xFFE65100)) 
            }
            items(tasks, key = { it.id }) { task ->
                TaskItem(
                    task = task,
                    onCheckedChange = { viewModel.toggleTaskCompletion(task) },
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
                item { ChatBubble("BeakAI: I am your technical mentor. Let's master Java and System Design together!", false) }
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
                    placeholder = { Text("Ask technical question...") },
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
        TaskStatus.ACHIEVED -> Color(0xFF4CAF50)
        TaskStatus.EXPIRED -> Color(0xFFF44336)
        TaskStatus.ACTIVE -> Color(0xFF2196F3)
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = task.isCompleted, 
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF4CAF50))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = task.status.name, 
                        style = MaterialTheme.typography.labelSmall, 
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:%02d", task.hour, task.minute), 
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
            Text("Prep materials will appear here.")
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(questions) { q ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(q.question, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
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
    val timePickerState = rememberTimePickerState(initialHour = hour, initialMinute = minute)

    if (showTimePicker) {
        DatePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    hour = timePickerState.hour
                    minute = timePickerState.minute
                    showTimePicker = false
                }) { Text("Confirm") }
            }
        ) {
            TimePicker(state = timePickerState, modifier = Modifier.padding(16.dp))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule BeakAI Goal", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title, 
                    onValueChange = { title = it }, 
                    label = { Text("Goal Title (Task/Meeting/Study)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(TaskCategory.TASK, TaskCategory.MEETING, TaskCategory.STUDY).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                
                OutlinedCard(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, null)
                        Spacer(Modifier.width(12.dp))
                        Text(String.format(Locale.getDefault(), "Alarm Time: %02d:%02d", hour, minute))
                    }
                }
                
                Text("Repetition", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskFrequency.entries.forEach { f ->
                        FilterChip(
                            selected = frequency == f, 
                            onClick = { frequency = f }, 
                            label = { Text(f.name.lowercase()) }
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
                Text("Schedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
