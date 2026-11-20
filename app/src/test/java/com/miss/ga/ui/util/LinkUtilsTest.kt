package com.miss.ga.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkUtilsTest {

    @Test
    fun testUrlExtraction() {
        val input = "Check https://example.com/test?a=1&b=2. Also visit www.google.com, (https://github.com/user/repo) and snapp.ir/discount!"
        val links = LinkUtils.extractLinks(input)
        assertEquals(4, links.size)

        assertEquals("https://example.com/test?a=1&b=2", links[0].text)
        assertEquals("https://example.com/test?a=1&b=2", links[0].url)

        assertEquals("www.google.com", links[1].text)
        assertEquals("https://www.google.com", links[1].url)

        assertEquals("https://github.com/user/repo", links[2].text)
        assertEquals("https://github.com/user/repo", links[2].url)

        assertEquals("snapp.ir/discount", links[3].text)
        assertEquals("https://snapp.ir/discount", links[3].url)
    }

    @Test
    fun testWikipediaBalancedParentheses() {
        val input = "Read this: https://en.wikipedia.org/wiki/Kotlin_(programming_language) for details."
        val links = LinkUtils.extractLinks(input)
        assertEquals(1, links.size)
        assertEquals("https://en.wikipedia.org/wiki/Kotlin_(programming_language)", links[0].text)
        assertEquals("https://en.wikipedia.org/wiki/Kotlin_(programming_language)", links[0].url)
    }

    @Test
    fun testEmailNotTreatedAsUrl() {
        val input = "Contact support@example.com for help or visit https://example.com"
        val links = LinkUtils.extractLinks(input)
        assertEquals(1, links.size)
        assertEquals("https://example.com", links[0].text)
    }

    @Test
    fun testPersianTextWithUrls() {
        val input = "سلام، لطفا برای ورود به سایت https://snapp.ir/dl یا www.digikala.com مراجعه کنید."
        val links = LinkUtils.extractLinks(input)
        assertEquals(2, links.size)
        assertEquals("https://snapp.ir/dl", links[0].text)
        assertEquals("www.digikala.com", links[1].text)
    }

    @Test
    fun testPortNumbersAndFragments() {
        val input = "Test server is at http://localhost:8080/dashboard#overview and http://192.168.1.1:3000/api."
        val links = LinkUtils.extractLinks(input)
        assertEquals(2, links.size)
        assertEquals("http://localhost:8080/dashboard#overview", links[0].text)
        assertEquals("http://192.168.1.1:3000/api", links[1].text)
    }

    @Test
    fun testNoLinksInPlainText() {
        val input = "Hello world, this is a normal message with no link at all."
        val links = LinkUtils.extractLinks(input)
        assertTrue(links.isEmpty())
        val annotated = LinkUtils.buildLinkedText(input, Color.Blue)
        assertEquals(input, annotated.text)
        assertTrue(annotated.getLinkAnnotations(0, input.length).isEmpty())
    }

    @Test
    fun testTrailingPunctuationsAndQuotes() {
        val input = "Visit «https://site1.ir», 'https://site2.com', and \"https://site3.org/page\"; please."
        val links = LinkUtils.extractLinks(input)
        assertEquals(3, links.size)
        assertEquals("https://site1.ir", links[0].text)
        assertEquals("https://site2.com", links[1].text)
        assertEquals("https://site3.org/page", links[2].text)
    }

    @Test
    fun testBuildLinkedTextAnnotations() {
        val input = "Go to https://example.com and check it out."
        val annotated = LinkUtils.buildLinkedText(input, Color.Cyan, enableLinks = true)
        assertEquals(input, annotated.text)

        val linkAnnotations = annotated.getLinkAnnotations(0, input.length)
        assertEquals(1, linkAnnotations.size)
        val linkItem = linkAnnotations[0]
        assertTrue(linkItem.item is LinkAnnotation.Url)
        assertEquals("https://example.com", (linkItem.item as LinkAnnotation.Url).url)
        assertEquals(6, linkItem.start)
        assertEquals(25, linkItem.end)
    }

    @Test
    fun testBuildLinkedTextDisabledLinks() {
        val input = "Go to https://example.com and check it out."
        val annotated = LinkUtils.buildLinkedText(input, Color.Cyan, enableLinks = false)
        assertEquals(input, annotated.text)

        // When links are disabled (e.g. selection mode), no LinkAnnotations are added
        val linkAnnotations = annotated.getLinkAnnotations(0, input.length)
        assertTrue(linkAnnotations.isEmpty())

        // But SpanStyles are present so the visual styling remains consistent
        val spanStyles = annotated.spanStyles
        assertEquals(1, spanStyles.size)
        assertEquals(6, spanStyles[0].start)
        assertEquals(25, spanStyles[0].end)
    }

    @Test
    fun testPhoneExtraction() {
        val input = "Call me at 09123456789 or office 02188887777, or intl +989351234567."
        val phoneLinks = LinkUtils.extractPhoneLinks(input)
        assertEquals(3, phoneLinks.size)

        assertEquals("09123456789", phoneLinks[0].text)
        assertEquals("tel:09123456789", phoneLinks[0].url)

        assertEquals("02188887777", phoneLinks[1].text)
        assertEquals("tel:02188887777", phoneLinks[1].url)

        assertEquals("+989351234567", phoneLinks[2].text)
        assertEquals("tel:+989351234567", phoneLinks[2].url)
    }

    @Test
    fun testUssdExtraction() {
        val input = "Dial *788# to check balance, or *140*11# for packages."
        val ussdLinks = LinkUtils.extractPhoneLinks(input)
        assertEquals(2, ussdLinks.size)

        assertEquals("*788#", ussdLinks[0].text)
        assertEquals("tel:*788%23", ussdLinks[0].url)

        assertEquals("*140*11#", ussdLinks[1].text)
        assertEquals("tel:*140*11%23", ussdLinks[1].url)
    }

    @Test
    fun testAllInteractiveLinksCombined() {
        val input = "لطفاً با 09123456789 تماس بگیرید یا به https://example.com بروید یا *100# را شماره‌گیری نمایید."
        val allLinks = LinkUtils.extractAllInteractiveLinks(input)
        assertEquals(3, allLinks.size)

        assertEquals("09123456789", allLinks[0].text)
        assertEquals("tel:09123456789", allLinks[0].url)

        assertEquals("https://example.com", allLinks[1].text)
        assertEquals("https://example.com", allLinks[1].url)

        assertEquals("*100#", allLinks[2].text)
        assertEquals("tel:*100%23", allLinks[2].url)
    }
}
