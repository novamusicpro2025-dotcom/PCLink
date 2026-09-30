package com.pcmaster.mobile.ui.main

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.pcmaster.mobile.ProcessItem
import com.pcmaster.mobile.FileItem
import com.pcmaster.mobile.NotificationItem
import com.pcmaster.mobile.R
import com.pcmaster.mobile.api.ApiClient
import com.pcmaster.mobile.service.ScreenCaptureService
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Connection Views
    private lateinit var statusPillHeader: View
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var tvLatency: TextView
    private lateinit var ipBadgeText: TextView
    private lateinit var togglePanelBtn: View
    private lateinit var connectionInputPanel: View
    private lateinit var ipInput: EditText
    private lateinit var portInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var connectBtn: View
    private lateinit var autoDetectBtn: View
    private lateinit var btnRecent: View
    private lateinit var btnSettings: View
    private lateinit var btnMoreMenu: View
    private lateinit var btnToggleTokenVisibility: ImageView
    private var isTokenVisible = false

    // Tab Container Views
    private lateinit var tabDashboardView: View
    private lateinit var tabTrackpadView: View
    private lateinit var tabAppsView: View
    private lateinit var tabFilesView: View
    private lateinit var tabMediaView: View
    private lateinit var bottomNavigation: BottomNavigationView

    // Tab 1 Views (Dashboard)
    private lateinit var tvTelemetryStatus: TextView
    private lateinit var telemetryStatusDot: View
    private lateinit var tvCpuLoad: TextView
    private lateinit var pbCpu: ProgressBar
    private lateinit var tvCpuTemp: TextView
    private lateinit var cmvCpu: com.pcmaster.mobile.CircularMetricView
    private lateinit var sparklineCpu: com.pcmaster.mobile.SparklineView

    private lateinit var tvRamLoad: TextView
    private lateinit var pbRam: ProgressBar
    private lateinit var tvRamUsed: TextView
    private lateinit var cmvRam: com.pcmaster.mobile.CircularMetricView
    private lateinit var sparklineRam: com.pcmaster.mobile.SparklineView

    private lateinit var tvGpuLoad: TextView
    private lateinit var pbGpu: ProgressBar
    private lateinit var tvGpuTemp: TextView
    private lateinit var cmvGpu: com.pcmaster.mobile.CircularMetricView
    private lateinit var sparklineGpu: com.pcmaster.mobile.SparklineView

    private lateinit var tvBatteryPct: TextView
    private lateinit var pbBattery: ProgressBar
    private lateinit var tvBatteryStatus: TextView
    private lateinit var cmvBattery: com.pcmaster.mobile.CircularMetricView
    private lateinit var sparklineBattery: com.pcmaster.mobile.SparklineView

    private lateinit var tvAntiIdleStatus: TextView
    private lateinit var tvCaffeineStatus: TextView
    private var isCaffeineActive = false
    private var isAntiIdleActive = false
    private lateinit var btnUnlockPc: View
    private lateinit var tvUnlockStatus: TextView
    private lateinit var btnAntiIdle: View
    private lateinit var btnCustomizeActions: View

    // Tab 2 Views (Trackpad)
    private lateinit var nativeTrackpad: com.pcmaster.mobile.TrackpadView
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
    private val fileList = mutableListOf<FileItem>()
    private var currentFolderPath = ""

    // Tab 5 Views (Media & Screen)
    private lateinit var tvMediaTitle: TextView
    private lateinit var btnMediaPrev: Button
    private lateinit var btnMediaPlayPause: Button
    private lateinit var btnMediaNext: Button
    private lateinit var ivScreenshot: ImageView
    private lateinit var btnRefreshScreenshot: Button
    private lateinit var btnToggleLiveStream: Button
    private lateinit var tvLiveStreamBadge: TextView
    private lateinit var tvScreenEmptyHint: TextView
    private lateinit var btnFullscreenScreen: ImageButton
    private lateinit var btnToggleMirror: Button
    private lateinit var tvMirrorStatus: TextView
    private var isMirroringRunning = false
    private lateinit var btnStreamQuality: Button

    // Live Screen Quick Controls
    private lateinit var tvTouchStatusHint: TextView
    private lateinit var btnQuickWin: Button
    private lateinit var btnQuickCopy: Button
    private lateinit var btnQuickPaste: Button
    private lateinit var btnQuickScrollUp: Button
    private lateinit var btnQuickScrollDown: Button
    private lateinit var btnQuickBw: Button
    private lateinit var etQuickSendText: EditText
    private lateinit var btnQuickSendText: Button
    private lateinit var btnQuickBackspace: Button
    private lateinit var btnQuickEnter: Button
    private lateinit var btnLiveLock: Button
    private lateinit var btnLiveUnlock: Button
    private lateinit var btnLiveAntiIdle: Button
    private var isBwThemeActive = false

    // MediaProjection for screen mirroring
    private var mediaProjectionManager: MediaProjectionManager? = null
    private val startMirroringLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        val resultData = result.data
        if (result.resultCode == Activity.RESULT_OK && resultData != null) {
            try {
                ScreenCaptureService.captureResultCode = result.resultCode
                ScreenCaptureService.captureResultData = resultData
                ScreenCaptureService.captureServerIp = ApiClient.getBaseUrl()

                val intent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra("resultCode", result.resultCode)
                    putExtra("data", resultData)
                    putExtra("serverIp", ApiClient.getBaseUrl())
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                isMirroringRunning = true
                ScreenCaptureService.isRunning = true
                btnToggleMirror.text = "STOP"
                btnToggleMirror.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF453A"))
                tvMirrorStatus.text = "🟢 Active Screen Cast to PC Hub"
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
    private val pickFileLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            handleFileUpload(uri)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
        
        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        initViews()
        setupObservers()
        setupTopBar()
        setupBottomNavigation()
        setupTabDashboard()
        setupTabTrackpad()
        setupTabApps()
        setupTabFiles()
        setupTabMedia()

        // Restore saved B&W theme
        val prefs = getSharedPreferences("pc_master", Context.MODE_PRIVATE)
        isBwThemeActive = prefs.getBoolean("pref_bw_theme", false)
        if (isBwThemeActive) {
            applyBwTheme(true)
        }

        // Restore saved settings and attempt connection
        val savedIp = prefs.getString("pc_ip", "127.0.0.1") ?: "127.0.0.1"
        val savedPort = prefs.getString("pc_port", "8099") ?: "8099"
        val savedToken = prefs.getString("pc_token", "") ?: ""

        ipInput.setText(savedIp)
        portInput.setText(savedPort)
        tokenInput.setText(savedToken)

        // Attempt initial auto-connection
        mainViewModel.connect(savedIp, savedPort, savedToken)
    }

    private fun initViews() {
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
        btnRecent = findViewById(R.id.btnRecent)
        btnSettings = findViewById(R.id.btnSettings)
        btnMoreMenu = findViewById(R.id.btnMoreMenu)
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
        tvCaffeineStatus = findViewById(R.id.tvCaffeineStatus)
        btnAntiIdle = findViewById(R.id.btnAntiIdle)
        btnUnlockPc = findViewById(R.id.btnUnlockPc)
        tvUnlockStatus = findViewById(R.id.tvUnlockStatus)
        btnCustomizeActions = findViewById(R.id.btnCustomizeActions)

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

        // Live Screen Quick Controls
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

    private fun setupObservers() {
        lifecycleScope.launch {
            mainViewModel.isConnected.collect { connected ->
                updateConnectionUI(connected)
            }
        }

        lifecycleScope.launch {
            mainViewModel.connectionStatus.collect { status ->
                runOnUiThread {
                    statusText.text = status
                }
            }
        }

        lifecycleScope.launch {
            mainViewModel.cpuLoad.collect { load ->
                runOnUiThread {
                    tvCpuLoad.text = "${load.toInt()}%"
                    pbCpu.progress = load.toInt()
                    cmvCpu.setProgress(load)
                }
            }
        }

        lifecycleScope.launch {
            mainViewModel.cpuTemp.collect { temp ->
                runOnUiThread {
                    tvCpuTemp.text = if (temp > 0) "${temp.toInt()}°C" else "--°C"
                }
            }
        }

        lifecycleScope.launch {
            mainViewModel.gpuLoad.collect { load ->
                runOnUiThread {
                    tvGpuLoad.text = "${load.toInt()}%"
                    pbGpu.progress = load.toInt()
                    cmvGpu.setProgress(load)
                }
            }
        }

        lifecycleScope.launch {
            mainViewModel.gpuTemp.collect { temp ->
                runOnUiThread {
                    tvGpuTemp.text = if (temp > 0) "${temp.toInt()}°C" else "--°C"
                }
            }
        }

        lifecycleScope.launch {
            mainViewModel.ramUsage.collect { usage ->
                runOnUiThread {
                    tvRamLoad.text = "${usage.toInt()}%"
                    pbRam.progress = usage.toInt()
                    cmvRam.setProgress(usage)
                    tvRamUsed.text = String.format("%.1f / %.1f GB", 
                        mainViewModel.ramUsed.value, mainViewModel.ramTotal.value)
                }
            }
        }

        lifecycleScope.launch {
            mainViewModel.battery.collect { battery ->
                runOnUiThread {
                    tvBatteryPct.text = battery
                }
            }
        }

        lifecycleScope.launch {
            mainViewModel.notifications.observe(this) { notifications ->
                // Update notifications UI if needed
            }
        }

        lifecycleScope.launch {
            mainViewModel.processes.observe(this) { processes ->
                processList.clear()
                processList.addAll(processes)
                processAdapter.notifyDataSetChanged()
            }
        }

        lifecycleScope.launch {
            mainViewModel.files.observe(this) { files ->
                fileList.clear()
                fileList.addAll(files)
                // Update files adapter
            }
        }

        lifecycleScope.launch {
            mainViewModel.isMirroring.collect { mirroring ->
                runOnUiThread {
                    isMirroringRunning = mirroring
                    btnToggleMirror.text = if (mirroring) "STOP" else "START"
                    btnToggleMirror.backgroundTintList = ColorStateList.valueOf(
                        if (mirroring) Color.parseColor("#FF453A") else Color.parseColor("#00D2FF")
                    )
                    tvMirrorStatus.text = if (mirroring) "🟢 Active Screen Cast to PC Hub" else "Screen Cast Stopped"
                }
            }
        }
    }

    private fun updateConnectionUI(connected: Boolean) {
        runOnUiThread {
            val color = if (connected) Color.parseColor("#00FF88") else Color.parseColor("#FF453A")
            val dimColor = if (connected) Color.parseColor("#00FF88") else Color.parseColor("#8A9BB5")
            
            statusText.setTextColor(color)
            statusDot.backgroundTintList = ColorStateList.valueOf(color)
            tvTelemetryStatus?.text = if (connected) "Live • Connected" else "Waiting for connection..."
            telemetryStatusDot?.backgroundTintList = ColorStateList.valueOf(color)
            
            statusPillHeader?.setBackgroundResource(
                if (connected) R.drawable.status_pill_connected else R.drawable.status_pill_disconnected
            )
            
            ipBadgeText.visibility = if (connected) View.VISIBLE else View.GONE
            tvLatency.visibility = if (connected) View.VISIBLE else View.GONE
        }
    }

    private fun setupTopBar() {
        togglePanelBtn.setOnClickListener {
            connectionInputPanel.visibility = if (connectionInputPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        connectBtn.setOnClickListener {
            val ip = ipInput.text.toString().trim()
            val port = portInput.text.toString().trim().ifEmpty { "8099" }
            val token = tokenInput.text.toString().trim()
            mainViewModel.connect(ip, port, token)
        }

        autoDetectBtn.setOnClickListener {
            Toast.makeText(this, "Scanning for PC Master Hub on Wi-Fi...", Toast.LENGTH_SHORT).show()
            mainViewModel.discoverHub()
        }

        btnRecent?.setOnClickListener {
            val recentIps = arrayOf(
                "10.145.56.60 (Current Wi-Fi)",
                "192.168.1.100 (Default LAN)",
                "127.0.0.1 (Localhost ADB)",
                "192.168.0.105 (Alternative)"
            )
            AlertDialog.Builder(this)
                .setTitle("Recent PC Master Hubs")
                .setItems(recentIps) { _, which ->
                    val chosenIp = recentIps[which].substringBefore(" ")
                    ipInput.setText(chosenIp)
                    mainViewModel.connect(chosenIp, portInput.text.toString().trim().ifEmpty { "8099" }, tokenInput.text.toString().trim())
                }
                .show()
        }

        btnToggleTokenVisibility.setOnClickListener {
            isTokenVisible = !isTokenVisible
            if (isTokenVisible) {
                tokenInput.transformationMethod = HideReturnsTransformationMethod.getInstance()
                btnToggleTokenVisibility.setColorFilter(Color.parseColor("#00D2FF"))
            } else {
                tokenInput.transformationMethod = PasswordTransformationMethod.getInstance()
                btnToggleTokenVisibility.setColorFilter(Color.parseColor("#4A5F80"))
            }
            tokenInput.setSelection(tokenInput.text.length)
        }

        btnSettings?.setOnClickListener { showSettingsMenuDialog() }
        btnMoreMenu?.setOnClickListener { showSettingsMenuDialog() }
    }

    private fun applyBwFilterToDialog(dialog: Dialog) {
        if (isBwThemeActive) {
            val cm = ColorMatrix().apply { setSaturation(0f) }
            val filter = ColorMatrixColorFilter(cm)
            val paint = Paint().apply { colorFilter = filter }
            dialog.window?.decorView?.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
        }
    }

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

        val activeIp = if (ApiClient.getBaseUrl().isNotEmpty()) ApiClient.getBaseUrl() else "No PC Linked"
        tvStatus?.text = "Status: ${statusText.text} • $activeIp"
        statusDotView?.backgroundTintList = statusDot.backgroundTintList
        if (ApiClient.getAuthToken().isNotEmpty()) {
            tvSecurityBadge?.text = "🔒 Encrypted"
            tvSecurityBadge?.setTextColor(Color.parseColor("#00FF88"))
        } else {
            tvSecurityBadge?.text = "🔓 Open LAN"
            tvSecurityBadge?.setTextColor(Color.parseColor("#00D2FF"))
        }

        val btnHowToUse = dialog.findViewById<View>(R.id.btnMenuHowToUse)
        val btnAboutApp = dialog.findViewById<View>(R.id.btnMenuAboutApp)
        val btnPrivacyPolicy = dialog.findViewById<View>(R.id.btnMenuPrivacyPolicy)
        val btnThemeToggle = dialog.findViewById<View>(R.id.btnMenuThemeToggle)
        val btnClose = dialog.findViewById<Button>(R.id.btnMenuClose)

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
                tvThemeBadge?.setTextColor(Color.parseColor("#00FF88"))
            } else {
                tvThemeIcon?.text = "🌓"
                tvThemeTitle?.text = "Black & White Theme"
                tvThemeSub?.text = "Switch between high-contrast monochrome and color"
                tvThemeBadge?.text = "TOGGLE"
                tvThemeBadge?.setTextColor(Color.parseColor("#00D2FF"))
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
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open link: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPlayStoreApp(packageName: String, webUrl: String) {
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(marketIntent)
        } catch (e: Exception) {
            openUrl(webUrl)
        }
    }

    private fun sendContactEmail() {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:novamusicpro2025@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "PC Master - Bug Report / Feature Request")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Hello PC Master Team,\n\n[Describe your bug, feedback, or feature request here]\n\nApp Version: 2.1.0\nDevice: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})\n"
                )
            }
            startActivity(Intent.createChooser(intent, "Send Email via"))
        } catch (e: Exception) {
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

        dialog.findViewById<Button>(R.id.btnPrivacyPolicyClose)?.setOnClickListener {
            dialog.dismiss()
        }

        applyBwFilterToDialog(dialog)
        dialog.show()
    }

    private fun toggleBwTheme() {
        isBwThemeActive = !isBwThemeActive
        applyBwTheme(isBwThemeActive)
        prefs.edit().putBoolean("pref_bw_theme", isBwThemeActive).apply()
    }

    private fun applyBwTheme(active: Boolean) {
        if (active) {
            val cm = ColorMatrix().apply { setSaturation(0f) }
            val filter = ColorMatrixColorFilter(cm)
            val paint = Paint().apply { colorFilter = filter }
            window?.decorView?.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
        } else {
            window?.decorView?.setLayerType(View.LAYER_TYPE_NONE, null)
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
                    tabDashboardView.visibility = View.VISIBLE
                    true
                }
                R.id.menu_trackpad -> {
                    tabTrackpadView.visibility = View.VISIBLE
                    true
                }
                R.id.menu_apps -> {
                    tabAppsView.visibility = View.VISIBLE
                    mainViewModel.fetchProcesses()
                    true
                }
                R.id.menu_files -> {
                    tabFilesView.visibility = View.VISIBLE
                    mainViewModel.loadFiles(currentFolderPath)
                    true
                }
                R.id.menu_media -> {
                    tabMediaView.visibility = View.VISIBLE
                    true
                }
                else -> false
            }
        }
    }

    private fun setupTabDashboard() {
        findViewById<View?>(R.id.btnRamBoost)?.setOnClickListener {
            mainViewModel.sendRemoteCommand("boostram")
            showToast("⚡ RAM Boost Executed!")
        }

        findViewById<View?>(R.id.btnEmptyTrash)?.setOnClickListener {
            mainViewModel.sendRemoteCommand("emptytrash")
            showToast("🗑️ Recycle Bin Cleared!")
        }

        val antiIdleClickListener = View.OnClickListener { toggleAntiIdle() }
        findViewById<View?>(R.id.btnAntiIdle)?.setOnClickListener(antiIdleClickListener)

        findViewById<View?>(R.id.btnMuteAudio)?.setOnClickListener {
            mainViewModel.sendRemoteCommand("mute")
            showToast("Audio Mute Toggled")
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
                    mainViewModel.sendRemoteCommand("sleep")
                    showToast("PC entering sleep mode...")
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<View?>(R.id.btnRestartPc)?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Restart PC?")
                .setMessage("Are you sure you want to restart the connected PC?")
                .setPositiveButton("Restart") { _, _ ->
                    mainViewModel.sendRemoteCommand("restart")
                    showToast("PC is rebooting...")
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<View?>(R.id.btnShutdown)?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Shut down PC?")
                .setMessage("This will power off the connected PC.")
                .setPositiveButton("Shutdown") { _, _ ->
                    mainViewModel.sendRemoteCommand("shutdown")
                    showToast("PC is shutting down...")
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

    private fun performLockPc() {
        mainViewModel.sendRemoteCommand("lock") { success ->
            if (success) {
                showToast("🔒 Workstation Locked!")
                tvTouchStatusHint?.text = "🔒 Workstation Locked"
            } else {
                showToast("Failed to lock PC")
            }
        }
    }

    private fun performUnlockPc() {
        val savedPin = prefs.getString("saved_windows_pin", "") ?: ""
        if (savedPin.isNotEmpty()) {
            showToast("🔓 Waking Screen & Entering PIN...")
            tvTouchStatusHint?.text = "🔓 Waking Screen & Entering PIN..."
            mainViewModel.sendRemoteCommand("unlock", mapOf("pin" to savedPin)) { success ->
                if (success) showToast("✅ Unlock command sent!")
            }
        } else {
            showUnlockOptionsDialog()
        }
    }

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
                    prefs.edit().remove("saved_windows_pin").apply()
                    showToast("Saved PIN removed")
                    tvUnlockStatus?.text = "Wake & Login"
                }
            }
            container.addView(btnClear)
        }

        builder.setView(container)

        builder.setPositiveButton("UNLOCK") { _, _ ->
            val typedPin = etPin.text.toString().trim()
            val effectivePin = if (typedPin.isNotEmpty()) typedPin else savedPin

            if (cbRemember.isChecked && typedPin.isNotEmpty()) {
                prefs.edit().putString("saved_windows_pin", typedPin).apply()
                tvUnlockStatus?.text = "1-Tap Ready"
            }

            if (effectivePin.isNotEmpty()) {
                showToast("🔓 Waking Screen & Entering PIN...")
                tvTouchStatusHint?.text = "🔓 Waking Screen & Entering PIN..."
                mainViewModel.sendRemoteCommand("unlock", mapOf("pin" to effectivePin)) { success ->
                    if (success) showToast("✅ Unlock command sent!")
                }
            } else {
                showToast("⚡ Waking PC Display & Dismissing Lock...")
                tvTouchStatusHint?.text = "⚡ Waking PC Display..."
                mainViewModel.sendRemoteCommand("unlock") { success ->
                    if (success) showToast("✅ Display woken & lock screen dismissed!")
                }
            }
        }

        builder.setNeutralButton("WAKE ONLY") { _, _ ->
            showToast("⚡ Waking PC Display...")
            tvTouchStatusHint?.text = "⚡ Waking PC Display..."
            mainViewModel.sendRemoteCommand("unlock") { success ->
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
        mainViewModel.sendRemoteCommand("antiidle", mapOf("state" to if (isAntiIdleActive) "on" else "off")) { success ->
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

        if (active) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun setupTabTrackpad() {
        nativeTrackpad.onMouseMove = { dx, dy ->
            mainViewModel.sendRemoteCommand("mouse/move", mapOf("x" to dx.toInt().toString(), "y" to dy.toInt().toString()))
        }
        nativeTrackpad.onLeftClick = {
            mainViewModel.sendRemoteCommand("mouse/leftclick")
        }
        nativeTrackpad.onRightClick = {
            mainViewModel.sendRemoteCommand("mouse/rightclick")
        }
        nativeTrackpad.onScroll = { deltaY ->
            mainViewModel.sendRemoteCommand("mouse/scroll", mapOf("val" to (deltaY * 30).toInt().toString()))
        }

        // Special Keys
        findViewById<View?>(R.id.keyEsc)?.setOnClickListener { mainViewModel.sendRemoteCommand("keyboard/press", mapOf("val" to "esc")) }
        findViewById<View?>(R.id.keyTab)?.setOnClickListener { mainViewModel.sendRemoteCommand("keyboard/press", mapOf("val" to "tab")) }
        findViewById<View?>(R.id.keyWin)?.setOnClickListener { mainViewModel.sendRemoteCommand("keyboard/press", mapOf("val" to "win")) }
        findViewById<View?>(R.id.keyEnter)?.setOnClickListener { mainViewModel.sendRemoteCommand("keyboard/press", mapOf("val" to "enter")) }
        findViewById<View?>(R.id.keyBackspace)?.setOnClickListener { mainViewModel.sendRemoteCommand("keyboard/backspace") }
        findViewById<View?>(R.id.keySpace)?.setOnClickListener { mainViewModel.sendRemoteCommand("keyboard/text", mapOf("val" to " ")) }
        findViewById<View?>(R.id.keyCopy)?.setOnClickListener { mainViewModel.sendRemoteCommand("keyboard/press", mapOf("val" to "ctrlc")) }
        findViewById<View?>(R.id.keyPaste)?.setOnClickListener { mainViewModel.sendRemoteCommand("keyboard/press", mapOf("val" to "ctrlv")) }

        btnSendText.setOnClickListener {
            val text = etSendText.text.toString()
            if (text.isNotEmpty()) {
                mainViewModel.sendRemoteCommand("keyboard/text", mapOf("val" to text)) { success ->
                    if (success) etSendText.setText("")
                }
            }
        }

        sbVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvVolumeVal.text = "$progress%"
                if (fromUser) {
                    mainViewModel.sendRemoteCommand("volume/$progress")
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvBrightnessVal.text = "$progress%"
                if (fromUser) {
                    mainViewModel.sendRemoteCommand("brightness", mapOf("level" to progress.toString()))
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
                mainViewModel.sendRemoteCommand(cmd)
                showToast("Launched $cmd")
            }
        }

        btnRefreshProcesses.setOnClickListener { mainViewModel.fetchProcesses() }

        processAdapter = object : ArrayAdapter<ProcessItem>(this, R.layout.item_process, processList) {
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
                                mainViewModel.killProcess(item.pid)
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

    private fun setupTabFiles() {
        btnUploadFile.setOnClickListener {
            pickFileLauncher.launch("*/*")
        }

        btnRefreshFiles.setOnClickListener { mainViewModel.loadFiles(currentFolderPath) }
    }

    private fun setupTabMedia() {
        btnMediaPrev.setOnClickListener { mainViewModel.sendRemoteCommand("prev") }
        btnMediaPlayPause.setOnClickListener { mainViewModel.sendRemoteCommand("playpause") }
        btnMediaNext.setOnClickListener { mainViewModel.sendRemoteCommand("next") }

        btnRefreshScreenshot.setOnClickListener {
            refreshScreenshot()
        }

        btnToggleLiveStream.setOnClickListener {
            if (isMirroringRunning) {
                stopMirroring()
            } else {
                startMirroring()
            }
        }

        btnToggleMirror.setOnClickListener {
            if (isMirroringRunning) {
                stopMirroring()
            } else {
                startMirroring()
            }
        }

        btnFullscreenScreen?.setOnClickListener {
            // Open fullscreen view
        }

        btnQuickWin?.setOnClickListener { mainViewModel.sendRemoteCommand("keyboard/press", mapOf("val" to "win")) }
        btnQuickCopy?.setOnClickListener { mainViewModel.sendRemoteCommand("keyboard/press", mapOf("val" to "ctrlc")) }
        btnQuickPaste?.setOnClickListener { mainViewModel.sendRemoteCommand("keyboard/press", mapOf("val" to "ctrlv")) }
        btnQuickScrollUp?.setOnClickListener { mainViewModel.sendRemoteCommand("mouse/scroll", mapOf("val" to "120")) }
        btnQuickScrollDown?.setOnClickListener { mainViewModel.sendRemoteCommand("mouse/scroll", mapOf("val" to "-120")) }
        btnQuickBw?.setOnClickListener { toggleBwTheme() }
        
        btnQuickSendText?.setOnClickListener {
            val text = etQuickSendText?.text.toString() ?: ""
            if (text.isNotEmpty()) {
                mainViewModel.sendRemoteCommand("keyboard/text", mapOf("val" to text))
                etQuickSendText?.setText("")
            }
        }
        
        btnQuickBackspace?.setOnClickListener { mainViewModel.sendRemoteCommand("keyboard/backspace") }
        btnQuickEnter?.setOnClickListener { mainViewModel.sendRemoteCommand("keyboard/press", mapOf("val" to "enter")) }
        btnLiveLock?.setOnClickListener { performLockPc() }
        btnLiveUnlock?.setOnClickListener { performUnlockPc() }
        btnLiveAntiIdle?.setOnClickListener { toggleAntiIdle() }
    }

    private fun refreshScreenshot() {
        lifecycleScope.launch {
            val result = mainViewModel.repository.getScreenshot()
            result.onSuccess { responseBody ->
                val bitmap = android.graphics.BitmapFactory.decodeStream(responseBody.byteStream())
                runOnUiThread {
                    ivScreenshot.setImageBitmap(bitmap)
                    tvScreenEmptyHint?.visibility = View.GONE
                }
            }.onFailure { e ->
                Log.e("MainActivity", "Screenshot failed", e)
                runOnUiThread { showToast("Screenshot failed: ${e.message}") }
            }
        }
    }

    private fun startMirroring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val intent = mediaProjectionManager!!.createScreenCaptureIntent()
            startMirroringLauncher.launch(intent)
        }
    }

    private fun stopMirroring() {
        val intent = Intent(this, ScreenCaptureService::class.java)
        stopService(intent)
        isMirroringRunning = false
        ScreenCaptureService.isRunning = false
        btnToggleMirror.text = "START"
        btnToggleMirror.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00D2FF"))
        tvMirrorStatus.text = "Screen Cast Stopped"
        Toast.makeText(this, "Screen Cast Stopped", Toast.LENGTH_SHORT).show()
    }

    private fun handleFileUpload(uri: Uri) {
        val contentResolver = contentResolver
        val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "upload_${System.currentTimeMillis()}"
        val inputStream = contentResolver.openInputStream(uri)
        if (inputStream != null) {
            pbUpload.visibility = View.VISIBLE
            showToast("Uploading $fileName to PC...")
            mainViewModel.uploadFile(fileName, inputStream)
            pbUpload.visibility = View.GONE
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private val prefs: SharedPreferences by lazy { getSharedPreferences("pc_master", Context.MODE_PRIVATE) }
}