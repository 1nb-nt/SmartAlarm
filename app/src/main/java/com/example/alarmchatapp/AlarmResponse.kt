package com.example.alarmchatapp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// -------------------- Models --------------------
@Serializable
data class NotificationItem(
    val type: String? = null,
    val datetime: String? = null
)

object NotificationListSerializer :
    JsonTransformingSerializer<List<NotificationItem>>(ListSerializer(NotificationItem.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        return if (element is JsonArray && element.isNotEmpty() && element.all { it is JsonPrimitive }) {
            JsonArray(
                element.map {
                    JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("on_time"),
                            "datetime" to it
                        )
                    )
                }
            )
        } else {
            element
        }
    }
}

@Serializable
data class AlarmResponse(
    @SerialName("p_type") val pType: String? = null,
    @SerialName("alarm type") val alarmType: String? = null,
    @SerialName("alarm_type") val alarmTypeAlt: String? = null,
    val title: String? = null,
    val datetime: String? = null,
    val time: String? = null,
    val location: String? = null,
    val distance: String? = null,
    val timezone: String? = null,
    val recurrence: String? = null,
    @SerialName("ex_days") val exDays: List<String>? = null,
    @Serializable(with = NotificationListSerializer::class)
    val notification: List<NotificationItem>? = null
)

// -------------------- Utilities --------------------
private val jsonLoose = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

fun buildUserInput(base: String): String {
    val today = LocalDate.now()
    val formattedDate = today.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
    val tz = ZoneId.systemDefault().id
    return "$base Today's date is $formattedDate and timezone is $tz"
}

fun buildRequestBody(userInput: String): String = """
{
  "objective": "Alarm Generator",
  "objective_key": "alarm_generator",
  "model": "openai",
  "inputs": {
    "user_input": "$userInput",
    "ctype": "text"
  }
}
""".trimIndent()

fun callApi(client: OkHttpClient, body: String): String {
    val request = Request.Builder()
        .url("https://demoapi.workofwisdom.in/generate")
        .post(body.toRequestBody("application/json".toMediaType()))
        .build()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("API Error: ${response.code}")
        return response.body?.string().orEmpty()
    }
}

/**
 * Extract the first balanced JSON object from the "response" string of the API result.
 * Removes common code fences and then finds the first balanced {...}.
 */
fun extractInnerJsonFromResponse(raw: String): String? {
    val root = runCatching { jsonLoose.parseToJsonElement(raw) }.getOrNull() as? JsonObject ?: return null
    val respStr = root["response"]?.let { (it as? JsonPrimitive)?.content } ?: return null

    val text = respStr
        .replace("```
            .replace("```", "")
            .trim()

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

fun parseAlarm(jsonPart: String): AlarmResponse =
    jsonLoose.decodeFromString(AlarmResponse.serializer(), jsonPart)

fun printAlarm(alarm: AlarmResponse) {
    println("\n=== Parsed Alarm ===")
    println("Title: ${alarm.title}")
    println("Type: ${alarm.alarmType ?: alarm.alarmTypeAlt}")
    println("Datetime: ${alarm.datetime}")
    println("Time: ${alarm.time}")
    println("Timezone: ${alarm.timezone}")
    println("Recurrence: ${alarm.recurrence}")
    if (!alarm.exDays.isNullOrEmpty()) {
        println("Exceptions: ${alarm.exDays.joinToString()}")
    }
    alarm.notification?.forEach { item ->
        println("Notification → ${item.type ?: "unknown"}: ${item.datetime}")
        val dt = item.datetime
        if (dt != null) {
            runCatching { convertDateTime(dt) }
                .onSuccess { epoch -> println("Date in long: $epoch") }
                .onFailure { println("Failed to parse datetime: $dt") }
        }
    }
}

fun convertDateTime(dt: String): Long =
    OffsetDateTime.parse(dt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        .toInstant()
        .toEpochMilli()

// -------------------- Entry points --------------------
fun runWithUserInput(baseInput: String) {
    val userInput = buildUserInput(baseInput)
    if (userInput.isBlank()) {
        println("No reminder provided.")
        return
    }

    val client = OkHttpClient()
    val jsonBody = buildRequestBody(userInput)

    try {
        val raw = callApi(client, jsonBody)
        val inner = extractInnerJsonFromResponse(raw)
        if (inner == null) {
            println("No JSON block in response.")
            return
        }
        val alarm = parseAlarm(inner)
        printAlarm(alarm)
    } catch (e: Exception) {
        println("Failed to process API response")
        e.printStackTrace()
    }
}

fun main(args: Array<String>) {
    val baseInput = if (args.isNotEmpty()) args.joinToString(" ")
    else "My wife's birthday is on 16th Nov and it is very important. Remind me one week before."
    runWithUserInput(baseInput)
}
