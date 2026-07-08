package com.juicedcooky.notimanager.walkthrough

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.juicedcooky.notimanager.R

private data class WalkthroughStep(
    val imageRes: Int,
    val title: String,
    val description: String
)

private val WALKTHROUGH_STEPS = listOf(
    WalkthroughStep(
        imageRes = R.drawable.walkthrough_overview,
        title = "Welcome to NotiManager",
        description = "Your apps live inside bubble groups on a zoomable canvas. Each group has a center dot and a ring of app bubbles around it."
    ),
    WalkthroughStep(
        imageRes = R.drawable.walkthrough_create_group,
        title = "Create a Group",
        description = "Tap the + button in the bottom bar, name your group, and it appears on the canvas ready for apps."
    ),
    WalkthroughStep(
        imageRes = R.drawable.walkthrough_edit_group,
        title = "Customize a Group",
        description = "Long-press a group's center dot to rename it, add a description, pick an icon and color, resize the dot, or toggle its notification behavior."
    ),
    WalkthroughStep(
        imageRes = R.drawable.walkthrough_multi_group,
        title = "Organize Your Apps",
        description = "Drag any app bubble into another group's circle to move it there. Each group keeps its own set of apps."
    ),
    WalkthroughStep(
        imageRes = R.drawable.walkthrough_app_info,
        title = "Long-Press for App Info",
        description = "Long-press an app bubble to jump straight to that app's system info page — handy for permissions or uninstalling."
    ),
    WalkthroughStep(
        imageRes = R.drawable.walkthrough_settings,
        title = "Fine-Tune Behavior",
        description = "Open Settings to control heads-up banners, skip media & ongoing notifications, and adjust app size, spacing, and touch area."
    )
)

@Composable
fun WalkthroughDialog(onDismiss: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    val current = WALKTHROUGH_STEPS[step]
    val isLast = step == WALKTHROUGH_STEPS.lastIndex

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(current.title) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(id = current.imageRes),
                    contentDescription = current.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Text(
                    current.description,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    WALKTHROUGH_STEPS.indices.forEach { i ->
                        Box(
                            modifier = Modifier
                                .size(if (i == step) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == step) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onDismiss) { Text("Skip") }
                Row {
                    if (step > 0) {
                        TextButton(onClick = { step-- }) { Text("Back") }
                    }
                    TextButton(onClick = { if (isLast) onDismiss() else step++ }) {
                        Text(if (isLast) "Done" else "Next")
                    }
                }
            }
        }
    )
}
