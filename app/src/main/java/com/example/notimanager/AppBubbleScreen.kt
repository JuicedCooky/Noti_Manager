package com.example.notimanager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

private const val SPRING = 40f
private const val DAMPING = 0.80f
private const val DT = 0.016f
// Extra gap between the center dot and the first ring of app bubbles, in bubbleRadius units.
private const val CENTER_GAP = 1.0f

private val DRAWABLE_ICONS: List<Pair<String, Int>> = listOf(
    "audio" to R.drawable.audio,
    "mail" to R.drawable.mail,
    "ic_launcher_foreground" to R.drawable.ic_launcher_foreground,
)

private val DOT_COLORS = listOf(
    Color(0xFF1976D2), // Blue
    Color(0xFF388E3C), // Green
    Color(0xFFF57C00), // Orange
    Color(0xFFE91E63), // Pink
    Color(0xFF7B1FA2), // Purple
    Color(0xFFD32F2F), // Red
    Color(0xFFFBC02D), // Yellow
    Color(0xFF455A64), // Blue Grey
)

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
    var dotColor by mutableStateOf<Color?>(null)
    var dotScale by mutableStateOf(1f)
    var icon by mutableStateOf("")
    var groupingEnabled by mutableStateOf(true)
    var headsUpEnabled by mutableStateOf(false)
    var showEditDialog by mutableStateOf(false)
}

