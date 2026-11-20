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
import com.miss.ga.engine.OtpExtractor

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

    // Iranian mobile (09... or +989...), landline (all Iranian province codes 01x-08x or +981x-+988x), USSD (*...#), international +...
    private val PHONE_OR_USSD_REGEX = Regex(
        "(?:(?<=^|[^0-9a-zA-Z+*#])(?:(?:\\+98|0)9\\d{2}[- ]?\\d{3}[- ]?\\d{4}|(?:\\+98|0)[1-8]\\d{1,2}[- ]?\\d{3,4}[- ]?\\d{4}|\\*[0-9]+(?:[*#][0-9]+)*#|\\+[1-9]\\d{1,3}[- ]?\\d{3,4}[- ]?\\d{4})(?=[^0-9a-zA-Z*#]|$))"
    )

    private val EMAIL_REGEX = Regex(
        "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}",
        RegexOption.IGNORE_CASE
    )

    fun extractEmailLinks(text: String): List<LinkMatch> {
        if (text.isBlank()) return emptyList()
        val matches = mutableListOf<LinkMatch>()
        for (match in EMAIL_REGEX.findAll(text)) {
            val email = match.value
            matches.add(LinkMatch(email, "mailto:$email", match.range.first, match.range.last + 1))
        }
        return matches
    }

    fun extractPhoneLinks(text: String): List<LinkMatch> {
        if (text.isBlank()) return emptyList()
        val normalized = OtpExtractor.normalizeDigits(text)
        val matches = mutableListOf<LinkMatch>()
        for (match in PHONE_OR_USSD_REGEX.findAll(normalized)) {
            val raw = text.substring(match.range.first, match.range.last + 1)
            val cleanDigitsOrUssd = match.value.replace(" ", "").replace("-", "")
            val targetUrl = if (cleanDigitsOrUssd.startsWith("*") && cleanDigitsOrUssd.endsWith("#")) {
                "tel:" + cleanDigitsOrUssd.replace("#", "%23")
            } else {
                "tel:$cleanDigitsOrUssd"
            }
            matches.add(LinkMatch(raw, targetUrl, match.range.first, match.range.last + 1))
        }
        return matches
    }

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

    fun extractAllInteractiveLinks(text: String): List<LinkMatch> {
        val urlLinks = extractLinks(text)
        val phoneLinks = extractPhoneLinks(text)
        val emailLinks = extractEmailLinks(text)
        if (phoneLinks.isEmpty() && emailLinks.isEmpty()) return urlLinks
        if (urlLinks.isEmpty() && emailLinks.isEmpty()) return phoneLinks
        if (urlLinks.isEmpty() && phoneLinks.isEmpty()) return emailLinks

        val combined = (urlLinks + phoneLinks + emailLinks).sortedBy { it.start }
        val result = mutableListOf<LinkMatch>()
        var lastEnd = 0
        for (link in combined) {
            if (link.start >= lastEnd) {
                result.add(link)
                lastEnd = link.end
            }
        }
        return result
    }

    fun buildLinkedText(
        text: String,
        linkColor: Color,
        enableLinks: Boolean = true,
        onLinkClick: ((String) -> Unit)? = null
    ): AnnotatedString {
        val links = extractAllInteractiveLinks(text)
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
 * Creates a rememberable link click callback that handles tel:, mailto:, and http/https URLs.
 */
@Composable
fun rememberLinkClickHandler(): (String) -> Unit {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    return remember(context, uriHandler) {
        { url ->
            try {
                if (url.startsWith("tel:", ignoreCase = true)) {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse(url)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } else if (url.startsWith("mailto:", ignoreCase = true)) {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(url)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } else {
                    try {
                        uriHandler.openUri(url)
                    } catch (_: Exception) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
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
