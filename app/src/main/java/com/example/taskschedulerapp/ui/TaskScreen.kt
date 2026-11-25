package com.example.taskschedulerapp.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var taskToAchieve by remember { mutableStateOf<Task?>(null) }
    
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Header date format: Tuesday, 26 Nov 2024
    val dateFormatter = remember { SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()) }

    // Real-time update for countdowns (every second)
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
        tasks.map { task ->
            val scheduledCal = Calendar.getInstance().apply {
                if (task.scheduledTimestamp > 0) {
                    timeInMillis = task.scheduledTimestamp
                } else {
                    set(Calendar.HOUR_OF_DAY, task.hour)
                    set(Calendar.MINUTE, task.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    task.dayOfMonth?.let { set(Calendar.DAY_OF_MONTH, it) }
                    task.month?.let { set(Calendar.MONTH, it) }
                    task.year?.let { set(Calendar.YEAR, it) }
                }
            }

            val isAchieved = if (task.isCompleted) {
                val lastComp = Calendar.getInstance().apply { timeInMillis = task.lastCompletedTimestamp }
                val targetDay = selectedDate
                when (task.frequency) {
                    TaskFrequency.DAILY -> lastComp.get(Calendar.DAY_OF_YEAR) == targetDay.get(Calendar.DAY_OF_YEAR) && 
                                          lastComp.get(Calendar.YEAR) == targetDay.get(Calendar.YEAR)
                    TaskFrequency.WEEKLY -> lastComp.get(Calendar.WEEK_OF_YEAR) == targetDay.get(Calendar.WEEK_OF_YEAR) && 
                                           lastComp.get(Calendar.YEAR) == targetDay.get(Calendar.YEAR)
                    TaskFrequency.MONTHLY -> lastComp.get(Calendar.MONTH) == targetDay.get(Calendar.MONTH) && 
                                            lastComp.get(Calendar.YEAR) == targetDay.get(Calendar.YEAR)
                    TaskFrequency.ONCE -> true
                }
            } else false

            val hasTimePassed = scheduledCal.timeInMillis < currentTime
            val finalStatus = when {
                isAchieved -> TaskStatus.ACHIEVED
                hasTimePassed -> TaskStatus.EXPIRED
                else -> TaskStatus.ACTIVE
            }
            task.copy(status = finalStatus, scheduledTimestamp = scheduledCal.timeInMillis)
        }
    }

    val filteredSortedTasks = remember(updatedTasks, selectedCategory, selectedTabIndex, selectedDate) {
        val selectedDayOfYear = selectedDate.get(Calendar.DAY_OF_YEAR)
        val selectedYear = selectedDate.get(Calendar.YEAR)
        val selectedDayStart = (selectedDate.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        updatedTasks.filter { 
            it.category == (selectedCategory ?: TaskCategory.TASK) && 
            (if (selectedCategory == TaskCategory.DSA || selectedCategory == TaskCategory.SYSTEM_DESIGN) true else {
                val taskCal = Calendar.getInstance().apply { timeInMillis = it.scheduledTimestamp }
                val matchesDate = taskCal.get(Calendar.DAY_OF_YEAR) == selectedDayOfYear &&
                                 taskCal.get(Calendar.YEAR) == selectedYear
                
                val targetFreq = when(selectedTabIndex) {
                    0 -> TaskFrequency.DAILY
                    1 -> TaskFrequency.WEEKLY
                    2 -> TaskFrequency.MONTHLY
                    else -> TaskFrequency.ONCE
                }
                val matchesFrequency = it.frequency == targetFreq
                
                if (it.frequency == TaskFrequency.DAILY) {
                    val creationDayStart = Calendar.getInstance().apply { 
                        timeInMillis = it.createdAt
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    selectedDayStart >= creationDayStart && matchesFrequency
                } else if (it.frequency == TaskFrequency.ONCE) {
                    matchesDate
                } else {
                    matchesFrequency && matchesDate
                }
            })
        }.sortedWith { t1, t2 ->
            if (t1.status != t2.status) t1.status.ordinal.compareTo(t2.status.ordinal)
            else t1.scheduledTimestamp.compareTo(t2.scheduledTimestamp)
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate.timeInMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { 
                        selectedDate = Calendar.getInstance().apply { timeInMillis = it } 
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                Spacer(Modifier.height(12.dp))
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("BeakAI assist", style = MaterialTheme.typography.titleLarge, color = Color(0xFFE65100))
                    Text("Your partner for growth.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                HorizontalDivider()
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item { Text("PLANNER", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(16.dp), color = Color.Gray) }
                    items(listOf(TaskCategory.TASK, TaskCategory.STUDY, TaskCategory.MEETING)) { category ->
                        NavigationDrawerItem(
                            label = { Text(category.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            selected = selectedCategory == category && !showQuestionsHistory && !showChatbot,
                            onClick = { selectedCategory = category; showQuestionsHistory = false; showChatbot = false; scope.launch { drawerState.close() } },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                    item { Text("LEARNING MODULES", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(16.dp), color = Color.Gray) }
                    item { NavigationDrawerItem(label = { Text("DSA Tracker") }, selected = selectedCategory == TaskCategory.DSA, icon = { Icon(Icons.Default.Code, null) }, onClick = { selectedCategory = TaskCategory.DSA; showChatbot = false; showQuestionsHistory = false; scope.launch { drawerState.close() } }, modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)) }
                    item { NavigationDrawerItem(label = { Text("System Design") }, selected = selectedCategory == TaskCategory.SYSTEM_DESIGN, icon = { Icon(Icons.Default.Architecture, null) }, onClick = { selectedCategory = TaskCategory.SYSTEM_DESIGN; showChatbot = false; showQuestionsHistory = false; scope.launch { drawerState.close() } }, modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)) }
                    item { NavigationDrawerItem(label = { Text("Daily Interview Qs") }, selected = showQuestionsHistory, icon = { Icon(Icons.Default.School, null) }, onClick = { showQuestionsHistory = true; showChatbot = false; selectedCategory = null; scope.launch { drawerState.close() } }, modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)) }
                    item { NavigationDrawerItem(label = { Text("AI Assistant") }, selected = showChatbot, icon = { Icon(Icons.AutoMirrored.Filled.Chat, null) }, onClick = { showChatbot = true; showQuestionsHistory = false; selectedCategory = null; scope.launch { drawerState.close() } }, modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)) }
                    
                    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)) {
                                Text(userEmail ?: "Guest", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                            }
                            NavigationDrawerItem(label = { Text("Logout") }, selected = false, icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) }, onClick = { showLogoutConfirm = true; scope.launch { drawerState.close() } })
                        }
                    }
                    item { NavigationDrawerItem(label = { Text("Exit") }, selected = false, icon = { Icon(Icons.Default.PowerSettingsNew, null) }, onClick = { showExitConfirm = true; scope.launch { drawerState.close() } }, modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)) }
                }
                
                Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Powered by Kishore Saravanan", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            text = if (showChatbot) "AI Assistant" 
                                   else if (showQuestionsHistory) "Interview Prep" 
                                   else selectedCategory?.name?.uppercase() ?: "PLANNER",
                            fontWeight = FontWeight.Bold
                        ) 
                    },
                    navigationIcon = { IconButton({ scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, null) } },
                    actions = {
                        IconButton({ onThemeToggle() }) {
                            Icon(if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode, contentDescription = "Toggle Theme")
                        }
                        IconButton({ currentTime = System.currentTimeMillis() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                )
            },
            floatingActionButton = {
                Column(horizontalAlignment = Alignment.End) {
                    FloatingActionButton(
                        onClick = { showChatbot = !showChatbot; selectedCategory = null; showQuestionsHistory = false },
                        containerColor = Color(0xFF00BFA5), // Teal circular bot icon from image
                        modifier = Modifier.padding(bottom = 16.dp),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Face, "BeakAI Assistance", tint = Color.White)
                    }
                    FloatingActionButton(
                        onClick = { showDialog = true },
                        containerColor = Color(0xFFFF9800), // Orange circular plus icon from image
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Add, "Add Task", tint = Color.White)
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when {
                    showChatbot -> GeminiChatScreen(viewModel, chatMessages)
                    showQuestionsHistory -> QuestionsList(questions)
                    selectedCategory == TaskCategory.DSA -> DsaModuleView(viewModel, dsaPatterns)
                    selectedCategory == TaskCategory.SYSTEM_DESIGN -> SystemDesignModuleView(viewModel, dsaPatterns)
                    else -> Column {
                        TabRow(
                            selectedTabIndex, 
                            containerColor = Color.Transparent, 
                            contentColor = Color(0xFFFF9800), 
                            divider = {},
                            indicator = { tabPositions ->
                                if (selectedTabIndex < tabPositions.size) {
                                    TabRowDefaults.SecondaryIndicator(
                                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                        color = Color(0xFFFF9800)
                                    )
                                }
                            }
                        ) {
                            freqTabs.forEachIndexed { index, title -> 
                                Tab(
                                    selectedTabIndex == index, 
                                    { selectedTabIndex = index }, 
                                    text = { 
                                        Text(
                                            title, 
                                            fontSize = 14.sp, 
                                            fontWeight = FontWeight.Medium,
                                            color = if (selectedTabIndex == index) Color(0xFFFF9800) else Color.Gray
                                        ) 
                                    }
                                ) 
                            }
                        }
                        
                        // Header showing current date in specified format: Day, Date Month Year
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = dateFormatter.format(selectedDate.time),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f).clickable { showDatePicker = true }
                            )
                        }
                        
                        TaskList(filteredSortedTasks, currentTime, viewModel) { taskToAchieve = it }
                    }
                }
            }
        }
    }

    if (taskToAchieve != null) {
        AlertDialog(onDismissRequest = { taskToAchieve = null }, title = { Text("Confirm Achievement") }, text = { Text("Have you completed this task?") }, confirmButton = { Button(onClick = { viewModel.setTaskCompletion(taskToAchieve!!, true); taskToAchieve = null }) { Text("Achieved") } }, dismissButton = { TextButton(onClick = { taskToAchieve = null }) { Text("Cancel") } })
    }
    if (showExitConfirm) {
        AlertDialog(onDismissRequest = { showExitConfirm = false }, title = { Text("Exit BeakAI") }, text = { Text("Do you want to exit?") }, confirmButton = { Button(onClick = { (context as? Activity)?.finish() }) { Text("Exit") } }, dismissButton = { TextButton(onClick = { showExitConfirm = false }) { Text("Cancel") } })
    }
    if (showLogoutConfirm) {
        AlertDialog(onDismissRequest = { showLogoutConfirm = false }, title = { Text("Sign Out") }, text = { Text("Are you sure you want to log out?") }, confirmButton = { Button(onClick = { onLogout(); showLogoutConfirm = false }) { Text("Logout") } }, dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel") } })
    }
    if (showDialog) {
        AddTaskDialog(
            initialFrequency = when(selectedTabIndex) { 0 -> TaskFrequency.DAILY; 1 -> TaskFrequency.WEEKLY; 2 -> TaskFrequency.MONTHLY; else -> TaskFrequency.ONCE },
            initialCategory = selectedCategory ?: TaskCategory.TASK,
            currentTime = currentTime,
            onDismiss = { showDialog = false },
            onConfirm = { t, d, p, f, c, h, m, dom, dow, mon, yr, ts -> viewModel.addTask(context, t, d, p, f, c, h, m, dom, dow, mon, yr, ts); showDialog = false }
        )
    }
}

