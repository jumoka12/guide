package com.ampgames.vidsaver.data.download

import java.io.File

/**
 * Rewrites a downloaded stream into a playable container without re-encoding.
 *
 * Behind an interface so [HlsDownloadStrategy] — the part with the interesting
 * logic — can be unit-tested on the JVM without Media3 or an Android runtime.
 */
interface Remuxer {

    /**
     * @return the produced file on success. On failure the caller keeps the raw
     *   concatenated stream, so a remux failure degrades quality-of-life, never
     *   loses the download.
     */
    suspend fun remux(source: File, target: File): Result<File>
}

/**
 * Fallback used when no remuxer is available: moves the concatenated stream into
 * place unchanged. The result is a valid MPEG-TS file, which plays in the
 * in-app player and most others, just with a less convenient container.
 */
class PassthroughRemuxer : Remuxer {
    override suspend fun remux(source: File, target: File): Result<File> = runCatching {
        if (source.absolutePath == target.absolutePath) return@runCatching target
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
        target
    }
}
