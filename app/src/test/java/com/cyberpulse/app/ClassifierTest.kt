package com.cyberpulse.app

import com.cyberpulse.app.data.db.Article
import com.cyberpulse.app.domain.Classifier
import com.cyberpulse.app.domain.ItemKind
import com.cyberpulse.app.domain.NewsCategory
import com.cyberpulse.app.domain.NotifyMode
import com.cyberpulse.app.domain.Severity
import com.cyberpulse.app.domain.SystemType
import com.cyberpulse.app.domain.VulnPolicy
import com.cyberpulse.app.domain.WatchLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassifierTest {

    @Test fun tagsWindowsAndBrowsers() {
        val types = Classifier.systemTypes("Google patches Chrome zero-day exploited on Windows systems")
        assertTrue(SystemType.BROWSERS in types)
        assertTrue(SystemType.WINDOWS in types)
    }

    @Test fun ciscoIosIsNetworkNotApple() {
        val types = Classifier.systemTypes("Cisco IOS XE flaw lets attackers take over routers")
        assertTrue(SystemType.NETWORK in types)
        assertFalse(SystemType.IOS in types)
    }

    @Test fun appleIosDetected() {
        assertTrue(SystemType.IOS in Classifier.systemTypes("Apple fixes iOS 26 kernel bug on iPhone"))
    }

    @Test fun usesCpeProducts() {
        val types = Classifier.systemTypes("A buffer overflow allows code execution.", listOf("vmware:esxi", "microsoft:windows_server_2022"))
        assertTrue(SystemType.VIRTUALIZATION in types)
        assertTrue(SystemType.WINDOWS in types)
    }

    @Test fun noSubstringMatches() {
        // "edge" alone and "ai" inside words must not trigger anything.
        assertTrue(Classifier.systemTypes("Bleeding edge research on the cutting edge").isEmpty())
    }

    @Test fun categorizesVulnerabilityNews() {
        assertEquals(
            NewsCategory.VULNERABILITIES,
            Classifier.category("Fortinet warns of critical FortiWeb flaw exploited in attacks", ""),
        )
        assertEquals(NewsCategory.VULNERABILITIES, Classifier.category("Details on CVE-2026-1234 published", ""))
    }

    @Test fun categorizesOtherNews() {
        assertEquals(NewsCategory.MALWARE, Classifier.category("New Android banking trojan spreads via fake apps", ""))
        assertEquals(NewsCategory.BREACHES, Classifier.category("Retailer confirms data breach exposing customer data", ""))
        assertEquals(NewsCategory.POLICY, Classifier.category("Ransomware affiliate sentenced to 10 years in US court", "The DOJ said"))
        assertEquals(NewsCategory.INDUSTRY, Classifier.category("Security startup raises $50M in Series B funding", ""))
        assertEquals(NewsCategory.GENERAL, Classifier.category("Weekly podcast episode", ""))
    }

    @Test fun categoryHintUsedForWeakSignals() {
        assertEquals(
            NewsCategory.VULNERABILITIES,
            Classifier.category("Siemens SIMATIC S7-1500", "", hint = NewsCategory.VULNERABILITIES),
        )
    }

    @Test fun extractsCvesAndScores() {
        val text = "The bug, tracked as cve-2026-10123 (CVSS score: 9.8), and CVE-2026-2222 with a CVSS v3.1 score of 7.5"
        assertEquals(listOf("CVE-2026-10123", "CVE-2026-2222"), Classifier.extractCves(text))
        assertEquals(9.8, Classifier.extractCvss(text)!!, 0.001)
        assertEquals(Severity.CRITICAL, Classifier.severityFromText(text))
        assertNull(Classifier.extractCvss("No scores here"))
    }

    private fun vuln(vararg types: SystemType, severity: Severity? = Severity.HIGH, kev: Boolean = false) = Article(
        id = "CVE-2026-0001", kind = ItemKind.CVE, title = "t", summary = "s", link = "l", source = "NVD",
        publishedAt = 0, fetchedAt = 0, category = NewsCategory.VULNERABILITIES, systemTypes = types.toSet(),
        cveIds = listOf("CVE-2026-0001"), cvssScore = null, severity = severity, knownExploited = kev,
    )

    @Test fun suppressionRequiresAllTypesSuppressed() {
        val watch = mapOf(SystemType.WINDOWS to WatchLevel.SUPPRESSED)
        assertTrue(VulnPolicy.isSuppressed(vuln(SystemType.WINDOWS), watch))
        assertFalse(VulnPolicy.isSuppressed(vuln(SystemType.WINDOWS, SystemType.BROWSERS), watch))
        assertFalse(VulnPolicy.isSuppressed(vuln(), watch))
    }

    @Test fun flaggedBeatsSuppressed() {
        val watch = mapOf(SystemType.WINDOWS to WatchLevel.SUPPRESSED, SystemType.BROWSERS to WatchLevel.FLAGGED)
        val item = vuln(SystemType.WINDOWS, SystemType.BROWSERS)
        assertFalse(VulnPolicy.isSuppressed(item, watch))
        assertTrue(VulnPolicy.shouldNotify(item, watch, NotifyMode.FLAGGED_ONLY, Severity.HIGH))
    }

    @Test fun notificationRules() {
        val watch = mapOf(SystemType.LINUX to WatchLevel.FLAGGED, SystemType.IOT to WatchLevel.SUPPRESSED)
        // Below threshold, not exploited -> no alert.
        assertFalse(VulnPolicy.shouldNotify(vuln(SystemType.LINUX, severity = Severity.MEDIUM), watch, NotifyMode.FLAGGED_ONLY, Severity.HIGH))
        // Exploited bypasses severity threshold.
        assertTrue(VulnPolicy.shouldNotify(vuln(SystemType.LINUX, severity = null, kev = true), watch, NotifyMode.FLAGGED_ONLY, Severity.HIGH))
        // Unflagged systems only alert in "all except suppressed" mode.
        assertFalse(VulnPolicy.shouldNotify(vuln(SystemType.CLOUD), watch, NotifyMode.FLAGGED_ONLY, null))
        assertTrue(VulnPolicy.shouldNotify(vuln(SystemType.CLOUD), watch, NotifyMode.ALL_EXCEPT_SUPPRESSED, null))
        assertFalse(VulnPolicy.shouldNotify(vuln(SystemType.IOT), watch, NotifyMode.ALL_EXCEPT_SUPPRESSED, null))
    }
}
