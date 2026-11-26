package dev.agentbrowser.translate

import dev.agentbrowser.browser.Tab
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Where a tab's translation stands. */
sealed interface TranslationState {
    data object Off : TranslationState
    /** [done] of [total] requests have come back; both are 0 while the page is still being read. */
    data class Working(val done: Int, val total: Int) : TranslationState
    data object Translated : TranslationState
    /** Translated, except [missed] pieces whose requests failed; asking again translates just those. */
    data class PartlyTranslated(val missed: Int) : TranslationState
    data object Failed : TranslationState
}

/** Where a tab stands after a run that ended with [result], when it stood at [before] as the run began. */
fun translationOutcome(before: TranslationState, result: TranslationResult): TranslationState {
    val shown = before == TranslationState.Translated || before is TranslationState.PartlyTranslated
    return when {
        result.total == 0 -> if (shown) TranslationState.Translated else TranslationState.Off
        result.translated == result.total -> TranslationState.Translated
        result.translated == 0 && !shown -> TranslationState.Failed
        else -> TranslationState.PartlyTranslated(missed = result.total - result.translated)
    }
}

/** Translates tabs: by itself when a page opens in a foreign language, or when asked. */
class PageTranslations(
    private val translator: Translator,
    private val scope: CoroutineScope,
    private val automatic: () -> Boolean,
    private val target: () -> TranslateTarget,
    private val hasKey: () -> Boolean,
) {
    /** A page finished loading. Translates it if it is in a language the user is not expected to read. */
    fun onPageLoaded(tab: Tab) {
        if (!automatic() || !hasKey() || tab.translation.value != TranslationState.Off) return
        tab.translationJob?.cancel()
        tab.translationJob = scope.launch {
            val page = tab.driver.pageLanguage() ?: return@launch
            // A sign-in page is left alone: nothing on it is sent anywhere unless the user asks.
            if (page.hasPasswordField) return@launch
            if (translatesByItself(detectLanguage(page.lang, page.sample))) run(tab)
        }
    }

    /** Translates the tab's page now. False when there is no API key to do it with. */
    fun translate(tab: Tab): Boolean {
        if (!hasKey()) return false
        tab.translationJob?.cancel()
        tab.translationJob = scope.launch { run(tab) }
        return true
    }

    fun showOriginal(tab: Tab) {
        tab.translationJob?.cancel()
        tab.translationJob = scope.launch {
            tab.driver.restore()
            tab.setTranslation(TranslationState.Off)
        }
    }

    private suspend fun run(tab: Tab) {
        val before = tab.translation.value
        tab.setTranslation(TranslationState.Working(0, 0))
        val state = try {
            val result = translator.translate(tab.driver, target()) { done, total ->
                tab.setTranslation(TranslationState.Working(done, total))
            }
            translationOutcome(before, result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The page could not be read: what an earlier run put on it is still there.
            if (before is TranslationState.PartlyTranslated) before else TranslationState.Failed
        }
        tab.setTranslation(state)
    }
}
