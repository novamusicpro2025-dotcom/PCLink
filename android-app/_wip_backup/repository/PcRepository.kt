package com.pcmaster.mobile.repository

import com.pcmaster.mobile.ProcessItem
import com.pcmaster.mobile.FileItem
import com.pcmaster.mobile.api.ApiClient
import com.pcmaster.mobile.api.PcApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.File
import java.io.InputStream
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody

class PcRepository {
    
    private val service: PcApiService? = ApiClient.getService()
    
    suspend fun ping(): Result<Boolean> = safeApiCall {
        val response = service?.ping()
        response?.isSuccessful == true && response.body()?.status == "ok"
    }
    
    suspend fun getSettings(): Result<ApiClient.SettingsResponse> = safeApiCall {
        service?.getSettings()?.let { response ->
            if (response.isSuccessful) response.body() 
            else throw Exception("HTTP ${response.code()}")
        }
    }
    
    suspend fun getStats(): Result<ApiClient.StatsResponse> = safeApiCall {
        service?.getStats()?.let { response ->
            if (response.isSuccessful) response.body() 
            else throw Exception("HTTP ${response.code()}")
        }
    }
    
    suspend fun getFileList(path: String? = null): Result<List<FileItem>> = safeApiCall {
        service?.getFileList(path)?.let { response ->
            if (response.isSuccessful) response.body()?.map { apiItem ->
                FileItem(
                    name = apiItem.name,
                    path = apiItem.path,
                    isDir = apiItem.isDir,
                    size = apiItem.size
                )
            } ?: emptyList() else throw Exception("HTTP ${response.code()}")
        }
    }
    
    suspend fun downloadFile(path: String): Result<okhttp3.ResponseBody> = safeApiCall {
        service?.downloadFile(path)?.let { response ->
            if (response.isSuccessful) response.body() 
            else throw Exception("HTTP ${response.code()}")
        }
    }
    
    suspend fun uploadFile(fileName: String, inputStream: InputStream): Result<Boolean> = safeApiCall {
        val requestFile = RequestBody.create(
            MediaType.parse("application/octet-stream"),
            inputStream.readAllBytes()
        )
        
        val part = MultipartBody.Part.createFormData("file", fileName, requestFile)
        
        service?.uploadFile(part)?.let { response ->
            response.isSuccessful && (response.body()?.status == "ok")
        } ?: false
    }
    
    suspend fun sendNotification(notification: com.pcmaster.mobile.NotificationItem): Result<Boolean> = safeApiCall {
        val request = com.pcmaster.mobile.api.NotificationRequest(
            id = notification.id,
            appName = notification.appName,
            title = notification.title,
            content = notification.content,
            packageName = notification.packageName,
            canReply = notification.canReply
        )
        
        service?.sendNotification(request)?.let { response ->
            response.isSuccessful && (response.body()?.status == "ok")
        } ?: false
    }
    
    suspend fun getNotifications(): Result<List<com.pcmaster.mobile.NotificationItem>> = safeApiCall {
        service?.getNotifications()?.let { response ->
            if (response.isSuccessful) response.body()?.map { apiItem ->
                com.pcmaster.mobile.NotificationItem(
                    id = apiItem.id,
                    appName = apiItem.appName,
                    title = apiItem.title,
                    content = apiItem.content,
                    packageName = apiItem.packageName,
                    canReply = apiItem.canReply
                )
            } ?: emptyList() else throw Exception("HTTP ${response.code()}")
        }
    }
    
    suspend fun sendRemoteCommand(command: String, params: Map<String, String> = emptyMap()): Result<Boolean> = safeApiCall {
        service?.sendRemoteCommand(command, params)?.let { response ->
            response.isSuccessful && (response.body()?.status == "ok")
        } ?: false
    }
    
    suspend fun getProcesses(): Result<List<ProcessItem>> = safeApiCall {
        service?.getProcesses()?.let { response ->
            if (response.isSuccessful) response.body()?.map { apiItem ->
                ProcessItem(
                    name = apiItem.name,
                    pid = apiItem.pid,
                    memory = apiItem.memory
                )
            } ?: emptyList() else throw Exception("HTTP ${response.code()}")
        }
    }
    
    suspend fun killProcess(pid: Int): Result<Boolean> = safeApiCall {
        service?.killProcess(com.pcmaster.mobile.api.PidRequest(pid))?.let { response ->
            response.isSuccessful && (response.body()?.status == "ok")
        } ?: false
    }
    
    suspend fun setPowerPlan(plan: String): Result<Boolean> = safeApiCall {
        service?.getPowerPlan(plan)?.let { response ->
            response.isSuccessful
        } ?: false
    }
    
    suspend fun getPowerPlan(): Result<String> = safeApiCall {
        service?.getPowerPlan(null)?.let { response ->
            if (response.isSuccessful) response.body()?.output ?: "Unknown"
            else throw Exception("HTTP ${response.code()}")
        }
    }
    
