package simplylucaapps.girocalendar.ui.common

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

private val URL_REGEX = Regex("""https?://[^\s]+""")
private val BOLD_REGEX = Regex("""\*\*(.+?)\*\*""")

/**
 * Testo con supporto minimale "markdown": **grassetto** e URL auto-linkati (cliccabili, aprono
 * il browser via [LinkAnnotation.Url]). Niente libreria esterna: basta per note/descrizioni brevi.
 */
@Composable
fun RichText(text: String, modifier: Modifier = Modifier) {
    val linkColor = MaterialTheme.colorScheme.primary
    val linkStyles = TextLinkStyles(style = SpanStyle(color = linkColor, fontWeight = FontWeight.Medium))

    data class Link(val range: IntRange, val url: String)
    val links = URL_REGEX.findAll(text).map { Link(it.range, it.value) }.toList()

    val annotated = buildAnnotatedString {
        var i = 0
        fun appendWithBold(segment: String) {
            var last = 0
            for (m in BOLD_REGEX.findAll(segment)) {
                append(segment.substring(last, m.range.first))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
                last = m.range.last + 1
            }
            append(segment.substring(last))
        }
        for (link in links) {
            if (link.range.first > i) appendWithBold(text.substring(i, link.range.first))
            withLink(LinkAnnotation.Url(link.url, linkStyles)) { append(link.url) }
            i = link.range.last + 1
        }
        if (i < text.length) appendWithBold(text.substring(i))
    }

    SelectionContainer(modifier) {
        Text(
            text = annotated,
            style = LocalTextStyle.current.copy(color = LocalContentColor.current),
        )
    }
}
