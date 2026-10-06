package com.cyberpulse.app.domain

import com.cyberpulse.app.data.db.Article

enum class TopicKind(val label: String) {
    /** Whole-word phrase anywhere in the title or summary. */
    KEYWORD("keyword"),
    /** CVE records for one product, as named in their description. */
    PRODUCT("product"),
    CATEGORY("category"),
    SOURCE("source"),
}

data class Topic(val kind: TopicKind, val value: String) {
    val display: String
        get() = when (kind) {
            TopicKind.CATEGORY -> NewsCategory.entries.firstOrNull { it.name == value }?.label ?: value
            else -> value
        }

    /** Identity for de-duplication; keywords and products are case-insensitive. */
    val key: String get() = "${kind.name}:${value.lowercase()}"
}

/** A topic the user flagged or suppressed. Only [WatchLevel.FLAGGED] and [WatchLevel.SUPPRESSED] are stored. */
data class TopicRule(val topic: Topic, val level: WatchLevel)

class TopicRules(val rules: List<TopicRule>) {
    private val flagged = rules.filter { it.level == WatchLevel.FLAGGED }.map { it.topic }
    private val suppressed = rules.filter { it.level == WatchLevel.SUPPRESSED }.map { it.topic }

    val isEmpty: Boolean get() = rules.isEmpty()
    val flaggedCount: Int get() = flagged.size
    val suppressedCount: Int get() = suppressed.size

    fun levelOf(topic: Topic): WatchLevel =
        rules.firstOrNull { it.topic.key == topic.key }?.level ?: WatchLevel.DEFAULT

    fun flaggedMatches(article: Article): List<Topic> = flagged.filter { TopicMatcher.matches(it, article) }

    fun isSuppressed(article: Article): Boolean = suppressed.any { TopicMatcher.matches(it, article) }

    companion object {
        val EMPTY = TopicRules(emptyList())
    }
}

object TopicMatcher {
    private val keywordCache = HashMap<String, Regex>()

    private fun keywordRegex(phrase: String): Regex = synchronized(keywordCache) {
        keywordCache.getOrPut(phrase.lowercase()) {
            Regex("(?<![\\w-])" + Regex.escape(phrase.trim()) + "(?![\\w-])", RegexOption.IGNORE_CASE)
        }
    }

    fun matches(topic: Topic, article: Article): Boolean = when (topic.kind) {
        TopicKind.KEYWORD -> keywordRegex(topic.value).let { it.containsMatchIn(article.title) || it.containsMatchIn(article.summary) }
        TopicKind.PRODUCT -> article.kind == ItemKind.CVE &&
            BriefingBuilder.productOf(article.title)?.equals(topic.value, ignoreCase = true) == true
        TopicKind.CATEGORY -> article.category.name == topic.value
        TopicKind.SOURCE -> article.source == topic.value
    }

    private val NAME = Regex("[A-Za-z][A-Za-z0-9]*(?:[-.][A-Za-z0-9]+)*")
    private val GENERIC = setOf(
        "about", "across", "actively", "actor", "actors", "after", "against", "alert", "alerts", "allows", "amid",
        "attack", "attackers", "attacks", "authentication", "backdoor", "breach", "breaches", "campaign", "code",
        "companies", "company", "confirms", "critical", "customer", "customers", "cyber", "cyberattack", "data",
        "details", "developer", "disrupts", "execution", "exploit", "exploited", "exploits", "exposed", "fake",
        "files", "fixes", "flaw", "flaws", "from", "hackers", "high", "impacted", "into", "known", "leak",
        "leaks", "lets", "linked", "malicious", "malware", "million", "more", "most", "multiple", "network",
        "news", "over", "patch", "patches", "phishing", "products", "ransomware", "read", "remote", "report",
        "researchers", "says", "security", "service", "services", "spread", "steal", "stealer", "systems",
        "targeted", "targets", "than", "that", "their", "these", "this", "threat", "tool", "tools", "turns",
        "unauthenticated", "update", "updates", "users", "uses", "using", "versions", "vulnerabilities",
        "vulnerability", "warns", "weekly", "what", "when", "which", "with", "zero-day", "zero-days",
    )

    /**
     * Distinctive names in a headline worth following or muting, e.g. "ShinyHunters", "NetScaler", "ASOS".
     * Names with inner capitals, digits or all caps rank first; generic security words are skipped.
     */
    fun suggestKeywords(title: String, limit: Int = 3): List<String> {
        val systemWords = SystemType.entries.flatMap { it.keywords }.toSet()
        return NAME.findAll(title)
            .map { it.value.trimEnd('.') }
            .filter { it.length >= 4 && it[0].isUpperCase() }
            .filter { it.lowercase() !in GENERIC && it.lowercase() !in systemWords }
            .distinctBy { it.lowercase() }
            .sortedByDescending { word -> if (word.drop(1).any { it.isUpperCase() || it.isDigit() }) 1 else 0 }
            .take(limit)
            .toList()
    }
}
