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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.material3.OutlinedTextFieldDefaults.contentPadding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
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

// ADDED: imports for persistence
import kotlinx.coroutines.Dispatchers // ADDED
import kotlinx.coroutines.withContext // ADDED
import com.example.alarmchatapp.chatroom.ChatMessageEntity // ADDED
import com.example.alarmchatapp.chatroom.ChatDao // ADDED

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

    // DB and DAO
    val db = remember { AppDatabase.getDatabase(context) }
    val chatDao: ChatDao = remember { db.chatDao() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    val pickSound = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val uri: Uri? = res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (uri != null) {
            context.getSharedPreferences("wow_prefs", Context.MODE_PRIVATE)
                .edit().putString("ringtone_uri", uri.toString()).apply()
            Toast.makeText(context, "Alarm sound set", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // optionally POST_NOTIFICATIONS
        }
        permissionLauncher.launch(perms.toTypedArray())

        val now = System.currentTimeMillis()
        val last24 = withContext(Dispatchers.IO) { chatDao.lastSince(now - 24L * 60L * 60L * 1000L) }
        if (last24.isNotEmpty()) {
            messages.clear()
            messages.addAll(last24.map { ChatMessage(it.text, if (it.sender == "User") Sender.User else Sender.App) })
        }
        if (messages.isEmpty()) {
            messages.add(0, ChatMessage("HELLO THERE", Sender.App))
            withContext(Dispatchers.IO) {
                chatDao.insert(ChatMessageEntity(text = "HELLO THERE", sender = "App", timeMillis = System.currentTimeMillis()))
                chatDao.pruneOlderThan(now - 7L * 24L * 60L * 60L * 1000L)
            }
        }
    }

    // Drawer
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val openDrawer = { scope.launch { drawerState.open() } }
    val closeDrawer = { scope.launch { drawerState.close() } }

    val showWelcomeBox by remember { derivedStateOf { !isTyping && !isProcessing } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("WOW Panel", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))

                    Text(
                        "Manage Alarms",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                closeDrawer()
                                onShow()
                            }
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
                                    putExtra(Intent.EXTRA_SUBJECT, "Join me on WOW Alarm")
                                    putExtra(Intent.EXTRA_TEXT, "I've been using WOW Alarm for smart wake-ups and reminders.\nWOW Assist is a great way to keep your timely reminders and the best thing is you can converse in your language. Check it out.\n\nhttps://www.workofwisdomai.com/assist ")
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
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            TopBarWithActions(
                onToggle = { openDrawer() },
                onShare = {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "\nWOW Assist is a great way to keep your timely reminders and the best thing is you can converse in your language. Check it out. \n\nhttps://www.workofwisdomai.com/assist "
                        )
                    }
                    context.startActivity(Intent.createChooser(share, "Share WOW Alarm"))
                }
            )

            Box(Modifier.weight(1f)) {
                ChatScrollableContent(
                    isProcessing = isProcessing,
                    showWelcome = showWelcomeBox,
                    messages = messages,
                    onCommandClick = { command ->
                        input = TextFieldValue(command)
                        isTyping = true
                    }
                )
            }

            InputSection(
                input = input,
                onInputChange = {
                    input = it
                    isTyping = it.text.isNotBlank()
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
                modifier = Modifier.navigationBarsPadding().padding(bottom = 8.dp),
                onProcessingChange = { processing ->
                    isProcessing = processing
                    if (processing) isTyping = true
                },
                onFocusChange = { focused -> isTyping = focused },
                onSubmit = {
                    isTyping = true
                    scope.launch {
                        kotlinx.coroutines.delay(2000)
                        if (input.text.isBlank()) isTyping = false
                    }
                }
            )
        }
    }
}

