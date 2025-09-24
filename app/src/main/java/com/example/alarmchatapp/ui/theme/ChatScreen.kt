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
import com.example.alarmchatapp.*
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
import com.example.alarmchatapp.R
import com.example.alarmchatapp.utils.SystemAlarmScheduler
import com.example.alarmchatapp.workers.DailyClockHydratorWorker
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

// Chat roles
enum class Sender { User, App }

data class ChatMessage(
    val text: String,
    val sender: Sender
)

@Composable
fun AppContent() {
    val navController = rememberNavController()
    val ctx = LocalContext.current

// Startup: schedule daily hydrator (00:00:01 local) and run a one‑time catch‑up now
    LaunchedEffect(ctx) {
        com.example.alarmchatapp.workers.DailyClockHydratorWorker.scheduleDailyHydrator(ctx)
        com.example.alarmchatapp.workers.DailyClockHydratorWorker.scheduleCatchUp(ctx)
    }

    NavHost(navController, startDestination = "chat") {
        composable("chat") { ChatScreen(onShow = { navController.navigate("alarms") }) }
        composable("alarms") { AlarmListScreen(onBack = { navController.popBackStack() }) }
    }
}


@Composable
fun ChatScreen(onShow: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf(TextFieldValue()) }
    var isProcessing by remember { mutableStateOf(false) }

    // Permissions launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    // Ask only location (no notification permission needed for chat UI)
    LaunchedEffect(Unit) {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Intentionally not requesting POST_NOTIFICATIONS to keep UI minimal
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    // Removed exact-alarm special access request; not needed when delegating to Clock app.

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        TopBar(onShow)
        Spacer(Modifier.height(50.dp))

        OldUiButtons(
            onCommandClick = { commandText -> input = TextFieldValue(commandText) },
            isProcessing = isProcessing
        )

        MessageList(messages)
        Spacer(Modifier.height(200.dp))

        InputSection(
            input = input,
            onInputChange = { input = it },
            scope = scope,
            context = context,
            messages = messages,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            onProcessingChange = { isProcessing = it }
        )
    }
}

@Composable
fun OldUiButtons(onCommandClick: (String) -> Unit, isProcessing: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WowLogoWithSpinner(
            isProcessing = isProcessing,
            logoSize = 200.dp,
            clockOverlaySize = 112.dp
        )
        Spacer(Modifier.height(24.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { onCommandClick("Set an alarm at 6 PM") },
                modifier = Modifier.weight(1f)
            ) { Text("Wake Up") }


            Button(
                onClick = { onCommandClick("Remind me at 9 PM daily") },
                modifier = Modifier.weight(1f)
            ) { Text("Remind Me") }
        }
    }
}

@Composable
private fun WowLogoWithSpinner(
    isProcessing: Boolean,
    logoSize: Dp,
    clockOverlaySize: Dp
) {
    Box(contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(id = R.drawable.wow_logo),
            contentDescription = "Wow Logo",
            modifier = Modifier.size(logoSize)
        )
        RotatingClockOverlay(isProcessing = isProcessing, sizeDp = clockOverlaySize)
    }
}

@Composable
private fun RotatingClockOverlay(isProcessing: Boolean, sizeDp: Dp) {
    val infinite = rememberInfiniteTransition(label = "clock-spin")
    val angle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1600, easing = LinearEasing)),
        label = "angle"
    )
    if (!isProcessing) return
    Canvas(
        modifier = Modifier
            .size(sizeDp)
            .graphicsLayer { rotationZ = angle }
    ) {
        val c = this.center
        val r = this.size.minDimension / 2f
        drawLine(
            color = Color(0xFF6A1B9A),
            start = c,
            end = c.copy(y = c.y - r * 0.65f),
            strokeWidth = 6f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF424242),
            start = c,
            end = c.copy(y = c.y - r * 0.45f),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
        drawCircle(color = Color(0xFF424242), radius = 6f, center = c)
    }
}

@Composable
fun TopBar(onShow: () -> Unit) {
    val ctx = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.clickable {
                Toast.makeText(ctx, "Settings clicked", Toast.LENGTH_SHORT).show()
            }
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings")
            Spacer(Modifier.width(8.dp))
            Text("Alarm App")
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onShow) { Text("Manage") }
    }
}

