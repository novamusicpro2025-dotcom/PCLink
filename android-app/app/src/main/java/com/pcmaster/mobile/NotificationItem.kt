package com.pcmaster.mobile

data class NotificationItem(
    val id: String = "",
    val appName: String = "",
    val title: String = "",
    val content: String = "",
    val packageName: String = "",
    val canReply: Boolean = false
)