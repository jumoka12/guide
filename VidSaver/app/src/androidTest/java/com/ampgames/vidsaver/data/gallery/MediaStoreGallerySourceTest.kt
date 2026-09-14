package com.ampgames.vidsaver.data.gallery

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ampgames.vidsaver.data.download.MediaStorePublisher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settles the riskiest assumption in the gallery: that the `RELATIVE_PATH LIKE`
 * query actually matches what [MediaStorePublisher] writes.
 *
 * If the trailing-separator convention is wrong, downloads save correctly and the
 * grid stays empty forever — a failure that looks like a UI bug and isn't. Only a
 * real MediaStore can answer it, so this runs on a device.
 *
 * Scoped to API 29+. Below that MediaStore needs WRITE_EXTERNAL_STORAGE and a
 * real file on shared storage, which is a different code path and a different
 * test.
 */
@RunWith(AndroidJUnit4::class)
class MediaStoreGallerySourceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver
    private val source = MediaStoreGallerySource(context, Dispatchers.IO)

    /** Everything this test inserted, removed on the way out. */
    private val inserted = mutableListOf<Uri>()

    private val marker = "vidsaver-test-${System.currentTimeMillis()}"

    @Before
    fun setUp() {
        assumeTrue(
            "Scoped storage only; the legacy path needs its own test",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
        )
    }

    @After
    fun tearDown() {
        inserted.forEach { uri -> runCatching { resolver.delete(uri, null, null) } }
    }

    /**
     * Writes a file into the app's folder exactly the way MediaStorePublisher
     * does. Content is a few bytes, not real video: MediaStore stores what it is
     * given, and the query reads the database row, not the file.
     */
    private fun insertVideo(displayName: String, relativePath: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            values,
        ) ?: error("MediaStore refused to create $displayName")
        inserted += uri

        resolver.openOutputStream(uri)?.use { it.write(ByteArray(2048)) }
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
            null,
            null,
        )
        return uri
    }

    @Test
    fun aVideoInTheAppFolderIsFoundByTheQuery() = runBlocking {
        val name = "$marker-found.mp4"
        insertVideo(name, MediaStorePublisher.RELATIVE_PATH)

        val found = source.query().firstOrNull { it.displayName == name }

        assertNotNull(
            "RELATIVE_PATH matching is broken: the publisher writes to " +
                "${MediaStorePublisher.RELATIVE_PATH} but the query did not find it",
            found,
        )
        assertEquals(2048L, found!!.sizeBytes)
        assertTrue("uri should be addressable", found.uri.startsWith("content://"))
    }

    /** The gallery must show our folder, not the user's whole video library. */
    @Test
    fun aVideoOutsideTheAppFolderIsIgnored() = runBlocking {
        val ours = "$marker-ours.mp4"
        val theirs = "$marker-theirs.mp4"
        insertVideo(ours, MediaStorePublisher.RELATIVE_PATH)
        insertVideo(theirs, android.os.Environment.DIRECTORY_MOVIES)

        val names = source.query().map { it.displayName }

        assertTrue("our own video must appear", names.contains(ours))
        assertFalse("a video outside the app folder must not appear", names.contains(theirs))
    }

    @Test
    fun severalVideosAllComeBack() = runBlocking {
        val names = (1..3).map { "$marker-multi-$it.mp4" }
        names.forEach { insertVideo(it, MediaStorePublisher.RELATIVE_PATH) }

        val found = source.query().map { it.displayName }.toSet()

        assertTrue("expected all of $names, got $found", found.containsAll(names))
    }

    @Test
    fun renameChangesTheNameInPlace() = runBlocking {
        val original = "$marker-before.mp4"
        val renamed = "$marker-after.mp4"
        insertVideo(original, MediaStorePublisher.RELATIVE_PATH)

        val video = source.query().first { it.displayName == original }
        val result = source.rename(video, renamed)

        assertTrue("rename failed: ${result.exceptionOrNull()}", result.isSuccess)
        val names = source.query().map { it.displayName }
        assertTrue(names.contains(renamed))
        assertFalse(names.contains(original))
    }

    @Test
    fun deleteRemovesTheVideo() = runBlocking {
        val name = "$marker-delete.mp4"
        insertVideo(name, MediaStorePublisher.RELATIVE_PATH)

        val video = source.query().first { it.displayName == name }
        val result = source.delete(video)

        assertTrue("delete failed: ${result.exceptionOrNull()}", result.isSuccess)
        assertFalse(source.query().any { it.displayName == name })
    }

    @Test
    fun anEmptyFolderQueriesCleanlyRatherThanThrowing() = runBlocking {
        // Nothing inserted yet in this test; the query must still succeed.
        val videos = source.query()
        assertNotNull(videos)
    }
}
