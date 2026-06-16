package com.example.notimanager
import androidx.compose.ui.graphics.ImageBitmap

data class AppSetting(
    val name: String,
    val packageName: String,
    val iconBitmap: ImageBitmap?,
    var isGroupingEnabled: Boolean = true // Default state for your grouping settings
)