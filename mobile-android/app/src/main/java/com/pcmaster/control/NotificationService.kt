package com.pcmaster.control

import android.app.Notification
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class NotificationService : NotificationListenerService() {

    private val executor = Executors.newCachedThreadPool()
    private val activeNotifications = mutableMapOf<String, StatusBarNotification>()
    private var replyServerThread: Thread? = null

    private val replyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.pcmaster.control.REPLY") {
                val id = intent.getStringExtra("id")
                val message = intent.getStringExtra("message")
                if (id != null && message != null) {
                    sendReply(id, message)
                }
            }
        }
    }

    private var httpServer: java.net.ServerSocket? = null

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter("com.pcmaster.control.REPLY")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(replyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(replyReceiver, filter)
        }
        startReplyServer()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(replyReceiver) } catch (e: Exception) {}
        try { httpServer?.close() } catch (e: Exception) {}
        replyServerThread?.interrupt()
        replyServerThread = null
        executor.shutdown()
    }

    private fun parseQueryParams(query: String?): Map<String, String> {
        if (query.isNullOrEmpty()) return emptyMap()
        return query.split("&").associate {
            val parts = it.split("=")
            val key = java.net.URLDecoder.decode(parts[0], "UTF-8")
            val valStr = if (parts.size > 1) java.net.URLDecoder.decode(parts[1], "UTF-8") else ""
            key to valStr
        }
    }

    private fun getTargetFile(requestedPath: String?): java.io.File {
        val rootDir = getExternalFilesDir(null) ?: filesDir
        rootDir.mkdirs()
        if (requestedPath.isNullOrEmpty()) {
            return rootDir
        }
        val file = java.io.File(requestedPath)
        if (file.absolutePath.startsWith(rootDir.absolutePath)) {
            return file
        }
        return java.io.File(rootDir, requestedPath.removePrefix("/"))
    }

    private fun startReplyServer() {
        replyServerThread = Thread({
            try {
                httpServer = java.net.ServerSocket(8092)
                while (!httpServer!!.isClosed) {
                    val socket = httpServer!!.accept()
                    val inputStream = socket.getInputStream()
                    
                    // Read headers byte-by-byte until we see "\r\n\r\n" or "\n\n"
                    val headerBytes = java.io.ByteArrayOutputStream()
                    var c1 = -1
                    var c2 = -1
                    var c3 = -1
                    var c4 = -1
                    while (true) {
                        val b = inputStream.read()
                        if (b == -1) break
                        headerBytes.write(b)
                        c1 = c2
                        c2 = c3
                        c3 = c4
                        c4 = b
                        if (c1 == 13 && c2 == 10 && c3 == 13 && c4 == 10) {
                            break // CRLFCRLF
                        }
                        if (c3 == 10 && c4 == 10) {
                            break // LFLF
                        }
                    }

                    val headersStr = headerBytes.toString("UTF-8")
                    val headerLines = headersStr.split(Regex("\r?\n"))
                    if (headerLines.isNotEmpty()) {
                        val requestLine = headerLines[0]
                        val parts = requestLine.split(" ")
                        if (parts.size >= 2) {
                            val method = parts[0]
                            val fullUrl = parts[1]
                            
                            // Extract headers
                            var contentLength = 0
                            var contentDisposition = ""
                            for (i in 1 until headerLines.size) {
                                val header = headerLines[i]
                                if (header.startsWith("Content-Length:", ignoreCase = true)) {
                                    contentLength = header.substring(15).trim().toInt()
                                } else if (header.startsWith("Content-Disposition:", ignoreCase = true)) {
                                    contentDisposition = header.substring(20).trim()
                                }
                            }

                            val uriParts = fullUrl.split("?")
                            val path = uriParts[0]
                            val query = if (uriParts.size > 1) uriParts[1] else null
                            val queryParams = parseQueryParams(query)

                            if (method == "POST" && path == "/reply") {
                                val bodyBytes = ByteArray(contentLength)
                                var read = 0
                                while (read < contentLength) {
                                    val r = inputStream.read(bodyBytes, read, contentLength - read)
                                    if (r == -1) break
                                    read += r
                                }
                                val bodyStr = String(bodyBytes, Charsets.UTF_8)
                                
                                val params = bodyStr.split("&").associate {
                                    val p = it.split("=")
                                    java.net.URLDecoder.decode(p[0], "UTF-8") to if (p.size > 1) java.net.URLDecoder.decode(p[1], "UTF-8") else ""
                                }
                                val id = params["id"]
                                val message = params["message"]
                                if (id != null && message != null) {
                                    sendReply(id, message)
                                }
                                
                                val out = socket.getOutputStream().bufferedWriter()
                                out.write("HTTP/1.1 200 OK\r\n")
                                out.write("Content-Length: 0\r\n")
                                out.write("\r\n")
                                out.flush()
                            }
                            else if (method == "GET" && path == "/files/list") {
                                val targetFile = getTargetFile(queryParams["path"])
                                val filesList = org.json.JSONArray()
                                if (targetFile.exists() && targetFile.isDirectory) {
                                    val files = targetFile.listFiles()
                                    if (files != null) {
                                        for (f in files) {
                                            val obj = JSONObject()
                                            obj.put("name", f.name)
                                            obj.put("path", f.absolutePath)
                                            obj.put("isDir", f.isDirectory)
                                            if (f.isDirectory) {
                                                obj.put("size", "")
                                            } else {
                                                val sizeInKb = f.length() / 1024
                                                val sizeStr = if (sizeInKb > 1024) {
                                                    String.format(java.util.Locale.US, "%.2f MB", sizeInKb / 1024.0)
                                                } else {
                                                    "$sizeInKb KB"
                                                }
                                                obj.put("size", sizeStr)
                                            }
                                            filesList.put(obj)
                                        }
                                    }
                                }
                                val responseBytes = filesList.toString().toByteArray(Charsets.UTF_8)
                                val out = socket.getOutputStream()
                                val writer = out.bufferedWriter()
                                writer.write("HTTP/1.1 200 OK\r\n")
                                writer.write("Content-Type: application/json; charset=utf-8\r\n")
                                writer.write("Content-Length: ${responseBytes.size}\r\n")
                                writer.write("\r\n")
                                writer.flush()
                                out.write(responseBytes)
                                out.flush()
                            }
                            else if (method == "GET" && path == "/files/download") {
                                val targetFile = getTargetFile(queryParams["path"])
                                if (targetFile.exists() && !targetFile.isDirectory) {
                                    val responseBytes = targetFile.readBytes()
                                    val out = socket.getOutputStream()
                                    val writer = out.bufferedWriter()
                                    writer.write("HTTP/1.1 200 OK\r\n")
                                    writer.write("Content-Type: application/octet-stream\r\n")
                                    writer.write("Content-Length: ${responseBytes.size}\r\n")
                                    writer.write("\r\n")
                                    writer.flush()
                                    out.write(responseBytes)
                                    out.flush()
                                } else {
                                    val out = socket.getOutputStream().bufferedWriter()
                                    out.write("HTTP/1.1 404 Not Found\r\n")
                                    out.write("Content-Length: 0\r\n")
                                    out.write("\r\n")
                                    out.flush()
                                }
                            }
                            else if (method == "POST" && path == "/files/delete") {
                                val targetFile = getTargetFile(queryParams["path"])
                                val rootDir = getExternalFilesDir(null) ?: filesDir
                                val success = if (targetFile.exists() && targetFile.absolutePath != rootDir.absolutePath) {
                                    targetFile.deleteRecursively()
                                } else {
                                    false
                                }
                                val out = socket.getOutputStream().bufferedWriter()
                                if (success) {
                                    out.write("HTTP/1.1 200 OK\r\n")
                                } else {
                                    out.write("HTTP/1.1 400 Bad Request\r\n")
                                }
                                out.write("Content-Length: 0\r\n")
                                out.write("\r\n")
                                out.flush()
                            }
                            else if (method == "POST" && path == "/files/upload") {
                                val destDir = getTargetFile(queryParams["dest"])
                                destDir.mkdirs()
                                
                                var filename = "uploaded_file.bin"
                                if (contentDisposition.isNotEmpty()) {
                                    val match = Regex("""filename="([^"]+)"""").find(contentDisposition)
                                    if (match != null) {
                                        filename = match.groupValues[1]
                                    } else {
                                        val match2 = Regex("""filename=([^;]+)""").find(contentDisposition)
                                        if (match2 != null) {
                                            filename = match2.groupValues[1].trim()
                                        }
                                    }
                                }
                                
                                val destFile = java.io.File(destDir, filename)
                                val fos = java.io.FileOutputStream(destFile)
                                val buffer = ByteArray(4096)
                                var totalRead = 0
                                while (totalRead < contentLength) {
                                    val toRead = Math.min(buffer.size, contentLength - totalRead)
                                    val r = inputStream.read(buffer, 0, toRead)
                                    if (r == -1) break
                                    fos.write(buffer, 0, r)
                                    totalRead += r
                                }
                                fos.close()
                                
                                val out = socket.getOutputStream().bufferedWriter()
                                out.write("HTTP/1.1 200 OK\r\n")
                                out.write("Content-Length: 0\r\n")
                                out.write("\r\n")
                                out.flush()
                            }
                            else {
                                val out = socket.getOutputStream().bufferedWriter()
                                out.write("HTTP/1.1 404 Not Found\r\n")
                                out.write("Content-Length: 0\r\n")
                                out.write("\r\n")
                                out.flush()
                            }
                        }
                    }
                    socket.close()
                }
            } catch (e: Exception) {
                Log.e("NotificationService", "HTTP server error", e)
            }
        }, "ReplyServerThread").apply { isDaemon = true; start() }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let {
            val key = it.key
            activeNotifications[key] = it
            forwardNotification(it)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let {
            activeNotifications.remove(it.key)
            // Optionally notify PC about removal
        }
    }

    private fun forwardNotification(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
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
            put("title", title)
            put("content", text)
            put("canReply", canReply)
        }

        sendToPc(json)
    }

    private fun sendToPc(json: JSONObject) {
        val prefs = getSharedPreferences("PCMasterPrefs", Context.MODE_PRIVATE)
        val lastIp = prefs.getString("last_ip", "")
        if (lastIp.isNullOrEmpty()) {
            Log.w("NotificationService", "Cannot send notification: last_ip is empty. Please connect the app to PC first.")
            return
        }

        var ip = lastIp.trim()
        if (!ip.startsWith("http")) {
            ip = "http://$ip"
        }
        if (!ip.substringAfter("://").contains(":")) {
            ip = "$ip:8090"
        }
        val urlStr = "$ip/remote/notif"
        Log.d("NotificationService", "Attempting to send notification to: $urlStr")

        executor.execute {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(urlStr)
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                
                val body = json.toString()
                Log.d("NotificationService", "Sending payload: $body")
                
                conn.outputStream.use { os ->
                    os.write(body.toByteArray(Charsets.UTF_8))
                }
                
                val code = conn.responseCode
                Log.d("NotificationService", "PC responded with code: $code")
            } catch (e: Exception) {
                Log.e("NotificationService", "Failed to send notification to PC at $urlStr", e)
            } finally {
                try { conn?.disconnect() } catch (_: Throwable) {}
            }
        }
    }

    private fun getReplyAction(notification: Notification): Notification.Action? {
        notification.actions?.forEach { action ->
            action.remoteInputs?.forEach { remoteInput ->
                if (remoteInput.allowFreeFormInput) {
                    return action
                }
            }
        }
        return null
    }

    private fun sendReply(id: String, message: String) {
        val sbn = activeNotifications[id] ?: return
        val action = getReplyAction(sbn.notification) ?: return
        val remoteInput = action.remoteInputs?.find { it.allowFreeFormInput } ?: return

        val bundle = Bundle()
        bundle.putCharSequence(remoteInput.resultKey, message)

        val intent = Intent()
        RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)

        try {
            action.actionIntent.send(this, 0, intent)
        } catch (e: Exception) {
            Log.e("NotificationService", "Error sending reply", e)
        }
    }
}
