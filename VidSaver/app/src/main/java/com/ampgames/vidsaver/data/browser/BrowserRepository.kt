package com.ampgames.vidsaver.data.browser

import com.ampgames.vidsaver.data.browser.db.BookmarkDao
import com.ampgames.vidsaver.data.browser.db.BookmarkEntity
import com.ampgames.vidsaver.data.browser.db.HistoryDao
import com.ampgames.vidsaver.data.browser.db.HistoryEntity
import com.ampgames.vidsaver.domain.browser.UnsupportedDomains
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class BrowserRepository @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val historyDao: HistoryDao,
) {

    fun observeBookmarks(): Flow<List<BookmarkEntity>> = bookmarkDao.observeAll()

    fun observeHistory(): Flow<List<HistoryEntity>> = historyDao.observeRecent()

    suspend fun isBookmarked(url: String): Boolean = bookmarkDao.exists(url)

    suspend fun addBookmark(url: String, title: String) {
        bookmarkDao.insert(
            BookmarkEntity(url = url, title = title, createdAt = System.currentTimeMillis()),
        )
    }

    suspend fun removeBookmark(url: String) = bookmarkDao.deleteByUrl(url)

    suspend fun toggleBookmark(url: String, title: String): Boolean {
        return if (bookmarkDao.exists(url)) {
            bookmarkDao.deleteByUrl(url)
            false
        } else {
            addBookmark(url, title)
            true
        }
    }

    /**
     * Records a visit. Unsupported domains are never written to history — we do
     * not keep a record of pages the app refuses to open.
     */
    suspend fun recordVisit(url: String, title: String) {
        if (url.isBlank() || UnsupportedDomains.isUnsupported(url)) return
        if (!url.startsWith("http", ignoreCase = true)) return
        historyDao.insert(
            HistoryEntity(url = url, title = title, visitedAt = System.currentTimeMillis()),
        )
        historyDao.trimTo(MAX_HISTORY_ENTRIES)
    }

    suspend fun searchHistory(query: String): List<HistoryEntity> =
        if (query.isBlank()) emptyList() else historyDao.search(query.trim())

    suspend fun clearHistory() = historyDao.clear()

    suspend fun removeHistoryEntry(url: String) = historyDao.deleteByUrl(url)

    private companion object {
        const val MAX_HISTORY_ENTRIES = 2000
    }
}
