package dev.agentbrowser.browser

/** [queryUrl] is the address the URL-encoded search words are appended to. */
data class SearchEngine(val id: String, val name: String, val queryUrl: String)

val SEARCH_ENGINES = listOf(
    SearchEngine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q="),
    SearchEngine("google", "Google", "https://www.google.com/search?q="),
    SearchEngine("naver", "Naver", "https://search.naver.com/search.naver?query="),
    SearchEngine("bing", "Bing", "https://www.bing.com/search?q="),
    SearchEngine("brave", "Brave Search", "https://search.brave.com/search?q="),
)

val DEFAULT_SEARCH_ENGINE = SEARCH_ENGINES.first()

/** The engine with this id, or the default when the id is missing or no longer offered. */
fun searchEngineById(id: String?): SearchEngine = SEARCH_ENGINES.firstOrNull { it.id == id } ?: DEFAULT_SEARCH_ENGINE
