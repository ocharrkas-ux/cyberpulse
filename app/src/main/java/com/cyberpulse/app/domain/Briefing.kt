package com.cyberpulse.app.domain

import com.cyberpulse.app.data.db.Article
import java.util.concurrent.TimeUnit

/** One story in the briefing, possibly merged from several articles/records about the same thing. */
data class BriefingItem(
    val key: String,
    val title: String,
    val summary: String,
    val link: String,
    val sources: List<String>,
    val category: NewsCategory,
    val cveIds: List<String>,
    val severity: Severity?,
    val cvssScore: Double?,
    val knownExploited: Boolean,
    val systemTypes: Set<SystemType>,
    val flagged: Boolean,
    val latestAt: Long,
    /** At least one member is a vulnerability item (CVE record or vulnerability news). */
    val isVulnerability: Boolean,
    /** At least one member is a news article or advisory, not only a raw CVE record. */
    val hasCoverage: Boolean,
    /** For a group of CVE records in one product: the product name. */
    val product: String? = null,
    /** Flagged topics this item matches. */
    val followedTopics: List<Topic> = emptyList(),
)

enum class SectionKind { YOUR_SYSTEMS, FOLLOWED_TOPICS, EXPLOITED, CRITICAL, TOP_STORIES, CATEGORY }

data class BriefingSection(
    val kind: SectionKind,
    val title: String,
    val items: List<BriefingItem>,
    val category: NewsCategory? = null,
    /** Matching items left out of this section for length. */
    val more: Int = 0,
)

data class BriefingStats(
    val newsStories: Int,
    val newCves: Int,
    val critical: Int,
    val exploited: Int,
    val affectingYou: Int,
    val watchingAny: Boolean,
)

data class Briefing(
    val generatedAt: Long,
    val windowStart: Long,
    val stats: BriefingStats,
    val sections: List<BriefingSection>,
) {
    val isEmpty: Boolean get() = sections.isEmpty()
}

object BriefingBuilder {
    val WINDOW_MILLIS = TimeUnit.HOURS.toMillis(24)

    private const val MAX_YOURS = 5
    private const val MAX_FOLLOWED = 5
    private const val MAX_EXPLOITED = 4
    private const val MAX_CRITICAL = 3
    private const val MAX_TOP = 5
    private const val MAX_PER_CATEGORY = 2

    fun build(
        articles: List<Article>,
        watch: Map<SystemType, WatchLevel>,
        now: Long = System.currentTimeMillis(),
        windowMillis: Long = WINDOW_MILLIS,
        topics: TopicRules = TopicRules.EMPTY,
    ): Briefing {
        val windowStart = now - windowMillis
        val recent = articles.filter { it.publishedAt in windowStart..now }
        // Specific beats broad: a suppressed topic (e.g. one product) hides an item even on a flagged
        // system type; only a flagged topic overrides it. System suppression applies to vulnerabilities.
        val visible = recent.filterNot { a ->
            topics.flaggedMatches(a).isEmpty() && (topics.isSuppressed(a) ||
                (a.category == NewsCategory.VULNERABILITIES && VulnPolicy.isSuppressed(a, watch)))
        }
        val items = cluster(visible).map { toItem(it, watch, topics) }

        val used = hashSetOf<String>()
        val sections = mutableListOf<BriefingSection>()

        /** Takes up to [limit] unclaimed items into a new section; the rest are counted as "more". */
        fun section(kind: SectionKind, title: String, candidates: List<BriefingItem>, limit: Int, category: NewsCategory? = null) {
            val available = candidates.filter { it.key !in used }
            val picked = available.take(limit)
            if (picked.isEmpty()) return
            picked.forEach { used += it.key }
            sections += BriefingSection(kind, title, picked, category, more = available.size - picked.size)
        }

        val vulns = items.filter { it.isVulnerability }.sortedWith(VULN_RANK)
        section(SectionKind.YOUR_SYSTEMS, "Affecting your systems", vulns.filter { it.flagged }, MAX_YOURS)
        section(
            SectionKind.FOLLOWED_TOPICS, "Topics you follow",
            items.filter { it.followedTopics.isNotEmpty() }.sortedWith(STORY_RANK), MAX_FOLLOWED,
        )
        section(SectionKind.EXPLOITED, "Actively exploited", vulns.filter { it.knownExploited }, MAX_EXPLOITED)
        section(SectionKind.CRITICAL, "Critical vulnerabilities", vulns.filter { it.severity == Severity.CRITICAL }, MAX_CRITICAL)

        val stories = items.filter { it.hasCoverage }.sortedWith(STORY_RANK)
        section(SectionKind.TOP_STORIES, "Top stories", stories, MAX_TOP)
        for (category in NewsCategory.entries) {
            if (category == NewsCategory.GENERAL) continue
            section(SectionKind.CATEGORY, category.label, stories.filter { it.category == category }, MAX_PER_CATEGORY, category)
        }

        val stats = BriefingStats(
            newsStories = items.count { it.hasCoverage },
            newCves = recent.count { it.kind == ItemKind.CVE },
            critical = vulns.count { it.severity == Severity.CRITICAL },
            exploited = vulns.count { it.knownExploited },
            affectingYou = vulns.count { it.flagged },
            watchingAny = watch.values.any { it == WatchLevel.FLAGGED },
        )
        return Briefing(now, windowStart, stats, sections)
    }

