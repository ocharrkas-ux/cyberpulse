package com.cyberpulse.app

import com.cyberpulse.app.data.db.Article
import com.cyberpulse.app.domain.BriefingBuilder
import com.cyberpulse.app.domain.BriefingScript
import com.cyberpulse.app.domain.ItemKind
import com.cyberpulse.app.domain.NotifyMode
import com.cyberpulse.app.domain.VulnPolicy
import com.cyberpulse.app.domain.NewsCategory
import com.cyberpulse.app.domain.SectionKind
import com.cyberpulse.app.domain.Severity
import com.cyberpulse.app.domain.SystemType
import com.cyberpulse.app.domain.Topic
import com.cyberpulse.app.domain.TopicKind
import com.cyberpulse.app.domain.TopicMatcher
import com.cyberpulse.app.domain.TopicRule
import com.cyberpulse.app.domain.TopicRules
import com.cyberpulse.app.domain.WatchLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

class TopicsTest {
    private val now = 1_791_273_600_000L

    private fun article(
        id: String,
        title: String,
        summary: String = "",
        source: String = "Src",
        kind: ItemKind = ItemKind.NEWS,
        category: NewsCategory = NewsCategory.GENERAL,
        systems: Set<SystemType> = emptySet(),
    ) = Article(
        id = id, kind = kind, title = title, summary = summary, link = "https://x/$id", source = source,
        publishedAt = now - TimeUnit.HOURS.toMillis(1), fetchedAt = now, category = category, systemTypes = systems,
        cveIds = if (kind == ItemKind.CVE) listOf(id) else emptyList(), cvssScore = null,
        severity = if (kind == ItemKind.CVE) Severity.CRITICAL else null, knownExploited = false,
    )

    private fun rules(vararg pairs: Pair<Topic, WatchLevel>) = TopicRules(pairs.map { TopicRule(it.first, it.second) })

    @Test fun keywordMatchesWholeWordsOnly() {
        val topic = Topic(TopicKind.KEYWORD, "ASOS")
        assertTrue(TopicMatcher.matches(topic, article("a", "ASOS confirms data breach")))
        assertTrue(TopicMatcher.matches(topic, article("b", "Retail news", summary = "Shares in asos fell")))
        assertFalse(TopicMatcher.matches(topic, article("c", "KASOS island ferry delayed")))
    }

    @Test fun productMatchesOnlyThatProductsCveRecords() {
        val topic = Topic(TopicKind.PRODUCT, "Payload")
        assertTrue(TopicMatcher.matches(topic, article("CVE-1", "Payload is a free and open source CMS.", kind = ItemKind.CVE)))
        assertFalse(TopicMatcher.matches(topic, article("n", "Payload delivered via phishing")))
    }

    @Test fun suggestsDistinctiveNames() {
        val words = TopicMatcher.suggestKeywords("FBI Removes Accenture Contractor After Patch Failure Led to ShinyHunters Breach")
        assertEquals("ShinyHunters", words.first())
        assertFalse("Patch" in words)
        assertTrue(TopicMatcher.suggestKeywords("New Critical Flaw Exploited in Attacks").isEmpty())
    }

    @Test fun suppressedTopicsAreLeftOut() {
        val articles = listOf(
            article("CVE-1", "Payload is a free and open source CMS.", source = "NVD", kind = ItemKind.CVE, category = NewsCategory.VULNERABILITIES),
            article("ind", "Startup raises funding", category = NewsCategory.INDUSTRY),
            article("dr", "Opinion on SOC careers", source = "Dark Reading"),
            article("keep", "ShinyHunters member detained", category = NewsCategory.POLICY),
        )
        val topics = rules(
            Topic(TopicKind.PRODUCT, "payload") to WatchLevel.SUPPRESSED, // case-insensitive
            Topic(TopicKind.CATEGORY, NewsCategory.INDUSTRY.name) to WatchLevel.SUPPRESSED,
            Topic(TopicKind.SOURCE, "Dark Reading") to WatchLevel.SUPPRESSED,
        )
        val keys = BriefingBuilder.build(articles, emptyMap(), now, topics = topics).sections.flatMap { it.items }.map { it.key }
        assertEquals(listOf("keep"), keys)
    }

