package dev.agentbrowser.passwords

import java.net.URI

/** [origin] is scheme://host[:port]. */
data class ParsedLogin(val origin: String, val username: String, val password: String)

data class CsvParseResult(val logins: List<ParsedLogin>, val skipped: Int)

/**
 * Reads the password CSV that Chrome exports. Columns are found by header name, so their order does
 * not matter. Rows that are not http(s) sites or have no password are counted in [CsvParseResult.skipped].
 * Never throws: a file without url, username and password columns yields an empty result.
 */
fun parsePasswordCsv(csv: String): CsvParseResult {
    val rows = parseCsvRows(csv.removePrefix("﻿")).filterNot { row -> row.all { it.isBlank() } }
    val header = rows.firstOrNull()?.map { it.trim().lowercase() } ?: return CsvParseResult(emptyList(), 0)
    val urlAt = header.indexOf("url")
    val userAt = header.indexOf("username")
    val passwordAt = header.indexOf("password")
    if (urlAt < 0 || userAt < 0 || passwordAt < 0) return CsvParseResult(emptyList(), 0)

    val logins = mutableListOf<ParsedLogin>()
    var skipped = 0
    for (row in rows.drop(1)) {
        val origin = row.getOrNull(urlAt)?.let(::originOf)
        val password = row.getOrNull(passwordAt).orEmpty()
        if (origin == null || password.isEmpty()) {
            skipped++
            continue
        }
        logins += ParsedLogin(origin, row.getOrNull(userAt).orEmpty(), password)
    }
    return CsvParseResult(logins, skipped)
}

/** scheme://host[:port] of an http(s) URL, or null for anything else. */
fun originOf(url: String): String? = try {
    val uri = URI(url.trim())
    val scheme = uri.scheme?.lowercase()
    val host = uri.host
    if ((scheme != "http" && scheme != "https") || host.isNullOrEmpty()) null
    else "$scheme://${host.lowercase()}" + if (uri.port >= 0) ":${uri.port}" else ""
} catch (e: Exception) {
    null
}

/** RFC 4180: quoted fields may hold commas, line breaks and doubled quotes. */
private fun parseCsvRows(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var i = 0
    fun endRow() {
        row += field.toString()
        field.clear()
        rows += row
        row = mutableListOf()
    }
    while (i < text.length) {
        val c = text[i]
        when {
            quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
            quoted && c == '"' -> quoted = false
            quoted -> field.append(c)
            c == '"' -> quoted = true
            c == ',' -> { row += field.toString(); field.clear() }
            c == '\r' -> { if (text.getOrNull(i + 1) == '\n') i++; endRow() }
            c == '\n' -> endRow()
            else -> field.append(c)
        }
        i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) endRow()
    return rows
}
