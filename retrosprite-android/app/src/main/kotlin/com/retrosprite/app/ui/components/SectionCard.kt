package com.retrosprite.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.retrosprite.app.ui.theme.RetroSpriteTheme

/**
 * The signature HUD surface used across the management app. It mirrors the
 * in-game overlay: cyan linework, low-opacity black glass, and a quiet tab label.
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val outlineColor = MaterialTheme.colorScheme.primary.copy(alpha = if (accent) 0.92f else 0.58f)
    val glowColor = MaterialTheme.colorScheme.primary.copy(alpha = if (accent) 0.30f else 0.16f)
    val scanlineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.035f)
    val tabBg = MaterialTheme.colorScheme.surface.copy(alpha = if (accent) 0.96f else 0.78f)
    val panelShape = RoundedCornerShape(18.dp)
    val ruleAlpha = if (accent) 0.56f else 0.28f

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 18.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .background(tabBg)
                    .border(1.dp, outlineColor, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            if (trailing != null) {
                Box(modifier = Modifier.padding(end = 4.dp)) { trailing() }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val radius = 18.dp.toPx()
                    drawRoundRect(
                        color = glowColor,
                        cornerRadius = CornerRadius(radius, radius),
                        style = Stroke(width = 4.dp.toPx())
                    )
                }
                .clip(panelShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        )
                    )
                )
                .drawBehind {
                    var y = 10.dp.toPx()
                    while (y < size.height) {
                        drawLine(
                            color = scanlineColor,
                            start = androidx.compose.ui.geometry.Offset(12.dp.toPx(), y),
                            end = androidx.compose.ui.geometry.Offset(size.width - 12.dp.toPx(), y),
                            strokeWidth = 1f
                        )
                        y += 8.dp.toPx()
                    }
                }
                .border(1.dp, outlineColor, panelShape)
                .padding(contentPadding)
        ) {
            content()
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .height(2.dp)
                    .weight(1f)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.0f),
                                MaterialTheme.colorScheme.primary.copy(alpha = ruleAlpha),
                                MaterialTheme.colorScheme.secondary.copy(alpha = ruleAlpha * 0.7f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.0f)
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .size(width = 18.dp, height = 2.dp)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = ruleAlpha))
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0620, widthDp = 360)
@Composable
private fun SectionCardPreview() {
    RetroSpriteTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SectionCard(title = "\u8fd0\u884c\u72b6\u6001", accent = true) {
                Text("\u7aef\u70b9\u5df2\u5728\u672c\u673a\u8fd0\u884c", color = MaterialTheme.colorScheme.onSurface)
            }
            SectionCard(title = "RetroArch \u914d\u7f6e") {
                Text("3 \u6b65\u5b8c\u6210\u63a5\u5165", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