@Composable
fun TaskList(tasks: List<Task>, currentTime: Long, viewModel: TaskViewModel, onAchieve: (Task) -> Unit) {
    val context = LocalContext.current
    if (tasks.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No tasks scheduled for this day.") }
    else LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        val groupedTasks = tasks.groupBy { it.status }
        
        // Priority wise grouping: Active first, then Achieved, then Expired (Missed)
        listOf(TaskStatus.ACTIVE, TaskStatus.ACHIEVED, TaskStatus.EXPIRED).forEach { status ->
            groupedTasks[status]?.let { statusTasks ->
                item {
                    Text(
                        text = when(status) {
                            TaskStatus.ACHIEVED -> "ACHIEVED"
                            TaskStatus.ACTIVE -> "ACTIVE"
                            TaskStatus.EXPIRED -> "EXPIRED"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = when(status) {
                            TaskStatus.ACHIEVED -> Color(0xFF2E7D32)
                            TaskStatus.ACTIVE -> Color(0xFF2196F3)
                            TaskStatus.EXPIRED -> Color(0xFFD32F2F)
                        },
                        modifier = Modifier.padding(8.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
                items(statusTasks, key = { it.id }) { task ->
                    TaskItem(task, currentTime, onCheckedChange = { onAchieve(task) }, onDelete = { viewModel.deleteTask(context, task) })
                }
            }
        }
    }
}

@Composable
fun TaskItem(task: Task, currentTime: Long, onCheckedChange: (Boolean) -> Unit, onDelete: () -> Unit) {
    val isAchieved = task.status == TaskStatus.ACHIEVED
    val isExpired = task.status == TaskStatus.EXPIRED
    val cal = remember(task.scheduledTimestamp) { Calendar.getInstance().apply { timeInMillis = task.scheduledTimestamp } }
    
    // Time formats matching user request
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val dayNameFormatter = remember { SimpleDateFormat("EEEE", Locale.getDefault()) } // Full day name
    
    val now = Calendar.getInstance().apply { timeInMillis = currentTime }
    val isToday = cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) && 
                  cal.get(Calendar.YEAR) == now.get(Calendar.YEAR)
    
    // dateLabel shows "Today," or "Tuesday, Nov 26, 2024"
    val dateLabel = if (isToday) "Today," else "${dayNameFormatter.format(cal.time)}, ${dateFormatter.format(cal.time)}"

    // Remaining time countdown including seconds
    val statusText = remember(task.scheduledTimestamp, currentTime, isAchieved, isExpired) {
        if (isAchieved) "Task Achieved" 
        else if (isExpired) "Missed"
        else {
            val diff = task.scheduledTimestamp - currentTime
            if (diff <= 0) "Missed"
            else {
                val h = diff / 3600000
                val m = (diff % 3600000) / 60000
                val s = (diff % 60000) / 1000
                if (h > 0) "${h} hr ${m} min ${s}s left" else if (m > 0) "${m} min ${s}s left" else "${s}s left"
            }
        }
    }

    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp), 
        colors = CardDefaults.cardColors(
            containerColor = when {
                isAchieved -> Color(0xFFE3F2FD) // Light blue background for achieved items from image
                else -> Color.Transparent
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(Modifier.padding(16.dp).fillMaxWidth().clickable(enabled = !isAchieved && !isExpired) { onCheckedChange(true) }, verticalAlignment = Alignment.CenterVertically) {
            // Checkbox/Icon Style matching image
            if (isAchieved) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2196F3), modifier = Modifier.size(24.dp))
            } else if (isExpired) {
                Box(Modifier.size(24.dp).border(2.dp, Color.Red, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(18.dp))
                }
            } else {
                Box(Modifier.size(24.dp).border(2.dp, Color(0xFF2196F3), RoundedCornerShape(4.dp)))
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.title, 
                    style = MaterialTheme.typography.titleMedium.copy(
                        textDecoration = if (isAchieved) TextDecoration.LineThrough else null, 
                        color = when {
                            isAchieved -> Color(0xFF2196F3)
                            isExpired -> Color.Red
                            else -> Color.Unspecified
                        },
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "Description: ${if(task.description.isBlank()) "N/A" else task.description}", 
                    style = MaterialTheme.typography.bodySmall, 
                    color = Color.Gray, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    if (!isAchieved && !isExpired) {
                        Icon(Icons.Default.Timer, null, modifier = Modifier.size(14.dp), tint = Color(0xFF00BFA5))
                        Spacer(Modifier.width(4.dp))
                    }
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (isAchieved) Color(0xFF2196F3) else if (isExpired) Color.Red else Color(0xFF2196F3)))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "$statusText  $dateLabel ${timeFormatter.format(cal.time)}", 
                        style = MaterialTheme.typography.labelSmall, 
                        color = if (isAchieved) Color.Gray else if (isExpired) Color.Red else Color.Gray
                    )
                }
            }
            
            IconButton({ onDelete() }) { 
                Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.6f), modifier = Modifier.size(20.dp)) 
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    initialFrequency: TaskFrequency,
    initialCategory: TaskCategory,
    currentTime: Long,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, TaskFrequency, TaskCategory, Int, Int, Int?, Int?, Int?, Int?, Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var freq by remember { mutableStateOf(initialFrequency) }
    var preAlert by remember { mutableStateOf(true) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    
    var hour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var min by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MINUTE)) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    val remainingTimeText = remember(datePickerState.selectedDateMillis, hour, min, currentTime) {
        val target = Calendar.getInstance().apply {
            datePickerState.selectedDateMillis?.let { timeInMillis = it }
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, min)
            set(Calendar.SECOND, 0)
        }
        val diff = target.timeInMillis - currentTime
        if (diff <= 0) "Time has passed"
        else {
            val hours = diff / (1000 * 60 * 60)
            val minutes = (diff / (1000 * 60)) % 60
            "Starts in ${hours}h ${minutes}m"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        content = {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Task Scheduling", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        IconButton(onDismiss) { Icon(Icons.Default.Close, null) }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(title, { title = it }, label = { Text("Task Name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(desc, { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    
                    Spacer(Modifier.height(16.dp))
                    Text("Select Date", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    DatePicker(datePickerState, showModeToggle = false, title = null, headline = null)
                    
                    Spacer(Modifier.height(16.dp))
                    Text("Set Alarm System", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f))) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                val amPm = if (hour >= 12) "PM" else "AM"
                                val displayHour = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }
                                Text("Alarm Time: $displayHour:${min.toString().padStart(2, '0')} $amPm", style = MaterialTheme.typography.bodyMedium)
                                Button(onClick = { showTimePicker = true }) {
                                    Text("Set Time")
                                }
                            }
                            Text(remainingTimeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                            
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(preAlert, { preAlert = it })
                                Column {
                                    Text("Pre-expiration alert", style = MaterialTheme.typography.bodyMedium)
                                    Text("Notify 5 min before alarm and notification", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }
                        }
                    }
                    
                    if (showTimePicker) {
                        val timePickerState = rememberTimePickerState(hour, min, is24Hour = false)
                        AlertDialog(
                            onDismissRequest = { showTimePicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    hour = timePickerState.hour
                                    min = timePickerState.minute
                                    showTimePicker = false
                                }) { Text("OK") }
                            },
                            title = { Text("Set Time") },
                            text = { TimePicker(timePickerState) }
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Repetitions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TaskFrequency.entries.forEach { f ->
                            FilterChip(
                                selected = freq == f,
                                onClick = { freq = f },
                                label = { Text(f.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(32.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onDismiss, Modifier.weight(1f)) { Text("Cancel") }
                        Button(
                            onClick = {
                                val cal = Calendar.getInstance().apply {
                                    datePickerState.selectedDateMillis?.let { timeInMillis = it }
                                    set(Calendar.HOUR_OF_DAY, hour)
                                    set(Calendar.MINUTE, min)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                onConfirm(title, desc, 1, freq, initialCategory, hour, min, cal.get(Calendar.DAY_OF_MONTH), null, cal.get(Calendar.MONTH), cal.get(Calendar.YEAR), cal.timeInMillis)
                            },
                            Modifier.weight(1f),
                            enabled = title.isNotBlank()
                        ) { Text("Finalize Alarm") }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    )
}

@Composable
fun QuestionsList(qs: List<Question>) {
    if (qs.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No questions saved yet.") }
    else LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        items(qs) { q -> Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) { Column(Modifier.padding(16.dp)) { Text(q.question, fontWeight = FontWeight.Bold); Text(q.answer) } } }
    }
}

@Composable
fun GeminiChatScreen(vm: TaskViewModel, msgs: List<Pair<String, Boolean>>) {
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(Modifier.weight(1f), state = listState) { items(msgs) { (m, user) -> ChatBubble(m, user) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), shape = RoundedCornerShape(24.dp))
            IconButton({ if(text.isNotBlank()) { vm.askGemini(text); text = "" } }) { Icon(Icons.AutoMirrored.Filled.Send, null) }
        }
    }
}

@Composable
fun ChatBubble(message: String, isUser: Boolean) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(color = if (isUser) Color(0xFFFF9800) else Color(0xFFEEEEEE), shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (isUser) 16.dp else 0.dp, bottomEnd = if (isUser) 0.dp else 16.dp), modifier = Modifier.widthIn(max = 280.dp) ) { Text(message, modifier = Modifier.padding(12.dp), color = if (isUser) Color.White else Color.Black, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
fun DsaModuleView(vm: TaskViewModel, patterns: List<DsaPattern>) {
    val categories = remember { listOf("Arrays", "Trees", "Graphs", "Sorting") }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item { Text("DSA Tracking", style = MaterialTheme.typography.headlineMedium, color = Color(0xFFE65100)) }
        items(categories) { cat ->
            Text(cat, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.primary)
            val p = patterns.filter { it.category == cat }
            if (p.isEmpty()) Text("No patterns.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            else {
                Column {
                    p.forEach { item -> 
                        Row(verticalAlignment = Alignment.CenterVertically) { 
                            // Enable checkboxes for DSA patterns
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
        item { Text("System Design Modules", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF00BFA5)) }
        items(categories) { cat ->
            Text(cat, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.primary)
            val p = patterns.filter { it.category == cat }
            if (p.isEmpty()) Text("No topics saved.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            else {
                Column {
                    p.forEach { item -> 
                        Row(verticalAlignment = Alignment.CenterVertically) { 
                            // Enable checkboxes for System Design patterns
                            Checkbox(checked = item.isCompleted, onCheckedChange = { vm.toggleDsaPattern(item.id, cat, item.patternName) })
                            Text(item.patternName) 
                        } 
                    }
                }
            }
        }
    }
}
