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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
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

@Composable
fun ChatScreen(onShow: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf(TextFieldValue()) }
    var isProcessing by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // ADDED: DB and DAO for chat persistence
    val db = remember { AppDatabase.getDatabase(context) } // ADDED
    val chatDao: ChatDao = remember { db.chatDao() } // ADDED

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    // Ringtone picker launcher
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
            // Optionally request POST_NOTIFICATIONS
        }
        permissionLauncher.launch(perms.toTypedArray())

        // ADDED: Load last 24h chat from Room, then seed if empty
        val now = System.currentTimeMillis() // ADDED
        val last24 = withContext(Dispatchers.IO) { // ADDED
            chatDao.lastSince(now - 24L * 60L * 60L * 1000L) // ADDED
        } // ADDED
        if (last24.isNotEmpty()) { // ADDED
            messages.clear() // ADDED
            messages.addAll(last24.map { ChatMessage(it.text, if (it.sender == "User") Sender.User else Sender.App) }) // ADDED
        } // ADDED

        if (messages.isEmpty()) {
            messages.add(
                0,
                ChatMessage(
                    "HELLO THERE",
                    Sender.App
                )
            )

            // ADDED: persist welcome once
            withContext(Dispatchers.IO) {
                chatDao.insert(ChatMessageEntity(text = "HELLO THERE", sender = "App", timeMillis = System.currentTimeMillis()))
                chatDao.pruneOlderThan(now - 7L * 24L * 60L * 60L * 1000L) // housekeeping
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        TopBar(
            onShow = onShow,
            onOpenSettings = { showSettings = true }
        )

        // Big logo just under TopBar (small logo near settings removed)
        Spacer(Modifier.height(4.dp))
        CenterLogo(isProcessing = isProcessing)

        // Header and buttons packed tighter to free more space for chat
        HeaderAndButtons(
            onCommandClick = { command -> input = TextFieldValue(command) }
        )

        // Chat list takes remaining space
        Box(Modifier.fillMaxWidth().weight(1f)) {
            MessageList(messages)
        }

        InputSection(
            input = input,
            onInputChange = { input = it },
            scope = scope,
            context = context,
            messages = messages,
            // ADDED: persist every bubble from here
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
            onProcessingChange = { isProcessing = it }
        )
    }

    if (showSettings) {
        SettingsSheet(
            onDismiss = { showSettings = false },
            onChooseSound = {
                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select alarm sound")
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                }
                pickSound.launch(intent)
            },
            onShare = {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Wake smarter with WOW Alarm – natural language alarms and reminders that really work. Try it today!")
                }
                context.startActivity(Intent.createChooser(share, "Share WOW Alarm"))
            },
            onInvite = {
                val invite = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:")
                    putExtra(Intent.EXTRA_SUBJECT, "Join me on WOW Alarm")
                    putExtra(Intent.EXTRA_TEXT, "I’ve been using WOW Alarm for smart wake-ups and reminders. Install it and I’ll share my templates!")
                }
                runCatching { context.startActivity(invite) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    onDismiss: () -> Unit,
    onChooseSound: () -> Unit,
    onShare: () -> Unit,
    onInvite: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Settings", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onChooseSound, modifier = Modifier.fillMaxWidth()) {
                Text("Choose alarm sound")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
                Text("Share")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onInvite, modifier = Modifier.fillMaxWidth()) {
                Text("Invite")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Close")
            }
        }
    }
}

@Composable
private fun CenterLogo(isProcessing: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        WowLogoWithSpinner(isProcessing = isProcessing, logoSize = 200.dp, clockOverlaySize = 72.dp)
    }
}

@Composable
private fun HeaderAndButtons(onCommandClick: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        val gradient = androidx.compose.ui.graphics.Brush.horizontalGradient(
            listOf(Color(0xFF6A1B9A), Color.Black)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(style = SpanStyle(brush = gradient)) {
                    append("Welcome to WOW Alarm! Try:\n- Wake me up at 5 AM daily\n- Call mom every Friday at 7 PM\n- Meeting tomorrow 3 PM\n- Son's birthday on Oct 24; remind a week before")
                }
            },
            fontSize = 18.sp
        )
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(onClick = { onCommandClick("Wake me up at 6 AM") }, modifier = Modifier.weight(1f)) {
                Text("Wake‑up Alarm")
            }
            Button(onClick = { onCommandClick("Remind me at 9 PM daily") }, modifier = Modifier.weight(1f)) {
                Text("Repeat Alarm")
            }
            Button(onClick = { onCommandClick("Event reminder tomorrow 3 PM") }, modifier = Modifier.weight(1f)) {
                Text("Event Reminder")
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun WowLogoWithSpinner(isProcessing: Boolean, logoSize: Dp, clockOverlaySize: Dp) {
    Box(contentAlignment = Alignment.Center) {
        Image(painter = painterResource(id = R.drawable.wow_logo), contentDescription = "Wow Logo", modifier = Modifier.size(logoSize))
        RotatingClockOverlay(isProcessing = isProcessing, sizeDp = clockOverlaySize)
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
            drawLine(Color(0xFF6A1B9A), c, c.copy(y = c.y - r * 0.65f), strokeWidth = 6f, cap = StrokeCap.Round)
            drawLine(Color(0xFF424242), c, c.copy(y = c.y - r * 0.45f), strokeWidth = 4f, cap = StrokeCap.Round)
            drawCircle(Color(0xFF424242), radius = 6f, center = c)
        }
    }
}

