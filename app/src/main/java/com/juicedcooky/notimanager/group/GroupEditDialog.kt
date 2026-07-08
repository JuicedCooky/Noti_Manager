package com.juicedcooky.notimanager.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun GroupEditDialog(
    group: GroupState,
    allGroups: List<GroupState>,
    resolvedDotColor: Color,
    onDeleteGroup: () -> Unit
) {
    if (!group.showEditDialog) return

    var editName by remember { mutableStateOf(group.name) }
    var editDesc by remember { mutableStateOf(group.description) }
    var editNotificationsEnabled by remember { mutableStateOf(group.notificationsEnabled) }
    var editGroupingEnabled by remember { mutableStateOf(group.groupingEnabled) }
    var editHeadsUpEnabled by remember { mutableStateOf(group.headsUpEnabled) }
    var editIcon by remember { mutableStateOf(group.icon) }
    var editDotScale by remember { mutableStateOf(group.dotScale) }
    var editDotColor by remember { mutableStateOf(group.dotColor) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }

    if (showColorDialog) {
        AlertDialog(
            onDismissRequest = { showColorDialog = false },
            title = { Text("Choose Color") },
            text = {
                HsvColorPicker(
                    initialColor = editDotColor ?: resolvedDotColor,
                    onColorChanged = { editDotColor = it }
                )
            },
            confirmButton = {
                TextButton(onClick = { showColorDialog = false }) { Text("Done") }
            }
        )
    } else if (showIconPicker) {
        var iconSearch by remember { mutableStateOf("") }
        val filteredIcons = DRAWABLE_ICONS.filter { (name, _) ->
            iconSearch.isBlank() || name.contains(iconSearch, ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { showIconPicker = false },
            title = { Text("Choose Icon") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = iconSearch,
                        onValueChange = { iconSearch = it },
                        label = { Text("Search") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(filteredIcons) { (name, resId) ->
                            val selected = editIcon == name
                            val accent = editDotColor ?: resolvedDotColor
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (selected) accent.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable { editIcon = name; showIconPicker = false }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = resId),
                                    contentDescription = name,
                                    modifier = Modifier.size(28.dp),
                                    tint = if (selected) accent else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    name.split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (selected) accent else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIconPicker = false }) { Text("Cancel") }
            }
        )
    } else if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete \"${group.name}\"?") },
            text = { Text("All apps will be moved to the default group.") },
            confirmButton = {
                TextButton(onClick = {
                    val dest = allGroups.firstOrNull { it.id == "default" }
                        ?: allGroups.firstOrNull { it.id != group.id }
                    dest?.let { def ->
                        group.apps.forEach { app ->
                            def.apps.add(app)
                            def.positions[app.packageName] = def.center
                            def.velocities[app.packageName] = Offset.Zero
                        }
                    }
                    showDeleteConfirm = false
                    group.showEditDialog = false
                    onDeleteGroup()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = { group.showEditDialog = false },
            title = { Text("Edit Group") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Title") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        label = { Text("Description") },
                        minLines = 2
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text("Notifications", style = MaterialTheme.typography.labelLarge)
                            Text(
                                if (editNotificationsEnabled) "Notifications from this group are shown"
                                else "All notifications from this group are blocked",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = editNotificationsEnabled,
                            onCheckedChange = { editNotificationsEnabled = it }
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text("Group notifications", style = MaterialTheme.typography.labelLarge)
                            Text(
                                if (editGroupingEnabled) "Show as a single grouped notification"
                                else "Show each notification individually",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = editGroupingEnabled,
                            onCheckedChange = { editGroupingEnabled = it }
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text("Heads-up notifications", style = MaterialTheme.typography.labelLarge)
                            Text(
                                if (editHeadsUpEnabled) "Peek banner for each notification"
                                else "Deliver silently to shade",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = editHeadsUpEnabled,
                            onCheckedChange = { editHeadsUpEnabled = it }
                        )
                    }
                    // Icon section
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Icon", style = MaterialTheme.typography.labelLarge)
                        if (editIcon.isNotBlank()) {
                            TextButton(onClick = { editIcon = "" }) { Text("Clear") }
                        }
                    }
                    TextButton(
                        onClick = { showIconPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val currentIconRes = DRAWABLE_ICONS.firstOrNull { it.first == editIcon }?.second
                        if (currentIconRes != null) {
                            Icon(
                                painter = painterResource(id = currentIconRes),
                                contentDescription = editIcon,
                                modifier = Modifier.size(22.dp),
                                tint = editDotColor ?: resolvedDotColor
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(editIcon.split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } })
                        } else {
                            Text("Select icon")
                        }
                    }
                    // Dot size section
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Dot size", style = MaterialTheme.typography.labelLarge)
                        Text("${"%.1f".format(editDotScale)}×", style = MaterialTheme.typography.labelMedium)
                    }
                    Slider(
                        value = editDotScale,
                        onValueChange = { editDotScale = it },
                        valueRange = 0.5f..3.0f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Color section
                    TextButton(
                        onClick = { showColorDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(editDotColor ?: resolvedDotColor)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Color")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editName.isNotBlank()) group.name = editName.trim()
                    group.description = editDesc.trim()
                    group.icon = editIcon.trim()
                    group.dotScale = editDotScale
                    group.dotColor = editDotColor
                    group.notificationsEnabled = editNotificationsEnabled
                    group.groupingEnabled = editGroupingEnabled
                    group.headsUpEnabled = editHeadsUpEnabled
                    group.showEditDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (group.id != "default") {
                        TextButton(
                            onClick = { showDeleteConfirm = true }
                        ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                    TextButton(onClick = { group.showEditDialog = false }) { Text("Cancel") }
                }
            }
        )
    }
}
