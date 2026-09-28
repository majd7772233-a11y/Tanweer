package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

class RealtimeChatManager(
    private val client: OkHttpClient = NetworkModule.okHttpClient
) {
    private var webSocket: WebSocket? = null
    private var currentGroupId: String? = null
    private var onMessageReceived: ((JSONObject) -> Unit)? = null

    private val _status = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect(groupId: String, onMessage: (JSONObject) -> Unit) {
        if (currentGroupId == groupId && _status.value == ConnectionStatus.CONNECTED) {
            return
        }
        disconnect()
        currentGroupId = groupId
        onMessageReceived = onMessage
        _status.value = ConnectionStatus.CONNECTING

        val wsUrl = "wss://tanweer.magd.workers.dev/ws/groups/$groupId"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _status.value = ConnectionStatus.CONNECTED
                Log.d("RealtimeChatManager", "Connected to group WS: $groupId")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    onMessageReceived?.invoke(json)
                } catch (e: Exception) {
                    Log.e("RealtimeChatManager", "Failed to parse message: $text", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _status.value = ConnectionStatus.DISCONNECTED
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _status.value = ConnectionStatus.DISCONNECTED
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _status.value = ConnectionStatus.DISCONNECTED
                Log.w("RealtimeChatManager", "WS failed for $groupId, will retry: ${t.message}")
                // Auto-reconnect after 5 seconds if still targeting this group
                scope.launch {
                    delay(5000)
                    if (currentGroupId == groupId && _status.value == ConnectionStatus.DISCONNECTED) {
                        connect(groupId, onMessage)
                    }
                }
            }
        })
    }

    fun sendMessage(json: JSONObject): Boolean {
        val ws = webSocket
        if (ws != null && _status.value == ConnectionStatus.CONNECTED) {
            return ws.send(json.toString())
        }
        return false
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "User left")
        } catch (_: Exception) {}
        webSocket = null
        currentGroupId = null
        _status.value = ConnectionStatus.DISCONNECTED
    }
}
