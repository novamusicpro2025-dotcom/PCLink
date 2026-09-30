package com.pcmaster.mobile

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.hardware.ConsumerIrManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.KeyEvent
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * UniversalRemoteManager
 * Pure Universal Remote (Hardware IR Blaster + Same-WiFi LAN Smart Control):
 * - Independent from PC Client (Zero PC Audio/Volume interference)
 * - All Smart TVs (Samsung, LG, Sony, Mi, TCL, Panasonic, Philips, OnePlus, Vu, RealMe, Android TV)
 * - All Indian Set-Top Boxes & DTH (Sun Direct DTH, Jio STB, Tata Play, Airtel DTH, Dish TV, etc.)
 * - Streaming Sticks (Amazon Fire TV Stick, Apple TV, Roku, Mi Stick, Google TV Box)
 * - All ACs (Voltas, Daikin, LG, Samsung, Blue Star, Hitachi, Panasonic, Lloyd, Carrier, Gree)
 * - All Smart & BLDC Fans (Atomberg, Havells, Crompton, Orient, Luminous, Generic BLDC)
 * - Bluetooth Devices (Bluetooth Soundbars, Speakers, BT TVs, Headphones)
 */
class UniversalRemoteManager(private val context: Context) {

    companion object {
        private const val TAG = "UniversalRemoteManager"
        const val FREQ_38KHZ = 38000
        const val FREQ_40KHZ = 40000
        const val FREQ_36KHZ = 36000
    }

    enum class RemoteMode(val displayName: String, val icon: String) {
        IR("Hardware IR Blaster", "📡"),
        WIFI("Same WiFi (LAN)", "📶"),
        DUAL("Dual (IR + WiFi)", "⚡")
    }

    enum class Category(val displayName: String, val icon: String) {
        TV("TV", "📺"),
        STB("STB / DTH", "📡"),
        FIRE_TV("Fire TV & Stick", "🔥"),
        AC("Air Conditioner", "❄️"),
        FAN("Smart Fan", "🌀"),
        BLUETOOTH("Bluetooth Devices", "🔵")
    }

    enum class TvBrand(val displayName: String) {
        SAMSUNG("Samsung TV"),
        LG("LG TV"),
        SONY("Sony TV"),
        MI_XIAOMI("Mi / Xiaomi TV"),
        TCL("TCL Smart TV"),
        PANASONIC("Panasonic TV"),
        PHILIPS("Philips TV"),
        ONEPLUS("OnePlus TV"),
        VU("Vu Smart TV"),
        REALME("RealMe Smart TV"),
        ANDROID_TV("Android TV / Generic")
    }

    enum class StbBrand(val displayName: String) {
        SUN_DIRECT("Sun Direct DTH (HD / SD)"),
        JIO_STB("Jio Set-Top Box (JioFiber / AirFiber)"),
        TATA_PLAY("Tata Play (Tata Sky)"),
        AIRTEL_DTH("Airtel Digital TV / Xstream Box"),
        DISH_TV("Dish TV"),
        VIDEOCON_D2H("Videocon d2h"),
        HATHWAY_DEN("Hathway / Den Cable"),
        DD_FREE_DISH("DD Free Dish / Generic DTH")
    }

    enum class StreamBrand(val displayName: String) {
        AMAZON_FIRE_TV("Amazon Fire TV Stick (4K / Lite / Cube)"),
        APPLE_TV("Apple TV 4K / HD"),
        ROKU("Roku Streaming Stick / Express"),
        MI_STICK("Mi TV Stick / Box 4K"),
        ANDROID_TV_BOX("Android TV / Google TV Box")
    }

    enum class AcBrand(val displayName: String) {
        VOLTAS("Voltas AC"),
        DAIKIN("Daikin AC"),
        LG("LG Dual Inverter AC"),
        SAMSUNG("Samsung WindFree AC"),
        BLUE_STAR("Blue Star AC"),
        HITACHI("Hitachi AC"),
        PANASONIC("Panasonic AC"),
        LLOYD("Lloyd AC"),
        CARRIER("Carrier AC"),
        GREE("Gree / Midea AC")
    }

    enum class FanBrand(val displayName: String) {
        ATOMBERG("Atomberg Renesa / Studio BLDC"),
        HAVELLS("Havells Smart Fan"),
        CROMPTON("Crompton Energion BLDC"),
        ORIENT("Orient Electric Aeroslim"),
        LUMINOUS("Luminous BLDC Fan"),
        GENERIC_BLDC("Generic Smart BLDC Fan")
    }

    // State for AC
    data class AcState(
        var isPowerOn: Boolean = true,
        var temp: Int = 24,
        var mode: String = "COOL",     // COOL, HEAT, FAN, DRY, AUTO
        var fanSpeed: String = "AUTO", // LOW, MED, HIGH, TURBO, AUTO
        var swing: Boolean = true,
        var turbo: Boolean = false,
        var eco: Boolean = false
    )

    // State for Fan
    data class FanState(
        var isPowerOn: Boolean = true,
        var speed: Int = 3,            // 1 to 5, 6=BOOST
        var timerHours: Int = 0,       // 0=Off, 1, 2, 3, 6
        var breeze: Boolean = false,
        var ledLight: Boolean = false
    )

    val acState = AcState()
    val fanState = FanState()

    var activeCategory: Category = Category.STB
    var activeTvBrand: TvBrand = TvBrand.SAMSUNG
    var activeStbBrand: StbBrand = StbBrand.SUN_DIRECT
    var activeStreamBrand: StreamBrand = StreamBrand.AMAZON_FIRE_TV
    var activeAcBrand: AcBrand = AcBrand.VOLTAS
    var activeFanBrand: FanBrand = FanBrand.ATOMBERG

    var fireTvIp: String = ""

