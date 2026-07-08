package com.juicedcooky.notimanager.physics

import androidx.compose.ui.geometry.Offset
import com.juicedcooky.notimanager.group.GroupState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

const val SPRING = 40f
const val DAMPING = 0.80f
const val DT = 0.016f
// Extra gap between the center dot and the first ring of app bubbles, in bubbleRadius units.
const val CENTER_GAP = 1.0f

fun runSeparationPass(groups: List<GroupState>) {
    repeat(10) {
        for (i in groups.indices) {
            for (j in i + 1 until groups.size) {
                val gi = groups[i]; val gj = groups[j]
                val delta = gj.center - gi.center
                val dist = delta.getDistance()
                val minDist = gi.visualGroupRadius + gj.visualGroupRadius + 40f
                if (dist < minDist) {
                    val norm = if (dist > 0.1f) delta / dist else Offset(1f, 0f)
                    val overlap = minDist - dist
                    val iFixed = gi.id == "default"
                    val jFixed = gj.id == "default"
                    if (!iFixed) {
                        val push = norm * (if (jFixed) overlap else overlap * 0.5f)
                        gi.center -= push
                        gi.apps.forEach { app ->
                            gi.positions[app.packageName]?.let { gi.positions[app.packageName] = it - push }
                        }
                    }
                    if (!jFixed) {
                        val push = norm * (if (iFixed) overlap else overlap * 0.5f)
                        gj.center += push
                        gj.apps.forEach { app ->
                            gj.positions[app.packageName]?.let { gj.positions[app.packageName] = it + push }
                        }
                    }
                }
            }
        }
    }
}

fun packBubblesInCircle(
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
