package com.juicedcooky.notimanager.group

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.juicedcooky.notimanager.AppSetting

@Composable
fun AppBubble(
    app: AppSetting,
    visualSizeDp: Dp,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    dimmed: Boolean = false
) {
    val bitmap = app.iconBitmap ?: return
    Box(
        modifier = modifier.graphicsLayer { alpha = if (dimmed) 0.25f else 1f },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = BitmapPainter(bitmap),
            contentDescription = app.name,
            modifier = Modifier
                .size(visualSizeDp)
                .clip(CircleShape)
                .then(if (highlighted) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
        )
    }
}