    @Test fun flaggedTopicsGetTheirOwnSectionAndBeatSuppression() {
        val articles = listOf(
            article("a", "ShinyHunters leak site seized", category = NewsCategory.POLICY, source = "Dark Reading"),
            article("b", "Unrelated story about printers", category = NewsCategory.GENERAL),
        )
        val topics = rules(
            Topic(TopicKind.KEYWORD, "ShinyHunters") to WatchLevel.FLAGGED,
            Topic(TopicKind.SOURCE, "Dark Reading") to WatchLevel.SUPPRESSED,
        )
        val b = BriefingBuilder.build(articles, emptyMap(), now, topics = topics)
        val followed = b.sections.first()
        assertEquals(SectionKind.FOLLOWED_TOPICS, followed.kind)
        assertEquals("a", followed.items.single().key)
        assertEquals("ShinyHunters", followed.items.single().followedTopics.single().value)

        val spoken = BriefingScript.build(b, ZoneOffset.UTC).joinToString("\n") { it.text }
        assertTrue(spoken, "Topics you follow." in spoken)
        assertTrue(spoken, "Matches ShinyHunters." in spoken)
    }

    @Test fun topicSuppressionAppliesToVulnFeedAndAlerts() {
        val watch = mapOf(SystemType.CLOUD to WatchLevel.FLAGGED)
        val dell = article("CVE-9", "Dell Container Storage Modules, versions prior to 1.18.0, contain a flaw.",
            kind = ItemKind.CVE, category = NewsCategory.VULNERABILITIES, systems = setOf(SystemType.CLOUD))
        val other = article("CVE-10", "Acme Cloud Agent versions before 2.0 allow takeover.",
            kind = ItemKind.CVE, category = NewsCategory.VULNERABILITIES, systems = setOf(SystemType.CLOUD))
        val muteDell = rules(Topic(TopicKind.PRODUCT, "Dell Container Storage Modules") to WatchLevel.SUPPRESSED)

        // Without topic rules: both alert and both are visible.
        assertTrue(VulnPolicy.shouldNotify(dell, watch, NotifyMode.FLAGGED_ONLY, null))
        assertFalse(VulnPolicy.isSuppressed(dell, watch))

        // Muting the product hides it and silences its alerts, in both notify modes, despite Cloud being flagged.
        assertTrue(VulnPolicy.isSuppressed(dell, watch, muteDell))
        assertFalse(VulnPolicy.shouldNotify(dell, watch, NotifyMode.FLAGGED_ONLY, null, muteDell))
        assertFalse(VulnPolicy.shouldNotify(dell, watch, NotifyMode.ALL_EXCEPT_SUPPRESSED, null, muteDell))
        assertTrue(VulnPolicy.shouldNotify(other, watch, NotifyMode.FLAGGED_ONLY, null, muteDell))

        // Muting a source silences it too; a flagged keyword overrides the mute.
        val muteNvdButFollowDell = rules(
            Topic(TopicKind.SOURCE, "Src") to WatchLevel.SUPPRESSED,
            Topic(TopicKind.KEYWORD, "Dell") to WatchLevel.FLAGGED,
        )
        assertFalse(VulnPolicy.isSuppressed(dell, watch, muteNvdButFollowDell))
        assertTrue(VulnPolicy.isSuppressed(other, watch, muteNvdButFollowDell))
    }

    @Test fun suppressedProductBeatsFlaggedSystemType() {
        // Flagging "Web apps" is broad; muting one noisy product within it should still work.
        val articles = listOf(
            article("CVE-2", "Payload is a free and open source CMS.", kind = ItemKind.CVE,
                category = NewsCategory.VULNERABILITIES, systems = setOf(SystemType.WEB_APPS)),
            article("CVE-3", "Craft CMS 5.1 contains a flaw.", kind = ItemKind.CVE,
                category = NewsCategory.VULNERABILITIES, systems = setOf(SystemType.WEB_APPS)),
        )
        val b = BriefingBuilder.build(
            articles,
            mapOf(SystemType.WEB_APPS to WatchLevel.FLAGGED),
            now,
            topics = rules(Topic(TopicKind.PRODUCT, "Payload") to WatchLevel.SUPPRESSED),
        )
        assertEquals(SectionKind.YOUR_SYSTEMS, b.sections.single().kind)
        assertEquals(listOf("CVE-3"), b.sections.single().items.map { it.key })
    }
}
