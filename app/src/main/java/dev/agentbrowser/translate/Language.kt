package dev.agentbrowser.translate

/** The language a page is translated into. [english] is the name the model is given. */
enum class TranslateTarget(val id: String, val english: String) {
    KOREAN("ko", "Korean"),
    ENGLISH("en", "English"),
}

fun translateTargetById(id: String?): TranslateTarget =
    TranslateTarget.entries.firstOrNull { it.id == id } ?: TranslateTarget.KOREAN

private const val MIN_LETTERS = 20

/**
 * The language of a page, as a two-letter code, or null when it cannot be told. Scripts other than Latin are
 * read off the text itself, because pages often claim a language they are not written in. Latin text looks the
 * same in dozens of languages, so there the page's own `lang` is taken at its word.
 */
fun detectLanguage(htmlLang: String?, sample: String): String? {
    var hangul = 0
    var kana = 0
    var han = 0
    var cyrillic = 0
    var arabic = 0
    var thai = 0
    var devanagari = 0
    var greek = 0
    var hebrew = 0
    var latin = 0
    for (c in sample) {
        when (c.code) {
            in 0xAC00..0xD7A3, in 0x1100..0x11FF, in 0x3130..0x318F -> hangul++
            in 0x3040..0x30FF -> kana++
            in 0x4E00..0x9FFF -> han++
            in 0x0400..0x04FF -> cyrillic++
            in 0x0600..0x06FF -> arabic++
            in 0x0E00..0x0E7F -> thai++
            in 0x0900..0x097F -> devanagari++
            in 0x0370..0x03FF -> greek++
            in 0x0590..0x05FF -> hebrew++
            else -> if (c.isLetter() && c.code < 0x0250) latin++
        }
    }
    val letters = hangul + kana + han + cyrillic + arabic + thai + devanagari + greek + hebrew + latin
    if (letters < MIN_LETTERS) return null
    fun share(count: Int) = count.toFloat() / letters
    return when {
        // Korean pages are full of English terms, so Hangul does not need a majority.
        share(hangul) >= 0.3f -> "ko"
        // Japanese is mostly Han characters with kana between them; any real amount of kana settles it.
        share(kana) >= 0.05f -> "ja"
        share(han) >= 0.3f -> "zh"
        share(cyrillic) >= 0.3f -> "ru"
        share(arabic) >= 0.3f -> "ar"
        share(thai) >= 0.3f -> "th"
        share(devanagari) >= 0.3f -> "hi"
        share(greek) >= 0.3f -> "el"
        share(hebrew) >= 0.3f -> "he"
        else -> htmlLang?.trim()?.lowercase()?.substringBefore('-')?.substringBefore('_')?.takeIf { it.length in 2..3 }
    }
}

/** Whether a page in this language is translated without being asked: anything that is known and not Korean or English. */
fun translatesByItself(language: String?): Boolean = language != null && language != "ko" && language != "en"
