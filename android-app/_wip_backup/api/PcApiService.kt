package com.pcmaster.mobile.api

import com.pcmaster.mobile.ProcessItem
import com.pcmaster.mobile.FileItem
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.*
import retrofit2.http.Url

interface PcApiService {
    
    @GET("/ping")
    suspend fun ping(): PingResponse

    @GET("/settings")
    suspend fun getSettings(): SettingsResponse

    @GET("/stats")
    suspend fun getStats(): StatsResponse

    @GET("/list")
    suspend fun getFileList(@Query("path") path: String?): List<FileItem>

    @GET("/download")
    suspend fun downloadFile(@Query("path") path: String): ResponseBody

    @GET("/download/{filename}")
    suspend fun downloadFileDirect(@Path("filename") filename: String): ResponseBody

    @Multipart
    @POST("/upload")
    suspend fun uploadFile(
        @Part("file") file: MultipartBody.Part
    ): UploadResponse

    @POST("/remote/notif")
    suspend fun sendNotification(@Body notification: NotificationRequest): BaseResponse

    @GET("/notifications")
    suspend fun getNotifications(): List<NotificationItem>

    @POST("/remote/{command}")
    suspend fun sendRemoteCommand(
        @Path("command") command: String,
        @QueryMap params: Map<String, String>
    ): BaseResponse

    @GET("/processes")
    suspend fun getProcesses(): List<ProcessItem>

    @POST("/kill")
    suspend fun killProcess(@Body pid: PidRequest): BaseResponse

    @GET("/powerplan")
    suspend fun getPowerPlan(@Query("plan") plan: String?): PowerPlanResponse

    @GET("/brightness")
    suspend fun getBrightness(@Query("level") level: String?): BrightnessResponse

    @GET("/battery")
    suspend fun getBattery(): BatteryInfo

    @GET("/wifi")
    suspend fun getWifi(): WifiInfo

    @POST("/timer")
    suspend fun setTimer(@Query("action") action: String, @Query("minutes") minutes: Int): BaseResponse

    @GET("/type")
    suspend fun typeText(@Query("text") text: String): BaseResponse

    @GET("/history")
    suspend fun getHistory(): HistoryResponse

    @GET("/pair/qr")
    suspend fun getPairQr(): PairQrResponse

    @GET("/mirror/stream")
    suspend fun getMirrorStream(): ResponseBody

    @POST("/mirror/start")
    suspend fun startMirror(): BaseResponse

    @POST("/mirror/stop")
    suspend fun stopMirror(): BaseResponse

    @GET("/mirror/live")
    suspend fun getLiveFrame(
        @Query("q") quality: Int? = 75,
        @Query("w") width: Int? = 0
    ): ResponseBody

    @POST("/mirror/frame")
    suspend fun sendFrame(@Body frame: RequestBody): BaseResponse

    @GET("/screen/info")
    suspend fun getScreenInfo(): ScreenInfo

    @GET("/screenshot")
    suspend fun getScreenshot(): ResponseBody

    @GET("/clipboard/get")
    suspend fun getClipboard(): ClipboardResponse

    @POST("/clipboard/set")
    suspend fun setClipboard(@Body clipboard: ClipboardRequest): BaseResponse

    @GET("/media")
    suspend fun getMedia(): MediaInfo

    // Discovery via UDP is not REST - handled separately
}

data class PingResponse(
    val status: String = "ok",
    val app: String = "PCMasterControl",
    val mirroring: Boolean = false
)

data class SettingsResponse(
    val authToken: String = "",
    val requireAuth: Boolean = true,
    val version: String = "2.0",
    val compactMode: Boolean = false,
    val ip: String = "",
    val port: Int = 8099,
    val hostname: String = "",
    val os: String = ""
)

data class StatsResponse(
    val cpuLoad: Float = 0f,
    val cpuTemp: Float = 0f,
    val gpuLoad: Float = 0f,
    val gpuTemp: Float = 0f,
    val ramUsed: Float = 0f,
    val ramTotal: Float = 0f,
    val storageUsed: Float = 0f,
    val storageTotal: Float = 0f,
    val netUp: Float = 0f,
    val netDown: Float = 0f,
    val gamingActive: Boolean = false,
    val antiIdleActive: Boolean = false,
    val caffeineActive: Boolean = false
)

data class UploadResponse(
    val status: String = "ok"
)

data class BaseResponse(
    val status: String = "ok",
    val error: String? = null
)

data class NotificationItem(
    val id: String = "",
    val appName: String = "",
    val title: String = "",
    val content: String = "",
    val packageName: String = "",
    val canReply: Boolean = false
)

data class NotificationRequest(
    val id: String = "",
    val appName: String = "",
    val title: String = "",
    val content: String = "",
    val packageName: String = "",
    val canReply: Boolean = false
)

data class PidRequest(
    val pid: Int
)

data class PowerPlanResponse(
    val output: String = ""
)

data class BrightnessResponse(
    val level: Int = 50
)

data class BatteryInfo(
    val isDesktop: Boolean = false,
    val percent: Int = 100,
    val charging: Boolean = true,
    val remaining: String = "Unknown"
)

data class WifiInfo(
    val interfaces: List<WifiInterface> = emptyList()
)

data class WifiInterface(
    val name: String = "",
    val description: String = "",
    val speed: Long = 0,
    val ip: String? = null
)

data class HistoryResponse(
    val history: List<TelemetryPoint> = emptyList()
)

data class TelemetryPoint(
    val t: String = "",
    val cpu: Float = 0f,
    val cpuT: Float = 0f,
    val gpu: Float = 0f,
    val gpuT: Float = 0f,
    val ram: Float = 0f
)

data class PairQrResponse(
    val url: String = "",
    val token: String = "",
    val port: Int = 8099
)

data class ScreenInfo(
    val width: Int = 1920,
    val height: Int = 1080,
    val left: Int = 0,
    val top: Int = 0
)

data class ClipboardResponse(
    val text: String = ""
)

data class ClipboardRequest(
    val text: String = ""
)

data class MediaInfo(
    val title: String = "",
    val app: String = ""
)