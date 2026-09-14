package com.ampgames.vidsaver.domain.download

/** Lifecycle of a download. Stored by name so the DB stays readable. */
enum class DownloadStatus {
    /** Waiting for a concurrency slot or for the network conditions to be met. */
    QUEUED,

    RUNNING,

    /** Paused by the user. Resuming continues from the bytes already on disk. */
    PAUSED,

    /** Failed and scheduled for an automatic retry. */
    RETRY_SCHEDULED,

    /** Failed permanently, or the retry budget ran out. */
    FAILED,

    COMPLETED,

    CANCELLED,
    ;

    val isActive: Boolean
        get() = this == QUEUED || this == RUNNING || this == RETRY_SCHEDULED

    val isTerminal: Boolean
        get() = this == COMPLETED || this == CANCELLED || this == FAILED

    /** Whether the user can start or restart this download. */
    val isResumable: Boolean
        get() = this == PAUSED || this == FAILED || this == RETRY_SCHEDULED

    companion object {
        fun fromName(name: String?): DownloadStatus =
            entries.firstOrNull { it.name == name } ?: QUEUED
    }
}

/**
 * Why a download failed, kept separate from the message so the UI can decide
 * what to offer (retry, or an explanation with no retry).
 */
enum class DownloadFailure {
    NONE,
    NETWORK,
    HTTP,

    /** The stream is access-controlled; retrying will not help. */
    ENCRYPTED,

    LIVE_STREAM,
    STORAGE,
    UNSUPPORTED,
    UNKNOWN,
    ;

    /** Retrying a protected or live stream is pointless, so we never do. */
    val isRetryable: Boolean
        get() = this == NETWORK || this == HTTP || this == UNKNOWN

    companion object {
        fun fromName(name: String?): DownloadFailure =
            entries.firstOrNull { it.name == name } ?: NONE
    }
}
