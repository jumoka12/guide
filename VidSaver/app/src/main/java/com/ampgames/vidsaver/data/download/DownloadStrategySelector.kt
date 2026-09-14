package com.ampgames.vidsaver.data.download

import com.ampgames.vidsaver.domain.media.MediaCandidate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks the strategy for a candidate. Keeping the choice here means the download
 * engine (Phase 3) never needs to know which strategies exist — adding one is a
 * `@Binds @IntoSet` line.
 */
@Singleton
class DownloadStrategySelector @Inject constructor(
    private val strategies: Set<@JvmSuppressWildcards DownloadStrategy>,
) {

    fun strategyFor(candidate: MediaCandidate): DownloadStrategy? =
        strategies.firstOrNull { it.canHandle(candidate) }

    fun requireStrategyFor(candidate: MediaCandidate): DownloadStrategy =
        strategyFor(candidate)
            ?: throw DownloadError.Unsupported("No download strategy for ${candidate.type}")
}
