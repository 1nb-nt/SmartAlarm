package com.example.alarmchatapp.ui.theme

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
import com.example.alarmchatapp.utils.AlarmHelper
import com.example.alarmchatapp.utils.LocationUtils
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.Calendar




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
            Text("grox", style = MaterialTheme.typography.bodyMedium)
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
fun InputSection(input: TextFieldValue, onInputChange: (TextFieldValue) -> Unit, coroutineScope: CoroutineScope, context: Context, messageList: MutableList<String>) {
    val TAG = "ChatScreen"
    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    val alarmTitle = response.title.ifEmpty { "Alarm" }

                    val specialDays = when {
                        "weekdays" in raw.lowercase() -> listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
                        "weekends" in raw.lowercase() -> listOf(Calendar.SATURDAY, Calendar.SUNDAY)
                        else -> null
                    }
                    val daysOfWeek = response.daysOfWeek ?: specialDays

                    val isRecurring = response.recurrence.lowercase() != "once" || (daysOfWeek?.size ?: 0) > 1

                    val eventDate = parseApiDateTime(response.datetime, raw)
                    if (eventDate == null) {
                        messageList.add(0, "Could not parse date/time from response")
                        return@launch
                    }

                    val dao = AppDatabase.getDatabase(context).alarmDao()

                    if (!isRecurring) {
                        // For one-time alarms, schedule exactly at parsed datetime
                        val alarm = Alarm(
                            message = alarmTitle,
                            triggerTimeMillis = eventDate.time,
                            isRecurring = false
                        )
                        val alarmId = dao.insert(alarm).toInt()
                        AlarmHelper.scheduleSingleAlarm(context, alarmTitle, eventDate.time, alarmId)
                        messageList.add(0, "Alarm '${alarmTitle}' set for ${SimpleDateFormat("EEE, dd MMM yyyy hh:mm a", Locale.getDefault()).format(eventDate)}")
                    } else {
                        // For recurring alarms, schedule for specified days
                        val alarm = Alarm(
                            message = alarmTitle,
                            triggerTimeMillis = eventDate.time,
                            isRecurring = true
                        )
                        val alarmId = dao.insert(alarm).toInt()
                        AlarmHelper.scheduleWeeklyAlarms(context, alarmTitle, Calendar.getInstance().apply { time = eventDate }.get(Calendar.HOUR_OF_DAY), Calendar.getInstance().apply { time = eventDate }.get(Calendar.MINUTE), daysOfWeek, alarmId)
                        messageList.add(0, "Recurring alarm '${alarmTitle}' set starting ${SimpleDateFormat("EEE, hh:mm a", Locale.getDefault()).format(eventDate)} on days ${daysOfWeek?.joinToString() ?: "undefined"}")
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
fun parseApiDateTime(apiDate: String?, input: String): Date? {
    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "dd-MM-yyyy HH:mm",
        "yyyy-MM-dd",
        "dd-MM-yyyy"
    )

    if (!apiDate.isNullOrBlank()) {
        for (fmt in formats) {
            try {
                val sdf =SimpleDateFormat(fmt, Locale.getDefault())
                sdf.timeZone=TimeZone.getTimeZone("UTC")
            } catch (_: Exception) {}
        }
    }

    val timeRegex = Regex("""\b(\d{1,2})(:(\d{2}))?\s*(am|pm)\b""", RegexOption.IGNORE_CASE)
    val timeMatch = timeRegex.find(input)
    val hour = timeMatch?.groupValues?.get(1)?.toIntOrNull()
    val minute = timeMatch?.groupValues?.get(3)?.toIntOrNull() ?: 0
    val amPm = timeMatch?.groupValues?.get(4)?.lowercase()

    if (hour != null && amPm != null) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (amPm == "pm" && hour < 12) cal.set(Calendar.HOUR_OF_DAY, hour + 12)
        else if (amPm == "am" && hour == 12) cal.set(Calendar.HOUR_OF_DAY, 0)
        else cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        return cal.time
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
    Log.d("ChatScreen", "Scheduled periodic checker for every 24 hours")
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
        calendar.add(Calendar.DATE, 7)
    }

    return calendar.timeInMillis
}

fun getDayName(dayOfWeek: Int): String =
    listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")[dayOfWeek - 1]

fun getNextValidAlarmTime(hour: Int, minute: Int, rawText: String): Long {
    val now = Calendar.getInstance()
    val alarmTime = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    if (alarmTime.timeInMillis <= now.timeInMillis && rawText.contains("today", true)) {
            // if time passed today, schedule for tomorrow instead
            alarmTime.add(Calendar.DATE, 1)
        }
        // For "tomorrow" assume backend parses correctly. Otherwise, handle accordingly here.
    return alarmTime.timeInMillis
}
