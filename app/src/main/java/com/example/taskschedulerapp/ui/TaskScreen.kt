package com.example.taskschedulerapp.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.taskschedulerapp.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
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
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }

    val dateFormatter = remember { SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = System.currentTimeMillis()
        }
    }

    BackHandler(enabled = true) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            showChatbot -> showChatbot = false
            showQuestionsHistory -> showQuestionsHistory = false
            selectedCategory != TaskCategory.TASK -> {
                selectedCategory = TaskCategory.TASK
                selectedTabIndex = 0
            }
            selectedTabIndex != 0 -> selectedTabIndex = 0
            else -> showExitConfirm = true
        }
    }

    val freqTabs = remember { listOf("Daily", "Weekly", "Monthly", "Once") }
    
    val updatedTasks = remember(tasks, currentTime, selectedDate) {
        tasks.filter { task ->
            val calendar = Calendar.getInstance().apply { timeInMillis = selectedDate.timeInMillis }
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val month = calendar.get(Calendar.MONTH)
            val year = calendar.get(Calendar.YEAR)
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

            when (task.frequency) {
                TaskFrequency.DAILY -> true
                TaskFrequency.WEEKLY -> task.dayOfWeek == dayOfWeek
                TaskFrequency.MONTHLY -> task.dayOfMonth == day
                TaskFrequency.ONCE -> task.dayOfMonth == day && task.month == month && task.year == year
            }
        }.map { task ->
            val taskTime = Calendar.getInstance().apply {
                timeInMillis = selectedDate.timeInMillis
                set(Calendar.HOUR_OF_DAY, task.hour)
                set(Calendar.MINUTE, task.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            val status = if (task.isCompleted) TaskStatus.ACHIEVED 
                         else if (taskTime < currentTime) TaskStatus.EXPIRED 
                         else TaskStatus.ACTIVE
            
            task.copy(status = status, scheduledTimestamp = taskTime)
        }
    }

    val filteredSortedTasks = remember(updatedTasks, selectedTabIndex) {
        val freq = TaskFrequency.entries[selectedTabIndex]
        updatedTasks.filter { it.frequency == freq }.sortedWith(
            compareBy<Task> { 
                when(it.status) {
                    TaskStatus.ACHIEVED -> 0
                    TaskStatus.ACTIVE -> 1
                    TaskStatus.EXPIRED -> 2
                }
            }.thenBy { it.scheduledTimestamp }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Column(Modifier.fillMaxHeight().padding(16.dp)) {
                    Text("BeakAI", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFF9800))
                    Text(userEmail ?: "user@beakai.app", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    
                    Spacer(Modifier.height(32.dp))
                    
                    NavigationDrawerItem(
                        label = { Text("Daily Schedule") },
                        selected = selectedCategory == TaskCategory.TASK && !showQuestionsHistory && !showChatbot,
                        onClick = { selectedCategory = TaskCategory.TASK; showQuestionsHistory = false; showChatbot = false; scope.launch { drawerState.close() } },
                        icon = { Icon(Icons.Default.DateRange, null) },
                        modifier = Modifier.padding(horizontal = 0.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("AI Assistant") },
                        selected = showChatbot,
                        onClick = { showChatbot = true; showQuestionsHistory = false; selectedCategory = null; scope.launch { drawerState.close() } },
                        icon = { Icon(Icons.Default.AutoAwesome, null) },
                        modifier = Modifier.padding(horizontal = 0.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("DSA Tracker") },
                        selected = selectedCategory == TaskCategory.DSA,
                        onClick = { selectedCategory = TaskCategory.DSA; showQuestionsHistory = false; showChatbot = false; scope.launch { drawerState.close() } },
                        icon = { Icon(Icons.Default.Code, null) },
                        modifier = Modifier.padding(horizontal = 0.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("Learning History") },
                        selected = showQuestionsHistory,
                        onClick = { showQuestionsHistory = true; showChatbot = false; selectedCategory = null; scope.launch { drawerState.close() } },
                        icon = { Icon(Icons.Default.History, null) },
                        modifier = Modifier.padding(horizontal = 0.dp)
                    )

                    Spacer(Modifier.weight(1f))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    NavigationDrawerItem(
                        label = { Text("Logout") },
                        selected = false,
                        onClick = { showLogoutConfirm = true },
                        icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) },
                        modifier = Modifier.padding(horizontal = 0.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("Exit") },
                        selected = false,
                        onClick = { showExitConfirm = true },
                        icon = { Icon(Icons.Default.PowerSettingsNew, null) },
                        modifier = Modifier.padding(horizontal = 0.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Powered by Kishore Saravanan", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = (selectedCategory?.name ?: if(showQuestionsHistory) "DAILY INTERVIEW Qs" else if(showChatbot) "AI ASSISTANT" else "TASKS").uppercase(), 
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    },
                    navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, null) } },
                    actions = {
                        IconButton(onClick = { onThemeToggle() }) { Icon(if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode, null) }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            floatingActionButton = {
                // BUG FIX: Hide FABs when chatbot is open to ensure send button visibility
                if (!showChatbot) {
                    Column(horizontalAlignment = Alignment.End) {
                        FloatingActionButton(
                            onClick = { showChatbot = true; showQuestionsHistory = false; selectedCategory = null },
                            containerColor = Color(0xFF00BFA5),
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.padding(bottom = 16.dp).size(56.dp)
                        ) {
                            Icon(Icons.Default.Face, "AI Assistant")
                        }
                        FloatingActionButton(
                            onClick = { showDialog = true },
                            containerColor = Color(0xFFFF9800),
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(Icons.Default.Add, "Add Task", modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                Column {
                    if (selectedCategory != TaskCategory.DSA && selectedCategory != TaskCategory.SYSTEM_DESIGN && !showQuestionsHistory && !showChatbot) {
                        TabRow(
                            selectedTabIndex = selectedTabIndex,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = Color(0xFFFF9800),
                            indicator = { tabPositions -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]), color = Color(0xFFFF9800)) },
                            divider = {}
                        ) {
                            freqTabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTabIndex == index, 
                                    onClick = { selectedTabIndex = index }, 
                                    text = { Text(title, fontSize = 14.sp, fontWeight = if(selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal) },
                                    selectedContentColor = Color(0xFFFF9800),
                                    unselectedContentColor = Color.Gray
                                )
                            }
                        }
                        
                        Text(
                            text = dateFormatter.format(selectedDate.time),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 16.dp)
                        )
                    }

                    when {
                        showChatbot -> GeminiChatScreen(viewModel, chatMessages)
                        showQuestionsHistory -> QuestionsHistoryView(questions)
                        selectedCategory == TaskCategory.DSA -> DsaModuleView(viewModel, dsaPatterns.filter { it.category == "Arrays" || it.category == "Trees" || it.category == "Graphs" || it.category == "Sorting" })
                        selectedCategory == TaskCategory.SYSTEM_DESIGN -> SystemDesignModuleView(viewModel, dsaPatterns.filter { it.category == "Scalability" || it.category == "Reliability" || it.category == "Databases" || it.category == "Communication" })
                        else -> {
                            if (filteredSortedTasks.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No tasks scheduled for this day.", color = Color.Gray)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(filteredSortedTasks, key = { it.id }) { task ->
                                        TaskItem(
                                            task = task,
                                            currentTime = currentTime,
                                            onDelete = { viewModel.deleteTask(task) },
                                            onToggleCompletion = { completed -> 
                                                viewModel.setTaskCompletion(task, completed)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AddTaskDialog(
            onDismiss = { showDialog = false },
            onConfirm = { name, desc, h, m, freq, cat, d, mo, y, dow ->
                viewModel.addTask(
                    title = name,
                    description = desc,
                    priority = 1,
                    frequency = freq,
                    category = cat,
                    hour = h,
                    minute = m,
                    dayOfMonth = d,
                    month = mo,
                    year = y,
                    dayOfWeek = dow
                )
                showDialog = false
            },
            initialCategory = selectedCategory ?: TaskCategory.TASK
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to logout?") },
            confirmButton = { TextButton(onClick = { onLogout(); showLogoutConfirm = false }) { Text("Logout", color = Color.Red) } },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Exit App") },
            text = { Text("Do you want to exit BeakAI?") },
            confirmButton = { TextButton(onClick = { (context as? Activity)?.finish(); showExitConfirm = false }) { Text("Exit") } },
            dismissButton = { TextButton(onClick = { showExitConfirm = false }) { Text("Stay") } }
        )
    }
}

@Composable
fun TaskItem(task: Task, currentTime: Long, onDelete: (Task) -> Unit, onToggleCompletion: (Boolean) -> Unit) {
    val remainingMillis = task.scheduledTimestamp - currentTime
    val isExpired = task.status == TaskStatus.EXPIRED
    val isAchieved = task.status == TaskStatus.ACHIEVED
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )
    val animatedElevation by animateDpAsState(
        targetValue = if (isPressed) 12.dp else 4.dp,
        label = "elevation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(animatedScale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { /* Tactile interaction feedback */ }
            )
            .shadow(animatedElevation, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isAchieved -> Color(0xFF2E7D32) // FULL GREEN
                isExpired -> Color(0xFFC62828)  // FULL RED
                else -> Color(0xFF1565C0)       // FULL BLUE (ACTIVE)
            },
            contentColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = animatedElevation),
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isAchieved,
                onCheckedChange = { onToggleCompletion(it) },
                colors = CheckboxDefaults.colors(
                    checkedColor = Color.White,
                    uncheckedColor = Color.White.copy(alpha = 0.6f),
                    checkmarkColor = when {
                        isAchieved -> Color(0xFF2E7D32)
                        isExpired -> Color(0xFFC62828)
                        else -> Color(0xFF1565C0)
                    }
                )
            )
            
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (isAchieved) TextDecoration.LineThrough else null,
                )
                if (task.description.isNotBlank()) {
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 2
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(16.dp), tint = Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = formatTimeWithAmPm(task.hour, task.minute),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                if (!isAchieved && !isExpired) {
                    Text("REMAINING", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Black)
                    Text(
                        formatCountdown(remainingMillis),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                } else if (isAchieved) {
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("ACHIEVED", fontWeight = FontWeight.Black, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                } else {
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("EXPIRED", fontWeight = FontWeight.Black, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
            
            Spacer(Modifier.width(12.dp))
            
            IconButton(onClick = { onDelete(task) }) { 
                Icon(Icons.Default.Delete, "Delete", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp)) 
            }
        }
    }
}

fun formatTimeWithAmPm(hour: Int, minute: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(calendar.time)
}

fun formatCountdown(millis: Long): String {
    if (millis <= 0) return "00:00:00"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, Int, TaskFrequency, TaskCategory, Int?, Int?, Int?, Int?) -> Unit,
    initialCategory: TaskCategory
) {
    var taskName by remember { mutableStateOf("") }
    var taskDescription by remember { mutableStateOf("") }
    var hour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MINUTE)) }
    var frequency by remember { mutableStateOf(TaskFrequency.DAILY) }
    var category by remember { mutableStateOf(initialCategory) }
    
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    var showTimePicker by remember { mutableStateOf(false) }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    hour = timePickerState.hour
                    minute = timePickerState.minute
                    showTimePicker = false
                }) { Text("OK", color = Color(0xFFFF9800)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel", color = Color.Gray) }
            },
            text = {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Mandatory AM/PM Time Picker", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Spacer(Modifier.height(16.dp))
                        TimePicker(state = timePickerState)
                    }
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Mandatory Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFF9800))
                Spacer(Modifier.height(16.dp))
                
                // MANDATORY FIELD: TASK NAME
                OutlinedTextField(
                    value = taskName,
                    onValueChange = { taskName = it },
                    label = { Text("Task Name (Mandatory)") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = taskName.isBlank(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(8.dp))
                
                // MANDATORY FIELD: TASK DESCRIPTION
                OutlinedTextField(
                    value = taskDescription,
                    onValueChange = { taskDescription = it },
                    label = { Text("Task Description (Mandatory)") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = taskDescription.isBlank(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2
                )
                
                Spacer(Modifier.height(16.dp))
                val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate.timeInMillis)
                DatePicker(
                    state = datePickerState, 
                    showModeToggle = false, 
                    title = null, 
                    headline = null,
                    colors = DatePickerDefaults.colors(
                        selectedDayContainerColor = Color(0xFFFF9800),
                        todayDateBorderColor = Color(0xFFFF9800)
                    )
                )
                LaunchedEffect(datePickerState.selectedDateMillis) {
                    datePickerState.selectedDateMillis?.let { selectedDate.timeInMillis = it }
                }

                Spacer(Modifier.height(16.dp))
                Text("Set Alarm (AM/PM Mandatory)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Alarm Time: ", style = MaterialTheme.typography.bodyLarge)
                            Text(formatTimeWithAmPm(hour, minute), 
                                color = Color(0xFFFF9800), fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Button(onClick = { showTimePicker = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))) {
                                Text("Select Time")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Repetitions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskFrequency.entries.forEach { freq ->
                        val selected = frequency == freq
                        Surface(
                            modifier = Modifier.weight(1f).clickable { frequency = freq },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if(selected) Color.Transparent else Color.Gray),
                            color = if(selected) Color(0xFF3F3F46) else Color.Transparent
                        ) {
                            Text(freq.name.lowercase().replaceFirstChar { it.uppercase() }, modifier = Modifier.padding(vertical = 8.dp), textAlign = TextAlign.Center,
                                color = if(selected) Color.White else Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel", color = Color(0xFFFF9800), fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { 
                            if (taskName.isNotBlank() && taskDescription.isNotBlank()) {
                                onConfirm(taskName, taskDescription, hour, minute, frequency, category, selectedDate.get(Calendar.DAY_OF_MONTH), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.DAY_OF_WEEK))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if(taskName.isNotBlank() && taskDescription.isNotBlank()) Color(0xFF3F3F46) else Color.Gray),
                        shape = RoundedCornerShape(24.dp),
                        enabled = taskName.isNotBlank() && taskDescription.isNotBlank()
                    ) {
                        Text("Finalize Task", color = Color.LightGray)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
fun QuestionsHistoryView(questions: List<Question>) {
    val sdf = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Your Daily Learning", style = MaterialTheme.typography.headlineMedium, color = Color(0xFFE65100), fontWeight = FontWeight.Bold) }
        items(questions.reversed()) { q ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(sdf.format(Date(q.timestamp)), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    Text("Q: ${q.question}", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("A: ${q.answer}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun GeminiChatScreen(vm: TaskViewModel, msgs: List<Pair<String, Boolean>>) {
    val apiKey by vm.geminiApiKey.collectAsState()
    var text by remember { mutableStateOf("") }
    var showKeyDialog by remember { mutableStateOf(false) }
    var tempKey by remember { mutableStateOf(apiKey) }
    
    val listState = rememberLazyListState()
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1) }

    if (showKeyDialog) {
        AlertDialog(
            onDismissRequest = { showKeyDialog = false },
            title = { Text("Gemini API Key") },
            text = {
                OutlinedTextField(
                    value = tempKey,
                    onValueChange = { tempKey = it },
                    label = { Text("Enter Google AI Studio Key") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.updateGeminiApiKey(tempKey)
                    showKeyDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showKeyDialog = false }) { Text("Cancel") } }
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Chat with BeakAI Mentor", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { showKeyDialog = true }) {
                Icon(Icons.Default.Settings, "API Key Settings", tint = Color.Gray)
            }
        }
        
        if (apiKey.isBlank()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.VpnKey, null, modifier = Modifier.size(48.dp), tint = Color.LightGray)
                    Text("API Key Required", fontWeight = FontWeight.Bold)
                    Text("Tap the settings icon above to set your key.", textAlign = TextAlign.Center, color = Color.Gray)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), state = listState) { items(msgs) { (m, user) -> ChatBubble(m, user) } }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            OutlinedTextField(
                value = text, 
                onValueChange = { text = it }, 
                modifier = Modifier.weight(1f), 
                shape = RoundedCornerShape(24.dp),
                placeholder = { Text("Ask for a technical solution...") },
                enabled = apiKey.isNotBlank()
            )
            // MANDATORY PROMINENT SEND BUTTON
            FilledIconButton(
                onClick = { if(text.isNotBlank()) { vm.askGemini(text); text = "" } }, 
                enabled = apiKey.isNotBlank() && text.isNotBlank(),
                modifier = Modifier.padding(start = 8.dp).size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFFF9800))
            ) { 
                Icon(
                    Icons.AutoMirrored.Filled.Send, 
                    null, 
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun ChatBubble(message: String, isUser: Boolean) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            color = if (isUser) Color(0xFFFF9800) else Color(0xFFEEEEEE), 
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (isUser) 16.dp else 0.dp, bottomEnd = if (isUser) 0.dp else 16.dp), 
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
fun DsaModuleView(vm: TaskViewModel, patterns: List<DsaPattern>) {
    val categories = remember { listOf("Arrays", "Trees", "Graphs", "Sorting") }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item { Text("DSA Tracker", style = MaterialTheme.typography.headlineMedium, color = Color(0xFFE65100), fontWeight = FontWeight.Bold) }
        items(categories) { cat ->
            Text(cat, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.primary)
            val filteredPatterns = patterns.filter { it.category == cat }
            if (filteredPatterns.isEmpty()) Text("No patterns saved.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            else {
                Column {
                    filteredPatterns.forEach { item -> 
                        Row(verticalAlignment = Alignment.CenterVertically) { 
                            Checkbox(checked = item.isCompleted, onCheckedChange = { vm.toggleDsaPattern(item.id, cat, item.patternName) })
                            Text(item.patternName) 
                        } 
                    }
                }
            }
        }
    }
}

@Composable
fun SystemDesignModuleView(vm: TaskViewModel, patterns: List<DsaPattern>) {
    val categories = remember { listOf("Scalability", "Reliability", "Databases", "Communication") }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item { Text("System Design Library", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF00BFA5), fontWeight = FontWeight.Bold) }
        items(categories) { cat ->
            Text(cat, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.primary)
            val filteredPatterns = patterns.filter { it.category == cat }
            if (filteredPatterns.isEmpty()) Text("No topics found.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            else {
                Column {
                    filteredPatterns.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) { 
                            Checkbox(checked = item.isCompleted, onCheckedChange = { vm.toggleDsaPattern(item.id, cat, item.patternName) })
                            Text(item.patternName) 
                        } 
                    }
                }
            }
        }
    }
}
