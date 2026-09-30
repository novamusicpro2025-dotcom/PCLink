package com.pcmaster.mobile

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.WindowManager
import android.widget.*

/**
 * UniversalRemoteDialog
 * Pure Hardware Appliance Remote (Completely Independent from PC):
 * - All Indian Set-Top Boxes & DTH (Sun Direct DTH, Jio STB, Tata Play, Airtel DTH, Dish TV, etc.)
 * - Streaming Sticks (Amazon Fire TV Stick, Apple TV, Roku, Mi Stick, Google TV Box)
 * - All Smart TVs (Samsung, LG, Sony, Mi, TCL, OnePlus, Vu, RealMe, Panasonic, Philips, etc.)
 * - All Air Conditioners (ACs)
 * - All Smart & BLDC Fans
 * - Bluetooth Audio & Devices (Soundbars, Speakers, BT TVs, Headphones)
 */
class UniversalRemoteDialog(private val context: Context) {

    private val dialog: Dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    private val manager: UniversalRemoteManager = UniversalRemoteManager(context)

    fun show() {
        dialog.setContentView(R.layout.dialog_universal_remote)
        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#080C14")))
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.parseColor("#080C14")
            navigationBarColor = Color.parseColor("#080C14")
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        }

        initViews()
        dialog.show()
    }

    private fun initViews() {
        val btnClose = dialog.findViewById<ImageButton>(R.id.btnCloseRemote)
        val tvStatus = dialog.findViewById<TextView>(R.id.tvIrHardwareStatus)
        val tvFeedback = dialog.findViewById<TextView>(R.id.tvRemoteFeedback)
        val ledIndicator = dialog.findViewById<View>(R.id.remoteLedIndicator)

        val tabCatTv = dialog.findViewById<TextView>(R.id.tabCatTv)
        val tabCatStb = dialog.findViewById<TextView>(R.id.tabCatStb)
        val tabCatFireTv = dialog.findViewById<TextView>(R.id.tabCatFireTv)
        val tabCatAc = dialog.findViewById<TextView>(R.id.tabCatAc)
        val tabCatFan = dialog.findViewById<TextView>(R.id.tabCatFan)
        val tabCatBt = dialog.findViewById<TextView>(R.id.tabCatBt)

        val layoutBrandSelector = dialog.findViewById<View>(R.id.layoutBrandSelector)
        val tvBrandPrefix = dialog.findViewById<TextView>(R.id.tvBrandPrefix)
        val spinnerBrand = dialog.findViewById<Spinner>(R.id.spinnerBrand)

        val layoutTvControls = dialog.findViewById<View>(R.id.layoutTvControls)
        val layoutStbControls = dialog.findViewById<View>(R.id.layoutStbControls)
        val layoutFireTvControls = dialog.findViewById<View>(R.id.layoutFireTvControls)
        val layoutAcControls = dialog.findViewById<View>(R.id.layoutAcControls)
        val layoutFanControls = dialog.findViewById<View>(R.id.layoutFanControls)
        val layoutBluetoothControls = dialog.findViewById<View>(R.id.layoutBluetoothControls)

        val btnModeIr = dialog.findViewById<TextView>(R.id.btnModeIr)
        val btnModeWifi = dialog.findViewById<TextView>(R.id.btnModeWifi)
        val btnModeDual = dialog.findViewById<TextView>(R.id.btnModeDual)

        val layoutWifiDeviceBar = dialog.findViewById<View>(R.id.layoutWifiDeviceBar)
        val tvWifiNetworkStatus = dialog.findViewById<TextView>(R.id.tvWifiNetworkStatus)
        val etTargetDeviceIp = dialog.findViewById<EditText>(R.id.etTargetDeviceIp)
        val btnScanWifiDevices = dialog.findViewById<Button>(R.id.btnScanWifiDevices)

        // Hardware IR check
        val hasIr = manager.hasIrEmitter()
        tvStatus.text = manager.getIrHardwareStatus()
        tvStatus.setTextColor(if (hasIr) Color.parseColor("#00FF88") else Color.parseColor("#FFB800"))

        fun blinkLed(colorHex: String = "#FF3B30") {
            ledIndicator?.let { led ->
                led.setBackgroundColor(Color.parseColor(colorHex))
                led.alpha = 1.0f
                led.postDelayed({ led.alpha = 0.25f }, 180)
            }
        }

        fun selectMode(mode: UniversalRemoteManager.RemoteMode) {
            manager.activeMode = mode
            val selBg = R.drawable.remote_mode_selected
            val unselBg = R.drawable.remote_mode_unselected
            val colActive = Color.parseColor("#00D2FF")
            val colInactive = Color.parseColor("#8EA4C4")

            btnModeIr?.setBackgroundResource(if (mode == UniversalRemoteManager.RemoteMode.IR) selBg else unselBg)
            btnModeIr?.setTextColor(if (mode == UniversalRemoteManager.RemoteMode.IR) colActive else colInactive)

            btnModeWifi?.setBackgroundResource(if (mode == UniversalRemoteManager.RemoteMode.WIFI) selBg else unselBg)
            btnModeWifi?.setTextColor(if (mode == UniversalRemoteManager.RemoteMode.WIFI) colActive else colInactive)

            btnModeDual?.setBackgroundResource(if (mode == UniversalRemoteManager.RemoteMode.DUAL) selBg else unselBg)
            btnModeDual?.setTextColor(if (mode == UniversalRemoteManager.RemoteMode.DUAL) colActive else colInactive)

            layoutWifiDeviceBar?.visibility = if (mode != UniversalRemoteManager.RemoteMode.IR) View.VISIBLE else View.GONE

            val localIp = manager.getLocalWifiIp()
            if (localIp.isNotBlank()) {
                tvWifiNetworkStatus?.text = "📶 WiFi: $localIp (Same LAN)"
            } else {
                tvWifiNetworkStatus?.text = "📶 WiFi: Not connected"
            }

            when (mode) {
                UniversalRemoteManager.RemoteMode.IR -> {
                    tvFeedback.text = "📡 IR Blaster Mode Active (Direct Hardware)"
                }
                UniversalRemoteManager.RemoteMode.WIFI -> {
                    tvFeedback.text = "📶 Same WiFi Mode Active • Controls Smart TVs & Sticks on LAN"
                }
                UniversalRemoteManager.RemoteMode.DUAL -> {
                    tvFeedback.text = "⚡ Dual Mode (IR + WiFi) Active"
                }
            }
        }

        btnModeIr?.setOnClickListener { selectMode(UniversalRemoteManager.RemoteMode.IR) }
        btnModeWifi?.setOnClickListener { selectMode(UniversalRemoteManager.RemoteMode.WIFI) }
        btnModeDual?.setOnClickListener { selectMode(UniversalRemoteManager.RemoteMode.DUAL) }

        if (manager.targetDeviceIp.isNotBlank()) {
            etTargetDeviceIp?.setText(manager.targetDeviceIp)
        }

        etTargetDeviceIp?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val ip = s?.toString()?.trim() ?: ""
                manager.targetDeviceIp = ip
                if (manager.activeCategory == UniversalRemoteManager.Category.FIRE_TV) {
                    dialog.findViewById<EditText>(R.id.etFireTvIp)?.setText(ip)
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        btnScanWifiDevices?.setOnClickListener {
            tvFeedback.text = "🔍 Scanning same WiFi for Smart TVs & Sticks..."
            btnScanWifiDevices.isEnabled = false
            btnScanWifiDevices.text = "..."
            manager.scanLocalSmartDevices(
                onDeviceFound = { foundIp, devName ->
                    etTargetDeviceIp?.setText(foundIp)
                    manager.targetDeviceIp = foundIp
                    tvFeedback.text = "✅ Found $devName at $foundIp!"
                    Toast.makeText(context, "Discovered $devName ($foundIp)", Toast.LENGTH_SHORT).show()
                },
                onComplete = { count ->
                    btnScanWifiDevices.isEnabled = true
                    btnScanWifiDevices.text = "🔍 SCAN"
                    if (count == 0) {
                        Toast.makeText(context, "No open Smart TVs found on subnet. Enter IP manually.", Toast.LENGTH_SHORT).show()
                        tvFeedback.text = "📶 Enter Smart TV / Stick IP manually"
                    }
                }
            )
        }

        // Category Switcher
        fun selectCategory(cat: UniversalRemoteManager.Category) {
            manager.activeCategory = cat
            val selBg = R.drawable.remote_cat_tab_selected
            val unselBg = R.drawable.remote_cat_tab_unselected
            val colActive = Color.parseColor("#00D2FF")
            val colInactive = Color.parseColor("#8EA4C4")

            tabCatTv.setBackgroundResource(if (cat == UniversalRemoteManager.Category.TV) selBg else unselBg)
            tabCatTv.setTextColor(if (cat == UniversalRemoteManager.Category.TV) colActive else colInactive)

            tabCatStb.setBackgroundResource(if (cat == UniversalRemoteManager.Category.STB) selBg else unselBg)
            tabCatStb.setTextColor(if (cat == UniversalRemoteManager.Category.STB) colActive else colInactive)

            tabCatFireTv.setBackgroundResource(if (cat == UniversalRemoteManager.Category.FIRE_TV) selBg else unselBg)
            tabCatFireTv.setTextColor(if (cat == UniversalRemoteManager.Category.FIRE_TV) colActive else colInactive)

            tabCatAc.setBackgroundResource(if (cat == UniversalRemoteManager.Category.AC) selBg else unselBg)
            tabCatAc.setTextColor(if (cat == UniversalRemoteManager.Category.AC) colActive else colInactive)

            tabCatFan.setBackgroundResource(if (cat == UniversalRemoteManager.Category.FAN) selBg else unselBg)
            tabCatFan.setTextColor(if (cat == UniversalRemoteManager.Category.FAN) colActive else colInactive)

            tabCatBt.setBackgroundResource(if (cat == UniversalRemoteManager.Category.BLUETOOTH) selBg else unselBg)
            tabCatBt.setTextColor(if (cat == UniversalRemoteManager.Category.BLUETOOTH) colActive else colInactive)

            layoutTvControls.visibility = if (cat == UniversalRemoteManager.Category.TV) View.VISIBLE else View.GONE
            layoutStbControls.visibility = if (cat == UniversalRemoteManager.Category.STB) View.VISIBLE else View.GONE
            layoutFireTvControls.visibility = if (cat == UniversalRemoteManager.Category.FIRE_TV) View.VISIBLE else View.GONE
            layoutAcControls.visibility = if (cat == UniversalRemoteManager.Category.AC) View.VISIBLE else View.GONE
            layoutFanControls.visibility = if (cat == UniversalRemoteManager.Category.FAN) View.VISIBLE else View.GONE
            layoutBluetoothControls.visibility = if (cat == UniversalRemoteManager.Category.BLUETOOTH) View.VISIBLE else View.GONE

            if (cat == UniversalRemoteManager.Category.BLUETOOTH) {
                layoutBrandSelector.visibility = View.GONE
                tvFeedback.text = "🔵 Bluetooth Audio & Device Remote"
                val btList = manager.getPairedBluetoothDevices()
                val tvBtDevices = dialog.findViewById<TextView>(R.id.tvBtDevicesList)
                if (btList.isNotEmpty()) {
                    tvBtDevices.text = "Paired Devices:\n• " + btList.joinToString("\n• ")
                } else {
                    tvBtDevices.text = "No paired Bluetooth audio devices found. Connect in Android Settings."
                }
            } else {
                layoutBrandSelector.visibility = View.VISIBLE

                when (cat) {
                    UniversalRemoteManager.Category.STB -> {
                        tvBrandPrefix.text = "STB BRAND:"
                        val brands = UniversalRemoteManager.StbBrand.values()
                        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, brands.map { it.displayName })
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        spinnerBrand.adapter = adapter
                        spinnerBrand.setSelection(brands.indexOf(manager.activeStbBrand).coerceAtLeast(0))
                        spinnerBrand.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                                manager.activeStbBrand = brands[pos]
                                tvFeedback.text = "📡 STB Target: ${brands[pos].displayName}"
                            }
                            override fun onNothingSelected(parent: AdapterView<*>?) {}
                        }
                    }
                    UniversalRemoteManager.Category.FIRE_TV -> {
                        tvBrandPrefix.text = "STREAM STICK:"
                        val brands = UniversalRemoteManager.StreamBrand.values()
                        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, brands.map { it.displayName })
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        spinnerBrand.adapter = adapter
                        spinnerBrand.setSelection(brands.indexOf(manager.activeStreamBrand).coerceAtLeast(0))
                        spinnerBrand.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                                manager.activeStreamBrand = brands[pos]
                                dialog.findViewById<TextView>(R.id.tvFireTvBadge)?.text = "🔥 ${brands[pos].displayName.uppercase()}"
                                tvFeedback.text = "🔥 Target: ${brands[pos].displayName}"
                            }
                            override fun onNothingSelected(parent: AdapterView<*>?) {}
                        }
                    }
                    UniversalRemoteManager.Category.TV -> {
                        tvBrandPrefix.text = "TV BRAND:"
                        val brands = UniversalRemoteManager.TvBrand.values()
                        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, brands.map { it.displayName })
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        spinnerBrand.adapter = adapter
                        spinnerBrand.setSelection(brands.indexOf(manager.activeTvBrand).coerceAtLeast(0))
                        spinnerBrand.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                                manager.activeTvBrand = brands[pos]
                                tvFeedback.text = "🎯 TV Target: ${brands[pos].displayName}"
                            }
                            override fun onNothingSelected(parent: AdapterView<*>?) {}
                        }
                    }
                    UniversalRemoteManager.Category.AC -> {
                        tvBrandPrefix.text = "AC BRAND:"
                        val brands = UniversalRemoteManager.AcBrand.values()
                        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, brands.map { it.displayName })
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        spinnerBrand.adapter = adapter
                        spinnerBrand.setSelection(brands.indexOf(manager.activeAcBrand).coerceAtLeast(0))
                        spinnerBrand.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                                manager.activeAcBrand = brands[pos]
                                dialog.findViewById<TextView>(R.id.tvAcBrandBadge)?.text = brands[pos].displayName.uppercase()
                                tvFeedback.text = "❄️ AC Target: ${brands[pos].displayName}"
                            }
                            override fun onNothingSelected(parent: AdapterView<*>?) {}
                        }
                    }
                    UniversalRemoteManager.Category.FAN -> {
                        tvBrandPrefix.text = "FAN BRAND:"
                        val brands = UniversalRemoteManager.FanBrand.values()
                        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, brands.map { it.displayName })
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        spinnerBrand.adapter = adapter
                        spinnerBrand.setSelection(brands.indexOf(manager.activeFanBrand).coerceAtLeast(0))
                        spinnerBrand.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                                manager.activeFanBrand = brands[pos]
                                dialog.findViewById<TextView>(R.id.tvFanBrandBadge)?.text = brands[pos].displayName.uppercase()
                                tvFeedback.text = "🌀 Fan Target: ${brands[pos].displayName}"
                            }
                            override fun onNothingSelected(parent: AdapterView<*>?) {}
                        }
                    }
                    else -> {}
                }
            }
        }

        tabCatTv.setOnClickListener { selectCategory(UniversalRemoteManager.Category.TV) }
        tabCatStb.setOnClickListener { selectCategory(UniversalRemoteManager.Category.STB) }
        tabCatFireTv.setOnClickListener { selectCategory(UniversalRemoteManager.Category.FIRE_TV) }
        tabCatAc.setOnClickListener { selectCategory(UniversalRemoteManager.Category.AC) }
        tabCatFan.setOnClickListener { selectCategory(UniversalRemoteManager.Category.FAN) }
        tabCatBt.setOnClickListener { selectCategory(UniversalRemoteManager.Category.BLUETOOTH) }

        // =====================================================================
        // 1. SET-TOP BOX & DTH SETUP (Sun Direct, Jio STB, Tata Play...)
        // =====================================================================
        fun sendStb(cmdId: String, label: String) {
            blinkLed("#BF5AF2")
            manager.executeStbCommand(cmdId, label) { success, _, desc ->
                tvFeedback.text = desc
                tvFeedback.setTextColor(if (success) Color.parseColor("#00FF88") else Color.parseColor("#FF9500"))
            }
        }

        dialog.findViewById<View>(R.id.btnStbPower)?.setOnClickListener { sendStb("power", "Power") }
        dialog.findViewById<View>(R.id.btnStbMute)?.setOnClickListener { sendStb("mute", "Mute") }
        dialog.findViewById<View>(R.id.btnStbGuide)?.setOnClickListener { sendStb("guide", "Guide / EPG") }
        dialog.findViewById<View>(R.id.btnStbInfo)?.setOnClickListener { sendStb("info", "Info") }

        dialog.findViewById<View>(R.id.btnStbVolUp)?.setOnClickListener { sendStb("volup", "Volume +") }
        dialog.findViewById<View>(R.id.btnStbVolDown)?.setOnClickListener { sendStb("voldown", "Volume -") }
        dialog.findViewById<View>(R.id.btnStbChUp)?.setOnClickListener { sendStb("chup", "Channel +") }
        dialog.findViewById<View>(R.id.btnStbChDown)?.setOnClickListener { sendStb("chdown", "Channel -") }

        dialog.findViewById<View>(R.id.btnStbDpadUp)?.setOnClickListener { sendStb("up", "Up") }
        dialog.findViewById<View>(R.id.btnStbDpadDown)?.setOnClickListener { sendStb("down", "Down") }
        dialog.findViewById<View>(R.id.btnStbDpadLeft)?.setOnClickListener { sendStb("left", "Left") }
        dialog.findViewById<View>(R.id.btnStbDpadRight)?.setOnClickListener { sendStb("right", "Right") }
        dialog.findViewById<View>(R.id.btnStbDpadOk)?.setOnClickListener { sendStb("ok", "OK") }

        dialog.findViewById<View>(R.id.btnStbBack)?.setOnClickListener { sendStb("back", "Back") }
        dialog.findViewById<View>(R.id.btnStbHome)?.setOnClickListener { sendStb("home", "Home / Jio") }
        dialog.findViewById<View>(R.id.btnStbMenu)?.setOnClickListener { sendStb("menu", "Menu") }
        dialog.findViewById<View>(R.id.btnStbExit)?.setOnClickListener { sendStb("exit", "Exit") }

        // Indian STB 4 Color Buttons
        dialog.findViewById<View>(R.id.btnStbRed)?.setOnClickListener { sendStb("red", "Red / Fav") }
        dialog.findViewById<View>(R.id.btnStbGreen)?.setOnClickListener { sendStb("green", "Green / Lang") }
        dialog.findViewById<View>(R.id.btnStbYellow)?.setOnClickListener { sendStb("yellow", "Yellow / Audio") }
        dialog.findViewById<View>(R.id.btnStbBlue)?.setOnClickListener { sendStb("blue", "Blue / Subs") }

        // STB Direct Numpad
        dialog.findViewById<View>(R.id.btnStbNum0)?.setOnClickListener { sendStb("num0", "0") }
        dialog.findViewById<View>(R.id.btnStbNum1)?.setOnClickListener { sendStb("num1", "1") }
        dialog.findViewById<View>(R.id.btnStbNum2)?.setOnClickListener { sendStb("num2", "2") }
        dialog.findViewById<View>(R.id.btnStbNum3)?.setOnClickListener { sendStb("num3", "3") }
        dialog.findViewById<View>(R.id.btnStbNum4)?.setOnClickListener { sendStb("num4", "4") }
        dialog.findViewById<View>(R.id.btnStbNum5)?.setOnClickListener { sendStb("num5", "5") }
        dialog.findViewById<View>(R.id.btnStbNum6)?.setOnClickListener { sendStb("num6", "6") }
        dialog.findViewById<View>(R.id.btnStbNum7)?.setOnClickListener { sendStb("num7", "7") }
        dialog.findViewById<View>(R.id.btnStbNum8)?.setOnClickListener { sendStb("num8", "8") }
        dialog.findViewById<View>(R.id.btnStbNum9)?.setOnClickListener { sendStb("num9", "9") }
        dialog.findViewById<View>(R.id.btnStbRewind)?.setOnClickListener { sendStb("rewind", "Rewind") }
        dialog.findViewById<View>(R.id.btnStbPlayPause)?.setOnClickListener { sendStb("playpause", "Play/Pause") }

        // =====================================================================
        // 2. AMAZON FIRE TV & STREAMING STICK SETUP
        // =====================================================================
        fun sendFire(cmdId: String, label: String) {
            blinkLed("#FF9900")
            manager.executeFireTvCommand(cmdId, label) { success, _, desc ->
                tvFeedback.text = desc
                tvFeedback.setTextColor(if (success) Color.parseColor("#00FF88") else Color.parseColor("#FF9500"))
            }
        }

        dialog.findViewById<View>(R.id.btnFirePower)?.setOnClickListener { sendFire("power", "Power") }
        dialog.findViewById<View>(R.id.btnFireAlexa)?.setOnClickListener { sendFire("alexa", "Alexa Voice") }
        dialog.findViewById<View>(R.id.btnFireMenu)?.setOnClickListener { sendFire("menu", "Menu / Options") }
        dialog.findViewById<View>(R.id.btnFireMute)?.setOnClickListener { sendFire("mute", "Mute") }

        dialog.findViewById<View>(R.id.btnFireDpadUp)?.setOnClickListener { sendFire("up", "Up") }
        dialog.findViewById<View>(R.id.btnFireDpadDown)?.setOnClickListener { sendFire("down", "Down") }
        dialog.findViewById<View>(R.id.btnFireDpadLeft)?.setOnClickListener { sendFire("left", "Left") }
        dialog.findViewById<View>(R.id.btnFireDpadRight)?.setOnClickListener { sendFire("right", "Right") }
        dialog.findViewById<View>(R.id.btnFireDpadSelect)?.setOnClickListener { sendFire("select", "Select") }

        dialog.findViewById<View>(R.id.btnFireBack)?.setOnClickListener { sendFire("back", "Back") }
        dialog.findViewById<View>(R.id.btnFireHome)?.setOnClickListener { sendFire("home", "Home") }
        dialog.findViewById<View>(R.id.btnFireOptions)?.setOnClickListener { sendFire("options", "Options") }

        dialog.findViewById<View>(R.id.btnFireRewind)?.setOnClickListener { sendFire("rewind", "Rewind 10s") }
        dialog.findViewById<View>(R.id.btnFirePlayPause)?.setOnClickListener { sendFire("playpause", "Play/Pause") }
        dialog.findViewById<View>(R.id.btnFireFastForward)?.setOnClickListener { sendFire("fastforward", "FastForward 10s") }
        dialog.findViewById<View>(R.id.btnFireVolDown)?.setOnClickListener { sendFire("voldown", "Volume -") }
        dialog.findViewById<View>(R.id.btnFireVolUp)?.setOnClickListener { sendFire("volup", "Volume +") }

        // Streaming App Shortcuts
        dialog.findViewById<View>(R.id.btnFireAppPrime)?.setOnClickListener { sendFire("prime", "Prime Video") }
        dialog.findViewById<View>(R.id.btnFireAppNetflix)?.setOnClickListener { sendFire("netflix", "Netflix") }
        dialog.findViewById<View>(R.id.btnFireAppYoutube)?.setOnClickListener { sendFire("youtube", "YouTube") }
        dialog.findViewById<View>(R.id.btnFireAppHotstar)?.setOnClickListener { sendFire("hotstar", "JioCinema / Hotstar") }

        // Fire TV WiFi IP
        val etFireTvIp = dialog.findViewById<EditText>(R.id.etFireTvIp)
        dialog.findViewById<Button>(R.id.btnSetFireTvIp)?.setOnClickListener {
            val ip = etFireTvIp?.text?.toString()?.trim() ?: ""
            manager.fireTvIp = ip
            Toast.makeText(context, if (ip.isNotEmpty()) "Fire TV IP set: $ip" else "Cleared Fire TV IP", Toast.LENGTH_SHORT).show()
        }

        // =====================================================================
        // 3. TV CONTROLS SETUP
        // =====================================================================
        fun sendTv(cmdId: String, label: String) {
            blinkLed("#00D2FF")
            manager.executeTvCommand(cmdId, label) { success, _, desc ->
                tvFeedback.text = desc
                tvFeedback.setTextColor(if (success) Color.parseColor("#00FF88") else Color.parseColor("#FF9500"))
            }
        }

        dialog.findViewById<View>(R.id.btnRemotePower)?.setOnClickListener { sendTv("power", "Power") }
        dialog.findViewById<View>(R.id.btnRemoteMute)?.setOnClickListener { sendTv("mute", "Mute") }
        dialog.findViewById<View>(R.id.btnRemoteSource)?.setOnClickListener { sendTv("input", "Source") }
        dialog.findViewById<View>(R.id.btnRemoteMenu)?.setOnClickListener { sendTv("menu", "Menu") }

        dialog.findViewById<View>(R.id.btnVolUp)?.setOnClickListener { sendTv("volup", "Volume +") }
        dialog.findViewById<View>(R.id.btnVolDown)?.setOnClickListener { sendTv("voldown", "Volume -") }
        dialog.findViewById<View>(R.id.btnChUp)?.setOnClickListener { sendTv("chup", "Channel +") }
        dialog.findViewById<View>(R.id.btnChDown)?.setOnClickListener { sendTv("chdown", "Channel -") }

        dialog.findViewById<View>(R.id.btnDpadUp)?.setOnClickListener { sendTv("up", "Up") }
        dialog.findViewById<View>(R.id.btnDpadDown)?.setOnClickListener { sendTv("down", "Down") }
        dialog.findViewById<View>(R.id.btnDpadLeft)?.setOnClickListener { sendTv("left", "Left") }
        dialog.findViewById<View>(R.id.btnDpadRight)?.setOnClickListener { sendTv("right", "Right") }
        dialog.findViewById<View>(R.id.btnDpadOk)?.setOnClickListener { sendTv("ok", "OK") }

        dialog.findViewById<View>(R.id.btnRemoteBack)?.setOnClickListener { sendTv("back", "Back") }
        dialog.findViewById<View>(R.id.btnRemoteHome)?.setOnClickListener { sendTv("home", "Home") }
        dialog.findViewById<View>(R.id.btnRemoteFullscreen)?.setOnClickListener { sendTv("fullscreen", "Fullscreen") }
        dialog.findViewById<View>(R.id.btnRemoteExit)?.setOnClickListener { sendTv("exit", "Exit") }

        dialog.findViewById<View>(R.id.btnRemotePrev)?.setOnClickListener { sendTv("prev", "Previous") }
        dialog.findViewById<View>(R.id.btnRemoteRewind)?.setOnClickListener { sendTv("rewind", "Rewind") }
        dialog.findViewById<View>(R.id.btnRemotePlayPause)?.setOnClickListener { sendTv("playpause", "Play/Pause") }
        dialog.findViewById<View>(R.id.btnRemoteFastForward)?.setOnClickListener { sendTv("fastforward", "Fast-Forward") }
        dialog.findViewById<View>(R.id.btnRemoteNext)?.setOnClickListener { sendTv("next", "Next") }

        val layoutNumpad = dialog.findViewById<View>(R.id.layoutNumpad)
        val btnToggleNumpad = dialog.findViewById<Button>(R.id.btnToggleNumpad)
        btnToggleNumpad?.setOnClickListener {
            if (layoutNumpad.visibility == View.VISIBLE) {
                layoutNumpad.visibility = View.GONE
                btnToggleNumpad.text = "🔢 SHOW NUMBER PAD & QUICK SHORTCUTS"
            } else {
                layoutNumpad.visibility = View.VISIBLE
                btnToggleNumpad.text = "🔼 HIDE NUMBER PAD"
            }
        }

        dialog.findViewById<View>(R.id.btnNum0)?.setOnClickListener { sendTv("num0", "0") }
        dialog.findViewById<View>(R.id.btnNum1)?.setOnClickListener { sendTv("num1", "1") }
        dialog.findViewById<View>(R.id.btnNum2)?.setOnClickListener { sendTv("num2", "2") }
        dialog.findViewById<View>(R.id.btnNum3)?.setOnClickListener { sendTv("num3", "3") }
        dialog.findViewById<View>(R.id.btnNum4)?.setOnClickListener { sendTv("num4", "4") }
        dialog.findViewById<View>(R.id.btnNum5)?.setOnClickListener { sendTv("num5", "5") }
        dialog.findViewById<View>(R.id.btnNum6)?.setOnClickListener { sendTv("num6", "6") }
        dialog.findViewById<View>(R.id.btnNum7)?.setOnClickListener { sendTv("num7", "7") }
        dialog.findViewById<View>(R.id.btnNum8)?.setOnClickListener { sendTv("num8", "8") }
        dialog.findViewById<View>(R.id.btnNum9)?.setOnClickListener { sendTv("num9", "9") }
        dialog.findViewById<View>(R.id.btnKeySpace)?.setOnClickListener { sendTv("space", "Space") }
        dialog.findViewById<View>(R.id.btnKeyTaskMgr)?.setOnClickListener { sendTv("menu", "Menu") }

        // =====================================================================
        // 4. AC CONTROLS SETUP
        // =====================================================================
        fun refreshAcDisplay() {
            val tvAcTemp = dialog.findViewById<TextView>(R.id.tvAcTemp)
            val tvAcPower = dialog.findViewById<TextView>(R.id.tvAcPowerStatus)
            val tvAcMode = dialog.findViewById<TextView>(R.id.tvAcModeBadge)
            val tvAcFan = dialog.findViewById<TextView>(R.id.tvAcFanBadge)
            val tvAcSwing = dialog.findViewById<TextView>(R.id.tvAcSwingBadge)

            tvAcTemp?.text = manager.acState.temp.toString()
            if (manager.acState.isPowerOn) {
                tvAcPower?.text = "🟢 POWER ON"
                tvAcPower?.setTextColor(Color.parseColor("#00FF88"))
            } else {
                tvAcPower?.text = "🔴 POWER OFF"
                tvAcPower?.setTextColor(Color.parseColor("#FF453A"))
            }

            tvAcMode?.text = when (manager.acState.mode) {
                "COOL" -> "❄️ COOL"
                "HEAT" -> "☀️ HEAT"
                "FAN" -> "💨 FAN"
                "DRY" -> "💧 DRY"
                else -> "⚡ AUTO"
            }

            tvAcFan?.text = "💨 FAN: ${manager.acState.fanSpeed}"
            tvAcSwing?.text = if (manager.acState.swing) "↕️ SWING ON" else "⏸️ SWING OFF"
        }

        fun sendAc(action: String) {
            blinkLed("#00D2FF")
            manager.executeAcCommand(action) { success, _, desc ->
                refreshAcDisplay()
                tvFeedback.text = desc
                tvFeedback.setTextColor(if (success) Color.parseColor("#00FF88") else Color.parseColor("#FF9500"))
            }
        }

        dialog.findViewById<View>(R.id.btnAcPower)?.setOnClickListener { sendAc("power") }
        dialog.findViewById<View>(R.id.btnAcTempUp)?.setOnClickListener { sendAc("temp_up") }
        dialog.findViewById<View>(R.id.btnAcTempDown)?.setOnClickListener { sendAc("temp_down") }
        dialog.findViewById<View>(R.id.btnAcMode)?.setOnClickListener { sendAc("mode") }
        dialog.findViewById<View>(R.id.btnAcFanSpeed)?.setOnClickListener { sendAc("fan_speed") }
        dialog.findViewById<View>(R.id.btnAcSwing)?.setOnClickListener { sendAc("swing") }
        dialog.findViewById<View>(R.id.btnAcTurbo)?.setOnClickListener { sendAc("turbo") }
        dialog.findViewById<View>(R.id.btnAcEco)?.setOnClickListener { sendAc("eco") }

        // =====================================================================
        // 5. FAN CONTROLS SETUP
        // =====================================================================
        fun refreshFanDisplay() {
            val tvSpeed = dialog.findViewById<TextView>(R.id.tvFanSpeedNumber)
            val tvFanStatus = dialog.findViewById<TextView>(R.id.tvFanPowerStatus)
            val tvBreeze = dialog.findViewById<TextView>(R.id.tvFanBreezeBadge)
            val tvTimer = dialog.findViewById<TextView>(R.id.tvFanTimerBadge)
            val tvLight = dialog.findViewById<TextView>(R.id.tvFanLedBadge)

            if (manager.fanState.isPowerOn) {
                tvSpeed?.text = if (manager.fanState.speed == 6) "⚡" else manager.fanState.speed.toString()
                tvFanStatus?.text = "🟢 RUNNING"
                tvFanStatus?.setTextColor(Color.parseColor("#00FF88"))
                tvBreeze?.text = if (manager.fanState.breeze) "🍃 BREEZE ON" else "🍃 NORMAL"
                tvTimer?.text = if (manager.fanState.timerHours > 0) "⏱ TIMER: ${manager.fanState.timerHours}H" else "⏱ TIMER: OFF"
                tvLight?.text = if (manager.fanState.ledLight) "💡 LIGHT ON" else "💡 LIGHT OFF"
            } else {
                tvSpeed?.text = "OFF"
                tvFanStatus?.text = "🔴 POWER OFF"
                tvFanStatus?.setTextColor(Color.parseColor("#FF453A"))
            }
        }

        fun sendFan(action: String) {
            blinkLed("#BF5AF2")
            manager.executeFanCommand(action) { success, _, desc ->
                refreshFanDisplay()
                tvFeedback.text = desc
                tvFeedback.setTextColor(if (success) Color.parseColor("#00FF88") else Color.parseColor("#FF9500"))
            }
        }

        dialog.findViewById<View>(R.id.btnFanPower)?.setOnClickListener { sendFan("power") }
        dialog.findViewById<View>(R.id.btnFanBreeze)?.setOnClickListener { sendFan("breeze") }
        dialog.findViewById<View>(R.id.btnFanLight)?.setOnClickListener { sendFan("light") }
        dialog.findViewById<View>(R.id.btnFanTimer)?.setOnClickListener { sendFan("timer") }

        dialog.findViewById<View>(R.id.btnFanSp1)?.setOnClickListener { sendFan("speed_1") }
        dialog.findViewById<View>(R.id.btnFanSp2)?.setOnClickListener { sendFan("speed_2") }
        dialog.findViewById<View>(R.id.btnFanSp3)?.setOnClickListener { sendFan("speed_3") }
        dialog.findViewById<View>(R.id.btnFanSp4)?.setOnClickListener { sendFan("speed_4") }
        dialog.findViewById<View>(R.id.btnFanSp5)?.setOnClickListener { sendFan("speed_5") }
        dialog.findViewById<View>(R.id.btnFanBoost)?.setOnClickListener { sendFan("speed_boost") }

        // =====================================================================
        // 6. BLUETOOTH CONTROLS SETUP
        // =====================================================================
        fun sendBt(action: String) {
            blinkLed("#00D2FF")
            manager.executeBluetoothCommand(action) { success, _, desc ->
                tvFeedback.text = desc
                tvFeedback.setTextColor(if (success) Color.parseColor("#00FF88") else Color.parseColor("#FF9500"))
            }
        }

        dialog.findViewById<View>(R.id.btnBtPlayPause)?.setOnClickListener { sendBt("playpause") }
        dialog.findViewById<View>(R.id.btnBtNext)?.setOnClickListener { sendBt("next") }
        dialog.findViewById<View>(R.id.btnBtPrev)?.setOnClickListener { sendBt("prev") }
        dialog.findViewById<View>(R.id.btnBtVolUp)?.setOnClickListener { sendBt("volup") }
        dialog.findViewById<View>(R.id.btnBtVolDown)?.setOnClickListener { sendBt("voldown") }
        dialog.findViewById<View>(R.id.btnBtMute)?.setOnClickListener { sendBt("mute") }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        // Initialize with default mode and category (STB & DTH with Sun Direct DTH)
        selectMode(manager.activeMode)
        selectCategory(UniversalRemoteManager.Category.STB)
        refreshAcDisplay()
        refreshFanDisplay()
    }
}
