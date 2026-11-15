package dev.agentbrowser.bookmarks

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.agentbrowser.data.AppDatabase
import dev.agentbrowser.data.ImportResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookmarkRepositoryTest {
    private val db = AppDatabase(InstrumentationRegistry.getInstrumentation().targetContext, name = null)
    private val repo = BookmarkRepository(db)

    @After fun close() = db.close()

    private val a = ParsedBookmark("A", "https://a.com/", "북마크바")
    private val b = ParsedBookmark("B", "https://b.com/", "북마크바/개발")

    @Test fun importStoresBookmarks() = runBlocking {
        assertEquals(ImportResult(added = 2, skipped = 0), repo.import(listOf(a, b)))
        assertEquals(listOf("A" to "북마크바", "B" to "북마크바/개발"), repo.all.value.map { it.title to it.folder })
    }

    @Test fun importSkipsExistingUrl() = runBlocking {
        repo.import(listOf(a, b))
        assertEquals(ImportResult(added = 0, skipped = 2), repo.import(listOf(a, b)))
        assertEquals(2, repo.all.value.size)
    }

    @Test fun importDedupesWithinFile() = runBlocking {
        assertEquals(ImportResult(added = 1, skipped = 1), repo.import(listOf(a, a.copy(title = "A again"))))
    }

    @Test fun addAndDelete() = runBlocking {
        assertTrue(repo.add("C", "https://c.com/"))
        assertFalse(repo.add("C dup", "https://c.com/"))
        val saved = repo.all.value.single()
        assertEquals("" to "https://c.com/", saved.folder to saved.url)
        repo.delete(saved.id)
        assertEquals(emptyList<Bookmark>(), repo.all.value)
    }
}
