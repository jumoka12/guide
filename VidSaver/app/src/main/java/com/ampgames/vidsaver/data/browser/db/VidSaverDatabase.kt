package com.ampgames.vidsaver.data.browser.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ampgames.vidsaver.data.download.db.DownloadDao
import com.ampgames.vidsaver.data.download.db.DownloadEntity

/**
 * Version 2 adds the `downloads` table (Phase 3). Migrations are written by
 * hand rather than falling back to destructive migration: losing a user's
 * bookmarks or download history on upgrade is not acceptable.
 */
@Database(
    entities = [
        BookmarkEntity::class,
        HistoryEntity::class,
        AllowlistEntry::class,
        DownloadEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class VidSaverDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun allowlistDao(): AllowlistDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        const val NAME = "vidsaver.db"

        /** Phase 2 (browser only) -> Phase 3 (browser + downloads). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `downloads` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `url` TEXT NOT NULL,
                        `pageUrl` TEXT NOT NULL,
                        `headersJson` TEXT NOT NULL DEFAULT '{}',
                        `fileName` TEXT NOT NULL,
                        `mediaType` TEXT NOT NULL,
                        `mimeType` TEXT,
                        `thumbnailUrl` TEXT,
                        `title` TEXT,
                        `status` TEXT NOT NULL,
                        `bytesDownloaded` INTEGER NOT NULL,
                        `totalBytes` INTEGER,
                        `speedBytesPerSecond` INTEGER NOT NULL,
                        `failure` TEXT NOT NULL,
                        `error` TEXT,
                        `retryCount` INTEGER NOT NULL,
                        `mediaStoreUri` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `completedAt` INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_status` ON `downloads` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_createdAt` ON `downloads` (`createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_url` ON `downloads` (`url`)")
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2)
    }
}
