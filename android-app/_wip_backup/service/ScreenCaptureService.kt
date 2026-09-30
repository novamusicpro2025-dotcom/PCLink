package com.pcmaster.mobile.service

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pcmaster.mobile.BuildConfig
import com.pcmaster.mobile.R
import com.pcmaster.mobile.api.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class ScreenCaptureService : Service() {
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "screen_capture_channel"
        
        @Volatile
        var isRunning = false
        
        var captureResultCode: Int = 0
        var captureResultData: Intent? = null
        var captureServerIp: String = ""
    }
    
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var serverIp = ""
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private val isSendingFrame = java.util.concurrent.atomic.AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    override fun onCreate() {
        super.onCreate()
        handlerThread = HandlerThread("ScreenCaptureThread").apply { start() }
        handler = Handler(handlerThread!!.looper)
        createNotificationChannel()
        startForegroundNotification()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        
        val resultCode = if (captureResultCode == android.app.Activity.RESULT_OK) 
            captureResultCode else intent?.getIntExtra("resultCode", android.app.Activity.RESULT_CANCELED) ?: android.app.Activity.RESULT_CANCELED
        
        val data = captureResultData ?: intent?.getParcelableExtra("data")
        
        if (resultCode != android.app.Activity.RESULT_OK || data == null) {
            Log.w("ScreenCaptureService", "Invalid resultCode or null data, stopping service")
            stopSelf()
            return START_NOT_STICKY
        }
        
        serverIp = if (captureServerIp.isNotEmpty()) captureServerIp 
            else intent?.getStringExtra("serverIp") 
            ?: getSharedPreferences("pc_master", MODE_PRIVATE).getString("pc_ip", "") 
            ?: ""
        
        if (serverIp.isNotEmpty()) {
            serverIp = if (serverIp.startsWith("http")) serverIp else "http://$serverIp"
            serverIp = serverIp.trimEnd('/')
        }
        
        try {
            val projectionManager = getSystemService(MediaProjectionManager::class.java)
            mediaProjection = projectionManager?.getMediaProjection(resultCode, data)
            
            if (mediaProjection == null) {
                Log.e("ScreenCaptureService", "Failed to obtain MediaProjection")
                stopSelf()
                return START_NOT_STICKY
            }
            
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    Log.d("ScreenCaptureService", "MediaProjection stopped by system/user")
                    stopSelf()
                }
            }, handler)
            
            startCapture()
            isRunning = true
            Log.d("ScreenCaptureService", "Screen capture successfully active -> $serverIp")
        } catch (e: Throwable) {
            Log.e("ScreenCaptureService", "Exception starting screen capture", e)
            stopSelf()
        }
        
        return START_NOT_STICKY
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "Screen Mirroring Service",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Streaming phone screen to PC Master"
                setShowBadge(false)
            }
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
    
    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PC Master Screen Cast")
            .setContentText("Screen mirroring to PC is active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.app.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } catch (se: SecurityException) {
                Log.w("ScreenCaptureService", "Fallback startForeground without type: ${se.message}")
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
    
    private fun startCapture() {
        try {
            val windowManager = getSystemService(android.view.WindowManager::class.java)
            val metrics = DisplayMetrics()
            var rawWidth = 720
            var rawHeight = 1280
            var density = DisplayMetrics.DENSITY_DEFAULT
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && windowManager != null) {
                val bounds = windowManager.currentWindowMetrics.bounds
                rawWidth = bounds.width()
                rawHeight = bounds.height()
                density = resources.displayMetrics.densityDpi
            } else {
                @Suppress("DEPRECATION")
                windowManager?.defaultDisplay?.getRealMetrics(metrics)
                if (metrics.widthPixels > 0) {
                    rawWidth = metrics.widthPixels
                    rawHeight = metrics.heightPixels
                    density = metrics.densityDpi
                }
            }
            
            // Downscale to 50% or max 720p for fast compression, low bandwidth
            var captureWidth = rawWidth / 2
            var captureHeight = rawHeight / 2
            
            // Ensure dimensions are even numbers (critical for VirtualDisplay buffer allocation)
            if (captureWidth % 2 != 0) captureWidth -= 1
            if (captureHeight % 2 != 0) captureHeight -= 1
            if (captureWidth < 320) captureWidth = 320
            if (captureHeight < 480) captureHeight = 480
            
            var virtualDensity = density / 2
            if (virtualDensity < 120) virtualDensity = 120
            
            Log.d("ScreenCaptureService", "VirtualDisplay resolution: ${captureWidth}x${captureHeight}, density: $virtualDensity")
            
            // Allocate 4 buffer slots to prevent queue starvation
            imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 4)
            imageReader?.setOnImageAvailableListener({ reader ->
                var image: Image? = null
                try {
                    image = reader.acquireLatestImage()
                } catch (_: Throwable) {
                    return@setOnImageAvailableListener
                }
                if (image == null) return@setOnImageAvailableListener
                
                // If previous frame is still uploading, drop frame to prevent network queue buildup
                if (serverIp.isEmpty() || isSendingFrame.get()) {
                    try { image.close() } catch (_: Throwable) {}
                    return@setOnImageAvailableListener
                }
                
                // Copy buffer bytes and immediately close image so ImageReader slot is instantly freed!
                val bitmap: Bitmap? = try {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * captureWidth
                    
                    val bmp = Bitmap.createBitmap(
                        captureWidth + rowPadding / pixelStride,
                        captureHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    bmp.copyPixelsFromBuffer(buffer)
                    if (rowPadding == 0) {
                        bmp
                    } else {
                        val cropped = Bitmap.createBitmap(bmp, 0, 0, captureWidth, captureHeight)
                        bmp.recycle()
                        cropped
                    }
                } catch (t: Throwable) {
                    Log.w("ScreenCaptureService", "Error reading frame buffer: ${t.message}")
                    null
                } finally {
                    try { image.close() } catch (_: Throwable) {}
                }
                
                if (bitmap == null) return@setOnImageAvailableListener
                
                coroutineScope.launch {
                    try {
                        val stream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream)
                        bitmap.recycle()
                        
                        sendFrame(stream.toByteArray())
                    } catch (e: Exception) {
                        Log.e("ScreenCaptureService", "Error compressing/sending frame", e)
                        try { bitmap.recycle() } catch (_: Throwable) {}
                    }
                }
            }, handler)
            
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "PCMasterScreenCapture",
                captureWidth,
                captureHeight,
                virtualDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )
        } catch (e: Exception) {
            Log.e("ScreenCaptureService", "Failed to start capture virtual display", e)
            stopSelf()
        }
    }
    
    private suspend fun sendFrame(jpegBytes: ByteArray) {
        if (serverIp.isEmpty() || !isSendingFrame.compareAndSet(false, true)) {
            return
        }
        
        try {
            val url = URL("$serverIp/mirror/frame")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "image/jpeg")
            connection.setRequestProperty("Content-Length", jpegBytes.size.toString())
            connection.setRequestProperty("User-Agent", "PC-Master-Android-App/2.1")
            
            val authToken = ApiClient.getAuthToken()
            if (authToken.isNotEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer $authToken")
                connection.setRequestProperty("X-Auth-Token", authToken)
            }
            
            connection.connectTimeout = 2500
            connection.readTimeout = 2500
            connection.outputStream.use { os ->
                os.write(jpegBytes)
                os.flush()
            }
            
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                Log.w("ScreenCaptureService", "Frame POST response: $responseCode")
            }
            connection.disconnect()
        } catch (e: Exception) {
            Log.e("ScreenCaptureService", "Send frame network error: ${e.message}")
        } finally {
            isSendingFrame.set(false)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(android.app.Service.STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {}
        
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        try { mediaProjection?.stop() } catch (_: Exception) {}
        try { handlerThread?.quitSafely() } catch (_: Exception) {}
        try { executor.shutdown() } catch (_: Exception) {}
        
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        Log.d("ScreenCaptureService", "Screen capture service cleanly terminated")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}