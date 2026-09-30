package com.pcmaster.mobile

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureService : Service() {

    companion object {
        @Volatile
        var isRunning = false
        var captureResultCode: Int = Activity.RESULT_CANCELED
        var captureResultData: Intent? = null
        var captureServerIp: String = ""
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var serverIp = ""
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private val isSendingFrame = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        handlerThread = HandlerThread("ScreenCaptureThread").apply { start() }
        handler = Handler(handlerThread!!.looper)
        if (captureResultData != null) {
            startForegroundNotification(withType = true)
        }
    }

    private fun safeStopSelf() {
        isRunning = false
        captureResultData = null
        captureResultCode = Activity.RESULT_CANCELED
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Throwable) {}
        try { virtualDisplay?.release() } catch (_: Throwable) {}
        try { imageReader?.close() } catch (_: Throwable) {}
        try { mediaProjection?.stop() } catch (_: Throwable) {}
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra<Intent>("data")
        }

        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.w("ScreenCaptureService", "Invalid resultCode or null data, stopping service")
            safeStopSelf()
            return START_NOT_STICKY
        }

        // On Android 14+, promote foreground service with FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION NOW that consent intent is present
        startForegroundNotification(withType = true)

        captureServerIp = intent?.getStringExtra("serverIp") ?: ""
        if (captureServerIp.isEmpty()) {
            captureServerIp = PcApiClient.baseUrl
        }
        if (captureServerIp.isEmpty()) {
            val prefs = getSharedPreferences("pc_master", MODE_PRIVATE)
            val ip = prefs.getString("pc_ip", "") ?: ""
            val port = prefs.getString("pc_port", "8099") ?: "8099"
            if (ip.isNotEmpty()) {
                captureServerIp = "http://$ip:$port"
            }
        }
        if (captureServerIp.isNotEmpty()) {
            captureServerIp = if (captureServerIp.startsWith("http")) captureServerIp else "http://$captureServerIp"
            captureServerIp = captureServerIp.trimEnd('/')
        }

        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            mediaProjection = projectionManager?.getMediaProjection(resultCode, data)

            if (mediaProjection == null) {
                Log.e("ScreenCaptureService", "Failed to obtain MediaProjection")
                safeStopSelf()
                return START_NOT_STICKY
            }

            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    Log.d("ScreenCaptureService", "MediaProjection stopped by system/user")
                    safeStopSelf()
                }
            }, handler)

            startCapture()
            isRunning = true
            Log.d("ScreenCaptureService", "Screen capture successfully active -> $captureServerIp")
        } catch (e: Throwable) {
            Log.e("ScreenCaptureService", "Exception starting screen capture", e)
            safeStopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startForegroundNotification(withType: Boolean = false) {
        val channelId = "screen_capture_channel"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Screen Mirroring Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Streaming phone screen to PC Master"
                    setShowBadge(false)
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("PC Master Screen Cast")
                .setContentText("Screen mirroring to PC is active")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (withType) {
                    try {
                        startForeground(
                            1001,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                        )
                    } catch (se: Exception) {
                        Log.w("ScreenCaptureService", "Fallback startForeground without type: ${se.message}")
                        startForeground(1001, notification)
                    }
                } else {
                    startForeground(1001, notification)
                }
            } else {
                startForeground(1001, notification)
            }
        } catch (e: Exception) {
            Log.e("ScreenCaptureService", "Error starting foreground notification", e)
        }
    }

    private fun startCapture() {
        try {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
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

            // Downscale to 50% or max 720p for fast compression, low bandwidth, and zero lag
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
                var image: Image?
                try {
                    image = reader.acquireLatestImage()
                } catch (_: Throwable) {
                    return@setOnImageAvailableListener
                }
                if (image == null) return@setOnImageAvailableListener

                // If previous frame is still uploading, drop frame to prevent network queue buildup
                if (captureServerIp.isEmpty() || isSendingFrame.get()) {
                    try { image.close() } catch (_: Throwable) {}
                    return@setOnImageAvailableListener
                }

                val bitmap: Bitmap? = try {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * captureWidth

                    val paddedWidth = captureWidth + (rowPadding / pixelStride)
                    val expectedBytes = paddedWidth * captureHeight * 4

                    val bmp = Bitmap.createBitmap(
                        paddedWidth,
                        captureHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    if (buffer.remaining() >= expectedBytes) {
                        bmp.copyPixelsFromBuffer(buffer)
                    } else {
                        val fullBuf = java.nio.ByteBuffer.allocateDirect(expectedBytes)
                        fullBuf.put(buffer)
                        fullBuf.position(0)
                        bmp.copyPixelsFromBuffer(fullBuf)
                    }

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

                try {
                    if (!executor.isShutdown) {
                        executor.execute {
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
                    } else {
                        try { bitmap.recycle() } catch (_: Throwable) {}
                    }
                } catch (_: java.util.concurrent.RejectedExecutionException) {
                    try { bitmap.recycle() } catch (_: Throwable) {}
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
            safeStopSelf()
        }
    }

    private fun sendFrame(jpegBytes: ByteArray) {
        if (captureServerIp.isEmpty() || !isSendingFrame.compareAndSet(false, true)) {
            return
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL("$captureServerIp/mirror/frame")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "image/jpeg")
            connection.setRequestProperty("Content-Length", jpegBytes.size.toString())
            connection.setRequestProperty("User-Agent", "PC-Master-Android-App/2.0")
            if (PcApiClient.authToken.isNotEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer ${PcApiClient.authToken}")
                connection.setRequestProperty("X-Auth-Token", PcApiClient.authToken)
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
            // Consume response stream to permit HTTP Keep-Alive socket reuse
            try { connection.inputStream?.close() } catch (_: Throwable) {}
        } catch (e: Exception) {
            Log.e("ScreenCaptureService", "Send frame network error: ${e.message}")
        } finally {
            try { connection?.disconnect() } catch (_: Throwable) {}
            isSendingFrame.set(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        captureResultData = null
        captureResultCode = Activity.RESULT_CANCELED
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
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