package dev.agentbrowser.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Bookmarks and saved logins. A null [name] gives an in-memory database for tests. */
class AppDatabase(context: Context, name: String? = "agentbrowser.db") :
    SQLiteOpenHelper(context.applicationContext, name, null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE bookmarks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                url TEXT NOT NULL UNIQUE,
                folder TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )""",
        )
        db.execSQL(
            """CREATE TABLE logins (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                origin TEXT NOT NULL,
                username TEXT NOT NULL,
                password_enc TEXT NOT NULL,
                UNIQUE (origin, username)
            )""",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}
