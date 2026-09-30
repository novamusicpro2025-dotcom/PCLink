package com.pcmaster.mobile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

data class ProcessItem(
    val name: String,
    val pid: Int,
    val memory: String
)

data class FileItem(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: String
)

data class PcHealthReport(
    val overallScore: Int,
    val healthGrade: String,
    val gradeColor: String,
    val summary: String,
    val cpuLoad: Int,
    val cpuTemp: Int,
    val cpuStatus: String,
    val ramUsedGb: Double,
    val ramTotalGb: Double,
    val ramPercent: Int,
    val ramStatus: String,
    val storageUsedGb: Double,
    val storageTotalGb: Double,
    val storagePercent: Int,
    val storageStatus: String,
    val netUpMb: Double,
    val netDownMb: Double,
    val pingMs: Long,
    val latencyStatus: String,
    val batteryPct: Int,
    val batteryStatus: String,
    val uptime: String,
    val hostname: String,
    val osInfo: String,
    val issues: List<String>
)

object PcApiClient {

    private const val TAG = "PcApiClient"
    private val executor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())

    var baseUrl: String = "http://192.168.1.100:8099"
    var authToken: String = ""
    var pcScreenWidth: Int = 1920
    var pcScreenHeight: Int = 1080

    fun formatBaseUrl(ip: String, port: String): String {
        val cleanIp = ip.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        val cleanPort = port.trim().ifEmpty { "8099" }
        return "http://$cleanIp:$cleanPort"
    }

    private fun openConnection(endpoint: String, method: String = "GET", timeoutMs: Int = 4000): HttpURLConnection {
        val urlStr = if (endpoint.startsWith("http")) endpoint else "$baseUrl$endpoint"
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.setRequestProperty("User-Agent", "PC-Master-Android-App/2.0")
        if (authToken.isNotEmpty()) {
            conn.setRequestProperty("X-Auth-Token", authToken)
            conn.setRequestProperty("Authorization", "Bearer $authToken")
        }
        return conn
    }

    /**
     * Fetches public /settings to auto-negotiate authentication token and port config.
     */
    fun fetchSettings(onResult: (token: String, requireAuth: Boolean, error: String?) -> Unit) {
        executor.execute {
            try {
                val conn = openConnection("/settings", timeoutMs = 3000)
                conn.connect()
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val token = json.optString("authToken", "")
                    val requireAuth = json.optBoolean("requireAuth", true)
                    if (token.isNotEmpty()) {
                        authToken = token
                    }
                    conn.disconnect()
                    mainHandler.post { onResult(token, requireAuth, null) }
                } else {
                    val err = "HTTP ${conn.responseCode}"
                    conn.disconnect()
                    mainHandler.post { onResult("", false, err) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchSettings error: ${e.message}")
                mainHandler.post { onResult("", false, e.message) }
            }
        }
    }

    fun ping(onResult: (success: Boolean, latencyMs: Long) -> Unit) {
        executor.execute {
            val start = System.currentTimeMillis()
            var success: Boolean
            try {
                val conn = openConnection("/ping", timeoutMs = 2500)
                conn.connect()
                success = conn.responseCode in 200..299
                conn.disconnect()
            } catch (e: Exception) {
                success = false
            }
            val latency = System.currentTimeMillis() - start
            mainHandler.post { onResult(success, latency) }
        }
    }

    fun getStats(onResult: (JSONObject?) -> Unit) {
        executor.execute {
            var json: JSONObject? = null
            try {
                val conn = openConnection("/stats", timeoutMs = 3000)
                conn.connect()
                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    json = JSONObject(response)
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "getStats error: ${e.message}")
            }
            mainHandler.post { onResult(json) }
        }
    }

    fun sendRemoteCommand(command: String, params: Map<String, String> = emptyMap(), onResult: ((Boolean) -> Unit)? = null) {
        executor.execute {
            var success = false
            var conn: HttpURLConnection? = null
            try {
                val query = if (params.isNotEmpty()) {
                    "?" + params.map { "${URLEncoder.encode(it.key, "UTF-8").replace("+", "%20")}=${URLEncoder.encode(it.value, "UTF-8").replace("+", "%20")}" }.joinToString("&")
                } else ""
                conn = openConnection("/remote/$command$query", method = "POST", timeoutMs = 3500)
                conn.doOutput = true
                conn.outputStream.use { /* ensure output stream closed cleanly for POST */ }
                success = conn.responseCode in 200..299
            } catch (e: Exception) {
                Log.e(TAG, "sendRemoteCommand $command error: ${e.message}")
            } finally {
                try { conn?.disconnect() } catch (_: Throwable) {}
            }
            mainHandler.post { onResult?.invoke(success) }
        }
    }

    fun lockPc(onResult: ((Boolean) -> Unit)? = null) {
        sendRemoteCommand("lock", onResult = onResult)
    }

    fun unlockPc(pin: String = "", onResult: ((Boolean) -> Unit)? = null) {
        val params = if (pin.isNotEmpty()) mapOf("pin" to pin) else emptyMap()
        sendRemoteCommand("unlock", params = params) { success ->
            // Dual-layer wake: wake display with micro mouse movement, space to clear curtain, then PIN
            sendMouseMove(1f, 1f)
            sendKeyPress("space")
            if (pin.isNotEmpty()) {
                sendText(pin)
                sendKeyPress("enter")
            }
            onResult?.invoke(success)
        }
    }

    fun setAntiIdle(enable: Boolean, onResult: ((Boolean) -> Unit)? = null) {
        val params = mapOf("state" to if (enable) "on" else "off")
        sendRemoteCommand("antiidle", params = params) { success ->
            // Dual-layer compatibility: trigger caffeine state
            sendRemoteCommand(if (enable) "caffeineon" else "caffeineoff")
            onResult?.invoke(success)
        }
    }

    fun setVolume(level: Int, onResult: ((Boolean) -> Unit)? = null) {
        executor.execute {
            var success = false
            try {
                val clamped = level.coerceIn(0, 100)
                val conn = openConnection("/remote/volume/$clamped", timeoutMs = 2000)
                conn.connect()
                success = conn.responseCode in 200..299
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "setVolume error: ${e.message}")
            }
            mainHandler.post { onResult?.invoke(success) }
        }
    }

    fun setBrightness(level: Int, onResult: ((Boolean) -> Unit)? = null) {
        executor.execute {
            var success = false
            try {
                val clamped = level.coerceIn(0, 100)
                val conn = openConnection("/brightness?level=$clamped", timeoutMs = 2000)
                conn.connect()
                success = conn.responseCode in 200..299
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "setBrightness error: ${e.message}")
            }
            mainHandler.post { onResult?.invoke(success) }
        }
    }

    fun sendMouseMove(dx: Float, dy: Float) {
        executor.execute {
            try {
                val conn = openConnection("/remote/mouse/move?x=${dx.toInt()}&y=${dy.toInt()}", timeoutMs = 1200)
                conn.connect()
                conn.responseCode
                conn.disconnect()
            } catch (_: Exception) {
                // Ignore transient motion error
            }
        }
    }

    fun sendMouseClick(btn: String = "left") {
        executor.execute {
            try {
                val endpoint = when (btn.lowercase()) {
                    "right" -> "/remote/mouse/rightclick"
                    "double" -> "/remote/mouse/doubleclick"
                    else -> "/remote/mouse/leftclick"
                }
                val conn = openConnection(endpoint, timeoutMs = 2000)
                conn.connect()
                conn.responseCode
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "sendMouseClick error: ${e.message}")
            }
        }
    }

    fun sendMouseScroll(deltaY: Float) {
        executor.execute {
            try {
                // val is wheel delta in Windows (-120 or 120 per click, scale appropriately)
                val wheelDelta = (deltaY * 30).toInt()
                val conn = openConnection("/remote/mouse/scroll?val=$wheelDelta", timeoutMs = 1200)
                conn.connect()
                conn.responseCode
                conn.disconnect()
            } catch (_: Exception) {
                // Ignore transient scroll error
            }
        }
    }

    fun sendMouseClickAt(x: Int, y: Int, btn: String = "left") {
        executor.execute {
            try {
                val conn = openConnection("/remote/mouse/clickat?x=$x&y=$y&btn=$btn", timeoutMs = 1500)
                conn.connect()
                conn.responseCode
                conn.disconnect()
            } catch (_: Exception) {}
        }
    }

    fun sendMouseMoveTo(x: Int, y: Int) {
        executor.execute {
            try {
                val conn = openConnection("/remote/mouse/moveto?x=$x&y=$y", timeoutMs = 1200)
                conn.connect()
                conn.responseCode
                conn.disconnect()
            } catch (_: Exception) {}
        }
    }

    fun sendCopy() {
        sendKeyPress("ctrlc")
    }

    fun sendPaste() {
        sendKeyPress("ctrlv")
    }

    /**
     * Plays a loud alarm sound + vibration on the phone itself.
     * Called when the user taps "Find My Phone" button on the mobile dashboard.
     */
    fun findMyPhone(context: android.content.Context) {
        mainHandler.post {
            try {
                val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                val originalVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM), 0)

                val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                    vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 1000, 500, 1000, 500, 1000, 500, 1000), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000, 500, 1000), -1)
                }

                var uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                if (uri == null) uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
                if (uri == null) uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)

                val player = android.media.MediaPlayer()
                player.setDataSource(context, uri!!)
                player.setAudioAttributes(android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                player.isLooping = true
                player.prepare()
                player.start()

                mainHandler.postDelayed({
                    try {
                        player.stop()
                        player.release()
                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, originalVolume, 0)
                    } catch (_: Exception) {}
                }, 8000)
                Log.d(TAG, "Find My Phone: alarm activated")
            } catch (e: Exception) {
                Log.e(TAG, "Find My Phone error: ${e.message}")
            }
        }
    }

    fun fetchScreenInfo(onComplete: ((width: Int, height: Int) -> Unit)? = null) {
        executor.execute {
            try {
                val conn = openConnection("/screen/info", timeoutMs = 2000)
                conn.connect()
                if (conn.responseCode == 200) {
                    val raw = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(raw)
                    val w = json.optInt("width", 1920)
                    val h = json.optInt("height", 1080)
                    if (w > 0) pcScreenWidth = w
                    if (h > 0) pcScreenHeight = h
                }
                conn.disconnect()
            } catch (_: Exception) {}
            mainHandler.post { onComplete?.invoke(pcScreenWidth, pcScreenHeight) }
        }
    }

    fun sendKeyPress(key: String, onResult: ((Boolean) -> Unit)? = null) {
        executor.execute {
            var success = false
            try {
                val keyParam = key.trim().lowercase()
                val conn = openConnection("/remote/keyboard/press?val=$keyParam", timeoutMs = 2500)
                conn.connect()
                success = conn.responseCode in 200..299
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "sendKeyPress error: ${e.message}")
            }
            mainHandler.post { onResult?.invoke(success) }
        }
    }

    fun sendBackspace(onResult: ((Boolean) -> Unit)? = null) {
        executor.execute {
            var success = false
            try {
                val conn = openConnection("/remote/keyboard/backspace", timeoutMs = 2000)
                conn.connect()
                success = conn.responseCode in 200..299
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "sendBackspace error: ${e.message}")
            }
            mainHandler.post { onResult?.invoke(success) }
        }
    }

    fun sendText(text: String, onResult: ((Boolean) -> Unit)? = null) {
        executor.execute {
            var success = false
            try {
                val encoded = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
                val conn = openConnection("/remote/keyboard/text?val=$encoded", timeoutMs = 3000)
                conn.connect()
                success = conn.responseCode in 200..299
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "sendText error: ${e.message}")
            }
            mainHandler.post { onResult?.invoke(success) }
        }
    }

    fun openUrlOnPc(url: String, onResult: ((Boolean) -> Unit)? = null) {
        executor.execute {
            var success = false
            try {
                val encodedUrl = URLEncoder.encode(url, "UTF-8").replace("+", "%20")
                val conn = openConnection("/remote/open-url?url=$encodedUrl", timeoutMs = 4000)
                conn.connect()
                success = conn.responseCode in 200..299
                conn.disconnect()
            } catch (exception: Exception) {
                Log.e(TAG, "openUrlOnPc error: ${exception.message}")
            }
            mainHandler.post { onResult?.invoke(success) }
        }
    }

    fun getProcesses(onResult: (List<ProcessItem>) -> Unit) {
        executor.execute {
            val list = mutableListOf<ProcessItem>()
            try {
                val conn = openConnection("/processes", timeoutMs = 4000)
                conn.connect()
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val arr = JSONArray(body)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(
                            ProcessItem(
                                name = obj.optString("name", "Unknown"),
                                pid = obj.optInt("pid", 0),
                                memory = obj.optString("memory", "0 MB")
                            )
                        )
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "getProcesses error: ${e.message}")
            }
            mainHandler.post { onResult(list) }
        }
    }

    fun killProcess(pid: Int, onResult: (Boolean) -> Unit) {
        executor.execute {
            var success = false
            try {
                val conn = openConnection("/kill", method = "POST", timeoutMs = 3500)
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val payload = "{\"pid\":$pid}"
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                success = conn.responseCode in 200..299
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "killProcess error: ${e.message}")
            }
            mainHandler.post { onResult(success) }
        }
    }

    fun getFileList(path: String = "", onResult: (List<FileItem>) -> Unit) {
        executor.execute {
            val files = mutableListOf<FileItem>()
            try {
                val query = if (path.isNotEmpty()) "?path=${URLEncoder.encode(path, "UTF-8").replace("+", "%20")}" else ""
                val conn = openConnection("/list$query", timeoutMs = 4000)
                conn.connect()
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val arr = JSONArray(body)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        files.add(
                            FileItem(
                                name = obj.optString("name", "Unknown"),
                                path = obj.optString("path", ""),
                                isDir = obj.optBoolean("isDir", false),
                                size = obj.optString("size", "")
                            )
                        )
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "getFileList error: ${e.message}")
            }
            mainHandler.post { onResult(files) }
        }
    }

    @Volatile
    var liveStreamQuality: Int = 92

    @Volatile
    var liveStreamWidth: Int = 0 // 0 = Full native resolution (e.g. 1080p/1440p/4K)

    fun getScreenshot(quality: Int = liveStreamQuality, targetWidth: Int = liveStreamWidth, onResult: (Bitmap?) -> Unit) {
        executor.execute {
            var bitmap: Bitmap? = null
            try {
                // /mirror/live delivers instantaneous high quality screen frames
                val query = "q=$quality&w=$targetWidth"
                val conn = openConnection("/mirror/live?$query", timeoutMs = 4500)
                conn.connect()
                if (conn.responseCode == 200) {
                    bitmap = BitmapFactory.decodeStream(conn.inputStream)
                }
                conn.disconnect()
            } catch (e: Exception) {
                // Fallback to /screenshot if needed
                try {
                    val conn2 = openConnection("/screenshot", timeoutMs = 4500)
                    conn2.connect()
                    if (conn2.responseCode == 200) {
                        bitmap = BitmapFactory.decodeStream(conn2.inputStream)
                    }
                    conn2.disconnect()
                } catch (_: Exception) {}
            }
            mainHandler.post { onResult(bitmap) }
        }
    }

    fun uploadFileStream(fileName: String, inputStream: InputStream, onProgress: (Int) -> Unit = {}, onComplete: (Boolean) -> Unit) {
        executor.execute {
            var success = false
            try {
                val boundary = "====${System.currentTimeMillis()}===="
                val conn = openConnection("/upload", method = "POST", timeoutMs = 60000)
                conn.doOutput = true
                conn.setChunkedStreamingMode(65536)
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

                val outputStream: OutputStream = conn.outputStream
                val header = "--$boundary\r\n" +
                        "Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n" +
                        "Content-Type: application/octet-stream\r\n\r\n"
                outputStream.write(header.toByteArray(Charsets.UTF_8))

                val buffer = ByteArray(65536)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.flush()

                val footer = "\r\n--$boundary--\r\n"
                outputStream.write(footer.toByteArray(Charsets.UTF_8))
                outputStream.flush()
                outputStream.close()

                val respCode = conn.responseCode
                Log.d(TAG, "uploadFileStream response code: $respCode")
                success = respCode in 200..302
                if (success) {
                    try { conn.inputStream.bufferedReader().use { it.readText() } } catch (_: Exception) {}
                } else {
                    try { conn.errorStream?.bufferedReader()?.use { it.readText() } } catch (_: Exception) {}
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "uploadFileStream error: ${e.message}")
            } finally {
                try { inputStream.close() } catch (_: Exception) {}
            }
            mainHandler.post { onComplete(success) }
        }
    }

    fun discoverPcHub(onDiscovered: (ip: String, port: Int) -> Unit) {
        executor.execute {
            try {
                val socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(8091))
                    soTimeout = 4000
                }
                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                val msg = String(packet.data, 0, packet.length, Charsets.UTF_8)
                val senderIp = packet.address.hostAddress ?: ""
                socket.close()

                if (msg.contains("PC_MASTER_HUB")) {
                    val port = msg.substringAfterLast(":", "8099").toIntOrNull() ?: 8099
                    mainHandler.post { onDiscovered(senderIp, port) }
                }
            } catch (e: Exception) {
                Log.d(TAG, "UDP discovery timeout/skipped: ${e.message}")
            }
        }
    }

    fun getClipboard(onResult: (String) -> Unit) {
        executor.execute {
            var text = ""
            try {
                val conn = openConnection("/clipboard/get", timeoutMs = 2500)
                conn.connect()
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    text = json.optString("text", "")
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "getClipboard error: ${e.message}")
            }
            mainHandler.post { onResult(text) }
        }
    }

    fun setClipboard(text: String, onResult: ((Boolean) -> Unit)? = null) {
        executor.execute {
            var success = false
            try {
                val conn = openConnection("/clipboard/set", method = "POST", timeoutMs = 3000)
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val json = JSONObject().apply { put("text", text) }
                conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
                success = conn.responseCode in 200..299
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "setClipboard error: ${e.message}")
            }
            mainHandler.post { onResult?.invoke(success) }
        }
    }

    fun sendSpecialKey(key: String, onResult: ((Boolean) -> Unit)? = null) {
        when (key.lowercase()) {
            "alttab" -> sendKeyPress("alttab", onResult)
            "showdesktop", "wind" -> sendKeyPress("showdesktop", onResult)
            "taskmgr" -> sendKeyPress("taskmgr", onResult)
            "win" -> sendKeyPress("win", onResult)
            "esc" -> sendKeyPress("esc", onResult)
            "tab" -> sendKeyPress("tab", onResult)
            "enter" -> sendKeyPress("enter", onResult)
            "ctrlc" -> sendCopy()
            "ctrlv" -> sendPaste()
            else -> sendText(key, onResult)
        }
    }

    fun sendWakeOnLan(macAddress: String, onResult: (Boolean) -> Unit) {
        executor.execute {
            var success = false
            try {
                val cleanMac = macAddress.replace(":", "").replace("-", "").trim()
                if (cleanMac.length == 12) {
                    val macBytes = ByteArray(6)
                    for (i in 0 until 6) {
                        macBytes[i] = cleanMac.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                    }
                    val magicPacket = ByteArray(102)
                    for (i in 0 until 6) {
                        magicPacket[i] = 0xFF.toByte()
                    }
                    for (i in 1..16) {
                        System.arraycopy(macBytes, 0, magicPacket, i * 6, 6)
                    }
                    val socket = DatagramSocket()
                    socket.broadcast = true
                    val packet9 = DatagramPacket(magicPacket, magicPacket.size, InetSocketAddress("255.255.255.255", 9))
                    val packet7 = DatagramPacket(magicPacket, magicPacket.size, InetSocketAddress("255.255.255.255", 7))
                    socket.send(packet9)
                    socket.send(packet7)
                    socket.close()
                    success = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendWakeOnLan error: ${e.message}")
            }
            mainHandler.post { onResult(success) }
        }
    }

    fun checkPcHealth(onResult: (PcHealthReport?) -> Unit) {
        executor.execute {
            val startPing = System.currentTimeMillis()
            var pingMs: Long
            var pingSuccess = false
            try {
                val pingConn = openConnection("/ping", timeoutMs = 2500)
                pingConn.connect()
                pingSuccess = pingConn.responseCode in 200..299
                pingMs = (System.currentTimeMillis() - startPing).coerceAtLeast(1L)
                pingConn.disconnect()
            } catch (e: Exception) {
                pingMs = 999L
            }

            var healthJson: JSONObject? = null
            // 1. Try dedicated /health endpoint
            try {
                val conn = openConnection("/health", timeoutMs = 3500)
                conn.connect()
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    healthJson = JSONObject(body)
                }
                conn.disconnect()
            } catch (_: Exception) {}

            // 2. If /health not available, fall back to /stats
            var statsJson: JSONObject? = null
            if (healthJson == null) {
                try {
                    val conn = openConnection("/stats", timeoutMs = 3500)
                    conn.connect()
                    if (conn.responseCode in 200..299) {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        statsJson = JSONObject(body)
                    }
                    conn.disconnect()
                } catch (_: Exception) {}
            }

            if (healthJson == null && statsJson == null && !pingSuccess) {
                mainHandler.post { onResult(null) }
                return@execute
            }

            var settingsJson: JSONObject? = null
            try {
                val sConn = openConnection("/settings", timeoutMs = 2500)
                sConn.connect()
                if (sConn.responseCode in 200..299) {
                    val sBody = sConn.inputStream.bufferedReader().use { it.readText() }
                    settingsJson = JSONObject(sBody)
                }
                sConn.disconnect()
            } catch (_: Exception) {}

            var batteryJson: JSONObject? = null
            try {
                val bConn = openConnection("/battery", timeoutMs = 2500)
                bConn.connect()
                if (bConn.responseCode in 200..299) {
                    val bBody = bConn.inputStream.bufferedReader().use { it.readText() }
                    batteryJson = JSONObject(bBody)
                }
                bConn.disconnect()
            } catch (_: Exception) {}

            val report = if (healthJson != null) {
                parseDedicatedHealthReport(healthJson, pingMs, settingsJson)
            } else {
                buildHealthReportFromStats(statsJson ?: JSONObject(), pingMs, settingsJson, batteryJson)
            }

            mainHandler.post { onResult(report) }
        }
    }

    private fun parseDedicatedHealthReport(h: JSONObject, pingMs: Long, settings: JSONObject?): PcHealthReport {
        val score = h.optInt("score", 95).coerceIn(0, 100)
        val grade = when {
            score >= 90 -> "SYSTEM OPTIMAL"
            score >= 75 -> "GOOD / NORMAL"
            score >= 55 -> "ATTENTION NEEDED"
            else -> "CRITICAL BOTTLENECK"
        }
        val gradeColor = when {
            score >= 90 -> "#00FF88"
            score >= 75 -> "#00D2FF"
            score >= 55 -> "#FFB800"
            else -> "#FF453A"
        }
        val cpuLoad = h.optInt("cpuLoad", 0)
        val cpuTemp = h.optInt("cpuTemp", 0)
        val cpuStatus = when {
            cpuTemp > 85 || cpuLoad > 90 -> "CRITICAL"
            cpuTemp > 75 || cpuLoad > 75 -> "HEAVY"
            else -> "OPTIMAL"
        }
        val ramUsed = h.optDouble("ramUsedGb", 0.0)
        val ramTotal = h.optDouble("ramTotalGb", 0.0)
        val ramPct = h.optInt("ramPercent", if (ramTotal > 0) ((ramUsed / ramTotal) * 100).toInt() else 0)
        val ramStatus = when {
            ramPct > 90 -> "HIGH PRESSURE"
            ramPct > 75 -> "MODERATE"
            else -> "HEALTHY BUFFER"
        }
        val storageUsed = h.optDouble("storageUsedGb", 0.0)
        val storageTotal = h.optDouble("storageTotalGb", 0.0)
        val storagePct = h.optInt("storagePercent", if (storageTotal > 0) ((storageUsed / storageTotal) * 100).toInt() else 0)
        val storageStatus = when {
            storagePct > 90 -> "LOW SPACE"
            storagePct > 80 -> "MODERATE"
            else -> "AMPLE SPACE"
        }
        val netUp = h.optDouble("netUpMb", 0.0)
        val netDown = h.optDouble("netDownMb", 0.0)
        val latencyStatus = when {
            pingMs < 20 -> "ULTRA FAST (<20ms)"
            pingMs < 60 -> "FAST LAN"
            else -> "ACCEPTABLE"
        }
        val batteryObj = h.optJSONObject("battery")
        val batteryPct = batteryObj?.optInt("charge", 100) ?: 100
        val isAc = batteryObj?.optString("status", "")?.contains("AC", ignoreCase = true) == true || batteryObj?.optBoolean("available", false) == false
        val batteryStatus = if (isAc) "AC Adapter Connected" else "On Battery ($batteryPct%)"

        val issuesList = mutableListOf<String>()
        val jsonIssues = h.optJSONArray("issues")
        if (jsonIssues != null) {
            for (i in 0 until jsonIssues.length()) {
                issuesList.add(jsonIssues.optString(i))
            }
        }

        val hostname = h.optString("hostname", settings?.optString("hostname", "Windows PC") ?: "Windows PC")
        val osInfo = h.optString("os", settings?.optString("os", "Windows 10/11") ?: "Windows 10/11")
        val uptime = h.optString("uptime", "Active")

        val summary = if (issuesList.isEmpty()) {
            "All core subsystems (CPU thermals, RAM allocation, NVMe/SSD space, and LAN latency) are in optimal condition."
        } else {
            issuesList.joinToString(" • ")
        }

        return PcHealthReport(
            overallScore = score,
            healthGrade = grade,
            gradeColor = gradeColor,
            summary = summary,
            cpuLoad = cpuLoad,
            cpuTemp = cpuTemp,
            cpuStatus = cpuStatus,
            ramUsedGb = ramUsed,
            ramTotalGb = ramTotal,
            ramPercent = ramPct,
            ramStatus = ramStatus,
            storageUsedGb = storageUsed,
            storageTotalGb = storageTotal,
            storagePercent = storagePct,
            storageStatus = storageStatus,
            netUpMb = netUp,
            netDownMb = netDown,
            pingMs = pingMs,
            latencyStatus = latencyStatus,
            batteryPct = batteryPct,
            batteryStatus = batteryStatus,
            uptime = uptime,
            hostname = hostname,
            osInfo = osInfo,
            issues = issuesList
        )
    }

    private fun buildHealthReportFromStats(stats: JSONObject, pingMs: Long, settings: JSONObject?, battery: JSONObject?): PcHealthReport {
        val cpuLoad = stats.optDouble("cpu", stats.optDouble("cpuLoad", 0.0)).toInt()
        val cpuTemp = stats.optDouble("cpuTemp", 0.0).toInt()
        val ramUsed = stats.optDouble("ramUsed", stats.optDouble("ramUsedGb", 0.0))
        val ramTotal = stats.optDouble("ramTotal", stats.optDouble("ramTotalGb", 0.0))
        val ramPct = if (ramTotal > 0.0) ((ramUsed / ramTotal) * 100).toInt().coerceIn(0, 100) else stats.optInt("ram", 0)

        val storageUsed = stats.optDouble("storageUsed", stats.optDouble("storageUsedGb", 0.0))
        val storageTotal = stats.optDouble("storageTotal", stats.optDouble("storageTotalGb", 0.0))
        val storagePct = if (storageTotal > 0.0) ((storageUsed / storageTotal) * 100).toInt().coerceIn(0, 100) else 0

        val netUp = stats.optDouble("netUp", stats.optDouble("netUpMb", 0.0))
        val netDown = stats.optDouble("netDown", stats.optDouble("netDownMb", 0.0))

        var score = 100
        val issues = mutableListOf<String>()

        if (cpuLoad > 90) { score -= 20; issues.add("High CPU Load ($cpuLoad%)") }
        else if (cpuLoad > 75) { score -= 10; issues.add("Elevated CPU Load ($cpuLoad%)") }

        if (cpuTemp > 85) { score -= 25; issues.add("High CPU Temperature (${cpuTemp}°C)") }
        else if (cpuTemp > 75) { score -= 10; issues.add("Warm CPU (${cpuTemp}°C)") }

        if (ramPct > 90) { score -= 20; issues.add("High RAM Usage ($ramPct%)") }
        else if (ramPct > 80) { score -= 10; issues.add("Elevated RAM ($ramPct%)") }

        if (storagePct > 95) { score -= 20; issues.add("Low Disk Space ($storagePct% full)") }
        else if (storagePct > 85) { score -= 10; issues.add("Disk Space filling up ($storagePct%)") }

        if (pingMs > 100) { score -= 10; issues.add("High LAN Latency (${pingMs}ms)") }

        score = score.coerceIn(10, 100)

        val grade = when {
            score >= 90 -> "SYSTEM OPTIMAL"
            score >= 75 -> "GOOD / NORMAL"
            score >= 55 -> "ATTENTION NEEDED"
            else -> "CRITICAL BOTTLENECK"
        }
        val gradeColor = when {
            score >= 90 -> "#00FF88"
            score >= 75 -> "#00D2FF"
            score >= 55 -> "#FFB800"
            else -> "#FF453A"
        }
        val cpuStatus = when {
            cpuTemp > 85 || cpuLoad > 90 -> "CRITICAL"
            cpuTemp > 75 || cpuLoad > 75 -> "HEAVY"
            else -> "OPTIMAL"
        }
        val ramStatus = when {
            ramPct > 90 -> "HIGH PRESSURE"
            ramPct > 75 -> "MODERATE"
            else -> "HEALTHY BUFFER"
        }
        val storageStatus = when {
            storagePct > 90 -> "LOW SPACE"
            storagePct > 80 -> "MODERATE"
            else -> "AMPLE SPACE"
        }
        val latencyStatus = when {
            pingMs < 20 -> "ULTRA FAST (<20ms)"
            pingMs < 60 -> "FAST LAN"
            else -> "ACCEPTABLE"
        }

        val batteryPct = battery?.optInt("charge", 100) ?: 100
        val isAc = battery?.optString("status", "")?.contains("AC", ignoreCase = true) == true || battery?.optBoolean("available", false) == false
        val batteryStatus = if (isAc) "AC Adapter Connected" else "On Battery ($batteryPct%)"

        val hostname = settings?.optString("hostname", "Windows PC") ?: "Windows PC"
        val osInfo = settings?.optString("os", "Windows 10/11") ?: "Windows 10/11"

        val summary = if (issues.isEmpty()) {
            "All hardware sensors, thermals, and memory buffers are operating within safe and peak limits."
        } else {
            issues.joinToString(" • ")
        }

        return PcHealthReport(
            overallScore = score,
            healthGrade = grade,
            gradeColor = gradeColor,
            summary = summary,
            cpuLoad = cpuLoad,
            cpuTemp = cpuTemp,
            cpuStatus = cpuStatus,
            ramUsedGb = ramUsed,
            ramTotalGb = ramTotal,
            ramPercent = ramPct,
            ramStatus = ramStatus,
            storageUsedGb = storageUsed,
            storageTotalGb = storageTotal,
            storagePercent = storagePct,
            storageStatus = storageStatus,
            netUpMb = netUp,
            netDownMb = netDown,
            pingMs = pingMs,
            latencyStatus = latencyStatus,
            batteryPct = batteryPct,
            batteryStatus = batteryStatus,
            uptime = "Active Session",
            hostname = hostname,
            osInfo = osInfo,
            issues = issues
        )
    }
}
