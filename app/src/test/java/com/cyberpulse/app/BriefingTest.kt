package com.cyberpulse.app

import com.cyberpulse.app.data.db.Article
import com.cyberpulse.app.domain.BriefingBuilder
import com.cyberpulse.app.domain.BriefingScript
import com.cyberpulse.app.domain.ItemKind
import com.cyberpulse.app.domain.NewsCategory
import com.cyberpulse.app.domain.SectionKind
import com.cyberpulse.app.domain.Severity
import com.cyberpulse.app.domain.SystemType
import com.cyberpulse.app.domain.WatchLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

class BriefingTest {
    // Tue 2026-10-06 08:00 UTC
    private val now = 1_791_273_600_000L
    private val hour = TimeUnit.HOURS.toMillis(1)

    private fun article(
        id: String,
        title: String,
        source: String = "Src",
        kind: ItemKind = ItemKind.NEWS,
        category: NewsCategory = NewsCategory.GENERAL,
        systems: Set<SystemType> = emptySet(),
        cves: List<String> = emptyList(),
        severity: Severity? = null,
        score: Double? = null,
        kev: Boolean = false,
        ageHours: Long = 1,
        summary: String = "",
    ) = Article(
        id = id, kind = kind, title = title, summary = summary, link = "https://x/$id", source = source,
        publishedAt = now - ageHours * hour, fetchedAt = now, category = category, systemTypes = systems,
        cveIds = cves, cvssScore = score, severity = severity, knownExploited = kev,
    )

    @Test fun mergesCoverageOfTheSameCve() {
        val articles = listOf(
            article("nvd", "Citrix NetScaler buffer issue", source = "NVD", kind = ItemKind.CVE,
                category = NewsCategory.VULNERABILITIES, cves = listOf("CVE-2026-1"), severity = Severity.HIGH, score = 8.7),
            article("thn", "NetScaler zero-day exploited in attacks", source = "The Hacker News",
                category = NewsCategory.VULNERABILITIES, cves = listOf("CVE-2026-1"), kev = true),
            article("bc", "Citrix warns of NetScaler zero-day", source = "BleepingComputer",
                category = NewsCategory.VULNERABILITIES, cves = listOf("CVE-2026-1")),
        )
        val b = BriefingBuilder.build(articles, emptyMap(), now)
        val all = b.sections.flatMap { it.items }
        assertEquals(1, all.size)
        val item = all.single()
        assertEquals(3, item.sources.size)
        assertEquals("NetScaler zero-day exploited in attacks", item.title) // news headline beats raw CVE text
        assertEquals(8.7, item.cvssScore!!, 0.0)
        assertTrue(item.knownExploited)
        assertEquals(SectionKind.EXPLOITED, b.sections.first().kind)
    }

    @Test fun mergesSimilarHeadlinesWithoutCves() {
        val articles = listOf(
            article("a", "Denmark population registry breach affects 8.8 million people", source = "A"),
            article("b", "Denmark registry breach: attackers accessed population records", source = "B"),
            article("c", "Unrelated story about Kubernetes operators", source = "C"),
        )
        val top = BriefingBuilder.build(articles, emptyMap(), now)
            .sections.first { it.kind == SectionKind.TOP_STORIES }.items
        assertEquals(2, top.size)
        assertEquals(listOf("A", "B"), top.first().sources)
    }

    @Test fun cveRecordsGroupByProductNotBoilerplate() {
        fun cve(n: Int, title: String) = article("CVE-2026-$n", title, source = "NVD", kind = ItemKind.CVE,
            category = NewsCategory.VULNERABILITIES, cves = listOf("CVE-2026-$n"), severity = Severity.CRITICAL, score = 9.0 + n / 10.0)
        val dell = (1..4).map { cve(it, "Dell Container Storage Modules, versions prior to 1.18.0, contain a vulnerability $it") } +
            cve(5, "Dell Container Storage Modules (CSM) Operator, versions prior to 1.18.0 contains a flaw")
        val other = listOf(
            cve(6, "Acme Router versions prior to 2.0 contain a vulnerability"),
            cve(7, "A vulnerability was found in Foo Shop 1.0. This affects an unknown function"),
        )
        val roundup = article("recap", "Weekly recap: five Dell bugs", cves = dell.map { it.id })
        val clusters = BriefingBuilder.cluster(dell + other + roundup)
        assertEquals(4, clusters.size) // Dell group, Acme, Foo Shop, roundup (cites too many CVEs to link)

        val item = BriefingBuilder.build(dell, emptyMap(), now).sections.flatMap { it.items }.single()
        assertEquals("Dell Container Storage Modules: 5 vulnerabilities", item.title)
        assertEquals(9.5, item.cvssScore!!, 0.001)
        val spoken = BriefingScript.build(BriefingBuilder.build(dell, emptyMap(), now), ZoneOffset.UTC)
            .first { it.itemKey != null }.text
        assertTrue(spoken, spoken.startsWith("5 vulnerabilities in Dell Container Storage Modules. The worst is rated critical, 9.5."))
    }

