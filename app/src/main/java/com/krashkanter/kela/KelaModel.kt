package com.krashkanter.kela

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
fun JSONObject.array(key: String) = optJSONArray(key) ?: JSONArray()
fun JSONObject.obj(key: String) = optJSONObject(key) ?: JSONObject()
fun containerName(item: JSONObject): String = item.array("Names").optString(0).removePrefix("/").ifBlank { item.optString("Id").take(12) }
fun imageName(item: JSONObject): String = item.array("RepoTags").optString(0).takeUnless { it == "<none>:<none>" }.orEmpty().ifBlank { item.optString("Id").removePrefix("sha256:").take(12) }
fun bytes(value: Long): String {
    val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB")
    var amount = value.toDouble(); var unit = 0
    while (amount >= 1024 && unit < units.lastIndex) { amount /= 1024; unit++ }
    return if (unit == 0) "$value B" else "%.1f %s".format(amount, units[unit])
}

class Bridge(private val app: Application) {
    private val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(50, TimeUnit.SECONDS).callTimeout(55, TimeUnit.SECONDS).build()
    private val tokenFile get() = File(app.filesDir, "token")
    fun configured() = tokenFile.exists() && tokenFile.readText().trim().isNotEmpty()
    fun saveToken(token: String) { tokenFile.writeText(token.trim()) }
    suspend fun request(method: String, path: String, body: JSONObject? = null): String = withContext(Dispatchers.IO) {
        val token = if (tokenFile.exists()) tokenFile.readText().trim() else ""
        check(token.isNotEmpty()) { "Pair Kela with the local bridge" }
        val request = Request.Builder().url("http://127.0.0.1:9473/v1/$path")
            .header("Authorization", "Bearer $token")
            .method(method, if (method in listOf("POST", "PUT")) (body ?: JSONObject()).toString().toRequestBody("application/json".toMediaType()) else null)
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            check(response.isSuccessful) { runCatching { JSONObject(text).optString("error") }.getOrDefault("HTTP ${response.code}").ifBlank { "HTTP ${response.code}" } }
            text
        }
    }
}

data class Dashboard(
    val overview: JSONObject? = null,
    val containers: List<JSONObject> = emptyList(),
    val images: List<JSONObject> = emptyList(),
    val volumes: List<JSONObject> = emptyList(),
    val networks: List<JSONObject> = emptyList(),
    val refreshing: Boolean = false,
    val connected: Boolean = false,
    val busy: Set<String> = emptySet(),
    val pull: String? = null,
    val error: String? = null,
    val notice: String? = null,
)

class KelaModel(application: Application) : AndroidViewModel(application) {
    val bridge = Bridge(application)
    private val mutable = MutableStateFlow(Dashboard())
    val state = mutable.asStateFlow()
    private var refreshing = false
    private var pullJob: Job? = null
    private fun update(change: (Dashboard) -> Dashboard) { mutable.value = change(mutable.value) }
    fun clearMessage() = update { it.copy(error = null, notice = null) }
    fun message(message: String) = update { it.copy(notice = message) }
    fun refresh(manual: Boolean = false) {
        if (refreshing) return
        refreshing = true
        viewModelScope.launch {
            if (manual) update { it.copy(refreshing = true) }
            try {
                val overview = JSONObject(bridge.request("GET", "overview"))
                val containers = JSONArray(bridge.request("GET", "containers")).objects().sortedBy { containerName(it) }
                val images = JSONArray(bridge.request("GET", "images")).objects().sortedBy { imageName(it) }
                val volumes = JSONObject(bridge.request("GET", "volumes")).array("Volumes").objects()
                val networks = JSONArray(bridge.request("GET", "networks")).objects()
                update { it.copy(overview = overview, containers = containers, images = images, volumes = volumes, networks = networks, connected = true, refreshing = false) }
            } catch (error: Exception) {
                update { it.copy(connected = false, refreshing = false, error = if (manual || it.overview == null) error.message else it.error) }
            } finally { refreshing = false }
        }
    }
    fun action(key: String, method: String, path: String, body: JSONObject? = null, onSuccess: (String) -> Unit = {}) {
        if (key in mutable.value.busy) return
        update { it.copy(busy = it.busy + key) }
        viewModelScope.launch {
            try {
                val response = bridge.request(method, path, body)
                onSuccess(response)
                refresh()
            } catch (error: Exception) { update { it.copy(error = error.message ?: "Request failed") } }
            finally { update { it.copy(busy = it.busy - key) } }
        }
    }
    fun create(spec: JSONObject, done: () -> Unit) = action("create", "POST", "containers", spec) {
        val warning = JSONObject(it).optString("warning")
        if (warning.isNotBlank()) message(warning)
        done()
    }
    fun pull(image: String, done: () -> Unit) {
        if (pullJob?.isActive == true) return
        pullJob = viewModelScope.launch {
            update { it.copy(pull = "Pulling $image") }
            try {
                val id = JSONObject(bridge.request("POST", "images/pull", JSONObject().put("image", image))).getString("id")
                done()
                while (true) {
                    val job = JSONObject(bridge.request("GET", "jobs/$id"))
                    update { it.copy(pull = job.optString("message")) }
                    if (job.optString("state") != "running") {
                        if (job.optString("state") == "error") error(job.optString("message"))
                        message("Pulled $image")
                        refresh()
                        break
                    }
                    delay(1000)
                }
            } catch (error: Exception) { update { it.copy(error = error.message ?: "Pull failed") } }
            finally { update { it.copy(pull = null) } }
        }
    }
}