    private val VULN_RANK = compareByDescending<BriefingItem> { it.knownExploited }
        .thenByDescending { it.cvssScore ?: severityFloor(it.severity) }
        .thenByDescending { it.sources.size }
        .thenByDescending { it.latestAt }

    private val STORY_RANK = compareByDescending<BriefingItem> { it.sources.size }
        .thenByDescending { it.knownExploited }
        .thenByDescending { it.latestAt }

    private fun severityFloor(s: Severity?) = when (s) {
        Severity.CRITICAL -> 9.0
        Severity.HIGH -> 7.0
        Severity.MEDIUM -> 4.0
        Severity.LOW -> 0.1
        null -> 0.0
    }

    // ---- clustering: merge coverage of the same story across sources ----

    private val WORD = Regex("[a-z0-9][a-z0-9-]*")
    private val STOP_WORDS = setOf(
        "about", "after", "against", "amid", "been", "could", "from", "have", "into", "more", "over", "said",
        "says", "than", "that", "their", "these", "they", "this", "those", "what", "when", "which", "while",
        "will", "with", "would", "your", "new", "warns", "attack", "attacks", "attackers", "hackers", "flaw",
        "flaws", "vulnerability", "vulnerabilities", "security", "exploited", "exploit", "critical", "patch",
        "patches", "update", "updates", "data", "users", "million",
    )

    private fun titleTokens(a: Article): Set<String> =
        WORD.findAll(a.title.lowercase()).map { it.value.trim('-') }
            .filter { it.length >= 4 && it !in STOP_WORDS }
            .toSet()

    /** Roundups that cite many CVEs ("13 more stories…") shouldn't swallow every CVE they mention. */
    private const val MAX_LINKING_CVES = 3

    private class Cluster(val members: MutableList<Article>, var headline: Set<String>)

