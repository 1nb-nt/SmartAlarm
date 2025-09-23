package com.example.alarmchatapp.ui.theme

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.alarmchatapp.Alarm
import com.example.alarmchatapp.utils.AlarmHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

enum class Sender { User, App }
data class ChatMessage(val text: String, val sender: Sender)

@Composable
fun ChatScreen(onShow: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var isProcessing by remember { mutableStateOf(false) }

    // Permissions (match old UI)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    LaunchedEffect(Unit) {
        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        TopBar(onShow)
        Spacer(Modifier.height(48.dp))
        MessageList(messages)
        Spacer(Modifier.height(12.dp))
        InputSection(
            input = input,
            onInputChange = { input = it },
            scope = scope,
            context = ctx,
            messages = messages,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            onProcessingChange = { isProcessing = it }
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TopBar(onShow: () -> Unit) {
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.clickable {
                Toast.makeText(ctx, "Settings clicked", Toast.LENGTH_SHORT).show()
            },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings")
            Spacer(Modifier.width(8.dp))
            Text("Alarm App")
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onShow) { Text("Manage") }
    }
}

@Composable
private fun MessageList(messages: List<ChatMessage>) {
    LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = 320.dp).padding(horizontal = 12.dp),
        reverseLayout = true
    ) {
        items(messages) { msg ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = if (msg.sender == Sender.User) Arrangement.End else Arrangement.Start
            ) {
                MessageBubble(msg.text, isUser = msg.sender == Sender.User)
            }
        }
    }
}

@Composable
private fun MessageBubble(text: String, isUser: Boolean) {
    val bg = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = if (isUser)
        RoundedCornerShape(16.dp, 12.dp, 4.dp, 16.dp)
    else
        RoundedCornerShape(12.dp, 16.dp, 16.dp, 4.dp)
    Surface(color = bg, shape = shape, tonalElevation = 1.dp, shadowElevation = 1.dp) {
        Text(text, color = fg, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

@Composable
private fun InputSection(
    input: TextFieldValue,
    onInputChange: (TextFieldValue) -> Unit,
    scope: CoroutineScope,
    context: Context,
    messages: MutableList<ChatMessage>,
    modifier: Modifier = Modifier,
    onProcessingChange: (Boolean) -> Unit = {}
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("Type alarm/reminder command") }
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = {
            val raw = input.text.trim()
            if (raw.isEmpty()) return@IconButton
            messages.add(0, ChatMessage(raw, Sender.User))
            onInputChange(TextFieldValue(""))

            scope.launch {
                onProcessingChange(true)
                try {
                    val alarms = parseUserInputToAlarms(raw)
                    if (alarms.isNotEmpty()) {
                        AlarmHelper.scheduleInAppAlarms(context, alarms)
                        messages.add(0, ChatMessage("Scheduled ${alarms.size} alarm(s).", Sender.App))
                    } else {
                        messages.add(0, ChatMessage("Could not parse any alarms from input.", Sender.App))
                    }
                } catch (e: Exception) {
                    messages.add(0, ChatMessage("Error: ${e.message}", Sender.App))
                } finally {
                    onProcessingChange(false)
                }
            }
        }) {
            Icon(Icons.Filled.Send, contentDescription = "Send")
        }
    }
}

// Parser that emits Alarm rows matching Alarm.kt (message, triggerTimeMillis, isRecurring, recurringDays)
// Builds Alarm rows that match Alarm.kt exactly
fun parseUserInputToAlarms(input: String): List<Alarm> {
    val lower = input.lowercase(java.util.Locale.getDefault())
    val now = java.util.Calendar.getInstance()

    // time: 12h or 24h
    var hour: Int? = null
    var minute: Int? = null
    val re12 = Regex("""\b(1[0-2]|0?[1-9])[:.]?([0-5][0-9])?\s*(am|pm)\b""", RegexOption.IGNORE_CASE)
    val re24 = Regex("""\b([01]?[0-9]|2[0-3]):([0-5][0-9])\b""", RegexOption.IGNORE_CASE)
    when {
        re12.containsMatchIn(lower) -> {
            val m = re12.find(lower)!!
            var h = m.groupValues[1].toInt()
            val mm = m.groupValues[2].ifBlank { "0" }.toInt()
            val ap = m.groupValues[3].lowercase()
            if (ap == "pm" && h != 12) h += 12
            if (ap == "am" && h == 12) h = 0
            hour = h; minute = mm
        }
        re24.containsMatchIn(lower) -> {
            val m = re24.find(lower)!!
            hour = m.groupValues[1].toInt()
            minute = m.groupValues[2].toInt()
        }
    }
    val h = hour ?: 9
    val m = minute ?: 0

    // weekdays -> recurringDays
    val daysMap = mapOf(
        "sunday" to java.util.Calendar.SUNDAY, "monday" to java.util.Calendar.MONDAY,
        "tuesday" to java.util.Calendar.TUESDAY, "wednesday" to java.util.Calendar.WEDNESDAY,
        "thursday" to java.util.Calendar.THURSDAY, "friday" to java.util.Calendar.FRIDAY,
        "saturday" to java.util.Calendar.SATURDAY
    )
    val recurringDays = daysMap.filter { lower.contains(it.key) }.values.toList().ifEmpty { null }

    // explicit dd-mm-yyyy or dd/mm/yyyy
    val reDate = Regex("""\b([0-3]?\d)[-/]([01]?\d)[-/](20\d\d)\b""")
    val explicitEpoch = reDate.find(lower)?.let { d ->
        val day = d.groupValues[1].toInt()
        val mon = d.groupValues[2].toInt() - 1
        val yr = d.groupValues[3].toInt()
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, yr); set(java.util.Calendar.MONTH, mon); set(java.util.Calendar.DAY_OF_MONTH, day)
            set(java.util.Calendar.HOUR_OF_DAY, h); set(java.util.Calendar.MINUTE, m)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // target timeMillis
    val timeMillis = explicitEpoch ?: run {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, h); set(java.util.Calendar.MINUTE, m)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(java.util.Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
    }

    // title and important flag
    val isImportant = lower.contains("important")
    val label = when {
        lower.contains("lunch") -> "Eat lunch"
        lower.contains("drink tea") -> "Drink tea"
        lower.contains("doctor") -> "Doctor appointment"
        lower.contains("meeting") && isImportant -> "Important meeting"
        lower.contains("meeting") -> "Meeting"
        lower.contains("alarm on monday") -> "Alarm"
        else -> "Alarm"
    }

    // weekly vs one-time
    return if (!recurringDays.isNullOrEmpty()) {
        // For recurring, timeMillis can be the next occurrence; scheduler will use recurringDays
        val first = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, h); set(java.util.Calendar.MINUTE, m)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            var add = 0
            while (add <= 7) {
                val c = (clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, add) }
                if (recurringDays.contains(c.get(java.util.Calendar.DAY_OF_WEEK)) && c.timeInMillis > now.timeInMillis) {
                    timeInMillis = c.timeInMillis; break
                }
                add++
            }
        }.timeInMillis
        listOf(Alarm(label = label, timeMillis = first, important = isImportant, recurringDays = recurringDays))
    } else {
        listOf(Alarm(label = label, timeMillis = timeMillis, important = isImportant, recurringDays = null))
    }
}