    @Test fun extractsProductNames() {
        assertEquals("Dell Container Storage Modules", BriefingBuilder.productOf("Dell Container Storage Modules (CSM), versions prior to v1.18.0, contains a…"))
        assertEquals("Quasar Framework", BriefingBuilder.productOf("Quasar Framework is a framework for building apps."))
        assertEquals("Foo Shop", BriefingBuilder.productOf("A vulnerability was found in Foo Shop 1.0."))
        assertEquals(null, BriefingBuilder.productOf("A flaw was found in the HyperShift operator."))
    }

    @Test fun flaggedFirstSuppressedHiddenOldDropped() {
        val watch = mapOf(SystemType.WINDOWS to WatchLevel.FLAGGED, SystemType.IOT to WatchLevel.SUPPRESSED)
        val articles = listOf(
            article("win", "Windows kernel bug", kind = ItemKind.CVE, category = NewsCategory.VULNERABILITIES,
                systems = setOf(SystemType.WINDOWS), severity = Severity.MEDIUM, score = 5.0),
            article("cam", "Camera firmware bug", kind = ItemKind.CVE, category = NewsCategory.VULNERABILITIES,
                systems = setOf(SystemType.IOT), severity = Severity.CRITICAL, score = 9.8),
            article("old", "Old critical bug", kind = ItemKind.CVE, category = NewsCategory.VULNERABILITIES,
                severity = Severity.CRITICAL, score = 9.1, ageHours = 30),
        )
        val b = BriefingBuilder.build(articles, watch, now)
        assertEquals(SectionKind.YOUR_SYSTEMS, b.sections.first().kind)
        assertEquals("win", b.sections.first().items.single().key)
        val keys = b.sections.flatMap { it.items }.map { it.key }
        assertFalse("suppressed IoT item hidden", "cam" in keys)
        assertFalse("outside 24h window", "old" in keys)
        assertEquals(1, b.stats.affectingYou)
        // Raw CVE records don't show up as "top stories".
        assertTrue(b.sections.none { it.kind == SectionKind.TOP_STORIES })
    }

    @Test fun scriptReadsNaturally() {
        val watch = mapOf(SystemType.WINDOWS to WatchLevel.FLAGGED)
        val articles = listOf(
            article("win", "Microsoft patches Windows zero-day (CVE-2026-12345)", category = NewsCategory.VULNERABILITIES,
                systems = setOf(SystemType.WINDOWS, SystemType.BROWSERS), severity = Severity.CRITICAL, score = 9.8,
                kev = true, cves = listOf("CVE-2026-12345")),
            article("mal", "New Android banking trojan spreads", category = NewsCategory.MALWARE,
                summary = "Researchers found a trojan targeting 40 banks across Europe. More details inside."),
        )
        val b = BriefingBuilder.build(articles, watch, now)
        val script = BriefingScript.build(b, ZoneOffset.UTC)
        val text = script.joinToString("\n") { it.text }

        assertTrue(text, script.first().text.startsWith("Hello, friend. Good morning. Here's your CyberPulse briefing for Tuesday, October 6."))
        assertTrue(text, "One item affects systems you're watching." in text)
        val win = script.first { it.itemKey == "win" }.text
        assertTrue(win, "CVE 2026 12345" in win)
        assertTrue(win, "Rated critical, 9.8." in win)
        assertTrue(win, "It's being actively exploited." in win)
        assertTrue(win, "Affects Windows and web browsers." in win)
        assertNotNull(script.firstOrNull { it.itemKey == "mal" && "40 banks" in it.text })
        assertEquals("That's your briefing. Stay vigilant.", script.last().text)
    }

    @Test fun emptyBriefing() {
        val script = BriefingScript.build(BriefingBuilder.build(emptyList(), emptyMap(), now), ZoneOffset.UTC)
        assertEquals(3, script.size)
        assertTrue("quiet" in script[1].text)
    }

    @Test fun joinsLists() {
        assertEquals("a", BriefingScript.joinSpoken(listOf("a")))
        assertEquals("a and b", BriefingScript.joinSpoken(listOf("a", "b")))
        assertEquals("a, b, and c", BriefingScript.joinSpoken(listOf("a", "b", "c")))
    }
}
