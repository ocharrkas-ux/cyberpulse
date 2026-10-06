package com.cyberpulse.app.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One utterance. [itemKey] links it back to the briefing card being read, for highlighting. */
data class SpeechSegment(val text: String, val itemKey: String? = null)

/** Turns a [Briefing] into natural spoken sentences. */
object BriefingScript {

    fun build(
        briefing: Briefing,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.US,
    ): List<SpeechSegment> {
        val out = mutableListOf<SpeechSegment>()
        val time = Instant.ofEpochMilli(briefing.generatedAt).atZone(zone)
        val greeting = when (time.hour) {
            in 4..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
        val date = DateTimeFormatter.ofPattern("EEEE, MMMM d", locale).format(time)
        out += SpeechSegment("Hello, friend. $greeting. Here's your CyberPulse briefing for $date.")

        val s = briefing.stats
        if (briefing.isEmpty) {
            out += SpeechSegment("It's been quiet. Nothing new came in over the last 24 hours.")
            out += SpeechSegment("That's all for now. Stay vigilant.")
            return out
        }

        out += SpeechSegment(statsSentence(s))
        if (s.watchingAny) {
            out += SpeechSegment(
                when (s.affectingYou) {
                    0 -> "Nothing new affects the systems you're watching."
                    1 -> "One item affects systems you're watching."
                    else -> "${s.affectingYou} items affect systems you're watching."
                }
            )
        } else {
            out += SpeechSegment("You aren't watching any systems yet. Flag the ones you run to get a personal section here.")
        }

        for (section in briefing.sections) {
            out += SpeechSegment(sectionIntro(section))
            section.items.forEachIndexed { i, item ->
                out += SpeechSegment(itemSentence(item, section, i == 0), item.key)
            }
            val more = when (section.kind) {
                SectionKind.YOUR_SYSTEMS -> "affecting your systems"
                SectionKind.FOLLOWED_TOPICS -> "on topics you follow"
                SectionKind.EXPLOITED -> "being exploited"
                SectionKind.CRITICAL -> "critical ones"
                else -> null // stories continue by category below
            }
            if (section.more > 0 && more != null) out += SpeechSegment("Plus ${section.more} more $more, in the app.")
        }

        out += SpeechSegment("That's your briefing. Stay vigilant.")
        return out
    }

    private fun statsSentence(s: BriefingStats): String = buildString {
        val parts = buildList {
            if (s.newsStories > 0) add(count(s.newsStories, "news story", "news stories"))
            if (s.newCves > 0) add(count(s.newCves, "newly published vulnerability", "newly published vulnerabilities"))
        }
        append("In the last 24 hours, I tracked ").append(joinSpoken(parts.ifEmpty { listOf("a few updates") })).append('.')
        val severe = buildList {
            if (s.critical > 0) add("${s.critical} ${if (s.critical == 1) "is" else "are"} rated critical")
            if (s.exploited > 0) add("${s.exploited} ${if (s.exploited == 1) "is" else "are"} being actively exploited")
        }
        if (severe.isNotEmpty()) append(' ').append(joinSpoken(severe).replaceFirstChar { it.uppercase() }).append('.')
    }

    private fun sectionIntro(section: BriefingSection): String = when (section.kind) {
        SectionKind.YOUR_SYSTEMS -> "First, what affects your systems."
        SectionKind.FOLLOWED_TOPICS -> "Topics you follow."
        SectionKind.EXPLOITED -> "Actively exploited vulnerabilities."
        SectionKind.CRITICAL -> "Other critical vulnerabilities."
        SectionKind.TOP_STORIES -> "Now, the top stories."
        SectionKind.CATEGORY ->
            if (section.category == NewsCategory.VULNERABILITIES) "More vulnerability news."
            else "In ${speakable(section.title).lowercase()}."
    }

    private fun itemSentence(item: BriefingItem, section: BriefingSection, first: Boolean): String = buildString {
        val count = item.cveIds.size
        if (item.product != null) {
            append("$count vulnerabilities in ${speakable(item.product)}.")
        } else {
            append(sentence(headline(item.title)))
        }
        if (item.isVulnerability) {
            val worst = if (item.product != null) " The worst is rated" else " Rated"
            when {
                item.cvssScore != null && item.severity != null ->
                    append("$worst ${item.severity.label.lowercase()}, ${"%.1f".format(Locale.US, item.cvssScore)}.")
                item.severity != null -> append("$worst ${item.severity.label.lowercase()}.")
            }
            if (item.knownExploited && section.kind != SectionKind.EXPLOITED) append(" It's being actively exploited.")
            if (item.systemTypes.isNotEmpty()) {
                append(" Affects ").append(joinSpoken(item.systemTypes.take(3).map { SPOKEN_SYSTEMS.getValue(it) })).append('.')
            }
        }
        if (section.kind == SectionKind.FOLLOWED_TOPICS && item.followedTopics.isNotEmpty()) {
            append(" Matches ").append(joinSpoken(item.followedTopics.take(2).map { speakable(it.display) })).append('.')
        }
        if (section.kind == SectionKind.TOP_STORIES) {
            firstSentence(item.summary)?.let { append(' ').append(sentence(speakable(it))) }
        }
        if (item.sources.size > 1) {
            val outlets = item.sources.filter { it != "NVD" && it != "CISA KEV" }
            if (outlets.size > 1) append(" Covered by ").append(joinSpoken(outlets.take(3))).append('.')
        } else if (first && item.hasCoverage && section.kind != SectionKind.YOUR_SYSTEMS) {
            append(" From ").append(item.sources.first()).append('.')
        }
    }

    private val SPOKEN_SYSTEMS = mapOf(
        SystemType.WINDOWS to "Windows",
        SystemType.MACOS to "macOS",
        SystemType.LINUX to "Linux",
        SystemType.ANDROID to "Android",
        SystemType.IOS to "iOS",
        SystemType.BROWSERS to "web browsers",
        SystemType.NETWORK to "network devices",
        SystemType.CLOUD to "cloud services",
        SystemType.VIRTUALIZATION to "virtualization",
        SystemType.WEB_APPS to "web apps",
        SystemType.DATABASES to "databases",
        SystemType.ENTERPRISE to "enterprise software",
        SystemType.DEV_TOOLS to "developer tools",
        SystemType.ICS_OT to "industrial systems",
        SystemType.IOT to "IoT devices",
        SystemType.AI to "AI systems",
    )

    private val DANGLING = Regex("(?:\\s+(?:a|an|the|in|of|for|to|and|or|with|on|at|by|from|that|which))+\\s*$", RegexOption.IGNORE_CASE)

    /** Spoken headline; a title shortened with "…" is cut back so it doesn't end on "in the". */
    private fun headline(title: String): String {
        val truncated = title.endsWith("…")
        val text = speakable(title)
        return if (truncated) text.replace(DANGLING, "").trimEnd(',', ';', ' ') else text
    }

    // ---- text helpers ----

    private val URL = Regex("https?://\\S+")
    private val CVE = Regex("\\bCVE-(\\d{4})-(\\d{4,7})\\b", RegexOption.IGNORE_CASE)
    private val WHITESPACE = Regex("\\s+")

    /** Makes headline text pleasant for a speech engine. */
    fun speakable(text: String): String = text
        .replace(URL, "")
        .replace(CVE) { "CVE ${it.groupValues[1]} ${it.groupValues[2]}" }
        .replace("&", " and ")
        .replace("…", "")
        .replace("⚡", "")
        .replace(" // ", ", ")
        .replace(WHITESPACE, " ")
        .trim()

    private fun sentence(text: String): String =
        if (text.isEmpty() || text.last() in ".!?") text else "$text."

    private fun firstSentence(text: String): String? =
        text.split(Regex("(?<=[.!?])\\s+"), limit = 2).firstOrNull()
            ?.trim()
            ?.takeIf { it.length in 20..240 }

    private fun count(n: Int, singular: String, plural: String) = "$n ${if (n == 1) singular else plural}"

    internal fun joinSpoken(parts: List<String>): String = when (parts.size) {
        0 -> ""
        1 -> parts[0]
        2 -> "${parts[0]} and ${parts[1]}"
        else -> parts.dropLast(1).joinToString(", ") + ", and " + parts.last()
    }
}
