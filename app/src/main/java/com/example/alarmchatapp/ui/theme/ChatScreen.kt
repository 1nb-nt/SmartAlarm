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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.alarchatmapp.TaskExecutionWorker
import com.example.alarmchatapp.AppDatabase
import com.example.alarmchatapp.Alarm
import com.example.alarmchatapp.network.AlarmApiRequest
import com.example.alarmchatapp.network.RetrofitClient
import com.example.alarmchatapp.ui.theme.AlarmListScreen
import com.example.alarmchatapp.utils.AlarmHelper
import com.example.alarmchatapp.utils.LocationUtils
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent() {
    var showAlarmList by remember { mutableStateOf(false) }
    if (showAlarmList) {
        AlarmListScreen(onBack = { showAlarmList = false })
    } else {
        ChatScreen(onShow = { showAlarmList = true })
    }
}

@Composable
fun ChatScreen(onShow: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val messageList = remember { mutableStateListOf<String>() }
    var input by remember { mutableStateOf(TextFieldValue("")) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Handle permission result if needed
    }

    val notifyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Handle permission result if needed
    }

    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) {
            notifyPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        schedulePeriodicChecker(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        TopBarSection(onShow)
        LogoSection()
        ButtonControlsSection(onInsert = { text -> input = TextFieldValue(text) })
        MessageListSection(messageList, modifier = Modifier.weight(1f))
        InputSection(input, { input = it }, coroutineScope, context, messageList)
    }
}

@Composable
fun TopBarSection(onShow: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.clickable { Toast.makeText(context, "Settings clicked", Toast.LENGTH_SHORT).show() }) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
            Spacer(Modifier.width(8.dp))
            Text("wow", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onShow) {
            Text("Manage Alarms")
        }
    }
}

@Composable
fun LogoSection() {
    val logo: Painter = painterResource(id = com.example.alarmchatapp.R.drawable.wow_logo)
    Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
        Image(logo, contentDescription = "Logo")
    }
}

@Composable
fun ButtonControlsSection(onInsert: (String) -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(onClick = { onInsert("Wake up at 07:00 am") }) { Text("Wake Up") }
        Button(onClick = { onInsert("Remind me in 1 minute") }) { Text("Remind Me") }
        Button(onClick = {
            val loc = LocationUtils.getLastKnownLocation(context)
            Toast.makeText(context, "Location: $loc", Toast.LENGTH_LONG).show()
        }) { Text("Connect") }
    }
}

