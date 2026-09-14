package com.ampgames.vidsaver.data.browser.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Version 1 covers the browser tables. Phase 3 adds the `downloads` table as
 * version 2 with a migration; nothing here is destructive-migration backed,
 * because losing a user's bookmarks on upgrade is not acceptable.
 */
@Database(
    entities = [
        BookmarkEntity::class,
        HistoryEntity::class,
        AllowlistEntry::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class VidSaverDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun allowlistDao(): AllowlistDao

    companion object {
        const val NAME = "vidsaver.db"
    }
}