@Composable
private fun ChatScrollableContent(
    isProcessing: Boolean,
    showWelcome: Boolean,
    messages: List<ChatMessage>,
    onCommandClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(), // replace weight with height/size fill
        contentPadding = PaddingValues(bottom = 8.dp),
        verticalArrangement = Arrangement.Top
    ) {
        stickyHeader {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                WowLogoInline(isProcessing = isProcessing, height = 220.dp)
            }
        }

        if (showWelcome) {
            item {
                WelcomeSection(onCommandClick = onCommandClick)
            }
        }

        items(messages.reversed()) { msg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
            .height(48.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggle) {
            Icon(Icons.Filled.Menu, contentDescription = "Menu")
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onShare) {
            Icon(Icons.Filled.Share, contentDescription = "Share")
        }
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
            ) {
                RotatingClockOverlay(isProcessing = true, sizeDp = overlaySize)
            }
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
        val gradient = Brush.horizontalGradient(
            listOf(Color(0xFF6A1B9A), Color.Black)
        )
        Text(
            text = buildAnnotatedString {
                withStyle(style = SpanStyle(brush = gradient)) {
                    append("Welcome to WOW Alarm!")
                }
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

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
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

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
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
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1600, easing = LinearEasing)
        ),
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
            drawLine(Color(0xFF6A1B9A), c, c.copy(y = c.y - r * 0.65f), strokeWidth = 6f)
            drawLine(Color(0xFF424242), c, c.copy(y = c.y - r * 0.45f), strokeWidth = 4f)
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
    onSubmit: () -> Unit
) {
    var hasFocus by remember { mutableStateOf(false) }

    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
            messages.add(0, userMsg)
            onPersist(userMsg)

            onInputChange(TextFieldValue(""))
            onSubmit()

            scope.launch {
                try {
                    onProcessingChange(true)

                    val zone = ZoneId.systemDefault()
                    val ianaId = zone.id
                    val nowOffset = OffsetDateTime.now(zone).toString()
                    val augmentedUserInput = buildString {
                        append("Current datetime ").append(nowOffset).append(' ').append(ianaId).append("\n\n")
                        append(rawText)
                    }

                    val payload: Map<String, Any> = mapOf(
                        "objective" to "Alarm Generator",
                        "objective_key" to "alarm_generator",
                        "model" to "openai",
                        "inputs" to mapOf("user_input" to augmentedUserInput, "ctype" to "text")
                    )

                    val http = RetrofitClient.instance.getAlarmDetailsRaw(payload)
                    if (!http.isSuccessful) {
                        messages.add(0, ChatMessage("Timeout or server error (${http.code()}). Tap to retry.", Sender.App))
                        messages.add(0, ChatMessage("Retry ▶", Sender.App))
                        onPersist(ChatMessage("Timeout or server error (${http.code()}). Tap to retry.", Sender.App))
                        onPersist(ChatMessage("Retry ▶", Sender.App))
                        return@launch
                    }
                    val bodyStr = http.body()?.string().orEmpty()
                    if (bodyStr.isBlank()) {
                        messages.add(0, ChatMessage("No response received. Tap to retry.", Sender.App))
                        messages.add(0, ChatMessage("Retry ▶", Sender.App))
                        onPersist(ChatMessage("No response received. Tap to retry.", Sender.App))
                        onPersist(ChatMessage("Retry ▶", Sender.App))
                        return@launch
                    }

                    val innerJson = extractInnerJsonFromResponse(bodyStr)
                    if (innerJson == null) {
                        messages.add(0, ChatMessage("API returned no JSON block; nothing scheduled.", Sender.App))
                        onPersist(ChatMessage("API returned no JSON block; nothing scheduled.", Sender.App))
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
                        messages.add(0, appMsg)
                        onPersist(appMsg)
                    }

                    val title = (fixed.title ?: "").ifBlank { "Alarm" }

                    var isoList: List<String> = fixed.notification
                    if (isoList.isEmpty() && !fixed.datetime.isNullOrBlank()) {
                        isoList = listOf(fixed.datetime!!)
                    }

                    if (isoList.isEmpty() && !fixed.time.isNullOrBlank()) {
                        val parts = fixed.time.split(":")
                        val hour = parts.getOrNull(0)?.toIntOrNull()
                        val minute = parts.getOrNull(1)?.toIntOrNull()
                        if (hour != null && minute != null) {
                            val now = Calendar.getInstance()
                            val cal = Calendar.getInstance().apply {
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                                set(Calendar.HOUR_OF_DAY, hour)
                                set(Calendar.MINUTE, minute)
                            }
                            if (cal.before(now)) cal.add(Calendar.DAY_OF_YEAR, 1)
                            val instant = java.time.Instant.ofEpochMilli(cal.timeInMillis)
                            val offset = ZoneId.systemDefault().rules.getOffset(instant)
                            isoList = listOf(java.time.OffsetDateTime.ofInstant(instant, offset).toString())
                        }
                    }

                    if (isoList.isEmpty()) {
                        val lower = rawText.lowercase(Locale.getDefault()).replace("on", " ")
                        val dateTimeRegex = Regex(
                            """\b(\d{1,2})[/-](\d{1,2})[/-](\d{4})\s*(?:at\s*)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b""",
                            RegexOption.IGNORE_CASE
                        )
                        val m = dateTimeRegex.find(lower)
                        if (m != null) {
                            val d = m.groupValues.getOrNull(1)?.toIntOrNull()
                            val mo = m.groupValues.getOrNull(2)?.toIntOrNull()
                            val y = m.groupValues.getOrNull(3)?.toIntOrNull()
                            val hStr = m.groupValues.getOrNull(4).orEmpty()
                            val minStr = m.groupValues.getOrNull(5).orEmpty().ifBlank { "0" }
                            val ampm = m.groupValues.getOrNull(6)?.lowercase(Locale.getDefault())
                            val h = hStr.toIntOrNull()
                            val min = minStr.toIntOrNull()
                            if (d != null && mo != null && y != null && h != null && min != null &&
                                d in 1..31 && mo in 1..12 && h in 0..23 && min in 0..59
                            ) {
                                var hour24 = h
                                if (ampm == "pm" && h in 1..11) hour24 = h + 12
                                if (ampm == "am" && h == 12) hour24 = 0
                                val cal = Calendar.getInstance().apply {
                                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                                    set(Calendar.YEAR, y); set(Calendar.MONTH, mo - 1); set(Calendar.DAY_OF_MONTH, d)
                                    set(Calendar.HOUR_OF_DAY, hour24); set(Calendar.MINUTE, min)
                                }
                                val instant = java.time.Instant.ofEpochMilli(cal.timeInMillis)
                                val offset = ZoneId.systemDefault().rules.getOffset(instant)
                                val iso = java.time.OffsetDateTime.ofInstant(instant, offset).toString()
                                isoList = listOf(iso)
                            }
                        }
                    }

                    if (isoList.isEmpty()) {
                        val lower = rawText.lowercase(Locale.getDefault())
                        val timeRegex = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b""", RegexOption.IGNORE_CASE)
                        val mr = timeRegex.find(lower)
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
                        messages.add(0, ChatMessage("No times from API or text; nothing scheduled.", Sender.App))
                        onPersist(ChatMessage("No times from API or text; nothing scheduled.", Sender.App))
                        return@launch
                    }

                    val nowMs = System.currentTimeMillis()
                    val times: List<Long> = isoList.mapNotNull { iso ->
                        runCatching { java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
                    }.filter { it > nowMs }.distinct().sorted()
                    if (times.isEmpty()) {
                        messages.add(0, ChatMessage("No future times after validation; nothing scheduled.", Sender.App))
                        onPersist(ChatMessage("No future times after validation; nothing scheduled.", Sender.App))
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
                        AlarmHelper.scheduleAlarmClockPublic(
                            context = context,
                            label = title,
                            triggerAt = whenMillis,
                            alarmId = newId
                        )
                        scheduled++
                    }
                    if (scheduled > 0) {

                    }
                } catch (e: Exception) {
                    Log.e("ChatScreen", "Error", e)
                    val err = ChatMessage("Failed: ${e.localizedMessage ?: "Unknown error"}", Sender.App)
                    messages.add(0, err)
                    onPersist(err)
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
