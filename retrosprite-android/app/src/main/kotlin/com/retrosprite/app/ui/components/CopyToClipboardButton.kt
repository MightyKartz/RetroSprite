package com.retrosprite.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.retrosprite.app.ui.theme.RetroSpriteTheme

/**
 * Action chip that copies [textToCopy] to the system clipboard and shows a tiny toast.
 * Uses an AssistChip so it visually nests inside cards without dominating like a Button.
 */
@Composable
fun CopyToClipboardButton(
    textToCopy: String,
    label: String = "\u590d\u5236",
    successMessage: String = "\u5df2\u590d\u5236\u5230\u526a\u8d34\u677f",
    clipLabel: String = "RetroSprite",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    AssistChip(
        modifier = modifier,
        onClick = {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(clipLabel, textToCopy))
            Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = MaterialTheme.colorScheme.primary,
            leadingIconContentColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0620)
@Composable
private fun CopyButtonPreview() {
    RetroSpriteTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CopyToClipboardButton(textToCopy = "http://192.168.1.42:8080")
        }
    }
}
