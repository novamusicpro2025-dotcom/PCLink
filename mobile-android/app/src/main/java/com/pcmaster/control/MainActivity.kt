package com.pcmaster.control

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.content.ComponentName
import android.provider.Settings
import android.service.notification.NotificationListenerService
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.content.res.ColorStateList
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var setupLayout: LinearLayout
    private lateinit var connectedLayout: LinearLayout
    private lateinit var dashboardLayout: View
    private lateinit var remoteLayout: View
    private lateinit var mirrorLayout: View
    private lateinit var fileLayout: View
    private lateinit var toolsLayout: View
    private lateinit var systemLayout: View
    private lateinit var appsLayout: View
    private lateinit var ipInput: EditText
    private lateinit var progressBar: android.widget.ProgressBar
    private lateinit var statusText: TextView
    private lateinit var cpuCircle: ProgressBar
    private lateinit var cpuUsageText: TextView
    private lateinit var cpuTempText: TextView
    private lateinit var gpuCircle: ProgressBar
    private lateinit var gpuUsageText: TextView
    private lateinit var gpuTempText: TextView
    private lateinit var ramBar: ProgressBar
    private lateinit var ramDetailText: TextView
    private lateinit var storageBar: ProgressBar
    private lateinit var storageDetailText: TextView
    private lateinit var storageBarFile: ProgressBar
    private lateinit var storageDetailTextFile: TextView
    private lateinit var btnGamingMode: View
    private lateinit var gamingModeLabel: TextView
    private lateinit var tabDashboard: Button
    private lateinit var tabRemote: Button
    private lateinit var tabMirror: Button
    private lateinit var tabFileTransfer: Button
    private lateinit var volumeSeekBar: android.widget.SeekBar
    private lateinit var volumeText: TextView
    private lateinit var touchpad: View
    private lateinit var btnToggleMirror: Button
    private lateinit var mirrorStatusText: TextView
    private lateinit var hiddenInput: EditText
    private lateinit var fileRecyclerView: RecyclerView
    private lateinit var fileAdapter: FileAdapter
    
    // Tools tab
    private lateinit var clipboardPhoneText: EditText
    private lateinit var btnPullClipboard: Button
    private lateinit var btnPushClipboard: Button
    private lateinit var btnScreenshotTool: Button
    private lateinit var btnLoadProcesses: Button
    private lateinit var typeTextInput: EditText
    private lateinit var btnTypeText: Button
    
    // System tab
    private lateinit var btnPowerSaver: Button
    private lateinit var btnPowerBalanced: Button
    private lateinit var btnPowerHigh: Button
    private lateinit var btnPowerUltimate: Button
    private lateinit var brightnessSlider: SeekBar
    private lateinit var btnBrightDim: Button
    private lateinit var btnBrightAuto: Button
    private lateinit var btnBrightBoost: Button
    private lateinit var btnTimerShutdown15: Button
    private lateinit var btnTimerShutdown30: Button
    private lateinit var btnTimerShutdown60: Button
    private lateinit var btnTimerRestart30: Button
    private lateinit var btnTimerSleep15: Button
    private lateinit var btnTimerAbort: Button
    private lateinit var batteryInfo: TextView
    private lateinit var wifiInfo: TextView
    
    // Dashboard - Battery & WiFi
    private lateinit var batteryIcon: TextView
    private lateinit var batteryPct: TextView
    private lateinit var batteryStatus: TextView
    private lateinit var wifiSpeed: TextView
    private lateinit var wifiName: TextView
    
    // Apps tab
    private lateinit var btnAppBrowser: Button
    private lateinit var btnAppNotepad: Button
    private lateinit var btnAppCalc: Button
    private lateinit var btnAppOSK: Button
    private lateinit var btnAppCMD: Button
    private lateinit var btnAppPowerShell: Button
    private lateinit var btnAppSnipping: Button
    private lateinit var btnAppFiles: Button
    private lateinit var btnMaintFlushDns: Button
    private lateinit var btnMaintClearTemp: Button
    private lateinit var btnMaintRestartExp: Button
    private lateinit var btnMaintToggleDark: Button
    
    // Mirror tab
    private lateinit var mirrorQualitySpinner: Spinner
    private lateinit var rbModeAdaptive: RadioButton
    private lateinit var rbModeMjpeg: RadioButton
    private lateinit var btnFullscreen: Button
    private lateinit var btnKeyboardMirror: Button
    private lateinit var btnTouchActions: Button
    private lateinit var mirrorImageView: ImageView
    private lateinit var mirrorProgress: ProgressBar
    private lateinit var touchActionsBar: LinearLayout
    private lateinit var btnTouchLeftClick: Button
    private lateinit var btnTouchRightClick: Button
    private lateinit var btnTouchScrollUp: Button
    private lateinit var btnTouchScrollDown: Button
    private lateinit var screenKeyboardBox: CardView
    private lateinit var screenKbInput: EditText
    private lateinit var screenKbSend: Button
    private lateinit var quickKeysBar: LinearLayout
    private lateinit var keyEsc: Button
    private lateinit var keyTab: Button
    private lateinit var keyCtrl: Button
    private lateinit var keyAlt: Button
    private lateinit var keyWin: Button
    private lateinit var keyEnter: Button
    private lateinit var btnExitFullscreen: Button
    
    private var mirrorMode = "adaptive"
    private var isFullscreen = false
    private var touchActionsEnabled = false
    private var keyboardEnabled = false
    private var quickKeysEnabled = false
    private val mirrorExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var currentPath = ""
    private val pathHistory = mutableListOf<String>()

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastScrollY = 0f
    private var isScrollingMode = false
    private lateinit var prefs: SharedPreferences
    private val mainHandler = Handler(Looper.getMainLooper())
    private var statsUpdateRunnable: Runnable? = null
    private var discoveryThread: Thread? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var currentIp = ""
    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Connection state
    private enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }
    private var connectionState = ConnectionState.DISCONNECTED
    private var connectRetryCount = 0
    private val MAX_RETRIES = 60
    private val PORT = 8099

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        setupLayout = findViewById(R.id.setupLayout)
        connectedLayout = findViewById(R.id.connectedLayout)
        dashboardLayout = findViewById(R.id.dashboardLayout)
        remoteLayout = findViewById(R.id.remoteLayout)
        mirrorLayout = findViewById(R.id.mirrorLayout)
        fileLayout = findViewById(R.id.fileLayout)
        toolsLayout = findViewById(R.id.toolsLayout)
        systemLayout = findViewById(R.id.systemLayout)
        appsLayout = findViewById(R.id.appsLayout)
        ipInput = findViewById(R.id.ipInput)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        cpuCircle = findViewById(R.id.cpuCircle)
        cpuUsageText = findViewById(R.id.cpuUsageText)
        cpuTempText = findViewById(R.id.cpuTempText)
        gpuCircle = findViewById(R.id.gpuCircle)
        gpuUsageText = findViewById(R.id.gpuUsageText)
        gpuTempText = findViewById(R.id.gpuTempText)
        ramBar = findViewById(R.id.ramBar)
        ramDetailText = findViewById(R.id.ramDetailText)
        storageBar = findViewById(R.id.storageBar)
        storageDetailText = findViewById(R.id.storageDetailText)
        storageBarFile = findViewById(R.id.storageBarFile)
        storageDetailTextFile = findViewById(R.id.storageDetailTextFile)
        volumeSeekBar = findViewById(R.id.volumeSeekBar)
        volumeText = findViewById(R.id.volumeText)
        touchpad = findViewById(R.id.touchpad)
        btnToggleMirror = findViewById(R.id.btnToggleMirror)
        mirrorStatusText = findViewById(R.id.mirrorStatusText)
        hiddenInput = findViewById(R.id.hiddenInput)
        
        btnGamingMode = findViewById(R.id.btnGamingMode)
        gamingModeLabel = findViewById(R.id.gamingModeText)
        tabDashboard = findViewById(R.id.tabDashboard)
        tabRemote = findViewById(R.id.tabRemote)
        tabMirror = findViewById(R.id.tabMirror)
        tabFileTransfer = findViewById(R.id.tabFileTransfer)
        val tabTools = findViewById<Button>(R.id.tabTools)
        val tabSystem = findViewById<Button>(R.id.tabSystem)
        val tabApps = findViewById<Button>(R.id.tabApps)

        val connectBtn = findViewById<Button>(R.id.connectBtn)
        val usbConnectBtn = findViewById<Button>(R.id.usbConnectBtn)
        val disconnectBtn = findViewById<Button>(R.id.disconnectBtn)
        
        // Tools tab
        clipboardPhoneText = findViewById(R.id.clipboardPhoneText)
        btnPullClipboard = findViewById(R.id.btnPullClipboard)
        btnPushClipboard = findViewById(R.id.btnPushClipboard)
        btnScreenshotTool = findViewById(R.id.btnScreenshotTool)
        btnLoadProcesses = findViewById(R.id.btnLoadProcesses)
        typeTextInput = findViewById(R.id.typeTextInput)
        btnTypeText = findViewById(R.id.btnTypeText)
        
        // System tab
        btnPowerSaver = findViewById(R.id.btnPowerSaver)
        btnPowerBalanced = findViewById(R.id.btnPowerBalanced)
        btnPowerHigh = findViewById(R.id.btnPowerHigh)
        btnPowerUltimate = findViewById(R.id.btnPowerUltimate)
        brightnessSlider = findViewById(R.id.brightnessSlider)
        btnBrightDim = findViewById(R.id.btnBrightDim)
        btnBrightAuto = findViewById(R.id.btnBrightAuto)
        btnBrightBoost = findViewById(R.id.btnBrightBoost)
        btnTimerShutdown15 = findViewById(R.id.btnTimerShutdown15)
        btnTimerShutdown30 = findViewById(R.id.btnTimerShutdown30)
        btnTimerShutdown60 = findViewById(R.id.btnTimerShutdown60)
        btnTimerRestart30 = findViewById(R.id.btnTimerRestart30)
        btnTimerSleep15 = findViewById(R.id.btnTimerSleep15)
        btnTimerAbort = findViewById(R.id.btnTimerAbort)
        batteryInfo = findViewById(R.id.batteryInfo)
        wifiInfo = findViewById(R.id.wifiInfo)
        
        // Dashboard - Battery & WiFi
        batteryIcon = findViewById(R.id.batteryIcon)
        batteryPct = findViewById(R.id.batteryPct)
        batteryStatus = findViewById(R.id.batteryStatus)
        wifiSpeed = findViewById(R.id.wifiSpeed)
        wifiName = findViewById(R.id.wifiName)
        
        // Apps tab
        btnAppBrowser = findViewById(R.id.btnAppBrowser)
        btnAppNotepad = findViewById(R.id.btnAppNotepad)
        btnAppCalc = findViewById(R.id.btnAppCalc)
        btnAppOSK = findViewById(R.id.btnAppOSK)
        btnAppCMD = findViewById(R.id.btnAppCMD)
        btnAppPowerShell = findViewById(R.id.btnAppPowerShell)
        btnAppSnipping = findViewById(R.id.btnAppSnipping)
        btnAppFiles = findViewById(R.id.btnAppFiles)
        btnMaintFlushDns = findViewById(R.id.btnMaintFlushDns)
        btnMaintClearTemp = findViewById(R.id.btnMaintClearTemp)
        btnMaintRestartExp = findViewById(R.id.btnMaintRestartExp)
        btnMaintToggleDark = findViewById(R.id.btnMaintToggleDark)
        
        // Mirror tab
        mirrorQualitySpinner = findViewById(R.id.mirrorQualitySpinner)
        rbModeAdaptive = findViewById(R.id.rbModeAdaptive)
        rbModeMjpeg = findViewById(R.id.rbModeMjpeg)
        btnFullscreen = findViewById(R.id.btnFullscreen)
        btnKeyboardMirror = findViewById(R.id.btnKeyboardMirror)
        btnTouchActions = findViewById(R.id.btnTouchActions)
        mirrorImageView = findViewById(R.id.mirrorImageView)
        mirrorProgress = findViewById(R.id.mirrorProgress)
        touchActionsBar = findViewById(R.id.touchActionsBar)
        btnTouchLeftClick = findViewById(R.id.btnTouchLeftClick)
        btnTouchRightClick = findViewById(R.id.btnTouchRightClick)
        btnTouchScrollUp = findViewById(R.id.btnTouchScrollUp)
        btnTouchScrollDown = findViewById(R.id.btnTouchScrollDown)
        screenKeyboardBox = findViewById(R.id.screenKeyboardBox)
        screenKbInput = findViewById(R.id.screenKbInput)
        screenKbSend = findViewById(R.id.screenKbSend)
        quickKeysBar = findViewById(R.id.quickKeysBar)
        keyEsc = findViewById(R.id.keyEsc)
        keyTab = findViewById(R.id.keyTab)
        keyCtrl = findViewById(R.id.keyCtrl)
        keyAlt = findViewById(R.id.keyAlt)
        keyWin = findViewById(R.id.keyWin)
        keyEnter = findViewById(R.id.keyEnter)
        btnExitFullscreen = findViewById(R.id.btnExitFullscreen)
        
        prefs = getSharedPreferences("PCMasterPrefs", Context.MODE_PRIVATE)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        configureWebView()

        val savedIp = prefs.getString("last_ip", "")
        if (!savedIp.isNullOrEmpty()) ipInput.setText(savedIp)

        if (intent?.getBooleanExtra("auto_connect", false) == true) {
            startUsbConnect()
        }
        handleIntent(intent)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (connectionState != ConnectionState.DISCONNECTED) {
                    disconnect()
                } else {
                    finish()
                }
            }
        })

        disconnectBtn.setOnClickListener { disconnect() }

        connectBtn.setOnClickListener {
            val ip = ipInput.text.toString().trim()
            if (ip.isNotEmpty()) {
                prefs.edit().putString("last_ip", ip).apply()
                connectToPc(ip)
            } else {
                Toast.makeText(this, "Enter PC IP address", Toast.LENGTH_SHORT).show()
            }
        }

        usbConnectBtn.setOnClickListener {
            startUsbConnect()
        }

        tabDashboard.setOnClickListener { switchTab("dashboard") }
        tabRemote.setOnClickListener { switchTab("remote") }
        tabMirror.setOnClickListener { switchTab("mirror") }
        tabFileTransfer.setOnClickListener { switchTab("filetransfer") }
        tabTools.setOnClickListener { switchTab("tools") }
        tabSystem.setOnClickListener { switchTab("system") }
        tabApps.setOnClickListener { switchTab("apps") }

        // Tools tab listeners
        btnPullClipboard.setOnClickListener { pullClipboard() }
        btnPushClipboard.setOnClickListener { pushClipboard() }
        btnScreenshotTool.setOnClickListener { sendRemoteCommand("remote/screenshot") }
        btnLoadProcesses.setOnClickListener { loadProcesses() }
        btnTypeText.setOnClickListener { typeText() }
        
        // System tab listeners
        btnPowerSaver.setOnClickListener { sendRemoteCommand("remote/powerplan?plan=saver") }
        btnPowerBalanced.setOnClickListener { sendRemoteCommand("remote/powerplan?plan=balanced") }
        btnPowerHigh.setOnClickListener { sendRemoteCommand("remote/powerplan?plan=high") }
        btnPowerUltimate.setOnClickListener { sendRemoteCommand("remote/powerplan?plan=ultimate") }
        brightnessSlider.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) sendRemoteCommand("remote/brightness?level=$progress")
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
        btnBrightDim.setOnClickListener { 
            val newVal = Math.max(0, brightnessSlider.progress - 20)
            brightnessSlider.progress = newVal
            sendRemoteCommand("remote/brightness?level=$newVal")
        }
        btnBrightAuto.setOnClickListener { 
            brightnessSlider.progress = 80
            sendRemoteCommand("remote/brightness?level=80")
        }
        btnBrightBoost.setOnClickListener { 
            val newVal = Math.min(100, brightnessSlider.progress + 20)
            brightnessSlider.progress = newVal
            sendRemoteCommand("remote/brightness?level=$newVal")
        }
        btnTimerShutdown15.setOnClickListener { sendRemoteCommand("remote/timer?action=shutdown&minutes=15") }
        btnTimerShutdown30.setOnClickListener { sendRemoteCommand("remote/timer?action=shutdown&minutes=30") }
        btnTimerShutdown60.setOnClickListener { sendRemoteCommand("remote/timer?action=shutdown&minutes=60") }
        btnTimerRestart30.setOnClickListener { sendRemoteCommand("remote/timer?action=restart&minutes=30") }
        btnTimerSleep15.setOnClickListener { sendRemoteCommand("remote/timer?action=sleep&minutes=15") }
        btnTimerAbort.setOnClickListener { sendRemoteCommand("remote/timer?action=abort&minutes=0") }
        
        // Apps tab listeners
        btnAppBrowser.setOnClickListener { sendRemoteCommand("remote/chrome") }
        btnAppNotepad.setOnClickListener { sendRemoteCommand("remote/notepad") }
        btnAppCalc.setOnClickListener { sendRemoteCommand("remote/calc") }
        btnAppOSK.setOnClickListener { sendRemoteCommand("remote/osk") }
        btnAppCMD.setOnClickListener { 
            sendRemoteCommand("remote/cmd")
            mainHandler.postDelayed({ showKeyboard() }, 500)
        }
        btnAppPowerShell.setOnClickListener { 
            sendRemoteCommand("remote/powershell")
            mainHandler.postDelayed({ showKeyboard() }, 500)
        }
        btnAppSnipping.setOnClickListener { sendRemoteCommand("remote/snipping") }
        btnAppFiles.setOnClickListener { sendRemoteCommand("remote/openfiles") }
        btnMaintFlushDns.setOnClickListener { sendRemoteCommand("remote/flushdns") }
        btnMaintClearTemp.setOnClickListener { sendRemoteCommand("remote/cleartemp") }
        btnMaintRestartExp.setOnClickListener { sendRemoteCommand("remote/restartexp") }
        btnMaintToggleDark.setOnClickListener { sendRemoteCommand("remote/toggledark") }
        
        // Mirror tab listeners
        setupMirrorTab()

        findViewById<ImageButton>(R.id.btnRefreshFiles)?.setOnClickListener { 
            fetchFileList(currentPath)
            sendRemoteCommand("remote/opendrive_d")
        }

        findViewById<ImageButton>(R.id.btnBackFolder)?.setOnClickListener {
            goBackFolder()
        }

        btnGamingMode.setOnClickListener {
            val isActive = isGamingModeActive
            if (isActive) {
                sendRemoteCommand("remote/gamingoff")
            } else {
                sendRemoteCommand("remote/gamingon")
            }
        }

        findViewById<Button>(R.id.btnBoostRam)?.setOnClickListener { 
            sendRemoteCommand("remote/boostram")
        }

        // System Tools wiring
        findViewById<ImageButton>(R.id.btnTaskMgr)?.setOnClickListener { sendRemoteCommand("remote/taskmgr") }
        findViewById<ImageButton>(R.id.btnCmd)?.setOnClickListener { 
            sendRemoteCommand("remote/cmd")
            mainHandler.postDelayed({ showKeyboard() }, 500)
        }
        findViewById<ImageButton>(R.id.btnPowershell)?.setOnClickListener { 
            sendRemoteCommand("remote/powershell")
            mainHandler.postDelayed({ showKeyboard() }, 500)
        }
        findViewById<ImageButton>(R.id.btnChrome)?.setOnClickListener { 
            sendRemoteCommand("remote/chrome")
            mainHandler.postDelayed({ showKeyboard() }, 500)
        }
        findViewById<ImageButton>(R.id.btnNotepad)?.setOnClickListener { 
            sendRemoteCommand("remote/notepad")
            mainHandler.postDelayed({ showKeyboard() }, 500)
        }
        findViewById<ImageButton>(R.id.btnCalc)?.setOnClickListener { sendRemoteCommand("remote/calc") }
        findViewById<ImageButton>(R.id.btnScreenshot)?.setOnClickListener { sendRemoteCommand("remote/screenshot") }
        findViewById<ImageButton>(R.id.btnMonOff)?.setOnClickListener { sendRemoteCommand("remote/monoff") }

        // Macros wiring
        findViewById<Button>(R.id.btnFlushDns)?.setOnClickListener { sendRemoteCommand("remote/flushdns") }
        findViewById<Button>(R.id.btnClearTemp)?.setOnClickListener { sendRemoteCommand("remote/cleartemp") }
        findViewById<Button>(R.id.btnRestartExplorer)?.setOnClickListener { sendRemoteCommand("remote/restartexp") }
        findViewById<Button>(R.id.btnToggleDark)?.setOnClickListener { sendRemoteCommand("remote/toggledark") }
        findViewById<Button>(R.id.btnAbortShutdown)?.setOnClickListener { sendRemoteCommand("remote/abort") }

        findViewById<Button>(R.id.pcSetupBtn)?.setOnClickListener { showPcSetupDialog() }
        findViewById<Button>(R.id.usbHelpBtn)?.setOnClickListener { showUsbHelpDialog() }

        startAutoDiscovery()

        findViewById<ImageButton>(R.id.btnRestart)?.setOnClickListener { showConfirmationDialog("Restart PC", "Are you sure you want to restart your PC?") { sendRemoteCommand("remote/restart") } }
        findViewById<ImageButton>(R.id.btnShutdown)?.setOnClickListener { showConfirmationDialog("Shutdown PC", "Are you sure you want to shutdown your PC?") { sendRemoteCommand("remote/shutdown") } }
        findViewById<ImageButton>(R.id.btnSleep)?.setOnClickListener { sendRemoteCommand("remote/sleep") }
        findViewById<ImageButton>(R.id.btnLockQuick)?.setOnClickListener { sendRemoteCommand("remote/lock") }
        
        findViewById<ImageButton>(R.id.btnPrev)?.setOnClickListener { sendRemoteCommand("remote/prev") }
        findViewById<ImageButton>(R.id.btnPlayPause)?.setOnClickListener { sendRemoteCommand("remote/playpause") }
        findViewById<ImageButton>(R.id.btnNext)?.setOnClickListener { sendRemoteCommand("remote/next") }
        findViewById<ImageView>(R.id.btnMute)?.setOnClickListener { sendRemoteCommand("remote/mute") }

        setupRemoteControls()
        setupFileTransfer()
        setupMirroring()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        updateMirrorUi(ScreenCaptureService.isRunning)

        checkOverlayPermission()
        checkNotificationPermission()
        registerNetworkCallback()

        val filter = android.content.IntentFilter("android.hardware.usb.action.USB_STATE")
        registerReceiver(usbReceiver, filter)
    }

    private fun checkOverlayPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                AlertDialog.Builder(this)
                    .setTitle("Auto-Launch Permission")
                    .setMessage("To open the app automatically when USB is connected, please allow 'Display over other apps' permission.")
                    .setPositiveButton("Grant") { _, _ ->
                        val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                        startActivity(intent)
                    }
                    .setNegativeButton("Not Now", null)
                    .show()
            }
        }
    }

    private fun checkNotificationPermission() {
        if (!isNotificationServiceEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("Notification Sync")
                .setMessage("To sync notifications to your PC, please enable Notification Access for PC Master Control.")
                .setPositiveButton("Enable") { _, _ ->
                    startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                }
                .setNegativeButton("Not Now", null)
                .show()
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (!flat.isNullOrEmpty()) {
            val names = flat.split(":")
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null && cn.packageName == pkgName) {
                    return true
                }
            }
        }
        return false
    }

    private val usbReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            if (intent?.extras?.getBoolean("connected") == true) {
                if (connectionState == ConnectionState.DISCONNECTED) {
                    startUsbConnect()
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("auto_connect", false)) {
            if (connectionState == ConnectionState.DISCONNECTED) {
                startUsbConnect()
            }
        }
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent?.action == "android.hardware.usb.action.USB_DEVICE_ATTACHED") {
            if (connectionState == ConnectionState.DISCONNECTED) {
                startUsbConnect()
            }
        }
    }

    private fun switchTab(tab: String) {
        val selectedBg = R.drawable.tab_selected
        val unselectedBg = android.R.color.transparent
        
        tabDashboard.setBackgroundResource(if (tab == "dashboard") selectedBg else unselectedBg)
        tabRemote.setBackgroundResource(if (tab == "remote") selectedBg else unselectedBg)
        tabMirror.setBackgroundResource(if (tab == "mirror") selectedBg else unselectedBg)
        tabFileTransfer.setBackgroundResource(if (tab == "filetransfer") selectedBg else unselectedBg)
        val tabTools = findViewById<Button>(R.id.tabTools)
        val tabSystem = findViewById<Button>(R.id.tabSystem)
        val tabApps = findViewById<Button>(R.id.tabApps)
        tabTools.setBackgroundResource(if (tab == "tools") selectedBg else unselectedBg)
        tabSystem.setBackgroundResource(if (tab == "system") selectedBg else unselectedBg)
        tabApps.setBackgroundResource(if (tab == "apps") selectedBg else unselectedBg)
        
        tabDashboard.setTextColor(if (tab == "dashboard") android.graphics.Color.WHITE else android.graphics.Color.parseColor("#8E8E93"))
        tabRemote.setTextColor(if (tab == "remote") android.graphics.Color.WHITE else android.graphics.Color.parseColor("#8E8E93"))
        tabMirror.setTextColor(if (tab == "mirror") android.graphics.Color.WHITE else android.graphics.Color.parseColor("#8E8E93"))
        tabFileTransfer.setTextColor(if (tab == "filetransfer") android.graphics.Color.WHITE else android.graphics.Color.parseColor("#8E8E93"))
        tabTools.setTextColor(if (tab == "tools") android.graphics.Color.WHITE else android.graphics.Color.parseColor("#8E8E93"))
        tabSystem.setTextColor(if (tab == "system") android.graphics.Color.WHITE else android.graphics.Color.parseColor("#8E8E93"))
        tabApps.setTextColor(if (tab == "apps") android.graphics.Color.WHITE else android.graphics.Color.parseColor("#8E8E93"))

        dashboardLayout.visibility = if (tab == "dashboard") View.VISIBLE else View.GONE
        remoteLayout.visibility = if (tab == "remote") View.VISIBLE else View.GONE
        mirrorLayout.visibility = if (tab == "mirror") View.VISIBLE else View.GONE
        fileLayout.visibility = if (tab == "filetransfer") View.VISIBLE else View.GONE
        toolsLayout.visibility = if (tab == "tools") View.VISIBLE else View.GONE
        systemLayout.visibility = if (tab == "system") View.VISIBLE else View.GONE
        appsLayout.visibility = if (tab == "apps") View.VISIBLE else View.GONE

        if (tab == "filetransfer") {
            currentPath = ""
            pathHistory.clear()
            fetchFileList("")
            sendRemoteCommand("remote/openfiles")
        }
        if (tab == "system") {
            fetchBatteryAndWifiInfo()
        }
    }

    private fun startAutoDiscovery() {
        if (discoveryThread?.isAlive == true) return
        
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("PCMasterDiscoveryLock")
        multicastLock?.setReferenceCounted(true)
        multicastLock?.acquire()

        discoveryThread = Thread {
            val socket = try {
                DatagramSocket(8091).apply { 
                    soTimeout = 3000 
                    broadcast = true
                }
            } catch (e: Exception) { null }

            val buffer = ByteArray(1024)
            while (!isFinishing) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)
                    val message = String(packet.data, 0, packet.length)
                    if (message.startsWith("PC_MASTER_HUB")) {
                        val pcIp = packet.address.hostAddress ?: continue
                        if (connectionState == ConnectionState.DISCONNECTED) {
                            runOnUiThread {
                                if (connectionState == ConnectionState.DISCONNECTED) {
                                    Toast.makeText(this, "PC Found: $pcIp", Toast.LENGTH_SHORT).show()
                                    connectToPc(pcIp)
                                }
                            }
                        }
                    }
                } catch (e: SocketTimeoutException) {
                    // Try to probe common gateway IPs if UDP discovery is failing
                    if (connectionState == ConnectionState.DISCONNECTED) {
                        probeCommonGateways()
                    }
                } catch (e: Exception) {
                    Thread.sleep(5000)
                }
            }
            socket?.close()
            try { multicastLock?.release() } catch (e: Exception) {}
        }.apply { start() }
    }

    private fun probeCommonGateways() {
        val candidates = mutableSetOf<String>()
        candidates.addAll(listOf("192.168.137.1", "192.168.43.1", "192.168.49.1", "172.20.10.1", "192.168.0.1", "192.168.1.1", "10.0.0.1"))
        
        // Identify hotspot/local subnet to scan likely PC IPs
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        val parts = ip.split(".")
                        if (parts.size == 4) {
                            val base = "${parts[0]}.${parts[1]}.${parts[2]}"
                            // If phone is .1 (hotspot host), scan likely PC client IPs .2 to .15
                            if (parts[3] == "1") {
                                for (i in 2..15) candidates.add("$base.$i")
                            } else {
                                // If phone is a client, the host/gateway is likely .1 or .137.1
                                candidates.add("$base.1")
                                if (base.startsWith("192.168.")) candidates.add("${base.substringBeforeLast(".")}.137.1")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {}

        // Fallback to WifiManager dhcpInfo for gateway
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val dhcp = wifiManager.dhcpInfo
            if (dhcp != null && dhcp.gateway != 0) {
                val ip = dhcp.gateway
                candidates.add(String.format("%d.%d.%d.%d", (ip and 0xFF), (ip shr 8 and 0xFF), (ip shr 16 and 0xFF), (ip shr 24 and 0xFF)))
            }
        } catch (e: Exception) {}

        Thread {
            for (ip in candidates) {
                if (connectionState != ConnectionState.DISCONNECTED) return@Thread
                val url = if (ip.startsWith("http")) ip else "http://$ip:$PORT"
                if (tryReachServer(url)) {
                    runOnUiThread { 
                        if (connectionState == ConnectionState.DISCONNECTED) {
                            connectToPc(ip) 
                        }
                    }
                    return@Thread
                }
            }
        }.start()
    }

    private fun registerNetworkCallback() {
        val request = android.net.NetworkRequest.Builder().build() 
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val lp = connectivityManager.getLinkProperties(network)
                val iface = lp?.interfaceName ?: ""
                if (isUsbInterface(iface)) {
                    runOnUiThread {
                        if (connectionState == ConnectionState.DISCONNECTED) {
                            statusText.visibility = View.VISIBLE
                            statusText.text = "USB Network detected. Connecting..."
                            startUsbConnect()
                        }
                    }
                }
            }
        }
        connectivityManager.registerNetworkCallback(request, networkCallback!!)
    }

    private fun isUsbInterface(iface: String): Boolean {
        val lower = iface.lowercase()
        return lower.contains("rndis") || lower.contains("usb") || lower.contains("ncm") || lower.contains("eth")
    }

    private fun startUsbConnect() {
        if (connectionState == ConnectionState.CONNECTING) return
        connectionState = ConnectionState.CONNECTING
        connectRetryCount = 0
        showConnectingUI("Searching for PC via USB...")
        attemptUsbConnection()
    }

    private fun attemptUsbConnection() {
        if (connectionState != ConnectionState.CONNECTING) return
        connectRetryCount++

        if (connectRetryCount > MAX_RETRIES) {
            runOnUiThread {
                connectionState = ConnectionState.DISCONNECTED
                showSetupWithError("USB Connection Timeout.\n1. Re-enable USB Tethering\n2. Ensure PC App is running as Admin\n3. Check USB Cable")
                showTetheringDialogOnce()
            }
            return
        }

        runOnUiThread { 
            statusText.visibility = View.VISIBLE
            statusText.text = "Searching for PC... ($connectRetryCount/$MAX_RETRIES)\nTip: Keep USB Tethering enabled" 
        }

        Thread {
            val candidates = getUsbCandidateIps()
            // Even if candidates are empty, we keep retrying until MAX_RETRIES
            // This handles the delay while the phone is "Obtaining IP address"
            
            for (ip in candidates) {
                if (connectionState != ConnectionState.CONNECTING) return@Thread
                if (tryReachServer(ip)) {
                    runOnUiThread { finishConnection(ip) }
                    return@Thread
                }
            }
            mainHandler.postDelayed({ attemptUsbConnection() }, 2000)
        }.start()
    }

    private fun getUsbCandidateIps(): List<String> {
        val candidates = mutableListOf<String>()
        try {
            for (network in connectivityManager.allNetworks) {
                val caps = connectivityManager.getNetworkCapabilities(network) ?: continue
                val lp = connectivityManager.getLinkProperties(network) ?: continue
                val iface = lp.interfaceName ?: ""
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) continue
                
                if (isUsbInterface(iface)) {
                    for (route in lp.routes) {
                        val gw = route.gateway?.hostAddress
                        if (gw != null && gw != "0.0.0.0" && gw != "::") {
                            if (!candidates.contains(gw)) candidates.add(gw)
                        }
                    }
                    for (linkAddr in lp.linkAddresses) {
                        val addr = linkAddr.address?.hostAddress ?: continue
                        if (addr.contains(".")) {
                            val parts = addr.split(".")
                            if (parts.size == 4) {
                                val baseIp = "${parts[0]}.${parts[1]}.${parts[2]}"
                                val gw1 = "$baseIp.1"
                                val gw2 = "$baseIp.2"
                                if (!candidates.contains(gw1)) candidates.add(gw1)
                                if (!candidates.contains(gw2)) candidates.add(gw2)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        
        val defaults = listOf("127.0.0.1", "192.168.42.1", "192.168.42.129", "192.168.43.1", "192.168.49.1", "192.168.137.1", "192.168.0.1", "172.20.10.1")
        for (d in defaults) { if (!candidates.contains(d)) candidates.add(d) }
        return candidates
    }

    private fun connectToPc(ip: String) {
        if (connectionState == ConnectionState.CONNECTING) return
        connectionState = ConnectionState.CONNECTING
        connectRetryCount = 0
        showConnectingUI("Connecting to $ip...")

        Thread {
            val normalizedIp = normalizeIp(ip)
            if (tryReachServer(normalizedIp)) {
                runOnUiThread { finishConnection(normalizedIp) }
            } else {
                val withPort = if (!ip.contains(":")) normalizeIp("$ip:$PORT") else normalizedIp
                if (withPort != normalizedIp && tryReachServer(withPort)) {
                    runOnUiThread { finishConnection(withPort) }
                } else {
                    runOnUiThread {
                        connectionState = ConnectionState.DISCONNECTED
                        showSetupWithError("Cannot reach PC at $ip\nCheck PC Hub and Firewall.")
                    }
                }
            }
        }.start()
    }

    private fun normalizeIp(ip: String): String {
        var result = ip.trim()
        if (!result.startsWith("http")) result = "http://$result"
        if (!result.substringAfter("://").contains(":")) result = "$result:$PORT"
        return result
    }

    private fun tryReachServer(baseUrl: String): Boolean {
        return try {
            val url = URL("$baseUrl/stats")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 2000
            connection.readTimeout = 2000
            connection.setRequestProperty("User-Agent", "PC-Master-Android-App/1.0")
            val code = connection.responseCode
            connection.disconnect()
            code == 200
        } catch (e: Exception) { false }
    }

    private fun finishConnection(url: String) {
        currentIp = url
        connectionState = ConnectionState.CONNECTED
        val hostPort = url.removePrefix("http://").removePrefix("https://")
        prefs.edit().putString("last_ip", hostPort).apply()
        ipInput.setText(hostPort)
        setupLayout.visibility = View.GONE
        connectedLayout.visibility = View.VISIBLE
        switchTab("dashboard")
        progressBar.visibility = View.GONE
        statusText.visibility = View.GONE
        startStatsUpdates()
    }

    private fun showConnectingUI(message: String) {
        setupLayout.visibility = View.GONE
        connectedLayout.visibility = View.VISIBLE
        switchTab("dashboard") // Default to dashboard while connecting? or just show progress
        progressBar.visibility = View.VISIBLE
        statusText.visibility = View.VISIBLE
        statusText.text = message
    }

    private fun showSetupWithError(error: String) {
        connectedLayout.visibility = View.GONE
        webView.visibility = View.GONE
        setupLayout.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
        statusText.visibility = View.VISIBLE
        statusText.text = error
    }

    private fun startStatsUpdates() {
        stopStatsUpdates()
        statsUpdateRunnable = object : Runnable {
            override fun run() {
                if (connectionState == ConnectionState.CONNECTED) {
                    fetchStats()
                    mainHandler.postDelayed(this, 2000)
                }
            }
        }
        mainHandler.post(statsUpdateRunnable!!)
    }

    private fun stopStatsUpdates() {
        statsUpdateRunnable?.let { mainHandler.removeCallbacks(it) }
        statsUpdateRunnable = null
    }

    private fun setupRemoteControls() {
        volumeSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    volumeText.text = "$progress%"
                    sendRemoteCommand("remote/volume/$progress")
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        findViewById<ImageButton>(R.id.btnPlayPause).setOnClickListener { sendRemoteCommand("remote/playpause") }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener { sendRemoteCommand("remote/next") }
        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener { sendRemoteCommand("remote/prev") }

        setupTouchpad()
        setupKeyboard()
    }

    private fun setupTouchpad() {
        touchpad.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    isScrollingMode = false
                    v.parent.requestDisallowInterceptTouchEvent(true)
                    true
                }
                android.view.MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount == 2) {
                        isScrollingMode = true
                        lastScrollY = (event.getY(0) + event.getY(1)) / 2
                    }
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (isScrollingMode && event.pointerCount >= 2) {
                        val currentScrollY = (event.getY(0) + event.getY(1)) / 2
                        val dy = (currentScrollY - lastScrollY).toInt()
                        if (Math.abs(dy) > 5) {
                            // Scale scroll delta: standard notch is 120
                            val scrollVal = -dy * 2
                            sendRemoteCommand("remote/mouse/scroll?val=$scrollVal")
                            lastScrollY = currentScrollY
                        }
                    } else if (!isScrollingMode) {
                        val dx = (event.x - lastTouchX).toInt()
                        val dy = (event.y - lastTouchY).toInt()
                        if (dx != 0 || dy != 0) {
                            val sentX = (dx * 1.5).toInt()
                            val sentY = (dy * 1.5).toInt()
                            sendRemoteCommand("remote/mouse/move?x=$sentX&y=$sentY")
                            lastTouchX = event.x
                            lastTouchY = event.y
                        }
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    v.parent.requestDisallowInterceptTouchEvent(false)
                    if (event.action == android.view.MotionEvent.ACTION_UP && !isScrollingMode) {
                        val duration = event.eventTime - event.downTime
                        if (duration < 200) {
                            sendRemoteCommand("remote/mouse/leftclick")
                        }
                    }
                    isScrollingMode = false
                    v.performClick()
                    true
                }
                else -> false
            }
        }
        touchpad.setOnLongClickListener {
            if (!isScrollingMode) {
                sendRemoteCommand("remote/mouse/rightclick")
                true
            } else false
        }
    }

    private fun setupKeyboard() {
        findViewById<ImageButton>(R.id.btnKeyboard).setOnClickListener {
            hiddenInput.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(hiddenInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }

        hiddenInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (count > before) {
                    val newChar = s?.get(s.length - 1).toString()
                    try {
                        sendRemoteCommand("remote/keyboard/text?val=" + java.net.URLEncoder.encode(newChar, "UTF-8"))
                    } catch (e: Exception) {}
                } else if (before > count) {
                    sendRemoteCommand("remote/keyboard/backspace")
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {
                if (s != null && s.length > 10) s.clear() // Prevent buffer grow
            }
        })
    }

    private fun setupFileTransfer() {
        fileRecyclerView = findViewById(R.id.fileRecyclerView)
        fileRecyclerView.layoutManager = LinearLayoutManager(this)
        fileAdapter = FileAdapter(emptyList()) { file ->
            if (file.isDir) {
                pathHistory.add(currentPath)
                currentPath = file.path
                fetchFileList(currentPath)
            } else {
                downloadFile(file)
            }
        }
        fileRecyclerView.adapter = fileAdapter
    }

    private fun goBackFolder() {
        if (pathHistory.isNotEmpty()) {
            currentPath = pathHistory.removeAt(pathHistory.size - 1)
            fetchFileList(currentPath)
        } else if (currentPath.isNotEmpty()) {
            currentPath = ""
            fetchFileList("")
        }
    }

    private fun fetchFileList(path: String) {
        if (connectionState != ConnectionState.CONNECTED) return
        Thread {
            try {
                val encodedPath = java.net.URLEncoder.encode(path, "UTF-8")
                val url = URL("$currentIp/list?path=$encodedPath")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 3000
                connection.setRequestProperty("User-Agent", "PC-Master-Android-App/1.0")
                if (connection.responseCode == 200) {
                    val text = connection.inputStream.bufferedReader().readText()
                    val jsonArray = org.json.JSONArray(text)
                    val fileList = mutableListOf<PcFile>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        fileList.add(PcFile(
                            obj.getString("name"),
                            obj.getString("path"),
                            obj.getBoolean("isDir"),
                            obj.optString("size", "")
                        ))
                    }
                    runOnUiThread { 
                        fileAdapter.updateFiles(fileList)
                        findViewById<TextView>(R.id.currentPathText)?.text = if (path.isEmpty()) "This PC" else path
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun downloadFile(file: PcFile) {
        try {
            val encodedPath = java.net.URLEncoder.encode(file.path, "UTF-8")
            val url = "$currentIp/download?path=$encodedPath"
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(browserIntent)
            Toast.makeText(this, "Downloading ${file.name}...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {}
    }

    private fun setupMirroring() {
        btnToggleMirror.setOnClickListener {
            if (ScreenCaptureService.isRunning) {
                stopMirroring()
            } else {
                startMirroring()
            }
        }
    }

    private val SCREEN_CAPTURE_REQUEST_CODE = 1002

    private fun startMirroring() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST_CODE)
    }

    private fun stopMirroring() {
        val intent = Intent(this, ScreenCaptureService::class.java)
        stopService(intent)
        updateMirrorUi(false)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SCREEN_CAPTURE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
                putExtra("serverIp", currentIp)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            updateMirrorUi(true)
        }
    }

    private fun showKeyboard() {
        hiddenInput.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(hiddenInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private var consecutiveFailures = 0

    private fun fetchStats() {
        Thread {
            try {
                val url = URL("$currentIp/stats")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.setRequestProperty("User-Agent", "PC-Master-Android-App/1.0")
                if (connection.responseCode == 200) {
                    val text = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(text)
                    connection.disconnect()
                    runOnUiThread {
                        consecutiveFailures = 0
                        progressBar.visibility = View.GONE
                        statusText.visibility = View.GONE
                        updateNativeUi(json)
                    }
                } else {
                    connection.disconnect()
                    handleStatsFetchFailure()
                }
            } catch (e: Exception) {
                handleStatsFetchFailure()
            }
        }.start()
    }

    private fun handleStatsFetchFailure() {
        runOnUiThread {
            consecutiveFailures++
            if (consecutiveFailures >= 5) {
                disconnect()
                showSetupWithError("Connection lost to PC.")
            } else {
                statusText.visibility = View.VISIBLE
                statusText.text = "Reconnecting... ($consecutiveFailures)"
            }
        }
    }

    private var isGamingModeActive = false

    private fun updateNativeUi(json: JSONObject) {
        val cpuLoad = json.optDouble("cpuLoad", 0.0).toInt()
        val cpuTemp = json.optDouble("cpuTemp", 0.0).toInt()
        val gpuLoad = json.optDouble("gpuLoad", 0.0).toInt()
        val gpuTemp = json.optDouble("gpuTemp", 0.0).toInt()
        val ramUsed = json.optDouble("ramUsed", 0.0)
        val ramTotal = json.optDouble("ramTotal", 16.0)
        val storageUsed = json.optDouble("storageUsed", 0.0)
        val storageTotal = json.optDouble("storageTotal", 1024.0)
        val gamingActive = json.optBoolean("gamingActive", false)
        val isMirroringOnPc = json.optBoolean("mirroringActive", false)

        if (isMirroringOnPc != ScreenCaptureService.isRunning) {
            // PC thinks we are mirroring but we aren't, or vice versa
            // For now, just update the UI to match our service state
            updateMirrorUi(ScreenCaptureService.isRunning)
        }

        if (gamingActive != isGamingModeActive) {
            isGamingModeActive = gamingActive
            gamingModeLabel.text = if (isGamingModeActive) "GAMING MODE ACTIVE" else "ACTIVATE GAMING MODE"
            btnGamingMode.alpha = if (isGamingModeActive) 1.0f else 0.8f
            
            // Sync background to match PC neon effect
            val bgView = (btnGamingMode as? androidx.cardview.widget.CardView)?.getChildAt(0)
            if (isGamingModeActive) {
                bgView?.setBackgroundResource(R.drawable.gaming_mode_active_grad)
            } else {
                bgView?.setBackgroundResource(R.drawable.gaming_mode_grad)
            }
        }

        cpuCircle.progress = cpuLoad
        cpuUsageText.text = "$cpuLoad%"
        cpuTempText.text = "$cpuTemp°C"

        gpuCircle.progress = gpuLoad
        gpuUsageText.text = "$gpuLoad%"
        gpuTempText.text = "$gpuTemp°C"

        ramBar.max = (ramTotal * 10).toInt()
        ramBar.progress = (ramUsed * 10).toInt()
        ramDetailText.text = String.format("%.1f / %.1f GB", ramUsed, ramTotal)

        storageBar.max = storageTotal.toInt()
        storageBar.progress = storageUsed.toInt()
        storageDetailText.text = String.format("%dGB / %dGB", storageUsed.toInt(), storageTotal.toInt())
        
        storageBarFile.max = storageTotal.toInt()
        storageBarFile.progress = storageUsed.toInt()
        storageDetailTextFile.text = String.format("%dGB / %dGB", storageUsed.toInt(), storageTotal.toInt())
    }

    private val commandExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private fun sendRemoteCommand(endpoint: String) {
        if (connectionState != ConnectionState.CONNECTED) return
        commandExecutor.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = URL("$currentIp/$endpoint").openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 1000
                connection.readTimeout = 1000
                connection.setRequestProperty("User-Agent", "PC-Master-Android-App/1.0")
                connection.outputStream.use { }
                connection.responseCode
            } catch (e: Exception) {
            } finally {
                try { connection?.disconnect() } catch (_: Throwable) {}
            }
        }
    }

    private fun disconnect() {
        connectionState = ConnectionState.DISCONNECTED
        consecutiveFailures = 0
        stopStatsUpdates()
        connectedLayout.visibility = View.GONE
        webView.visibility = View.GONE
        setupLayout.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
        statusText.visibility = View.GONE
    }

    private fun configureWebView() {
        webView.setBackgroundColor(android.graphics.Color.parseColor("#060912"))
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
    }

    private var dialogShown = false
    private fun showTetheringDialogOnce() {
        if (dialogShown) return
        dialogShown = true
        AlertDialog.Builder(this)
            .setTitle("USB Tethering Required")
            .setMessage("Enable USB Tethering in phone settings to connect.")
            .setPositiveButton("Settings") { _, _ ->
                try {
                    val intent = Intent().setClassName("com.android.settings", "com.android.settings.Settings\$TetherSettingsActivity")
                    startActivity(intent)
                } catch (e: Exception) {
                    startActivity(Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS))
                }
                dialogShown = false
            }
            .setNegativeButton("Cancel") { _, _ -> dialogShown = false }
            .show()
    }

    private fun showPcSetupDialog() {
        AlertDialog.Builder(this)
            .setTitle("PC Setup")
            .setMessage("1. Run PC App as Admin\n2. Start Hub (Port $PORT)\n3. Fix Firewall if needed")
            .setPositiveButton("OK", null).show()
    }

    private fun showUsbHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle("USB Help")
            .setMessage("1. Connect USB\n2. Enable USB Tethering\n3. PC will connect automatically")
            .setPositiveButton("OK", null).show()
    }

    private fun showConfirmationDialog(title: String, message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Yes") { _, _ -> onConfirm() }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStatsUpdates()
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        try { unregisterReceiver(usbReceiver) } catch (e: Exception) {}
    }

    // ===== Tools Tab Functions =====
    private fun pullClipboard() {
        if (connectionState != ConnectionState.CONNECTED) return
        Thread {
            try {
                val url = URL("$currentIp/clipboard/get")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 3000
                connection.setRequestProperty("User-Agent", "PC-Master-Android-App/1.0")
                if (connection.responseCode == 200) {
                    val text = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(text)
                    val pcText = json.optString("text", "")
                    runOnUiThread {
                        clipboardPhoneText.setText(pcText)
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("PCMaster", pcText))
                        Toast.makeText(this, "Clipboard synced from PC", Toast.LENGTH_SHORT).show()
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Failed to pull clipboard", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun pushClipboard() {
        if (connectionState != ConnectionState.CONNECTED) return
        val text = clipboardPhoneText.text.toString()
        if (text.isEmpty()) {
            Toast.makeText(this, "Enter text first", Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            try {
                val url = URL("$currentIp/clipboard/set")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 3000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("User-Agent", "PC-Master-Android-App/1.0")
                val json = JSONObject().put("text", text).toString()
                connection.outputStream.write(json.toByteArray())
                connection.outputStream.flush()
                if (connection.responseCode == 200) {
                    runOnUiThread { Toast.makeText(this, "Clipboard pushed to PC", Toast.LENGTH_SHORT).show() }
                }
                connection.disconnect()
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Failed to push clipboard", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun loadProcesses() {
        if (connectionState != ConnectionState.CONNECTED) return
        Thread {
            try {
                val url = URL("$currentIp/processes")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.setRequestProperty("User-Agent", "PC-Master-Android-App/1.0")
                if (connection.responseCode == 200) {
                    val text = connection.inputStream.bufferedReader().readText()
                    val jsonArray = org.json.JSONArray(text)
                    val processList = mutableListOf<PcProcess>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        processList.add(PcProcess(
                            obj.getInt("pid"),
                            obj.getString("name"),
                            obj.optString("memory", "")
                        ))
                    }
                    runOnUiThread {
                        showProcessDialog(processList)
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Failed to load processes", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun showProcessDialog(processes: List<PcProcess>) {
        val adapter = object : android.widget.ArrayAdapter<PcProcess>(this, android.R.layout.simple_list_item_2, android.R.id.text1, processes) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                val process = getItem(position) ?: return view
                (view.findViewById<android.widget.TextView>(android.R.id.text1)).text = "${process.name} (PID: ${process.pid})"
                (view.findViewById<android.widget.TextView>(android.R.id.text2)).text = process.memory
                return view
            }
        }
        AlertDialog.Builder(this)
            .setTitle("Running Processes")
            .setAdapter(adapter) { _, position ->
                val process = processes[position]
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Kill Process?")
                    .setMessage("Kill ${process.name} (PID: ${process.pid})?")
                    .setPositiveButton("Kill") { _, _ -> killProcess(process.pid) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun killProcess(pid: Int) {
        if (connectionState != ConnectionState.CONNECTED) return
        sendRemoteCommand("remote/kill?pid=$pid")
        Toast.makeText(this, "Kill signal sent for PID $pid", Toast.LENGTH_SHORT).show()
    }

    private fun typeText() {
        if (connectionState != ConnectionState.CONNECTED) return
        val text = typeTextInput.text.toString()
        if (text.isEmpty()) {
            Toast.makeText(this, "Enter text first", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val encoded = java.net.URLEncoder.encode(text, "UTF-8")
            sendRemoteCommand("remote/type?text=$encoded")
            runOnUiThread {
                typeTextInput.text.clear()
                Toast.makeText(this, "Text sent to PC", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to send text", Toast.LENGTH_SHORT).show()
        }
    }

    // ===== System Tab Functions =====
    private fun fetchBatteryAndWifiInfo() {
        if (connectionState != ConnectionState.CONNECTED) return
        
        // Battery from PC
        Thread {
            try {
                val url = URL("$currentIp/battery")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 3000
                connection.setRequestProperty("User-Agent", "PC-Master-Android-App/1.0")
                if (connection.responseCode == 200) {
                    val text = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(text)
                    val available = json.optBoolean("available", false)
                    val charge = json.optInt("charge", 0)
                    val status = json.optString("status", "Unknown")
                    val charging = json.optBoolean("charging", false)
                    
                    runOnUiThread {
                        if (available) {
                            batteryPct.text = "$charge%"
                            val icon = if (charging) "🔌" else "🔋"
                            batteryIcon.text = icon
                            batteryStatus.text = "$status ${if (charging) "(Charging)" else ""}"
                        } else {
                            batteryPct.text = "--%"
                            batteryIcon.text = "❌"
                            batteryStatus.text = "No battery"
                        }
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                runOnUiThread {
                    batteryPct.text = "--%"
                    batteryIcon.text = "❓"
                    batteryStatus.text = "Error"
                }
            }
        }.start()
        
        // WiFi from PC
        Thread {
            try {
                val url = URL("$currentIp/wifi")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 3000
                connection.setRequestProperty("User-Agent", "PC-Master-Android-App/1.0")
                if (connection.responseCode == 200) {
                    val text = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(text)
                    val connected = json.optBoolean("connected", false)
                    val name = json.optString("ssid", json.optString("name", "WiFi"))
                    val speed = json.optInt("speed", 0)
                    val ip = json.optString("ip", "")
                    
                    runOnUiThread {
                        if (connected) {
                            wifiSpeed.text = "${speed}Mbps"
                            wifiName.text = name
                        } else {
                            wifiSpeed.text = "--"
                            wifiName.text = "Disconnected"
                        }
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                runOnUiThread {
                    wifiSpeed.text = "--"
                    wifiName.text = "Error"
                }
            }
        }.start()
    }

    // ===== Mirror Tab Functions =====
    private fun setupMirrorTab() {
        // Quality spinner
        val qualityOptions = arrayOf("30%", "50%", "70%", "90%")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, qualityOptions)
        mirrorQualitySpinner.adapter = adapter
        mirrorQualitySpinner.setSelection(2) // 70%
        mirrorQualitySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (connectionState == ConnectionState.CONNECTED) {
                    sendRemoteCommand("remote/mirror/quality?q=${qualityOptions[position].removeSuffix("%")}")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Mode radio buttons
        rbModeAdaptive.setOnClickListener { 
            mirrorMode = "adaptive"
            sendRemoteCommand("remote/mirror/mode?mode=adaptive")
        }
        rbModeMjpeg.setOnClickListener { 
            mirrorMode = "mjpeg"
            sendRemoteCommand("remote/mirror/mode?mode=mjpeg")
        }

        // Toggle mirror
        btnToggleMirror.setOnClickListener {
            if (ScreenCaptureService.isRunning) {
                stopMirroring()
            } else {
                startMirroring()
            }
        }

        // Fullscreen
        btnFullscreen.setOnClickListener { toggleFullscreen() }
        
        // Keyboard toggle
        btnKeyboardMirror.setOnClickListener { toggleKeyboard() }
        
        // Touch actions toggle
        btnTouchActions.setOnClickListener { toggleTouchActions() }
        
        // Touch action buttons
        btnTouchLeftClick.setOnClickListener { sendRemoteCommand("remote/mouse/leftclick") }
        btnTouchRightClick.setOnClickListener { sendRemoteCommand("remote/mouse/rightclick") }
        btnTouchScrollUp.setOnClickListener { sendRemoteCommand("remote/mouse/scroll?val=-120") }
        btnTouchScrollDown.setOnClickListener { sendRemoteCommand("remote/mouse/scroll?val=120") }
        
        // Screen keyboard
        screenKbSend.setOnClickListener {
            val text = screenKbInput.text.toString()
            if (text.isNotEmpty()) {
                try {
                    val encoded = java.net.URLEncoder.encode(text, "UTF-8")
                    sendRemoteCommand("remote/type?text=$encoded")
                    screenKbInput.text.clear()
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to send text", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        // Quick keys
        keyEsc.setOnClickListener { sendRemoteCommand("remote/key?key=esc") }
        keyTab.setOnClickListener { sendRemoteCommand("remote/key?key=tab") }
        keyCtrl.setOnClickListener { sendRemoteCommand("remote/key?key=ctrl") }
        keyAlt.setOnClickListener { sendRemoteCommand("remote/key?key=alt") }
        keyWin.setOnClickListener { sendRemoteCommand("remote/key?key=win") }
        keyEnter.setOnClickListener { sendRemoteCommand("remote/key?key=enter") }
        
        // Fullscreen exit
        btnExitFullscreen.setOnClickListener { exitFullscreen() }
    }

    private var mirrorStreamRunnable: Runnable? = null
    private var mirrorStreaming = false

    private fun startMirrorStream() {
        if (!mirrorStreaming || connectionState != ConnectionState.CONNECTED) return
        
        mirrorExecutor.execute {
            try {
                val quality = mirrorQualitySpinner.selectedItem.toString().removeSuffix("%")
                val mode = if (mirrorMode == "mjpeg") "stream" else "live"
                val url = URL("$currentIp/mirror/$mode?q=$quality&w=1280")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 10000
                connection.setRequestProperty("User-Agent", "PC-Master-Android-App/1.0")
                
                if (connection.responseCode == 200) {
                    val inputStream = connection.inputStream
                    val buffer = ByteArray(8192)
                    val byteArrayOutputStream = ByteArrayOutputStream()
                    
                    var bytesRead: Int
                    while (mirrorStreaming && connectionState == ConnectionState.CONNECTED) {
                        bytesRead = inputStream.read(buffer)
                        if (bytesRead == -1) break
                        byteArrayOutputStream.write(buffer, 0, bytesRead)
                        
                        // For MJPEG, find JPEG boundaries
                        val data = byteArrayOutputStream.toByteArray()
                        if (mode == "stream") {
                            val start = findJpegStart(data)
                            val end = findJpegEnd(data)
                            if (start >= 0 && end > start) {
                                val jpegData = data.copyOfRange(start, end + 2)
                                runOnUiThread {
                                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
                                    mirrorImageView.setImageBitmap(bitmap)
                                    mirrorProgress.visibility = View.GONE
                                }
                                byteArrayOutputStream.reset()
                            }
                        } else {
                            // Adaptive mode - single JPEG
                            if (data.isNotEmpty()) {
                                runOnUiThread {
                                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size)
                                    mirrorImageView.setImageBitmap(bitmap)
                                    mirrorProgress.visibility = View.GONE
                                }
                                byteArrayOutputStream.reset()
                            }
                        }
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                // Stream ended or error
            }
        }
    }

    private fun findJpegStart(data: ByteArray): Int {
        for (i in 0 until data.size - 1) {
            if (data[i] == 0xFF.toByte() && data[i + 1] == 0xD8.toByte()) return i
        }
        return -1
    }

    private fun findJpegEnd(data: ByteArray): Int {
        for (i in 0 until data.size - 1) {
            if (data[i] == 0xFF.toByte() && data[i + 1] == 0xD9.toByte()) return i
        }
        return -1
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            enterFullscreen()
        } else {
            exitFullscreen()
        }
    }

    private fun enterFullscreen() {
        isFullscreen = true
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        
        btnFullscreen.text = "⛶ Exit FS"
        btnExitFullscreen.visibility = View.VISIBLE
        quickKeysBar.visibility = View.VISIBLE
        
        // Hide other tabs/navigation
        findViewById<LinearLayout>(R.id.connectedLayout)?.let { layout ->
            for (i in 0 until layout.childCount) {
                val child = layout.getChildAt(i)
                if (child.id != R.id.mirrorLayout && child.id != R.id.mirrorViewport) {
                    child.visibility = View.GONE
                }
            }
        }
    }

    private fun exitFullscreen() {
        isFullscreen = false
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        
        btnFullscreen.text = "⛶ Fullscreen"
        btnExitFullscreen.visibility = View.GONE
        quickKeysBar.visibility = View.GONE
        
        // Restore other tabs/navigation
        findViewById<LinearLayout>(R.id.connectedLayout)?.let { layout ->
            for (i in 0 until layout.childCount) {
                val child = layout.getChildAt(i)
                if (child.id != R.id.mirrorViewport) {
                    child.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun toggleKeyboard() {
        keyboardEnabled = !keyboardEnabled
        screenKeyboardBox.visibility = if (keyboardEnabled) View.VISIBLE else View.GONE
        btnKeyboardMirror.alpha = if (keyboardEnabled) 1.0f else 0.5f
    }

    private fun toggleTouchActions() {
        touchActionsEnabled = !touchActionsEnabled
        touchActionsBar.visibility = if (touchActionsEnabled) View.VISIBLE else View.GONE
        btnTouchActions.alpha = if (touchActionsEnabled) 1.0f else 0.5f
    }

    private fun updateMirrorUi(running: Boolean) {
        if (running) {
            btnToggleMirror.text = "STOP"
            btnToggleMirror.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF453A"))
            mirrorStatusText.text = "Mirroring Active"
            mirrorStatusText.setTextColor(Color.parseColor("#34C759"))
            mirrorProgress.visibility = View.VISIBLE
            
            // Start stream
            mirrorStreaming = true
            startMirrorStream()
        } else {
            btnToggleMirror.text = "START"
            btnToggleMirror.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#007AFF"))
            mirrorStatusText.text = "Not Mirroring"
            mirrorStatusText.setTextColor(Color.parseColor("#8E8E93"))
            mirrorImageView.setImageBitmap(null)
            mirrorProgress.visibility = View.GONE
            
            // Stop stream
            mirrorStreaming = false
        }
    }

    // Process data class
    data class PcProcess(
        val pid: Int,
        val name: String,
        val memory: String
    )
}
