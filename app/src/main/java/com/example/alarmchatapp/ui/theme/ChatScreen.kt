package com.example.alarmchatapp.ui

import android.Manifest
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.alarmchatapp.AppDatabase
import com.example.alarmchatapp.Alarm
import com.example.alarmchatapp.network.AlarmApiRequest
import com.example.alarmchatapp.network.RetrofitClient
import com.example.alarmchatapp.ui.theme.AlarmListScreen
import com.example.alarmchatapp.utils.AlarmHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.example.alarmchatapp.R
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.format.DateTimeFormatter
import java.util.Date
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController


@Composable
fun AppContent() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "chat") {
        composable("chat") {
            ChatScreen(onShow = {
                navController.navigate("alarms")
            })
        }
        composable("alarms") {
            AlarmListScreen(onBack = {
                navController.popBackStack()
            })
        }
    }
}
@Composable
fun ChatScreen(onShow: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<String>() }
    var input by remember { mutableStateOf(TextFieldValue()) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions += Manifest.permission.POST_NOTIFICATIONS
        permissionLauncher.launch(permissions.toTypedArray())
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        TopBar(onShow)
        Spacer(Modifier.height(6.dp))
        OldUiButtons(onCommandClick = { commandText ->
            input = TextFieldValue(commandText)
        })
        MessageList(messages)
        Spacer(Modifier.height(6.dp))
        InputSection(input, { input = it }, scope, context, messages)
    }
}

@Composable
fun OldUiButtons(onCommandClick: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.wow_logo),
            contentDescription = "Wow Logo",
            modifier = Modifier.size(200.dp)
        )
        Spacer(Modifier.height(20.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { onCommandClick("Wake Up at 7 AM tomorrow") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Wake Up")
            }
            Button(
                onClick = { onCommandClick("Remind me at 9 AM daily") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Remind Me")
            }
        }
    }
}

