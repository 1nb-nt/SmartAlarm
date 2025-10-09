package com.example.alarmchatapp.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.alarmchatapp.R
import com.example.alarmchatapp.Alarm
import com.example.alarmchatapp.AppDatabase
import com.example.alarmchatapp.network.AlarmContract
import com.example.alarmchatapp.network.AlarmParser
import com.example.alarmchatapp.network.RetrofitClient
import com.example.alarmchatapp.ui.theme.AlarmListScreen
import com.example.alarmchatapp.utils.AlarmHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.alarmchatapp.chatroom.ChatMessageEntity
import com.example.alarmchatapp.chatroom.ChatDao
import kotlinx.serialization.json.contentOrNull

enum class Sender { User, App }
data class ChatMessage(val text: String, val sender: Sender)

@Composable
fun AppContent() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "chat") {
        composable("chat") { ChatScreen(onShow = { nav.navigate("alarms") }) }
        composable("alarms") { AlarmListScreen(onBack = { nav.popBackStack() }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(onShow: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf(TextFieldValue()) }
    var isProcessing by remember { mutableStateOf(false) }
    var isTyping by remember { mutableStateOf(false) }

    // iterative Q&A flow state
    var accumulator by remember { mutableStateOf<String?>(null) }     // carries all user texts in current flow
    var pendingQuestion by remember { mutableStateOf<String?>(null) } // last asked question, if any
    var inFlow by remember { mutableStateOf(false) }                   // whether awaiting follow-up

    // Show home again after each attempt finishes
    var alarmHandled by remember { mutableStateOf(false) }
    val showHome by remember { derivedStateOf { (!isTyping && !isProcessing) || alarmHandled } }

    val db = remember { AppDatabase.getDatabase(context) }
    val chatDao: ChatDao = remember { db.chatDao() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* no-op */ }

    // Ringtone picker launcher
    val pickSound = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val uri: Uri? = res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (uri != null) {
            context.getSharedPreferences("wow_prefs", Context.MODE_PRIVATE)
                .edit().putString("ringtone_uri", uri.toString()).apply()
            Toast.makeText(context, "Alarm sound set", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "No sound selected", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Optionally request POST_NOTIFICATIONS if desired
            // perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (perms.isNotEmpty()) {
            permissionLauncher.launch(perms.toTypedArray())
        }

        val now = System.currentTimeMillis()
        val last24 = withContext(Dispatchers.IO) { chatDao.lastSince(now - 24L * 60L * 60L * 1000L) }
        if (last24.isNotEmpty()) {
            messages.clear()
            val loaded = last24.map { ChatMessage(it.text, if (it.sender == "User") Sender.User else Sender.App) }
            messages.addAll(loaded.asReversed())
            messages.removeAll { it.text.equals("HELLO THERE", true) && it.sender == Sender.App }
        }
        if (messages.isEmpty()) {
            withContext(Dispatchers.IO) {
                chatDao.pruneOlderThan(now - 7L * 24L * 60L * 1000L)
                runCatching { chatDao.deleteByExactText("HELLO THERE") }
            }
        }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val openDrawer = { scope.launch { drawerState.open() } }
    val closeDrawer = { scope.launch { drawerState.close() } }

    val config = LocalConfiguration.current
    val screenWidthDp = config.screenWidthDp.dp
    val drawerTargetWidth: Dp = (screenWidthDp * 0.58f).coerceAtMost(280.dp)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(drawerTargetWidth),
                windowInsets = WindowInsets.statusBars
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Text("WOW Panel", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Manage Alarms",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { closeDrawer(); onShow() }
                            .padding(vertical = 8.dp)
                    )
                    Text(
                        "Choose alarm sound",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select alarm sound")
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    // Pre-select existing if saved
                                    val saved = context.getSharedPreferences("wow_prefs", Context.MODE_PRIVATE)
                                        .getString("ringtone_uri", null)
                                    val existing = saved?.let { Uri.parse(it) }
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
                                }
                                pickSound.launch(intent)
                                closeDrawer()
                            }
                            .padding(vertical = 8.dp)
                    )
                    Text(
                        "Invite",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val invite = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:")
                                    putExtra(Intent.EXTRA_SUBJECT, "Join me on WOW Assist")
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "I've been using WOW Assist for smart wake-ups and reminders.\n\nWOW Assist lets conversations in local languages.\n\nhttps://www.workofwisdomai.com/assist"
                                    )
                                }
                                runCatching { context.startActivity(invite) }
                                closeDrawer()
                            }
                            .padding(vertical = 8.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopBarWithActions(
                    onToggle = { openDrawer() },
                    onShare = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "I've been using WOW Assist for smart wake-ups and reminders.\n\nWOW Assist supports your language.\n\nhttps://www.workofwisdomai.com/assist"
                            )
                        }
                        context.startActivity(Intent.createChooser(share, "Share WOW Assist"))
                    }
                )
            },
            contentWindowInsets = WindowInsets(0)
        ) { inner ->
            Column(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(top = inner.calculateTopPadding())
            ) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    ChatScrollableContent(
                        isProcessing = isProcessing,
                        showWelcome = showHome,
                        messages = messages,
                        onCommandClick = { command ->
                            input = TextFieldValue(command)
                            isTyping = true
                            alarmHandled = false
                        },
                        forceShowTopOnFocus = isTyping
                    )
                }
                InputSection(
                    input = input,
                    onInputChange = {
                        input = it
                        isTyping = it.text.isNotBlank()
                        if (isTyping) alarmHandled = false
                    },
                    scope = scope,
                    context = context,
                    messages = messages,
                    onPersist = { uiMsg ->
                        scope.launch(Dispatchers.IO) {
                            chatDao.insert(
                                ChatMessageEntity(
                                    text = uiMsg.text,
                                    sender = uiMsg.sender.name,
                                    timeMillis = System.currentTimeMillis()
                                )
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    onProcessingChange = { processing ->
                        isProcessing = processing
                        if (processing) {
                            isTyping = true
                            alarmHandled = false
                        }
                    },
                    onFocusChange = { focused ->
                        isTyping = focused
                        if (focused) alarmHandled = false
                    },
                    onSubmit = {
                        isTyping = true
                        alarmHandled = false
                        scope.launch {
                            kotlinx.coroutines.delay(2000)
                            if (input.text.isBlank()) isTyping = false
                        }
                    },
                    // iterative flow lambdas
                    buildAccumulatedInput = { rawText ->
                        val zone = ZoneId.systemDefault()
                        val ianaId = zone.id
                        val nowOffset = OffsetDateTime.now(zone).toString()
                        accumulator = if (!inFlow) {
                            "Current datetime $nowOffset $ianaId\n\n$rawText"
                        } else {
                            (accumulator.orEmpty() + "\n" + rawText)
                        }
                        accumulator!!
                    },
                    onFlowAwaitQuestion = { q ->
                        val appMsg = ChatMessage(q, Sender.App)
                        messages.add(appMsg)
                        // persist question asynchronously
                        scope.launch(Dispatchers.IO) {
                            chatDao.insert(
                                ChatMessageEntity(
                                    text = appMsg.text,
                                    sender = appMsg.sender.name,
                                    timeMillis = System.currentTimeMillis()
                                )
                            )
                        }
                        pendingQuestion = q
                        inFlow = true
                    },
                    onFlowComplete = {
                        pendingQuestion = null
                        inFlow = false
                        accumulator = null
                        alarmHandled = true
                        isTyping = false
                    }
                )
            }
        }
    }
}
@Composable
private fun ChatScrollableContent(
    isProcessing: Boolean,
    showWelcome: Boolean,
    messages: List<ChatMessage>,
    onCommandClick: (String) -> Unit,
    forceShowTopOnFocus: Boolean
) {
    val listState = rememberLazyListState()

    LaunchedEffect(forceShowTopOnFocus) { if (forceShowTopOnFocus) listState.scrollToItem(0) }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        contentPadding = PaddingValues(bottom = 8.dp),
        verticalArrangement = Arrangement.Top
    ) {
        stickyHeader {
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
                WowLogoInline(isProcessing = isProcessing, height = 180.dp)
            }
        }
        if (showWelcome) {
            item { WelcomeSection(onCommandClick = onCommandClick) }
        }
        // Ascending order; user (purple) then app (grey)
        items(messages) { msg ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = if (msg.sender == Sender.User) Arrangement.End else Arrangement.Start
            ) {
                MessageBubble(text = msg.text, isUser = msg.sender == Sender.User)
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
fun TopBarWithActions(
    onToggle: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(44.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggle) { Icon(Icons.Filled.Menu, contentDescription = "Menu") }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = "Share") }
    }
}

