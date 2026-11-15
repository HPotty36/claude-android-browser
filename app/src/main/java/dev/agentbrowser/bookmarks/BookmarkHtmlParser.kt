package dev.agentbrowser.bookmarks

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** [folder] is the path of folder names joined with "/", or "" at the top level. */
data class ParsedBookmark(val title: String, val url: String, val folder: String)

/**
 * Reads the Netscape bookmark HTML that Chrome exports. Only http(s) links are kept.
 * Never throws: input that is not a bookmark file yields an empty list.
 */
fun parseBookmarkHtml(html: String): List<ParsedBookmark> = try {
    Jsoup.parse(html).select("a[href]").mapNotNull { link ->
        val url = link.attr("href").trim()
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return@mapNotNull null
        }
        ParsedBookmark(link.text().trim().ifEmpty { url }, url, folderPath(link))
    }
} catch (e: Exception) {
    emptyList()
}

/** A folder is an H3 followed by the DL that holds its entries, so each enclosing DL names one level. */
private fun folderPath(link: Element): String =
    link.parents().filter { it.normalName() == "dl" }.mapNotNull { folderName(it) }.reversed().joinToString("/")

private fun folderName(list: Element): String? {
    var sibling = list.previousElementSibling()
    while (sibling != null) {
        if (sibling.normalName() == "h3") return sibling.text().trim()
        if (sibling.normalName() == "dl") return null
        sibling = sibling.previousElementSibling()
    }
    return null
}
