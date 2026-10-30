package com.miss.ga.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration

data class LinkMatch(
    val text: String,
    val url: String,
    val start: Int,
    val end: Int
)

object LinkUtils {

    private const val SCHEME_URL = "(?:https?://|ftp://)[^\\s<>\"'`{}|\\\\^]+"
    private const val WWW_URL = "www\\.[^\\s<>\"'`{}|\\\\^]+"
    private const val DOMAIN_TLD_URL =
        "(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+(?:com|org|net|edu|gov|mil|io|co|me|ir|ai|app|dev|xyz|info|biz|site|online|tech|link|store|shop|pro|cc|tv|club|space|live|news|today|vip|mobi|asia|ly|gl|is|to|so|gg|in|us|uk|ca|de|fr|nl|ru|cn|jp|au|eu)(?::\\d{1,5})?(?:/[^\\s<>\"'`{}|\\\\^]*)?"

    val URL_REGEX = Regex(
        "(?:$SCHEME_URL)|(?:$WWW_URL)|(?:$DOMAIN_TLD_URL)",
        RegexOption.IGNORE_CASE
    )

    fun extractLinks(text: String): List<LinkMatch> {
        if (text.isBlank()) return emptyList()

        val matches = mutableListOf<LinkMatch>()
        for (match in URL_REGEX.findAll(text)) {
            val start = match.range.first
            // Skip matches immediately preceded by @ (likely an email address)
            if (start > 0 && text[start - 1] == '@') {
                continue
            }

            var raw = match.value
            var end = match.range.last + 1

            // Trim trailing punctuation marks that often trail URLs in natural text
            while (raw.isNotEmpty() && (raw.endsWith('.') || raw.endsWith(',') || raw.endsWith(';') ||
                        raw.endsWith(':') || raw.endsWith('!') || raw.endsWith('?') ||
                        raw.endsWith('\'') || raw.endsWith('"') || raw.endsWith('«') || raw.endsWith('»'))
            ) {
                raw = raw.dropLast(1)
                end--
            }

            // Trim unbalanced closing brackets/parentheses
            while (raw.endsWith(')') && raw.count { it == ')' } > raw.count { it == '(' }) {
                raw = raw.dropLast(1)
                end--
            }
            while (raw.endsWith(']') && raw.count { it == ']' } > raw.count { it == '[' }) {
                raw = raw.dropLast(1)
                end--
            }
            while (raw.endsWith('}') && raw.count { it == '}' } > raw.count { it == '{' }) {
                raw = raw.dropLast(1)
                end--
            }

            if (raw.isNotEmpty()) {
                val normalizedUrl = if (raw.startsWith("http://", ignoreCase = true) ||
                    raw.startsWith("https://", ignoreCase = true) ||
                    raw.startsWith("ftp://", ignoreCase = true)
                ) {
                    raw
                } else {
                    "https://$raw"
                }
                matches.add(LinkMatch(raw, normalizedUrl, start, end))
            }
        }
        return matches
    }

    fun buildLinkedText(
        text: String,
        linkColor: Color,
        enableLinks: Boolean = true,
        onLinkClick: ((String) -> Unit)? = null
    ): AnnotatedString {
        val links = extractLinks(text)
        if (links.isEmpty()) {
            return AnnotatedString(text)
        }

        val linkStyles = TextLinkStyles(
            style = SpanStyle(
                color = linkColor,
                textDecoration = TextDecoration.Underline
            ),
            pressedStyle = SpanStyle(
                color = linkColor.copy(alpha = 0.7f),
                textDecoration = TextDecoration.Underline
            )
        )

        val linkListener = if (onLinkClick != null) {
            LinkInteractionListener { annotation ->
                if (annotation is LinkAnnotation.Url) {
                    onLinkClick(annotation.url)
                }
            }
        } else null

        return buildAnnotatedString {
            append(text)
            for (link in links) {
                if (enableLinks) {
                    addLink(
                        url = LinkAnnotation.Url(
                            url = link.url,
                            styles = linkStyles,
                            linkInteractionListener = linkListener
                        ),
                        start = link.start,
                        end = link.end
                    )
                } else {
                    addStyle(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline
                        ),
                        start = link.start,
                        end = link.end
                    )
                }
            }
        }
    }
}

/**
 * Creates a rememberable link click callback that attempts to open the URL with Compose's
 * UriHandler first, falls back to Android's ACTION_VIEW Intent, and catches any error with a Toast.
 */
@Composable
fun rememberLinkClickHandler(): (String) -> Unit {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    return remember(context, uriHandler) {
        { url ->
            try {
                uriHandler.openUri(url)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e2: Exception) {
                    Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

/**
 * Returns a remembered [AnnotatedString] with styled, interactive links.
 */
@Composable
fun rememberLinkedText(
    text: String,
    linkColor: Color,
    enableLinks: Boolean = true,
    onLinkClick: ((String) -> Unit)? = null
): AnnotatedString {
    return remember(text, linkColor, enableLinks, onLinkClick) {
        LinkUtils.buildLinkedText(
            text = text,
            linkColor = linkColor,
            enableLinks = enableLinks,
            onLinkClick = onLinkClick
        )
    }
}
