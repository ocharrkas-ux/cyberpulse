package com.cyberpulse.app.domain

import com.cyberpulse.app.data.db.Article

/**
 * The single place that decides how flag/suppress preferences apply.
 *
 * - An item is *flagged* if any of its system types is flagged.
 * - An item is *suppressed* only if it has system types and all of them are suppressed,
 *   so "Chrome on Windows" stays visible if you suppress Windows but not browsers.
 * - Flagged beats suppressed.
 */
object VulnPolicy {
    fun isFlagged(item: Article, watch: Map<SystemType, WatchLevel>): Boolean =
        item.systemTypes.any { watch[it] == WatchLevel.FLAGGED }

    fun isSuppressed(item: Article, watch: Map<SystemType, WatchLevel>): Boolean =
        !isFlagged(item, watch) &&
            item.systemTypes.isNotEmpty() &&
            item.systemTypes.all { watch[it] == WatchLevel.SUPPRESSED }

    fun meetsSeverity(item: Article, minSeverity: Severity?): Boolean =
        minSeverity == null || item.knownExploited || (item.severity != null && item.severity >= minSeverity)

    fun shouldNotify(
        item: Article,
        watch: Map<SystemType, WatchLevel>,
        mode: NotifyMode,
        minSeverity: Severity?,
    ): Boolean {
        if (item.category != NewsCategory.VULNERABILITIES) return false
        if (!meetsSeverity(item, minSeverity)) return false
        return when (mode) {
            NotifyMode.FLAGGED_ONLY -> isFlagged(item, watch)
            NotifyMode.ALL_EXCEPT_SUPPRESSED -> !isSuppressed(item, watch)
        }
    }
}