// Right/Left chat list
@Composable
fun MessageList(messages: List<ChatMessage>) {
    LazyColumn(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .padding(horizontal = 12.dp),
        reverseLayout = true
    ) {
        items(messages) { msg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
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
    Surface(
        color = bg,
        shape = shape,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Text(
            text = text,
            color = fg,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
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
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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

                    // Ensure midnight worker and run catch-up
                    com.example.alarmchatapp.workers.DailyClockHydratorWorker.scheduleDailyHydrator(context)
                    com.example.alarmchatapp.workers.DailyClockHydratorWorker.scheduleCatchUp(context)

                    // Augment query with local date/timezone for backend
                    val zone = java.time.ZoneId.systemDefault()
                    val todayDmy = java.time.LocalDate.now(zone)
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                    val ianaId = zone.id
                    val augmentedUserInput = buildString {
                        append(rawText).append(' ')
                        append("Today's date is $todayDmy and the timezone is IST ($ianaId)")
                    }

                    // Call backend
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
                    android.util.Log.d("AlarmParser", "innerJson=$innerJson")
                    android.util.Log.d("AlarmParser", "fixed.notification=${fixed.notification}")
                    issues.forEach { android.util.Log.d("AlarmParser", it) }

                    val title = (fixed.title ?: "").ifBlank { "Alarm" }
                    val assistantMessage = fixed.response?.takeIf { it.isNotBlank() }

                    var isoList: List<String> = fixed.notification
                    if (isoList.isEmpty() && !fixed.datetime.isNullOrBlank()) {
                        isoList = listOf(fixed.datetime!!)
                    }

                    // Time-only normalization (from fixed.time HH:mm)
                    if (isoList.isEmpty() && !fixed.time.isNullOrBlank()) {
                        val parts = fixed.time.split(":")
                        val hour = parts.getOrNull(0)?.toIntOrNull()
                        val minute = parts.getOrNull(1)?.toIntOrNull()
                        if (hour != null && minute != null) {
                            val now = java.util.Calendar.getInstance()
                            val cal = java.util.Calendar.getInstance().apply {
                                set(java.util.Calendar.SECOND, 0)
                                set(java.util.Calendar.MILLISECOND, 0)
                                set(java.util.Calendar.HOUR_OF_DAY, hour)
                                set(java.util.Calendar.MINUTE, minute)
                            }
                            if (cal.before(now)) cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                            val instant = java.time.Instant.ofEpochMilli(cal.timeInMillis)
                            val offset = java.time.ZoneId.systemDefault().rules.getOffset(instant)
                            isoList = listOf(java.time.OffsetDateTime.ofInstant(instant, offset).toString())
                        }
                    }

                    // dd/mm/yyyy hh:mm am/pm fallback
                    if (isoList.isEmpty()) {
                        val lowerTmp = rawText.lowercase(java.util.Locale.getDefault()).replace("on", " ")
                        val dateTimeRegex = Regex(
                            """\b(\d{1,2})[/-](\d{1,2})[/-](\d{4})\s*(?:at\s*)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b""",
                            RegexOption.IGNORE_CASE
                        )
                        val m = dateTimeRegex.find(lowerTmp)
                        if (m != null) {
                            val d = m.groupValues.getOrNull(1)?.toIntOrNull()
                            val mo = m.groupValues.getOrNull(2)?.toIntOrNull()
                            val y = m.groupValues.getOrNull(3)?.toIntOrNull()
                            val hStr = m.groupValues.getOrNull(4).orEmpty()
                            val minStr = m.groupValues.getOrNull(5).orEmpty().ifBlank { "0" }
                            val ampm = m.groupValues.getOrNull(6)?.lowercase(java.util.Locale.getDefault())
                            val h = hStr.toIntOrNull()
                            val min = minStr.toIntOrNull()
                            if (d != null && mo != null && y != null && h != null && min != null &&
                                d in 1..31 && mo in 1..12 && h in 0..23 && min in 0..59
                            ) {
                                var hour24 = h
                                if (ampm == "pm" && h in 1..11) hour24 = h + 12
                                if (ampm == "am" && h == 12) hour24 = 0
                                val cal = java.util.Calendar.getInstance().apply {
                                    set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                                    set(java.util.Calendar.YEAR, y); set(java.util.Calendar.MONTH, mo - 1); set(java.util.Calendar.DAY_OF_MONTH, d)
                                    set(java.util.Calendar.HOUR_OF_DAY, hour24); set(java.util.Calendar.MINUTE, min)
                                }
                                val instant = java.time.Instant.ofEpochMilli(cal.timeInMillis)
                                val offset = java.time.ZoneId.systemDefault().rules.getOffset(instant)
                                val iso = java.time.OffsetDateTime.ofInstant(instant, offset).toString()
                                isoList = listOf(iso)
                            }
                        }
                    }

                    // Generic time-only fallback with "tomorrow" override from raw text
                    if (isoList.isEmpty()) {
                        val lowerTmp = rawText.lowercase(java.util.Locale.getDefault())
                        val timeRegex = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b""", RegexOption.IGNORE_CASE)
                        val mr = timeRegex.find(lowerTmp)
                        if (mr != null) {
                            val hourStr = mr.groupValues.getOrNull(1)
                            val minStr = mr.groupValues.getOrNull(2).orEmpty().ifBlank { "0" }
                            val ampmStr = mr.groupValues.getOrNull(3)?.lowercase(java.util.Locale.getDefault())
                            val h = hourStr?.toIntOrNull()
                            val min = minStr.toIntOrNull()
                            if (h != null && min != null && h in 0..23 && min in 0..59) {
                                var hour24 = h
                                if (ampmStr == "pm" && h in 1..11) hour24 = h + 12
                                if (ampmStr == "am" && h == 12) hour24 = 0
                                val now = java.util.Calendar.getInstance()
                                val cal = java.util.Calendar.getInstance().apply {
                                    set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                                    set(java.util.Calendar.HOUR_OF_DAY, hour24); set(java.util.Calendar.MINUTE, min)
                                }
                                val forceTomorrow = lowerTmp.contains("tomorrow")
                                if (forceTomorrow || cal.before(now)) cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                                val instant = java.time.Instant.ofEpochMilli(cal.timeInMillis)
                                val offset = java.time.ZoneId.systemDefault().rules.getOffset(instant)
                                val iso = java.time.OffsetDateTime.ofInstant(instant, offset).toString()
                                isoList = listOf(iso)
                            }
                        }
                    }

                    // To future epoch millis
                    val nowMs = System.currentTimeMillis()
                    val futureTimes = isoList.mapNotNull {
                        runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull()
                    }.filter { it > nowMs }.distinct().sorted()
                    if (futureTimes.isEmpty()) {
                        messages.add(0, ChatMessage("No future times after validation; nothing scheduled.", Sender.App))
                        return@launch
                    }

                    // Weekly parsing
                    val weekly = parseWeeklyIntent(rawText)

                    val dao = AppDatabase.getDatabase(context).alarmDao()
                    var scheduledCount = 0

                    val lower = rawText.lowercase(java.util.Locale.getDefault())
                    val hasExplicitDate = Regex("""\d{1,2}[/-]\d{1,2}[/-]\d{2,4}""").containsMatchIn(lower)
                    val weekdayWords = listOf(
                        "sunday","monday","tuesday","wednesday","thursday","friday","saturday",
                        "sun","mon","tue","tues","wed","thu","thur","thurs","fri","sat"
                    )
                    val mentionsWeekday = weekdayWords.any { w ->
                        lower.contains("important meeting on $w") ||
                                lower.contains("important on $w") ||
                                lower.contains("meeting on $w")
                    }

                    // RECURRING SERIES (weekly)
                    if (weekly.recurringDays != null && !weekly.nextWeekOnly) {
                        val hhmmPairs = hhmmPairsFromFutureTimes(futureTimes)
                        for ((hour, minute) in hhmmPairs) {
                            val next = com.example.alarmchatapp.utils.AlarmHelper
                                .computeNextAmongDays(hour, minute, weekly.recurringDays)
                            val id = dao.insert(
                                Alarm(
                                    message = title,
                                    triggerTimeMillis = next,
                                    isRecurring = true,
                                    recurringDays = weekly.recurringDays
                                )
                            ).toInt()
                            com.example.alarmchatapp.utils.AlarmHelper.scheduleWeeklyInClock(
                                context = context,
                                label = title,
                                hour = hour,
                                minute = minute,
                                days = weekly.recurringDays,
                                alarmId = id,
                                skipUi = true,
                                showToast = false
                            )
                            scheduledCount++
                        }

                        // NEXT WEEK ONLY (one-shot)
                    } else if (weekly.recurringDays != null && weekly.nextWeekOnly) {
                        val first = futureTimes.first()
                        val hhmm = java.util.Calendar.getInstance().apply { timeInMillis = first }
                        val hour = hhmm.get(java.util.Calendar.HOUR_OF_DAY)
                        val minute = hhmm.get(java.util.Calendar.MINUTE)
                        val trigger = computeNextWeekOnlyMillis(hour, minute, weekly.recurringDays.first())
                        val id = dao.insert(
                            Alarm(message = title, triggerTimeMillis = trigger, isRecurring = false, recurringDays = null)
                        ).toInt()
                        com.example.alarmchatapp.workers.ClockPreSchedulerWorker.enqueue(
                            context = context,
                            label = title,
                            triggerAt = trigger,
                            isRecurring = false,
                            recurringDays = null,
                            alarmId = id
                        )
                        scheduledCount = 1

                        // IMPORTANT with explicit date: force place now (exactly three)
                    } else if (lower.contains("important") && hasExplicitDate) {
                        val placed = com.example.alarmchatapp.utils.AlarmHelper.forcePlaceThreeOneShotsInClock(
                            context = context,
                            dao = dao,
                            title = title,
                            futureTimes = futureTimes
                        )
                        scheduledCount += placed

                        // IMPORTANT with weekday: enrich to three and force place now
                    } else if (lower.contains("important") && mentionsWeekday) {
                        val enrichedTimes = buildList {
                            addAll(futureTimes)
                            if (size < 3 && futureTimes.isNotEmpty()) {
                                val cal = java.util.Calendar.getInstance().apply { timeInMillis = futureTimes.first() }
                                val hh = cal.get(java.util.Calendar.HOUR_OF_DAY)
                                val mm = cal.get(java.util.Calendar.MINUTE)
                                val today = java.util.Calendar.getInstance().apply {
                                    set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                                    set(java.util.Calendar.HOUR_OF_DAY, hh); set(java.util.Calendar.MINUTE, mm)
                                }
                                if (today.timeInMillis > System.currentTimeMillis()) add(today.timeInMillis)
                                val tomorrow = (today.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
                                add(tomorrow.timeInMillis)
                            }
                        }.distinct().sorted().take(3)

                        val placed = com.example.alarmchatapp.utils.AlarmHelper.forcePlaceThreeOneShotsInClock(
                            context = context,
                            dao = dao,
                            title = title,
                            futureTimes = enrichedTimes
                        )
                        scheduledCount += placed

                        // DEFAULT one-shots: store + prescheduler
                    } else {
                        for (t in futureTimes) {
                            val id = dao.insert(
                                Alarm(message = title, triggerTimeMillis = t, isRecurring = false, recurringDays = null)
                            ).toInt()
                            com.example.alarmchatapp.workers.ClockPreSchedulerWorker.enqueue(
                                context = context,
                                label = title,
                                triggerAt = t,
                                isRecurring = false,
                                recurringDays = null,
                                alarmId = id
                            )
                            scheduledCount++
                        }
                    }

                    assistantMessage?.let { messages.add(0, ChatMessage(it, Sender.App)) }
                } catch (e: Exception) {
                    android.util.Log.e("ChatScreen", "Error", e)
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

// Helpers

private fun extractDays(text: String): List<Int>? {
    val lowered = text.lowercase(Locale.getDefault())
    return when {
        lowered.contains("weekdays") -> listOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY
        )
        lowered.contains("weekends") -> listOf(Calendar.SATURDAY, Calendar.SUNDAY)
        lowered.contains("everyday") || lowered.contains("daily") ->
            listOf(
                Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
            )
        else -> {
            val map = mapOf(
                "sunday" to Calendar.SUNDAY, "monday" to Calendar.MONDAY, "tuesday" to Calendar.TUESDAY,
                "wednesday" to Calendar.WEDNESDAY, "thursday" to Calendar.THURSDAY,
                "friday" to Calendar.FRIDAY, "saturday" to Calendar.SATURDAY
            )
            val match = map.entries.firstOrNull {
                lowered.contains("every ${it.key}") || lowered.contains(it.key)
            } ?: return null
            listOf(match.value)
        }
    }
}

private data class WeeklyIntent(
    val recurringDays: List<Int>?, // Calendar constants if recurring
    val nextWeekOnly: Boolean      // true if text says "next <weekday>"
)
private fun parseWeeklyIntent(text: String): WeeklyIntent {
    val t = text.lowercase(Locale.getDefault())

    if (t.contains("everyday") || t.contains("daily")) {
        return WeeklyIntent(
            listOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY),
            nextWeekOnly = false
        )
    }
    if (t.contains("weekdays")) return WeeklyIntent(
        listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY),
        nextWeekOnly = false
    )
    if (t.contains("weekends")) return WeeklyIntent(
        listOf(Calendar.SATURDAY, Calendar.SUNDAY),
        nextWeekOnly = false
    )

    val dayMap = mapOf(
        "sunday" to Calendar.SUNDAY, "sun" to Calendar.SUNDAY,
        "monday" to Calendar.MONDAY, "mon" to Calendar.MONDAY,
        "tuesday" to Calendar.TUESDAY, "tue" to Calendar.TUESDAY, "tues" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY, "wed" to Calendar.WEDNESDAY,
        "thursday" to Calendar.THURSDAY, "thu" to Calendar.THURSDAY, "thur" to Calendar.THURSDAY, "thurs" to Calendar.THURSDAY,
        "friday" to Calendar.FRIDAY, "fri" to Calendar.FRIDAY,
        "saturday" to Calendar.SATURDAY, "sat" to Calendar.SATURDAY
    )

    // "next monday"
    dayMap.keys.firstOrNull { key -> t.contains("next $key") }?.let { key ->
        return WeeklyIntent(listOf(dayMap.getValue(key)), nextWeekOnly = true)
    }

    // Proceed only if it's a recurring phrase
    val recurringSignal = t.contains("every ") || t.contains("each ") || t.contains("weekly")
    if (!recurringSignal) return WeeklyIntent(null, nextWeekOnly = false)

    // Ranges like "mon-wed"
    val order = listOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
        Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY)
    val rangeRegex = Regex("""\b(mon|tue(?:s)?|wed|thu(?:r|rs)?|fri|sat|sun)\s*-\s*(mon|tue(?:s)?|wed|thu(?:r|rs)?|fri|sat|sun)\b""")
    val ranged = rangeRegex.findAll(t).flatMap { m ->
        val a = dayMap.getValue(m.groupValues[1]); val b = dayMap.getValue(m.groupValues[2])
        val ai = order.indexOf(a); val bi = order.indexOf(b)
        if (ai <= bi) order.subList(ai, bi + 1) else (order.subList(ai, order.size) + order.subList(0, bi + 1))
    }.toMutableList()

    // Comma/and-separated like "mon, tue and fri"
    val listRegex = Regex("""\b(mon|tue(?:s)?|wed|thu(?:r|rs)?|fri|sat|sun)\b""")
    val listed = listRegex.findAll(t).map { dayMap.getValue(it.value) }.toList()

    val allDays = (ranged + listed).distinct()
    if (allDays.isNotEmpty()) return WeeklyIntent(allDays, nextWeekOnly = false)

    return WeeklyIntent(null, nextWeekOnly = false)
}



private fun computeNextWeekOnlyMillis(hour: Int, minute: Int, dayOfWeek: Int): Long {
    val now = Calendar.getInstance()
    // Find the Monday-of-this-week anchor (or the week start by locale if desired)
    val anchor = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        // Move to start of week (Monday) then add 7 days to target next week block
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        add(Calendar.WEEK_OF_YEAR, 1)
        // Now set target weekday inside next week
        set(Calendar.DAY_OF_WEEK, dayOfWeek)
    }
    // If somehow before now due to DST, push one week
    if (anchor.timeInMillis <= now.timeInMillis) anchor.add(Calendar.WEEK_OF_YEAR, 1)
    return anchor.timeInMillis
}
// Helpers

private fun hhmmPairsFromFutureTimes(futureTimes: List<Long>): List<Pair<Int, Int>> {
    return futureTimes.map { t ->
        Calendar.getInstance().apply { timeInMillis = t }
    }.map { it.get(Calendar.HOUR_OF_DAY) to it.get(Calendar.MINUTE) }
        .distinct()
}


private fun extractInnerJsonFromResponse(raw: String): String? {
    val jsonObj = runCatching {
        Json { ignoreUnknownKeys = true }.parseToJsonElement(raw).jsonObject
    }.getOrNull() ?: return null
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