@Composable
fun AppBubbleScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val groups = remember { mutableStateListOf<GroupState>() }
    var showDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var zoom by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var managementEnabled by remember { mutableStateOf(isNotificationManagementEnabled(context)) }
    var globalHeadsUpEnabled by remember { mutableStateOf(isGlobalHeadsUpEnabled(context)) }
    var ignoreMediaAndOngoing by remember { mutableStateOf(isIgnoreMediaAndOngoing(context)) }
    var appBubbleScale by remember { mutableStateOf(getAppBubbleScale(context)) }
    var appSpacingScale by remember { mutableStateOf(getAppSpacingScale(context)) }

    // Save state whenever the app is paused (user switches away or locks screen).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) saveGroups(context, groups)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (groups.isEmpty()) {
            val allApps = getInstalledAppsWithUi(context)
            val byPackage = allApps.associateBy { it.packageName }
            val saved = loadSavedGroups(context)
            if (saved != null) {
                val assigned = mutableSetOf<String>()
                for (s in saved) {
                    val g = GroupState(s.id, s.name)
                    g.dotColor = s.dotColor
                    g.dotScale = s.dotScale
                    g.icon = s.icon
                    g.description = s.description
                    g.groupingEnabled = s.groupingEnabled
                    g.headsUpEnabled = s.headsUpEnabled
                    if (s.center != Offset.Zero) g.center = s.center
                    for (pkg in s.packageNames) {
                        byPackage[pkg]?.let { g.apps.add(it); assigned.add(pkg) }
                    }
                    groups.add(g)
                }
                // Any apps installed since the last save land in the default group.
                val default = groups.firstOrNull { it.id == "default" }
                    ?: GroupState("default", "Default").also { groups.add(0, it) }
                allApps.filter { it.packageName !in assigned }.forEach { default.apps.add(it) }
            } else {
                val g = GroupState("default", "Default")
                g.apps.addAll(allApps)
                groups.add(g)
            }
        }
    }

    if (groups.isEmpty()) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        if (widthPx == 0f || heightPx == 0f) return@BoxWithConstraints

        // Assign screen center to any group that hasn't been placed yet.
        LaunchedEffect(groups.size, widthPx, heightPx) {
            groups.forEach { g ->
                if (g.center == Offset.Zero)
                    g.center = Offset(widthPx / 2f, heightPx / 2f)
            }
        }

        // Separation: push overlapping groups apart each frame.
        // Three passes per frame resolve chains of groups in one shot.
        // Clamping uses the dot radius so large groups can extend off-screen
        // (pan/zoom handles navigation) instead of fighting the separation.
        LaunchedEffect(Unit) {
            while (true) {
                withFrameNanos {
                    repeat(3) {
                        for (i in groups.indices) {
                            for (j in i + 1 until groups.size) {
                                val gi = groups[i]; val gj = groups[j]
                                val delta = gj.center - gi.center
                                val dist = delta.getDistance()
                                val minDist = gi.groupRadius + gj.groupRadius + 40f
                                if (dist < minDist) {
                                    val norm = if (dist > 0.1f) delta / dist else Offset(1f, 0f)
                                    val overlap = minDist - dist
                                    val iFixed = gi.isGroupDragging
                                    val jFixed = gj.isGroupDragging
                                    if (!iFixed) {
                                        val push = norm * (if (jFixed) overlap else overlap * 0.5f)
                                        gi.center -= push
                                        gi.apps.forEach { app ->
                                            gi.positions[app.packageName]?.let {
                                                gi.positions[app.packageName] = it - push
                                            }
                                        }
                                    }
                                    if (!jFixed) {
                                        val push = norm * (if (iFixed) overlap else overlap * 0.5f)
                                        gj.center += push
                                        gj.apps.forEach { app ->
                                            gj.positions[app.packageName]?.let {
                                                gj.positions[app.packageName] = it + push
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Clamp center dot to screen; group bubbles may extend off-edge.
                    val dotR = min(widthPx, heightPx) / 14f
                    groups.forEach { g ->
                        if (!g.isGroupDragging) {
                            val cx = g.center.x.coerceIn(dotR, (widthPx - dotR).coerceAtLeast(dotR))
                            val cy = g.center.y.coerceIn(dotR, (heightPx - dotR).coerceAtLeast(dotR))
                            if (cx != g.center.x || cy != g.center.y) {
                                val delta = Offset(cx - g.center.x, cy - g.center.y)
                                g.center = Offset(cx, cy)
                                g.apps.forEach { app ->
                                    g.positions[app.packageName]?.let {
                                        g.positions[app.packageName] = it + delta
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            zoom = (zoom * zoomChange).coerceIn(0.3f, 3f)
            panOffset += panChange
        }

        // All groups rendered inside the pinch-to-zoom layer.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .transformable(state = transformState)
                .graphicsLayer(
                    scaleX = zoom,
                    scaleY = zoom,
                    translationX = panOffset.x,
                    translationY = panOffset.y
                )
        ) {
            groups.forEach { group ->
                if (group.center != Offset.Zero) {
                    BubbleGroupCluster(
                        group = group,
                        allGroups = groups,
                        widthPx = widthPx,
                        heightPx = heightPx,
                        appBubbleScale = appBubbleScale,
                        appSpacingScale = appSpacingScale,
                        onDeleteGroup = { groups.remove(group) }
                    )
                }
            }
        }

        // Management toggle — top-left, outside zoom layer.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Switch(
                checked = managementEnabled,
                onCheckedChange = { enabled ->
                    managementEnabled = enabled
                    setNotificationManagementEnabled(context, enabled)
                    if (!enabled) NotificationManagerCompat.from(context).cancelAll()
                }
            )
            Text("Notifications", style = MaterialTheme.typography.labelMedium)
        }

        FloatingActionButton(
            onClick = { showSettingsDialog = true },
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
        ) {
            Text("⚙", style = MaterialTheme.typography.headlineMedium)
        }

        FloatingActionButton(
            onClick = { showDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Text("+", style = MaterialTheme.typography.headlineMedium)
        }

        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("Settings") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text("Heads-up notifications", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    if (globalHeadsUpEnabled) "Peek banner enabled for all groups"
                                    else "All notifications delivered silently",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = globalHeadsUpEnabled,
                                onCheckedChange = { enabled ->
                                    globalHeadsUpEnabled = enabled
                                    setGlobalHeadsUpEnabled(context, enabled)
                                }
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text("Skip media & ongoing", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    if (ignoreMediaAndOngoing) "Media players and active notifications ignored"
                                    else "All notifications managed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = ignoreMediaAndOngoing,
                                onCheckedChange = { enabled ->
                                    ignoreMediaAndOngoing = enabled
                                    setIgnoreMediaAndOngoing(context, enabled)
                                }
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("App size", style = MaterialTheme.typography.labelLarge)
                            Text("${"%.1f".format(appBubbleScale)}×", style = MaterialTheme.typography.labelMedium)
                        }
                        Slider(
                            value = appBubbleScale,
                            onValueChange = { appBubbleScale = it; setAppBubbleScale(context, it) },
                            valueRange = 0.5f..2.0f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("App spacing", style = MaterialTheme.typography.labelLarge)
                            Text("${"%.1f".format(appSpacingScale)}×", style = MaterialTheme.typography.labelMedium)
                        }
                        Slider(
                            value = appSpacingScale,
                            onValueChange = { appSpacingScale = it; setAppSpacingScale(context, it) },
                            valueRange = 0.8f..2.5f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettingsDialog = false }) { Text("Done") }
                }
            )
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false; newGroupName = "" },
                title = { Text("New Group") },
                text = {
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { Text("Enter group name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newGroupName.isNotBlank()) {
                            val g = GroupState(System.currentTimeMillis().toString(), newGroupName.trim())
                            g.center = Offset(widthPx / 2f + 220f, heightPx / 2f + 220f)
                            groups.add(g)
                            newGroupName = ""
                            showDialog = false
                        }
                    }) { Text("Create") }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false; newGroupName = "" }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun BubbleGroupCluster(
    group: GroupState,
    allGroups: List<GroupState>,
    widthPx: Float,
    heightPx: Float,
    appBubbleScale: Float = 1f,
    appSpacingScale: Float = 1f,
    onDeleteGroup: () -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val bubbleRadius = min(widthPx, heightPx) / 14f * appBubbleScale
    val packRadius = min(widthPx, heightPx) * 2f  // large enough to never clip packing

    val currentBubbleRadius by rememberUpdatedState(bubbleRadius)
    val currentCenter by rememberUpdatedState(group.center)
    val currentAllGroups by rememberUpdatedState(allGroups)

    // Compute groupRadius synchronously so the background circle updates the same frame
    // the app count changes, rather than one frame late via LaunchedEffect.
    val groupRadius = remember(group.apps.size, bubbleRadius, group.dotScale, appSpacingScale) {
        val dotR = bubbleRadius * group.dotScale
        val cp = (bubbleRadius * (CENTER_GAP + group.dotScale - 1f)).coerceAtLeast(0f)
        if (group.apps.isEmpty()) dotR + bubbleRadius * 2f
        else {
            val packed = packBubblesInCircle(group.apps.size + 1, packRadius, bubbleRadius, cp, appSpacingScale)
            (packed.maxOfOrNull { p -> sqrt(p.x * p.x + p.y * p.y) } ?: 0f) + bubbleRadius * 1.3f
        }
    }
    SideEffect { group.groupRadius = groupRadius }

    // Build slot table. Slot 0 = center dot (reserved); apps occupy slots 1..N.
    LaunchedEffect(group.apps.size, group.id, group.dotScale, bubbleRadius, appSpacingScale) {
        if (group.apps.isEmpty()) {
            group.slots.clear()
            group.slots.add(Offset(-bubbleRadius, -bubbleRadius))
            group.slotOf.clear()
            group.ownerOf.clear()
            return@LaunchedEffect
        }
        val cp = (bubbleRadius * (CENTER_GAP + group.dotScale - 1f)).coerceAtLeast(0f)
        val packed = packBubblesInCircle(group.apps.size + 1, packRadius, bubbleRadius, cp, appSpacingScale)
        group.slots.clear()
        group.ownerOf.clear()
        packed.forEach { p -> group.slots.add(Offset(p.x - bubbleRadius, p.y - bubbleRadius)) }
        group.apps.forEachIndexed { i, app ->
            group.slotOf[app.packageName] = i + 1
            group.ownerOf[i + 1] = app.packageName
            // Preserve existing positions so spring-animates to new slot rather than teleporting.
            // New apps start at group center and spring outward.
            if (!group.positions.containsKey(app.packageName)) {
                group.positions[app.packageName] = group.center
                group.velocities[app.packageName] = Offset.Zero
            }
        }
    }

    // Spring-physics loop: animate each non-dragged bubble toward its assigned slot.
    LaunchedEffect(group.id) {
        while (true) {
            withFrameNanos {
                val dragged = group.draggedPackage
                val anyMoving = group.velocities.values.any { it.getDistanceSquared() > 0.05f }
                if (dragged == null && !anyMoving) {
                    val anyDisplaced = group.apps.any { app ->
                        val pos = group.positions[app.packageName] ?: return@any false
                        val slotIdx = group.slotOf[app.packageName] ?: return@any false
                        val slotRel = group.slots.getOrNull(slotIdx) ?: return@any false
                        (pos - (currentCenter + slotRel)).getDistanceSquared() > 1f
                    }
                    if (!anyDisplaced) return@withFrameNanos
                }
                group.apps.forEach { app ->
                    if (app.packageName == dragged) return@forEach
                    val pos = group.positions[app.packageName] ?: return@forEach
                    val slotIdx = group.slotOf[app.packageName] ?: return@forEach
                    val slotRel = group.slots.getOrNull(slotIdx) ?: return@forEach
                    val target = currentCenter + slotRel
                    var vel = group.velocities.getOrDefault(app.packageName, Offset.Zero)
                    vel = (vel + (target - pos) * (SPRING * DT)) * DAMPING
                    val newPos = pos + vel * DT
                    group.velocities[app.packageName] = vel
                    if ((newPos - pos).getDistanceSquared() > 0.001f)
                        group.positions[app.packageName] = newPos
                }
            }
        }
    }

    val resolvedDotColor = group.dotColor ?: MaterialTheme.colorScheme.primary
    val sizeDp = with(density) { (bubbleRadius * 2f).toDp() }
    val dotRadius = bubbleRadius * group.dotScale
    val dotSizeDp = with(density) { (dotRadius * 2f).toDp() }
    val targetBgRadius = groupRadius.coerceAtLeast(dotRadius + bubbleRadius * 2f)
    val bgRadius by animateFloatAsState(
        targetValue = targetBgRadius,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "bgRadius"
    )

    // Background circle drawn directly on a Canvas so it is never constrained by the
    // parent layout bounds (which would squash a large Box into a non-square and make
    // CircleShape clip it into an oval).
    val bgColor = resolvedDotColor.copy(alpha = 0.12f)
    ComposeCanvas(modifier = Modifier.fillMaxSize().zIndex(-1f)) {
        drawCircle(color = bgColor, radius = bgRadius, center = currentCenter)
    }

    // Center dot — scales with group.dotScale. Long-press = open edit dialog, drag = move group.
    val dotFontSize = with(density) { (dotRadius * 0.65f).toSp() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(dotSizeDp)
            .offset {
                IntOffset(
                    (currentCenter.x - dotRadius).roundToInt(),
                    (currentCenter.y - dotRadius).roundToInt()
                )
            }
            .zIndex(2f)
            .clip(CircleShape)
            .background(resolvedDotColor)
            .pointerInput(group.id + "drag") {
                detectDragGestures(
                    onDragStart = { group.isGroupDragging = true },
                    onDragEnd = { group.isGroupDragging = false },
                    onDragCancel = { group.isGroupDragging = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        group.center += dragAmount
                        group.apps.forEach { app ->
                            group.positions[app.packageName]?.let {
                                group.positions[app.packageName] = it + dragAmount
                            }
                        }
                    }
                )
            }
            .pointerInput(group.id + "longpress") {
                detectTapGestures(onLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    group.showEditDialog = true
                })
            }
    ) {
        DRAWABLE_ICONS.firstOrNull { it.first == group.icon }?.second?.let { resId ->
            Icon(
                painter = painterResource(id = resId),
                contentDescription = null,
                modifier = Modifier.size(dotSizeDp * 0.6f),
                tint = Color.White
            )
        }
    }

    // Edit group dialog — title, description, icon picker, color picker.
    if (group.showEditDialog) {
        var editName by remember { mutableStateOf(group.name) }
        var editDesc by remember { mutableStateOf(group.description) }
        var editGroupingEnabled by remember { mutableStateOf(group.groupingEnabled) }
        var editHeadsUpEnabled by remember { mutableStateOf(group.headsUpEnabled) }
        var editIcon by remember { mutableStateOf(group.icon) }
        var editDotScale by remember { mutableStateOf(group.dotScale) }
        var editDotColor by remember { mutableStateOf(group.dotColor) }
        var showColorDialog by remember { mutableStateOf(false) }
        var showDeleteConfirm by remember { mutableStateOf(false) }

        if (showColorDialog) {
            AlertDialog(
                onDismissRequest = { showColorDialog = false },
                title = { Text("Choose Color") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DOT_COLORS.chunked(4).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { c ->
                                    ColorSwatch(
                                        color = c,
                                        selected = c == editDotColor,
                                        onSelect = { editDotColor = it }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showColorDialog = false }) { Text("Done") }
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
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DRAWABLE_ICONS.forEach { (name, resId) ->
                                val selected = editIcon == name
                                val accent = editDotColor ?: resolvedDotColor
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(if (selected) accent.copy(alpha = 0.2f) else Color.Transparent)
                                        .border(
                                            width = if (selected) 2.dp else 1.dp,
                                            color = if (selected) accent else Color.Gray.copy(alpha = 0.4f),
                                            shape = CircleShape
                                        )
                                        .clickable { editIcon = name }
                                ) {
                                    Icon(
                                        painter = painterResource(id = resId),
                                        contentDescription = name,
                                        modifier = Modifier.size(30.dp),
                                        tint = if (selected) accent else MaterialTheme.colorScheme.onSurface
                                    )
                                }
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

    // App bubbles
    group.apps.forEach { app ->
        val isDragged = group.draggedPackage == app.packageName
        AppBubble(
            app = app,
            modifier = Modifier
                .size(sizeDp)
                .offset {
                    val pos = group.positions[app.packageName] ?: Offset.Zero
                    IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                }
                .zIndex(if (isDragged) 1f else 0f)
                .scale(if (isDragged) 1.15f else 1f)
                .pointerInput(app.packageName + "drag") {
                    detectDragGestures(
                        onDragStart = {
                            group.velocities[app.packageName] = Offset.Zero
                            group.draggedPackage = app.packageName
                        },
                        onDragEnd = {
                            group.velocities[app.packageName] = Offset.Zero
                            group.draggedPackage = null

                            // Transfer to another group if dropped inside its radius.
                            val br = currentBubbleRadius
                            val pos = group.positions[app.packageName]
                            if (pos != null) {
                                val appCenter = pos + Offset(br, br)
                                val target = currentAllGroups.firstOrNull { other ->
                                    other.id != group.id &&
                                        (appCenter - other.center).getDistance() < other.groupRadius
                                }
                                if (target != null) {
                                    group.apps.remove(app)
                                    group.positions.remove(app.packageName)
                                    group.velocities.remove(app.packageName)
                                    group.slotOf.remove(app.packageName)
                                    target.apps.add(app)
                                    target.positions[app.packageName] = target.center
                                    target.velocities[app.packageName] = Offset.Zero
                                }
                            }
                        },
                        onDragCancel = {
                            group.velocities[app.packageName] = Offset.Zero
                            group.draggedPackage = null
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val newPos = (group.positions[app.packageName]
                                ?: return@detectDragGestures) + dragAmount
                            group.positions[app.packageName] = newPos

                            val br = currentBubbleRadius
                            val gc = currentCenter
                            val dragCenter = newPos + Offset(br, br)
                            val closestSlot = group.slots.indices.minByOrNull { i ->
                                (gc + group.slots[i] + Offset(br, br) - dragCenter).getDistanceSquared()
                            } ?: return@detectDragGestures

                            val mySlot = group.slotOf[app.packageName]
                                ?: return@detectDragGestures
                            if (closestSlot != mySlot && closestSlot != 0) {
                                val otherPkg = group.ownerOf[closestSlot]
                                    ?: return@detectDragGestures
                                group.slotOf[app.packageName] = closestSlot
                                group.slotOf[otherPkg] = mySlot
                                group.ownerOf[closestSlot] = app.packageName
                                group.ownerOf[mySlot] = otherPkg
                                group.velocities[otherPkg] = Offset.Zero
                            }
                        }
                    )
                }
                .pointerInput(app.packageName + "longpress") {
                    detectTapGestures(onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", app.packageName, null)
                        }
                        context.startActivity(intent)
                    })
                }
        )
    }
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onSelect: (Color) -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier
            )
            .clickable { onSelect(color) }
    )
}

@Composable
private fun AppBubble(app: AppSetting, modifier: Modifier = Modifier) {
    val bitmap = app.iconBitmap ?: return
    Image(
        painter = BitmapPainter(bitmap),
        contentDescription = app.name,
        modifier = modifier.clip(CircleShape)
    )
}

suspend fun getInstalledAppsWithUi(context: Context): List<AppSetting> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    pm.getInstalledPackages(PackageManager.GET_META_DATA)
        .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
        .map { pkg ->
            AppSetting(
                name = pkg.applicationInfo?.loadLabel(pm).toString(),
                packageName = pkg.packageName,
                iconBitmap = pkg.applicationInfo?.loadIcon(pm)?.toBitmap()?.asImageBitmap()
            )
        }
        .sortedBy { it.name }
}


private fun packBubblesInCircle(
    count: Int,
    containerRadius: Float,
    bubbleRadius: Float,
    centerPadding: Float = 0f,
    spacingScale: Float = 1f
): List<Offset> {
    if (count == 0) return emptyList()
    val step = bubbleRadius * 2.15f * spacingScale
    val positions = mutableListOf(Offset.Zero)
    var ringRadius = step + centerPadding
    while (positions.size < count && ringRadius + bubbleRadius <= containerRadius) {
        val capacity = floor(2f * PI.toFloat() * ringRadius / step).toInt().coerceAtLeast(1)
        val inThisRing = min(count - positions.size, capacity)
        val startAngle = (positions.size * 2.399f) % (2f * PI.toFloat())
        val angleStep = 2f * PI.toFloat() / inThisRing
        repeat(inThisRing) { i ->
            val a = startAngle + i * angleStep
            positions.add(Offset(ringRadius * cos(a), ringRadius * sin(a)))
        }
        ringRadius += step
    }
    return positions
}

private fun Drawable.toBitmap(): Bitmap {
    val w = if (intrinsicWidth > 0) intrinsicWidth else 96
    val h = if (intrinsicHeight > 0) intrinsicHeight else 96
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp
}
