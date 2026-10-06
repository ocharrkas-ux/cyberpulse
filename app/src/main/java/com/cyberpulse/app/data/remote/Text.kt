package com.cyberpulse.app.data.remote

import androidx.core.text.HtmlCompat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WHITESPACE = Regex("\\s+")
private val SCRIPT_STYLE = Regex("(?is)<(script|style)[^>]*>.*?</\\1>")

fun htmlToPlainText(html: String, maxLength: Int = 600): String {
    val text = HtmlCompat.fromHtml(SCRIPT_STYLE.replace(html, " "), HtmlCompat.FROM_HTML_MODE_COMPACT)
        .toString()
        .replace('￼', ' ') // object replacement chars left behind by <img>
        .replace(WHITESPACE, " ")
        .trim()
    return if (text.length <= maxLength) text else text.take(maxLength).substringBeforeLast(' ') + "…"
}

/** Parses RSS (RFC 822), Atom/ISO-8601 and NVD's zone-less timestamps. Returns epoch millis. */
fun parseFeedDate(raw: String?): Long? {
    val s = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    runCatching { return ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
    runCatching { return OffsetDateTime.parse(s).toInstant().toEpochMilli() }
    runCatching { return Instant.parse(s).toEpochMilli() }
    runCatching { return LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli() }
    for (pattern in LEGACY_PATTERNS) {
        runCatching {
            return SimpleDateFormat(pattern, Locale.US).apply { isLenient = true }.parse(s)!!.time
        }
    }
    return null
}

private val LEGACY_PATTERNS = listOf(
    "EEE, dd MMM yyyy HH:mm:ss zzz",
    "EEE, dd MMM yyyy HH:mm zzz",
    "dd MMM yyyy HH:mm:ss Z",
    "yyyy-MM-dd HH:mm:ss",
    "yyyy-MM-dd",
)

/** Drops fragments and utm_* tracking params so the same story doesn't appear twice. */
fun canonicalLink(link: String): String {
    val base = link.trim().substringBefore('#')
    val path = base.substringBefore('?')
    val query = base.substringAfter('?', "")
        .split('&')
        .filter { it.isNotEmpty() && !it.startsWith("utm_") }
        .joinToString("&")
    return if (query.isEmpty()) path else "$path?$query"
}
