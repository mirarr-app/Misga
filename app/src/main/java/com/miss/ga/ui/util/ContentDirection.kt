package com.miss.ga.ui.util

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection

/**
 * The app UI is English-only, but message bodies, snippets, contact names and
 * anything typed by users can be in RTL scripts (Persian, Arabic, Hebrew...).
 * Resolving the paragraph direction from the text content itself makes those
 * render with correct alignment and punctuation placement.
 */
fun TextStyle.contentAware(): TextStyle = copy(textDirection = TextDirection.Content)
