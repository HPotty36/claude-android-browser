package dev.agentbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StringsTest {
    @Test fun systemFollowsAKoreanDevice() = assertEquals(Ko, stringsFor(Language.SYSTEM, deviceLanguage = "ko"))
    @Test fun systemFallsBackToEnglish() = assertEquals(En, stringsFor(Language.SYSTEM, deviceLanguage = "ja"))
    @Test fun englishOverridesAKoreanDevice() = assertEquals(En, stringsFor(Language.ENGLISH, deviceLanguage = "ko"))
    @Test fun koreanOverridesAnEnglishDevice() = assertEquals(Ko, stringsFor(Language.KOREAN, deviceLanguage = "en"))
    @Test fun unknownSavedValueMeansSystem() = assertEquals(Language.SYSTEM, languageById("nope"))
    @Test fun savedValueRoundTrips() = Language.entries.forEach { assertEquals(it, languageById(it.id)) }

    /** Every plain string of the English set, read through its getter. */
    private fun texts(strings: Strings): Map<String, String> = Strings::class.java.methods
        .filter { it.parameterCount == 0 && it.returnType == String::class.java }
        .associate { it.name to it.invoke(strings) as String }

    @Test fun englishHasNoKoreanLeftInIt() {
        val hangul = Regex("[\\uAC00-\\uD7A3]")
        val leftovers = texts(En).filter { (name, text) -> name != "getLanguageKorean" && hangul.containsMatchIn(text) }
        assertEquals(emptyMap<String, String>(), leftovers)
    }

    @Test fun noStringIsBlank() {
        assertTrue(texts(En).values.none { it.isBlank() })
        assertTrue(texts(Ko).values.none { it.isBlank() })
    }

    @Test fun countsReadNaturallyInEnglish() {
        assertEquals("1 tab", En.tabCount(1))
        assertEquals("3 tabs", En.tabCount(3))
        assertEquals("Imported 2 bookmarks", En.importedBookmarks(2, 0))
        assertEquals("Imported 1 password (4 skipped)", En.importedPasswords(1, 4))
    }

    @Test fun updateStringsCarryTheirNumbers() {
        assertEquals("Version 0.5.0 is available", En.updateAvailable("0.5.0"))
        assertEquals("새 버전 0.5.0", Ko.updateAvailable("0.5.0"))
        assertEquals("Download and install (100 MB)", En.downloadUpdate(100))
        assertEquals("다운로드 중… 42%", Ko.downloadingUpdate(42))
    }

    @Test fun actionsAreDescribedInTheChosenLanguage() {
        assertEquals("Click 'Search'", En.actionClick("Search"))
        assertEquals("'검색' 클릭", Ko.actionClick("검색"))
        assertEquals("Type 'shoes' into 'Search' and submit", En.actionType("Search", "shoes", submit = true))
        assertEquals("'검색'에 '운동화' 입력 후 제출", Ko.actionType("검색", "운동화", submit = true))
    }
}
