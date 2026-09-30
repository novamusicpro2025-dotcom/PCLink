package com.pcmaster.mobile.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.pcmaster.mobile.ProcessItem
import com.pcmaster.mobile.FileItem
import com.pcmaster.mobile.NotificationItem
import com.pcmaster.mobile.repository.PcRepository
import com.pcmaster.mobile.api.ApiClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asLiveData

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = PcRepository()
    
    // Connection state
    val isConnected = MutableStateFlow(false)
    val connectionStatus = MutableStateFlow("Disconnected")
    val latency = MutableStateFlow(0L)
    val currentIp = MutableStateFlow("")
    val authToken = MutableStateFlow(ApiClient.getAuthToken())
    
    // Telemetry
    val cpuLoad = MutableStateFlow(0f)
    val cpuTemp = MutableStateFlow(0f)
    val gpuLoad = MutableStateFlow(0f)
    val gpuTemp = MutableStateFlow(0f)
    val ramUsage = MutableStateFlow(0f)
    val ramUsed = MutableStateFlow(0f)
    val ramTotal = MutableStateFlow(0f)
    val netUp = MutableStateFlow(0f)
    val netDown = MutableStateFlow(0f)
    val fanSpeed = MutableStateFlow(0f)
    val battery = MutableStateFlow("Unknown")
    val uptime = MutableStateFlow("Unknown")
    val hostname = MutableStateFlow("")
    val osName = MutableStateFlow("")
    
    // Notifications
    val notifications = MutableLiveData<List<NotificationItem>>(emptyList())
    val unreadCount = MutableLiveData(0)
    
    // Processes
    val processes = MutableLiveData<List<ProcessItem>>(emptyList())
    
    // Files
    val files = MutableLiveData<List<FileItem>>(emptyList())
    val currentPath = MutableLiveData<String>("")
    
    // Mirroring
    val isMirroring = MutableStateFlow(false)
    
    // Polling
    private var pollingJob: kotlinx.coroutines.Job? = null
    
    init {
        loadSavedSettings()
    }
    
    private fun loadSavedSettings() {
        val prefs = getApplication<Application>().getSharedPreferences("pc_master", Context.MODE_PRIVATE)
        val savedIp = prefs.getString("pc_ip", "127.0.0.1") ?: "127.0.0.1"
        val savedPort = prefs.getString("pc_port", "8099") ?: "8099"
        val savedToken = prefs.getString("pc_token", "") ?: ""
        
        currentIp.value = "$savedIp:$savedPort"
        ApiClient.initialize(getApplication<Application>(), "$savedIp:$savedPort", savedToken)
    }
    
    fun connect(ip: String, port: String, token: String) {
        val cleanIp = ip.trim()
        val cleanPort = port.trim().ifEmpty { "8099" }
        val baseUrl = "http://$cleanIp:$cleanPort"
        
        connectionStatus.value = "Connecting..."
        ApiClient.initialize(getApplication<Application>(), baseUrl, token)
        
        viewModelScope.launch {
            // First, try to get settings (auto-negotiate token)
            val settingsResult = repository.getSettings()
            settingsResult.onSuccess { settings ->
                val newToken = settings.authToken
                if (newToken.isNotEmpty()) {
                    authToken.value = newToken
                    ApiClient.updateAuthToken(newToken)
                    saveSettings(cleanIp, cleanPort, newToken)
                }
                
                // Then ping to verify
                val pingResult = repository.ping()
                pingResult.onSuccess { success ->
                    if (success) {
                        isConnected.value = true
                        connectionStatus.value = "Connected"
                        startPolling()
                    } else {
                        isConnected.value = false
                        connectionStatus.value = "Connection failed"
                    }
                }.onFailure { e ->
                    isConnected.value = false
                    connectionStatus.value = "Error: ${e.message}"
                }
            }.onFailure { e ->
                isConnected.value = false
                connectionStatus.value = "Error: ${e.message}"
            }
        }
    }
    
    private fun saveSettings(ip: String, port: String, token: String) {
        val prefs = getApplication<Application>().getSharedPreferences("pc_master", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("pc_ip", ip)
            .putString("pc_port", port)
            .putString("pc_token", token)
            .apply()
        currentIp.value = "$ip:$port"
    }
    
    fun disconnect() {
        stopPolling()
        isConnected.value = false
        connectionStatus.value = "Disconnected"
    }
    
    private fun startPolling() {
        stopPolling()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                fetchTelemetry()
                fetchNotifications()
                kotlinx.coroutines.delay(2000)
            }
        }
    }
    
    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }
    
    private fun fetchTelemetry() {
        viewModelScope.launch {
            val result = repository.getStats()
            result.onSuccess { stats ->
                cpuLoad.value = stats.cpuLoad
                cpuTemp.value = stats.cpuTemp
                gpuLoad.value = stats.gpuLoad
                gpuTemp.value = stats.gpuTemp
                ramUsage.value = (stats.ramUsed / stats.ramTotal * 100).coerceAtMost(100f)
                ramUsed.value = stats.ramUsed
                ramTotal.value = stats.ramTotal
                netUp.value = stats.netUp
                netDown.value = stats.netDown
                fanSpeed.value = stats.fanSpeed
            }
        }
    }
    
    private fun fetchNotifications() {
        viewModelScope.launch {
            val result = repository.getNotifications()
            result.onSuccess { notifs ->
                notifications.postValue(notifs)
                unreadCount.postValue(notifs.size)
            }
        }
    }
    
    fun fetchProcesses() {
        viewModelScope.launch {
            val result = repository.getProcesses()
            result.onSuccess { procs ->
                processes.postValue(procs)
            }
        }
    }
    
    fun loadFiles(path: String = "") {
        currentPath.value = path
        viewModelScope.launch {
            val result = repository.getFileList(path.ifEmpty { null })
            result.onSuccess { files ->
                this@MainViewModel.files.postValue(files)
            }
        }
    }
    
    fun sendRemoteCommand(command: String, params: Map<String, String> = emptyMap()) {
        viewModelScope.launch {
            repository.sendRemoteCommand(command, params)
        }
    }
    
    fun killProcess(pid: Int) {
        viewModelScope.launch {
            val result = repository.killProcess(pid)
            result.onSuccess { 
                fetchProcesses()
            }
        }
    }
    
    fun uploadFile(fileName: String, inputStream: java.io.InputStream) {
        viewModelScope.launch {
            val result = repository.uploadFile(fileName, inputStream)
            result.onSuccess {
                loadFiles(currentPath.value ?: "")
            }
        }
    }
    
    fun downloadFile(fileName: String) {
        viewModelScope.launch {
            val result = repository.downloadFile(fileName)
            result.onSuccess { responseBody ->
                // Handle download - would need DownloadManager
            }
        }
    }
    
    fun startMirroring() {
        viewModelScope.launch {
            val result = repository.startMirroring()
            result.onSuccess { success ->
                if (success) isMirroring.value = true
            }
        }
    }
    
    fun stopMirroring() {
        viewModelScope.launch {
            val result = repository.stopMirroring()
            result.onSuccess { success ->
                if (success) isMirroring.value = false
            }
        }
    }
    
    fun discoverHub() {
        viewModelScope.launch {
            val result = repository.discoverPcHub()
            result.onSuccess { (ip, port) ->
                // Auto-fill and connect
            }
        }
    }
    
    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }
}