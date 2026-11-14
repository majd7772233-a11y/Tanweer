package com.magd.tanweer.data.remote

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

class RealtimeChatManager(
    private val client: OkHttpClient = NetworkModule.okHttpClient
) {
    private val lock = Any()
    private var webSocket: WebSocket? = null
    private var currentGroupId: String? = null
    private var onMessageReceived: ((JSONObject) -> Unit)? = null
    private var sessionGeneration = 0L
    private var socketGeneration = 0L
    private var reconnectGeneration = 0L
    private var reconnectAttempt = 0
    private var reconnectJob: Job? = null

    private val _status = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect(groupId: String, onMessage: (JSONObject) -> Unit) {
        if (groupId.isBlank()) {
            disconnect()
            return
        }

        val oldSocket: WebSocket?
        val generation: Long
        synchronized(lock) {
            if (currentGroupId == groupId &&
                (_status.value == ConnectionStatus.CONNECTING || _status.value == ConnectionStatus.CONNECTED)
            ) {
                onMessageReceived = onMessage
                return
            }

            sessionGeneration += 1
            generation = sessionGeneration
            reconnectGeneration += 1
            oldSocket = webSocket
            webSocket = null
            reconnectJob?.cancel()
            reconnectJob = null
            currentGroupId = groupId
            onMessageReceived = onMessage
            reconnectAttempt = 0
            _status.value = ConnectionStatus.CONNECTING
        }
        oldSocket?.cancel()
        openSocket(groupId, generation)
    }

    private fun openSocket(groupId: String, generation: Long) {
        val attemptId = synchronized(lock) {
            if (currentGroupId != groupId || sessionGeneration != generation) return
            socketGeneration += 1
            socketGeneration
        }

        val request = try {
            Request.Builder()
                .url(NetworkModule.resolveWebSocketUrl("/ws/groups/$groupId"))
                .build()
        } catch (error: Exception) {
            Log.w("RealtimeChatManager", "Invalid WebSocket URL for group $groupId", error)
            scheduleReconnect(groupId, generation)
            return
        }

        val listener = object : WebSocketListener() {
            private fun isCurrent(): Boolean = synchronized(lock) {
                currentGroupId == groupId &&
                    sessionGeneration == generation &&
                    socketGeneration == attemptId
            }

            override fun onOpen(webSocket: WebSocket, response: Response) {
                synchronized(lock) {
                    if (currentGroupId != groupId ||
                        sessionGeneration != generation ||
                        socketGeneration != attemptId
                    ) {
                        webSocket.cancel()
                        return
                    }
                    this@RealtimeChatManager.webSocket = webSocket
                    reconnectAttempt = 0
                    _status.value = ConnectionStatus.CONNECTED
                }
                Log.d("RealtimeChatManager", "Connected to group WS: $groupId")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isCurrent()) return
                try {
                    val json = JSONObject(text)
                    val callback = synchronized(lock) {
                        if (currentGroupId == groupId &&
                            sessionGeneration == generation &&
                            socketGeneration == attemptId
                        ) onMessageReceived else null
                    }
                    callback?.invoke(json)
                } catch (error: Exception) {
                    Log.e("RealtimeChatManager", "Failed to parse incoming chat frame", error)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (!isCurrent()) return
                synchronized(lock) {
                    if (this@RealtimeChatManager.webSocket === webSocket) {
                        _status.value = ConnectionStatus.DISCONNECTED
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!isCurrent()) return
                synchronized(lock) {
                    if (this@RealtimeChatManager.webSocket === webSocket) {
                        this@RealtimeChatManager.webSocket = null
                        _status.value = ConnectionStatus.DISCONNECTED
                    }
                }
                if (RealtimeReconnectPolicy.shouldReconnectAfterClose(code)) {
                    scheduleReconnect(groupId, generation)
                } else {
                    Log.i("RealtimeChatManager", "WebSocket closed permanently for this session (code=$code)")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!isCurrent()) return
                synchronized(lock) {
                    if (this@RealtimeChatManager.webSocket === webSocket) {
                        this@RealtimeChatManager.webSocket = null
                    }
                    _status.value = ConnectionStatus.DISCONNECTED
                }
                val responseCode = response?.code
                if (RealtimeReconnectPolicy.shouldReconnectAfterFailure(responseCode)) {
                    Log.w("RealtimeChatManager", "WS failed for $groupId; reconnect scheduled (HTTP=$responseCode)", t)
                    scheduleReconnect(groupId, generation)
                } else {
                    Log.i("RealtimeChatManager", "WebSocket authorization/protocol failure; automatic retry stopped (HTTP=$responseCode)")
                }
            }
        }

        val created = try {
            client.newWebSocket(request, listener)
        } catch (error: Exception) {
            if (isSessionCurrent(groupId, generation, attemptId)) {
                synchronized(lock) { _status.value = ConnectionStatus.DISCONNECTED }
                scheduleReconnect(groupId, generation)
            }
            Log.w("RealtimeChatManager", "Could not create WebSocket for $groupId", error)
            return
        }

        synchronized(lock) {
            if (currentGroupId == groupId &&
                sessionGeneration == generation &&
                socketGeneration == attemptId &&
                _status.value != ConnectionStatus.DISCONNECTED
            ) {
                webSocket = created
            } else {
                created.cancel()
            }
        }
    }

    private fun isSessionCurrent(groupId: String, generation: Long, attemptId: Long): Boolean =
        synchronized(lock) {
            currentGroupId == groupId &&
                sessionGeneration == generation &&
                socketGeneration == attemptId
        }

    private fun scheduleReconnect(groupId: String, generation: Long) {
        synchronized(lock) {
            if (currentGroupId != groupId ||
                sessionGeneration != generation ||
                reconnectJob?.isActive == true
            ) return

            reconnectGeneration += 1
            val reconnectToken = reconnectGeneration
            val delayMs = RealtimeReconnectPolicy.delayMillis(reconnectAttempt)
            reconnectAttempt += 1
            _status.value = ConnectionStatus.DISCONNECTED
            reconnectJob = scope.launch {
                delay(delayMs)
                val shouldReconnect = synchronized(lock) {
                    if (currentGroupId != groupId ||
                        sessionGeneration != generation ||
                        reconnectGeneration != reconnectToken
                    ) {
                        false
                    } else {
                        reconnectJob = null
                        _status.value = ConnectionStatus.CONNECTING
                        true
                    }
                }
                if (shouldReconnect) openSocket(groupId, generation)
            }
        }
    }

    fun sendMessage(json: JSONObject): Boolean {
        val socket = synchronized(lock) {
            webSocket.takeIf { _status.value == ConnectionStatus.CONNECTED }
        }
        return socket?.send(json.toString()) == true
    }

    fun disconnect() {
        val oldSocket: WebSocket?
        synchronized(lock) {
            sessionGeneration += 1
            socketGeneration += 1
            reconnectGeneration += 1
            reconnectJob?.cancel()
            reconnectJob = null
            oldSocket = webSocket
            webSocket = null
            currentGroupId = null
            onMessageReceived = null
            reconnectAttempt = 0
            _status.value = ConnectionStatus.DISCONNECTED
        }
        oldSocket?.close(1000, "User left")
    }

    fun shutdown() {
        disconnect()
        scope.cancel()
    }
}
