package dev.agentbrowser.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageTest {
    private val korean = "오늘 서울은 대체로 맑겠고 낮 기온은 15도까지 오르겠습니다. 미세먼지는 보통 수준입니다."
    private val japanese = "今日の東京は晴れで、最高気温は15度になるでしょう。花粉は少ない見込みです。"
    private val chinese = "今天北京天气晴朗，最高气温十五度，空气质量良好，适合外出活动。明天有小雨。"
    private val russian = "Сегодня в Москве ясно, температура поднимется до пятнадцати градусов тепла."
    private val french = "Aujourd'hui à Paris le temps sera ensoleillé avec une température maximale de quinze degrés."
    private val english = "Today in London it will be mostly sunny with a high of fifteen degrees and light winds."

    @Test fun textInHangulIsKoreanWhateverThePageSays() = assertEquals("ko", detectLanguage("en", korean))
    @Test fun kanaMeansJapanese() = assertEquals("ja", detectLanguage(null, japanese))
    @Test fun hanWithoutKanaMeansChinese() = assertEquals("zh", detectLanguage(null, chinese))
    @Test fun cyrillicMeansRussian() = assertEquals("ru", detectLanguage(null, russian))
    @Test fun aPageThatSaysEnglishButIsJapaneseIsJapanese() = assertEquals("ja", detectLanguage("en-US", japanese))
    @Test fun latinTextTakesThePagesWord() {
        assertEquals("fr", detectLanguage("fr-FR", french))
        assertEquals("en", detectLanguage("EN", english))
    }
    @Test fun latinTextOnAPageThatSaysNothingIsUnknown() = assertNull(detectLanguage(null, french))
    @Test fun tooLittleTextIsUnknown() = assertNull(detectLanguage("fr", "12 345"))
    @Test fun aKoreanPageFullOfEnglishWordsIsStillKorean() =
        assertEquals("ko", detectLanguage(null, "React Native 앱에서 TypeScript 설정을 바꾸는 방법을 Docker 환경 기준으로 정리했습니다"))

    @Test fun koreanAndEnglishPagesAreLeftAlone() {
        assertFalse(translatesByItself("ko"))
        assertFalse(translatesByItself("en"))
    }
    @Test fun otherLanguagesAreTranslated() {
        assertTrue(translatesByItself("ja"))
        assertTrue(translatesByItself("fr"))
    }
    @Test fun anUnknownLanguageIsLeftAlone() = assertFalse(translatesByItself(null))

    @Test fun unknownSavedTargetMeansKorean() = assertEquals(TranslateTarget.KOREAN, translateTargetById("nope"))
    @Test fun savedTargetRoundTrips() = TranslateTarget.entries.forEach { assertEquals(it, translateTargetById(it.id)) }
}
