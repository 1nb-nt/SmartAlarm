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
import androidx.compose.ui.unit.sp
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
import com.example.alarmchatapp.utils.ApiAlarm
import com.example.alarmchatapp.utils.ApiAlarmMapper
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

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
    var accumulator by remember { mutableStateOf<String?>(null) }
    var pendingQuestion by remember { mutableStateOf<String?>(null) }
    var inFlow by remember { mutableStateOf(false) }

    // clarify-first/fail-second counter
    var noTimeAttempts by remember { mutableStateOf(0) }

    // Show home again after each attempt finishes
    var alarmHandled by remember { mutableStateOf(false) }
    val showHome by remember { derivedStateOf { (!isTyping && !isProcessing) || alarmHandled } }

    val db = remember { AppDatabase.getDatabase(context) }
    val chatDao: ChatDao = remember { db.chatDao() }



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
                    val ver = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull() }
                    val versionLabel = buildString {
                        append("Version ")
                        if (ver != null) {
                            // Prefer versionName if available; fall back to longVersionCode
                            append(ver.versionName ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ver.longVersionCode.toString() else "1.0")
                        } else append("1.0")
                    }
                    Text(versionLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
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
                    // iterative flow hooks
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
                        noTimeAttempts = 0
                    },
                    // bridges for flow state
                    inFlowGetter = { inFlow },
                    inFlowSetter = { v -> inFlow = v },
                    noTimeGetter = { noTimeAttempts },
                    noTimeSetter = { v -> noTimeAttempts = v }
                )
            }
        }
    }
}

