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
import com.example.alarmchatapp.*
import com.example.alarmchatapp.network.*
import com.example.alarmchatapp.ui.theme.AlarmListScreen
import com.example.alarmchatapp.utils.AlarmHelper
import com.example.alarmchatapp.utils.LocationUtils
import com.google.gson.*
import kotlinx.coroutines.*
import java.lang.reflect.Type
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import com.example.alarmchatapp.R


class NotificationDeserializer : JsonDeserializer<AlarmApiResponse.NotificationInfo> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): AlarmApiResponse.NotificationInfo? {
        if (json == null || json.isJsonNull) return null
        return when {
            json.isJsonObject -> context?.deserialize(json, typeOfT)
            json.isJsonPrimitive && json.asJsonPrimitive.isBoolean -> null
            else -> null
        }
    }
}

fun schedulePeriodicChecker(context: Context) {
    val workRequest = PeriodicWorkRequestBuilder< TaskExecutionWorker>(24, TimeUnit.HOURS).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("PeriodicChecker", ExistingPeriodicWorkPolicy.KEEP, workRequest)
    Log.d("ChatScreen", "Scheduled periodic checker task.")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent() {
    var showingList by remember { mutableStateOf(false) }
    if (showingList) AlarmListScreen(onBack = { showingList = false })
    else ChatScreen(onShow = { showingList = true })
}

@Composable
fun ChatScreen(onShow: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<String>() }
    var input by remember { mutableStateOf(TextFieldValue("")) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val notifyPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            notifyPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        schedulePeriodicChecker(context)
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), verticalArrangement = Arrangement.SpaceBetween) {
        TopBar(onShow)
        Logo()
        ActionButtons(
            onAction = { text -> input = TextFieldValue(text) },
            onConnect = {
                val loc = LocationUtils.getLastKnownLocation(context)
                Toast.makeText(context, "Location: $loc", Toast.LENGTH_LONG).show()
            }
        )
        MessageList(messages = messages, modifier = Modifier.weight(1f))
        InputSection(input, { input = it }, coroutineScope, context, messages)
    }
}

@Composable
fun TopBar(onShow: () -> Unit) {
    val ctx = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = Modifier.clickable {
            Toast.makeText(ctx, "Settings clicked", Toast.LENGTH_SHORT).show()
        }) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
            Spacer(Modifier.width(8.dp))
            Text("AlarmApp")
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onShow) { Text("Manage Alarms") }
    }
}

@Composable
fun Logo() {
    val logo: Painter = painterResource(id = R.drawable.wow_logo)
    Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
        Image(logo, contentDescription = "Logo")
    }
}

@Composable
fun ActionButtons(onAction: (String) -> Unit, onConnect: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Button(onClick = { onAction("Wake up at 7:00 AM") }) { Text("Wake Up") }
        Button(onClick = { onAction("Remind me in 1 minute") }) { Text("Remind Me") }
        Button(onClick = onConnect) { Text("Connect") }
    }
}

@Composable
fun MessageList(messages: List<String>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxWidth().padding(12.dp), reverseLayout = true) {
        items(messages) { msg ->
            Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 4.dp, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(msg, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
fun InputSection(
    input: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    scope: CoroutineScope,
    context: Context,
    messages: MutableList<String>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = input,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("Type your message") }
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = {
            val text = input.text.trim()
            if (text.isEmpty()) return@IconButton
            onChange(TextFieldValue(""))

            scope.launch {
                try {
                    val request = AlarmApiRequest(
                        objective = "Alarm Generator",
                        objective_key = "alarm_generator",
                        model = "openai",
                        inputs = mapOf("user_input" to text, "ctype" to "text")
                    )
                    val apiResponseWrapper = RetrofitClient.instance.getAlarmDetails(request)
                    val rawJson = apiResponseWrapper.response ?: ""

                    val cleanedJson = rawJson.replace("```")
                        if (!cleanedJson.startsWith("{")) {
                            messages.add(0, "Invalid API response format.")
                            return@launch
                        }

                        val gson = GsonBuilder()
                        .registerTypeAdapter(AlarmApiResponse.NotificationInfo::class.java, NotificationDeserializer())
                        .create()

                    val response = gson.fromJson(cleanedJson, AlarmApiResponse::class.java)
                    val title = response.title ?: "Alarm"
                    val dateVal = response.datetime

                    val eventDate = parseIso8601Date(dateVal) ?: parseTimeString(response.time)
                    if (eventDate == null) {
                        messages.add(0, "Could not parse date/time")
                        return@launch
                    }

                    if(eventDate.before(Date())){
                        messages.add(0, "Cannot set alarm in the past")
                        return@launch
                    }

                    val dao = AppDatabase.getDatabase(context).alarmDao()
                    val isRecurring = (response.recurrence ?: "once").lowercase() != "once" || (response.daysOfWeek?.size ?: 0) > 1

                    if (isRecurring) {
                        val calendar = Calendar.getInstance().apply { time = eventDate }
                        val scheduleDays = response.daysOfWeek ?: listOf(calendar.get(Calendar.DAY_OF_WEEK))
                        val alarm = Alarm(message = title, triggerTimeMillis = calendar.timeInMillis, isRecurring = true)
                        val id = dao.insert(alarm).toInt()
                        AlarmHelper.scheduleRecurringAlarm(context, title, calendar, scheduleDays, id)
                        messages.add(0, "Set recurring alarm for ${scheduleDays.joinToString()}")
                    } else {
                        val alarm = Alarm(message = title, triggerTimeMillis = eventDate.time, isRecurring = false)
                        val id = dao.insert(alarm).toInt()
                        AlarmHelper.scheduleSingleAlarm(context, title, eventDate.time, id)
                        messages.add(0, "Alarm set for ${formatDate(eventDate)}")
                    }
                } catch (e: Exception) {
                    Log.e("ChatScreen", "Error scheduling alarm", e)
                    messages.add(0, "Error scheduling alarm: ${e.localizedMessage}")
                }
            }
        }) {
            Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = Color.Blue)
        }
    }
}

// Helper functions used in InputSection:

fun parseIso8601Date(dateStr: String?): Date? {
    if (dateStr.isNullOrBlank()) return null
    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'"
    )
    for (format in formats) {
        try {
            val sdf = SimpleDateFormat(format, Locale.getDefault())
            sdf.isLenient = false
            val date = sdf.parse(dateStr.trim())
            if (date != null) return date
        } catch (_: Exception) {}
    }
    return null
}

fun parseTimeString(timeStr: String?): Date? {
    if (timeStr.isNullOrBlank()) return null
    return try {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val cal = Calendar.getInstance()
        val parsedCal = Calendar.getInstance()
        parsedCal.time = sdf.parse(timeStr.trim())!!
        cal.set(Calendar.HOUR_OF_DAY, parsedCal.get(Calendar.HOUR_OF_DAY))
        cal.set(Calendar.MINUTE, parsedCal.get(Calendar.MINUTE))
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.before(Calendar.getInstance())) cal.add(Calendar.DAY_OF_YEAR, 1)
        cal.time
    } catch (_: Exception) {
        null
    }
}

fun formatDate(date: Date): String = SimpleDateFormat("EEE, dd MMM yyyy hh:mm a", Locale.getDefault()).format(date)

fun dayName(day: Int): String = listOf("Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday")[day-1]