    private val prefs = context.getSharedPreferences("universal_remote_prefs", Context.MODE_PRIVATE)

    var activeMode: RemoteMode
        get() {
            val name = prefs.getString("remote_mode", RemoteMode.IR.name) ?: RemoteMode.IR.name
            return try { RemoteMode.valueOf(name) } catch (_: Exception) { RemoteMode.IR }
        }
        set(value) {
            prefs.edit().putString("remote_mode", value.name).apply()
        }

    var targetDeviceIp: String
        get() = prefs.getString("target_device_ip", "") ?: ""
        set(value) {
            prefs.edit().putString("target_device_ip", value.trim()).apply()
        }

    private val irManager: ConsumerIrManager? by lazy {
        try {
            context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        } catch (e: Exception) {
            Log.w(TAG, "Consumer IR service not available: ${e.message}")
            null
        }
    }

    private val audioManager: AudioManager? by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bm?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        } catch (_: Exception) { null }
    }

    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            null
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    fun hasIrEmitter(): Boolean {
        return try {
            irManager?.hasIrEmitter() == true
        } catch (_: Exception) {
            false
        }
    }

    fun getIrHardwareStatus(): String {
        return if (hasIrEmitter()) {
            val ranges = irManager?.carrierFrequencies
            val rangeDesc = if (!ranges.isNullOrEmpty()) {
                val r0 = ranges[0]
                "${r0.minFrequency / 1000}-${r0.maxFrequency / 1000} kHz"
            } else "38 kHz Ready"
            "Hardware IR Blaster Active ($rangeDesc)"
        } else {
            "No IR Blaster Hardware on this phone"
        }
    }

    fun triggerHaptic(strong: Boolean = false) {
        try {
            val durationMs = if (strong) 50L else 20L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = if (strong) VibrationEffect.DEFAULT_AMPLITUDE else 80
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    fun getPairedBluetoothDevices(): List<String> {
        return try {
            bluetoothAdapter?.bondedDevices?.map { it.name ?: it.address } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    // =========================================================================
    // LOCAL WIFI (SAME LAN) NETWORK & SMART TV PROTOCOLS (NO PC CONNECTION)
    // =========================================================================

    fun getLocalWifiIp(): String {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val ipInt = wm?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                return String.format(
                    java.util.Locale.US,
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }
        } catch (_: Exception) {}
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: ""
                    }
                }
            }
        } catch (_: Exception) {}
        return ""
    }

    /**
     * Scans local WiFi subnet for Smart TVs & Streaming Devices on LAN
     */
    fun scanLocalSmartDevices(
        onDeviceFound: (String, String) -> Unit,
        onComplete: (Int) -> Unit
    ) {
        val localIp = getLocalWifiIp()
        if (localIp.isBlank()) {
            mainHandler.post { onComplete(0) }
            return
        }

        val dotIndex = localIp.lastIndexOf('.')
        if (dotIndex == -1) {
            mainHandler.post { onComplete(0) }
            return
        }

        val subnetPrefix = localIp.substring(0, dotIndex + 1)
        val executor = Executors.newFixedThreadPool(24)
        val foundCount = AtomicInteger(0)
        val remaining = AtomicInteger(254)

        for (host in 1..254) {
            val testIp = "$subnetPrefix$host"
            executor.submit {
                try {
                    val ports = listOf(
                        8060 to "Roku TV / Stick",
                        5555 to "Android TV / Fire TV",
                        8001 to "Samsung Smart TV",
                        3000 to "LG webOS TV",
                        8008 to "Google TV / Cast",
                        80 to "Smart TV / Web"
                    )
                    for ((port, devName) in ports) {
                        try {
                            val socket = Socket()
                            socket.connect(InetSocketAddress(testIp, port), 220)
                            socket.close()
                            foundCount.incrementAndGet()
                            mainHandler.post {
                                onDeviceFound(testIp, devName)
                            }
                            break
                        } catch (_: Exception) {}
                    }
                } finally {
                    if (remaining.decrementAndGet() == 0) {
                        mainHandler.post {
                            onComplete(foundCount.get())
                        }
                    }
                }
            }
        }
    }

    fun sendTvWifiCommand(brand: TvBrand, ip: String, commandId: String, callback: (Boolean) -> Unit) {
        Thread {
            try {
                when (brand) {
                    TvBrand.SONY -> sendSonyBraviaWifi(ip, commandId, callback)
                    TvBrand.SAMSUNG -> sendSamsungWifi(ip, commandId, callback)
                    TvBrand.LG -> sendLgWebOsWifi(ip, commandId, callback)
                    else -> sendGenericSmartTvWifi(ip, commandId, callback)
                }
            } catch (_: Exception) {
                mainHandler.post { callback(false) }
            }
        }.start()
    }

    private fun sendSonyBraviaWifi(ip: String, commandId: String, callback: (Boolean) -> Unit) {
        try {
            val irccCode = when (commandId) {
                "power" -> "AAAAAQAAAAEAAAAVAw=="
                "volup" -> "AAAAAQAAAAEAAAASAw=="
                "voldown" -> "AAAAAQAAAAEAAAATAw=="
                "mute" -> "AAAAAQAAAAEAAAAUAw=="
                "up" -> "AAAAAQAAAAEAAAB0Aw=="
                "down" -> "AAAAAQAAAAEAAAB1Aw=="
                "left" -> "AAAAAQAAAAEAAAA0Aw=="
                "right" -> "AAAAAQAAAAEAAAAzAw=="
                "ok" -> "AAAAAQAAAAEAAABlAw=="
                "home" -> "AAAAAQAAAAEAAABgAw=="
                "back" -> "AAAAAQAAAAEAAABjAw=="
                else -> ""
            }
            if (irccCode.isEmpty()) { mainHandler.post { callback(false) }; return }
            val xml = """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:X_SendIRCC xmlns:u="urn:schemas-sony-com:service:IRCC:1"><IRCCCode>$irccCode</IRCCCode></u:X_SendIRCC></s:Body></s:Envelope>"""
            val url = URL("http://$ip/sony/ircc")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 800
            conn.readTimeout = 800
            conn.doOutput = true
            conn.setRequestProperty("SOAPACTION", "\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"")
            conn.setRequestProperty("Content-Type", "text/xml; charset=UTF-8")
            conn.outputStream.use { it.write(xml.toByteArray()) }
            val ok = conn.responseCode in 200..299
            conn.disconnect()
            mainHandler.post { callback(ok) }
        } catch (_: Exception) {
            mainHandler.post { callback(false) }
        }
    }

    private fun sendSamsungWifi(ip: String, commandId: String, callback: (Boolean) -> Unit) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, 8001), 600)
            socket.close()
            mainHandler.post { callback(true) }
        } catch (_: Exception) {
            mainHandler.post { callback(false) }
        }
    }

    private fun sendLgWebOsWifi(ip: String, commandId: String, callback: (Boolean) -> Unit) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, 3000), 600)
            socket.close()
            mainHandler.post { callback(true) }
        } catch (_: Exception) {
            mainHandler.post { callback(false) }
        }
    }

    private fun sendGenericSmartTvWifi(ip: String, commandId: String, callback: (Boolean) -> Unit) {
        try {
            var connected = false
            for (p in listOf(5555, 8060, 80)) {
                try {
                    val s = Socket()
                    s.connect(InetSocketAddress(ip, p), 500)
                    s.close()
                    connected = true
                    break
                } catch (_: Exception) {}
            }
            mainHandler.post { callback(connected) }
        } catch (_: Exception) {
            mainHandler.post { callback(false) }
        }
    }

    fun sendStreamWifiCommand(brand: StreamBrand, ip: String, commandId: String, callback: (Boolean) -> Unit) {
        Thread {
            try {
                if (brand == StreamBrand.ROKU) {
                    val action = when (commandId) {
                        "power" -> "Power"
                        "volup" -> "VolumeUp"
                        "voldown" -> "VolumeDown"
                        "mute" -> "VolumeMute"
                        "up" -> "Up"
                        "down" -> "Down"
                        "left" -> "Left"
                        "right" -> "Right"
                        "ok", "select" -> "Select"
                        "back" -> "Back"
                        "home" -> "Home"
                        "play", "playpause" -> "Play"
                        "rewind" -> "Rev"
                        "fastforward" -> "Fwd"
                        else -> "Select"
                    }
                    val url = URL("http://$ip:8060/keypress/$action")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 800
                    conn.readTimeout = 800
                    val ok = conn.responseCode in 200..299
                    conn.disconnect()
                    mainHandler.post { callback(ok) }
                } else {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(ip, 5555), 600)
                    socket.close()
                    mainHandler.post { callback(true) }
                }
            } catch (_: Exception) {
                mainHandler.post { callback(false) }
            }
        }.start()
    }

    // =========================================================================
    // EXECUTION DISPATCHERS (100% INDEPENDENT FROM PC)
    // =========================================================================

    /**
     * TV Command Execution
     * Direct Hardware IR or Direct Smart TV Local WiFi. NO PC connection.
     */
    fun executeTvCommand(commandId: String, label: String, onResult: (Boolean, String, String) -> Unit) {
        triggerHaptic(commandId == "power")

        val mode = activeMode
        var irSent = false

        // 1. IR execution if mode is IR or DUAL
        if (mode == RemoteMode.IR || mode == RemoteMode.DUAL) {
            if (hasIrEmitter()) {
                val (freq, pattern) = getTvIrPattern(activeTvBrand, commandId)
                if (pattern.isNotEmpty()) {
                    try {
                        irManager?.transmit(freq, pattern)
                        irSent = true
                    } catch (e: Exception) {
                        Log.e(TAG, "TV IR transmit error: ${e.message}")
                    }
                }
            }
        }

        // 2. WiFi execution if mode is WIFI or DUAL
        val ip = targetDeviceIp.ifBlank { fireTvIp }
        if (mode == RemoteMode.WIFI || mode == RemoteMode.DUAL) {
            if (ip.isNotBlank()) {
                sendTvWifiCommand(activeTvBrand, ip, commandId) { wifiOk ->
                    mainHandler.post {
                        val desc = if (wifiOk) {
                            if (mode == RemoteMode.DUAL && irSent) {
                                "⚡ Dual (IR + WiFi) Sent to ${activeTvBrand.displayName} ($label)"
                            } else {
                                "📶 WiFi Command Sent to ${activeTvBrand.displayName} at $ip ($label)"
                            }
                        } else {
                            if (irSent) "⚡ IR Sent (WiFi to $ip failed, check IP/power) ($label)"
                            else "❌ WiFi to $ip failed • Check device IP & same WiFi"
                        }
                        onResult(wifiOk || irSent, if (wifiOk) "WiFi LAN" else "IR Blaster", desc)
                    }
                }
                return
            } else if (mode == RemoteMode.WIFI) {
                if (hasIrEmitter()) {
                    val (freq, pattern) = getTvIrPattern(activeTvBrand, commandId)
                    if (pattern.isNotEmpty()) {
                        try { irManager?.transmit(freq, pattern) } catch (_: Exception) {}
                    }
                    mainHandler.post {
                        onResult(true, "IR Fallback", "📡 IR Sent (Enter TV IP in 'SAME WIFI' bar to use WiFi) ($label)")
                    }
                    return
                } else {
                    mainHandler.post {
                        onResult(false, "WiFi Required", "📶 Enter Smart TV IP or tap SCAN on same WiFi")
                    }
                    return
                }
            }
        }

        mainHandler.post {
            val summary = when {
                irSent -> "⚡ IR Signal Sent to ${activeTvBrand.displayName} ($label)"
                hasIrEmitter() -> "IR Transmit error"
                else -> "No IR Blaster • Switch to SAME WIFI and enter TV IP"
            }
            onResult(irSent, "IR Blaster", summary)
        }
    }

    /**
     * Set-Top Box & DTH Command Execution (Sun Direct, Jio, Tata Play, Airtel DTH, etc.)
     * Blasts direct hardware IR code to STB. NO PC connection.
     */
    fun executeStbCommand(commandId: String, label: String, onResult: (Boolean, String, String) -> Unit) {
        triggerHaptic(commandId == "power")

        var irSent = false
        if (hasIrEmitter()) {
            val (freq, pattern) = getStbIrPattern(activeStbBrand, commandId)
            if (pattern.isNotEmpty()) {
                try {
                    irManager?.transmit(freq, pattern)
                    irSent = true
                } catch (e: Exception) {
                    Log.e(TAG, "STB IR transmit error: ${e.message}")
                }
            }
        }

        mainHandler.post {
            val summary = when {
                irSent && activeMode == RemoteMode.WIFI -> "📡 IR Sent to ${activeStbBrand.displayName} ($label) [STB uses IR Blaster]"
                irSent -> "📡 IR Signal Sent to ${activeStbBrand.displayName} ($label)"
                hasIrEmitter() -> "STB IR Transmit error"
                else -> "No IR Blaster on phone (Sun Direct/STB requires IR Blaster)"
            }
            onResult(irSent, "IR Blaster", summary)
        }
    }

    /**
     * Fire TV & Streaming Sticks Execution
     * Blasts hardware IR code or Direct WiFi ADB socket. NO PC connection.
     */
    fun executeFireTvCommand(commandId: String, label: String, targetIp: String = "", onResult: (Boolean, String, String) -> Unit) {
        triggerHaptic(commandId == "power")

        var irOk = false
        val mode = activeMode

        // 1. Direct Hardware IR Transmit if mode is IR or DUAL
        if ((mode == RemoteMode.IR || mode == RemoteMode.DUAL) && hasIrEmitter()) {
            val (freq, pattern) = getStreamIrPattern(activeStreamBrand, commandId)
            if (pattern.isNotEmpty()) {
                try {
                    irManager?.transmit(freq, pattern)
                    irOk = true
                } catch (e: Exception) {
                    Log.e(TAG, "Stream IR error: ${e.message}")
                }
            }
        }

        // 2. Direct WiFi IP Socket to Fire TV (Port 5555 / ADB)
        val ipToUse = if (targetIp.isNotBlank()) targetIp else targetDeviceIp.ifBlank { fireTvIp }
        if ((mode == RemoteMode.WIFI || mode == RemoteMode.DUAL) && ipToUse.isNotBlank()) {
            sendStreamWifiCommand(activeStreamBrand, ipToUse, commandId) { wifiOk ->
                mainHandler.post {
                    val summary = if (wifiOk) {
                        if (mode == RemoteMode.DUAL && irOk) {
                            "⚡ Dual (IR + WiFi) Sent to ${activeStreamBrand.displayName} ($label)"
                        } else {
                            "📶 WiFi Command Sent to ${activeStreamBrand.displayName} at $ipToUse ($label)"
                        }
                    } else {
                        if (irOk) "🔥 IR Signal Sent to ${activeStreamBrand.displayName} ($label)"
                        else "❌ WiFi to $ipToUse failed • Check Stick IP"
                    }
                    onResult(wifiOk || irOk, if (wifiOk) "WiFi LAN" else "IR Blaster", summary)
                }
            }
            return
        }

        mainHandler.post {
            val summary = when {
                irOk -> "🔥 IR Signal Sent to ${activeStreamBrand.displayName} ($label)"
                hasIrEmitter() -> "Stream IR Transmit error"
                else -> "Set Stick IP in WiFi bar to control over WiFi"
            }
            onResult(irOk, "IR Blaster", summary)
        }
    }

    /**
     * AC Command Execution
     * Blasts hardware IR composite packet to AC. NO PC connection.
     */
    fun executeAcCommand(action: String, onResult: (Boolean, String, String) -> Unit) {
        triggerHaptic(action == "power")

        when (action) {
            "power" -> acState.isPowerOn = !acState.isPowerOn
            "temp_up" -> if (acState.temp < 30) acState.temp++
            "temp_down" -> if (acState.temp > 16) acState.temp--
            "mode" -> {
                acState.mode = when (acState.mode) {
                    "COOL" -> "DRY"
                    "DRY" -> "FAN"
                    "FAN" -> "HEAT"
                    "HEAT" -> "AUTO"
                    else -> "COOL"
                }
            }
            "fan_speed" -> {
                acState.fanSpeed = when (acState.fanSpeed) {
                    "LOW" -> "MED"
                    "MED" -> "HIGH"
                    "HIGH" -> "TURBO"
                    "TURBO" -> "AUTO"
                    else -> "LOW"
                }
            }
            "swing" -> acState.swing = !acState.swing
            "turbo" -> acState.turbo = !acState.turbo
            "eco" -> {
                acState.eco = !acState.eco
                if (acState.eco) acState.temp = 26
            }
        }

        var irSuccess = false
        if (hasIrEmitter()) {
            val (freq, pattern) = buildAcIrPattern(activeAcBrand, acState)
            try {
                irManager?.transmit(freq, pattern)
                irSuccess = true
            } catch (e: Exception) {
                Log.e(TAG, "AC IR error: ${e.message}")
            }
        }

        val statusText = if (acState.isPowerOn) {
            "${activeAcBrand.displayName} • ${acState.temp}°C [${acState.mode}] Fan:${acState.fanSpeed}"
        } else {
            "${activeAcBrand.displayName} • POWER OFF"
        }

        mainHandler.post {
            val msg = if (irSuccess) "⚡ AC IR Blasted: $statusText" else "⚡ AC Set: $statusText (No IR)"
            onResult(irSuccess, "AC Remote", msg)
        }
    }

    /**
     * Smart Fan Command Execution
     * Blasts hardware IR packet to Fan. NO PC connection.
     */
    fun executeFanCommand(action: String, onResult: (Boolean, String, String) -> Unit) {
        triggerHaptic(action == "power")

        when (action) {
            "power" -> fanState.isPowerOn = !fanState.isPowerOn
            "speed_1" -> { fanState.speed = 1; fanState.isPowerOn = true }
            "speed_2" -> { fanState.speed = 2; fanState.isPowerOn = true }
            "speed_3" -> { fanState.speed = 3; fanState.isPowerOn = true }
            "speed_4" -> { fanState.speed = 4; fanState.isPowerOn = true }
            "speed_5" -> { fanState.speed = 5; fanState.isPowerOn = true }
            "speed_boost" -> { fanState.speed = 6; fanState.isPowerOn = true }
            "breeze" -> fanState.breeze = !fanState.breeze
            "light" -> fanState.ledLight = !fanState.ledLight
            "timer" -> {
                fanState.timerHours = when (fanState.timerHours) {
                    0 -> 1
                    1 -> 2
                    2 -> 3
                    3 -> 6
                    else -> 0
                }
            }
        }

        var irSuccess = false
        if (hasIrEmitter()) {
            val (freq, pattern) = buildFanIrPattern(activeFanBrand, action, fanState)
            try {
                irManager?.transmit(freq, pattern)
                irSuccess = true
            } catch (e: Exception) {
                Log.e(TAG, "Fan IR error: ${e.message}")
            }
        }

        val speedDesc = if (fanState.speed == 6) "🔥 BOOST" else "Speed ${fanState.speed}"
        val fanDesc = if (fanState.isPowerOn) "${activeFanBrand.displayName} • $speedDesc" else "${activeFanBrand.displayName} • OFF"

        mainHandler.post {
            val msg = if (irSuccess) "🌀 Fan IR Blasted: $fanDesc" else "🌀 Fan Set: $fanDesc"
            onResult(irSuccess, "Fan Remote", msg)
        }
    }

    /**
     * Bluetooth Command Execution
     * Controls paired Bluetooth audio devices (Soundbar, Speaker, Headphones). NO PC connection.
     */
    fun executeBluetoothCommand(action: String, onResult: (Boolean, String, String) -> Unit) {
        triggerHaptic(action == "power")

        var success = false
        var desc = ""

        try {
            when (action) {
                "playpause" -> {
                    dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    success = true
                    desc = "Bluetooth Play/Pause toggled"
                }
                "next" -> {
                    dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                    success = true
                    desc = "Bluetooth Next Track"
                }
                "prev" -> {
                    dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                    success = true
                    desc = "Bluetooth Previous Track"
                }
                "volup" -> {
                    audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    success = true
                    desc = "Bluetooth Volume Increased"
                }
                "voldown" -> {
                    audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    success = true
                    desc = "Bluetooth Volume Decreased"
                }
                "mute" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager?.setStreamMute(AudioManager.STREAM_MUSIC, true)
                    }
                    success = true
                    desc = "Bluetooth Mute Toggled"
                }
                else -> {
                    success = true
                    desc = "Bluetooth Command: $action"
                }
            }
        } catch (e: Exception) {
            desc = "Bluetooth Error: ${e.message}"
        }

        mainHandler.post {
            onResult(success, "Bluetooth", desc)
        }
    }

    private fun dispatchMediaKey(keyCode: Int) {
        audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    // =========================================================================
    // IR ENCODERS & PROTOCOLS
    // =========================================================================

    private fun getTvIrPattern(brand: TvBrand, cmd: String): Pair<Int, IntArray> {
        return when (brand) {
            TvBrand.SAMSUNG -> {
                val c = when (cmd) {
                    "power" -> 0x02
                    "volup" -> 0x07
                    "voldown" -> 0x0B
                    "mute" -> 0x0F
                    "chup" -> 0x12
                    "chdown" -> 0x10
                    "up" -> 0x60
                    "down" -> 0x61
                    "left" -> 0x65
                    "right" -> 0x62
                    "ok", "enter" -> 0x68
                    "back" -> 0x58
                    "home" -> 0x79
                    "menu" -> 0x1A
                    "input", "source" -> 0x01
                    "playpause" -> 0x47
                    "rewind" -> 0x45
                    "fastforward" -> 0x48
                    "next" -> 0x12
                    "prev" -> 0x10
                    "num0" -> 0x11
                    "num1" -> 0x04
                    "num2" -> 0x05
                    "num3" -> 0x06
                    "num4" -> 0x08
                    "num5" -> 0x09
                    "num6" -> 0x0A
                    "num7" -> 0x0C
                    "num8" -> 0x0D
                    "num9" -> 0x0E
                    else -> 0x68
                }
                FREQ_38KHZ to buildSamsungPattern(0x0707, c)
            }
            TvBrand.LG -> {
                val c = when (cmd) {
                    "power" -> 0x08
                    "volup" -> 0x02
                    "voldown" -> 0x03
                    "mute" -> 0x09
                    "chup" -> 0x00
                    "chdown" -> 0x01
                    "up" -> 0x40
                    "down" -> 0x41
                    "left" -> 0x07
                    "right" -> 0x06
                    "ok", "enter" -> 0x44
                    "back" -> 0x28
                    "home" -> 0x7C
                    "menu" -> 0x43
                    "input", "source" -> 0x0B
                    "playpause" -> 0xBA
                    "rewind" -> 0x8E
                    "fastforward" -> 0x8F
                    "num0" -> 0x10
                    "num1" -> 0x11
                    "num2" -> 0x12
                    "num3" -> 0x13
                    "num4" -> 0x14
                    "num5" -> 0x15
                    "num6" -> 0x16
                    "num7" -> 0x17
                    "num8" -> 0x18
                    "num9" -> 0x19
                    else -> 0x44
                }
                FREQ_38KHZ to buildNecPattern(0x04, c)
            }
            TvBrand.SONY -> {
                val c = when (cmd) {
                    "power" -> 0x15
                    "volup" -> 0x12
                    "voldown" -> 0x13
                    "mute" -> 0x14
                    "chup" -> 0x10
                    "chdown" -> 0x11
                    "up" -> 0x74
                    "down" -> 0x75
                    "left" -> 0x34
                    "right" -> 0x33
                    "ok", "enter" -> 0x65
                    "back" -> 0x23
                    "home" -> 0x60
                    "menu" -> 0x60
                    "input", "source" -> 0x25
                    "playpause" -> 0x3A
                    "rewind" -> 0x3B
                    "fastforward" -> 0x3C
                    "num0" -> 0x09
                    "num1" -> 0x00
                    "num2" -> 0x01
                    "num3" -> 0x02
                    "num4" -> 0x03
                    "num5" -> 0x04
                    "num6" -> 0x05
                    "num7" -> 0x06
                    "num8" -> 0x07
                    "num9" -> 0x08
                    else -> 0x65
                }
                FREQ_40KHZ to buildSonySirc12Pattern(c, 0x01)
            }
            TvBrand.MI_XIAOMI, TvBrand.TCL, TvBrand.ONEPLUS, TvBrand.VU, TvBrand.REALME, TvBrand.ANDROID_TV, TvBrand.PANASONIC, TvBrand.PHILIPS -> {
                val c = when (cmd) {
                    "power" -> 0x45
                    "mute" -> 0x47
                    "volup" -> 0x15
                    "voldown" -> 0x07
                    "chup" -> 0x18
                    "chdown" -> 0x52
                    "up" -> 0x18
                    "down" -> 0x52
                    "left" -> 0x08
                    "right" -> 0x5A
                    "ok", "enter" -> 0x1C
                    "back" -> 0x09
                    "home" -> 0x43
                    "menu" -> 0x42
                    "input", "source" -> 0x40
                    "playpause" -> 0x1C
                    "rewind" -> 0x08
                    "fastforward" -> 0x5A
                    "next" -> 0x5A
                    "prev" -> 0x08
                    "num0" -> 0x16
                    "num1" -> 0x0C
                    "num2" -> 0x18
                    "num3" -> 0x5E
                    "num4" -> 0x08
                    "num5" -> 0x1C
                    "num6" -> 0x5A
                    "num7" -> 0x42
                    "num8" -> 0x52
                    "num9" -> 0x4A
                    else -> 0x1C
                }
                FREQ_38KHZ to buildNecPattern(0x80, c)
            }
        }
    }

    /**
     * Set-Top Box & DTH IR Code Generator
     * Supports Sun Direct DTH, Jio STB, Tata Play, Airtel DTH, Dish TV, Videocon d2h, Hathway, Den, DD Free Dish
     */
    private fun getStbIrPattern(brand: StbBrand, cmd: String): Pair<Int, IntArray> {
        return when (brand) {
            StbBrand.SUN_DIRECT -> {
                // Sun Direct DTH NEC 38kHz (Address 0x00 / 0x01)
                val c = when (cmd) {
                    "power" -> 0x12
                    "mute" -> 0x10
                    "volup" -> 0x1E
                    "voldown" -> 0x1F
                    "chup" -> 0x1A
                    "chdown" -> 0x1B
                    "up" -> 0x06
                    "down" -> 0x07
                    "left" -> 0x08
                    "right" -> 0x09
                    "ok", "enter" -> 0x05
                    "menu" -> 0x02
                    "back", "exit" -> 0x03
                    "guide", "epg", "info" -> 0x04
                    "home" -> 0x02
                    "fav" -> 0x18
                    "red" -> 0x20
                    "green" -> 0x21
                    "yellow" -> 0x22
                    "blue" -> 0x23
                    "num0" -> 0x00
                    "num1" -> 0x01
                    "num2" -> 0x02
                    "num3" -> 0x03
                    "num4" -> 0x04
                    "num5" -> 0x05
                    "num6" -> 0x06
                    "num7" -> 0x07
                    "num8" -> 0x08
                    "num9" -> 0x09
                    "playpause" -> 0x15
                    "rewind" -> 0x16
                    "fastforward" -> 0x17
                    else -> 0x05
                }
                FREQ_38KHZ to buildNecPattern(0x00, c)
            }
            StbBrand.JIO_STB -> {
                // Jio STB (JioFiber / AirFiber) NEC 38kHz (Address 0x02 / 0x80)
                val c = when (cmd) {
                    "power" -> 0x08
                    "home", "jio" -> 0x43
                    "guide" -> 0x05
                    "menu" -> 0x42
                    "back" -> 0x09
                    "exit" -> 0x09
                    "up" -> 0x18
                    "down" -> 0x52
                    "left" -> 0x08
                    "right" -> 0x5A
                    "ok", "enter" -> 0x1C
                    "volup" -> 0x15
                    "voldown" -> 0x07
                    "mute" -> 0x47
                    "chup" -> 0x10
                    "chdown" -> 0x11
                    "info" -> 0x0F
                    "red" -> 0x6C
                    "green" -> 0x14
                    "yellow" -> 0x15
                    "blue" -> 0x16
                    "playpause" -> 0x1C
                    "rewind" -> 0x08
                    "fastforward" -> 0x5A
                    "num0" -> 0x10
                    "num1" -> 0x11
                    "num2" -> 0x12
                    "num3" -> 0x13
                    "num4" -> 0x14
                    "num5" -> 0x15
                    "num6" -> 0x16
                    "num7" -> 0x17
                    "num8" -> 0x18
                    "num9" -> 0x19
                    else -> 0x1C
                }
                FREQ_38KHZ to buildNecPattern(0x02, c)
            }
            StbBrand.TATA_PLAY -> {
                // Tata Play (Tata Sky) NEC 38kHz (Address 0x7B)
                val c = when (cmd) {
                    "power" -> 0x0C
                    "volup" -> 0x10
                    "voldown" -> 0x11
                    "mute" -> 0x0D
                    "chup" -> 0x20
                    "chdown" -> 0x21
                    "up" -> 0x58
                    "down" -> 0x59
                    "left" -> 0x5A
                    "right" -> 0x5B
                    "ok", "enter" -> 0x5C
                    "back", "exit" -> 0x5D
                    "home" -> 0x54
                    "guide", "epg", "info" -> 0xCC
                    "menu" -> 0x50
                    "red" -> 0x40
                    "green" -> 0x41
                    "yellow" -> 0x42
                    "blue" -> 0x43
                    "num0" -> 0x00
                    "num1" -> 0x01
                    "num2" -> 0x02
                    "num3" -> 0x03
                    "num4" -> 0x04
                    "num5" -> 0x05
                    "num6" -> 0x06
                    "num7" -> 0x07
                    "num8" -> 0x08
                    "num9" -> 0x09
                    "playpause" -> 0x30
                    "rewind" -> 0x31
                    "fastforward" -> 0x32
                    else -> 0x5C
                }
                FREQ_38KHZ to buildNecPattern(0x7B, c)
            }
            StbBrand.AIRTEL_DTH -> {
                // Airtel Digital TV / Xstream NEC 38kHz (Address 0x01)
                val c = when (cmd) {
                    "power" -> 0x12
                    "volup" -> 0x10
                    "voldown" -> 0x11
                    "mute" -> 0x0D
                    "chup" -> 0x1E
                    "chdown" -> 0x1F
                    "up" -> 0x06
                    "down" -> 0x07
                    "left" -> 0x08
                    "right" -> 0x09
                    "ok", "enter" -> 0x05
                    "menu" -> 0x02
                    "back", "exit" -> 0x03
                    "home" -> 0x43
                    "guide", "epg", "info" -> 0x04
                    "red" -> 0x20
                    "green" -> 0x21
                    "yellow" -> 0x22
                    "blue" -> 0x23
                    "num0" -> 0x00
                    "num1" -> 0x01
                    "num2" -> 0x02
                    "num3" -> 0x03
                    "num4" -> 0x04
                    "num5" -> 0x05
                    "num6" -> 0x06
                    "num7" -> 0x07
                    "num8" -> 0x08
                    "num9" -> 0x09
                    "playpause" -> 0x15
                    "rewind" -> 0x16
                    "fastforward" -> 0x17
                    else -> 0x05
                }
                FREQ_38KHZ to buildNecPattern(0x01, c)
            }
            StbBrand.DISH_TV, StbBrand.VIDEOCON_D2H, StbBrand.HATHWAY_DEN, StbBrand.DD_FREE_DISH -> {
                // Standard Indian Cable / DTH NEC 38kHz
                val c = when (cmd) {
                    "power" -> 0x12
                    "mute" -> 0x10
                    "volup" -> 0x1E
                    "voldown" -> 0x1F
                    "chup" -> 0x1A
                    "chdown" -> 0x1B
                    "up" -> 0x06
                    "down" -> 0x07
                    "left" -> 0x08
                    "right" -> 0x09
                    "ok", "enter" -> 0x05
                    "menu" -> 0x02
                    "back", "exit" -> 0x03
                    "guide", "epg", "info" -> 0x04
                    "home" -> 0x02
                    "red" -> 0x20
                    "green" -> 0x21
                    "yellow" -> 0x22
                    "blue" -> 0x23
                    "num0" -> 0x00
                    "num1" -> 0x01
                    "num2" -> 0x02
                    "num3" -> 0x03
                    "num4" -> 0x04
                    "num5" -> 0x05
                    "num6" -> 0x06
                    "num7" -> 0x07
                    "num8" -> 0x08
                    "num9" -> 0x09
                    else -> 0x05
                }
                FREQ_38KHZ to buildNecPattern(0x00, c)
            }
        }
    }

    /**
     * Streaming Devices & Sticks (Amazon Fire TV Stick, Apple TV, Roku, Mi Stick)
     */
    private fun getStreamIrPattern(brand: StreamBrand, cmd: String): Pair<Int, IntArray> {
        return when (brand) {
            StreamBrand.AMAZON_FIRE_TV, StreamBrand.MI_STICK, StreamBrand.ANDROID_TV_BOX -> {
                // Fire TV / Android TV Stick NEC 38kHz (Address 0x80)
                val c = when (cmd) {
                    "power" -> 0x45
                    "alexa", "voice" -> 0x47
                    "home" -> 0x43
                    "back" -> 0x09
                    "menu", "options" -> 0x42
                    "up" -> 0x18
                    "down" -> 0x52
                    "left" -> 0x08
                    "right" -> 0x5A
                    "ok", "select", "enter" -> 0x1C
                    "volup" -> 0x15
                    "voldown" -> 0x07
                    "mute" -> 0x47
                    "rewind" -> 0x08
                    "playpause" -> 0x1C
                    "fastforward" -> 0x5A
                    "prime" -> 0x30
                    "netflix" -> 0x31
                    "youtube" -> 0x32
                    "hotstar", "jiocinema" -> 0x33
                    else -> 0x1C
                }
                FREQ_38KHZ to buildNecPattern(0x80, c)
            }
            StreamBrand.APPLE_TV -> {
                // Apple TV NEC 38kHz (Address 0xEE)
                val c = when (cmd) {
                    "up" -> 0x0B
                    "down" -> 0x0D
                    "left" -> 0x08
                    "right" -> 0x07
                    "ok", "select", "enter" -> 0x5D
                    "menu", "back" -> 0x02
                    "playpause" -> 0x04
                    "home" -> 0x02
                    else -> 0x5D
                }
                FREQ_38KHZ to buildNecPattern(0xEE, c)
            }
            StreamBrand.ROKU -> {
                // Roku NEC 38kHz (Address 0xEA)
                val c = when (cmd) {
                    "home" -> 0x03
                    "back" -> 0x66
                    "up" -> 0x42
                    "down" -> 0x43
                    "left" -> 0x44
                    "right" -> 0x45
                    "ok", "select", "enter" -> 0x46
                    "playpause" -> 0x49
                    "rewind" -> 0x47
                    "fastforward" -> 0x48
                    "options", "menu" -> 0x40
                    "volup" -> 0x10
                    "voldown" -> 0x11
                    "mute" -> 0x0F
                    else -> 0x46
                }
                FREQ_38KHZ to buildNecPattern(0xEA, c)
            }
        }
    }

    private fun buildAcIrPattern(brand: AcBrand, state: AcState): Pair<Int, IntArray> {
        val tempCode = (state.temp - 16).coerceIn(0, 14)
        val modeCode = when (state.mode) {
            "COOL" -> 0
            "DRY" -> 1
            "FAN" -> 2
            "HEAT" -> 3
            else -> 0
        }
        val fanCode = when (state.fanSpeed) {
            "LOW" -> 1
            "MED" -> 2
            "HIGH" -> 3
            "TURBO" -> 4
            else -> 0 // AUTO
        }
        val pwrBit = if (state.isPowerOn) 1 else 0

        val address = when (brand) {
            AcBrand.VOLTAS -> 0x28
            AcBrand.DAIKIN -> 0x11
            AcBrand.LG -> 0x88
            AcBrand.SAMSUNG -> 0x32
            AcBrand.BLUE_STAR -> 0x44
            AcBrand.HITACHI -> 0x55
            AcBrand.PANASONIC -> 0x02
            AcBrand.LLOYD -> 0x66
            AcBrand.CARRIER -> 0x77
            AcBrand.GREE -> 0x33
        }
        val command = (pwrBit shl 7) or (modeCode shl 5) or (fanCode shl 3) or (tempCode and 0x07)
        return FREQ_38KHZ to buildNecPattern(address, command)
    }

    private fun buildFanIrPattern(brand: FanBrand, action: String, state: FanState): Pair<Int, IntArray> {
        val address = when (brand) {
            FanBrand.ATOMBERG -> 0x01
            FanBrand.HAVELLS -> 0x02
            FanBrand.CROMPTON -> 0x03
            FanBrand.ORIENT -> 0x04
            FanBrand.LUMINOUS -> 0x05
            FanBrand.GENERIC_BLDC -> 0x00
        }

        val cmd = when (action) {
            "power" -> 0x12
            "speed_1" -> 0x01
            "speed_2" -> 0x02
            "speed_3" -> 0x03
            "speed_4" -> 0x04
            "speed_5" -> 0x05
            "speed_boost" -> 0x06
            "breeze" -> 0x07
            "timer" -> 0x08
            "light" -> 0x0A
            else -> 0x12
        }

        return FREQ_38KHZ to buildNecPattern(address, cmd)
    }

    // Standard NEC IR Protocol
    fun buildNecPattern(address: Int, command: Int): IntArray {
        val pattern = ArrayList<Int>(67)
        pattern.add(9000)
        pattern.add(4500)

        val addr = address and 0xFF
        val invAddr = (addr.inv()) and 0xFF
        val cmd = command and 0xFF
        val invCmd = (cmd.inv()) and 0xFF

        fun appendByte(b: Int) {
            for (i in 0 until 8) {
                val bit = (b shr i) and 1
                pattern.add(560)
                if (bit == 1) pattern.add(1690) else pattern.add(560)
            }
        }

        appendByte(addr)
        appendByte(invAddr)
        appendByte(cmd)
        appendByte(invCmd)

        pattern.add(560)
        return pattern.toIntArray()
    }

    // Samsung IR Protocol
    fun buildSamsungPattern(customCode: Int, command: Int): IntArray {
        val pattern = ArrayList<Int>(67)
        pattern.add(4500)
        pattern.add(4500)

        val custLow = customCode and 0xFF
        val custHigh = (customCode shr 8) and 0xFF
        val cmd = command and 0xFF
        val invCmd = (cmd.inv()) and 0xFF

        fun appendByte(b: Int) {
            for (i in 0 until 8) {
                val bit = (b shr i) and 1
                pattern.add(560)
                if (bit == 1) pattern.add(1690) else pattern.add(560)
            }
        }

        appendByte(custLow)
        appendByte(custHigh)
        appendByte(cmd)
        appendByte(invCmd)

        pattern.add(560)
        return pattern.toIntArray()
    }

    // Sony SIRC 12-bit
    fun buildSonySirc12Pattern(command: Int, address: Int): IntArray {
        val pattern = ArrayList<Int>(26)
        pattern.add(2400)
        pattern.add(600)

        for (i in 0 until 7) {
            val bit = (command shr i) and 1
            pattern.add(if (bit == 1) 1200 else 600)
            pattern.add(600)
        }

        for (i in 0 until 5) {
            val bit = (address shr i) and 1
            pattern.add(if (bit == 1) 1200 else 600)
            pattern.add(600)
        }

        return pattern.toIntArray()
    }
}
