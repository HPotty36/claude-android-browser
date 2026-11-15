package dev.agentbrowser

import android.app.Application
import dev.agentbrowser.agent.AgentLoop
import dev.agentbrowser.agent.HttpClaudeClient
import dev.agentbrowser.browser.BrowserController
import dev.agentbrowser.bookmarks.BookmarkRepository
import dev.agentbrowser.chat.ChatViewModel
import dev.agentbrowser.data.AppDatabase
import dev.agentbrowser.passwords.GeckoLoginStorage
import dev.agentbrowser.passwords.LoginPromptDelegate
import dev.agentbrowser.passwords.LoginRepository
import dev.agentbrowser.settings.AppSettings
import dev.agentbrowser.settings.SecretStore
import dev.agentbrowser.update.Updater
import dev.agentbrowser.update.installApk
import java.io.File
import kotlinx.coroutines.MainScope

class App : Application() {
    val scope = MainScope()
    val secrets: SecretStore by lazy { SecretStore() }
    val settings: AppSettings by lazy { AppSettings(this, secrets) }

    val database: AppDatabase by lazy { AppDatabase(this) }
    val bookmarks: BookmarkRepository by lazy { BookmarkRepository(database) }
    val logins: LoginRepository by lazy { LoginRepository(database, secrets) }
    val loginPrompts = LoginPromptDelegate()

    /** One GeckoRuntime per process, so the controller lives with the application. */
    val browser: BrowserController by lazy {
        BrowserController(this).also { controller ->
            controller.runtime.autocompleteStorageDelegate = GeckoLoginStorage(logins, scope)
            controller.promptDelegate = loginPrompts
        }
    }

    val updater: Updater by lazy {
        Updater(
            installedVersion = BuildConfig.VERSION_NAME,
            // A debug build is a different app id; the release APK would install beside it, not over it.
            enabled = !BuildConfig.DEBUG,
            dir = File(cacheDir, "update"),
            install = { installApk(this, it) },
            lastCheck = { settings.lastUpdateCheck },
            saveLastCheck = { settings.lastUpdateCheck = it },
        )
    }

    val chat: ChatViewModel by lazy {
        val client = HttpClaudeClient(settings::apiKeyBlocking, settings::modelBlocking)
        ChatViewModel(AgentLoop(client, browser.pageDriver, strings = settings::strings), scope, settings::strings)
    }
}
