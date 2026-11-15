package dev.agentbrowser.bookmarks

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.agentbrowser.data.AppDatabase
import dev.agentbrowser.data.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

data class Bookmark(val id: Long, val title: String, val url: String, val folder: String)

class BookmarkRepository(private val db: AppDatabase) {
    private val _all = MutableStateFlow(load())
    /** Every bookmark, in the order it was added. */
    val all: StateFlow<List<Bookmark>> = _all

    private fun load(): List<Bookmark> =
        db.readableDatabase.rawQuery("SELECT id, title, url, folder FROM bookmarks ORDER BY id", null).use { c ->
            buildList { while (c.moveToNext()) add(Bookmark(c.getLong(0), c.getString(1), c.getString(2), c.getString(3))) }
        }

    private fun insert(title: String, url: String, folder: String): Boolean {
        val values = ContentValues().apply {
            put("title", title)
            put("url", url)
            put("folder", folder)
            put("created_at", System.currentTimeMillis())
        }
        return db.writableDatabase.insertWithOnConflict("bookmarks", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    /** Adds bookmarks whose URL is not saved yet; the rest are counted as skipped. */
    suspend fun import(items: List<ParsedBookmark>): ImportResult = withContext(Dispatchers.IO) {
        var added = 0
        val database = db.writableDatabase
        database.beginTransaction()
        try {
            for (item in items) if (insert(item.title, item.url, item.folder)) added++
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        _all.value = load()
        ImportResult(added, items.size - added)
    }

    /** False when the URL is already bookmarked. */
    suspend fun add(title: String, url: String): Boolean = withContext(Dispatchers.IO) {
        insert(title.ifBlank { url }, url, "").also { _all.value = load() }
    }

    /** Puts back a bookmark that was just deleted, folder included. */
    suspend fun restore(bookmark: Bookmark) = withContext(Dispatchers.IO) {
        insert(bookmark.title, bookmark.url, bookmark.folder)
        _all.value = load()
    }

    suspend fun deleteByUrl(url: String) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("bookmarks", "url = ?", arrayOf(url))
        _all.value = load()
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("bookmarks", "id = ?", arrayOf(id.toString()))
        _all.value = load()
    }
}
