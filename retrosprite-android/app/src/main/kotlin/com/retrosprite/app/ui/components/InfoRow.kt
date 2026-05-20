package com.retrosprite.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.retrosprite.app.ui.theme.RetroSpriteTheme

/**
 * Labelled key/value row. By default the label is mono-uppercase on the left and the
 * value (sans, larger) on the right; pass [stacked] = true for a vertical layout used
 * when the value is long (multi-line URL / response preview).
 */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
    valueMonospace: Boolean = false
) {
    if (stacked) {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = if (valueMonospace) MaterialTheme.typography.labelLarge
                else MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(96.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = value,
                style = if (valueMonospace) MaterialTheme.typography.labelLarge
                else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF15103A, widthDp = 320)
@Composable
private fun InfoRowPreview() {
    RetroSpriteTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            InfoRow(label = "\u7aef\u53e3", value = "4404")
            InfoRow(label = "\u5b9d\u5e94\u65f6\u95f4", value = "1.2 s")
            InfoRow(
                label = "URL",
                value = "http://localhost:4404",
                stacked = true,
                valueMonospace = true
            )
        }
    }
}