@Composable
fun TopBar(onShow: () -> Unit) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
fun MessageList(messages: List<String>) {
    LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = 180.dp).padding(12.dp),
        reverseLayout = true
    ) {
        items(messages) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 4.dp,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(it, Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
fun InputSection(
    input: TextFieldValue,
    onInputChange: (TextFieldValue) -> Unit,
    scope: CoroutineScope,
    context: Context,
    messages: MutableList<String>
) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
            onInputChange(TextFieldValue(""))  // Clear input

            scope.launch {
                try {
                    val request = AlarmApiRequest(
                        objective = "Alarm Generator",
                        objective_key = "alarm_generator",
                        model = "openai",
                        inputs = mapOf("user_input" to rawText, "ctype" to "text")
                    )
                    val response = RetrofitClient.instance.getAlarmDetails(request)
                    val title = response.title ?: "Alarm"
                    val rawTextLower = rawText.lowercase()

                    val exclusions = extractExcludedDays(rawTextLower)
                    val baseDays = response.daysOfWeek ?: extractDays(rawTextLower)
                    val filteredDays = baseDays?.let { days -> filterExcludedDays(days, exclusions) } ?: emptyList()

                    // Attempt to parse date from API ISO string with ThreeTenABP
                    val parsedDate = parseDateISO(response.datetime) ?: parseDate(response.datetime) ?: run {
                        // Fallback parsing: parse custom date and explicitly assign time
                        val dateOnly = parseCustomDate(rawText)
                        if (dateOnly != null) {
                            val time = extractTime(rawText)
                            val cal = Calendar.getInstance(TimeZone.getDefault())
                            cal.time = dateOnly
                            if (time != null) {
                                cal.set(Calendar.HOUR_OF_DAY, time.first)
                                cal.set(Calendar.MINUTE, time.second)
                            } else {
                                cal.set(Calendar.HOUR_OF_DAY, 9)
                                cal.set(Calendar.MINUTE, 0)
                            }
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            cal.time
                        } else null
                    } ?: extractDayTime(rawText)?.let { (day, time) ->
                        val cal = Calendar.getInstance(TimeZone.getDefault())
                        cal.set(Calendar.HOUR_OF_DAY, time.first)
                        cal.set(Calendar.MINUTE, time.second)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        getNextOccurrence(cal, day)
                    } ?: extractTime(rawText)?.let { (hour, min) ->
                        val cal = Calendar.getInstance(TimeZone.getDefault())
                        cal.set(Calendar.HOUR_OF_DAY, hour)
                        cal.set(Calendar.MINUTE, min)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        if (cal.before(Calendar.getInstance())) cal.add(Calendar.DATE, 1)
                        cal.time
                    }

                    if (parsedDate == null) {
                        messages.add(0, "Cannot parse alarm date/time.")
                        return@launch
                    }

                    if (parsedDate.before(Date())) {
                        messages.add(0, "Cannot schedule alarm in the past.")
                        return@launch
                    }

                    val timezone = runCatching { TimeZone.getTimeZone(response.timezone ?: "Asia/Kolkata") }.getOrDefault(TimeZone.getDefault())
                    val eventDate = adjustToNextValidDate(parsedDate, timezone)
                    val dao = AppDatabase.getDatabase(context).alarmDao()
                    val isRecurring = !(response.recurrence.equals("once", true) || filteredDays.isEmpty())

                    if (!isRecurring) {
                        val cal = Calendar.getInstance()
                        cal.time = eventDate
                        val triggerTime = getValidTriggerTime(cal, rawText)
                        if (triggerTime <= System.currentTimeMillis()) {
                            messages.add(0, "Alarm time already passed.")
                            return@launch
                        }
                        val alarm = Alarm(message = title, triggerTimeMillis = triggerTime, isRecurring = false)
                        val id = dao.insert(alarm).toInt()
                        AlarmHelper.scheduleSingleAlarm(context, title, triggerTime, id)
                        messages.add(0, "Alarm set for ${formatDate(triggerTime)}")

                    } else {
                        val scheduleDays = if (filteredDays.isEmpty()) listOf(Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) else filteredDays
                        val cal = Calendar.getInstance()
                        cal.time = eventDate
                        val alarm = Alarm(message = title, triggerTimeMillis = eventDate.time, isRecurring = true)
                        val id = dao.insert(alarm).toInt()
                        AlarmHelper.scheduleWeeklyAlarms(context, title, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), scheduleDays, id)
                        messages.add(0, "Recurring alarm set for days: ${scheduleDays.joinToString()}")
                    }
                } catch (e: Exception) {
                    Log.e("InputSection", "Error scheduling alarm", e)
                    messages.add(0, "Failed to schedule alarm: ${e.localizedMessage ?: "Unknown error"}")
                }
            }
        }) {
            Icon(imageVector = Icons.Filled.Send, contentDescription = "Send")
        }
    }
}


