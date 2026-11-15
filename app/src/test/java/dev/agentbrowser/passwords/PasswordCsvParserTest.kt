package dev.agentbrowser.passwords

import org.junit.Assert.assertEquals
import org.junit.Test

class PasswordCsvParserTest {
    @Test fun chromeFormat() {
        val r = parsePasswordCsv("name,url,username,password,note\nex,https://ex.com/login,me@x.com,pw1,\n")
        assertEquals(listOf(ParsedLogin("https://ex.com", "me@x.com", "pw1")), r.logins)
        assertEquals(0, r.skipped)
    }

    @Test fun quotedCommaQuoteAndNewline() {
        val r = parsePasswordCsv("name,url,username,password\nex,https://ex.com/,me,\"a,b\"\"c\nd\"\n")
        assertEquals("a,b\"c\nd", r.logins.single().password)
    }

    @Test fun bomAndCrlf() {
        val r = parsePasswordCsv("﻿name,url,username,password\r\nex,https://ex.com/,me,pw\r\nex2,https://ex2.com/,you,pw2\r\n")
        assertEquals(
            listOf(ParsedLogin("https://ex.com", "me", "pw"), ParsedLogin("https://ex2.com", "you", "pw2")),
            r.logins,
        )
    }

    @Test fun columnsInDifferentOrder() {
        val r = parsePasswordCsv("Password,Username,URL\npw,me,https://ex.com/a\n")
        assertEquals(listOf(ParsedLogin("https://ex.com", "me", "pw")), r.logins)
    }

    @Test fun skipsAndroidAndEmpty() {
        val r = parsePasswordCsv(
            "name,url,username,password\n" +
                "app,android://hash@com.example/,me,pw\n" +
                "empty,https://a.com/,me,\n" +
                "\n" +
                "ok,https://b.com/,me,pw\n",
        )
        assertEquals(listOf(ParsedLogin("https://b.com", "me", "pw")), r.logins)
        assertEquals(2, r.skipped)
    }

    @Test fun missingColumnReturnsEmpty() {
        val r = parsePasswordCsv("name,url,username\nex,https://ex.com/,me\n")
        assertEquals(CsvParseResult(emptyList(), 0), r)
    }

    @Test fun keepsPort() {
        val r = parsePasswordCsv("url,username,password\nhttp://192.168.0.1:8080/x,admin,pw\n")
        assertEquals("http://192.168.0.1:8080", r.logins.single().origin)
    }

    @Test fun shortRowIsSkipped() {
        val r = parsePasswordCsv("url,username,password\nhttps://a.com/\nhttps://b.com/,me,pw\n")
        assertEquals(listOf(ParsedLogin("https://b.com", "me", "pw")), r.logins)
        assertEquals(1, r.skipped)
    }

    @Test fun emptyInputReturnsEmpty() = assertEquals(CsvParseResult(emptyList(), 0), parsePasswordCsv(""))
}
