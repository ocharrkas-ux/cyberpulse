package com.cyberpulse.app.domain

import com.cyberpulse.app.data.db.Article

/**
 * The single place that decides how flag/suppress preferences apply.
 *
 * - An item is *flagged* if any of its system types is flagged.
 * - An item is *suppressed* by system types only if it has some and all of them are suppressed,
 *   so "Chrome on Windows" stays visible if you suppress Windows but not browsers.
 * - A flagged system type beats a suppressed system type.
 * - A suppressed topic (product, keyword, category, source) is more specific, so it beats a
 *   flagged system type; only a flagged topic overrides it.
 */
object VulnPolicy {
    fun isFlagged(item: Article, watch: Map<SystemType, WatchLevel>): Boolean =
        item.systemTypes.any { watch[it] == WatchLevel.FLAGGED }

    fun isSuppressed(
        item: Article,
        watch: Map<SystemType, WatchLevel>,
        topics: TopicRules = TopicRules.EMPTY,
    ): Boolean {
        if (!topics.isEmpty && topics.flaggedMatches(item).isNotEmpty()) return false
        if (!topics.isEmpty && topics.isSuppressed(item)) return true
        return !isFlagged(item, watch) &&
            item.systemTypes.isNotEmpty() &&
            item.systemTypes.all { watch[it] == WatchLevel.SUPPRESSED }
    }

    fun meetsSeverity(item: Article, minSeverity: Severity?): Boolean =
        minSeverity == null || item.knownExploited || (item.severity != null && item.severity >= minSeverity)

    fun shouldNotify(
        item: Article,
        watch: Map<SystemType, WatchLevel>,
        mode: NotifyMode,
        minSeverity: Severity?,
        topics: TopicRules = TopicRules.EMPTY,
    ): Boolean {
        if (item.category != NewsCategory.VULNERABILITIES) return false
        if (!meetsSeverity(item, minSeverity)) return false
        if (isSuppressed(item, watch, topics)) return false
        return when (mode) {
            NotifyMode.FLAGGED_ONLY -> isFlagged(item, watch)
            NotifyMode.ALL_EXCEPT_SUPPRESSED -> true
        }
    }
}
