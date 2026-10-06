package com.cyberpulse.app.data.remote

import com.cyberpulse.app.domain.ItemKind
import com.cyberpulse.app.domain.NewsCategory

data class FeedSource(
    val name: String,
    val url: String,
    val kind: ItemKind = ItemKind.NEWS,
    /** Default category when an item's text doesn't clearly point elsewhere. */
    val categoryHint: NewsCategory? = null,
)

val FEEDS = listOf(
    FeedSource("The Hacker News", "https://feeds.feedburner.com/TheHackersNews"),
    FeedSource("BleepingComputer", "https://www.bleepingcomputer.com/feed/"),
    FeedSource("Krebs on Security", "https://krebsonsecurity.com/feed/"),
    FeedSource("SecurityWeek", "https://www.securityweek.com/feed/"),
    FeedSource("Dark Reading", "https://www.darkreading.com/rss.xml"),
    FeedSource("The Record", "https://therecord.media/feed"),
    FeedSource(
        "CISA Advisories",
        "https://www.cisa.gov/cybersecurity-advisories/all.xml",
        kind = ItemKind.ADVISORY,
        categoryHint = NewsCategory.VULNERABILITIES,
    ),
)
