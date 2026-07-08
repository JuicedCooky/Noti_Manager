package com.juicedcooky.notimanager.group

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.juicedcooky.notimanager.AppSetting

class GroupState(val id: String, name: String) {
    var name by mutableStateOf(name)
    var description by mutableStateOf("")
    val apps = mutableStateListOf<AppSetting>()
    val positions = mutableStateMapOf<String, Offset>()
    val velocities = HashMap<String, Offset>()
    // Each slot is the offset from group.center to the top-left corner of that bubble slot.
    val slots = ArrayList<Offset>()
    val slotOf = HashMap<String, Int>()
    val ownerOf = HashMap<Int, String>()
    var center by mutableStateOf(Offset.Zero)
    var draggedPackage by mutableStateOf<String?>(null)
    var isGroupDragging by mutableStateOf(false)
    var groupRadius = 0f
    var visualGroupRadius = 0f
    var dotColor by mutableStateOf<Color?>(null)
    var dotScale by mutableStateOf(1f)
    var icon by mutableStateOf("")
    var groupingEnabled by mutableStateOf(true)
    var headsUpEnabled by mutableStateOf(false)
    var notificationsEnabled by mutableStateOf(true)
    var showEditDialog by mutableStateOf(false)
}
