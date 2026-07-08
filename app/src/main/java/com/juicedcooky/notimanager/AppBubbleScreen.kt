package com.juicedcooky.notimanager

import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.juicedcooky.notimanager.group.BubbleGroupCluster
import com.juicedcooky.notimanager.group.GroupState
import com.juicedcooky.notimanager.group.NewGroupDialog
import com.juicedcooky.notimanager.physics.runSeparationPass
import com.juicedcooky.notimanager.search.AppSearchBar
import com.juicedcooky.notimanager.settings.SettingsDialog
import com.juicedcooky.notimanager.util.getInstalledAppsWithUi
import com.juicedcooky.notimanager.walkthrough.WalkthroughDialog
import kotlin.math.min

@Composable
fun AppBubbleScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val groups = remember { mutableStateListOf<GroupState>() }
    var showDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showWalkthroughDialog by remember { mutableStateOf(!hasSeenWalkthrough(context)) }
    var newGroupName by remember { mutableStateOf("") }
    var zoom by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var managementEnabled by remember { mutableStateOf(isNotificationManagementEnabled(context)) }
    var globalHeadsUpEnabled by remember { mutableStateOf(isGlobalHeadsUpEnabled(context)) }
    var ignoreMediaAndOngoing by remember { mutableStateOf(isIgnoreMediaAndOngoing(context)) }
    var appBubbleScale by remember { mutableStateOf(getAppBubbleScale(context)) }
    var appSpacingScale by remember { mutableStateOf(getAppSpacingScale(context)) }
    var touchAreaFraction by remember { mutableStateOf(getTouchAreaFraction(context)) }
    var searchQuery by remember { mutableStateOf("") }

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
                    g.notificationsEnabled = s.notificationsEnabled
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

        // Push groups apart when global scale settings change.
        LaunchedEffect(appBubbleScale, appSpacingScale) {
            runSeparationPass(groups)
        }

        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            zoom = (zoom * zoomChange).coerceIn(0.01f, 3f)
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
                        touchAreaFraction = touchAreaFraction,
                        searchQuery = searchQuery,
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

        SmallFloatingActionButton(
            onClick = {
                if (groups.isNotEmpty()) {
                    var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
                    var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
                    groups.forEach { g ->
                        val r = g.visualGroupRadius.coerceAtLeast(1f)
                        minX = minOf(minX, g.center.x - r)
                        maxX = maxOf(maxX, g.center.x + r)
                        minY = minOf(minY, g.center.y - r)
                        maxY = maxOf(maxY, g.center.y + r)
                    }
                    val bw = (maxX - minX).coerceAtLeast(1f)
                    val bh = (maxY - minY).coerceAtLeast(1f)
                    val newZoom = (min(widthPx * 0.85f / bw, heightPx * 0.85f / bh)).coerceIn(0.01f, 3f)
                    zoom = newZoom
                    panOffset = Offset(
                        (widthPx / 2f - (minX + maxX) / 2f) * newZoom,
                        (heightPx / 2f - (minY + maxY) / 2f) * newZoom
                    )
                }
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.outline_arrows_output_24),
                contentDescription = "Fit all groups on screen"
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(onClick = { showSettingsDialog = true }) {
                Text("⚙", style = MaterialTheme.typography.headlineMedium)
            }
            AppSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                modifier = Modifier.weight(1f)
            )
            FloatingActionButton(onClick = { showDialog = true }) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        }

        if (showSettingsDialog) {
            SettingsDialog(
                groups = groups,
                widthPx = widthPx,
                heightPx = heightPx,
                globalHeadsUpEnabled = globalHeadsUpEnabled,
                onGlobalHeadsUpChange = { globalHeadsUpEnabled = it; setGlobalHeadsUpEnabled(context, it) },
                ignoreMediaAndOngoing = ignoreMediaAndOngoing,
                onIgnoreMediaAndOngoingChange = { ignoreMediaAndOngoing = it; setIgnoreMediaAndOngoing(context, it) },
                appBubbleScale = appBubbleScale,
                onAppBubbleScaleChange = { appBubbleScale = it; setAppBubbleScale(context, it) },
                appSpacingScale = appSpacingScale,
                onAppSpacingScaleChange = { appSpacingScale = it; setAppSpacingScale(context, it) },
                touchAreaFraction = touchAreaFraction,
                onTouchAreaFractionChange = { touchAreaFraction = it; setTouchAreaFraction(context, it) },
                onDismiss = { showSettingsDialog = false },
                onOpenWalkthrough = {
                    showSettingsDialog = false
                    showWalkthroughDialog = true
                }
            )
        }

        if (showWalkthroughDialog) {
            WalkthroughDialog(onDismiss = {
                showWalkthroughDialog = false
                setHasSeenWalkthrough(context, true)
            })
        }

        if (showDialog) {
            NewGroupDialog(
                groupName = newGroupName,
                onGroupNameChange = { newGroupName = it },
                onDismiss = { showDialog = false; newGroupName = "" },
                onCreate = {
                    if (newGroupName.isNotBlank()) {
                        val g = GroupState(System.currentTimeMillis().toString(), newGroupName.trim())
                        g.center = Offset(widthPx / 2f + 220f, heightPx / 2f + 220f)
                        groups.add(g)
                        newGroupName = ""
                        showDialog = false
                    }
                }
            )
        }
    }
}
