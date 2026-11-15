package dev.agentbrowser.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecretStoreTest {
    private val secrets = SecretStore("agentbrowser_test")

    @Test fun roundTrip() = assertEquals("sk-ant-한글-123", secrets.decrypt(secrets.encrypt("sk-ant-한글-123")))

    @Test fun differentIvEachTime() = assertNotEquals(secrets.encrypt("same"), secrets.encrypt("same"))

    @Test fun ciphertextDoesNotContainPlaintext() = assertFalse(secrets.encrypt("plaintext-key").contains("plaintext"))

    @Test fun tamperedFails() {
        val encoded = secrets.encrypt("secret")
        val flipped = encoded.dropLast(2) + (if (encoded.takeLast(2) == "AA") "BB" else "AA")
        assertThrows(Exception::class.java) { secrets.decrypt(flipped) }
    }

    @Test fun settingsStoreKeyEncrypted() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = AppSettings(context, secrets, prefsName = "settings_test")
        settings.setApiKey("sk-ant-test-abcd1234")
        assertEquals("sk-ant-test-abcd1234", settings.apiKeyBlocking())
        assertEquals("1234", settings.apiKeyHint.value)
        val stored = context.getSharedPreferences("settings_test", 0).all.values.joinToString()
        assertFalse(stored.contains("abcd1234"))
        assertEquals("sk-ant-test-abcd1234", AppSettings(context, secrets, prefsName = "settings_test").apiKeyBlocking())
        settings.setApiKey(null)
        assertNull(settings.apiKeyBlocking())
        assertNull(settings.apiKeyHint.value)
    }

    @Test fun settingsModelDefaultsAndPersists() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("settings_test2", 0).edit().clear().commit()
        val settings = AppSettings(context, secrets, prefsName = "settings_test2")
        assertEquals("claude-sonnet-5-5", settings.modelBlocking())
        settings.setModel("claude-opus-5-5")
        assertTrue(AppSettings(context, secrets, prefsName = "settings_test2").modelBlocking() == "claude-opus-5-5")
    }
}