@Composable
fun TopBar(onShow: () -> Unit, onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .heightIn(min = 44.dp)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Small logo near settings removed; only settings icon remains
        Row(Modifier.clickable { onOpenSettings() }) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings")
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onShow, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
            Text("Manage")
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
        items(messages) { msg ->
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
    onPersist: (ChatMessage) -> Unit, // ADDED
    modifier: Modifier = Modifier,
    onProcessingChange: (Boolean) -> Unit = {}
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("What should i remind you about ?") }
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = {
            val rawText = input.text.trim()
            if (rawText.isEmpty()) return@IconButton

            val userMsg = ChatMessage(rawText, Sender.User) // ADDED (local var)
            messages.add(0, userMsg)
            onPersist(userMsg) // ADDED

            onInputChange(TextFieldValue(""))

            scope.launch {
                try {
                    onProcessingChange(true)

                    val zone = ZoneId.systemDefault()
                    val ianaId = zone.id
                    val nowOffset = OffsetDateTime.now(zone).toString()
                    // WOW format: "Current datetime <ISO_OFFSET> <IANA>"
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
                        // ADDED: persist error/info
                        onPersist(ChatMessage("Timeout or server error (${http.code()}). Tap to retry.", Sender.App))
                        onPersist(ChatMessage("Retry ▶", Sender.App))
                        return@launch
                    }
                    val bodyStr = http.body()?.string().orEmpty()
                    if (bodyStr.isBlank()) {
                        messages.add(0, ChatMessage("No response received. Tap to retry.", Sender.App))
                        messages.add(0, ChatMessage("Retry ▶", Sender.App))
                        onPersist(ChatMessage("No response received. Tap to retry.", Sender.App)) // ADDED
                        onPersist(ChatMessage("Retry ▶", Sender.App)) // ADDED
                        return@launch
                    }

                    val innerJson = extractInnerJsonFromResponse(bodyStr)
                    if (innerJson == null) {
                        messages.add(0, ChatMessage("API returned no JSON block; nothing scheduled.", Sender.App))
                        onPersist(ChatMessage("API returned no JSON block; nothing scheduled.", Sender.App)) // ADDED
                        return@launch
                    }

                    val parsed: AlarmContract = AlarmParser.parseAlarmJson(innerJson)
                    val (fixed, issues) = AlarmParser.validateAndFixAlarm(parsed)
                    Log.d("AlarmParser", "innerJson=$innerJson")
                    Log.d("AlarmParser", "fixed.notification=${fixed.notification}")
                    issues.forEach { Log.d("AlarmParser", it) }

                    val assistantReply = fixed.responseText?.trim().orEmpty()
                    if (assistantReply.isNotEmpty()) {
                        val appMsg = ChatMessage(assistantReply, Sender.App) // ADDED
                        messages.add(0, appMsg)
                        onPersist(appMsg) // ADDED
                    }

                    val title = (fixed.title ?: "").ifBlank { "Alarm" }

                    var isoList: List<String> = fixed.notification
                    if (isoList.isEmpty() && !fixed.datetime.isNullOrBlank()) {
                        isoList = listOf(fixed.datetime!!)
                    }

                    // HH:mm only -> today/tomorrow
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
                        // dd/MM/yyyy HH:mm [AM/PM]
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

                    // time-only like "6 pm"
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
                        onPersist(ChatMessage("No times from API or text; nothing scheduled.", Sender.App)) // ADDED
                        return@launch
                    }

                    val nowMs = System.currentTimeMillis()
                    val times: List<Long> = isoList.mapNotNull { iso ->
                        runCatching { java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
                    }.filter { it > nowMs }.distinct().sorted()
                    if (times.isEmpty()) {
                        messages.add(0, ChatMessage("No future times after validation; nothing scheduled.", Sender.App))
                        onPersist(ChatMessage("No future times after validation; nothing scheduled.", Sender.App)) // ADDED
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
                    val err = ChatMessage("Failed: ${e.localizedMessage ?: "Unknown error"}", Sender.App) // ADDED
                    messages.add(0, err)
                    onPersist(err) // ADDED
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
