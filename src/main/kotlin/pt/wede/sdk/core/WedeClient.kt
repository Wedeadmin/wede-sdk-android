package pt.wede.sdk.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pt.wede.sdk.cache.WedeCache
import pt.wede.sdk.cache.WedeStorage
import pt.wede.sdk.models.*
import pt.wede.sdk.offline.WedeOfflineDispatch
import pt.wede.sdk.sync.WedeDeviceId
import java.net.HttpURLConnection
import java.net.URL

private val json = Json { ignoreUnknownKeys = true }

data class WedeClientOptions(
    val apiKey: String,
    val baseUrl: String = "https://api.wede.pt",
    val timeoutMs: Int = 10_000,
    val storage: WedeStorage? = null
)

class WedeClient(private val options: WedeClientOptions) {
    val offline: WedeOfflineDispatch? = options.storage?.let { WedeOfflineDispatch(it) }
    val cache: WedeCache? = options.storage?.let { WedeCache(it) }
    private val deviceId: WedeDeviceId? = options.storage?.let { WedeDeviceId(it) }

    private suspend fun request(method: String, path: String, body: Any? = null): String =
        withContext(Dispatchers.IO) {
            val url = URL(options.baseUrl + path)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.setRequestProperty("x-wede-api-key", options.apiKey)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = options.timeoutMs
            conn.readTimeout = options.timeoutMs
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.bufferedWriter().use { it.write(Json.encodeToString(body as Any)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream.bufferedReader().use { it.readText() }
            if (code == 401) throw WedeAuthException("Unauthorized")
            if (code !in 200..299) throw WedeApiException("API error $code", code)
            response
        }

    suspend fun registerDevice(platform: String = "android", appVersion: String? = null): String {
        val id = deviceId?.getOrCreate() ?: throw IllegalStateException("Storage required")
        val body = buildMap<String, Any> {
            put("device_id", id); put("platform", platform)
            appVersion?.let { put("app_version", it) }
        }
        return request("POST", "/v1/devices/register", body)
    }

    suspend fun syncDeviceQueue(): SyncResult? {
        val dispatch = offline ?: return null
        val id = deviceId?.get() ?: return null
        val pending = dispatch.getPendingQueue()
        val body = mapOf(
            "device_id" to id,
            "last_received_seq" to 0,
            "dispatches" to pending.map { d ->
                mapOf(
                    "sequence_number" to d.sequenceNumber,
                    "action_id" to d.actionId,
                    "event_lat" to d.event.lat,
                    "event_lng" to d.event.lng,
                    "vertical" to d.event.vertical,
                    "priority" to d.event.priority,
                    "created_offline_at" to d.queuedAt
                )
            }
        )
        val raw = request("POST", "/v1/devices/sync", body)
        val result = json.decodeFromString<SyncResult>(raw)
        result.accepted.forEach { seq ->
            pending.firstOrNull { it.sequenceNumber == seq }?.let { dispatch.markSynced(it.id) }
        }
        dispatch.clearSynced()
        return result
    }

    suspend fun refreshCache() {
        // Refresh teams and catalog from server when online
        cache ?: return
    }
}

class WedeAuthException(message: String) : Exception(message)
class WedeApiException(message: String, val statusCode: Int) : Exception(message)