@Composable
fun WelcomeSection(onCommandClick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val gradient = Brush.horizontalGradient(listOf(Color(0xFF6A1B9A), Color.Black))
        Text(
            text = buildAnnotatedString { withStyle(style = SpanStyle(brush = gradient)) { append("Welcome to WOW Assist") } },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(text = "Try", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
    onFlowComplete: () -> Unit,
    inFlowGetter: () -> Boolean,
    inFlowSetter: (Boolean) -> Unit,
    noTimeGetter: () -> Int,
    noTimeSetter: (Int) -> Unit
) {
    var hasFocus by remember { mutableStateOf(false) }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
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
            messages.add(userMsg); onPersist(userMsg)
            onInputChange(TextFieldValue(""))
            onSubmit()
            if (!inFlowGetter()) noTimeSetter(0)

            scope.launch {
                var finishedCalled = false
                fun finishOnce() { if (!finishedCalled) { finishedCalled = true; onFlowComplete() } }

                fun handleNoTimeAndStop(clarifyText: String = "Please provide details for the alarm you'd like to set.") {
                    val attempts = noTimeGetter()
                    if (attempts == 0) {
                        val msg = ChatMessage(clarifyText, Sender.App); messages.add(msg); onPersist(msg); noTimeSetter(1)
                    } else {
                        val msg = ChatMessage("No times from API or text; nothing scheduled.", Sender.App)
                        messages.add(msg); onPersist(msg); noTimeSetter(0); onFlowComplete()
                    }
                    finishOnce()
                }

                fun isCustomDate(epochMs: Long): Boolean {
                    val zone = java.time.ZoneId.systemDefault()
                    val d = java.time.Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
                    val today = java.time.LocalDate.now(zone)
                    val tomorrow = today.plusDays(1)
                    return !(d == today || d == tomorrow)
                }
                fun makePrimaryTimeLine(epochMs: Long): String {
                    val t = java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
                    val h12 = if (t.hour % 12 == 0) 12 else (t.hour % 12)
                    val mm = t.minute.toString().padStart(2, '0')
                    val ampm = if (t.hour < 12) "am" else "pm"
                    return "Your reminder is set for $h12:$mm $ampm."
                }

                try {
                    onProcessingChange(true)

                    val accumulated = buildAccumulatedInput(rawText)
                    val payload: Map<String, Any> = mapOf(
                        "objective" to "Alarm Generator",
                        "objective_key" to "alarm_generator",
                        "model" to "openai",
                        "inputs" to mapOf("user_input" to accumulated, "ctype" to "text")
                    )

                    val http = RetrofitClient.instance.getAlarmDetailsRaw(payload)
                    if (!http.isSuccessful) {
                        val a = ChatMessage("Timeout or server error (${http.code()}). Tap to retry.", Sender.App); messages.add(a); onPersist(a)
                        val b = ChatMessage("Retry ▶", Sender.App); messages.add(b); onPersist(b)
                        finishOnce(); return@launch
                    }
                    val bodyStr = http.body()?.string().orEmpty()
                    if (bodyStr.isBlank()) {
                        val a = ChatMessage("No response received. Tap to retry.", Sender.App); messages.add(a); onPersist(a)
                        val b = ChatMessage("Retry ▶", Sender.App); messages.add(b); onPersist(b)
                        finishOnce(); return@launch
                    }

                    val innerJson = extractInnerJsonFromResponse(bodyStr) ?: run { handleNoTimeAndStop(); return@launch }
                    val rootEl = Json.parseToJsonElement(innerJson).jsonObject

                    val maybeQuestion = rootEl["question"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    if (maybeQuestion != null) { onFlowAwaitQuestion(maybeQuestion); inFlowSetter(true); return@launch }

                    val parsed: AlarmContract = AlarmParser.parseAlarmJson(innerJson)
                    val (fixed, issues) = AlarmParser.validateAndFixAlarm(parsed)
                    Log.d("AlarmParser", "innerJson=$innerJson")
                    Log.d("AlarmParser", "fixed.notification=${fixed.notification}")
                    issues.forEach { Log.d("AlarmParser", it) }

                    val assistantReply = fixed.responseText?.trim().orEmpty()
                    val title = (fixed.title ?: "").ifBlank { "Alarm" }

                    // ------------ Interval parsing (JSON wins), supports "every1h", "hourly"
                    val jsonIntervalMins = rootEl["interval_minutes"]?.jsonPrimitive?.intOrNull
                    val jsonIntervalHours = rootEl["interval_hours"]?.jsonPrimitive?.intOrNull
                    val jsonDurationMins = rootEl["duration_minutes"]?.jsonPrimitive?.intOrNull
                    val jsonDurationHours = rootEl["duration_hours"]?.jsonPrimitive?.intOrNull

                    val lowerRaw = rawText.lowercase(Locale.getDefault())
                    fun findInt(rx: Regex): Int? = rx.find(lowerRaw)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    val everyMinutesA = findInt(Regex("""\bevery\s+(\d+)\s*min(?:ute|utes)?\b""", RegexOption.IGNORE_CASE))
                    val everyMinutesB = findInt(Regex("""\bevery(\d+)\s*min(?:ute|utes)?\b""", RegexOption.IGNORE_CASE))
                    val everyHoursA = findInt(Regex("""\bevery\s+(\d+)\s*(?:hour|hours|hr|hrs|h)\b""", RegexOption.IGNORE_CASE))
                    val everyHoursB = findInt(Regex("""\bevery(\d+)\s*(?:hour|hours|hr|hrs|h)\b""", RegexOption.IGNORE_CASE))
                    val everyHourSingle = Regex("""\bevery\s+(?:hour|hr)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerRaw)
                    val hourlyKeyword = Regex("""\bhourly\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerRaw)

                    val everyHours = when {
                        everyHoursA != null -> everyHoursA * 60
                        everyHoursB != null -> everyHoursB * 60
                        everyHourSingle || hourlyKeyword -> 60
                        else -> null
                    }
                    val everyMinutes = everyMinutesA ?: everyMinutesB

                    val forMinutesA = findInt(Regex("""\b(?:for|till|until)\s+(\d+)\s*min(?:ute|utes)?\b""", RegexOption.IGNORE_CASE))
                    val forMinutesB = findInt(Regex("""\b(?:for|till|until)(\d+)\s*min(?:ute|utes)?\b""", RegexOption.IGNORE_CASE))
                    val forHoursA = findInt(Regex("""\b(?:for|till|until)\s+(\d+)\s*(?:hour|hours|hr|hrs|h)\b""", RegexOption.IGNORE_CASE))
                    val forHoursB = findInt(Regex("""\b(?:for|till|until)(\d+)\s*(?:hour|hours|hr|hrs|h)\b""", RegexOption.IGNORE_CASE))
                    val forHours = (forHoursA ?: forHoursB)?.let { it * 60 }

                    val intervalMinutes = jsonIntervalMins ?: jsonIntervalHours?.let { it * 60 } ?: everyMinutes ?: everyHours
                    val durationMinutes = jsonDurationMins ?: jsonDurationHours?.let { it * 60 } ?: forMinutesA ?: forMinutesB ?: forHours

                    if (intervalMinutes != null && intervalMinutes > 0) {
                        val now = java.time.OffsetDateTime.now()
                        val nowIso = now.toString()
                        val time24 = "%02d:%02d".format(now.hour, now.minute)
                        val timezone = fixed.timezone ?: java.time.ZoneId.systemDefault().id

                        val api = com.example.alarmchatapp.utils.ApiAlarm(
                            title = title,
                            datetimeIso = nowIso,
                            time24 = time24,
                            timezone = timezone,
                            recurrenceShort = null,
                            initialNote = assistantReply.ifBlank { fixed.responseText.orEmpty() },
                            intervalMinutes = intervalMinutes,
                            durationMinutes = durationMinutes
                        )
                        val (alarmRow, firstEpoch) = com.example.alarmchatapp.utils.ApiAlarmMapper.toAlarmAndEpoch(api)
                        val dao = AppDatabase.getDatabase(context).alarmDao()
                        val newId = dao.insert(alarmRow).toInt()
                        val saved = alarmRow.copy(id = newId)

                        val ok = runCatching {
                            AlarmHelper.scheduleAlarmClockPublic(
                                context = context,
                                label = saved.message,
                                triggerAt = saved.triggerTimeMillis,
                                alarmId = saved.id,
                                initialNote = saved.initialNote ?: ""
                            ); true
                        }.getOrElse { false }

                        if (ok) {
                            val primary = makePrimaryTimeLine(firstEpoch)
                            val msg1 = ChatMessage(primary, Sender.App); messages.add(msg1); onPersist(msg1)
                            val details = buildString {
                                append("This will repeat every $intervalMinutes minute(s)")
                                durationMinutes?.let {
                                    val text = if (it % 60 == 0) "${it / 60} hour(s)" else "$it minute(s)"
                                    append(" for $text")
                                }
                                append(".")
                            }
                            val msg2 = ChatMessage(details, Sender.App); messages.add(msg2); onPersist(msg2)
                            finishOnce(); return@launch
                        } else {
                            val fail = "Couldn’t create your alarm. Please allow exact alarms and try again."
                            val msg = ChatMessage(fail, Sender.App); messages.add(msg); onPersist(msg)
                            finishOnce(); return@launch
                        }
                    }

                    // ------------ VERB-AGNOSTIC weekly detection: "every <weekday>" anywhere
                    val weekdayMap = mapOf(
                        "sunday" to "Sun","sun" to "Sun","sundays" to "Sun",
                        "monday" to "Mon","mon" to "Mon","mondays" to "Mon",
                        "tuesday" to "Tue","tue" to "Tue","tues" to "Tue","tuesdays" to "Tue",
                        "wednesday" to "Wed","wed" to "Wed","wednesdays" to "Wed",
                        "thursday" to "Thu","thu" to "Thu","thur" to "Thu","thurs" to "Thu","thursdays" to "Thu",
                        "friday" to "Fri","fri" to "Fri","fridays" to "Fri",
                        "saturday" to "Sat","sat" to "Sat","saturdays" to "Sat"
                    )
                    val everyDayRx = Regex("""\b(?:every|each)(?:\s+week)?\s+(?:on\s+)?([a-z,\s]+)""", RegexOption.IGNORE_CASE)
                    fun extractEveryWeekdays(text: String): List<String> {
                        val lower = text.lowercase(Locale.getDefault())
                        val m = everyDayRx.find(lower) ?: return emptyList()
                        val tail = m.groupValues[1].replace(Regex("""\band\b"""), ",")
                        val parts = tail.split(Regex("""[,/ ]+""")).filter { it.isNotBlank() }.take(7)
                        val hits = mutableListOf<String>()
                        parts.forEach { token ->
                            val t = token.trim()
                            weekdayMap[t]?.let { hits += it }
                            weekdayMap.keys.firstOrNull { k -> Regex("""\b$k\b""").containsMatchIn(t) }?.let { k ->
                                weekdayMap[k]?.let { hits += it }
                            }
                        }
                        return hits.distinct()
                    }
                    fun weekdaysFromText(text: String): List<String> {
                        val lower = text.lowercase(Locale.getDefault())
                        val hits = mutableListOf<String>()
                        weekdayMap.forEach { (k, v) -> if (Regex("""\b$k\b""").containsMatchIn(lower)) hits += v }
                        return hits.distinct()
                    }

                    val serverRecurrence: List<String>? = when (val r = rootEl["recurrence"]) {
                        null -> null
                        is kotlinx.serialization.json.JsonArray -> r.mapNotNull { it.jsonPrimitive.contentOrNull }.filter { it.isNotBlank() }
                        is kotlinx.serialization.json.JsonPrimitive -> r.contentOrNull?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
                        else -> null
                    }

                    val forcedFromEvery = extractEveryWeekdays(rawText)
                    val recurrenceShort = when {
                        forcedFromEvery.isNotEmpty() -> forcedFromEvery
                        !serverRecurrence.isNullOrEmpty() -> serverRecurrence
                        else -> weekdaysFromText(rawText)
                    }.let { tokens ->
                        if (tokens.any { it.equals("daily", true) || it.equals("everyday", true) || it.equals("all", true) })
                            listOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")
                        else tokens.filter { it in listOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat") }.distinct()
                    }

                    // ------------ Build ISO candidates (server notifications or single datetime)
                    var isoList: List<String> = fixed.notification
                    if (isoList.isEmpty() && !fixed.datetime.isNullOrBlank()) isoList = listOf(fixed.datetime!!)

                    if (recurrenceShort.isNotEmpty()) {
                        // derive time24
                        val time24 = fixed.time?.takeIf { it.matches(Regex("""^\d{1,2}:\d{2}$""")) } ?: run {
                            val baseIsoCandidate = fixed.datetime?.takeIf { it.isNotBlank() } ?: isoList.firstOrNull()
                            val t = baseIsoCandidate?.let { runCatching { java.time.OffsetDateTime.parse(it).toLocalTime() }.getOrNull() }
                            t?.let { "%02d:%02d".format(it.hour, it.minute) }
                        }
                        if (time24 == null) { handleNoTimeAndStop("Please provide a time for the alarm."); return@launch }

                        val hour = time24.substringBefore(":").toInt()
                        val minute = time24.substringAfter(":").toInt()
                        val daysInt: List<Int> = recurrenceShort.mapNotNull { com.example.alarmchatapp.utils.AlarmHelper.dayShortToCal[it] }
                        if (daysInt.isEmpty()) { handleNoTimeAndStop("Please mention a weekday or say daily."); return@launch }

                        val nextEpoch = com.example.alarmchatapp.utils.AlarmHelper.computeNextAmongDays(hour, minute, daysInt)

                        val timezone = fixed.timezone ?: java.time.ZoneId.systemDefault().id
                        val baseIso = java.time.ZonedDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(nextEpoch), java.time.ZoneId.of(timezone)
                        ).toOffsetDateTime().toString()

                        val api = com.example.alarmchatapp.utils.ApiAlarm(
                            title = title,
                            datetimeIso = baseIso,
                            time24 = time24,
                            timezone = timezone,
                            recurrenceShort = recurrenceShort,
                            initialNote = assistantReply.ifBlank { fixed.responseText.orEmpty() }
                        )
                        val (mapped, _) = com.example.alarmchatapp.utils.ApiAlarmMapper.toAlarmAndEpoch(api)

                        val corrected = mapped.copy(
                            triggerTimeMillis = nextEpoch,
                            isRecurring = true,
                            recurringDays = daysInt
                        )

                        val dao = AppDatabase.getDatabase(context).alarmDao()
                        val newId = dao.insert(corrected).toInt()
                        val saved = corrected.copy(id = newId)

                        val ok = runCatching {
                            AlarmHelper.scheduleAlarmClockPublic(
                                context, saved.message, saved.triggerTimeMillis, saved.id, saved.initialNote ?: ""
                            ); true
                        }.getOrElse { false }

                        if (ok) {
                            Log.d("ChatScreen", "CREATED WEEKLY id=${saved.id} days=$daysInt epoch=${saved.triggerTimeMillis}")
                            val primary = makePrimaryTimeLine(saved.triggerTimeMillis)
                            val msg1 = ChatMessage(primary, Sender.App); messages.add(msg1); onPersist(msg1)
                            finishOnce(); return@launch
                        } else {
                            val fail = "Couldn’t create your alarm. Please allow exact alarms and try again."
                            val msg = ChatMessage(fail, Sender.App); messages.add(msg); onPersist(msg)
                            finishOnce(); return@launch
                        }
                    }

                    // ------------ One-time path
                    if (isoList.isEmpty()) { handleNoTimeAndStop(); return@launch }
                    val nowMs = System.currentTimeMillis()
                    val times: List<Long> = isoList.mapNotNull { iso ->
                        runCatching { java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
                    }.filter { it > nowMs }.distinct().sorted()
                    if (times.isEmpty()) { handleNoTimeAndStop(); return@launch }

                    val whenMillis = times.first()
                    val dao = AppDatabase.getDatabase(context).alarmDao()
                    val assistantReplyOrNote = assistantReply.ifBlank { parsed.responseText.orEmpty() }

                    val row = Alarm(
                        message = title,
                        triggerTimeMillis = whenMillis,
                        isRecurring = false,
                        recurringDays = null,
                        initialNote = assistantReplyOrNote
                    )
                    val newId = dao.insert(row).toInt()

                    val ok = runCatching {
                        AlarmHelper.scheduleAlarmClockPublic(context, title, whenMillis, newId, assistantReplyOrNote); true
                    }.getOrElse { false }

                    if (ok) {
                        val primary = makePrimaryTimeLine(whenMillis)
                        val msg1 = ChatMessage(primary, Sender.App); messages.add(msg1); onPersist(msg1)
                        if (isCustomDate(whenMillis)) {
                            val secondary = "Your reminder has been set for ${formatLocalTime(whenMillis)}."
                            val msg2 = ChatMessage(secondary, Sender.App); messages.add(msg2); onPersist(msg2)
                        }
                        finishOnce(); return@launch
                    } else {
                        val fail = "Couldn’t create your alarm. Please allow exact alarms and try again."
                        val msg = ChatMessage(fail, Sender.App); messages.add(msg); onPersist(msg)
                        finishOnce(); return@launch
                    }
                } catch (e: Exception) {
                    Log.e("ChatScreen", "Error", e)
                    val err = ChatMessage("Failed: ${e.localizedMessage ?: "Unknown error"}", Sender.App)
                    messages.add(err); onPersist(err); finishOnce()
                } finally {
                    onProcessingChange(false)
                }
            }
        }) {
            Icon(imageVector = Icons.Filled.Send, contentDescription = "Send")
        }
    }
}

private fun formatLocalTime(epoch: Long): String =
    java.time.Instant.ofEpochMilli(epoch)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDateTime()
        .let { dt ->
            val time = dt.toLocalTime()
            val h = time.hour % 12
            val hour12 = if (h == 0) 12 else h
            val m = time.minute.toString().padStart(2, '0')
            val ampm = if (time.hour < 12) "am" else "pm"
            "${dt.toLocalDate()} at $hour12:$m $ampm"
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
