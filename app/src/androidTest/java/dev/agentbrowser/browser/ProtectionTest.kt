package dev.agentbrowser.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.agentbrowser.App
import dev.agentbrowser.settings.AppSettings
import dev.agentbrowser.settings.SecretStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtectionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as App

    private fun controller(): BrowserController {
        lateinit var controller: BrowserController
        instrumentation.runOnMainSync { controller = app.browser }
        return controller
    }

    private fun awaitAdBlock(controller: BrowserController, enabled: Boolean) = runBlocking {
        withTimeout(60_000) { controller.adBlockActive.first { it == enabled } }
    }

    @Test fun bothAreOnUntilTurnedOff() {
        val name = "protection-test"
        val context = instrumentation.targetContext
        context.getSharedPreferences(name, 0).edit().clear().commit()
        val settings = AppSettings(context, SecretStore(), name)
        assertTrue(settings.adBlock.value)
        assertTrue(settings.encryptedDns.value)
        settings.setAdBlock(false)
        settings.setEncryptedDns(false)
        val reopened = AppSettings(context, SecretStore(), name)
        assertFalse(reopened.adBlock.value)
        assertFalse(reopened.encryptedDns.value)
    }

    @Test fun theAdBlockerIsInstalledAndFollowsTheSwitch() {
        val controller = controller()
        awaitAdBlock(controller, true)
        try {
            instrumentation.runOnMainSync { controller.setAdBlock(false) }
            awaitAdBlock(controller, false)
        } finally {
            instrumentation.runOnMainSync { controller.setAdBlock(true) }
        }
        awaitAdBlock(controller, true)
    }

    @Test fun lookupsGoThroughTheEncryptedResolverAndFollowTheSwitch() {
        val controller = controller()
        assertEquals(DOH_URL, controller.runtime.settings.trustedRecursiveResolverUri)
        assertTrue(controller.encryptedDns)
        try {
            instrumentation.runOnMainSync { controller.setEncryptedDns(false) }
            assertFalse(controller.encryptedDns)
        } finally {
            instrumentation.runOnMainSync { controller.setEncryptedDns(true) }
        }
    }
}
