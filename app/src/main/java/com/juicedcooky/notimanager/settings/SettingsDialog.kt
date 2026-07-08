package com.juicedcooky.notimanager.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.juicedcooky.notimanager.group.GroupState
import com.juicedcooky.notimanager.physics.runSeparationPass
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
private fun SettingsSectionHeader(title: String) {
    HorizontalDivider()
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun SettingsDialog(
    groups: List<GroupState>,
    widthPx: Float,
    heightPx: Float,
    globalHeadsUpEnabled: Boolean,
    onGlobalHeadsUpChange: (Boolean) -> Unit,
    ignoreMediaAndOngoing: Boolean,
    onIgnoreMediaAndOngoingChange: (Boolean) -> Unit,
    appBubbleScale: Float,
    onAppBubbleScaleChange: (Float) -> Unit,
    appSpacingScale: Float,
    onAppSpacingScaleChange: (Float) -> Unit,
    touchAreaFraction: Float,
    onTouchAreaFractionChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    onOpenWalkthrough: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SettingsSectionHeader("Notifications")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Heads-up notifications", style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (globalHeadsUpEnabled) "Peek banner enabled for all groups"
                            else "All notifications delivered silently",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = globalHeadsUpEnabled,
                        onCheckedChange = onGlobalHeadsUpChange
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Skip media & ongoing", style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (ignoreMediaAndOngoing) "Media players and active notifications ignored"
                            else "All notifications managed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = ignoreMediaAndOngoing,
                        onCheckedChange = onIgnoreMediaAndOngoingChange
                    )
                }
                SettingsSectionHeader("Appearance")
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
                    onValueChange = onAppBubbleScaleChange,
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
                    onValueChange = onAppSpacingScaleChange,
                    valueRange = 0.8f..2.5f,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Touch area", style = MaterialTheme.typography.labelLarge)
                    Text("${(touchAreaFraction * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                }
                Slider(
                    value = touchAreaFraction,
                    onValueChange = onTouchAreaFractionChange,
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                SettingsSectionHeader("Layout")
                TextButton(
                    onClick = {
                        val default = groups.firstOrNull { it.id == "default" }
                        val nonDefault = groups.filter { it.id != "default" }
                        if (default != null && nonDefault.isNotEmpty()) {
                            val n = nonDefault.size
                            nonDefault.forEachIndexed { i, g ->
                                val angle = 2.0 * PI * i / n
                                // Place just outside default's radius; runSeparationPass
                                // below resolves any remaining overlap between groups.
                                val spread = default.visualGroupRadius + g.visualGroupRadius + 40f
                                val newCenter = Offset(
                                    default.center.x + (spread * cos(angle)).toFloat(),
                                    default.center.y + (spread * sin(angle)).toFloat()
                                )
                                g.center = newCenter
                                g.apps.forEach { app ->
                                    g.positions[app.packageName] = newCenter
                                    g.velocities[app.packageName] = Offset.Zero
                                }
                            }
                            runSeparationPass(groups)
                        }
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Gather groups near default")
                }
                TextButton(
                    onClick = {
                        val n = groups.size
                        if (n > 0) {
                            val cols = ceil(sqrt(n.toFloat())).toInt().coerceAtLeast(1)
                            val rows = ceil(n.toFloat() / cols).toInt().coerceAtLeast(1)
                            val spacingX = widthPx / (cols + 1)
                            val spacingY = heightPx / (rows + 1)
                            groups.forEachIndexed { i, g ->
                                val col = (i % cols) + 1
                                val row = (i / cols) + 1
                                val newCenter = Offset(spacingX * col, spacingY * row)
                                g.center = newCenter
                                g.apps.forEach { app ->
                                    g.positions[app.packageName] = newCenter
                                    g.velocities[app.packageName] = Offset.Zero
                                }
                            }
                        }
                        onAppBubbleScaleChange(1.0f)
                        onAppSpacingScaleChange(1.0f)
                        onTouchAreaFractionChange(1.0f)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset group positions")
                }
                SettingsSectionHeader("Help & About")
                TextButton(
                    onClick = onOpenWalkthrough,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Walkthrough: How NotiManager works")
                }
                TextButton(
                    onClick = {
                        val packageName = context.packageName
                        try {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("market://details?id=$packageName")
                                ).apply {
                                    setPackage("com.android.vending")
                                }
                            )
                        } catch (e: ActivityNotFoundException) {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Rate NotiManager on the Play Store")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}