@Composable
fun MessageListSection(messageList: List<String>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 150.dp)
            .padding(12.dp),
        reverseLayout = true
    ) {
        items(messageList) { msg ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 4.dp,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(msg, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
fun InputSection(
    input: TextFieldValue,
    onInputChange: (TextFieldValue) -> Unit,
    coroutineScope: CoroutineScope,
    context: Context,
    messageList: MutableList<String>
) {
    val TAG = "ChatScreen"
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("Type your message") }
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = {
            val raw = input.text.trim()
            if (raw.isBlank()) return@IconButton
            onInputChange(TextFieldValue(""))

            coroutineScope.launch {
                try {
                    val request = AlarmApiRequest(
                        objective = "Alarm Generator",
                        objective_key = "alarm_generator",
                        model = "openai",
                        inputs = mapOf("user_input" to raw, "ctype" to "text")
                    )
                    val response = RetrofitClient.instance.getAlarmDetails(request)
                    val alarmTitle = response.title?.takeIf { it.isNotEmpty() } ?: "Alarm"

                    // Keyword-based recurring types (unchanged)
                    val specialDays = when {
                        raw.contains("weekdays", true) -> listOf(
                            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                            Calendar.THURSDAY, Calendar.FRIDAY
                        )
                        raw.contains("weekends", true) -> listOf(Calendar.SATURDAY, Calendar.SUNDAY)
                        raw.contains("everyday", true) || raw.contains("daily", true) -> listOf(
                            Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
                            Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
                        )
                        else -> null
                        //todo :Handle tomorrow here , no extraction needed only api response should be considered
                    }

                    // Day names map
                    val dayNameMap = mapOf(
                        "sunday" to Calendar.SUNDAY,
                        "monday" to Calendar.MONDAY,
                        "tuesday" to Calendar.TUESDAY,
                        "wednesday" to Calendar.WEDNESDAY,
                        "thursday" to Calendar.THURSDAY,
                        "friday" to Calendar.FRIDAY,
                        "saturday" to Calendar.SATURDAY
                    )

                    // Compose final daysOfWeek (for correct recurring)
                    var daysOfWeek = response.daysOfWeek ?: specialDays //todo: Special days needed either from input or API
//                    if (daysOfWeek == null) {
//                        val detectedDays = dayNameMap.entries.filter { (name, _) ->
//                            raw.lowercase().contains(name)
//                        }.map { it.value }
//                        daysOfWeek = if (detectedDays.isNotEmpty()) detectedDays else null
//                    }

                    val isRecurring =
                        (response.recurrence?.lowercase(Locale.getDefault()) ?: "once") != "once" ||
                                (daysOfWeek?.size ?: 0) > 1//todo:needed either from input or API --find a better logic.

                    // Parsed date from API or raw (priority: API > custom date > day+time > time only)
                    var eventDate = parseApiDateTime(response.datetime, raw)//todo:Include extractcustomdatefrom raw inside parse api date and time
                    if (eventDate == null) {
                        eventDate = extractCustomDateFromRaw(raw)
                    }
                    if (eventDate == null) {
                        val dayAndTime = extractDayAndTimeFromRaw(raw)
                        if (dayAndTime != null) {
                            val (dayOfWeek, timePair) = dayAndTime
                            val (hour, minute) = timePair
                            val nextTrigger = getNextOccurrence(hour, minute, dayOfWeek)
                            eventDate = Date(nextTrigger)
                            if (daysOfWeek == null) daysOfWeek = listOf(dayOfWeek)
                        }
                    }
                    if (eventDate == null) {
                        val extractedTime = extractTimeFromRaw(raw)
                        if (extractedTime != null) {
                            val cal = Calendar.getInstance()
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            cal.set(Calendar.HOUR_OF_DAY, extractedTime.first)
                            cal.set(Calendar.MINUTE, extractedTime.second)
                            if (cal.timeInMillis <= System.currentTimeMillis() && raw.contains("today", true)) {
                                cal.add(Calendar.DATE, 1)
                            }
                            eventDate = cal.time
                        }
                    }//todo:Return type should be standard daate or calendar object
                    //todo:Combine all date and time information inside one value,if multiple needed just list of calendar or list ofstandara date
                    if (eventDate == null) {
                        messageList.add(0, "Could not parse date/time from input or response")
                        return@launch
                    }

                    val dao = AppDatabase.getDatabase(context).alarmDao()

                    if (!isRecurring) {
                        val calendar = Calendar.getInstance().apply { time = eventDate }
                        val now = Calendar.getInstance()
                        if (calendar.before(now)) {
                            messageList.add(0, "Cannot schedule alarm in the past")
                            return@launch
                        }
                        val triggerTimeMillis =
                            getNextValidAlarmTime(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), raw)
                        val alarm = Alarm(
                            message = alarmTitle,
                            triggerTimeMillis = triggerTimeMillis,
                            isRecurring = false
                        )
                        val alarmId = dao.insert(alarm).toInt()
                        AlarmHelper.scheduleSingleAlarm(context, alarmTitle, triggerTimeMillis, alarmId)
                        messageList.add(
                            0,
                            "Alarm '${alarmTitle}' set for ${
                                SimpleDateFormat(
                                    "EEE, dd MMM yyyy hh:mm a",
                                    Locale.getDefault()
                                ).format(Date(triggerTimeMillis))
                            }"
                        )
                    } else {
                        val calendar = Calendar.getInstance().apply { time = eventDate }
                        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
                        val minute = calendar.get(Calendar.MINUTE)
                        val finalDaysOfWeek: List<Int> = daysOfWeek ?: listOf(calendar.get(Calendar.DAY_OF_WEEK))

                        val alarm = Alarm(
                            message = alarmTitle,
                            triggerTimeMillis = eventDate.time,
                            isRecurring = true
                        )
                        val alarmId = dao.insert(alarm).toInt()

                        finalDaysOfWeek.forEach { day ->
                            val nextTriggerTime = getNextOccurrence(hourOfDay, minute, day)
                            // Optionally log trigger time
                        }

                        AlarmHelper.scheduleWeeklyAlarms(
                            context,
                            alarmTitle,
                            hourOfDay,
                            minute,
                            finalDaysOfWeek,
                            alarmId
                        )
                        messageList.add(
                            0,
                            "Recurring alarm '${alarmTitle}' set starting ${
                                SimpleDateFormat(
                                    "EEE, hh:mm a",
                                    Locale.getDefault()
                                ).format(eventDate)
                            } on days ${finalDaysOfWeek.joinToString { getDayName(it) }}"
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed scheduling alarm", e)
                    messageList.add(0, "Error scheduling alarm: ${e.message ?: "unknown error"}")
                }
            }
        }) {
            Icon(imageVector = Icons.Filled.Send, contentDescription = "Send", tint = Color.Blue)
        }
    }
}
fun extractCustomDateFromRaw(raw: String): Date? {
    val patterns = listOf(
        Pair("""(\d{2}-\d{2}-\d{4})\s*at\s*(\d{1,2})(:(\d{2}))?\s*(am|pm)""", "dd-MM-yyyy hh:mm a"),
        Pair("""(\d{2}/\d{2}/\d{4})\s*at\s*(\d{1,2})(:(\d{2}))?\s*(am|pm)""", "dd/MM/yyyy hh:mm a"),
        Pair("""(\d{4}-\d{2}-\d{2})\s*at\s*(\d{1,2})(:(\d{2}))?\s*(am|pm)""", "yyyy-MM-dd hh:mm a"),
        Pair("""(\d{2}-\d{2}-\d{4})""", "dd-MM-yyyy"),
        Pair("""(\d{2}/\d{2}/\d{4})""", "dd/MM/yyyy"),
        Pair("""(\d{4}-\d{2}-\d{2})""", "yyyy-MM-dd")
    )
    for ((pattern, format) in patterns) {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        val match = regex.find(raw)
        if (match != null) {
            val datePart = match.groups[1]?.value ?: continue
            val hour = match.groups[2]?.value?.toIntOrNull() ?: 0
            val minute = match.groups[4]?.value?.toIntOrNull() ?: 0
            val amPm = match.groups[5]?.value ?: "am"
            try {
                val sdf = SimpleDateFormat(format, Locale.getDefault())
                val dateString = if (format.contains("hh")) {
                    "$datePart ${"%02d".format(hour)}:${"%02d".format(minute)} $amPm"
                } else{
                    datePart
                }
                sdf.timeZone = TimeZone.getDefault()
                return sdf.parse(dateString)
            } catch (_: Exception) { }
        }
    }
    return null
}

