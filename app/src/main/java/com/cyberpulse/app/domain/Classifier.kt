package com.cyberpulse.app.domain

/**
 * Keyword-based tagging. Deliberately simple and offline: no ML, no network calls,
 * so it runs fine inside a background refresh.
 */
object Classifier {

    private fun alternation(words: List<String>): Regex =
        Regex("\\b(?:" + words.joinToString("|") { Regex.escape(it) } + ")\\b", RegexOption.IGNORE_CASE)

    private class SystemMatcher(val type: SystemType) {
        val include = alternation(type.keywords)
        val exclude = type.excludes.takeIf { it.isNotEmpty() }?.let { alternation(it) }

        fun matches(text: String): Boolean {
            val cleaned = exclude?.replace(text, " ") ?: text
            return include.containsMatchIn(cleaned)
        }
    }

    private val systemMatchers = SystemType.entries.map(::SystemMatcher)

    /**
     * @param cpes "vendor:product" pairs from NVD configurations, e.g. "microsoft:windows_10".
     */
    fun systemTypes(text: String, cpes: Collection<String> = emptyList()): Set<SystemType> {
        val cpeText = cpes.joinToString(" ; ") { it.replace('_', ' ').replace(':', ' ') }
        val haystack = normalize("$text ; $cpeText")
        return systemMatchers.filter { it.matches(haystack) }.mapTo(linkedSetOf()) { it.type }
    }

    private class CategoryRule(val category: NewsCategory, words: List<String>) {
        val regex = alternation(words)
        fun hits(text: String): Int = regex.findAll(text).count()
    }

    // Order matters: earlier rules win ties.
    private val categoryRules = listOf(
        CategoryRule(
            NewsCategory.VULNERABILITIES,
            listOf("vulnerability", "vulnerabilities", "zero-day", "zero-days", "0-day", "patch", "patches",
                "patched", "flaw", "flaws", "exploit", "exploited", "exploitation", "rce",
                "remote code execution", "security update", "security updates", "advisory", "bug", "bugs",
                "buffer overflow", "privilege escalation", "cvss", "kev", "out-of-band", "hotfix", "exploits",
                "exploiting", "bypass", "vulnerable", "cve", "security flaw", "code execution", "command injection",
                "path traversal", "unauthenticated", "side-channel", "spectre"),
        ),
        CategoryRule(
            NewsCategory.MALWARE,
            listOf("malware", "ransomware", "trojan", "botnet", "infostealer", "stealer", "backdoor", "rat",
                "spyware", "wiper", "loader", "worm", "rootkit", "cryptominer", "keylogger", "malicious package",
                "malicious packages", "implant", "implants"),
        ),
        CategoryRule(
            NewsCategory.BREACHES,
            listOf("breach", "breaches", "data breach", "leak", "leaked", "exposed", "million people", "impacted",
                "cyberattack", "cyberattacks", "outage", "incident", "stolen data",
                "hacked", "compromised", "extortion", "data theft", "customer data", "personal information",
                "records", "dark web"),
        ),
        CategoryRule(
            NewsCategory.THREAT_ACTORS,
            listOf("apt", "threat actor", "threat actors", "hacker group", "hacking group", "nation-state",
                "state-sponsored", "espionage", "campaign", "phishing", "scam", "scams", "fraud", "lazarus",
                "hacktivist", "hacktivists", "cybercriminals", "gang", "initial access broker", "hackers", "actor",
                "steals", "fake", "impersonate", "impersonating"),
        ),
        CategoryRule(
            NewsCategory.POLICY,
            listOf("law", "laws", "regulation", "regulations", "regulator", "sec", "ftc", "fcc", "congress",
                "senate", "bill", "legislation", "sanctions", "sanctioned", "arrested", "arrest", "arrests", "sentenced",
                "police", "detained", "most wanted", "seize",
                "charged", "indicted", "extradited", "doj", "europol", "fbi", "court", "lawsuit", "gdpr", "fine",
                "fined", "takedown", "seized", "cisa directive", "executive order", "policy"),
        ),
        CategoryRule(
            NewsCategory.RESEARCH,
            listOf("research", "study", "report", "open-source tool", "open source tool", "framework", "technique",
                "proof-of-concept", "poc", "analysis", "benchmark", "honeypot", "conference", "black hat",
                "def con"),
        ),
        CategoryRule(
            NewsCategory.INDUSTRY,
            listOf("acquires", "acquisition", "acquired", "funding", "raises", "series a", "series b", "startup",
                "layoffs", "ipo", "partnership", "launches", "ceo", "earnings", "revenue", "valuation", "merger"),
        ),
    )

    fun category(
        title: String,
        summary: String,
        tags: List<String> = emptyList(),
        hint: NewsCategory? = null,
    ): NewsCategory {
        val t = normalize(title)
        val s = normalize(summary)
        val g = normalize(tags.joinToString(" ; "))
        if (CVE_REGEX.containsMatchIn(t)) return NewsCategory.VULNERABILITIES

        var best: NewsCategory? = null
        var bestScore = 0
        for (rule in categoryRules) {
            val score = rule.hits(t) * 3 + rule.hits(g) * 2 + rule.hits(s)
            if (score > bestScore) {
                best = rule.category
                bestScore = score
            }
        }
        // A feed hint (e.g. CISA advisories) wins unless the text is strongly about something else.
        if (hint != null && (best == null || bestScore < 4)) return hint
        return best ?: NewsCategory.GENERAL
    }

    private val CVE_REGEX = Regex("\\bCVE-\\d{4}-\\d{4,7}\\b", RegexOption.IGNORE_CASE)

    fun extractCves(text: String): List<String> =
        CVE_REGEX.findAll(text).map { it.value.uppercase() }.distinct().toList()

    private val CVSS_REGEX = Regex(
        "cvss(?:\\s*v?[234](?:\\.\\d)?)?(?:\\s+base)?(?:\\s+score)?(?:\\s+of)?\\s*[:=]?\\s*(\\d{1,2}(?:\\.\\d)?)",
        RegexOption.IGNORE_CASE,
    )

    /** Highest CVSS score mentioned in free text, if any. */
    fun extractCvss(text: String): Double? =
        CVSS_REGEX.findAll(text)
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .filter { it in 0.0..10.0 }
            .maxOrNull()

    private val CRITICAL_PHRASE = Regex("\\bcritical\\s+(?:\\w+\\s+)?(?:flaw|vulnerabilit(?:y|ies)|bug|zero-day|rce)", RegexOption.IGNORE_CASE)

    /** Severity for free-text news: explicit CVSS score first, then wording. */
    fun severityFromText(text: String): Severity? =
        Severity.fromScore(extractCvss(text)) ?: if (CRITICAL_PHRASE.containsMatchIn(text)) Severity.CRITICAL else null

    private fun normalize(s: String) = s.replace(Regex("\\s+"), " ")
}
