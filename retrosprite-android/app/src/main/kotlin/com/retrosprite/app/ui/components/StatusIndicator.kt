package com.retrosprite.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.retrosprite.app.ui.theme.RetroSpriteTheme
import com.retrosprite.app.ui.theme.StatusError
import com.retrosprite.app.ui.theme.StatusRunning
import com.retrosprite.app.ui.theme.StatusStarting
import com.retrosprite.app.ui.theme.StatusStopped
import com.retrosprite.app.ui.viewmodel.UiEndpointPhase

/**
 * Pill-shaped status chip: glowing dot + uppercase mono label.
 * The dot pulses while [pulse] is true (Starting / Running) for that "tube TV" liveness.
 */
@Composable
fun StatusIndicator(
    phase: UiEndpointPhase,
    label: String,
    modifier: Modifier = Modifier
) {
    val color = phase.color()
    val pulse = phase == UiEndpointPhase.Starting || phase == UiEndpointPhase.Running

    val transition = rememberInfiniteTransition(label = "status-pulse")
    val alpha by transition.animateFloat(
        initialValue = if (pulse) 0.45f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (phase == UiEndpointPhase.Starting) 480 else 1_400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "status-pulse-alpha"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .alpha(if (pulse) alpha else 1f)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

private fun UiEndpointPhase.color(): Color = when (this) {
    UiEndpointPhase.Running -> StatusRunning
    UiEndpointPhase.Starting -> StatusStarting
    UiEndpointPhase.Stopped -> StatusStopped
    UiEndpointPhase.Error -> StatusError
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0620)
@Composable
private fun StatusIndicatorPreview() {
    RetroSpriteTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusIndicator(UiEndpointPhase.Running, "RUNNING : 4404")
            StatusIndicator(UiEndpointPhase.Stopped, "STOPPED")
            StatusIndicator(UiEndpointPhase.Starting, "STARTING")
            StatusIndicator(UiEndpointPhase.Error, "ERROR")
        }
    }
}
