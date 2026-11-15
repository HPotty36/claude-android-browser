package dev.agentbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlInputTest {
    @Test fun keepsFullUrl() = assertEquals("https://example.com/a", toUrl("https://example.com/a"))
    @Test fun addsScheme() = assertEquals("https://naver.com", toUrl("naver.com"))
    @Test fun trims() = assertEquals("https://naver.com", toUrl("  naver.com "))
    @Test fun searchesWords() = assertEquals(
        "https://www.google.com/search?q=%EC%9A%B4%EB%8F%99%ED%99%94+%EC%B6%94%EC%B2%9C",
        toUrl("운동화 추천"),
    )
    @Test fun searchesSingleWordWithoutDot() =
        assertEquals("https://www.google.com/search?q=weather", toUrl("weather"))

    @Test fun displayHostStripsSchemeAndWww() = assertEquals("google.com", displayHost("https://www.google.com/search?q=a"))
    @Test fun displayHostKeepsSubdomain() = assertEquals("m.naver.com", displayHost("https://m.naver.com/"))
    @Test fun displayHostKeepsPort() = assertEquals("192.168.0.1:8080", displayHost("http://192.168.0.1:8080/x"))
    @Test fun displayHostFallsBackToInput() = assertEquals("about:blank", displayHost("about:blank"))
    @Test fun displayHostOfEmptyIsEmpty() = assertEquals("", displayHost(""))
}
