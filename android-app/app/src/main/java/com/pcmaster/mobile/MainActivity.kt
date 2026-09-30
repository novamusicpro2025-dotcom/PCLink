package com.pcmaster.mobile

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.media.MediaPlayer
import android.media.MediaScannerConnection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import android.content.ClipData
import android.content.ClipboardManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private data class SavedConnection(val host: String, val port: String)

    private lateinit var prefs: SharedPreferences
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isPolling = false
    private var pendingSharedUrl: String? = null

    // Air Mouse & Clipboard Sync
    private var sensorManager: SensorManager? = null
    private var gyroSensor: Sensor? = null
    private var isAirMouseActive = false
    private var isAutoClipboardSync = false
    private var clipChangedListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var lastSyncedClipboardText = ""

    private val gyroListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (!isAirMouseActive || event == null) return
            val rawX = -event.values[1]
            val rawY = -event.values[0]
            val threshold = 0.05f
            if (abs(rawX) > threshold || abs(rawY) > threshold) {
                val speed = 14f
                PcApiClient.sendMouseMove(rawX * speed, rawY * speed)
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    // Top Bar & Connection Views
    private var statusPillHeader: View? = null
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var tvLatency: TextView
    private lateinit var ipBadgeText: TextView
    private var togglePanelBtn: View? = null
    private var connectionInputPanel: View? = null
    private lateinit var ipInput: EditText
    private lateinit var portInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var connectBtn: View
    private lateinit var autoDetectBtn: View
    private var btnSettings: View? = null
    private var btnToggleTokenVisibility: ImageView? = null
    private var isTokenVisible = false
    // Dedicated Login Screen Views (Open Screen)
    private lateinit var loginScreenView: View
    private lateinit var loginIpInput: EditText
    private lateinit var loginPortInput: EditText
    private lateinit var loginTokenInput: EditText
    private var btnLoginToggleToken: ImageView? = null
    private var isLoginTokenVisible = false
    private lateinit var cbAutoConnect: CheckBox
    private lateinit var btnLoginConnect: View
    private lateinit var tvLoginBtnText: TextView
    private lateinit var pbLoginLoading: ProgressBar
    private lateinit var btnLoginScan: View
    private lateinit var btnLoginRecent: View
    private lateinit var loginStatusDot: View
    private lateinit var tvLoginStatusMessage: TextView
    private lateinit var contentContainer: View
    private var btnLoginRemoteQuick: View? = null
    private var cardLoginUniversalRemote: View? = null
    private var btnOpenUniversalRemoteFromLogin: View? = null

    // Tab Container Views
    private lateinit var tabDashboardView: View
    private lateinit var tabTrackpadView: View
    private lateinit var tabAppsView: View
    private lateinit var tabFilesView: View
    private lateinit var tabMediaView: View
    private lateinit var bottomNavigation: BottomNavigationView

    // Tab 1 Views (Dashboard - Telemetry & Actions)
    private var tvTelemetryStatus: TextView? = null
    private var telemetryStatusDot: View? = null
    private lateinit var tvCpuLoad: TextView
    private var pbCpu: ProgressBar? = null
    private lateinit var tvCpuTemp: TextView
    private var cmvCpu: CircularMetricView? = null
    private var sparklineCpu: SparklineView? = null

    private lateinit var tvRamLoad: TextView
    private var pbRam: ProgressBar? = null
    private lateinit var tvRamUsed: TextView
    private var cmvRam: CircularMetricView? = null
    private var sparklineRam: SparklineView? = null

    private lateinit var tvGpuLoad: TextView
    private var pbGpu: ProgressBar? = null
    private lateinit var tvGpuTemp: TextView
    private var cmvGpu: CircularMetricView? = null
    private var sparklineGpu: SparklineView? = null

    private lateinit var tvBatteryPct: TextView
    private var pbBattery: ProgressBar? = null
    private lateinit var tvBatteryStatus: TextView
    private var cmvBattery: CircularMetricView? = null
    private var sparklineBattery: SparklineView? = null

    private var tvCaffeineStatus: TextView? = null
    private var tvAntiIdleStatus: TextView? = null
    private var isCaffeineActive = false
    private var isAntiIdleActive = false
    private var btnUnlockPc: View? = null
    private var tvUnlockStatus: TextView? = null
    private var btnAntiIdle: View? = null
    private var btnCustomizeActions: View? = null

    // Tab 2 Views (Trackpad)
    private lateinit var nativeTrackpad: TrackpadView
    private lateinit var sbVolume: SeekBar
    private lateinit var tvVolumeVal: TextView
    private lateinit var sbBrightness: SeekBar
    private lateinit var tvBrightnessVal: TextView
    private lateinit var etSendText: EditText
    private lateinit var btnSendText: Button

    // Tab 3 Views (Apps)
    private lateinit var lvProcesses: ListView
    private lateinit var btnRefreshProcesses: Button
    private val processList = mutableListOf<ProcessItem>()
    private lateinit var processAdapter: ArrayAdapter<ProcessItem>

    // Tab 4 Views (Files)
    private lateinit var btnUploadFile: View
    private lateinit var pbUpload: ProgressBar
    private lateinit var btnRefreshFiles: Button
    private lateinit var lvSharedFiles: ListView
    private var btnFolderBack: Button? = null
    private var tvCurrentFolderPath: TextView? = null
    private val fileList = mutableListOf<FileItem>()
    private var currentFolderPath = ""
    private var activeFullscreenDialog: Dialog? = null

    // Tab 5 Views (Media & Screen)
    private lateinit var tvMediaTitle: TextView
    private lateinit var btnMediaPrev: Button
    private lateinit var btnMediaPlayPause: Button
    private lateinit var btnMediaNext: Button
    private lateinit var ivScreenshot: ImageView
    private lateinit var btnRefreshScreenshot: Button
    private lateinit var btnToggleLiveStream: Button
    private lateinit var tvLiveStreamBadge: TextView
    private var tvScreenEmptyHint: TextView? = null
    private var btnFullscreenScreen: ImageButton? = null
    private lateinit var btnToggleMirror: Button
    private lateinit var tvMirrorStatus: TextView
    private var isMirroringRunning = false
    private var btnStreamQuality: Button? = null

    // Tab 5 Live Screen Quick Controls & Touch
    private var tvTouchStatusHint: TextView? = null
    private var btnQuickWin: Button? = null
    private var btnQuickCopy: Button? = null
    private var btnQuickPaste: Button? = null
    private var btnQuickScrollUp: Button? = null
    private var btnQuickScrollDown: Button? = null
    private var btnQuickBw: Button? = null
    private var etQuickSendText: EditText? = null
    private var btnQuickSendText: Button? = null
    private var btnQuickBackspace: Button? = null
    private var btnQuickEnter: Button? = null
    private var btnLiveLock: Button? = null
    private var btnLiveUnlock: Button? = null
    private var btnLiveAntiIdle: Button? = null
    private var isBwThemeActive = false

    // PC Health & Diagnostics Views
    private var cardPcHealthCheck: View? = null
    private var tvHealthSummaryBrief: TextView? = null
    private var badgeHealthScore: View? = null
    private var dotHealthStatus: View? = null
    private var tvHealthScoreBadge: TextView? = null
    private var tvHealthChipCpu: TextView? = null
    private var tvHealthChipRam: TextView? = null
    private var tvHealthChipDisk: TextView? = null
    private var tvHealthChipLan: TextView? = null

    // Dashboard Quick Hub: Notification Sync & Screen Mirror
    private var btnQuickNotifSync: View? = null
    private var tvQuickNotifStatus: TextView? = null
    private var btnQuickScreenMirror: View? = null
    private var tvQuickMirrorStatus: TextView? = null

    // PC Live Screen Streaming Loop
    private var isLiveStreaming = false
    private val liveStreamHandler = Handler(Looper.getMainLooper())
    private val liveStreamRunnable = Runnable { fetchNextLiveFrame() }

    // Android 13+ Notification Permission Launcher
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (!isGranted) {
            Log.w("MainActivity", "POST_NOTIFICATIONS permission not granted")
        }
    }

    // MediaProjection launcher for screen mirroring
    private var mediaProjectionManager: MediaProjectionManager? = null
    @SuppressLint("SetTextI18n")
    private val startMirroringLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val resultData = result.data
        if (result.resultCode == RESULT_OK && resultData != null) {
            try {
                ScreenCaptureService.captureResultCode = result.resultCode
                ScreenCaptureService.captureResultData = resultData
                val intent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra("resultCode", result.resultCode)
                    putExtra("data", resultData)
                    putExtra("serverIp", PcApiClient.baseUrl)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                isMirroringRunning = true
                @SuppressLint("SetTextI18n")
                btnToggleMirror.text = "STOP"
                btnToggleMirror.backgroundTintList = ColorStateList.valueOf("#FF453A".toColorInt())
                tvMirrorStatus.text = "🟢 Active Screen Cast to PC Hub"
                updateQuickHubStatus()
                Toast.makeText(this, "Screen Cast Started to PC!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to start ScreenCaptureService", e)
                Toast.makeText(this, "Screen Cast failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Screen Cast permission cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    // File Picker Launcher
    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            handleFileUpload(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("pc_master", MODE_PRIVATE)
        mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        initViews()
        setupTopBar()
        setupLoginScreen()
        setupBottomNavigation()
        setupTabDashboard()
        setupTabTrackpad()
        setupTabApps()
        setupTabFiles()
        setupTabMedia()

        // Handle System Back Button gracefully (navigate folders, close modals, switch to dashboard)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // If on dedicated login screen, exit app
                if (::loginScreenView.isInitialized && loginScreenView.isVisible) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                    return
                }

                // 1. If currently inside a folder/drive in Files tab, navigate up
                if (tabFilesView.isVisible && currentFolderPath.isNotEmpty()) {
                    val parent = File(currentFolderPath).parent
                    if (parent == null || currentFolderPath.matches(Regex("^[a-zA-Z]:[/\\\\]?$"))) {
                        loadSharedFiles("")
                    } else {
                        loadSharedFiles(parent)
                    }
                    return
                }

                // 2. If fullscreen live stream modal is showing, dismiss it
                if (activeFullscreenDialog?.isShowing == true) {
                    activeFullscreenDialog?.dismiss()
                    activeFullscreenDialog = null
                    return
                }

                // 3. If on a secondary tab, return to Dashboard tab
                if (tabDashboardView.visibility != View.VISIBLE) {
                    bottomNavigation.selectedItemId = R.id.menu_dashboard
                    return
                }

                // 4. Default: exit/minimize app
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })

        // Restore saved B&W theme
        isBwThemeActive = prefs.getBoolean("pref_bw_theme", false)
        if (isBwThemeActive) {
            applyBwTheme(true)
        }

        // Restore saved settings (default auto-connect is false so the app opens directly to the Login Screen with IP & Port)
        val savedIp = prefs.getString("pc_ip", "") ?: ""
        val savedPort = prefs.getString("pc_port", "8099") ?: "8099"
        val savedToken = prefs.getString("pc_token", "") ?: ""
        val autoConnectEnabled = prefs.getBoolean("pref_auto_connect", false)

        loginIpInput.setText(savedIp)
        loginPortInput.setText(savedPort)
        loginTokenInput.setText(savedToken)
        cbAutoConnect.isChecked = autoConnectEnabled

        ipInput.setText(savedIp)
        portInput.setText(savedPort)
        tokenInput.setText(savedToken)

        PcApiClient.baseUrl = PcApiClient.formatBaseUrl(savedIp, savedPort)
        PcApiClient.authToken = savedToken

        // If auto-connect is enabled, attempt silent auto-connection; otherwise present open login screen
        if (autoConnectEnabled && savedIp.isNotEmpty()) {
            updateLoginStatus("Auto-connecting to $savedIp:$savedPort...", isProgress = true, dotColor = "#FF9F0A")
            performConnect(savedIp, savedPort, savedToken, isSilent = true)
        } else {
            updateLoginStatus("Ready to connect. Enter your PC IP and Port.", isProgress = false, dotColor = "#8A9BB5")
            showLoginScreen()
        }
        handleSharedUrl(intent)
        handleIncomingRing(intent)
        val ringFilter = IntentFilter("com.pcmaster.mobile.RING_PHONE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(ringBroadcastReceiver, ringFilter, RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(ringBroadcastReceiver, ringFilter)
        }
    }

    private val ringBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.pcmaster.mobile.RING_PHONE") {
                showToast("🔔 Ringing phone from PC...")
                PcApiClient.findMyPhone(this@MainActivity)
            }
        }
    }

    private fun handleIncomingRing(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val extraAction = intent.getStringExtra("action")
        if (action == "com.pcmaster.mobile.RING_PHONE" || extraAction == "ring") {
            showToast("🔔 Ringing phone from PC...")
            PcApiClient.findMyPhone(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedUrl(intent)
        handleIncomingRing(intent)
    }

    private fun initViews() {
        // Dedicated Login Screen Views
        loginScreenView = findViewById(R.id.loginScreenView)
        loginIpInput = findViewById(R.id.loginIpInput)
        loginPortInput = findViewById(R.id.loginPortInput)
        loginTokenInput = findViewById(R.id.loginTokenInput)
        btnLoginToggleToken = findViewById(R.id.btnLoginToggleToken)
        cbAutoConnect = findViewById(R.id.cbAutoConnect)
        btnLoginConnect = findViewById(R.id.btnLoginConnect)
        tvLoginBtnText = findViewById(R.id.tvLoginBtnText)
        pbLoginLoading = findViewById(R.id.pbLoginLoading)
        btnLoginScan = findViewById(R.id.btnLoginScan)
        btnLoginRecent = findViewById(R.id.btnLoginRecent)
        loginStatusDot = findViewById(R.id.loginStatusDot)
        tvLoginStatusMessage = findViewById(R.id.tvLoginStatusMessage)
        contentContainer = findViewById(R.id.contentContainer)
        btnLoginRemoteQuick = findViewById(R.id.btnLoginRemoteQuick)
        cardLoginUniversalRemote = findViewById(R.id.cardLoginUniversalRemote)
        btnOpenUniversalRemoteFromLogin = findViewById(R.id.btnOpenUniversalRemoteFromLogin)

        // Top Bar & Connection Views
        statusPillHeader = findViewById(R.id.statusPillHeader)
        statusDot = findViewById(R.id.statusDot)
        statusText = findViewById(R.id.statusText)
        tvLatency = findViewById(R.id.tvLatency)
        ipBadgeText = findViewById(R.id.ipBadgeText)
        togglePanelBtn = findViewById(R.id.togglePanelBtn)
        connectionInputPanel = findViewById(R.id.connectionInputPanel)
        ipInput = findViewById(R.id.ipInput)
        portInput = findViewById(R.id.portInput)
        tokenInput = findViewById(R.id.tokenInput)
        connectBtn = findViewById(R.id.connectBtn)
        autoDetectBtn = findViewById(R.id.autoDetectBtn)
        btnSettings = findViewById(R.id.btnSettings)
        btnToggleTokenVisibility = findViewById(R.id.btnToggleTokenVisibility)

        // Tabs
        tabDashboardView = findViewById(R.id.tabDashboardView)
        tabTrackpadView = findViewById(R.id.tabTrackpadView)
        tabAppsView = findViewById(R.id.tabAppsView)
        tabFilesView = findViewById(R.id.tabFilesView)
        tabMediaView = findViewById(R.id.tabMediaView)
        bottomNavigation = findViewById(R.id.bottomNavigation)

        // Dashboard Telemetry Views
        tvTelemetryStatus = findViewById(R.id.tvTelemetryStatus)
        telemetryStatusDot = findViewById(R.id.telemetryStatusDot)
        tvCpuLoad = findViewById(R.id.tvCpuLoad)
        pbCpu = findViewById(R.id.pbCpu)
        tvCpuTemp = findViewById(R.id.tvCpuTemp)
        cmvCpu = findViewById(R.id.cmvCpu)
        sparklineCpu = findViewById(R.id.sparklineCpu)

        tvRamLoad = findViewById(R.id.tvRamLoad)
        pbRam = findViewById(R.id.pbRam)
        tvRamUsed = findViewById(R.id.tvRamUsed)
        cmvRam = findViewById(R.id.cmvRam)
        sparklineRam = findViewById(R.id.sparklineRam)

        tvGpuLoad = findViewById(R.id.tvGpuLoad)
        pbGpu = findViewById(R.id.pbGpu)
        tvGpuTemp = findViewById(R.id.tvGpuTemp)
        cmvGpu = findViewById(R.id.cmvGpu)
        sparklineGpu = findViewById(R.id.sparklineGpu)

        tvBatteryPct = findViewById(R.id.tvBatteryPct)
        pbBattery = findViewById(R.id.pbBattery)
        tvBatteryStatus = findViewById(R.id.tvBatteryStatus)
        cmvBattery = findViewById(R.id.cmvBattery)
        sparklineBattery = findViewById(R.id.sparklineBattery)

        tvAntiIdleStatus = findViewById(R.id.tvAntiIdleStatus)
        tvCaffeineStatus = tvAntiIdleStatus
        btnAntiIdle = findViewById(R.id.btnAntiIdle)
        btnUnlockPc = findViewById(R.id.btnUnlockPc)
        tvUnlockStatus = findViewById(R.id.tvUnlockStatus)
        btnCustomizeActions = findViewById(R.id.btnCustomizeActions)

        // PC Health Hero Card
        cardPcHealthCheck = findViewById(R.id.cardPcHealthCheck)
        cardPcHealthCheck?.setOnClickListener { showPcHealthDiagnosticsDialog() }
        tvHealthSummaryBrief = findViewById(R.id.tvHealthSummaryBrief)
        badgeHealthScore = findViewById(R.id.badgeHealthScore)
        dotHealthStatus = findViewById(R.id.dotHealthStatus)
        tvHealthScoreBadge = findViewById(R.id.tvHealthScoreBadge)
        tvHealthChipCpu = findViewById(R.id.tvHealthChipCpu)
        tvHealthChipRam = findViewById(R.id.tvHealthChipRam)
        tvHealthChipDisk = findViewById(R.id.tvHealthChipDisk)
        tvHealthChipLan = findViewById(R.id.tvHealthChipLan)

        // Trackpad Elements
        nativeTrackpad = findViewById(R.id.nativeTrackpad)
        sbVolume = findViewById(R.id.sbVolume)
        tvVolumeVal = findViewById(R.id.tvVolumeVal)
        sbBrightness = findViewById(R.id.sbBrightness)
        tvBrightnessVal = findViewById(R.id.tvBrightnessVal)
        etSendText = findViewById(R.id.etSendText)
        btnSendText = findViewById(R.id.btnSendText)

        // Apps Elements
        lvProcesses = findViewById(R.id.lvProcesses)
        btnRefreshProcesses = findViewById(R.id.btnRefreshProcesses)

        // Files Elements
        btnUploadFile = findViewById(R.id.btnUploadFile)
        pbUpload = findViewById(R.id.pbUpload)
        btnRefreshFiles = findViewById(R.id.btnRefreshFiles)
        lvSharedFiles = findViewById(R.id.lvSharedFiles)
        btnFolderBack = findViewById(R.id.btnFolderBack)
        tvCurrentFolderPath = findViewById(R.id.tvCurrentFolderPath)

        // Media Elements
        tvMediaTitle = findViewById(R.id.tvMediaTitle)
        btnMediaPrev = findViewById(R.id.btnMediaPrev)
        btnMediaPlayPause = findViewById(R.id.btnMediaPlayPause)
        btnMediaNext = findViewById(R.id.btnMediaNext)
        ivScreenshot = findViewById(R.id.ivScreenshot)
        btnRefreshScreenshot = findViewById(R.id.btnRefreshScreenshot)
        btnToggleLiveStream = findViewById(R.id.btnToggleLiveStream)
        tvLiveStreamBadge = findViewById(R.id.tvLiveStreamBadge)
        tvScreenEmptyHint = findViewById(R.id.tvScreenEmptyHint)
        btnFullscreenScreen = findViewById(R.id.btnFullscreenScreen)
        btnToggleMirror = findViewById(R.id.btnToggleMirror)
        tvMirrorStatus = findViewById(R.id.tvMirrorStatus)
        btnStreamQuality = findViewById(R.id.btnStreamQuality)

        // Live Screen Quick Controls & Touch
        tvTouchStatusHint = findViewById(R.id.tvTouchStatusHint)
        btnQuickWin = findViewById(R.id.btnQuickWin)
        btnQuickCopy = findViewById(R.id.btnQuickCopy)
        btnQuickPaste = findViewById(R.id.btnQuickPaste)
        btnQuickScrollUp = findViewById(R.id.btnQuickScrollUp)
        btnQuickScrollDown = findViewById(R.id.btnQuickScrollDown)
        btnQuickBw = findViewById(R.id.btnQuickBw)
        etQuickSendText = findViewById(R.id.etQuickSendText)
        btnQuickSendText = findViewById(R.id.btnQuickSendText)
        btnQuickBackspace = findViewById(R.id.btnQuickBackspace)
        btnQuickEnter = findViewById(R.id.btnQuickEnter)
        btnLiveLock = findViewById(R.id.btnLiveLock)
        btnLiveUnlock = findViewById(R.id.btnLiveUnlock)
        btnLiveAntiIdle = findViewById(R.id.btnLiveAntiIdle)
    }

    private fun setupTopBar() {
        togglePanelBtn?.setOnClickListener {
            connectionInputPanel?.let { panel ->
                panel.isVisible = !panel.isVisible
            }
        }

        connectBtn.setOnClickListener {
            val ip = ipInput.text.toString().trim()
            val port = portInput.text.toString().trim().ifEmpty { "8099" }
            val token = tokenInput.text.toString().trim()
            performConnect(ip, port, token)
        }

        autoDetectBtn.setOnClickListener {
            Toast.makeText(this, "Scanning for PCLink Hub on Wi-Fi...", Toast.LENGTH_SHORT).show()
            PcApiClient.discoverPcHub { ip, port ->
                ipInput.setText(ip)
                portInput.setText(port.toString())
                performConnect(ip, port.toString(), tokenInput.text.toString().trim())
                Toast.makeText(this, "Found PCLink Hub at $ip:$port!", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View?>(R.id.btnRecent)?.setOnClickListener {
            showRecentConnections { connection ->
                ipInput.setText(connection.host)
                portInput.setText(connection.port)
                performConnect(connection.host, connection.port, tokenInput.text.toString().trim())
            }
        }

        btnToggleTokenVisibility?.setOnClickListener {
            isTokenVisible = !isTokenVisible
            if (isTokenVisible) {
                tokenInput.transformationMethod = HideReturnsTransformationMethod.getInstance()
                btnToggleTokenVisibility?.setColorFilter("#00D2FF".toColorInt())
            } else {
                tokenInput.transformationMethod = PasswordTransformationMethod.getInstance()
                btnToggleTokenVisibility?.setColorFilter("#4A5F80".toColorInt())
            }
            tokenInput.setSelection(tokenInput.text.length)
        }

        btnSettings?.setOnClickListener { showSettingsMenuDialog() }
        findViewById<View?>(R.id.btnMoreMenu)?.setOnClickListener { showSettingsMenuDialog() }

        // Click status pill to disconnect / switch PC
        statusPillHeader?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("PCLink Connection")
                .setMessage("Server: ${PcApiClient.baseUrl}\nStatus: ${statusText.text}\n\nWould you like to disconnect or switch to another PC?")
                .setPositiveButton("Disconnect & Switch PC") { _, _ ->
                    disconnectAndShowLogin()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupLoginScreen() {
        btnLoginToggleToken?.setOnClickListener {
            isLoginTokenVisible = !isLoginTokenVisible
            if (isLoginTokenVisible) {
                loginTokenInput.transformationMethod = HideReturnsTransformationMethod.getInstance()
                btnLoginToggleToken?.setColorFilter("#00D2FF".toColorInt())
            } else {
                loginTokenInput.transformationMethod = PasswordTransformationMethod.getInstance()
                btnLoginToggleToken?.setColorFilter("#4A5F80".toColorInt())
            }
            loginTokenInput.setSelection(loginTokenInput.text.length)
        }

        cbAutoConnect.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("pref_auto_connect", isChecked) }
        }

        findViewById<View>(R.id.tvAutoConnectLabel)?.setOnClickListener {
            cbAutoConnect.isChecked = !cbAutoConnect.isChecked
        }

        btnLoginScan.setOnClickListener {
            updateLoginStatus("Scanning Wi-Fi for PCLink Hub (Port 8091)...", isProgress = true, dotColor = "#00D2FF")
            Toast.makeText(this, "Scanning for PCLink Hub on Wi-Fi...", Toast.LENGTH_SHORT).show()
            PcApiClient.discoverPcHub { ip, port ->
                loginIpInput.setText(ip)
                loginPortInput.setText(port.toString())
                ipInput.setText(ip)
                portInput.setText(port.toString())
                updateLoginStatus("Found PCLink Hub at $ip:$port! Tap Login to connect.", isProgress = false, dotColor = "#00FF88")
                Toast.makeText(this, "Found PCLink Hub at $ip:$port!", Toast.LENGTH_SHORT).show()
            }
        }

        btnLoginRecent.setOnClickListener {
            showRecentConnections { connection ->
                loginIpInput.setText(connection.host)
                loginPortInput.setText(connection.port)
                ipInput.setText(connection.host)
                portInput.setText(connection.port)
                updateLoginStatus("Selected ${connection.host}:${connection.port}. Tap Login to connect.", dotColor = "#00D2FF")
            }
        }

        btnLoginConnect.setOnClickListener {
            val ip = loginIpInput.text.toString().trim()
            val port = loginPortInput.text.toString().trim().ifEmpty { "8099" }
            val token = loginTokenInput.text.toString().trim()
            prefs.edit { putBoolean("pref_auto_connect", cbAutoConnect.isChecked) }
            performConnect(ip, port, token, isSilent = false)
        }

        // Direct Offline Universal Remote access without connecting to PC
        val openUniversalRemoteAction = View.OnClickListener {
            UniversalRemoteDialog(this@MainActivity).show()
        }
        btnLoginRemoteQuick?.setOnClickListener(openUniversalRemoteAction)
        cardLoginUniversalRemote?.setOnClickListener(openUniversalRemoteAction)
        btnOpenUniversalRemoteFromLogin?.setOnClickListener(openUniversalRemoteAction)

        findViewById<View?>(R.id.btnLoginAboutApp)?.setOnClickListener {
            showAboutAppDialog()
        }

        findViewById<View?>(R.id.btnLoginPrivacyPolicy)?.setOnClickListener {
            openPrivacyPolicyUrl()
        }
    }

    private fun updateLoginStatus(statusMsg: String, isProgress: Boolean = false, dotColor: String = "#8A9BB5") {
        tvLoginStatusMessage.text = statusMsg
        loginStatusDot.backgroundTintList = ColorStateList.valueOf(dotColor.toColorInt())
        pbLoginLoading.visibility = if (isProgress) View.VISIBLE else View.GONE
        tvLoginBtnText.text = if (isProgress) "CONNECTING..." else "LOGIN & CONNECT"
    }

    private fun showDashboard() {
        loginScreenView.visibility = View.GONE
        contentContainer.visibility = View.VISIBLE
        bottomNavigation.visibility = View.VISIBLE
    }

    private fun handleSharedUrl(sharedIntent: Intent?) {
        if (sharedIntent?.action != Intent.ACTION_SEND || sharedIntent.type?.startsWith("text/") != true) return
        val sharedText = sharedIntent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val sharedUrl = Regex("https?://\\S+", RegexOption.IGNORE_CASE).find(sharedText)?.value ?: return
        pendingSharedUrl = sharedUrl
        if (statusText.text == getString(R.string.connected)) {
            PcApiClient.openUrlOnPc(sharedUrl) { opened ->
                showToast(if (opened) "Opened shared link on PC" else "Could not open shared link on PC")
                if (opened) pendingSharedUrl = null
            }
        } else {
            updateLoginStatus("Link ready. Connect to open it on your PC browser.", dotColor = "#00D2FF")
            showToast("Link is ready to open on your PC after connecting")
        }
    }

    private fun showLoginScreen() {
        loginScreenView.visibility = View.VISIBLE
        contentContainer.visibility = View.GONE
        bottomNavigation.visibility = View.GONE
    }

    private fun disconnectAndShowLogin() {
        stopPolling()
        stopLiveStreaming()

        // Turn off auto-connect so the user can easily reconfigure IP/Port without being interrupted
        prefs.edit { putBoolean("pref_auto_connect", false) }
        cbAutoConnect.isChecked = false

        statusText.text = getString(R.string.disconnected)
        statusText.setTextColor("#FF453A".toColorInt())
        statusDot.backgroundTintList = ColorStateList.valueOf("#FF453A".toColorInt())
        statusPillHeader?.setBackgroundResource(R.drawable.status_pill_disconnected)
        tvLatency.visibility = View.GONE

        updateLoginStatus("Disconnected from PC. Enter IP & Port to reconnect.", isProgress = false, dotColor = "#FF9F0A")
        showLoginScreen()
        Toast.makeText(this, "Disconnected from PCLink", Toast.LENGTH_SHORT).show()
    }

    private fun showRecentConnections(onSelected: (SavedConnection) -> Unit) {
        val connections = loadRecentConnections()
        if (connections.isEmpty()) {
            Toast.makeText(this, "No saved PC connections yet", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Recent PCLink Hubs")
            .setItems(connections.map { "${it.host}:${it.port}" }.toTypedArray()) { _, which ->
                onSelected(connections[which])
            }
            .show()
    }

    private fun loadRecentConnections(): List<SavedConnection> = try {
        val storedConnections = JSONArray(prefs.getString("pref_recent_connections", "[]"))
        buildList {
            for (index in 0 until storedConnections.length()) {
                val connection = storedConnections.optJSONObject(index) ?: continue
                val host = connection.optString("host").trim()
                val port = connection.optString("port", "8099").trim()
                if (host.isNotEmpty() && port.isNotEmpty()) add(SavedConnection(host, port))
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun rememberConnection(host: String, port: String) {
        val connections = loadRecentConnections()
            .filterNot { it.host.equals(host, ignoreCase = true) && it.port == port }
            .toMutableList()
        connections.add(0, SavedConnection(host, port))

        val storedConnections = JSONArray()
        connections.take(5).forEach { connection ->
            storedConnections.put(JSONObject().apply {
                put("host", connection.host)
                put("port", connection.port)
            })
        }
        prefs.edit { putString("pref_recent_connections", storedConnections.toString()) }
    }

    private fun parseConnectionDetails(rawAddress: String, rawPort: String, rawToken: String): Triple<String, String, String>? {
        val pairingParts = rawAddress.trim().split("|", limit = 2)
        var address = pairingParts.firstOrNull().orEmpty()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore('/')
            .trim()
        val embeddedPortIndex = address.lastIndexOf(':')
        val embeddedPort = if (embeddedPortIndex > 0 && address.indexOf(':') == embeddedPortIndex) {
            address.substring(embeddedPortIndex + 1)
        } else {
            ""
        }
        if (embeddedPort.isNotEmpty()) address = address.substring(0, embeddedPortIndex)

        val port = embeddedPort.ifEmpty { rawPort.trim().ifEmpty { "8099" } }
        val token = rawToken.trim().ifEmpty { pairingParts.getOrNull(1)?.trim().orEmpty() }
        if (address.isEmpty() || port.toIntOrNull()?.takeIf { it in 1..65535 } == null) return null

        return Triple(address, port, token)
    }

    private fun applyBwFilterToDialog(dialog: Dialog) {
        if (isBwThemeActive) {
            val cm = ColorMatrix().apply { setSaturation(0f) }
            val filter = ColorMatrixColorFilter(cm)
            val paint = Paint().apply { colorFilter = filter }
            dialog.window?.decorView?.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showSettingsMenuDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_settings_menu)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width = (resources.displayMetrics.widthPixels * 0.94).toInt()
        val height = (resources.displayMetrics.heightPixels * 0.88).toInt()
        dialog.window?.setLayout(width, height)

        val tvStatus = dialog.findViewById<TextView>(R.id.tvSettingsConnectionStatus)
        val statusDotView = dialog.findViewById<View>(R.id.settingsStatusDot)
        val tvSecurityBadge = dialog.findViewById<TextView>(R.id.tvSettingsSecurityBadge)

        val activeIp = PcApiClient.baseUrl.ifEmpty { "No PC Linked" }
        @SuppressLint("SetTextI18n")
        tvStatus?.text = "Status: ${statusText.text} • $activeIp"
        statusDotView?.backgroundTintList = statusDot.backgroundTintList
        if (PcApiClient.authToken.isNotEmpty()) {
            tvSecurityBadge?.text = "🔒 Encrypted"
            tvSecurityBadge?.setTextColor("#00FF88".toColorInt())
        } else {
            tvSecurityBadge?.text = "🔓 Open LAN"
            tvSecurityBadge?.setTextColor("#00D2FF".toColorInt())
        }

        val btnHealthCheck = dialog.findViewById<View>(R.id.btnMenuHealthCheck)
        btnHealthCheck?.setOnClickListener {
            dialog.dismiss()
            showPcHealthDiagnosticsDialog()
        }

        val btnHowToUse = dialog.findViewById<View>(R.id.btnMenuHowToUse)
        val btnAboutApp = dialog.findViewById<View>(R.id.btnMenuAboutApp)
        val btnPrivacyPolicy = dialog.findViewById<View>(R.id.btnMenuPrivacyPolicy)
        val btnThemeToggle = dialog.findViewById<View>(R.id.btnMenuThemeToggle)
        val btnClose = dialog.findViewById<Button>(R.id.btnMenuClose)
        val btnDisconnect = dialog.findViewById<View>(R.id.btnMenuDisconnect)

        btnDisconnect?.setOnClickListener {
            dialog.dismiss()
            disconnectAndShowLogin()
        }

        val tvThemeIcon = dialog.findViewById<TextView>(R.id.tvMenuThemeIcon)
        val tvThemeTitle = dialog.findViewById<TextView>(R.id.tvMenuThemeTitle)
        val tvThemeSub = dialog.findViewById<TextView>(R.id.tvMenuThemeSub)
        val tvThemeBadge = dialog.findViewById<TextView>(R.id.tvMenuThemeBadge)

        val updateThemeLabels = {
            if (isBwThemeActive) {
                tvThemeIcon?.text = "🎨"
                tvThemeTitle?.text = "Switch to Color Theme"
                tvThemeSub?.text = "Currently in high-contrast Black & White mode"
                tvThemeBadge?.text = "ACTIVE"
                tvThemeBadge?.setTextColor("#00FF88".toColorInt())
            } else {
                tvThemeIcon?.text = "🌓"
                tvThemeTitle?.text = "Black & White Theme"
                tvThemeSub?.text = "Switch between high-contrast monochrome and color"
                tvThemeBadge?.text = "TOGGLE"
                tvThemeBadge?.setTextColor("#00D2FF".toColorInt())
            }
        }
        updateThemeLabels()

        val btnYouTube = dialog.findViewById<View>(R.id.btnMenuYouTube)
        val btnAppNovaMusic = dialog.findViewById<View>(R.id.btnMenuAppNovaMusic)
        val btnAppMathRush = dialog.findViewById<View>(R.id.btnMenuAppMathRush)
        val btnContactUs = dialog.findViewById<View>(R.id.btnMenuContactUs)

        btnHowToUse?.setOnClickListener {
            dialog.dismiss()
            showHowToUseDialog()
        }

        btnAboutApp?.setOnClickListener {
            dialog.dismiss()
            showAboutAppDialog()
        }

        btnPrivacyPolicy?.setOnClickListener {
            dialog.dismiss()
            showPrivacyPolicyDialog()
        }

        btnThemeToggle?.setOnClickListener {
            toggleBwTheme()
            updateThemeLabels()
            applyBwFilterToDialog(dialog)
        }

        btnYouTube?.setOnClickListener {
            openUrl("https://www.youtube.com/@BeyondEdge1995")
        }

        btnAppNovaMusic?.setOnClickListener {
            openPlayStoreApp("com.novemusic.pro", "https://play.google.com/store/apps/details?id=com.novemusic.pro")
        }

        btnAppMathRush?.setOnClickListener {
            openPlayStoreApp("com.mathrush.official", "https://play.google.com/store/apps/details?id=com.mathrush.official")
        }

        btnContactUs?.setOnClickListener {
            sendContactEmail()
        }

        btnClose?.setOnClickListener {
            dialog.dismiss()
        }

        applyBwFilterToDialog(dialog)
        dialog.show()
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open link: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPlayStoreApp(packageName: String, webUrl: String) {
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(marketIntent)
        } catch (_: Exception) {
            openUrl(webUrl)
        }
    }

    private fun sendContactEmail() {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = "mailto:novamusicpro2025@gmail.com".toUri()
                putExtra(Intent.EXTRA_SUBJECT, "PCLink - Bug Report / Feature Request")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Hello PCLink Team,\n\n[Describe your bug, feedback, or feature request here]\n\nApp Version: 2.4.0 Pro\nDevice: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})\n"
                )
            }
            startActivity(Intent.createChooser(intent, "Send Email via"))
        } catch (_: Exception) {
            Toast.makeText(this, "Contact Email: novamusicpro2025@gmail.com", Toast.LENGTH_LONG).show()
        }
    }

    private fun showHowToUseDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_how_to_use)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width = (resources.displayMetrics.widthPixels * 0.94).toInt()
        val height = (resources.displayMetrics.heightPixels * 0.88).toInt()
        dialog.window?.setLayout(width, height)

        dialog.findViewById<Button>(R.id.btnHowToUseClose)?.setOnClickListener {
            dialog.dismiss()
        }

        applyBwFilterToDialog(dialog)
        dialog.show()
    }

    @SuppressLint("SetTextI18n")
    private fun showPcHealthDiagnosticsDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_pc_health_check)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width = (resources.displayMetrics.widthPixels * 0.94).toInt()
        val height = (resources.displayMetrics.heightPixels * 0.88).toInt()
        dialog.window?.setLayout(width, height)

        val pbScanning = dialog.findViewById<ProgressBar>(R.id.pbDiagScanning)
        val tvScore = dialog.findViewById<TextView>(R.id.tvDiagScoreText)
        val tvGrade = dialog.findViewById<TextView>(R.id.tvDiagGradeText)
        val cmvHealth = dialog.findViewById<CircularMetricView>(R.id.cmvDiagHealth)
        val tvSummary = dialog.findViewById<TextView>(R.id.tvDiagSummaryDesc)
        val tvHostSubtitle = dialog.findViewById<TextView>(R.id.tvDiagHostSubtitle)

        // CPU
        val tvCpuStatus = dialog.findViewById<TextView>(R.id.tvDiagCpuStatusPill)
        val tvCpuLoad = dialog.findViewById<TextView>(R.id.tvDiagCpuLoadVal)
        val tvCpuTemp = dialog.findViewById<TextView>(R.id.tvDiagCpuTempVal)
        val tvCpuThrottle = dialog.findViewById<TextView>(R.id.tvDiagCpuThrottleVal)

        // RAM
        val tvRamStatus = dialog.findViewById<TextView>(R.id.tvDiagRamStatusPill)
        val tvRamUsed = dialog.findViewById<TextView>(R.id.tvDiagRamUsedVal)
        val tvRamFree = dialog.findViewById<TextView>(R.id.tvDiagRamFreeVal)

        // Disk
        val tvDiskStatus = dialog.findViewById<TextView>(R.id.tvDiagDiskStatusPill)
        val tvDiskUsed = dialog.findViewById<TextView>(R.id.tvDiagDiskUsedVal)
        val tvDiskFree = dialog.findViewById<TextView>(R.id.tvDiagDiskFreeVal)

        // LAN
        val tvLanStatus = dialog.findViewById<TextView>(R.id.tvDiagLanStatusPill)
        val tvLanPing = dialog.findViewById<TextView>(R.id.tvDiagLanPingVal)
        val tvLanThroughput = dialog.findViewById<TextView>(R.id.tvDiagLanThroughputVal)

        // Power
        val tvPowerStatus = dialog.findViewById<TextView>(R.id.tvDiagPowerStatusPill)
        val tvUptime = dialog.findViewById<TextView>(R.id.tvDiagUptimeVal)
        val tvHost = dialog.findViewById<TextView>(R.id.tvDiagHostVal)

        // Issues
        val containerIssues = dialog.findViewById<View>(R.id.containerDiagIssues)
        val tvIssuesTitle = dialog.findViewById<TextView>(R.id.tvDiagIssuesTitle)
        val tvIssuesDesc = dialog.findViewById<TextView>(R.id.tvDiagIssuesDesc)

        val btnRefresh = dialog.findViewById<Button>(R.id.btnDiagRefresh)
        val btnDone = dialog.findViewById<Button>(R.id.btnDiagDone)
        val btnClose = dialog.findViewById<ImageButton>(R.id.btnDiagClose)

        @SuppressLint("SetTextI18n")
        fun bindReport(report: PcHealthReport) {
            tvScore.text = "${report.overallScore}%"
            try { tvScore.setTextColor(report.gradeColor.toColorInt()) } catch (_: Exception) {}
            tvGrade.text = report.healthGrade
            try { tvGrade.setTextColor(report.gradeColor.toColorInt()) } catch (_: Exception) {}
            cmvHealth.setProgress(report.overallScore.toFloat())
            tvSummary.text = report.summary
            tvHostSubtitle.text = "${report.hostname} • ${report.osInfo}"

            // CPU
            tvCpuStatus.text = report.cpuStatus
            try {
                tvCpuStatus.setTextColor((if (report.cpuStatus == "OPTIMAL") "#00FF88" else if (report.cpuStatus == "HEAVY") "#FFB800" else "#FF453A").toColorInt())
            } catch (_: Exception) {}
            tvCpuLoad.text = "Load: ${report.cpuLoad}%"
            tvCpuTemp.text = if (report.cpuTemp > 0) "Temp: ${report.cpuTemp} °C" else "Temp: Normal"
            tvCpuThrottle.text = if (report.cpuTemp > 85) "Throttle: Detected!" else "Throttle: None"
            try {
                tvCpuThrottle.setTextColor((if (report.cpuTemp > 85) "#FF453A" else "#00FF88").toColorInt())
            } catch (_: Exception) {}

            // RAM
            tvRamStatus.text = report.ramStatus
            try {
                tvRamStatus.setTextColor((if (report.ramPercent < 80) "#00FF88" else "#FFB800").toColorInt())
            } catch (_: Exception) {}
            tvRamUsed.text = if (report.ramTotalGb > 0) "Used: ${report.ramUsedGb} / ${report.ramTotalGb} GB (${report.ramPercent}%)" else "RAM: ${report.ramPercent}% in use"
            val freeGb = (report.ramTotalGb - report.ramUsedGb).coerceAtLeast(0.0)
            tvRamFree.text = if (report.ramTotalGb > 0) String.format(Locale.getDefault(), "Avail: %.1f GB", freeGb) else "Normal Buffer"

            // Disk
            tvDiskStatus.text = report.storageStatus
            try {
                tvDiskStatus.setTextColor((if (report.storagePercent < 85) "#00FF88" else "#FFB800").toColorInt())
            } catch (_: Exception) {}
            tvDiskUsed.text = if (report.storageTotalGb > 0) "Storage: ${report.storageUsedGb} / ${report.storageTotalGb} GB (${report.storagePercent}%)" else "Disk: ${report.storagePercent}% full"
            val diskFreeGb = (report.storageTotalGb - report.storageUsedGb).coerceAtLeast(0.0)
            tvDiskFree.text = if (report.storageTotalGb > 0) String.format(Locale.getDefault(), "Free: %.1f GB", diskFreeGb) else "Clean"

            // LAN
            tvLanStatus.text = report.latencyStatus
            tvLanPing.text = "Ping: ${report.pingMs} ms"
            tvLanThroughput.text = "Up ${report.netUpMb} MB/s • Down ${report.netDownMb} MB/s"

            // Power
            tvPowerStatus.text = report.batteryStatus
            tvUptime.text = "Uptime: ${report.uptime}"
            tvHost.text = report.hostname

            // Issues
            if (report.issues.isEmpty()) {
                tvIssuesTitle.text = "✅ Diagnostic Status: All Systems Clean"
                try { tvIssuesTitle.setTextColor("#00FF88".toColorInt()) } catch (_: Exception) {}
                tvIssuesDesc.text = "No bottlenecks, thermal throttling, or critical memory pressure detected on host PC."
                containerIssues.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.badge_glow_green)
            } else {
                tvIssuesTitle.text = "⚠️ Notice: ${report.issues.size} Attention Item(s)"
                try { tvIssuesTitle.setTextColor("#FFB800".toColorInt()) } catch (_: Exception) {}
                tvIssuesDesc.text = report.issues.joinToString("\n• ", prefix = "• ")
                containerIssues.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.badge_glow_amber)
            }
        }

        fun runCheck() {
            pbScanning.visibility = View.VISIBLE
            btnRefresh.isEnabled = false
            btnRefresh.text = "Scanning PC..."

            PcApiClient.checkPcHealth { report ->
                pbScanning.visibility = View.GONE
                btnRefresh.isEnabled = true
                btnRefresh.text = "🔄 Re-Scan Health"

                if (report != null) {
                    bindReport(report)
                } else {
                    Toast.makeText(this@MainActivity, "Could not reach PC for diagnostics. Verify connection.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnRefresh.setOnClickListener { runCheck() }
        btnDone.setOnClickListener { dialog.dismiss() }
        btnClose.setOnClickListener { dialog.dismiss() }

        runCheck()
        applyBwFilterToDialog(dialog)
        dialog.show()
    }

    private fun showAboutAppDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_about_app)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width = (resources.displayMetrics.widthPixels * 0.94).toInt()
        val height = (resources.displayMetrics.heightPixels * 0.88).toInt()
        dialog.window?.setLayout(width, height)

        dialog.findViewById<Button>(R.id.btnAboutAppClose)?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.findViewById<View>(R.id.btnAboutYoutube)?.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, "https://youtube.com/@BeyondEdge1995".toUri()))
            } catch (e: Exception) {
                showToast("Could not open browser: ${e.message}")
            }
        }

        dialog.findViewById<View>(R.id.btnAboutNovaMusic)?.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, "market://search?q=NovaMusicPro".toUri()))
            } catch (_: Exception) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/search?q=NovaMusicPro&c=apps".toUri()))
                } catch (e: Exception) {
                    showToast("Could not open Play Store: ${e.message}")
                }
            }
        }

        dialog.findViewById<View>(R.id.btnAboutMathRush)?.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, "market://search?q=MathRush".toUri()))
            } catch (_: Exception) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/search?q=MathRush&c=apps".toUri()))
                } catch (e: Exception) {
                    showToast("Could not open Play Store: ${e.message}")
                }
            }
        }

        dialog.findViewById<View>(R.id.btnAboutEmail)?.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_SENDTO, "mailto:novamusicpro2025@gmail.com".toUri())
                intent.putExtra(Intent.EXTRA_SUBJECT, "PCLink Feedback & Feature Request")
                startActivity(intent)
            } catch (_: Exception) {
                showToast("Contact email: novamusicpro2025@gmail.com")
            }
        }

        dialog.findViewById<View>(R.id.btnAboutPrivacyPolicy)?.setOnClickListener {
            openPrivacyPolicyUrl()
        }

        applyBwFilterToDialog(dialog)
        dialog.show()
    }

    private fun showPrivacyPolicyDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_privacy_policy)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width = (resources.displayMetrics.widthPixels * 0.94).toInt()
        val height = (resources.displayMetrics.heightPixels * 0.90).toInt()
        dialog.window?.setLayout(width, height)

        dialog.findViewById<View>(R.id.btnOpenOnlinePrivacyPolicy)?.setOnClickListener {
            openPrivacyPolicyUrl()
        }

        dialog.findViewById<View>(R.id.btnOpenOnlinePrivacyPolicyBottom)?.setOnClickListener {
            openPrivacyPolicyUrl()
        }

        dialog.findViewById<Button>(R.id.btnPrivacyPolicyClose)?.setOnClickListener {
            dialog.dismiss()
        }

        applyBwFilterToDialog(dialog)
        dialog.show()
    }

    private fun openPrivacyPolicyUrl() {
        val url = "https://novamusicpro2025-dotcom.github.io/PcMaster/"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: Exception) {
            showToast("Could not open browser: ${e.message}")
        }
    }

    @SuppressLint("SetTextI18n")
    private fun performConnect(ip: String, port: String, manualToken: String, isSilent: Boolean = false) {
        val connection = parseConnectionDetails(ip, port, manualToken)
        if (connection == null) {
            if (!isSilent) Toast.makeText(this, "Enter a valid PC address and port", Toast.LENGTH_SHORT).show()
            updateLoginStatus("Enter a valid PC address and port", isProgress = false, dotColor = "#FF453A")
            return
        }

        val (host, connectionPort, connectionToken) = connection
        loginIpInput.setText(host)
        loginPortInput.setText(connectionPort)
        ipInput.setText(host)
        portInput.setText(connectionPort)

        PcApiClient.baseUrl = PcApiClient.formatBaseUrl(host, connectionPort)
        if (connectionToken.isNotEmpty()) {
            PcApiClient.authToken = connectionToken
        }

        updateLoginStatus("Connecting to $host:$connectionPort...", isProgress = true, dotColor = "#FF9F0A")
        statusText.text = "Connecting..."
        statusText.setTextColor("#FF9F0A".toColorInt())
        statusDot.backgroundTintList = ColorStateList.valueOf("#FF9F0A".toColorInt())
        statusPillHeader?.setBackgroundResource(R.drawable.status_pill_bg)
        tvTelemetryStatus?.text = "Connecting to PC..."
        telemetryStatusDot?.backgroundTintList = ColorStateList.valueOf("#FF9F0A".toColorInt())

        // Auto-negotiate auth token from /settings (works seamlessly with zero typing)
        PcApiClient.fetchSettings { token, _, error ->
            if (error != null) {
                // Settings fetch failed - show error and don't proceed to ping
                statusText.text = getString(R.string.disconnected)
                statusText.setTextColor("#FF453A".toColorInt())
                statusDot.backgroundTintList = ColorStateList.valueOf("#FF453A".toColorInt())
                statusPillHeader?.setBackgroundResource(R.drawable.status_pill_disconnected)
                tvTelemetryStatus?.text = "Waiting for connection..."
                telemetryStatusDot?.backgroundTintList = ColorStateList.valueOf("#8A9BB5".toColorInt())
                tvLatency.visibility = View.GONE
                updateLoginStatus("Failed to reach PC: $error", isProgress = false, dotColor = "#FF453A")
                showLoginScreen()
                if (!isSilent) Toast.makeText(this, "Failed to reach PC: $error", Toast.LENGTH_LONG).show()
                return@fetchSettings
            }

            if (token.isNotEmpty()) {
                PcApiClient.authToken = token
                tokenInput.setText(token)
                loginTokenInput.setText(token)
            }

            // Now ping to verify connectivity & latency
            PcApiClient.ping { success, latencyMs ->
                if (success) {
                    statusText.text = getString(R.string.connected)
                    statusText.setTextColor(Color.parseColor("#00FF88"))
                    statusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00FF88"))
                    statusPillHeader?.setBackgroundResource(R.drawable.status_pill_connected)

                    tvTelemetryStatus?.text = "Live • Connected"
                    telemetryStatusDot?.backgroundTintList = ColorStateList.valueOf("#00FF88".toColorInt())

                    ipBadgeText.text = "$host:$connectionPort"
                    ipBadgeText.visibility = View.VISIBLE
                    tvLatency.text = "${latencyMs}ms"
                    tvLatency.visibility = View.VISIBLE

                    prefs.edit {
                        putString("pc_ip", host)
                        putString("pc_port", connectionPort)
                        putString("pc_token", PcApiClient.authToken)
                    }

                    rememberConnection(host, connectionPort)
                    updateLoginStatus("Connected to $host:$connectionPort!", isProgress = false, dotColor = "#00FF88")
                    if (!isSilent) Toast.makeText(this, "Connected to PCLink Hub!", Toast.LENGTH_SHORT).show()

                    // Sync Dashboard inputs
                    ipInput.setText(host)
                    portInput.setText(connectionPort)
                    tokenInput.setText(PcApiClient.authToken)

                    showDashboard()
                    PcApiClient.fetchScreenInfo()
                    startPolling()
                    pendingSharedUrl?.let { sharedUrl ->
                        PcApiClient.openUrlOnPc(sharedUrl) { opened ->
                            showToast(if (opened) "Opened shared link on PC" else "Could not open shared link on PC")
                            if (opened) pendingSharedUrl = null
                        }
                    }
                } else {
                    statusText.text = getString(R.string.disconnected)
                    statusText.setTextColor("#FF453A".toColorInt())
                    statusDot.backgroundTintList = ColorStateList.valueOf("#FF453A".toColorInt())
                    statusPillHeader?.setBackgroundResource(R.drawable.status_pill_disconnected)

                    tvTelemetryStatus?.text = "Waiting for connection..."
                    telemetryStatusDot?.backgroundTintList = ColorStateList.valueOf("#8A9BB5".toColorInt())
                    tvLatency.visibility = View.GONE

                    updateLoginStatus("Connection failed to $host:$connectionPort. Ensure PCLink is running on PC.", isProgress = false, dotColor = "#FF453A")
                    showLoginScreen()
                    if (!isSilent) Toast.makeText(this, "Connection failed. Ensure PCLink is running on PC.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            tabDashboardView.visibility = View.GONE
            tabTrackpadView.visibility = View.GONE
            tabAppsView.visibility = View.GONE
            tabFilesView.visibility = View.GONE
            tabMediaView.visibility = View.GONE

            when (item.itemId) {
                R.id.menu_dashboard -> {
                    stopLiveStreaming()
                    tabDashboardView.visibility = View.VISIBLE
                    true
                }
                R.id.menu_trackpad -> {
                    stopLiveStreaming()
                    tabTrackpadView.visibility = View.VISIBLE
                    true
                }
                R.id.menu_apps -> {
                    stopLiveStreaming()
                    tabAppsView.visibility = View.VISIBLE
                    loadProcesses()
                    true
                }
                R.id.menu_files -> {
                    stopLiveStreaming()
                    tabFilesView.visibility = View.VISIBLE
                    loadSharedFiles()
                    true
                }
                R.id.menu_media -> {
                    tabMediaView.visibility = View.VISIBLE
                    startLiveStreaming()
                    true
                }
                else -> false
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupTabDashboard() {
        btnQuickNotifSync = findViewById(R.id.btnQuickNotifSync)
        tvQuickNotifStatus = findViewById(R.id.tvQuickNotifStatus)
        btnQuickScreenMirror = findViewById(R.id.btnQuickScreenMirror)
        tvQuickMirrorStatus = findViewById(R.id.tvQuickMirrorStatus)

        btnQuickNotifSync?.setOnClickListener {
            if (isNotificationAccessGranted()) {
                showToast("🟢 PC Notification Sync is Active! Notifications and quick replies sync automatically.")
            } else {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                Toast.makeText(this, "Enable 'PCLink' in Notification Access settings to forward alerts & replies to PC", Toast.LENGTH_LONG).show()
            }
        }

        btnQuickScreenMirror?.setOnClickListener {
            btnToggleMirror.performClick()
        }

        updateQuickHubStatus()

        findViewById<View?>(R.id.btnFindPc)?.setOnClickListener {
            PcApiClient.sendRemoteCommand("findmypc") { success ->
                showToast(if (success) "🔊 Alerting PC!" else "Failed to alert PC")
            }
        }

        // ── POWER PLAN ──────────────────────────────────────
        val tvPowerPlan = findViewById<TextView?>(R.id.tvCurrentPowerPlan)
        findViewById<View?>(R.id.btnPowerSaver)?.setOnClickListener {
            PcApiClient.sendRemoteCommand("powerplan", mapOf("plan" to "powersaver")) { success ->
                if (success) { showToast("🔋 Power Saver activated"); tvPowerPlan?.text = "Power Saver" }
                else showToast("Failed to change power plan")
            }
        }
        findViewById<View?>(R.id.btnPowerBalanced)?.setOnClickListener {
            PcApiClient.sendRemoteCommand("powerplan", mapOf("plan" to "balanced")) { success ->
                if (success) { showToast("⚖ Balanced activated"); tvPowerPlan?.text = "Balanced" }
                else showToast("Failed to change power plan")
            }
        }
        findViewById<View?>(R.id.btnPowerHigh)?.setOnClickListener {
            PcApiClient.sendRemoteCommand("powerplan", mapOf("plan" to "high")) { success ->
                if (success) { showToast("🚀 High Performance activated"); tvPowerPlan?.text = "High Perf" }
                else showToast("Failed to change power plan")
            }
        }

        // ── SHUTDOWN TIMER ──────────────────────────────────
        val btnCancelTimer = findViewById<Button?>(R.id.btnCancelShutdownTimer)
        fun setShutdownTimer(minutes: Int) {
            val seconds = minutes * 60
            PcApiClient.sendRemoteCommand("timer", mapOf("seconds" to "$seconds", "action" to "shutdown")) { success ->
                if (success) {
                    showToast("⏰ PC shuts down in $minutes min")
                    btnCancelTimer?.visibility = android.view.View.VISIBLE
                } else showToast("Failed to set shutdown timer")
            }
        }
        findViewById<View?>(R.id.btnTimer15)?.setOnClickListener { setShutdownTimer(15) }
        findViewById<View?>(R.id.btnTimer30)?.setOnClickListener { setShutdownTimer(30) }
        findViewById<View?>(R.id.btnTimer60)?.setOnClickListener { setShutdownTimer(60) }
        findViewById<View?>(R.id.btnTimerCustom)?.setOnClickListener {
            val etInput = android.widget.EditText(this).apply {
                hint = "Minutes (e.g. 45)"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setTextColor(android.graphics.Color.WHITE)
            }
            android.app.AlertDialog.Builder(this)
                .setTitle("⏰ Custom Shutdown Timer")
                .setView(etInput)
                .setPositiveButton("SET") { _, _ ->
                    val mins = etInput.text.toString().trim().toIntOrNull()
                    if (mins != null && mins > 0) setShutdownTimer(mins)
                    else showToast("Enter a valid number of minutes")
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        btnCancelTimer?.setOnClickListener {
            PcApiClient.sendRemoteCommand("abort") { success ->
                if (success) { showToast("✅ Shutdown timer cancelled"); btnCancelTimer.visibility = android.view.View.GONE }
                else showToast("Failed to cancel timer")
            }
        }

        // ── DISPLAY CONTROLS ────────────────────────────────
        findViewById<View?>(R.id.btnMonitorOff)?.setOnClickListener {
            PcApiClient.sendRemoteCommand("monoff") { success ->
                showToast(if (success) "🌑 Monitor off!" else "Failed")
            }
        }
        findViewById<View?>(R.id.btnSleep)?.setOnClickListener {
            PcApiClient.sendRemoteCommand("sleep") { success ->
                showToast(if (success) "💤 PC sleeping..." else "Failed")
            }
        }
        var isDarkMode = false
        val btnDark = findViewById<Button?>(R.id.btnToggleDarkMode)
        btnDark?.setOnClickListener {
            PcApiClient.sendRemoteCommand("toggledark") { success ->
                if (success) {
                    isDarkMode = !isDarkMode
                    btnDark.text = if (isDarkMode) "☀️ Light Mode" else "🌙 Dark Mode"
                    showToast(if (isDarkMode) "🌙 Dark mode ON" else "☀️ Light mode ON")
                } else showToast("Failed to toggle dark mode")
            }
        }

        // ── PC UTILITIES ────────────────────────────────────
        findViewById<View?>(R.id.btnTaskMgr)?.setOnClickListener {
            PcApiClient.sendRemoteCommand("taskmgr") { success ->
                showToast(if (success) "📊 Task Manager opened!" else "Failed")
            }
        }
        findViewById<View?>(R.id.btnEmptyBin)?.setOnClickListener {
            PcApiClient.sendRemoteCommand("emptybin") { success ->
                showToast(if (success) "🗑️ Recycle Bin emptied!" else "Failed")
            }
        }
        findViewById<View?>(R.id.btnKillFrozen)?.setOnClickListener {
            PcApiClient.sendRemoteCommand("killnotresponding") { success ->
                showToast(if (success) "💀 Frozen apps killed!" else "Failed")
            }
        }
        findViewById<View?>(R.id.btnFlushDns)?.setOnClickListener {
            PcApiClient.sendRemoteCommand("flushdns") { success ->
                showToast(if (success) "🌐 DNS flushed!" else "Failed")
            }
        }
        findViewById<View?>(R.id.btnClearTemp)?.setOnClickListener {
            PcApiClient.sendRemoteCommand("cleartemp") { success ->
                showToast(if (success) "🧹 Temp files cleared!" else "Failed")
            }
        }
        findViewById<View?>(R.id.btnRestartExplorer)?.setOnClickListener {
            PcApiClient.sendRemoteCommand("restartexp") { success ->
                showToast(if (success) "🔄 Explorer restarted!" else "Failed")
            }
        }

        // ── QUICK LAUNCH ─────────────────────────────────────
        findViewById<View?>(R.id.btnLaunchNotepad)?.setOnClickListener {
            PcApiClient.sendRemoteCommand("notepad") { success ->
                showToast(if (success) "📝 Notepad opened!" else "Failed")
            }
        }
        findViewById<View?>(R.id.btnLaunchCalc)?.setOnClickListener {
            PcApiClient.sendRemoteCommand("calc") { success ->
                showToast(if (success) "🧮 Calculator opened!" else "Failed")
            }
        }
        findViewById<View?>(R.id.btnLaunchChrome)?.setOnClickListener {
            PcApiClient.sendRemoteCommand("chrome") { success ->
                showToast(if (success) "🌐 Chrome opened!" else "Failed")
            }
        }
        findViewById<View?>(R.id.btnLaunchSpotify)?.setOnClickListener {
            PcApiClient.sendRemoteCommand("spotify") { success ->
                showToast(if (success) "🎵 Spotify opened!" else "Failed")
            }
        }
        findViewById<View?>(R.id.btnLaunchDownloads)?.setOnClickListener {
            PcApiClient.sendRemoteCommand("downloads") { success ->
                showToast(if (success) "📥 Downloads folder opened!" else "Failed")
            }
        }
        findViewById<View?>(R.id.btnLaunchDocuments)?.setOnClickListener {
            PcApiClient.sendRemoteCommand("documents") { success ->
                showToast(if (success) "📂 Documents folder opened!" else "Failed")
            }
        }


        // Dashboard Clipboard Sync Buttons
        findViewById<View?>(R.id.btnDashCopyFromPc)?.setOnClickListener {
            PcApiClient.getClipboard { text ->
                if (text.isNotEmpty()) {
                    val clipMgr = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
                    clipMgr?.setPrimaryClip(ClipData.newPlainText("PCLink Clipboard", text))
                    showToast("📋 Copied from PC: \"${text.take(30)}...\"")
                } else {
                    showToast("PC Clipboard is empty")
                }
            }
        }
        findViewById<View?>(R.id.btnDashSendToPc)?.setOnClickListener {
            val clipMgr = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
            val item = clipMgr?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            if (item.isNotEmpty()) {
                PcApiClient.setClipboard(item) { success ->
                    if (success) showToast("📋 Sent to PC!") else showToast("Failed to send clipboard")
                }
            } else {
                showToast("Phone clipboard is empty")
            }
        }
        val btnDashAutoClipboard = findViewById<Button?>(R.id.btnDashAutoClipboard)
        btnDashAutoClipboard?.setOnClickListener {
            isAutoClipboardSync = !isAutoClipboardSync
            val clipMgr = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
            if (isAutoClipboardSync) {
                btnDashAutoClipboard.text = "⚡ Auto Clipboard Sync: ON"
                btnDashAutoClipboard.setTextColor("#00FF88".toColorInt())
                clipChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
                    val text = clipMgr?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                    if (text.isNotEmpty() && text != lastSyncedClipboardText) {
                        lastSyncedClipboardText = text
                        PcApiClient.setClipboard(text)
                    }
                }
                clipMgr?.addPrimaryClipChangedListener(clipChangedListener)
                showToast("⚡ Auto Clipboard Sync: Activated")
                // Also update the trackpad auto clipboard button if exists
                val trackpadAutoBtn = findViewById<Button?>(R.id.btnAutoClipboardToggle)
                trackpadAutoBtn?.text = "⚡ Auto: ON"
                trackpadAutoBtn?.setTextColor("#00FF88".toColorInt())
            } else {
                btnDashAutoClipboard.text = "⚡ Auto Clipboard Sync: OFF"
                btnDashAutoClipboard.setTextColor("#8A9BB5".toColorInt())
                clipChangedListener?.let { clipMgr?.removePrimaryClipChangedListener(it) }
                showToast("Auto Clipboard Sync: Deactivated")
                val trackpadAutoBtn = findViewById<Button?>(R.id.btnAutoClipboardToggle)
                trackpadAutoBtn?.text = "⚡ Auto: OFF"
                trackpadAutoBtn?.setTextColor("#8A9BB5".toColorInt())
            }
        }

        // Gaming Mode Button
        var isGamingMode = false
        val btnGaming = findViewById<Button?>(R.id.btnGamingMode)
        btnGaming?.setOnClickListener {
            isGamingMode = !isGamingMode
            if (isGamingMode) {
                btnGaming.text = "🎮 GAMING MODE: ON"
                btnGaming.setTextColor("#00FF88".toColorInt())
                // Activate gaming-optimized config on PC
                PcApiClient.sendRemoteCommand("gamingon") { success ->
                    if (success) {
                        showToast("🎮 Gaming Mode Activated! Performance optimized.")
                    } else {
                        showToast("🎮 Gaming Mode ON (local)")
                    }
                }
                // Enable anti-idle to prevent sleep during gaming
                PcApiClient.setAntiIdle(true)
            } else {
                btnGaming.text = "🎮 GAMING MODE"
                btnGaming.setTextColor(Color.WHITE)
                PcApiClient.sendRemoteCommand("gamingoff") { success ->
                    showToast(if (success) "Gaming Mode Deactivated" else "Gaming Mode OFF (local)")
                }
                PcApiClient.setAntiIdle(false)
            }
        }

        findViewById<View?>(R.id.btnRamBoost)?.setOnClickListener {
            PcApiClient.sendRemoteCommand("boostram") { success ->
                showToast(if (success) "⚡ RAM Boost Executed!" else "Failed to trigger RAM Boost")
            }
        }

        findViewById<View?>(R.id.btnEmptyTrash)?.setOnClickListener {
            PcApiClient.sendRemoteCommand("emptytrash") { success ->
                showToast(if (success) "🗑️ Recycle Bin Cleared!" else "Failed to empty recycle bin")
            }
        }

        val antiIdleClickListener = View.OnClickListener { toggleAntiIdle() }
        findViewById<View?>(R.id.btnAntiIdle)?.setOnClickListener(antiIdleClickListener)

        findViewById<View?>(R.id.btnMuteAudio)?.setOnClickListener {
            PcApiClient.sendRemoteCommand("mute") { success ->
                showToast(if (success) "Audio Mute Toggled" else "Failed to toggle mute")
            }
        }

        findViewById<View?>(R.id.btnLockWorkstation)?.setOnClickListener {
            performLockPc()
        }

        btnUnlockPc?.setOnClickListener {
            performUnlockPc()
        }
        btnUnlockPc?.setOnLongClickListener {
            showUnlockOptionsDialog()
            true
        }

        findViewById<View?>(R.id.btnSleepMode)?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Sleep Mode")
                .setMessage("Are you sure you want to put your connected PC to sleep?")
                .setPositiveButton("Sleep") { _, _ ->
                    PcApiClient.sendRemoteCommand("sleep") { showToast("PC entering sleep mode...") }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<View?>(R.id.btnRestartPc)?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Restart PC?")
                .setMessage("Are you sure you want to restart the connected PC?")
                .setPositiveButton("Restart") { _, _ ->
                    PcApiClient.sendRemoteCommand("restart") { showToast("PC is rebooting...") }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<View?>(R.id.btnShutdown)?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Shut down PC?")
                .setMessage("This will power off the connected PC.")
                .setPositiveButton("Shutdown") { _, _ ->
                    PcApiClient.sendRemoteCommand("shutdown") { showToast("PC is shutting down...") }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnCustomizeActions?.setOnClickListener {
            val actions = arrayOf("RAM Boost", "Empty Trash", "Caffeine", "Mute Sound", "Lock Workstation", "Sleep Mode", "Restart PC", "Shutdown")
            val checkedItems = booleanArrayOf(true, true, true, true, true, true, true, true)
            AlertDialog.Builder(this)
                .setTitle("Customize Quick Actions")
                .setMultiChoiceItems(actions, checkedItems) { _, which, isChecked ->
                    checkedItems[which] = isChecked
                }
                .setPositiveButton("Save") { _, _ ->
                    showToast("Action layout customized!")
                }
                .setNeutralButton("Reset Default") { _, _ ->
                    showToast("Default action order restored")
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun performLockPc() {
        PcApiClient.lockPc { success ->
            if (success) {
                showToast("🔒 Workstation Locked!")
                tvTouchStatusHint?.text = "🔒 Workstation Locked"
            } else {
                showToast("Failed to lock PC")
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun performUnlockPc() {
        val savedPin = prefs.getString("saved_windows_pin", "") ?: ""
        if (savedPin.isNotEmpty()) {
            // Saved PIN ఉంటే silently use చేసి unlock చేయి
            showToast("🔓 Waking Screen & Entering PIN...")
            tvTouchStatusHint?.text = "🔓 Waking Screen & Entering PIN..."
            PcApiClient.unlockPc(savedPin) { success ->
                if (success) {
                    showToast("✅ Unlock command sent!")
                } else {
                    showToast("Failed to send unlock command")
                }
            }
        } else {
            // PIN లేకపోయినా direct గా wake + lock screen dismiss చేయి
            showToast("⚡ Unlocking PC...")
            tvTouchStatusHint?.text = "⚡ Unlocking PC..."
            PcApiClient.unlockPc("") { success ->
                if (success) {
                    showToast("✅ PC Unlocked!")
                } else {
                    showToast("Failed to unlock PC")
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showUnlockOptionsDialog() {
        val savedPin = prefs.getString("saved_windows_pin", "") ?: ""
        val builder = AlertDialog.Builder(this)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (resources.displayMetrics.density * 18).toInt()
            setPadding(pad, pad, pad, pad / 2)
        }

        val tvTitle = TextView(this).apply {
            text = "🔓 Unlock & Wake PC"
            setTextColor(Color.WHITE)
            textSize = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }
        container.addView(tvTitle)

        val tvDesc = TextView(this).apply {
            text = "Wake your PC screen from sleep or enter your Windows PIN/Password to login automatically."
            setTextColor(Color.parseColor("#8A9BB5"))
            textSize = 12.5f
            setPadding(0, 0, 0, 20)
        }
        container.addView(tvDesc)

        val etPin = EditText(this).apply {
            hint = if (savedPin.isNotEmpty()) "Enter new PIN (or leave empty to use saved)" else "Enter Windows PIN / Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#4A5F80"))
            setBackgroundResource(R.drawable.edittext_bg)
            val p = (resources.displayMetrics.density * 10).toInt()
            setPadding(p, p, p, p)
        }
        container.addView(etPin)

        val cbRemember = CheckBox(this).apply {
            text = "Remember PIN on this phone (1-Tap Unlock)"
            isChecked = savedPin.isNotEmpty()
            setTextColor(Color.parseColor("#C5D1E8"))
            textSize = 12f
            setPadding(8, 16, 0, 8)
        }
        container.addView(cbRemember)

        if (savedPin.isNotEmpty()) {
            val btnClear = Button(this).apply {
                text = "🗑️ Remove Saved PIN"
                textSize = 11f
                setTextColor(Color.parseColor("#FF453A"))
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener {
                    prefs.edit { remove("saved_windows_pin") }
                    showToast("Saved PIN removed")
                    tvUnlockStatus?.text = "Wake & Login"
                }
            }
            container.addView(btnClear)
        }

        builder.setView(container)

        builder.setPositiveButton("UNLOCK") { _, _ ->
            val typedPin = etPin.text.toString().trim()
            val effectivePin = typedPin.ifEmpty { savedPin }

            if (cbRemember.isChecked && typedPin.isNotEmpty()) {
                prefs.edit { putString("saved_windows_pin", typedPin) }
                tvUnlockStatus?.text = "1-Tap Ready"
            }

            if (effectivePin.isNotEmpty()) {
                showToast("🔓 Waking Screen & Entering PIN...")
                tvTouchStatusHint?.text = "🔓 Waking Screen & Entering PIN..."
                PcApiClient.unlockPc(effectivePin) { success ->
                    if (success) showToast("✅ Unlock command sent!")
                }
            } else {
                showToast("⚡ Waking PC Display & Dismissing Lock...")
                tvTouchStatusHint?.text = "⚡ Waking PC Display..."
                PcApiClient.unlockPc("") { success ->
                    if (success) showToast("✅ Display woken & lock screen dismissed!")
                }
            }
        }

        builder.setNeutralButton("WAKE ONLY") { _, _ ->
            showToast("⚡ Waking PC Display...")
            tvTouchStatusHint?.text = "⚡ Waking PC Display..."
            PcApiClient.unlockPc("") { success ->
                if (success) showToast("✅ Display woken & lock screen dismissed!")
            }
        }

        builder.setNegativeButton("Cancel", null)

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.card_pod_bg)
        applyBwFilterToDialog(dialog)
        dialog.show()
    }

    private fun toggleAntiIdle() {
        isAntiIdleActive = !isAntiIdleActive
        updateAntiIdleUi(isAntiIdleActive)
        PcApiClient.setAntiIdle(isAntiIdleActive) { success ->
            if (success) {
                showToast(if (isAntiIdleActive) "☕ Anti-Idle ON: PC Screen will stay awake" else "Anti-Idle Disabled: Standard sleep restored")
            }
        }
    }

    private fun updateAntiIdleUi(active: Boolean) {
        isAntiIdleActive = active
        isCaffeineActive = active
        tvAntiIdleStatus?.text = if (active) "ACTIVE (Screen Awake)" else "Prevent Screen Off"
        tvAntiIdleStatus?.setTextColor(if (active) Color.parseColor("#00FF88") else Color.parseColor("#8A9BB5"))
        tvCaffeineStatus?.text = if (active) "ACTIVE (Screen Awake)" else "Prevent Screen Off"
        tvCaffeineStatus?.setTextColor(if (active) Color.parseColor("#00FF88") else Color.parseColor("#8A9BB5"))

        btnLiveAntiIdle?.text = if (active) "☕ AWAKE ON" else "☕ ANTI-IDLE"
        btnLiveAntiIdle?.setTextColor(if (active) Color.parseColor("#00FF88") else Color.parseColor("#00D2FF"))

        // Keep phone screen awake as well when anti-idle is active
        if (active) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupTabTrackpad() {
        nativeTrackpad.onMouseMove = { dx, dy ->
            PcApiClient.sendMouseMove(dx, dy)
        }
        nativeTrackpad.onLeftClick = {
            PcApiClient.sendMouseClick("left")
        }
        nativeTrackpad.onRightClick = {
            PcApiClient.sendMouseClick("right")
        }
        nativeTrackpad.onScroll = { deltaY ->
            PcApiClient.sendMouseScroll(deltaY)
        }

        // Air Mouse Toggle
        val btnAirMouseToggle = findViewById<View?>(R.id.btnAirMouseToggle)
        val ivAirMouseIcon = findViewById<ImageView?>(R.id.ivAirMouseIcon)
        val tvAirMouseStatus = findViewById<TextView?>(R.id.tvAirMouseStatus)

        btnAirMouseToggle?.setOnClickListener {
            isAirMouseActive = !isAirMouseActive
            if (isAirMouseActive) {
                if (sensorManager == null) {
                    sensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
                    gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
                        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                }
                if (gyroSensor != null) {
                    sensorManager?.registerListener(gyroListener, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
                    tvAirMouseStatus?.text = "AIR MOUSE: ON"
                    tvAirMouseStatus?.setTextColor(Color.parseColor("#00FF88"))
                    ivAirMouseIcon?.setColorFilter(Color.parseColor("#00FF88"))
                    showToast("Air Mouse ON: Tilt phone to move cursor")
                } else {
                    isAirMouseActive = false
                    showToast("Gyroscope sensor not available on this device")
                }
            } else {
                sensorManager?.unregisterListener(gyroListener)
                tvAirMouseStatus?.text = "AIR MOUSE: OFF"
                tvAirMouseStatus?.setTextColor(Color.parseColor("#8A9BB5"))
                ivAirMouseIcon?.setColorFilter(Color.parseColor("#6E829F"))
                showToast("Air Mouse OFF")
            }
        }

        // Special Keys Row 1
        findViewById<View?>(R.id.keyEsc)?.setOnClickListener { PcApiClient.sendKeyPress("esc") }
        findViewById<View?>(R.id.keyTab)?.setOnClickListener { PcApiClient.sendKeyPress("tab") }
        findViewById<View?>(R.id.keyWin)?.setOnClickListener { PcApiClient.sendKeyPress("win") }
        findViewById<View?>(R.id.keyAltTab)?.setOnClickListener { PcApiClient.sendKeyPress("alttab") }
        findViewById<View?>(R.id.keyWinD)?.setOnClickListener { PcApiClient.sendKeyPress("showdesktop") }
        findViewById<View?>(R.id.keyAltF4)?.setOnClickListener { PcApiClient.sendText("%{F4}") }
        findViewById<View?>(R.id.keyEnter)?.setOnClickListener { PcApiClient.sendKeyPress("enter") }
        findViewById<View?>(R.id.keyBackspace)?.setOnClickListener { PcApiClient.sendBackspace() }
        findViewById<View?>(R.id.keySpace)?.setOnClickListener { PcApiClient.sendText(" ") }
        findViewById<View?>(R.id.keyCopy)?.setOnClickListener { PcApiClient.sendCopy() }
        findViewById<View?>(R.id.keyPaste)?.setOnClickListener { PcApiClient.sendPaste() }

        // Arrow Keys Mini D-Pad
        findViewById<View?>(R.id.keyArrowLeft)?.setOnClickListener { PcApiClient.sendText("{LEFT}") }
        findViewById<View?>(R.id.keyArrowUp)?.setOnClickListener { PcApiClient.sendText("{UP}") }
        findViewById<View?>(R.id.keyArrowDown)?.setOnClickListener { PcApiClient.sendText("{DOWN}") }
        findViewById<View?>(R.id.keyArrowRight)?.setOnClickListener { PcApiClient.sendText("{RIGHT}") }

        // F-Keys Collapsible Toggle
        val layoutFKeys = findViewById<View?>(R.id.layoutFKeys)
        findViewById<View?>(R.id.btnToggleFKeys)?.setOnClickListener {
            val isVis = layoutFKeys?.visibility == View.VISIBLE
            layoutFKeys?.visibility = if (isVis) View.GONE else View.VISIBLE
        }
        val fKeyIds = listOf(
            R.id.keyF1 to "{F1}", R.id.keyF2 to "{F2}", R.id.keyF3 to "{F3}", R.id.keyF4 to "{F4}",
            R.id.keyF5 to "{F5}", R.id.keyF6 to "{F6}", R.id.keyF7 to "{F7}", R.id.keyF8 to "{F8}",
            R.id.keyF9 to "{F9}", R.id.keyF10 to "{F10}", R.id.keyF11 to "{F11}", R.id.keyF12 to "{F12}"
        )
        fKeyIds.forEach { (btnId, keySeq) ->
            findViewById<View?>(btnId)?.setOnClickListener { PcApiClient.sendText(keySeq) }
        }

        // Clipboard Bar Toggle & Actions
        val layoutClipboardBar = findViewById<View?>(R.id.layoutClipboardBar)
        findViewById<View?>(R.id.btnToggleClipboard)?.setOnClickListener {
            val isVis = layoutClipboardBar?.visibility == View.VISIBLE
            layoutClipboardBar?.visibility = if (isVis) View.GONE else View.VISIBLE
        }
        findViewById<View?>(R.id.btnCopyFromPc)?.setOnClickListener {
            PcApiClient.getClipboard { text ->
                if (text.isNotEmpty()) {
                    val clipMgr = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
                    clipMgr?.setPrimaryClip(ClipData.newPlainText("PC Clipboard", text))
                    showToast("Copied from PC: \"${text.take(30)}...\"")
                } else {
                    showToast("PC Clipboard is empty or non-text")
                }
            }
        }
        findViewById<View?>(R.id.btnSendToPcClipboard)?.setOnClickListener {
            val clipMgr = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
            val item = clipMgr?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            if (item.isNotEmpty()) {
                PcApiClient.setClipboard(item) { success ->
                    if (success) showToast("Sent clipboard text to PC!") else showToast("Failed to send clipboard")
                }
            } else {
                showToast("Phone clipboard is empty")
            }
        }
        val btnAutoClipboardToggle = findViewById<Button?>(R.id.btnAutoClipboardToggle)
        btnAutoClipboardToggle?.setOnClickListener {
            isAutoClipboardSync = !isAutoClipboardSync
            val clipMgr = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
            if (isAutoClipboardSync) {
                btnAutoClipboardToggle.text = "⚡ Auto: ON"
                btnAutoClipboardToggle.setTextColor(Color.parseColor("#00FF88"))
                clipChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
                    val text = clipMgr?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                    if (text.isNotEmpty() && text != lastSyncedClipboardText) {
                        lastSyncedClipboardText = text
                        PcApiClient.setClipboard(text)
                    }
                }
                clipMgr?.addPrimaryClipChangedListener(clipChangedListener)
                showToast("Auto Clipboard Sync: Activated")
            } else {
                btnAutoClipboardToggle.text = "⚡ Auto: OFF"
                btnAutoClipboardToggle.setTextColor(Color.parseColor("#8A9BB5"))
                clipChangedListener?.let { clipMgr?.removePrimaryClipChangedListener(it) }
                showToast("Auto Clipboard Sync: Deactivated")
            }
        }

        btnSendText.setOnClickListener {
            val text = etSendText.text.toString()
            if (text.isNotEmpty()) {
                PcApiClient.sendText(text) { success ->
                    if (success) etSendText.setText("")
                }
            }
        }

        sbVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            @SuppressLint("SetTextI18n")
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvVolumeVal.text = "$progress%"
                if (fromUser) {
                    PcApiClient.setVolume(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            @SuppressLint("SetTextI18n")
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvBrightnessVal.text = "$progress%"
                if (fromUser) {
                    PcApiClient.setBrightness(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupTabApps() {
        val appButtons = mapOf(
            R.id.appTaskMgr to "taskmgr",
            R.id.appPowerShell to "powershell",
            R.id.appCmd to "cmd",
            R.id.appSnipping to "snipping",
            R.id.appSettings to "settings",
            R.id.appBrowser to "chrome",
            R.id.appYouTube to "youtube",
            R.id.appSpeedtest to "speedtest",
            R.id.appSpotify to "spotify"
        )

        for ((viewId, cmd) in appButtons) {
            findViewById<View?>(viewId)?.setOnClickListener {
                PcApiClient.sendRemoteCommand(cmd) { showToast("Launched $cmd") }
            }
        }

        btnRefreshProcesses.setOnClickListener { loadProcesses() }

        processAdapter = object : ArrayAdapter<ProcessItem>(this, R.layout.item_process, processList) {
            @SuppressLint("SetTextI18n")
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_process, parent, false)
                val item = getItem(position)
                val tvProcName = view.findViewById<TextView>(R.id.tvProcName)
                val btnKillProc = view.findViewById<Button>(R.id.btnKillProc)

                if (item != null) {
                    tvProcName.text = "${item.name} (${item.memory})"
                    btnKillProc.setOnClickListener {
                        AlertDialog.Builder(context)
                            .setTitle("Kill Process")
                            .setMessage("Terminate ${item.name} (PID: ${item.pid})?")
                            .setPositiveButton("Kill") { _, _ ->
                                PcApiClient.killProcess(item.pid) { success ->
                                    if (success) {
                                        showToast("Terminated ${item.name}")
                                        loadProcesses()
                                    } else {
                                        showToast("Failed to kill ${item.name}")
                                    }
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
                return view
            }
        }
        lvProcesses.adapter = processAdapter
    }

    private fun loadProcesses() {
        PcApiClient.getProcesses { procs ->
            processList.clear()
            processList.addAll(procs)
            processAdapter.notifyDataSetChanged()
        }
    }

    private fun setupTabFiles() {
        btnUploadFile.setOnClickListener {
            pickFileLauncher.launch("*/*")
        }

        btnRefreshFiles.setOnClickListener { loadSharedFiles(currentFolderPath) }

        btnFolderBack?.setOnClickListener {
            if (currentFolderPath.isNotEmpty()) {
                val parent = File(currentFolderPath).parent
                if (parent == null || currentFolderPath.matches(Regex("^[a-zA-Z]:[/\\\\]?$"))) {
                    loadSharedFiles("")
                } else {
                    loadSharedFiles(parent)
                }
            }
        }
    }

    private fun isImageFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in listOf("jpg", "jpeg", "png", "bmp", "webp", "gif")
    }

    private fun isVideoFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in listOf("mp4", "mkv", "webm", "avi", "mov", "wmv", "3gp", "flv")
    }

    private fun isAudioFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in listOf("mp3", "wav", "m4a", "aac", "ogg", "flac")
    }

    @SuppressLint("SetTextI18n")
    private fun loadSharedFiles(path: String = "") {
        currentFolderPath = path
        btnFolderBack?.visibility = if (path.isEmpty()) View.GONE else View.VISIBLE
        tvCurrentFolderPath?.text = if (path.isEmpty()) "Root: PC Drives (C:, D:)" else "Path: $path"

        PcApiClient.getFileList(path) { files ->
            fileList.clear()
            fileList.addAll(files)

            val adapter = object : ArrayAdapter<FileItem>(this, R.layout.item_file, fileList) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_file, parent, false)
                    val item = getItem(position)

                    val flIconBg = view.findViewById<FrameLayout?>(R.id.flIconBg)
                    val tvIcon = view.findViewById<TextView?>(R.id.tvFileIcon)
                    val tvName = view.findViewById<TextView>(R.id.tvFileName)
                    val tvSize = view.findViewById<TextView>(R.id.tvFileSize)
                    val btnPreview = view.findViewById<Button?>(R.id.btnPreviewFile)
                    val btnDownload = view.findViewById<Button>(R.id.btnDownloadFile)

                    if (item != null) {
                        tvName.text = item.name
                        tvSize.text = if (item.size.isNotEmpty()) item.size else if (item.isDir) "Folder" else ""

                        if (item.isDir) {
                            tvIcon?.text = "📁"
                            flIconBg?.setBackgroundResource(R.drawable.badge_glow_amber)
                            btnPreview?.visibility = View.GONE
                            btnDownload.visibility = View.VISIBLE
                            btnDownload.text = "OPEN ➔"
                            val openAction = View.OnClickListener {
                                loadSharedFiles(item.path)
                            }
                            btnDownload.setOnClickListener(openAction)
                            view.setOnClickListener(openAction)
                        } else if (isImageFile(item.name)) {
                            tvIcon?.text = "🖼️"
                            flIconBg?.setBackgroundResource(R.drawable.badge_glow_purple)
                            btnPreview?.visibility = View.VISIBLE
                            btnPreview?.text = "👁 VIEW"
                            btnDownload.visibility = View.VISIBLE
                            btnDownload.text = "GET ⬇"

                            val previewAction = View.OnClickListener {
                                showPhotoPreviewDialog(item)
                            }
                            val downloadAction = View.OnClickListener {
                                downloadFile(item.path, item.name)
                            }
                            btnPreview?.setOnClickListener(previewAction)
                            btnDownload.setOnClickListener(downloadAction)
                            view.setOnClickListener(previewAction)
                        } else if (isVideoFile(item.name)) {
                            tvIcon?.text = "🎬"
                            flIconBg?.setBackgroundResource(R.drawable.badge_glow_green)
                            btnPreview?.visibility = View.VISIBLE
                            btnPreview?.text = "▶ PLAY"
                            btnDownload.visibility = View.VISIBLE
                            btnDownload.text = "GET ⬇"

                            val playAction = View.OnClickListener {
                                showVideoPreviewDialog(item)
                            }
                            val downloadAction = View.OnClickListener {
                                downloadFile(item.path, item.name)
                            }
                            btnPreview?.setOnClickListener(playAction)
                            btnDownload.setOnClickListener(downloadAction)
                            view.setOnClickListener(playAction)
                        } else {
                            if (isAudioFile(item.name)) {
                                tvIcon?.text = "🎵"
                                flIconBg?.setBackgroundResource(R.drawable.badge_glow_cyan)
                            } else {
                                tvIcon?.text = "📄"
                                flIconBg?.setBackgroundResource(R.drawable.badge_glow_cyan)
                            }
                            btnPreview?.visibility = View.GONE
                            btnDownload.visibility = View.VISIBLE
                            btnDownload.text = "GET ⬇"
                            val downloadAction = View.OnClickListener {
                                downloadFile(item.path, item.name)
                            }
                            btnDownload.setOnClickListener(downloadAction)
                            view.setOnClickListener(downloadAction)
                            if (isAudioFile(item.name)) {
                                val playAction = View.OnClickListener {
                                    showAudioPlayerDialog(item)
                                }
                                btnPreview?.visibility = View.VISIBLE
                                btnPreview?.text = "PLAY"
                                btnPreview?.setOnClickListener(playAction)
                                view.setOnClickListener(playAction)
                            }
                        }
                    }
                    return view
                }
            }
            lvSharedFiles.adapter = adapter
        }
    }

    private fun showPhotoPreviewDialog(item: FileItem) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_photo_preview)

        val btnClose = dialog.findViewById<ImageButton>(R.id.btnClosePhotoPreview)
        val tvTitle = dialog.findViewById<TextView>(R.id.tvPhotoPreviewTitle)
        val tvSize = dialog.findViewById<TextView>(R.id.tvPhotoPreviewSize)
        val ivPhoto = dialog.findViewById<ImageView>(R.id.ivPhotoPreview)
        val pbLoading = dialog.findViewById<ProgressBar>(R.id.pbPhotoLoading)
        val btnDownload = dialog.findViewById<Button>(R.id.btnDownloadPhoto)

        tvTitle.text = item.name
        tvSize.text = if (item.size.isNotEmpty()) "${item.size} • Remote PC Photo" else "Remote PC Photo"
        btnClose.setOnClickListener { dialog.dismiss() }

        var loadedBitmap: Bitmap? = null

        btnDownload.setOnClickListener {
            val bmp = loadedBitmap
            if (bmp != null && !bmp.isRecycled) {
                saveBitmapToPhoneGallery(bmp)
            } else {
                downloadFile(item.path, item.name)
            }
        }

        Thread {
            try {
                val encodedPath = URLEncoder.encode(item.path, "UTF-8").replace("+", "%20")
                val tokenParam = if (PcApiClient.authToken.isNotEmpty()) "&token=${PcApiClient.authToken}" else ""
                val urlStr = "${PcApiClient.baseUrl}/download?path=$encodedPath$tokenParam"
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 20000
                conn.setRequestProperty("User-Agent", "PC-Master-Android-App/2.0")
                if (PcApiClient.authToken.isNotEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer ${PcApiClient.authToken}")
                    conn.setRequestProperty("X-Auth-Token", PcApiClient.authToken)
                }

                if (conn.responseCode in 200..299) {
                    val bmp = BitmapFactory.decodeStream(conn.inputStream)
                    loadedBitmap = bmp
                    runOnUiThread {
                        pbLoading.visibility = View.GONE
                        if (bmp != null) {
                            ivPhoto.setImageBitmap(bmp)
                        } else {
                            showToast("Could not decode image preview")
                        }
                    }
                } else {
                    runOnUiThread {
                        pbLoading.visibility = View.GONE
                        showToast("Server HTTP ${conn.responseCode}")
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading photo preview: ${e.message}")
                runOnUiThread {
                    pbLoading.visibility = View.GONE
                    showToast("Preview error: ${e.message}")
                }
            }
        }.start()

        dialog.show()
    }

    private fun showVideoPreviewDialog(item: FileItem) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_video_preview)

        val btnClose = dialog.findViewById<ImageButton>(R.id.btnCloseVideoPreview)
        val tvTitle = dialog.findViewById<TextView>(R.id.tvVideoPreviewTitle)
        val tvSize = dialog.findViewById<TextView>(R.id.tvVideoPreviewSize)
        val vvVideo = dialog.findViewById<VideoView>(R.id.vvVideoPreview)
        val pbBuffering = dialog.findViewById<ProgressBar>(R.id.pbVideoBuffering)
        val btnDownload = dialog.findViewById<Button>(R.id.btnDownloadVideo)

        tvTitle.text = item.name
        tvSize.text = if (item.size.isNotEmpty()) "${item.size} • Live Streaming" else "Live Streaming"

        btnClose.setOnClickListener {
            try { vvVideo.stopPlayback() } catch (_: Exception) {}
            dialog.dismiss()
        }

        btnDownload.setOnClickListener {
            downloadFile(item.path, item.name)
        }

        val encodedPath = URLEncoder.encode(item.path, "UTF-8").replace("+", "%20")
        val tokenParam = if (PcApiClient.authToken.isNotEmpty()) "&token=${PcApiClient.authToken}" else ""
        val videoUrl = "${PcApiClient.baseUrl}/download?path=$encodedPath$tokenParam"

        val mediaController = MediaController(this)
        mediaController.setAnchorView(vvVideo)
        vvVideo.setMediaController(mediaController)

        val headers = HashMap<String, String>()
        headers["User-Agent"] = "PC-Master-Android-App/2.0"
        if (PcApiClient.authToken.isNotEmpty()) {
            headers["Authorization"] = "Bearer ${PcApiClient.authToken}"
            headers["X-Auth-Token"] = PcApiClient.authToken
        }

        pbBuffering.visibility = View.VISIBLE
        vvVideo.setVideoURI(videoUrl.toUri(), headers)

        vvVideo.setOnPreparedListener { mp ->
            pbBuffering.visibility = View.GONE
            mp.start()
        }

        vvVideo.setOnInfoListener { _, what, _ ->
            if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                pbBuffering.visibility = View.VISIBLE
            } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                pbBuffering.visibility = View.GONE
            }
            false
        }

        vvVideo.setOnErrorListener { _, _, _ ->
            pbBuffering.visibility = View.GONE
            showToast("Video format playback error. Tap Download to view locally.")
            true
        }

        dialog.setOnDismissListener {
            try {
                vvVideo.stopPlayback()
            } catch (_: Exception) {}
        }

        dialog.show()
    }

    @SuppressLint("SetTextI18n")
    private fun showAudioPlayerDialog(item: FileItem) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_audio_player)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val title = dialog.findViewById<TextView>(R.id.tvAudioTitle)
        val status = dialog.findViewById<TextView>(R.id.tvAudioStatus)
        val elapsed = dialog.findViewById<TextView>(R.id.tvAudioElapsed)
        val duration = dialog.findViewById<TextView>(R.id.tvAudioDuration)
        val progress = dialog.findViewById<SeekBar>(R.id.sbAudioProgress)
        val playPause = dialog.findViewById<Button>(R.id.btnAudioPlayPause)
        val close = dialog.findViewById<Button>(R.id.btnAudioClose)
        val download = dialog.findViewById<Button>(R.id.btnAudioDownload)
        val updateHandler = Handler(Looper.getMainLooper())
        var player: MediaPlayer? = null
        var isSeeking = false

        fun formatDuration(milliseconds: Int): String {
            val totalSeconds = milliseconds.coerceAtLeast(0) / 1000
            return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
        }

        val updateProgress = object : Runnable {
            override fun run() {
                val currentPlayer = player
                if (currentPlayer != null && currentPlayer.isPlaying) {
                    if (!isSeeking) {
                        progress.progress = currentPlayer.currentPosition
                        elapsed.text = formatDuration(currentPlayer.currentPosition)
                    }
                    updateHandler.postDelayed(this, 500)
                }
            }
        }

        title.text = item.name
        status.text = if (item.size.isNotEmpty()) "${item.size} - Streaming from PC" else "Streaming from PC"
        close.setOnClickListener { dialog.dismiss() }
        download.setOnClickListener { downloadFile(item.path, item.name) }
        playPause.isEnabled = false
        playPause.setOnClickListener {
            val currentPlayer = player ?: return@setOnClickListener
            if (currentPlayer.isPlaying) {
                currentPlayer.pause()
                playPause.text = "PLAY"
                updateHandler.removeCallbacks(updateProgress)
            } else {
                currentPlayer.start()
                playPause.text = "PAUSE"
                updateHandler.post(updateProgress)
            }
        }
        progress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) {
                if (fromUser) elapsed.text = formatDuration(value)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                isSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                player?.seekTo(seekBar.progress)
                isSeeking = false
            }
        })

        val encodedPath = URLEncoder.encode(item.path, "UTF-8").replace("+", "%20")
        val tokenParam = if (PcApiClient.authToken.isNotEmpty()) "&token=${PcApiClient.authToken}" else ""
        val audioUri = "${PcApiClient.baseUrl}/download?path=$encodedPath$tokenParam".toUri()
        val headers = HashMap<String, String>()
        headers["User-Agent"] = "PC-Master-Android-App/2.0"
        if (PcApiClient.authToken.isNotEmpty()) {
            headers["Authorization"] = "Bearer ${PcApiClient.authToken}"
            headers["X-Auth-Token"] = PcApiClient.authToken
        }

        try {
            player = MediaPlayer().apply {
                setOnPreparedListener { preparedPlayer ->
                    progress.max = preparedPlayer.duration
                    duration.text = formatDuration(preparedPlayer.duration)
                    playPause.isEnabled = true
                    preparedPlayer.start()
                    playPause.text = "PAUSE"
                    updateHandler.post(updateProgress)
                }
                setOnCompletionListener {
                    playPause.text = "PLAY"
                    progress.progress = progress.max
                    elapsed.text = duration.text
                    updateHandler.removeCallbacks(updateProgress)
                }
                setOnErrorListener { _, _, _ ->
                    status.text = "This format cannot stream on this device. Download it to play locally."
                    playPause.isEnabled = false
                    true
                }
            }
            
            // Background thread to download the audio file locally to avoid MediaHTTPConnection timeout & Range header issues
            status.text = "Buffering from PC..."
            Thread {
                var conn: HttpURLConnection? = null
                var tempFile: File?
                try {
                    val url = URL(audioUri.toString())
                    conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 5000
                    conn.readTimeout = 0 // Stream until done
                    conn.setRequestProperty("User-Agent", "PC-Master-Android-App/2.0")
                    if (PcApiClient.authToken.isNotEmpty()) {
                        conn.setRequestProperty("Authorization", "Bearer ${PcApiClient.authToken}")
                        conn.setRequestProperty("X-Auth-Token", PcApiClient.authToken)
                    }
                    
                    if (conn.responseCode == 200 || conn.responseCode == 206) {
                        tempFile = File(cacheDir, "stream_cache_${System.currentTimeMillis()}.tmp")
                        conn.inputStream.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                val buffer = ByteArray(8192)
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    output.write(buffer, 0, read)
                                }
                                output.flush()
                            }
                        }
                        
                        runOnUiThread {
                            if (player != null) {
                                try {
                                    // Use file path instead of URI to avoid MediaHTTPConnection
                                    player?.setDataSource(tempFile.absolutePath)
                                    status.text = if (item.size.isNotEmpty()) "${item.size} - Playing from cache" else "Playing from cache"
                                    player?.prepareAsync()
                                } catch (_: Exception) {
                                    status.text = "Error starting playback."
                                }
                            }
                        }
                    } else {
                        runOnUiThread { status.text = "Stream failed: HTTP ${conn.responseCode}" }
                    }
                } catch (e: Exception) {
                    runOnUiThread { 
                        status.text = "Could not start audio stream. Download it to play locally." 
                        Log.e("MainActivity", "Error downloading audio stream", e)
                    }
                } finally {
                    conn?.disconnect()
                }
            }.start()

        } catch (exception: Exception) {
            status.text = "Could not start audio stream. Download it to play locally."
            Log.e("MainActivity", "Error starting audio stream", exception)
        }

        dialog.setOnDismissListener {
            updateHandler.removeCallbacks(updateProgress)
            player?.release()
            player = null
        }
        dialog.show()
    }

    private fun downloadFile(filePath: String, fileName: String) {
        val encodedPath = URLEncoder.encode(filePath, "UTF-8").replace("+", "%20")
        val tokenParam = if (PcApiClient.authToken.isNotEmpty()) "&token=${PcApiClient.authToken}" else ""
        val downloadUrl = "${PcApiClient.baseUrl}/download?path=$encodedPath$tokenParam"
        val request = DownloadManager.Request(downloadUrl.toUri()).apply {
            setTitle(fileName)
            setDescription("Downloading $fileName from PCLink Hub")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            addRequestHeader("User-Agent", "PC-Master-Android-App/2.0")
            if (PcApiClient.authToken.isNotEmpty()) {
                addRequestHeader("Authorization", "Bearer ${PcApiClient.authToken}")
                addRequestHeader("X-Auth-Token", PcApiClient.authToken)
            }
        }
        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        showToast("Download started: $fileName")
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var queriedName: String? = null
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            queriedName = cursor.getString(index)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to query displayName: ${e.message}")
            }
        }
        var resolvedName: String = queriedName ?: run {
            val path = uri.path
            if (path != null) {
                val cut = path.lastIndexOf('/')
                if (cut != -1) path.substring(cut + 1) else path
            } else {
                "upload_${System.currentTimeMillis()}"
            }
        }
        if (resolvedName.isBlank()) {
            resolvedName = "upload_${System.currentTimeMillis()}"
        }
        // Sanitize for Windows filesystem invalid characters
        resolvedName = resolvedName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        // If extension is missing, infer from MIME type
        if (!resolvedName.contains('.')) {
            val mimeType = contentResolver.getType(uri)
            if (!mimeType.isNullOrEmpty()) {
                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                if (!ext.isNullOrEmpty()) {
                    resolvedName = "$resolvedName.$ext"
                }
            }
        }
        return resolvedName
    }

    private fun handleFileUpload(uri: Uri) {
        val fileName = getFileNameFromUri(uri)
        try {
            val inputStream = applicationContext.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                pbUpload.visibility = View.VISIBLE
                showToast("Uploading $fileName to PC...")

                PcApiClient.uploadFileStream(fileName, inputStream, onProgress = {}) { success ->
                    pbUpload.visibility = View.GONE
                    if (success) {
                        showToast("Uploaded $fileName to PC Downloads!")
                        loadSharedFiles(currentFolderPath)
                    } else {
                        showToast("Failed to upload $fileName")
                    }
                }
            } else {
                showToast("Cannot read selected file")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "handleFileUpload error: ${e.message}")
            showToast("Upload error: ${e.message}")
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupTabMedia() {
        findViewById<View?>(R.id.btnOpenRemote)?.setOnClickListener {
            UniversalRemoteDialog(this).show()
        }

        btnMediaPrev.setOnClickListener { PcApiClient.sendRemoteCommand("prev") }
        btnMediaPlayPause.setOnClickListener { PcApiClient.sendRemoteCommand("playpause") }
        btnMediaNext.setOnClickListener { PcApiClient.sendRemoteCommand("next") }

        findViewById<View?>(R.id.btnVol25)?.setOnClickListener { PcApiClient.setVolume(25) }
        findViewById<View?>(R.id.btnVol50)?.setOnClickListener { PcApiClient.setVolume(50) }
        findViewById<View?>(R.id.btnVol75)?.setOnClickListener { PcApiClient.setVolume(75) }
        findViewById<View?>(R.id.btnVol100)?.setOnClickListener { PcApiClient.setVolume(100) }

        btnRefreshScreenshot.setOnClickListener {
            captureAndSaveLiveScreenshot()
        }

        btnStreamQuality?.setOnClickListener {
            // Cycle: 1080p HD (Native, q=92) -> 🔥 4K MAX (Native, q=95) -> ⚡ FAST 720p (w=1280, q=75)
            when (PcApiClient.liveStreamQuality) {
                92 -> {
                    PcApiClient.liveStreamQuality = 95
                    PcApiClient.liveStreamWidth = 0
                    btnStreamQuality?.text = "🔥 4K MAX"
                    btnStreamQuality?.setTextColor(Color.parseColor("#FF9500"))
                    showToast("Live Stream Quality: Ultra HD / 4K (95%)")
                }
                95 -> {
                    PcApiClient.liveStreamQuality = 75
                    PcApiClient.liveStreamWidth = 1280
                    btnStreamQuality?.text = "⚡ 720p"
                    btnStreamQuality?.setTextColor(Color.parseColor("#00D2FF"))
                    showToast("Live Stream Quality: Fast Mode (720p)")
                }
                else -> {
                    PcApiClient.liveStreamQuality = 92
                    PcApiClient.liveStreamWidth = 0
                    btnStreamQuality?.text = "💎 1080p HD"
                    btnStreamQuality?.setTextColor(Color.parseColor("#00FF88"))
                    showToast("Live Stream Quality: Full Native HD (92%)")
                }
            }
            if (!isLiveStreaming) {
                loadScreenshot()
            }
        }

        btnToggleLiveStream.setOnClickListener {
            if (isLiveStreaming) {
                stopLiveStreaming()
            } else {
                startLiveStreaming()
            }
        }

        // Live Screen Touch capability (Tap=Left Click, Double-Tap=Double Click, Long-Press=Right Click, Drag=Move Mouse)
        setupLiveScreenTouch(ivScreenshot, isFullscreen = false)
        btnFullscreenScreen?.setOnClickListener { showFullscreenPcScreen() }

        // Live Screen Quick Buttons
        btnQuickWin?.setOnClickListener {
            PcApiClient.sendKeyPress("win")
            tvTouchStatusHint?.text = "🪟 Windows Start key sent"
        }

        btnQuickCopy?.setOnClickListener {
            PcApiClient.sendCopy()
            tvTouchStatusHint?.text = "📋 CTRL+C (Copy) sent"
            Toast.makeText(this, "Ctrl+C sent", Toast.LENGTH_SHORT).show()
        }

        btnQuickPaste?.setOnClickListener {
            PcApiClient.sendPaste()
            tvTouchStatusHint?.text = "📄 CTRL+V (Paste) sent"
            Toast.makeText(this, "Ctrl+V sent", Toast.LENGTH_SHORT).show()
        }

        btnQuickScrollUp?.setOnClickListener {
            PcApiClient.sendMouseScroll(8f)
            tvTouchStatusHint?.text = "▲ Scrolled Up"
        }

        btnQuickScrollDown?.setOnClickListener {
            PcApiClient.sendMouseScroll(-8f)
            tvTouchStatusHint?.text = "▼ Scrolled Down"
        }

        btnQuickBw?.setOnClickListener {
            toggleBwTheme()
        }

        val sendQuickText = {
            val text = etQuickSendText?.text?.toString().orEmpty()
            if (text.isNotEmpty()) {
                PcApiClient.sendText(text) { success ->
                    if (success) {
                        tvTouchStatusHint?.text = "⌨️ Typed: \"$text\""
                        etQuickSendText?.setText("")
                    } else {
                        tvTouchStatusHint?.text = "❌ Failed to send text"
                    }
                }
            }
        }
        btnQuickSendText?.setOnClickListener { sendQuickText() }
        etQuickSendText?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                sendQuickText()
                true
            } else false
        }

        btnQuickBackspace?.setOnClickListener {
            PcApiClient.sendBackspace()
            tvTouchStatusHint?.text = "⌫ Backspace sent"
        }

        btnQuickEnter?.setOnClickListener {
            PcApiClient.sendKeyPress("enter")
            tvTouchStatusHint?.text = "↵ Enter sent"
        }

        btnLiveLock?.setOnClickListener {
            performLockPc()
        }

        btnLiveUnlock?.setOnClickListener {
            performUnlockPc()
        }
        btnLiveUnlock?.setOnLongClickListener {
            showUnlockOptionsDialog()
            true
        }

        btnLiveAntiIdle?.setOnClickListener {
            toggleAntiIdle()
        }

        btnToggleMirror.setOnClickListener {
            if (!ScreenCaptureService.isRunning) {
                try {
                    if (mediaProjectionManager == null) {
                        mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
                    }
                    val captureIntent = mediaProjectionManager?.createScreenCaptureIntent()
                    if (captureIntent != null) {
                        startMirroringLauncher.launch(captureIntent)
                    } else {
                        Toast.makeText(this, "Screen cast not supported on this device", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Cannot start Screen Cast: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                try {
                    stopService(Intent(this, ScreenCaptureService::class.java))
                } catch (_: Exception) {}
                ScreenCaptureService.captureResultData = null
                ScreenCaptureService.captureResultCode = RESULT_CANCELED
                ScreenCaptureService.isRunning = false
                isMirroringRunning = false
                btnToggleMirror.text = "START"
                btnToggleMirror.backgroundTintList = null
                tvMirrorStatus.text = "Stream this phone's screen live to PC"
                updateQuickHubStatus()
                showToast("Screen Cast Stopped")
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun startLiveStreaming() {
        if (isLiveStreaming) return
        isLiveStreaming = true
        btnToggleLiveStream.text = "⏹ PAUSE"
        btnToggleLiveStream.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF453A"))
        tvLiveStreamBadge.text = "🟢 STREAMING LIVE"
        tvLiveStreamBadge.setTextColor(Color.parseColor("#00FF88"))
        tvScreenEmptyHint?.visibility = View.GONE
        fetchNextLiveFrame()
    }

    @SuppressLint("SetTextI18n")
    private fun stopLiveStreaming() {
        if (!isLiveStreaming) return
        isLiveStreaming = false
        liveStreamHandler.removeCallbacks(liveStreamRunnable)
        btnToggleLiveStream.text = "🔴 LIVE STREAM"
        btnToggleLiveStream.backgroundTintList = null
        tvLiveStreamBadge.text = "⏸ PAUSED"
        tvLiveStreamBadge.setTextColor(Color.parseColor("#8A9BB5"))
    }

    private fun fetchNextLiveFrame() {
        if (!isLiveStreaming) return
        PcApiClient.getScreenshot { bmp ->
            if (isLiveStreaming) {
                if (bmp != null) {
                    ivScreenshot.setImageBitmap(bmp)
                    tvScreenEmptyHint?.visibility = View.GONE
                }
                // Schedule next frame with 40ms pacing (~25 FPS) for smooth live streaming
                liveStreamHandler.postDelayed(liveStreamRunnable, 40)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showFullscreenPcScreen() {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        activeFullscreenDialog = dialog
        val frameLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#050A14"))
        }

        val fullImageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            ivScreenshot.drawable?.let { setImageDrawable(it) }
            if (isBwThemeActive) {
                val cm = ColorMatrix().apply { setSaturation(0f) }
                colorFilter = ColorMatrixColorFilter(cm)
            }
        }
        setupLiveScreenTouch(fullImageView, isFullscreen = true)
        frameLayout.addView(fullImageView)

        val density = resources.displayMetrics.density
        val closeBtn = Button(this).apply {
            text = "✕ CLOSE"
            setTextColor(Color.WHITE)
            textSize = 12f
            setBackgroundResource(R.drawable.btn_scan_bg)
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                (38 * density).toInt()
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins((16 * density).toInt(), (32 * density).toInt(), (16 * density).toInt(), 0)
            }
            layoutParams = params
            setOnClickListener { dialog.dismiss() }
        }
        frameLayout.addView(closeBtn)

        var isDialogActive = true
        val dialogLoop = object : Runnable {
            override fun run() {
                if (isDialogActive && dialog.isShowing) {
                    PcApiClient.getScreenshot { bmp ->
                        if (isDialogActive && dialog.isShowing && bmp != null) {
                            fullImageView.setImageBitmap(bmp)
                            ivScreenshot.setImageBitmap(bmp)
                        }
                        if (isDialogActive && dialog.isShowing) {
                            liveStreamHandler.postDelayed(this, 40)
                        }
                    }
                }
            }
        }
        liveStreamHandler.post(dialogLoop)

        dialog.setOnDismissListener {
            isDialogActive = false
            activeFullscreenDialog = null
        }

        dialog.setContentView(frameLayout)
        dialog.show()
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun setupLiveScreenTouch(imageView: ImageView, isFullscreen: Boolean = false) {
        var lastMoveTime = 0L

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val coords = mapTouchToPcCoords(imageView, e.x, e.y)
                if (coords != null) {
                    val (pcX, pcY) = coords
                    PcApiClient.sendMouseClickAt(pcX, pcY, "left")
                    val msg = "👆 Left Click at ($pcX, $pcY)"
                    tvTouchStatusHint?.text = msg
                    if (isFullscreen) Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val coords = mapTouchToPcCoords(imageView, e.x, e.y)
                if (coords != null) {
                    val (pcX, pcY) = coords
                    PcApiClient.sendMouseClickAt(pcX, pcY, "double")
                    val msg = "✌️ Double Click at ($pcX, $pcY)"
                    tvTouchStatusHint?.text = msg
                    if (isFullscreen) Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                val coords = mapTouchToPcCoords(imageView, e.x, e.y)
                if (coords != null) {
                    val (pcX, pcY) = coords
                    PcApiClient.sendMouseClickAt(pcX, pcY, "right")
                    val msg = "🖱️ Right Click at ($pcX, $pcY)"
                    tvTouchStatusHint?.text = msg
                    if (isFullscreen) Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                }
            }
        })

        imageView.setOnTouchListener { v, event ->
            v.parent?.requestDisallowInterceptTouchEvent(true)
            gestureDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_MOVE) {
                val now = System.currentTimeMillis()
                if (now - lastMoveTime > 40) {
                    lastMoveTime = now
                    val coords = mapTouchToPcCoords(imageView, event.x, event.y)
                    if (coords != null) {
                        val (pcX, pcY) = coords
                        PcApiClient.sendMouseMoveTo(pcX, pcY)
                        tvTouchStatusHint?.text = "🖱 Cursor at ($pcX, $pcY)"
                    }
                }
            } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                v.parent?.requestDisallowInterceptTouchEvent(false)
            }
            true
        }
    }

    private fun mapTouchToPcCoords(imageView: ImageView, eventX: Float, eventY: Float): Pair<Int, Int>? {
        val drawable = imageView.drawable ?: return null
        val dw = drawable.intrinsicWidth.toFloat()
        val dh = drawable.intrinsicHeight.toFloat()
        val vw = imageView.width.toFloat()
        val vh = imageView.height.toFloat()

        if (dw <= 0f || dh <= 0f || vw <= 0f || vh <= 0f) return null

        val scale = minOf(vw / dw, vh / dh)
        val renderW = dw * scale
        val renderH = dh * scale
        val offsetX = (vw - renderW) / 2f
        val offsetY = (vh - renderH) / 2f

        val imgX = eventX - offsetX
        val imgY = eventY - offsetY

        if (imgX !in 0f..renderW || imgY !in 0f..renderH) {
            return null
        }

        val normX = (imgX / renderW).coerceIn(0f, 1f)
        val normY = (imgY / renderH).coerceIn(0f, 1f)

        val pcX = (normX * PcApiClient.pcScreenWidth).toInt().coerceIn(0, PcApiClient.pcScreenWidth - 1)
        val pcY = (normY * PcApiClient.pcScreenHeight).toInt().coerceIn(0, PcApiClient.pcScreenHeight - 1)
        return Pair(pcX, pcY)
    }

    private fun toggleBwTheme() {
        isBwThemeActive = !isBwThemeActive
        prefs.edit { putBoolean("pref_bw_theme", isBwThemeActive) }
        applyBwTheme(isBwThemeActive)
    }

    @SuppressLint("SetTextI18n")
    private fun applyBwTheme(active: Boolean) {
        val decorView = window.decorView
        if (active) {
            val cm = ColorMatrix().apply { setSaturation(0f) }
            val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
            decorView.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
            val bwFilter = ColorMatrixColorFilter(cm)
            ivScreenshot.colorFilter = bwFilter
            btnQuickBw?.text = "🎨 COLOR"
            btnQuickBw?.setTextColor(Color.parseColor("#FFFFFF"))
            Toast.makeText(this, "🌓 Black & White Theme Enabled", Toast.LENGTH_SHORT).show()
        } else {
            decorView.setLayerType(View.LAYER_TYPE_NONE, null)
            ivScreenshot.colorFilter = null
            btnQuickBw?.text = "🌓 B&W"
            btnQuickBw?.setTextColor(Color.parseColor("#FFD700"))
            Toast.makeText(this, "🎨 Full Color Theme Restored", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadScreenshot() {
        PcApiClient.getScreenshot { bmp ->
            if (bmp != null) {
                ivScreenshot.setImageBitmap(bmp)
                tvScreenEmptyHint?.visibility = View.GONE
            }
        }
    }

    private fun captureAndSaveLiveScreenshot() {
        // Shutter flash animation effect
        ivScreenshot.animate().alpha(0.3f).setDuration(80).withEndAction {
            ivScreenshot.animate().alpha(1.0f).setDuration(120).start()
        }.start()

        if (isLiveStreaming) {
            // Live stream is actively running: capture current frame instantly
            val currentDrawable = ivScreenshot.drawable as? BitmapDrawable
            val currentBitmap = currentDrawable?.bitmap
            if (currentBitmap != null && !currentBitmap.isRecycled) {
                try {
                    val bmpCopy = currentBitmap.copy(currentBitmap.config, false)
                    if (bmpCopy != null) {
                        saveBitmapToPhoneGallery(bmpCopy)
                        return
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error copying live frame: ${e.message}")
                }
            }
        }

        // If not streaming or couldn't copy live frame, fetch fresh high-res screenshot from PC API
        showToast("📸 Capturing screenshot from PC...")
        PcApiClient.getScreenshot { bmp ->
            if (bmp != null) {
                ivScreenshot.setImageBitmap(bmp)
                tvScreenEmptyHint?.visibility = View.GONE
                saveBitmapToPhoneGallery(bmp)
            } else {
                // Fallback: check if we have any existing image displayed
                val fallbackBmp = (ivScreenshot.drawable as? BitmapDrawable)?.bitmap
                if (fallbackBmp != null && !fallbackBmp.isRecycled) {
                    val bmpCopy = fallbackBmp.copy(fallbackBmp.config, false)
                    if (bmpCopy != null) {
                        saveBitmapToPhoneGallery(bmpCopy)
                        return@getScreenshot
                    }
                }
                showToast("❌ Failed to capture screenshot")
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun saveBitmapToPhoneGallery(bitmap: Bitmap) {
        Thread {
            try {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "PCMaster_Snap_$timeStamp.png"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PCMaster")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }

                    val resolver = contentResolver
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { outStream ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                            outStream.flush()
                        }
                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)

                        runOnUiThread {
                            Toast.makeText(this, "📸 Saved to Photos (Pictures/PCMaster)!", Toast.LENGTH_SHORT).show()
                            tvTouchStatusHint?.text = "📸 Photo saved: $fileName"
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this, "Failed to create photo in gallery", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    // Pre-Android 10
                    val picturesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "PCMaster")
                    if (!picturesDir.exists()) {
                        picturesDir.mkdirs()
                    }
                    val imageFile = File(picturesDir, fileName)
                    FileOutputStream(imageFile).use { outStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                        outStream.flush()
                    }
                    MediaScannerConnection.scanFile(this, arrayOf(imageFile.absolutePath), arrayOf("image/png"), null)

                    runOnUiThread {
                        Toast.makeText(this, "📸 Saved to Photos (Pictures/PCMaster)!", Toast.LENGTH_SHORT).show()
                        tvTouchStatusHint?.text = "📸 Photo saved: $fileName"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Error saving photo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun stopPolling() {
        isPolling = false
    }

    @SuppressLint("SetTextI18n")
    private fun startPolling() {
        if (isPolling) return
        isPolling = true

        val pollRunnable = object : Runnable {
            override fun run() {
                if (!isPolling) return

                PcApiClient.ping { success, latency ->
                    if (success) {
                        statusText.text = getString(R.string.connected)
                        statusText.setTextColor(Color.parseColor("#00FF88"))
                        statusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00FF88"))
                        statusPillHeader?.setBackgroundResource(R.drawable.status_pill_connected)
                        tvTelemetryStatus?.text = "Live • Connected"
                        telemetryStatusDot?.backgroundTintList = ColorStateList.valueOf("#00FF88".toColorInt())

                        tvLatency.text = "${latency}ms"
                        tvLatency.visibility = View.VISIBLE
                    } else {
                        statusText.text = getString(R.string.disconnected)
                        statusText.setTextColor("#FF453A".toColorInt())
                        statusDot.backgroundTintList = ColorStateList.valueOf("#FF453A".toColorInt())
                        statusPillHeader?.setBackgroundResource(R.drawable.status_pill_disconnected)
                        tvTelemetryStatus?.text = "Waiting for connection..."
                        telemetryStatusDot?.backgroundTintList = ColorStateList.valueOf("#8A9BB5".toColorInt())

                        tvLatency.visibility = View.GONE
                    }
                }

                // If currently on Dashboard tab, fetch live telemetry stats
                if (tabDashboardView.isVisible) {
                    PcApiClient.getStats { json ->
                        if (json != null) {
                            updateDashboardUi(json)
                        }
                    }
                }

                mainHandler.postDelayed(this, 2500)
            }
        }
        mainHandler.post(pollRunnable)
    }

    @SuppressLint("SetTextI18n")
    private fun updateDashboardUi(data: JSONObject) {
        // CPU Load & Temp
        val cpuLoad = data.optDouble("cpu", data.optDouble("cpuLoad", 0.0)).toInt()
        val cpuTemp = data.optDouble("cpuTemp", 0.0).toInt()
        tvCpuLoad.text = "$cpuLoad%"
        pbCpu?.progress = cpuLoad
        cmvCpu?.setProgress(cpuLoad.toFloat())
        sparklineCpu?.addPoint(cpuLoad.toFloat())
        tvCpuTemp.text = if (cpuTemp > 0) "Temp: $cpuTemp °C" else "Temp: -- °C"

        // RAM Load & Usage
        val ramUsed = data.optDouble("ramUsed", data.optDouble("ramUsedGb", 0.0))
        val ramTotal = data.optDouble("ramTotal", data.optDouble("ramTotalGb", 0.0))
        val ramLoad = if (ramTotal > 0.0) ((ramUsed / ramTotal) * 100).toInt().coerceIn(0, 100) else data.optDouble("ram", 0.0).toInt()
        tvRamLoad.text = "$ramLoad%"
        pbRam?.progress = ramLoad
        cmvRam?.setProgress(ramLoad.toFloat())
        sparklineRam?.addPoint(ramLoad.toFloat())
        if (ramUsed > 0 && ramTotal > 0) {
            tvRamUsed.text = String.format(Locale.getDefault(), "%.1f / %.1f GB", ramUsed, ramTotal)
        } else {
            tvRamUsed.text = "$ramLoad% in use"
        }

        // GPU Load & Temp
        val gpuLoad = data.optDouble("gpu", data.optDouble("gpuLoad", 0.0)).toInt()
        val gpuTemp = data.optDouble("gpuTemp", 0.0).toInt()
        tvGpuLoad.text = "$gpuLoad%"
        pbGpu?.progress = gpuLoad
        cmvGpu?.setProgress(gpuLoad.toFloat())
        sparklineGpu?.addPoint(gpuLoad.toFloat())
        tvGpuTemp.text = if (gpuTemp > 0) "Temp: $gpuTemp °C" else "Temp: -- °C"

        // Battery & Power
        val battery = data.optInt("battery", data.optInt("batteryPct", 100))
        val isCharging = data.optBoolean("isCharging", true)
        tvBatteryPct.text = "$battery%"
        pbBattery?.progress = battery
        cmvBattery?.setProgress(battery.toFloat())
        sparklineBattery?.addPoint(battery.toFloat())
        tvBatteryStatus.text = if (isCharging) "Power: AC Adapter" else "Power: Battery Discharge"

        // PC Health Quick Chips & Status Badge
        try {
            val diskUsed = data.optDouble("storageUsed", data.optDouble("storageUsedGb", 0.0))
            val diskTotal = data.optDouble("storageTotal", data.optDouble("storageTotalGb", 0.0))
            val diskPct = if (diskTotal > 0.0) ((diskUsed / diskTotal) * 100).toInt().coerceIn(0, 100) else 35

            var healthScore = 100
            if (cpuLoad > 90) healthScore -= 25 else if (cpuLoad > 75) healthScore -= 10
            if (cpuTemp > 85) healthScore -= 25 else if (cpuTemp > 75) healthScore -= 10
            if (ramLoad > 90) healthScore -= 20 else if (ramLoad > 80) healthScore -= 10
            if (diskPct > 95) healthScore -= 20 else if (diskPct > 85) healthScore -= 10
            healthScore = healthScore.coerceIn(10, 100)

            val healthColor = when {
                healthScore >= 90 -> "#00FF88"
                healthScore >= 75 -> "#00D2FF"
                healthScore >= 55 -> "#FFB800"
                else -> "#FF453A"
            }
            val healthGrade = when {
                healthScore >= 90 -> "HEALTHY"
                healthScore >= 75 -> "GOOD"
                healthScore >= 55 -> "ATTENTION"
                else -> "CRITICAL"
            }

            tvHealthScoreBadge?.text = "$healthGrade • $healthScore%"
            tvHealthScoreBadge?.setTextColor(Color.parseColor(healthColor))
            dotHealthStatus?.backgroundTintList = ColorStateList.valueOf(Color.parseColor(healthColor))

            tvHealthChipCpu?.text = if (cpuLoad < 70 && cpuTemp < 75) "Optimal" else "${cpuLoad}%"
            tvHealthChipCpu?.setTextColor(Color.parseColor(if (cpuLoad < 70 && cpuTemp < 75) "#00FF88" else "#FFB800"))

            tvHealthChipRam?.text = if (ramLoad < 80) "Normal" else "${ramLoad}%"
            tvHealthChipRam?.setTextColor(Color.parseColor(if (ramLoad < 80) "#00D2FF" else "#FFB800"))

            tvHealthChipDisk?.text = if (diskPct < 85) "Clean" else "${diskPct}%"
            tvHealthChipDisk?.setTextColor(Color.parseColor(if (diskPct < 85) "#00FF88" else "#FFB800"))

            val curLatency = tvLatency.text.toString().trim()
            tvHealthChipLan?.text = curLatency.ifEmpty { "Fast" }
        } catch (_: Exception) {}

        // Anti-Idle state sync from PC Hub telemetry
        if (data.has("antiIdleActive") || data.has("caffeineActive")) {
            val pcAntiIdle = data.optBoolean("antiIdleActive", data.optBoolean("caffeineActive", false))
            if (pcAntiIdle != isAntiIdleActive) {
                updateAntiIdleUi(pcAntiIdle)
            }
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()
        val savedPin = getSharedPreferences("pc_master", MODE_PRIVATE).getString("saved_windows_pin", "")
        if (!savedPin.isNullOrEmpty()) {
            tvUnlockStatus?.text = "1-Tap Ready"
            tvUnlockStatus?.setTextColor(Color.parseColor("#00FF88"))
        } else {
            tvUnlockStatus?.text = "Wake / Enter PIN"
            tvUnlockStatus?.setTextColor(Color.parseColor("#8A9BB5"))
        }

        // Refresh connection status and restart polling if connected
        if (PcApiClient.baseUrl.isNotEmpty() && statusText.text == getString(R.string.connected)) {
            startPolling()
            PcApiClient.fetchScreenInfo()
            updateQuickHubStatus()
        }

        // Restore screen mirroring state
        if (ScreenCaptureService.isRunning) {
            isMirroringRunning = true
            btnToggleMirror.text = "STOP"
            btnToggleMirror.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF453A"))
            tvMirrorStatus.text = "🟢 Active Screen Cast to PC Hub"
        } else {
            isMirroringRunning = false
            btnToggleMirror.text = "START"
            btnToggleMirror.backgroundTintList = null
            tvMirrorStatus.text = "Stream this phone's screen live to PC"
        }
    }

    private fun isNotificationAccessGranted(): Boolean {
        val granted = try {
            val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
            enabledListeners.contains(packageName)
        } catch (_: Exception) {
            false
        }
        if (granted) {
            try {
                android.service.notification.NotificationListenerService.requestRebind(
                    android.content.ComponentName(this, NotificationService::class.java)
                )
            } catch (_: Exception) {}
        }
        return granted
    }

    @SuppressLint("SetTextI18n")
    private fun updateQuickHubStatus() {
        if (isNotificationAccessGranted()) {
            tvQuickNotifStatus?.text = "🟢 Active"
            tvQuickNotifStatus?.setTextColor(Color.parseColor("#00FF88"))
        } else {
            tvQuickNotifStatus?.text = "Tap to Grant"
            tvQuickNotifStatus?.setTextColor(Color.parseColor("#FFB800"))
        }

        if (ScreenCaptureService.isRunning) {
            tvQuickMirrorStatus?.text = "🟢 Casting (STOP)"
            tvQuickMirrorStatus?.setTextColor(Color.parseColor("#00FF88"))
        } else {
            tvQuickMirrorStatus?.text = "Tap to Cast"
            tvQuickMirrorStatus?.setTextColor(Color.parseColor("#00D2FF"))
        }
    }

    override fun onPause() {
        super.onPause()
        stopLiveStreaming()
    }

    override fun onStop() {
        super.onStop()
        // Don't stop live streaming here - let it continue in background if needed
        // stopLiveStreaming()
    }

    override fun onDestroy() {
        super.onDestroy()
        isPolling = false
        stopLiveStreaming()
        
        // Clean up fullscreen dialog if showing
        activeFullscreenDialog?.dismiss()
        activeFullscreenDialog = null
        
        try {
            unregisterReceiver(ringBroadcastReceiver)
        } catch (_: Exception) {}

        mainHandler.removeCallbacksAndMessages(null)
        liveStreamHandler.removeCallbacksAndMessages(null)
    }
}
