package com.cyberpulse.app.data

import com.cyberpulse.app.data.db.Article
import com.cyberpulse.app.data.db.ArticleDao
import com.cyberpulse.app.data.remote.CveRecord
import com.cyberpulse.app.data.remote.FEEDS
import com.cyberpulse.app.data.remote.FeedSource
import com.cyberpulse.app.data.remote.Http
import com.cyberpulse.app.data.remote.KevClient
import com.cyberpulse.app.data.remote.KevEntry
import com.cyberpulse.app.data.remote.NvdClient
import com.cyberpulse.app.data.remote.RssParser
import com.cyberpulse.app.data.remote.canonicalLink
import com.cyberpulse.app.domain.Classifier
import com.cyberpulse.app.domain.ItemKind
import com.cyberpulse.app.domain.NewsCategory
import com.cyberpulse.app.domain.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class RefreshResult(
    /** Items that weren't in the database before this refresh (empty on the very first sync). */
    val newItems: List<Article>,
    val failedSources: List<String>,
)

class NewsRepository(
    private val dao: ArticleDao,
    private val settings: Settings,
    http: Http = Http(),
) {
    private val nvd = NvdClient(http)
    private val kev = KevClient(http)
    private val feedHttp = http
    private val mutex = Mutex()

    fun observeArticles(): Flow<List<Article>> = dao.observeAll()

    suspend fun markRead(id: String) = dao.markRead(id)

    suspend fun refresh(): RefreshResult = mutex.withLock {
        coroutineScope {
            val now = System.currentTimeMillis()
            val feedJobs = FEEDS.map { source -> async { source to runCatching { fetchFeed(source, now) } } }
            val nvdJob = async { runCatching { nvd.fetchRecent(days = NVD_LOOKBACK_DAYS) } }
            val kevJob = async { runCatching { kev.fetch() } }

            val failed = mutableListOf<String>()
            val feedItems = feedJobs.awaitAll().flatMap { (source, result) ->
                result.getOrElse { failed += source.name; emptyList() }
            }
            val cves = nvdJob.await().getOrElse { failed += "NVD"; emptyList() }
            val kevEntries = kevJob.await().getOrElse { failed += "CISA KEV"; emptyList() }

            withContext(Dispatchers.Default) {
                val kevIds = kevEntries.mapTo(hashSetOf()) { it.cveId }
                val byId = linkedMapOf<String, Article>()

                for (item in feedItems) {
                    byId.putIfAbsent(item.id, item.copy(knownExploited = item.cveIds.any { it in kevIds }))
                }
                for (cve in cves) byId[cve.id] = cveArticle(cve, now, cve.inKev || cve.id in kevIds)

                val recentKevCutoff = now - TimeUnit.DAYS.toMillis(KEV_LOOKBACK_DAYS)
                for (entry in kevEntries) {
                    if (entry.dateAdded < recentKevCutoff) continue
                    val existing = byId[entry.cveId]
                    byId[entry.cveId] = existing?.copy(title = entry.name.ifBlank { existing.title }, knownExploited = true)
                        ?: kevArticle(entry, now)
                }

                val retentionCutoff = now - TimeUnit.DAYS.toMillis(RETENTION_DAYS)
                val incoming = byId.values.filter { it.publishedAt >= retentionCutoff }
                persist(incoming, failed, retentionCutoff)
            }.also { settings.lastRefresh = now }
        }
    }

    private suspend fun persist(incoming: List<Article>, failed: List<String>, retentionCutoff: Long): RefreshResult {
        val firstSync = dao.count() == 0
        val existing = incoming.map { it.id }.chunked(500)
            .flatMap { dao.getByIds(it) }
            .associateBy { it.id }

        val merged = incoming.map { new ->
            val old = existing[new.id] ?: return@map new
            new.copy(
                isRead = old.isRead,
                fetchedAt = old.fetchedAt,
                publishedAt = minOf(old.publishedAt, new.publishedAt),
                cvssScore = new.cvssScore ?: old.cvssScore,
                severity = new.severity ?: old.severity,
                knownExploited = new.knownExploited || old.knownExploited,
                systemTypes = new.systemTypes.ifEmpty { old.systemTypes },
            )
        }
        merged.chunked(500).forEach { dao.upsertAll(it) }
        dao.deleteOlderThan(retentionCutoff)

        // "New" also covers a CVE we already had that has just been added to CISA KEV.
        val newItems = if (firstSync) emptyList() else merged.filter { item ->
            val old = existing[item.id]
            old == null || (item.knownExploited && !old.knownExploited)
        }
        return RefreshResult(newItems, failed)
    }

    private suspend fun fetchFeed(source: FeedSource, now: Long): List<Article> {
        val entries = RssParser.parse(feedHttp.get(source.url))
        return entries.map { e ->
            val text = "${e.title} ${e.summary}"
            val cves = Classifier.extractCves(text)
            val category = Classifier.category(e.title, e.summary, e.tags, source.categoryHint)
            val score = Classifier.extractCvss(text)
            val link = canonicalLink(e.link)
            Article(
                id = link,
                kind = source.kind,
                title = e.title,
                summary = e.summary,
                link = e.link.trim(),
                source = source.name,
                publishedAt = e.publishedAt?.coerceAtMost(now) ?: now,
                fetchedAt = now,
                category = category,
                systemTypes = Classifier.systemTypes("$text ${e.tags.joinToString(" ")}"),
                cveIds = cves,
                cvssScore = score,
                severity = Classifier.severityFromText(text),
                knownExploited = false,
            )
        }
    }

    private fun cveArticle(cve: CveRecord, now: Long, exploited: Boolean) = Article(
        id = cve.id,
        kind = ItemKind.CVE,
        title = headline(cve.description).ifBlank { cve.id },
        summary = cve.description,
        link = "https://nvd.nist.gov/vuln/detail/${cve.id}",
        source = "NVD",
        publishedAt = cve.publishedAt.coerceAtMost(now),
        fetchedAt = now,
        category = NewsCategory.VULNERABILITIES,
        systemTypes = Classifier.systemTypes(cve.description, cve.products),
        cveIds = listOf(cve.id),
        cvssScore = cve.cvssScore,
        severity = Severity.fromScore(cve.cvssScore),
        knownExploited = exploited,
    )

    private fun kevArticle(entry: KevEntry, now: Long) = Article(
        id = entry.cveId,
        kind = ItemKind.CVE,
        title = entry.name.ifBlank { "${entry.vendor} ${entry.product}" },
        summary = buildString {
            append(entry.description)
            if (entry.ransomware) append(" Known to be used in ransomware campaigns.")
        },
        link = "https://nvd.nist.gov/vuln/detail/${entry.cveId}",
        source = "CISA KEV",
        publishedAt = entry.dateAdded.coerceAtMost(now),
        fetchedAt = now,
        category = NewsCategory.VULNERABILITIES,
        systemTypes = Classifier.systemTypes(
            "${entry.vendor} ${entry.product} ${entry.name} ${entry.description}",
            listOf("${entry.vendor}:${entry.product}"),
        ),
        cveIds = listOf(entry.cveId),
        cvssScore = null,
        severity = null,
        knownExploited = true,
    )

    /** First sentence of a CVE description, shortened for display as a title. */
    private fun headline(description: String): String {
        val firstSentence = description.split(Regex("(?<=[.!?])\\s+"), limit = 2).first().trim()
        return if (firstSentence.length <= 140) firstSentence
        else firstSentence.take(140).substringBeforeLast(' ') + "…"
    }

    private companion object {
        const val NVD_LOOKBACK_DAYS = 3L
        const val KEV_LOOKBACK_DAYS = 30L
        const val RETENTION_DAYS = 30L
    }
}
