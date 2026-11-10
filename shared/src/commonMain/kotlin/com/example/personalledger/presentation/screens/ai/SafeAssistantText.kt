package com.example.personalledger.presentation.screens.ai

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily

/**
 * Renders assistant replies safely: strips HTML / executable markup and shows plain text.
 * No clickable links, no HTML rendering.
 */
@Composable
fun SafeAssistantText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = sanitizeAssistantMarkdown(text),
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.SansSerif),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

internal fun sanitizeAssistantMarkdown(raw: String): String {
    var text = raw
        .replace(Regex("(?is)<script.*?>.*?</script>"), "")
        .replace(Regex("(?is)<style.*?>.*?</style>"), "")
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
    // Soften markdown markers without executing anything.
    text = text
        .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
        .replace(Regex("""__(.+?)__"""), "$1")
        .replace(Regex("""`([^`]+)`"""), "$1")
        .replace(Regex("""\[(.+?)]\([^)]+\)"""), "$1")
    return text.trim()
}