fun extractDayAndTimeFromRaw(raw: String): Pair<Int, Pair<Int, Int>>? {
    val days = mapOf(
        "sunday" to Calendar.SUNDAY,
        "monday" to Calendar.MONDAY,
        "tuesday" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY,
        "thursday" to Calendar.THURSDAY,
        "friday" to Calendar.FRIDAY,
        "saturday" to Calendar.SATURDAY
    )
    val dayMatch = days.entries.find { raw.lowercase().contains(it.key,ignoreCase = true) }?.value
    val time = extractTimeFromRaw(raw)
    return if (dayMatch != null && time != null) Pair(dayMatch, time) else null
}

fun extractTimeFromRaw(raw: String): Pair<Int, Int>? {
    val match = Regex("""\b(\d{1,2})(:(\d{2}))?\s*(am|pm)\b""", RegexOption.IGNORE_CASE).find(raw)
    if (match != null) {
        var hour = match.groups[1]?.value?.toIntOrNull() ?: return null
        val minute = match.groups[3]?.value?.toIntOrNull() ?: 0
        val amPm = match.groups[4]?.value?.lowercase(Locale.getDefault())
        if (amPm == "pm" && hour < 12) hour += 12
        if (amPm == "am" && hour == 12) hour = 0
        return Pair(hour, minute)
    }
    return null
}

fun parseApiDateTime(apiTime: String?, raw: String): Date? {
    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss",
        "dd-MM-yyyy HH:mm",
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd",
        "dd-MM-yyyy"
    )

    if (!apiTime.isNullOrBlank()) {
        val locale = Locale.getDefault()
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, locale)
                if (!format.contains("XXX")) {
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                }
                sdf.isLenient = false
                return sdf.parse(apiTime)
            } catch (e: Exception) {
                // ignore parse errors and try others
            }
        }
    }
    return null
}

fun schedulePeriodicChecker(context: Context) {
    val workRequest = PeriodicWorkRequestBuilder<TaskExecutionWorker>(
        24, TimeUnit.HOURS
    ).build()
    WorkManager.getInstance().enqueueUniquePeriodicWork(
        "PeriodicChecker",
        ExistingPeriodicWorkPolicy.KEEP,
        workRequest
    )
    Log.d("ChatScreen", "Scheduled periodic checker every 24 hours")
}

fun getNextOccurrence(hour: Int, minute: Int, dayOfWeek: Int): Long {
    val now = Calendar.getInstance()

    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, hour)
    calendar.set(Calendar.MINUTE, minute)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    calendar.set(Calendar.DAY_OF_WEEK, dayOfWeek)
    if (calendar.timeInMillis <= now.timeInMillis) {
        calendar.add(Calendar.WEEK_OF_YEAR, 7)
    }

    return calendar.timeInMillis
}

fun getDayName(dayOfWeek: Int): String =
    listOf(
        "Sunday",
        "Monday",
        "Tuesday",
        "Wednesday",
        "Thursday",
        "Friday",
        "Saturday"
    )[dayOfWeek - 1]

fun getNextValidAlarmTime(hour: Int, minute: Int, rawText: String): Long {
    val now = Calendar.getInstance()
    val alarmTime = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    when {
        rawText.contains("today", true) -> {
            if (alarmTime.timeInMillis <= now.timeInMillis) {
                alarmTime.add(Calendar.DATE, 1)
            }
        }
        rawText.contains("tomorrow", true) -> {
            alarmTime.add(Calendar.DATE, 1)
        }
        else -> {
            if (alarmTime.timeInMillis <= now.timeInMillis) {
                alarmTime.add(Calendar.DATE, 1)
            }
        }
    }
    return alarmTime.timeInMillis
}

