package com.cyberpulse.app.data.remote

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

data class FeedEntry(
    val title: String,
    val link: String,
    val summary: String,
    val publishedAt: Long?,
    val tags: List<String>,
)

/** Minimal RSS 2.0 + Atom parser. Tolerates malformed feeds by returning what it parsed so far. */
object RssParser {

    fun parse(xml: String): List<FeedEntry> {
        val entries = mutableListOf<FeedEntry>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        runCatching { parser.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) }
        parser.setInput(StringReader(xml.trimStart('﻿', ' ', '\n', '\r', '\t')))

        var inEntry = false
        var title: String? = null
        var link: String? = null
        var summary: String? = null
        var content: String? = null
        var date: Long? = null
        val tags = mutableListOf<String>()

        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name.lowercase()
                        if (name == "item" || name == "entry") {
                            inEntry = true
                            title = null; link = null; summary = null; content = null; date = null
                            tags.clear()
                        } else if (inEntry) {
                            when (name) {
                                "title" -> readText(parser).let { if (title == null) title = it }
                                "link" -> {
                                    val href = parser.getAttributeValue(null, "href")
                                    if (href != null) {
                                        val rel = parser.getAttributeValue(null, "rel")
                                        if (link == null && (rel == null || rel == "alternate")) link = href
                                    } else {
                                        readText(parser).takeIf { it.isNotBlank() }?.let { if (link == null) link = it }
                                    }
                                }
                                "description", "summary" -> readText(parser).let { if (summary == null) summary = it }
                                "content:encoded", "content" -> readText(parser).let { if (content == null) content = it }
                                "pubdate", "published", "updated", "dc:date" ->
                                    readText(parser).let { if (date == null) date = parseFeedDate(it) }
                                "category" -> {
                                    val term = parser.getAttributeValue(null, "term")
                                    val tag = term ?: readText(parser)
                                    if (tag.isNotBlank()) tags += tag.trim()
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name.lowercase()
                        if ((name == "item" || name == "entry") && inEntry) {
                            inEntry = false
                            val t = title?.let { htmlToPlainText(it, 300) }
                            val l = link?.trim()
                            if (!t.isNullOrBlank() && !l.isNullOrBlank()) {
                                entries += FeedEntry(
                                    title = t,
                                    link = l,
                                    summary = htmlToPlainText(summary ?: content ?: ""),
                                    publishedAt = date,
                                    tags = tags.toList(),
                                )
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
            // Keep whatever entries parsed cleanly before the error.
        }
        return entries
    }

    /** Reads all text inside the current element, including nested markup, and leaves the parser on its end tag. */
    private fun readText(parser: XmlPullParser): String {
        val depth = parser.depth
        val sb = StringBuilder()
        while (true) {
            when (parser.next()) {
                XmlPullParser.TEXT -> sb.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.depth == depth) break
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return sb.toString().trim()
    }
}