@Composable
private fun WowLogoInline(isProcessing: Boolean, height: Dp) {
    Box(
        modifier = Modifier
            .height(height)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.wow_logo),
            contentDescription = "Wow Logo",
            modifier = Modifier
                .height(height)
                .fillMaxWidth(),
            contentScale = ContentScale.Fit
        )
        if (isProcessing) {
            val overlaySize = height * 0.28f
            Box(
                modifier = Modifier
                    .height(overlaySize)
                    .width(overlaySize),
                contentAlignment = Alignment.Center
            ) { RotatingClockOverlay(isProcessing = true, sizeDp = overlaySize) }
        }
    }
}

@Composable
fun WelcomeSection(onCommandClick: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        val gradient = Brush.horizontalGradient(listOf(Color(0xFF6A1B9A), Color.Black))
        Text(
            text = buildAnnotatedString {
                withStyle(style = SpanStyle(brush = gradient)) { append("Welcome to WOW Assist") }
            },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Try",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = false,
                onClick = { onCommandClick("Wake me up at 5 AM daily") },
                label = { Text("Wake me up at 5 AM daily") },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = false,
                onClick = { onCommandClick("Call mom every Friday at 7 PM") },
                label = { Text("Call mom every Friday at 7 PM") },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = false,
                onClick = { onCommandClick("Meeting tomorrow 3 PM") },
                label = { Text("Meeting tomorrow 3 PM") },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = false,
                onClick = { onCommandClick("Son's birthday on Oct 24; remind a week before") },
                label = { Text("Son's birthday on Oct 24; remind a week before") },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RotatingClockOverlay(isProcessing: Boolean, sizeDp: Dp) {
    if (!isProcessing) return
    val infinite = rememberInfiniteTransition(label = "clock-spin")
    val angle by infinite.animateFloat(
        0f, 360f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "angle"
    )
    Box(
        modifier = Modifier
            .size(sizeDp)
            .graphicsLayer { rotationZ = angle },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f

            drawLine(
                color = Color(0xFF6A1B9A),
                start = c,
                end = c.copy(y = c.y - r * 0.65f),
                strokeWidth = 6f
            )
            drawLine(
                color = Color(0xFF424242),
                start = c,
                end = c.copy(y = c.y - r * 0.45f),
                strokeWidth = 4f
            )
            drawCircle(Color(0xFF424242), radius = 6f, center = c)
        }
    }
}

@Composable
fun MessageList(messages: List<ChatMessage>) {
    LazyColumn(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .fillMaxHeight(),
        reverseLayout = true
    ) {
        items(messages.size) { idx ->
            val msg = messages[idx]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = if (msg.sender == Sender.User) Arrangement.End else Arrangement.Start
            ) {
                MessageBubble(text = msg.text, isUser = msg.sender == Sender.User)
            }
        }
    }
}

