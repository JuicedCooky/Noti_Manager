package com.juicedcooky.notimanager.group

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.juicedcooky.notimanager.physics.CENTER_GAP
import com.juicedcooky.notimanager.physics.DAMPING
import com.juicedcooky.notimanager.physics.DT
import com.juicedcooky.notimanager.physics.SPRING
import com.juicedcooky.notimanager.physics.packBubblesInCircle
import com.juicedcooky.notimanager.physics.runSeparationPass
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun BubbleGroupCluster(
    group: GroupState,
    allGroups: List<GroupState>,
    widthPx: Float,
    heightPx: Float,
    appBubbleScale: Float = 1f,
    appSpacingScale: Float = 1f,
    touchAreaFraction: Float = 1f,
    searchQuery: String = "",
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

    // Collision radius uses spacingScale=1 so increasing app spacing doesn't force
    // groups apart beyond what the screen can accommodate.
    val groupRadius = remember(group.apps.size, bubbleRadius, group.dotScale) {
        val dotR = bubbleRadius * group.dotScale
        val cp = (bubbleRadius * (CENTER_GAP + group.dotScale - 1f)).coerceAtLeast(0f)
        if (group.apps.isEmpty()) dotR + bubbleRadius * 2f
        else {
            val packed = packBubblesInCircle(group.apps.size + 1, packRadius, bubbleRadius, cp, 1f)
            (packed.maxOfOrNull { p -> sqrt(p.x * p.x + p.y * p.y) } ?: 0f) + bubbleRadius * 1.3f
        }
    }
    SideEffect { group.groupRadius = groupRadius }

    // Visual radius uses the actual spacing scale so the background expands to contain
    // apps when spacing is increased, and the fit-to-screen button accounts for it too.
    val visualGroupRadius = remember(group.apps.size, bubbleRadius, group.dotScale, appSpacingScale) {
        val dotR = bubbleRadius * group.dotScale
        val cp = (bubbleRadius * (CENTER_GAP + group.dotScale - 1f)).coerceAtLeast(0f)
        if (group.apps.isEmpty()) dotR + bubbleRadius * 2f
        else {
            val packed = packBubblesInCircle(group.apps.size + 1, packRadius, bubbleRadius, cp, appSpacingScale)
            (packed.maxOfOrNull { p -> sqrt(p.x * p.x + p.y * p.y) } ?: 0f) + bubbleRadius * 1.3f
        }
    }
    SideEffect { group.visualGroupRadius = visualGroupRadius }

    // Push other groups away when this group grows via dot size or app count.
    LaunchedEffect(group.dotScale, group.apps.size) {
        runSeparationPass(allGroups)
    }

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
    val targetBgRadius = visualGroupRadius.coerceAtLeast(dotRadius + bubbleRadius * 2f)
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
            .then(
                if (group.id != "default") {
                    Modifier.pointerInput(group.id + "drag") {
                        detectDragGestures(
                            onDragStart = { group.isGroupDragging = true },
                            onDragEnd = { group.isGroupDragging = false },
                            onDragCancel = { group.isGroupDragging = false },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                var proposed = group.center + dragAmount
                                for (other in currentAllGroups) {
                                    if (other.id == group.id) continue
                                    val delta = proposed - other.center
                                    val dist = delta.getDistance()
                                    val minDist = group.visualGroupRadius + other.visualGroupRadius + 40f
                                    if (dist < minDist) {
                                        val norm = if (dist > 0.1f) delta / dist else Offset(1f, 0f)
                                        proposed = other.center + norm * minDist
                                    }
                                }
                                val actualDrag = proposed - group.center
                                group.center = proposed
                                group.apps.forEach { app ->
                                    group.positions[app.packageName]?.let {
                                        group.positions[app.packageName] = it + actualDrag
                                    }
                                }
                            }
                        )
                    }
                } else Modifier
            )
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

    GroupEditDialog(
        group = group,
        allGroups = allGroups,
        resolvedDotColor = resolvedDotColor,
        onDeleteGroup = onDeleteGroup
    )

    // Interpolate touch diameter from visual size (0%) to max non-overlapping size (100%).
    // Max = step size in packBubblesInCircle = bubbleRadius * 2.15 * appSpacingScale.
    val touchDiameter = bubbleRadius * (2f + (2.15f * appSpacingScale - 2f) * touchAreaFraction)
    val touchSizeDp = with(density) { touchDiameter.toDp() }

    // App bubbles
    val isSearchActive = searchQuery.isNotEmpty()
    group.apps.forEach { app ->
        val isDragged = group.draggedPackage == app.packageName
        val isMatch = isSearchActive && app.name.contains(searchQuery, ignoreCase = true)
        AppBubble(
            app = app,
            visualSizeDp = sizeDp,
            highlighted = isMatch,
            dimmed = isSearchActive && !isMatch,
            modifier = Modifier
                .size(touchSizeDp)
                .offset {
                    val pos = group.positions[app.packageName] ?: Offset.Zero
                    val padding = touchDiameter / 2f - bubbleRadius
                    IntOffset(
                        (pos.x - padding).roundToInt(),
                        (pos.y - padding).roundToInt()
                    )
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
