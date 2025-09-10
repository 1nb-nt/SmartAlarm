package com.example.alarmchatapp.ui

import android.Manifest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.alarmchatapp.*
import com.example.alarmchatapp.network.AlarmParser
import com.example.alarmchatapp.network.AlarmContract
import com.example.alarmchatapp.network.RetrofitClient
import com.example.alarmchatapp.ui.theme.AlarmListScreen
import com.example.alarmchatapp.utils.AlarmHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import com.example.alarmchatapp.R


@Composable
fun AppContent() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "chat") {
        composable("chat") { ChatScreen(onShow = { navController.navigate("alarms") }) }
        composable("alarms") { AlarmListScreen(onBack = { navController.popBackStack() }) }
    }
}

@Composable
fun ChatScreen(onShow: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<String>() }
    var input by remember { mutableStateOf(TextFieldValue()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        TopBar(onShow)
        Spacer(Modifier.height(6.dp))
        OldUiButtons(onCommandClick = { commandText -> input = TextFieldValue(commandText) })
        MessageList(messages)
        Spacer(Modifier.height(6.dp))
        InputSection(input, { input = it }, scope, context, messages)
    }
}

@Composable
fun OldUiButtons(onCommandClick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(id = R.drawable.wow_logo),
            contentDescription = "Wow Logo",
            modifier = Modifier.size(200.dp)
        )
        Spacer(Modifier.height(20.dp))
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
            Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 4.dp, modifier = Modifier.padding(vertical = 4.dp)) {
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
            onInputChange(TextFieldValue(""))

            scope.launch {
                try {
                    // 1) Build backend payload
                    val payload: Map<String, Any> = mapOf(
                        "objective" to "Alarm Generator",
                        "objective_key" to "alarm_generator",
                        "model" to "openai",
                        "inputs" to mapOf(
                            "user_input" to rawText,
                            "ctype" to "text"
                        )
                    ) // [11][6]

                    // 2) Call and read raw JSON string
                    val http = RetrofitClient.instance.getAlarmDetailsRaw(payload)
                    if (!http.isSuccessful) {
                        messages.add(0, "API failed: ${http.code()}")
                        return@launch
                    } // [19]
                    val bodyStr = http.body()?.string()?:""
                    if (bodyStr.isBlank()) {
                        messages.add(0, "API failed: empty body")
                        return@launch
                    }
                    val innerJson = extractInnerJsonFromResponse(bodyStr)
                    if (innerJson == null) {
                        messages.add(0, "API returned no JSON block; nothing scheduled.")
                        return@launch
                    }
                    // Extract the inner fenced JSON from the "response" field

                    // 3) Parse and validate
                    val parsed: AlarmContract = AlarmParser.parseAlarmJson(innerJson)
                    val (fixed, issues) = AlarmParser.validateAndFixAlarm(parsed)
                    Log.d("AlarmParser", "innerJson=$innerJson")
                    Log.d("AlarmParser", "fixed.notification=${fixed.notification}")
                    issues.forEach { Log.d("AlarmParser", it) } // [2]

                    val title = (fixed.title ?: "").ifBlank { "Alarm" }

                    // 4) Preferred times: notification, then datetime/time/text fallbacks
                    var isoList: List<String> = fixed.notification

                    if (isoList.isEmpty() && !fixed.datetime.isNullOrBlank()) {
                        isoList = listOf(fixed.datetime!!)
                    } // [17]

                    // HH:mm -> today/tomorrow
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
                    } // [17]

                    // Fallback 2.5: dd-MM-yyyy or dd/MM/yyyy + time [+AM/PM]
                    if (isoList.isEmpty()) {
                        val lower = rawText.lowercase(Locale.getDefault()).replace("on", " ")
                        val dateTimeRegex = Regex(
                            """\b(\d{1,2})[/-](\d{1,2})[/-](\d{4})\s*(?:at\s*)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b""",
                            RegexOption.IGNORE_CASE
                        )
                        val m = dateTimeRegex.find(lower)
                        if (m != null) {
                            val d = m.groupValues[1].toIntOrNull()
                            val mo = m.groupValues[8].toIntOrNull()
                            val y = m.groupValues[9].toIntOrNull()
                            val hStr = m.groupValues[10]
                            val minStr = m.groupValues.getOrNull(5)?.takeIf { it.isNotBlank() } ?: "0"
                            val ampm = m.groupValues.getOrNull(6)?.lowercase(Locale.getDefault())

                            val h = hStr.toIntOrNull()
                            val min = minStr.toIntOrNull()

                            if (d != null && mo != null && y != null && h != null && min != null &&
                                d in 1..31 && mo in 1..12 && h in 0..23 && min in 0..59
                            ) {
                                var hour24 = h
                                if (ampm == "pm" && h in 1..11) hour24 = h + 12
                                if (ampm == "am" && h == 12) hour24 = 0

                                val cal = java.util.Calendar.getInstance().apply {
                                    set(java.util.Calendar.SECOND, 0)
                                    set(java.util.Calendar.MILLISECOND, 0)
                                    set(java.util.Calendar.YEAR, y)
                                    set(java.util.Calendar.MONTH, mo - 1)
                                    set(java.util.Calendar.DAY_OF_MONTH, d)
                                    set(java.util.Calendar.HOUR_OF_DAY, hour24)
                                    set(java.util.Calendar.MINUTE, min)
                                }
                                val instant = java.time.Instant.ofEpochMilli(cal.timeInMillis)
                                val offset = java.time.ZoneId.systemDefault().rules.getOffset(instant)
                                val iso = java.time.OffsetDateTime.ofInstant(instant, offset).toString()
                                isoList = listOf(iso)
                            }
                        }
                    } // [17][18]

                    // Fallback 3: time-only ("6 pm", "18:00")
                    if (isoList.isEmpty()) {
                        val lower = rawText.lowercase(Locale.getDefault())
                        val timeRegex = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b""", RegexOption.IGNORE_CASE)
                        val mr = timeRegex.find(lower)
                        if (mr != null) {
                            val gv = mr.groupValues
                            val hourStr = if (gv.size > 1) gv[1] else null
                            val minStr  = if (gv.size > 2) gv[8] else null
                            val ampmStr = if (gv.size > 3) gv[9]?.lowercase(Locale.getDefault()) else null

                            val h = hourStr?.toIntOrNull()
                            val min = (minStr?.takeIf { it.isNotBlank() } ?: "0").toIntOrNull()

                            if (h != null && min != null && h in 0..23 && min in 0..59) {
                                var hour24 = h
                                if (ampmStr == "pm" && h in 1..11) hour24 = h + 12
                                if (ampmStr == "am" && h == 12) hour24 = 0

                                val now = java.util.Calendar.getInstance()
                                val cal = java.util.Calendar.getInstance().apply {
                                    set(java.util.Calendar.SECOND, 0)
                                    set(java.util.Calendar.MILLISECOND, 0)
                                    set(java.util.Calendar.HOUR_OF_DAY, hour24)
                                    set(java.util.Calendar.MINUTE, min)
                                }
                                if (cal.before(now)) cal.add(java.util.Calendar.DAY_OF_YEAR, 1)

                                val instant = java.time.Instant.ofEpochMilli(cal.timeInMillis)
                                val offset = java.time.ZoneId.systemDefault().rules.getOffset(instant)
                                val iso = java.time.OffsetDateTime.ofInstant(instant, offset).toString()
                                isoList = listOf(iso)
                            }
                        }
                    } // [17]

                    if (isoList.isEmpty()) {
                        messages.add(0, "No times from API or text; nothing scheduled.")
                        return@launch
                    }

                    // 5) ISO -> future epoch millis
                    val nowMs = System.currentTimeMillis()
                    val futureTimes = isoList.mapNotNull {
                        runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull()
                    }.filter { it > nowMs }
                        .distinct()
                        .sorted() // [17]

                    if (futureTimes.isEmpty()) {
                        messages.add(0, "No future times after validation; nothing scheduled.")
                        return@launch
                    }

                    // 6) Recurrence and scheduling
                    val txt = rawText.lowercase(Locale.getDefault())
                    val daysFromText: List<Int>? = extractDays(txt)
                    val recStr = (fixed.recurrence as? String)?.lowercase() ?: "once"

                    val dao = AppDatabase.getDatabase(context).alarmDao()
                    var scheduledCount = 0

                    if (!daysFromText.isNullOrEmpty()) {
                        val first = futureTimes.first()
                        val cal = Calendar.getInstance().apply { timeInMillis = first }
                        val hour = cal.get(Calendar.HOUR_OF_DAY)
                        val minute = cal.get(Calendar.MINUTE)
                        val id = dao.insert(
                            Alarm(message = title, triggerTimeMillis = first, isRecurring = true, recurringDays = daysFromText)
                        ).toInt()
                        AlarmHelper.scheduleWeeklyAlarms(context, title, hour, minute, daysFromText, id)
                        scheduledCount = daysFromText.size
                    } else if (recStr == "daily") {
                        val first = futureTimes.first()
                        val cal = Calendar.getInstance().apply { timeInMillis = first }
                        val hour = cal.get(Calendar.HOUR_OF_DAY)
                        val minute = cal.get(Calendar.MINUTE)
                        val allDays = (Calendar.SUNDAY..Calendar.SATURDAY).toList()
                        val id = dao.insert(
                            Alarm(message = title, triggerTimeMillis = first, isRecurring = true, recurringDays = allDays)
                        ).toInt()
                        AlarmHelper.scheduleWeeklyAlarms(context, title, hour, minute, allDays, id)
                        scheduledCount = allDays.size
                    } else {
                        for (t in futureTimes) {
                            val id = dao.insert(
                                Alarm(message = title, triggerTimeMillis = t, isRecurring = false, recurringDays = null)
                            ).toInt()
                            AlarmHelper.scheduleSingleAlarm(context, title, t, id)
                            scheduledCount++
                        }
                    }

                    messages.add(0, "Scheduled $scheduledCount alarm(s) for '$title'.")

                } catch (e: Exception) {
                    Log.e("ChatScreen", "Error", e)
                    messages.add(0, "Failed: ${e.localizedMessage ?: "Unknown error"}")
                }
            }
        }) {
            Icon(imageVector = Icons.Filled.Send, contentDescription = "Send")
        }
    }
}




private fun extractDays(text: String): List<Int>? {
    val lowered = text.lowercase(Locale.getDefault())
    return when {
        lowered.contains("weekdays") -> listOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY
        )
        lowered.contains("weekends") -> listOf(Calendar.SATURDAY, Calendar.SUNDAY)
        lowered.contains("everyday") || lowered.contains("daily") ->
            (Calendar.SUNDAY..Calendar.SATURDAY).toList()
        else -> {
            val map = mapOf(
                "sunday" to Calendar.SUNDAY, "monday" to Calendar.MONDAY, "tuesday" to Calendar.TUESDAY,
                "wednesday" to Calendar.WEDNESDAY, "thursday" to Calendar.THURSDAY,
                "friday" to Calendar.FRIDAY, "saturday" to Calendar.SATURDAY
            )
            val match = map.entries.firstOrNull {
                lowered.contains("every ${it.key}") || lowered.contains(it.key)
            }
            match?.let { listOf(it.value) }
        }
    }
}

private fun extractInnerJsonFromResponse(raw: String): String? {
    val jsonObj = runCatching {
        Json { ignoreUnknownKeys = true }
            .parseToJsonElement(raw).jsonObject
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