    suspend fun setBrightness(level: Int): Result<Boolean> = safeApiCall {
        service?.getBrightness(level.toString())?.let { response ->
            response.isSuccessful
        } ?: false
    }
    
    suspend fun getBrightness(): Result<Int> = safeApiCall {
        service?.getBrightness(null)?.let { response ->
            if (response.isSuccessful) response.body()?.level ?: 50
            else throw Exception("HTTP ${response.code()}")
        }
    }
    
    suspend fun getBattery(): Result<ApiClient.BatteryInfo> = safeApiCall {
        service?.getBattery()?.let { response ->
            if (response.isSuccessful) response.body() 
            else throw Exception("HTTP ${response.code()}")
        }
    }
    
    suspend fun getWifi(): Result<ApiClient.WifiInfo> = safeApiCall {
        service?.getWifi()?.let { response ->
            if (response.isSuccessful) response.body() 
            else throw Exception("HTTP ${response.code()}")
        }
    }
    
    suspend fun setTimer(action: String, minutes: Int): Result<Boolean> = safeApiCall {
        service?.setTimer(action, minutes)?.let { response ->
            response.isSuccessful && (response.body()?.status == "ok")
        } ?: false
    }
    
    suspend fun typeText(text: String): Result<Boolean> = safeApiCall {
        service?.typeText(text)?.let { response ->
            response.isSuccessful && (response.body()?.status == "ok")
        } ?: false
    }
    
    suspend fun getHistory(): Result<List<ApiClient.TelemetryPoint>> = safeApiCall {
        service?.getHistory()?.let { response ->
            if (response.isSuccessful) response.body()?.history ?: emptyList()
            else throw Exception("HTTP ${response.code()}")
        }
    }
    
    suspend fun getPairQr(): Result<ApiClient.PairQrResponse> = safeApiCall {
        service?.getPairQr()?.let { response ->
            if (response.isSuccessful) response.body() 
            else throw Exception("HTTP ${response.code()}")
        }
    }
    
    suspend fun startMirror(): Result<Boolean> = safeApiCall {
        service?.startMirror()?.let { response ->
            response.isSuccessful && (response.body()?.status == "ok")
        } ?: false
    }
    
    suspend fun stopMirror(): Result<Boolean> = safeApiCall {
        service?.stopMirror()?.let { response ->
            response.isSuccessful && (response.body()?.status == "ok")
        } ?: false
    }
    
    suspend fun getLiveFrame(quality: Int = 75, width: Int = 0): Result<okhttp3.ResponseBody> = safeApiCall {
        service?.getLiveFrame(quality, width)?.let { response ->
            if (response.isSuccessful) response.body() 
            else throw Exception("HTTP ${response.code()}")
        }
    }
    
    suspend fun sendFrame(frameBytes: ByteArray): Result<Boolean> = safeApiCall {
        val requestBody = RequestBody.create(
            MediaType.parse("image/jpeg"),
            frameBytes
        )
        service?.sendFrame(requestBody)?.let { response ->
            response.isSuccessful && (response.body()?.status == "ok")
        } ?: false
    }
    
    suspend fun getScreenInfo(): Result<ApiClient.ScreenInfo> = safeApiCall {
        service?.getScreenInfo()?.let { response ->
            if (response.isSuccessful) response.body() 
            else throw Exception("HTTP ${response.code()}")
        }
    }
    
    suspend fun getScreenshot(): Result<okhttp3.ResponseBody> = safeApiCall {
        service?.getScreenshot()?.let { response ->
            if (response.isSuccessful) response.body() 
            else throw Exception("HTTP ${response.code()}")
        }
    }
    
    suspend fun getClipboard(): Result<String> = safeApiCall {
        service?.getClipboard()?.let { response ->
            if (response.isSuccessful) response.body()?.text ?: ""
            else throw Exception("HTTP ${response.code()}")
        }
    }
    
    suspend fun setClipboard(text: String): Result<Boolean> = safeApiCall {
        service?.setClipboard(com.pcmaster.mobile.api.ClipboardRequest(text))?.let { response ->
            response.isSuccessful && (response.body()?.status == "ok")
        } ?: false
    }
    
    suspend fun getMedia(): Result<ApiClient.MediaInfo> = safeApiCall {
        service?.getMedia()?.let { response ->
            if (response.isSuccessful) response.body() 
            else throw Exception("HTTP ${response.code()}")
        }
    }
    
    // Discovery - uses raw UDP, not REST
    suspend fun discoverPcHub(): Result<Pair<String, Int>> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val socket = java.net.DatagramSocket().apply {
                soTimeout = 4000
                setReuseAddress(true)
            }
            val buffer = ByteArray(1024)
            val packet = java.net.DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            val msg = String(packet.data, 0, packet.length)
            val senderIp = packet.address.hostAddress ?: ""
            socket.close()
            
            if (msg.contains("PC_MASTER_HUB")) {
                val port = msg.substringAfterLast(":", "8099").toIntOrNull() ?: 8099
                Result.success(senderIp to port)
            } else {
                Result.failure(Exception("Invalid discovery response"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}