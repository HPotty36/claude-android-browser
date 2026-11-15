package dev.agentbrowser.passwords

import android.content.ContentValues
import dev.agentbrowser.data.AppDatabase
import dev.agentbrowser.data.ImportResult
import dev.agentbrowser.settings.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.net.URI

/** A saved login without its password. */
data class Login(val id: Long, val origin: String, val username: String)

/** Saved logins. Passwords are encrypted with [secrets] before they reach the database. */
class LoginRepository(private val db: AppDatabase, private val secrets: SecretStore) {
    private val _all = MutableStateFlow(load())
    val all: StateFlow<List<Login>> = _all

    private fun load(): List<Login> =
        db.readableDatabase.rawQuery("SELECT id, origin, username FROM logins ORDER BY origin, username", null).use { c ->
            buildList { while (c.moveToNext()) add(Login(c.getLong(0), c.getString(1), c.getString(2))) }
        }

    private fun decryptAll(where: (origin: String) -> Boolean): List<ParsedLogin> =
        db.readableDatabase.rawQuery("SELECT origin, username, password_enc FROM logins ORDER BY id", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    if (!where(c.getString(0))) continue
                    val password = runCatching { secrets.decrypt(c.getString(2)) }.getOrNull() ?: continue
                    add(ParsedLogin(c.getString(0), c.getString(1), password))
                }
            }
        }

    /** True when a new row was added, false when an existing origin+username was updated. */
    private fun upsert(login: ParsedLogin): Boolean {
        val database = db.writableDatabase
        val values = ContentValues().apply { put("password_enc", secrets.encrypt(login.password)) }
        val updated = database.update("logins", values, "origin = ? AND username = ?", arrayOf(login.origin, login.username))
        if (updated > 0) return false
        values.put("origin", login.origin)
        values.put("username", login.username)
        database.insert("logins", null, values)
        return true
    }

    /** New logins are added; an existing origin+username gets the imported password and counts as skipped. */
    suspend fun import(items: List<ParsedLogin>): ImportResult = withContext(Dispatchers.IO) {
        var added = 0
        for (item in items) if (upsert(item)) added++
        _all.value = load()
        ImportResult(added, items.size - added)
    }

    suspend fun save(login: ParsedLogin) = withContext(Dispatchers.IO) {
        upsert(login)
        _all.value = load()
    }

    suspend fun forOrigin(origin: String): List<ParsedLogin> = withContext(Dispatchers.IO) {
        decryptAll { it == origin }
    }

    /** Logins whose host is [domain] or one of its subdomains. */
    suspend fun forDomain(domain: String): List<ParsedLogin> = withContext(Dispatchers.IO) {
        val wanted = domain.lowercase()
        decryptAll { origin ->
            val host = runCatching { URI(origin).host }.getOrNull()?.lowercase() ?: return@decryptAll false
            host == wanted || host.endsWith(".$wanted")
        }
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("logins", "id = ?", arrayOf(id.toString()))
        _all.value = load()
    }

    /** The password of one saved login, or null when it no longer exists. */
    suspend fun reveal(id: Long): String? = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery("SELECT password_enc FROM logins WHERE id = ?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) runCatching { secrets.decrypt(c.getString(0)) }.getOrNull() else null
        }
    }
}