// Helpers: Implement precisely per naming & signatures used above
fun parseDateISO(dateStr: String?): Date? {
    return try {
        if (dateStr == null) return null
        val odt = OffsetDateTime.parse(dateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            return Date(odt.toInstant().toEpochMilli())  // <-- CORRECT FOR THREETENABP!
    } catch (e: Exception) {
        null
    }
}
fun parseDate(dateStr: String?): Date? {
    if (dateStr.isNullOrEmpty()) return null
    val formats = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss", "dd-MM-yyyy HH:mm", "yyyy-MM-dd HH:mm", "dd-MM-yyyy", "yyyy-MM-dd")
    for (fmt in formats) {
        try {
            val sdf = SimpleDateFormat(fmt, Locale.getDefault())
            sdf.isLenient = false
            if (fmt == "yyyy-MM-dd'T'HH:mm:ss'Z'") {
                sdf.timeZone = TimeZone.getTimeZone("UTC")
            } else {
                // Use default device timezone for other formats
                sdf.timeZone = TimeZone.getDefault()
            }

            val d = sdf.parse(dateStr)
            if (d != null) return d
        } catch (_: Exception) {}
    }
    return null
}

fun parseCustomDate(text: String): Date? {
    val regexes = listOf("""\b\d{2}[-/]\d{2}[-/]\d{4}\b""", """\b(?:january|february|march|april|may|june|july|august|september|october|november|december) \d{1,2}\b""")
    for (regex in regexes) {
        val match = Regex(regex, RegexOption.IGNORE_CASE).find(text)
        if (match != null) {
            val dateText = match.value
            val formats = listOf("dd-MM-yyyy HH:mm", "dd/MM/yyyy HH:mm", "dd-MM-yyyy", "dd/MM/yyyy", "MMMM d", "MMMM dd")
            for (fmt in formats) {
                try {
                    val sdf = SimpleDateFormat(fmt, Locale.getDefault())
                    sdf.isLenient = false
                    sdf.timeZone= TimeZone.getDefault()
                    val d = sdf.parse(dateText)
                    if (d != null) return d
                } catch (_: Exception) {}
            }
        }
    }
    return null
}

fun extractDayTime(text: String): Pair<Int, Pair<Int, Int>>? {
    val days = mapOf("sunday" to Calendar.SUNDAY, "monday" to Calendar.MONDAY, "tuesday" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY, "thursday" to Calendar.THURSDAY,
        "friday" to Calendar.FRIDAY, "saturday" to Calendar.SATURDAY)
    val dayEntry = days.entries.firstOrNull { text.contains(it.key, true) } ?: return null
    val time = extractTime(text) ?: return null
    return dayEntry.value to time
}

fun extractTime(text: String): Pair<Int, Int>? {
    val regex = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""", RegexOption.IGNORE_CASE)
    val match = regex.find(text) ?: return null
    var hour = match.groups[1]?.value?.toIntOrNull() ?: return null
    val minute = match.groups[2]?.value?.toIntOrNull() ?: 0
    val ampm = match.groups[3]?.value?.lowercase()
    if (ampm == "pm" && hour < 12) hour += 12
    if (ampm == "am" && hour == 12) hour = 0
    return hour to minute
}

fun getNextOccurrence(cal: Calendar, targetDay: Int): Date {
    cal.set(Calendar.DAY_OF_WEEK, targetDay)
    if (cal.before(Calendar.getInstance())) cal.add(Calendar.WEEK_OF_YEAR, 1)
    return cal.time
}

fun getValidTriggerTime(cal: Calendar, rawText: String): Long {
    val now = Calendar.getInstance()

    if (rawText.contains("today", ignoreCase = true) && cal.before(now)) {
        cal.add(Calendar.DATE, 1)
    } else if (rawText.contains("tomorrow", ignoreCase = true)) {
        // Only add a day if not already shifted by parser
        if (cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) {
            cal.add(Calendar.DATE, 1)
        }
    } else if (cal.before(now)) {
        cal.add(Calendar.DATE, 1)
    }
    return cal.timeInMillis
}


fun adjustToNextValidDate(date: Date, timezone: TimeZone): Date {
    val cal = Calendar.getInstance(timezone).apply { time = date }
    val now = Calendar.getInstance(timezone)
    while (cal.before(now)) {
        cal.add(Calendar.DATE, 7)
    }
    return cal.time
}


fun extractExcludedDays(text: String): List<Int> {
    val days = mapOf("sunday" to Calendar.SUNDAY, "monday" to Calendar.MONDAY, "tuesday" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY, "thursday" to Calendar.THURSDAY,
        "friday" to Calendar.FRIDAY, "saturday" to Calendar.SATURDAY)
    val lowered = text.lowercase()
    return days.filter { lowered.contains("except ${it.key}") }.values.toList()
}


fun filterExcludedDays(days: List<Int>, excluded: List<Int>): List<Int> =
    days.filterNot { it in excluded }

fun extractDays(text: String): List<Int>? {
    val lowered = text.lowercase()
    return when {
        lowered.contains("weekdays") -> listOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY
        )

        lowered.contains("weekends") -> listOf(Calendar.SATURDAY, Calendar.SUNDAY)
        lowered.contains("everyday") || lowered.contains("daily") -> (1..7).toList()
        else -> null
    }
}

fun formatDate(millis: Long): String =
    SimpleDateFormat("EEE, dd MMM yyyy hh:mm a", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }.format(Date(millis))
