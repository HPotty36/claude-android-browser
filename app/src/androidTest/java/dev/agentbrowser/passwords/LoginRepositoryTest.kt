package dev.agentbrowser.passwords

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.agentbrowser.data.AppDatabase
import dev.agentbrowser.data.ImportResult
import dev.agentbrowser.settings.SecretStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginRepositoryTest {
    private val db = AppDatabase(InstrumentationRegistry.getInstrumentation().targetContext, name = null)
    private val repo = LoginRepository(db, SecretStore("agentbrowser_test"))

    @After fun close() = db.close()

    private val google = ParsedLogin("https://accounts.google.com", "me@x.com", "pw-google-1")
    private val other = ParsedLogin("https://notgoogle.com", "me", "pw-other")

    @Test fun storedPasswordIsNotPlaintext() = runBlocking {
        repo.import(listOf(google))
        db.readableDatabase.rawQuery("SELECT password_enc FROM logins", null).use { cursor ->
            cursor.moveToFirst()
            assertFalse(cursor.getString(0).contains("pw-google-1"))
        }
    }

    @Test fun forOriginDecrypts() = runBlocking {
        repo.import(listOf(google, other))
        assertEquals(listOf(google), repo.forOrigin("https://accounts.google.com"))
    }

    @Test fun forDomainMatchesSubdomainsOnly() = runBlocking {
        repo.import(listOf(google, other))
        assertEquals(listOf(google), repo.forDomain("google.com"))
        assertEquals(listOf(google), repo.forDomain("accounts.google.com"))
        assertEquals(listOf(other), repo.forDomain("notgoogle.com"))
    }

    @Test fun importUpdatesExistingPair() = runBlocking {
        assertEquals(ImportResult(added = 1, skipped = 0), repo.import(listOf(google)))
        assertEquals(ImportResult(added = 0, skipped = 1), repo.import(listOf(google.copy(password = "pw-new"))))
        assertEquals("pw-new", repo.forOrigin(google.origin).single().password)
    }

    @Test fun saveUpserts() = runBlocking {
        repo.save(google)
        repo.save(google.copy(password = "changed"))
        assertEquals(1, repo.all.value.size)
        assertEquals("changed", repo.reveal(repo.all.value.single().id))
    }

    @Test fun deleteRemoves() = runBlocking {
        repo.save(google)
        val id = repo.all.value.single().id
        repo.delete(id)
        assertEquals(emptyList<Login>(), repo.all.value)
        assertNull(repo.reveal(id))
    }
}
