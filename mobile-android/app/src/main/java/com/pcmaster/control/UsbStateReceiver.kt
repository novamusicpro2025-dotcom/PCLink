package com.pcmaster.control

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import android.util.Log

class UsbStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == "android.hardware.usb.action.USB_STATE" || action == Intent.ACTION_POWER_CONNECTED) {
            val connected = if (action == Intent.ACTION_POWER_CONNECTED) true 
                            else intent.extras?.getBoolean("connected") ?: false
            
            if (connected) {
                Log.d("UsbStateReceiver", "USB/Power Connected! Launching app...")
                
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("auto_connect", true)
                }
                
                try {
                    context.startActivity(launchIntent)
                } catch (e: Exception) {
                    Log.e("UsbStateReceiver", "Background launch failed: ${e.message}")
                }

                showConnectNotification(context)
            }
        }
    }

    private fun showConnectNotification(context: Context) {
        val channelId = "usb_connect_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "USB Connection", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("auto_connect", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth) // Use a proper icon later
            .setContentTitle("PC Connected")
            .setContentText("Tap to open PC Master Control")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(1001, notification)
    }
}