    private fun similarHeadlines(a: Set<String>, b: Set<String>): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        val shared = a.count { it in b }
        return shared >= 3 && shared * 2 >= minOf(a.size, b.size)
    }

    /**
     * Groups coverage of the same story. Two items join when they cite the same CVE, or when two
     * news headlines overlap strongly. CVE records only ever join by ID: their descriptions are
     * boilerplate ("versions prior to…") and would otherwise chain unrelated bugs together.
     * Headlines are compared against each cluster's lead headline, never transitively.
     */
    internal fun cluster(articles: List<Article>): List<List<Article>> {
        val clusters = mutableListOf<Cluster>()
        val byCve = hashMapOf<String, Cluster>()
        val byProduct = hashMapOf<String, Cluster>()
        for (article in articles.sortedBy { it.publishedAt }) {
            val linkingCves = article.cveIds.takeIf { it.size <= MAX_LINKING_CVES }.orEmpty()
            val tokens = if (article.kind == ItemKind.CVE) emptySet() else titleTokens(article)
            val product = if (article.kind == ItemKind.CVE) productOf(article.title)?.lowercase() else null

            val home = linkingCves.firstNotNullOfOrNull { byCve[it] }
                ?: product?.let { byProduct[it] }
                ?: clusters.firstOrNull { similarHeadlines(it.headline, tokens) }
            val target = home ?: Cluster(mutableListOf(), emptySet()).also { clusters += it }
            target.members += article
            if (target.headline.isEmpty()) target.headline = tokens
            linkingCves.forEach { byCve.putIfAbsent(it, target) }
            product?.let { byProduct.putIfAbsent(it, target) }
        }
        return clusters.map { it.members }
    }

    private val FINDING_PREFIX = Regex(
        "^(?:an?|multiple)\\s+(?:[\\w-]+\\s+){0,3}(?:was|were|has been|have been)\\s+[\\w-]+\\s+in\\s+(?:the\\s+)?",
        RegexOption.IGNORE_CASE,
    )
    private val PRODUCT_END = Regex(
        "\\s*(?:[,(:]|<=|\\s(?:v?\\d|versions?\\b|before\\b|through\\b|up to\\b|prior to\\b|is\\b|are\\b|" +
            "contains?\\b|contain\\(s\\)|allows?\\b|has\\b|have\\b|could\\b|may\\b|via\\b|due to\\b|[0-9a-f]{7,}\\b))",
        RegexOption.IGNORE_CASE,
    )
    private val NOT_PRODUCTS = setOf("a", "an", "the", "this", "multiple", "an issue", "a flaw", "a vulnerability")

    /** Best-effort product name from a CVE headline, e.g. "Dell Container Storage Modules, versions…" -> "Dell Container Storage Modules". */
    fun productOf(cveTitle: String): String? {
        val rest = FINDING_PREFIX.replace(cveTitle.removeSuffix("…"), "")
        val end = PRODUCT_END.find(rest)?.range?.first ?: return null
        val product = rest.substring(0, end).trim()
        return product.takeIf { it.length >= 4 && it.split(' ').size <= 8 && it.lowercase() !in NOT_PRODUCTS }
    }

    private fun toItem(members: List<Article>, watch: Map<SystemType, WatchLevel>, topics: TopicRules): BriefingItem {
        // Prefer a human-written headline over a raw CVE description.
        val lead = members.firstOrNull { it.kind == ItemKind.NEWS }
            ?: members.firstOrNull { it.kind == ItemKind.ADVISORY }
            ?: members.maxBy { it.cvssScore ?: 0.0 }
        val productGroup = members.size > 1 && members.all { it.kind == ItemKind.CVE }
        val product = if (productGroup) productOf(lead.title) else null
        val systems = members.flatMapTo(linkedSetOf()) { it.systemTypes }
        val score = members.mapNotNull { it.cvssScore }.maxOrNull()
        val severity = members.mapNotNull { it.severity }.maxOrNull()
        return BriefingItem(
            key = lead.id,
            title = if (product != null) "$product: ${members.size} vulnerabilities" else lead.title,
            summary = if (product != null) "Worst: ${lead.title}" else bodyWithoutHeadline(lead.title, lead.summary, lead.kind == ItemKind.CVE),
            link = lead.link,
            sources = members.map { it.source }.distinct(),
            category = if (members.any { it.category == NewsCategory.VULNERABILITIES }) NewsCategory.VULNERABILITIES else lead.category,
            cveIds = members.flatMap { it.cveIds }.distinct(),
            severity = severity,
            cvssScore = score,
            knownExploited = members.any { it.knownExploited },
            systemTypes = systems,
            flagged = systems.any { watch[it] == WatchLevel.FLAGGED },
            latestAt = members.maxOf { it.publishedAt },
            isVulnerability = members.any { it.category == NewsCategory.VULNERABILITIES },
            hasCoverage = members.any { it.kind != ItemKind.CVE },
            product = product,
            followedTopics = if (topics.isEmpty) emptyList() else members.flatMap { topics.flaggedMatches(it) }.distinctBy { it.key },
        )
    }
}

private val SENTENCE_BREAK = Regex("(?<=[.!?])\\s+")

/** CVE titles are the description's first sentence; returns the summary without repeating it. */
fun bodyWithoutHeadline(title: String, summary: String, isCve: Boolean): String {
    if (summary == title) return ""
    val headline = title.removeSuffix("…")
    return if (isCve && headline.length >= 20 && summary.startsWith(headline)) {
        summary.split(SENTENCE_BREAK, limit = 2).getOrNull(1).orEmpty()
    } else {
        summary
    }
}
