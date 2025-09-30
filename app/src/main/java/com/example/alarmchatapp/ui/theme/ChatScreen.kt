package com.example.alarmchatapp.ui

import android.Manifest
import android.content.Context
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
import androidx.compose.ui.text.input.TextFieldValue
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    LaunchedEffect(Unit) {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Optionally request POST_NOTIFICATIONS on Android 13+
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        TopBar(onShow)
        Spacer(Modifier.width(0.dp))

        OldUiButtons(
            onCommandClick = { command -> input = TextFieldValue(command) },
            isProcessing = isProcessing
        )

        MessageList(messages)
        Spacer(Modifier.width(0.dp))

        InputSection(
            input = input,
            onInputChange = { input = it },
            scope = scope,
            context = context,
            messages = messages,
            modifier = Modifier.navigationBarsPadding().padding(bottom = 8.dp),
            onProcessingChange = { isProcessing = it }
        )
    }
}

@Composable
fun OldUiButtons(onCommandClick: (String) -> Unit, isProcessing: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WowLogoWithSpinner(isProcessing = isProcessing, logoSize = 200.dp, clockOverlaySize = 112.dp)
        Spacer(Modifier.width(0.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { onCommandClick("Set an alarm at 6 PM") }, modifier = Modifier.weight(1f)) {
                Text("Wake Up")
            }
            Button(onClick = { onCommandClick("Remind me at 9 PM daily") }, modifier = Modifier.weight(1f)) {
                Text("Remind Me")
            }
        }
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
            .graphicsLayer {
                rotationZ = angle
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
            },
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
fun TopBar(onShow: () -> Unit) {
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.clickable { Toast.makeText(ctx, "Settings clicked", Toast.LENGTH_SHORT).show() }) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings")
            Spacer(Modifier.width(8.dp))
            Text("Alarm App")
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onShow) { Text("Manage") }
    }
}

@Composable
fun MessageList(messages: List<ChatMessage>) {
    LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = 280.dp).padding(horizontal = 12.dp),
        reverseLayout = true
    ) {
        items(messages) { msg ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
    modifier: Modifier = Modifier,
    onProcessingChange: (Boolean) -> Unit = {}
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("Type alarm/reminder command") }
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = {
            val rawText = input.text.trim()
            if (rawText.isEmpty()) return@IconButton

            messages.add(0, ChatMessage(rawText, Sender.User))
            onInputChange(TextFieldValue(""))

            scope.launch {
                try {
                    onProcessingChange(true)

                    val zone = ZoneId.systemDefault()
                    val todayDmy = LocalDate.now(zone).format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                    val ianaId = zone.id
                    val augmentedUserInput = buildString {
                        append(rawText).append(' ')
                        append("Today's date is $todayDmy and the timezone is IST ($ianaId)")
                    }

                    val payload: Map<String, Any> = mapOf(
                        "objective" to "Alarm Generator",
                        "objective_key" to "alarm_generator",
                        "model" to "openai",
                        "inputs" to mapOf("user_input" to augmentedUserInput, "ctype" to "text")
                    )

                    val http = RetrofitClient.instance.getAlarmDetailsRaw(payload)
                    if (!http.isSuccessful) {
                        messages.add(0, ChatMessage("API failed: ${http.code()}", Sender.App))
                        return@launch
                    }
                    val bodyStr = http.body()?.string().orEmpty()
                    if (bodyStr.isBlank()) {
                        messages.add(0, ChatMessage("API failed: empty body", Sender.App))
                        return@launch
                    }

                    val innerJson = extractInnerJsonFromResponse(bodyStr)
                    if (innerJson == null) {
                        messages.add(0, ChatMessage("API returned no JSON block; nothing scheduled.", Sender.App))
                        return@launch
                    }

                    val parsed: AlarmContract = AlarmParser.parseAlarmJson(innerJson)
                    val (fixed, issues) = AlarmParser.validateAndFixAlarm(parsed)
                    Log.d("AlarmParser", "innerJson=$innerJson")
                    Log.d("AlarmParser", "fixed.notification=${fixed.notification}")
                    issues.forEach { Log.d("AlarmParser", it) }

                    // Show server conversational response as left-side bubble, if present
                    val assistantReply = fixed.responseText?.trim().orEmpty()
                    if (assistantReply.isNotEmpty()) {
                        messages.add(0, ChatMessage(assistantReply, Sender.App))
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

                    // dd/MM/yyyy HH:mm [AM/PM]
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
                        return@launch
                    }

                    val nowMs = System.currentTimeMillis()
                    val times: List<Long> = isoList.mapNotNull { iso ->
                        runCatching { java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
                    }.filter { it > nowMs }.distinct().sorted()
                    if (times.isEmpty()) {
                        messages.add(0, ChatMessage("No future times after validation; nothing scheduled.", Sender.App))
                        return@launch
                    }

                    val dao = AppDatabase.getDatabase(context).alarmDao()
                    var scheduled = 0
                    for (whenMillis in times) {
                        val row = Alarm(
                            message = title,
                            triggerTimeMillis = whenMillis,
                            isRecurring = false,
                            recurringDays = null,
                            initialNote = assistantReply.ifBlank { fixed.responseText } // pass to AlarmActivity marquee
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
                } catch (e: Exception) {
                    Log.e("ChatScreen", "Error", e)
                    messages.add(0, ChatMessage("Failed: ${e.localizedMessage ?: "Unknown error"}", Sender.App))
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
