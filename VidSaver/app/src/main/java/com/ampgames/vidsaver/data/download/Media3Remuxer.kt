package com.ampgames.vidsaver.data.download

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.ampgames.vidsaver.di.MainDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Remuxes a concatenated MPEG-TS stream into MP4 with Media3's [Transformer].
 *
 * This is a container rewrite, not a re-encode: the elementary streams are
 * copied across, so it is fast and lossless. Transformer needs a Looper, hence
 * the hop to the main dispatcher.
 */
@UnstableApi
class Media3Remuxer @Inject constructor(
    @ApplicationContext private val context: Context,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) : Remuxer {

    override suspend fun remux(source: File, target: File): Result<File> =
        withContext(mainDispatcher) {
            runCatching {
                target.parentFile?.mkdirs()
                if (target.exists()) target.delete()

                val result = suspendCancellableCoroutine { continuation ->
                    val transformer = Transformer.Builder(context)
                        .addListener(
                            object : Transformer.Listener {
                                override fun onCompleted(
                                    composition: Composition,
                                    exportResult: ExportResult,
                                ) {
                                    if (continuation.isActive) continuation.resume(null)
                                }

                                override fun onError(
                                    composition: Composition,
                                    exportResult: ExportResult,
                                    exportException: ExportException,
                                ) {
                                    if (continuation.isActive) continuation.resume(exportException)
                                }
                            },
                        )
                        .build()

                    continuation.invokeOnCancellation {
                        runCatching { transformer.cancel() }
                    }

                    transformer.start(
                        MediaItem.fromUri(android.net.Uri.fromFile(source)),
                        target.absolutePath,
                    )
                }

                if (result != null) throw result
                if (!target.exists() || target.length() == 0L) {
                    error("Transformer reported success but produced no output")
                }

                source.delete()
                Timber.d("Remuxed %s -> %s", source.name, target.name)
                target
            }
        }
}
