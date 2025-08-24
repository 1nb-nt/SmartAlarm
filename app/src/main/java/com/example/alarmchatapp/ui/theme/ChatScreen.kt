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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent() {
    var showAlarmList by remember { mutableStateOf(false) }
    if (showAlarmList) {
        AlarmListScreen(onBack = { showAlarmList = false })
    } else {
        ChatScreen(onShowAlarms = { showAlarmList = true })
    }
}

@Composable
fun ChatScreen(onShowAlarms: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val messageList = remember { mutableStateListOf<String>() }
    var input by remember { mutableStateOf(TextFieldValue("")) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}
    val notifyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

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
        TopBarSection(onShowAlarms)
        LogoSection()
        ButtonControlsSection(onInsertText = { text -> input = TextFieldValue(text) })
        MessageListSection(messageList, modifier = Modifier.weight(1f).fillMaxWidth())
        InputSection(input, { input = it }, coroutineScope, context, messageList)
    }
}

@Composable
fun TopBarSection(onShowAlarms: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.clickable {
                Toast.makeText(context, "Settings clicked", Toast.LENGTH_SHORT).show()
            },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(6.dp))
            Text("grox", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onShowAlarms) {
            Text("Manage Alarms")
        }
    }
}

@Composable
fun LogoSection() {
    val logo: Painter = painterResource(id = com.example.alarmchatapp.R.drawable.wow_logo)
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(logo, contentDescription = "Logo", modifier = Modifier.size(140.dp))
    }
}

@Composable
fun ButtonControlsSection(onInsertText: (String) -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(onClick = { onInsertText("Wake up at 07:00 am") }) { Text("Wake Up") }
        Button(onClick = { onInsertText("Remind me in 1 minute") }) { Text("Remind Me") }
        Button(onClick = {
            val loc = LocationUtils.getLastKnownLocation(context)
            Toast.makeText(context, "Location: $loc", Toast.LENGTH_LONG).show()
        }) {
            Text("Connect")
        }
    }
}

@Composable
fun MessageListSection(messageList: List<String>, modifier: Modifier) {
    LazyColumn(
        modifier = modifier.heightIn(max = 150.dp).padding(horizontal = 12.dp),
        reverseLayout = true
    ) {
        items(messageList) { msg ->
            Surface(
                shape = RoundedCornerShape(12),
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(msg, Modifier.padding(12.dp))
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = input,
            onValueChange = onInputChange,
            placeholder = { Text("Type your message") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = {
            val rawText = input.text.trim()
            if (rawText.isBlank()) return@IconButton
            onInputChange(TextFieldValue(""))
            messageList.add(0, "You: $rawText")

            coroutineScope.launch {
                try {
                    val response = RetrofitClient.instance.getAlarmDetails(AlarmApiRequest(rawText))
                    val alarmTitle = response.title
                    val eventDate = parseApiDateTime(response.datetime, rawText)
                    if (eventDate == null) {
                        messageList.add(0, "Error: Could not parse time")
                        return@launch
                    }
                    val eventMillis = eventDate.time
                    val cal = Calendar.getInstance().apply { timeInMillis = eventMillis }

                    val db = AppDatabase.getDatabase(context)
                    val alarmDao = db.alarmDao()
                    Log.d("","Days of week: $response.daysOfWeek")
                    val isRecurring = response.daysOfWeek?.isNotEmpty() ?: false

                    if (isRecurring) {
                        // Schedule recurring alarm in Clock app
                        AlarmHelper.scheduleWeeklyAlarms(
                            context,
                            alarmTitle,
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            response.daysOfWeek
                        )
                        messageList.add(0, "Recurring alarm set for $alarmTitle at ${SimpleDateFormat("hh:mm a", Locale.getDefault()).format(eventDate)} on selected days.")
                    } else {
                        // Schedule single alarm in app with exact time
                        val alarm = Alarm(
                            message = alarmTitle,
                            triggerTimeMillis = eventMillis,
                            isRecurring = false
                        )
                        val alarmId = withContext(Dispatchers.IO) { alarmDao.insert(alarm).toInt() }
                        AlarmHelper.scheduleInAppAlarm(context, alarmTitle, eventMillis, alarmId)
                        messageList.add(0, "One-time alarm set for $alarmTitle at ${SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault()).format(eventDate)}")
                    }
                } catch (e: Exception) {
                    Log.e("ChatScreen", "Failed to schedule alarm", e)
                    messageList.add(0, "Failed to schedule alarm")
                }
            }
        }) {
            Icon(Icons.Default.Send, contentDescription = "Send", tint = Color(0xFF9C27))
        }
    }
}

fun parseApiDateTime(apiDate: String, input: String): Date? {
    if (apiDate.isNotBlank()) {
        val formats = listOf("yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss")
        for (fmt in formats) {
            try {
                return SimpleDateFormat(fmt, Locale.getDefault()).parse(apiDate)
            } catch (_: Exception) {}
        }
    }
    try {
        val datePart = Regex("""\b(\d{2}-\d{2}-\d{4})\b""").find(input)?.value
        val timePart = Regex("""\b(\d{1,2}:\d{2}\s*[ap]m)\b""", RegexOption.IGNORE_CASE).find(input)?.value
        if (datePart != null && timePart != null) {
            return SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault()).parse("$datePart $timePart")
        }
    } catch (_: Exception) {}
    return null
}

fun schedulePeriodicChecker(context: Context) {
    val workRequest = PeriodicWorkRequestBuilder<com.example.alarchatmapp.TaskExecutionWorker>(
        24, TimeUnit.HOURS
    ).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "PeriodicChecker",
        ExistingPeriodicWorkPolicy.KEEP,
        workRequest
    )
    Log.d("ChatScreen", "Scheduled periodic checker for every 24 hours")
}
