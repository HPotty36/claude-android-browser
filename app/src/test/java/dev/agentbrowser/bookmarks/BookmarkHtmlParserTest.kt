package dev.agentbrowser.bookmarks

import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkHtmlParserTest {
    private val sample = """
<!DOCTYPE NETSCAPE-Bookmark-file-1>
<META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
<TITLE>Bookmarks</TITLE>
<H1>Bookmarks</H1>
<DL><p>
  <DT><H3 ADD_DATE="1" PERSONAL_TOOLBAR_FOLDER="true">북마크바</H3>
  <DL><p>
    <DT><A HREF="https://a.com/" ADD_DATE="1">A</A>
    <DT><H3>개발</H3>
    <DL><p>
      <DT><A HREF="https://b.com/x?y=1&amp;z=2">B &amp; C</A>
    </DL><p>
    <DT><A HREF="https://d.com/">D</A>
  </DL><p>
  <DT><A HREF="https://c.com/">C</A>
  <DT><A HREF="javascript:void(0)">북마클릿</A>
  <DT><A HREF="chrome://settings/">설정</A>
</DL><p>
"""

    @Test fun nestedFolders() {
        val parsed = parseBookmarkHtml(sample)
        assertEquals(ParsedBookmark("A", "https://a.com/", "북마크바"), parsed[0])
        assertEquals(ParsedBookmark("B & C", "https://b.com/x?y=1&z=2", "북마크바/개발"), parsed[1])
        assertEquals(ParsedBookmark("D", "https://d.com/", "북마크바"), parsed[2])
        assertEquals(ParsedBookmark("C", "https://c.com/", ""), parsed[3])
    }

    @Test fun skipsNonHttp() = assertEquals(4, parseBookmarkHtml(sample).size)

    @Test fun emptyTitleUsesUrl() = assertEquals(
        listOf(ParsedBookmark("https://e.com/", "https://e.com/", "")),
        parseBookmarkHtml("""<DL><p><DT><A HREF="https://e.com/"></A></DL><p>"""),
    )

    @Test fun garbageReturnsEmpty() =
        assertEquals(emptyList<ParsedBookmark>(), parseBookmarkHtml("not html at all <<<"))

    @Test fun emptyInputReturnsEmpty() = assertEquals(emptyList<ParsedBookmark>(), parseBookmarkHtml(""))
}
