package com.aiassistant.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aiassistant.app.voice.VoiceSessionState
import com.aiassistant.app.voice.VoiceSessionState.*

fun orbColor(state: VoiceSessionState): Color = when (state) {
    IDLE -> Color(0xFF3A5068)
    ERROR -> Danger
    EXECUTING_ACTION -> Amber
    CONNECTING, RECONNECTING, PROCESSING -> Blue
    PAUSED -> Color(0xFF5C7C99)
    else -> Cyan
}

/** The central orb. Its size and glow follow the REAL session state and measured audio level. */
@Composable
fun VoiceOrb(state: VoiceSessionState, level: Float, size: Dp = 220.dp, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "orb")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "spin")
    val lvl by animateFloatAsState(level, label = "level")
    val color = orbColor(state)
    val active = state != IDLE && state != ERROR && state != PAUSED

    Canvas(modifier.size(size)) {
        val c = center
        val base = this.size.minDimension / 2f
        val r = base * 0.55f * (1f + if (active) 0.06f * pulse + 0.28f * lvl else 0f)
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = 0.5f), Color.Transparent), center = c, radius = base),
            radius = base * (0.85f + 0.15f * lvl),
        )
        drawCircle(color, radius = r * 1.18f, style = Stroke(width = 2.dp.toPx()))
        if (active) {
            rotate(spin, c) {
                val arc = Size(base * 1.6f, base * 1.6f)
                val tl = Offset(c.x - base * 0.8f, c.y - base * 0.8f)
                drawArc(color.copy(alpha = 0.85f), 0f, 100f, false, tl, arc, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                drawArc(color.copy(alpha = 0.5f), 180f, 60f, false, tl, arc, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.15f)), center = c, radius = r), radius = r)
    }
}

@Composable
fun SoundWave(active: Boolean, level: Float, color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "wave")
    Row(modifier.height(40.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(11) { i ->
            val h by t.animateFloat(0.2f, 1f, infiniteRepeatable(tween(400 + i * 47, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bar$i")
            val frac = if (active) (0.15f + h * (0.25f + 0.75f * level)).coerceIn(0.1f, 1f) else 0.1f
            Box(Modifier.width(4.dp).fillMaxHeight(frac).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}
