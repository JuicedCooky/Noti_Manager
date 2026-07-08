package com.juicedcooky.notimanager.group

import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun HsvColorPicker(
    initialColor: Color,
    onColorChanged: (Color) -> Unit
) {
    val initHsv = remember(initialColor) {
        val arr = FloatArray(3)
        android.graphics.Color.colorToHSV(
            android.graphics.Color.argb(
                255,
                (initialColor.red * 255).toInt(),
                (initialColor.green * 255).toInt(),
                (initialColor.blue * 255).toInt()
            ),
            arr
        )
        arr
    }
    var hue by remember { mutableStateOf(initHsv[0]) }
    var sat by remember { mutableStateOf(initHsv[1]) }
    var brightness by remember { mutableStateOf(initHsv[2]) }

    val currentColor = remember(hue, sat, brightness) {
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, brightness)))
    }

    LaunchedEffect(currentColor) {
        onColorChanged(currentColor)
    }

    val hueColor = remember(hue) {
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    }

    val hueBarColors = remember {
        listOf(
            Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
            Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000)
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Saturation / brightness panel
        ComposeCanvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            change.consume()
                            sat = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            brightness = 1f - (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                        }
                    }
                }
        ) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.White, hueColor), startX = 0f, endX = size.width),
                size = size
            )
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = size.height),
                size = size
            )
            val thumbX = sat * size.width
            val thumbY = (1f - brightness) * size.height
            drawCircle(Color.White, radius = 14f, center = Offset(thumbX, thumbY))
            drawCircle(currentColor, radius = 11f, center = Offset(thumbX, thumbY))
        }

        // Hue bar
        ComposeCanvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(50))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            change.consume()
                            hue = (change.position.x / size.width.toFloat() * 360f).coerceIn(0f, 360f)
                        }
                    }
                }
        ) {
            drawRect(
                brush = Brush.horizontalGradient(hueBarColors, startX = 0f, endX = size.width),
                size = size
            )
            val thumbX = hue / 360f * size.width
            val thumbR = size.height / 2f
            drawCircle(Color.White, radius = thumbR, center = Offset(thumbX, thumbR))
            drawCircle(hueColor, radius = thumbR - 2f, center = Offset(thumbX, thumbR))
        }

        // Color preview + hex code
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(currentColor)
            )
            Text(
                text = "#%02X%02X%02X".format(
                    (currentColor.red * 255).toInt(),
                    (currentColor.green * 255).toInt(),
                    (currentColor.blue * 255).toInt()
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
