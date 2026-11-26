package dev.agentbrowser

import dev.agentbrowser.translate.TranslationModel
import dev.agentbrowser.agent.Block
import dev.agentbrowser.agent.Message
import dev.agentbrowser.settings.TRANSLATION_MODEL
import dev.agentbrowser.translate.ClaudeTranslationClient
import dev.agentbrowser.translate.PageTranslations
import dev.agentbrowser.translate.Translator
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
import dev.agentbrowser.browser.KeyShortcut
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

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
        BrowserController(
            this,
            encryptedDns = settings.encryptedDns.value,
            adBlock = settings.adBlock.value,
        ).also { controller ->
            controller.runtime.autocompleteStorageDelegate = GeckoLoginStorage(logins, scope)
            controller.promptDelegate = loginPrompts
            controller.onPageLoaded = { tab -> translations.onPageLoaded(tab) }
            scope.launch { settings.adBlock.collect(controller::setAdBlock) }
            scope.launch { settings.encryptedDns.collect(controller::setEncryptedDns) }
        }
    }

    val translations: PageTranslations by lazy {
        // Should the translation model be retired, the chat model takes over rather than every page failing.
        val model = TranslationModel(TRANSLATION_MODEL) { settings.modelBlocking() }
        // A chunk's translation can run longer than its source; room for it, so the reply is not cut off.
        val client = HttpClaudeClient(settings::apiKeyBlocking, model::current, maxTokens = 8_192)
        val translator = Translator(ClaudeTranslationClient { system, user ->
            model.withFallback { client.send(system, listOf(Message("user", listOf(Block.Text(user)))), emptyList()) }
                .content.filterIsInstance<Block.Text>().joinToString("") { it.text }
        })
        PageTranslations(
            translator = translator,
            scope = scope,
            automatic = { settings.autoTranslate.value },
            target = { settings.translateTarget.value },
            hasKey = { settings.apiKeyBlocking() != null },
        )
    }

    /** Hardware keyboard shortcuts, from the activity that sees the keys to the screen that acts on them. */
    val shortcuts = MutableSharedFlow<KeyShortcut>(extraBufferCapacity = 8)

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