@Composable
private fun MessageBubble(text: String, isUser: Boolean) {
    val bg = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = if (isUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 12.dp, bottomEnd = 4.dp, bottomStart = 16.dp)
    } else {
        RoundedCornerShape(topStart = 12.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp)
    }
    Surface(color = bg, shape = shape, tonalElevation = 2.dp, shadowElevation = 2.dp) {
        Text(text = text, color = fg, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

@Composable
fun InputSection(
    input: TextFieldValue,
    onInputChange: (TextFieldValue) -> Unit,
    scope: CoroutineScope,
    context: Context,
    messages: MutableList<ChatMessage>,
    onPersist: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier,
    onProcessingChange: (Boolean) -> Unit = {},
    onFocusChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    buildAccumulatedInput: (String) -> String,
    onFlowAwaitQuestion: (String) -> Unit,
    onFlowComplete: () -> Unit
) {
    var hasFocus by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = input,
            onValueChange = {
                onInputChange(it)
                onFocusChange(it.text.isNotBlank() || hasFocus)
            },
            modifier = Modifier
                .weight(1f)
                .onFocusChanged {
                    hasFocus = it.isFocused
                    onFocusChange(it.isFocused || input.text.isNotBlank())
                },
            singleLine = true,
            placeholder = { Text("What should i remind you about ?") }
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = {
            val rawText = input.text.trim()
            if (rawText.isEmpty()) return@IconButton

            val userMsg = ChatMessage(rawText, Sender.User)
            messages.add(userMsg)
            onPersist(userMsg)

            onInputChange(TextFieldValue(""))
            onSubmit()

            scope.launch {
                var finishedCalled = false
                fun finishOnce() {
                    if (!finishedCalled) {
                        finishedCalled = true
                        onFlowComplete()
                    }
                }
                try {
                    onProcessingChange(true)

                    // Local recurring detection (bypass API if matched)
                    val lower = rawText.lowercase(Locale.getDefault())

                    val dayMap = mapOf(
                        "monday" to Calendar.MONDAY,
                        "tuesday" to Calendar.TUESDAY,
                        "wednesday" to Calendar.WEDNESDAY,
                        "thursday" to Calendar.THURSDAY,
                        "friday" to Calendar.FRIDAY,
                        "saturday" to Calendar.SATURDAY,
                        "sunday" to Calendar.SUNDAY
                    )

                    fun parseEveryParentheses(text: String): List<Int>? {
                        val m = Regex("""every\s*\(([^)]+)\)""").find(text) ?: return null
                        val inside = m.groupValues[1]
                        val tokens = inside.split(',', ';').flatMap { it.split(' ') }
                            .map { it.trim().lowercase(Locale.getDefault()) }
                            .filter { it.isNotBlank() }
                        val resolved = tokens.mapNotNull { token ->
                            when (token.take(3)) {
                                "mon" -> Calendar.MONDAY
                                "tue" -> Calendar.TUESDAY
                                "wed" -> Calendar.WEDNESDAY
                                "thu" -> Calendar.THURSDAY
                                "fri" -> Calendar.FRIDAY
                                "sat" -> Calendar.SATURDAY
                                "sun" -> Calendar.SUNDAY
                                else -> dayMap[token]
                            }
                        }.distinct()
                        return if (resolved.isNotEmpty()) resolved else null
                    }

                    fun extractTimeHM(text: String): Pair<Int, Int>? {
                        val timeRegex = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b""", RegexOption.IGNORE_CASE)
                        val mr = timeRegex.find(text) ?: return null
                        val h = mr.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
                        val min = mr.groupValues.getOrNull(2).orEmpty().ifBlank { "0" }.toIntOrNull() ?: 0
                        val ampmStr = mr.groupValues.getOrNull(3)?.lowercase(Locale.getDefault())
                        var hour24 = h
                        if (ampmStr == "pm" && h in 1..11) hour24 = h + 12
                        if (ampmStr == "am" && h == 12) hour24 = 0
                        if (hour24 in 0..23 && min in 0..59) return hour24 to min
                        return null
                    }

                    val isDaily = lower.contains("everyday") || lower.contains("every day") || lower.contains("daily")
                    val isWeekday = lower.contains("every weekday")
                    val isWeekend = lower.contains("every weekend")
                    val isSpecificDay = dayMap.keys.any { lower.contains("every $it") }
                    val fromParen = parseEveryParentheses(lower)

                    val recurringDetected = isDaily || isWeekday || isWeekend || isSpecificDay || (fromParen != null)

                    if (recurringDetected) {
                        val hm = extractTimeHM(lower)
                        if (hm == null) {
                            val msg = ChatMessage("Please include a time, e.g. 7:00 AM.", Sender.App)
                            messages.add(msg); onPersist(msg); finishOnce()
                            onProcessingChange(false); return@launch
                        }
                        val (hour, minute) = hm

                        val recurringDays: List<Int>? = when {
                            isDaily -> null // daily => handled as recurringDays=null
                            isWeekday -> listOf(
                                Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY
                            )
                            isWeekend -> listOf(Calendar.SATURDAY, Calendar.SUNDAY)
                            isSpecificDay -> {
                                val d = dayMap.entries.first { lower.contains("every ${it.key}") }.value
                                listOf(d)
                            }
                            else -> fromParen // e.g., every (mon, wed, fri)
                        }

                        val firstTrigger: Long = if (recurringDays == null || recurringDays.size == 7) {
                            AlarmHelper.computeNextDaily(hour, minute)
                        } else {
                            AlarmHelper.computeNextAmongDays(hour, minute, recurringDays)
                        }

                        val dao = AppDatabase.getDatabase(context).alarmDao()
                        val row = Alarm(
                            message = "Alarm",
                            triggerTimeMillis = firstTrigger,
                            isRecurring = true,
                            recurringDays = recurringDays,
                            initialNote = ""
                        )
                        val newId = dao.insert(row).toInt()
                        AlarmHelper.scheduleAlarmClockPublic(context, "Alarm", firstTrigger, newId, "")

                        val friendly = when {
                            recurringDays == null -> "daily"
                            recurringDays.size == 1 -> dayMap.entries.first { it.value == recurringDays.first() }.key.replaceFirstChar { it.uppercase() }
                            recurringDays.size == 2 && recurringDays.containsAll(listOf(Calendar.SATURDAY, Calendar.SUNDAY)) -> "weekend"
                            recurringDays.size == 5 && !recurringDays.contains(Calendar.SATURDAY) && !recurringDays.contains(Calendar.SUNDAY) -> "weekday"
                            else -> "selected days"
                        }
                        val confirm = ChatMessage("Scheduled $friendly at %02d:%02d".format(hour, minute), Sender.App)
                        messages.add(confirm); onPersist(confirm)
                        finishOnce()
                        onProcessingChange(false)
                        return@launch
                    }

                    // Fallback to existing API path (unchanged) when no local recurring pattern
                    val accumulated = buildAccumulatedInput(rawText)
                    val payload: Map<String, Any> = mapOf(
                        "objective" to "Alarm Generator",
                        "objective_key" to "alarm_generator",
                        "model" to "openai",
                        "inputs" to mapOf("user_input" to accumulated, "ctype" to "text")
                    )

                    val http = RetrofitClient.instance.getAlarmDetailsRaw(payload)
                    if (!http.isSuccessful) {
                        messages.add(ChatMessage("Timeout or server error (${http.code()}). Tap to retry.", Sender.App))
                        messages.add(ChatMessage("Retry ▶", Sender.App))
                        onPersist(ChatMessage("Timeout or server error (${http.code()}). Tap to retry.", Sender.App))
                        onPersist(ChatMessage("Retry ▶", Sender.App))
                        finishOnce()
                        return@launch
                    }
                    val bodyStr = http.body()?.string().orEmpty()
                    if (bodyStr.isBlank()) {
                        messages.add(ChatMessage("No response received. Tap to retry.", Sender.App))
                        messages.add(ChatMessage("Retry ▶", Sender.App))
                        onPersist(ChatMessage("No response received. Tap to retry.", Sender.App))
                        onPersist(ChatMessage("Retry ▶", Sender.App))
                        finishOnce()
                        return@launch
                    }

                    val innerJson = extractInnerJsonFromResponse(bodyStr)
                    if (innerJson == null) {
                        messages.add(ChatMessage("API returned no JSON block; nothing scheduled.", Sender.App))
                        onPersist(ChatMessage("API returned no JSON block; nothing scheduled.", Sender.App))
                        finishOnce()
                        return@launch
                    }

                    val rootEl = Json.parseToJsonElement(innerJson).jsonObject
                    val maybeQuestion = rootEl["question"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    if (maybeQuestion != null) {
                        onFlowAwaitQuestion(maybeQuestion)
                        return@launch
                    }

                    val parsed: AlarmContract = AlarmParser.parseAlarmJson(innerJson)
                    val (fixed, issues) = AlarmParser.validateAndFixAlarm(parsed)
                    Log.d("AlarmParser", "innerJson=$innerJson")
                    Log.d("AlarmParser", "fixed.notification=${fixed.notification}")
                    issues.forEach { Log.d("AlarmParser", it) }

                    val assistantReply = fixed.responseText?.trim().orEmpty()
                    if (assistantReply.isNotEmpty()) {
                        val appMsg = ChatMessage(assistantReply, Sender.App)
                        messages.add(appMsg)
                        onPersist(appMsg)
                    }

                    val title = (fixed.title ?: "").ifBlank { "Alarm" }

                    var isoList: List<String> = fixed.notification
                    if (isoList.isEmpty() && !fixed.datetime.isNullOrBlank()) {
                        isoList = listOf(fixed.datetime!!)
                    }

                    if (isoList.isEmpty()) {
                        val lower2 = rawText.lowercase(Locale.getDefault()).replace("on", " ")
                        val timeRegex = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b""", RegexOption.IGNORE_CASE)
                        val mr = timeRegex.find(lower2)
                        if (mr != null) {
                            val h = mr.groupValues.getOrNull(1)?.toIntOrNull()
                            val min = mr.groupValues.getOrNull(2).orEmpty().ifBlank { "0" }.toIntOrNull()
                            val ampmStr = mr.groupValues.getOrNull(3)?.lowercase(Locale.getDefault())
                            if (h != null && min != null && h in 0..23 && min in 0..59) {
                                var hour24 = h
                                if (ampmStr == "pm" && h in 1..11) hour24 = h + 12
                                if (ampmStr == "am" && h == 12) hour24 = 0
                                val now = Calendar.getInstance()
                                val cal = Calendar.getInstance().apply {
                                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                                    set(Calendar.HOUR_OF_DAY, hour24); set(Calendar.MINUTE, min)
                                }
                                if (cal.before(now)) cal.add(Calendar.DAY_OF_YEAR, 1)
                                val instant = java.time.Instant.ofEpochMilli(cal.timeInMillis)
                                val offset = ZoneId.systemDefault().rules.getOffset(instant)
                                val iso = java.time.OffsetDateTime.ofInstant(instant, offset).toString()
                                isoList = listOf(iso)
                            }
                        }
                    }

                    if (isoList.isEmpty()) {
                        messages.add(ChatMessage("No times from API or text; nothing scheduled.", Sender.App))
                        onPersist(ChatMessage("No times from API or text; nothing scheduled.", Sender.App))
                        finishOnce()
                        return@launch
                    }

                    val nowMs = System.currentTimeMillis()
                    val times: List<Long> = isoList.mapNotNull { iso ->
                        runCatching { java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
                    }.filter { it > nowMs }.distinct().sorted()
                    if (times.isEmpty()) {
                        messages.add(ChatMessage("No future times after validation; nothing scheduled.", Sender.App))
                        onPersist(ChatMessage("No future times after validation; nothing scheduled.", Sender.App))
                        finishOnce()
                        return@launch
                    }

                    val dao = AppDatabase.getDatabase(context).alarmDao()
                    val assistantReplyOrNote = assistantReply.ifBlank { fixed.responseText.orEmpty() }
                    var scheduled = 0
                    for (whenMillis in times) {
                        val row = Alarm(
                            message = title,
                            triggerTimeMillis = whenMillis,
                            isRecurring = false,
                            recurringDays = null,
                            initialNote = assistantReplyOrNote
                        )
                        val newId = dao.insert(row).toInt()
                        AlarmHelper.scheduleAlarmClockPublic(context, title, whenMillis, newId, assistantReplyOrNote)
                        scheduled++
                    }
                    finishOnce()
                } catch (e: Exception) {
                    Log.e("ChatScreen", "Error", e)
                    val err = ChatMessage("Failed: ${e.localizedMessage ?: "Unknown error"}", Sender.App)
                    messages.add(err)
                    onPersist(err)
                    finishOnce()
                } finally {
                    onProcessingChange(false)
                }
            }
        }) {
            Icon(imageVector = Icons.Filled.Send, contentDescription = "Send")
        }
    }
}

private fun extractInnerJsonFromResponse(raw: String): String? {
    val jsonObj = runCatching { Json { ignoreUnknownKeys = true }.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
    val resp = jsonObj["response"]?.jsonPrimitive?.content ?: return null
    val text = resp.replace("``````", "")
    val start = text.indexOf('{')
    if (start == -1) return null
    var depth = 0
    for (i in start until text.length) {
        when (text[i]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return text.substring(start, i + 1).trim()
            }
        }
    }
    return null
}
