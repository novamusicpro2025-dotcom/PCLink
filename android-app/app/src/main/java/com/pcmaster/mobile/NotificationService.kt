package com.pcmaster.mobile

import android.app.Notification
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class NotificationService : NotificationListenerService() {

    private val networkExecutor = Executors.newCachedThreadPool()
    private var replyServerThread: Thread? = null
    private val activeNotifications = ConcurrentHashMap<String, StatusBarNotification>()
    private val lastSentHashes = ConcurrentHashMap<String, Int>()
    private val isShuttingDown = AtomicBoolean(false)
    private val httpServerRef = AtomicReference<ServerSocket?>(null)

    private val replyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.pcmaster.mobile.REPLY" -> {
                    val id = intent.getStringExtra("id")
                    val message = intent.getStringExtra("message")
                    if (id != null && message != null) {
                        sendReply(id, message)
                    }
                }
                "com.pcmaster.mobile.RING_PHONE" -> {
                    Log.d("NotificationService", "Received RING_PHONE broadcast from PC/system")
                    ringPhone()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction("com.pcmaster.mobile.REPLY")
            addAction("com.pcmaster.mobile.RING_PHONE")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(replyReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(replyReceiver, filter)
        }
        startReplyServer()
        startPendingRepliesPoller()
        Log.d("NotificationService", "NotificationService started, listening on port 8092 and polling PC queue")
    }

    override fun onDestroy() {
        super.onDestroy()
        isShuttingDown.set(true)
        
        // Close the server socket to unblock accept()
        val serverSocket = httpServerRef.getAndSet(null)
        try { serverSocket?.close() } catch (e: Exception) { Log.e("NotificationService", "Error closing server socket", e) }
        
        try { unregisterReceiver(replyReceiver) } catch (e: Exception) { Log.e("NotificationService", "Error unregistering receiver", e) }
        
        replyServerThread?.interrupt()
        replyServerThread = null

        // Shutdown executor
        networkExecutor.shutdown()
        try { 
            if (!networkExecutor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                networkExecutor.shutdownNow()
            }
        } catch (e: Exception) { Log.e("NotificationService", "Error shutting down executor", e) }
    }

    private fun startReplyServer() {
        replyServerThread = Thread({
            var serverSocket: ServerSocket? = null
            try {
                serverSocket = ServerSocket(8092)
                serverSocket.soTimeout = 1000 // Add timeout to allow checking isShuttingDown
                httpServerRef.set(serverSocket)
                while (!isShuttingDown.get() && !serverSocket.isClosed) {
                    val socket: Socket?
                    try {
                        socket = serverSocket.accept()
                    } catch (e: Exception) {
                        if (isShuttingDown.get()) break
                        continue
                    }
                    socket?.let {
                        try {
                            val reader = it.getInputStream().bufferedReader()
                            val requestLine = reader.readLine()
                            if (requestLine != null && requestLine.startsWith("POST /")) {
                                var contentLength = 0
                                var headerLine: String?
                                while (true) {
                                    headerLine = reader.readLine()
                                    if (headerLine == null || headerLine.isEmpty()) break
                                    if (headerLine.startsWith("Content-Length:", ignoreCase = true)) {
                                        contentLength = headerLine.substring(15).trim().toIntOrNull() ?: 0
                                    }
                                }

                                val bodyStr = if (contentLength > 0) {
                                    val body = CharArray(contentLength)
                                    var totalRead = 0
                                    while (totalRead < contentLength) {
                                        val r = reader.read(body, totalRead, contentLength - totalRead)
                                        if (r == -1) break
                                        totalRead += r
                                    }
                                    String(body, 0, totalRead)
                                } else {
                                    ""
                                }

                                // Parse form-urlencoded: id=...&message=...
                                val params = bodyStr.split("&").associate { param ->
                                    val parts = param.split("=", limit = 2)
                                    if (parts.size == 2) {
                                        java.net.URLDecoder.decode(parts[0], "UTF-8") to java.net.URLDecoder.decode(parts[1], "UTF-8")
                                    } else {
                                        "" to ""
                                    }
                                }

                                val requestPath = requestLine.substringAfter(" ").substringBefore(" ")
                                when (requestPath) {
                                    "/reply" -> {
                                        val id = params["id"]
                                        val message = params["message"]
                                        if (!id.isNullOrEmpty() && !message.isNullOrEmpty()) {
                                            Log.d("NotificationService", "Received reply command for $id: $message")
                                            sendReply(id, message)
                                        }
                                    }
                                    "/clipboard" -> {
                                        val text = params["text"]?.trim().orEmpty()
                                        if (text.isNotEmpty()) {
                                            Handler(Looper.getMainLooper()).post {
                                                try {
                                                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(ClipData.newPlainText("PCLink", text))
                                                    Log.d("NotificationService", "PC clipboard text received")
                                                } catch (e: Exception) {
                                                    Log.e("NotificationService", "Clipboard set error", e)
                                                }
                                            }
                                        }
                                    }
                                    "/ring" -> ringPhone()
                                }

                                val out = it.getOutputStream().bufferedWriter()
                                out.write("HTTP/1.1 200 OK\r\n")
                                out.write("Content-Length: 0\r\n")
                                out.write("Access-Control-Allow-Origin: *\r\n")
                                out.write("\r\n")
                                out.flush()
                            } else {
                                // Not a reply request, ignore
                            }
                        } catch (ex: Exception) {
                            Log.e("NotificationService", "Socket handling error", ex)
                        } finally {
                            try { it.close() } catch (e: Exception) { Log.e("NotificationService", "Error closing socket", e) }
                        }
                    }
                }
            } catch (e: Exception) {
                if (!isShuttingDown.get()) {
                    Log.e("NotificationService", "Reply server error", e)
                }
            } finally {
                try { serverSocket?.close() } catch (e: Exception) { Log.e("NotificationService", "Error closing server socket in finally", e) }
                httpServerRef.set(null)
            }
        }, "ReplyServerThread").apply { isDaemon = true; start() }
    }

    private fun ringPhone() {
        Handler(Looper.getMainLooper()).post {
            try {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)

                val vibration = getSystemService(Vibrator::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibration?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 1000, 500, 1000, 500, 1000, 500, 1000), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibration?.vibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000, 500, 1000), -1)
                }
                
                var uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                val player = MediaPlayer()
                player.setDataSource(this, uri)
                player.setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                player.isLooping = true
                player.prepare()
                player.start()
                
                Handler(Looper.getMainLooper()).postDelayed({
                    try { 
                        player.stop()
                        player.release()
                        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
                    } catch (e: Exception) {}
                }, 8000)
                Log.d("NotificationService", "Find Phone alert activated")
            } catch (exception: Exception) {
                Log.e("NotificationService", "Find Phone alert failed", exception)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("NotificationService", "NotificationListenerService connected to Android system!")
        try {
            val current = getActiveNotifications()
            current?.forEach { sbn ->
                if (sbn != null && sbn.packageName != packageName) {
                    activeNotifications[sbn.key] = sbn
                    forwardNotification(sbn)
                }
            }
            Log.d("NotificationService", "Forwarded ${activeNotifications.size} active notifications to PC")
        } catch (e: Exception) {
            Log.e("NotificationService", "Error reading initial active notifications", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let {
            if (it.packageName == packageName) return // Skip self notifications
            val key = it.key
            activeNotifications[key] = it
            forwardNotification(it)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let {
            activeNotifications.remove(it.key)
            lastSentHashes.remove(it.key)
        }
    }

    private fun forwardNotification(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = firstNonBlank(
            extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString(),
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        )
        val text = firstNonBlank(
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString(),
            getLatestMessageText(extras)
        )
        val packageName = sbn.packageName
        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }

        val canReply = getReplyAction(sbn.notification) != null

        val json = JSONObject().apply {
            put("id", sbn.key)
            put("appName", appName)
            put("package", packageName)
            put("title", title.ifEmpty { appName })
            put("content", text.ifEmpty { "New notification" })
            put("canReply", canReply)
        }
        
        val contentHash = json.toString().hashCode()
        if (lastSentHashes[sbn.key] == contentHash) {
            return // Skip sending duplicate notification content (e.g. progress bar updates)
        }
        lastSentHashes[sbn.key] = contentHash

        sendToPc(json)
    }

    private fun firstNonBlank(vararg values: String?): String =
        values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

    private fun getLatestMessageText(extras: Bundle): String {
        val messages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES, Bundle::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        } ?: return ""
        for (index in messages.indices.reversed()) {
            val message = messages[index] as? Bundle ?: continue
            val text = message.getCharSequence("text")?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) return text
        }
        return ""
    }

    private fun sendToPc(json: JSONObject) {
        val candidateEndpoints = mutableListOf<String>()
        if (PcApiClient.baseUrl.isNotEmpty()) {
            candidateEndpoints.add("${PcApiClient.baseUrl.trimEnd('/')}/remote/notif")
        }
        val prefs = getSharedPreferences("pc_master", Context.MODE_PRIVATE)
        val pcIp = prefs.getString("pc_ip", "") ?: ""
        val pcPort = prefs.getString("pc_port", "8099") ?: "8099"
        if (pcIp.isNotEmpty()) {
            val base = if (pcIp.startsWith("http")) pcIp else "http://$pcIp:$pcPort"
            candidateEndpoints.add("${base.trimEnd('/')}/remote/notif")
        }
        val urlStr = prefs.getString("url", "") ?: ""
        if (urlStr.isNotEmpty()) {
            val base = if (urlStr.startsWith("http")) urlStr else "http://$urlStr"
            candidateEndpoints.add("${base.trimEnd('/')}/remote/notif")
        }
        // Always try USB / local reverse forward
        candidateEndpoints.add("http://127.0.0.1:8099/remote/notif")

        val endpointsToTry = candidateEndpoints.distinct()

        networkExecutor.execute {
            var delivered = false
            for (endpoint in endpointsToTry) {
                var conn: HttpURLConnection? = null
                try {
                    val url = URL(endpoint)
                    conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    if (PcApiClient.authToken.isNotEmpty()) {
                        conn.setRequestProperty("Authorization", "Bearer ${PcApiClient.authToken}")
                        conn.setRequestProperty("X-Auth-Token", PcApiClient.authToken)
                    }
                    conn.doOutput = true
                    conn.connectTimeout = 1500
                    conn.readTimeout = 1500

                    val body = json.toString()
                    conn.outputStream.use { os ->
                        os.write(body.toByteArray(StandardCharsets.UTF_8))
                    }

                    val code = conn.responseCode
                    if (code == 200) {
                        Log.d("NotificationService", "Notification DELIVERED to PC via $endpoint")
                        delivered = true
                        break
                    }
                } catch (e: Exception) {
                    Log.d("NotificationService", "Endpoint $endpoint unavailable: ${e.message}")
                } finally {
                    try { conn?.disconnect() } catch (_: Throwable) {}
                }
            }
            if (!delivered) {
                Log.w("NotificationService", "Notification could not be delivered to any PC endpoint (${endpointsToTry.size} tried)")
            }
        }
    }

    private fun startPendingRepliesPoller() {
        networkExecutor.execute {
            while (!isShuttingDown.get()) {
                try {
                    Thread.sleep(1500)
                    if (isShuttingDown.get()) break
                    pollPendingReplies()
                } catch (_: InterruptedException) {
                    break
                } catch (_: Exception) {}
            }
        }
    }

    private fun pollPendingReplies() {
        val candidateBases = mutableListOf<String>()
        if (PcApiClient.baseUrl.isNotEmpty()) {
            candidateBases.add(PcApiClient.baseUrl.trimEnd('/'))
        }
        val prefs = getSharedPreferences("pc_master", Context.MODE_PRIVATE)
        val pcIp = prefs.getString("pc_ip", "") ?: ""
        val pcPort = prefs.getString("pc_port", "8099") ?: "8099"
        if (pcIp.isNotEmpty()) {
            val base = if (pcIp.startsWith("http")) pcIp else "http://$pcIp:$pcPort"
            candidateBases.add(base.trimEnd('/'))
        }
        candidateBases.add("http://127.0.0.1:8099")

        for (base in candidateBases.distinct()) {
            try {
                val url = URL("$base/notif/pending-replies")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 1000
                conn.readTimeout = 1000
                if (PcApiClient.authToken.isNotEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer ${PcApiClient.authToken}")
                    conn.setRequestProperty("X-Auth-Token", PcApiClient.authToken)
                }
                if (conn.responseCode == 200) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    if (resp.isNotEmpty() && resp != "[]") {
                        val arr = org.json.JSONArray(resp)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val id = obj.optString("id", "")
                            val msg = obj.optString("message", "")
                            if (id.isNotEmpty() && msg.isNotEmpty()) {
                                Log.d("NotificationService", "Executing queued reply from PC for $id: $msg")
                                sendReply(id, msg)
                            }
                        }
                    }
                    break
                }
                conn.disconnect()
            } catch (_: Exception) {}
        }
    }

    private fun getReplyAction(notification: Notification): Notification.Action? {
        val actions = notification.actions ?: return null
        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (remoteInput in remoteInputs) {
                if (remoteInput.allowFreeFormInput && !remoteInput.resultKey.isNullOrEmpty()) {
                    return action
                }
            }
        }
        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (remoteInput in remoteInputs) {
                if (!remoteInput.resultKey.isNullOrEmpty()) {
                    return action
                }
            }
        }
        return null
    }

    private fun sendReply(id: String, message: String) {
        var sbn = activeNotifications[id]
        if (sbn == null) {
            try {
                val current = activeNotifications.values.find { it.key == id || it.id.toString() == id }
                if (current != null) sbn = current
            } catch (_: Throwable) {}
        }
        if (sbn == null) {
            try {
                val allActive = getActiveNotifications()
                sbn = allActive?.find { it.key == id || it.id.toString() == id }
            } catch (_: Throwable) {}
        }

        if (sbn == null) {
            Log.e("NotificationService", "Could not find notification with id: $id")
            return
        }

        val action = getReplyAction(sbn.notification)
        if (action == null) {
            Log.e("NotificationService", "No reply action found on notification: $id")
            return
        }

        val remoteInput = action.remoteInputs?.find { it.allowFreeFormInput }
            ?: action.remoteInputs?.firstOrNull() ?: return

        val bundle = Bundle()
        bundle.putCharSequence(remoteInput.resultKey, message)

        val intent = Intent()
        RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)

        try {
            action.actionIntent.send(this, 0, intent)
            Log.d("NotificationService", "Reply sent successfully for $id: $message")
        } catch (e: Exception) {
            Log.e("NotificationService", "Error sending reply", e)
        }
    }
}
