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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.retrosprite.app.ui.theme.RetroSpriteTheme

/**
 * The signature surface used everywhere: a card with an asymmetric "tab" header
 * (mono label in primary, double-line accent) sitting on a 14dp-rounded panel.
 * It evokes a cartridge sticker peeking off the top edge.
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
    val outlineColor = if (accent) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant

    Column(modifier = modifier.fillMaxWidth()) {
        // Tab header sticking out of the card top-left
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(
                        if (accent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (accent) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            if (trailing != null) {
                Box(modifier = Modifier.padding(end = 4.dp)) { trailing() }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, outlineColor, RoundedCornerShape(14.dp))
                .padding(contentPadding)
        ) {
            content()
        }

        // Bottom accent rule: thin neon hairline under the card. Subtle CRT undercurrent.
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
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.0f)
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .size(width = 18.dp, height = 2.dp)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f))
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
