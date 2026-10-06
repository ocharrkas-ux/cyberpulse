package com.cyberpulse.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cyberpulse.app.domain.ItemKind
import com.cyberpulse.app.domain.NewsCategory
import com.cyberpulse.app.domain.Severity
import com.cyberpulse.app.domain.SystemType

@Entity(
    tableName = "articles",
    indices = [Index("category"), Index("publishedAt")],
)
data class Article(
    /** Canonical link for news; CVE id for CVE records. */
    @PrimaryKey val id: String,
    val kind: ItemKind,
    val title: String,
    val summary: String,
    val link: String,
    val source: String,
    val publishedAt: Long,
    val fetchedAt: Long,
    val category: NewsCategory,
    val systemTypes: Set<SystemType>,
    val cveIds: List<String>,
    val cvssScore: Double?,
    val severity: Severity?,
    /** Listed in CISA's Known Exploited Vulnerabilities catalog (or mentions a CVE that is). */
    val knownExploited: Boolean,
    val isRead: Boolean = false,
)
