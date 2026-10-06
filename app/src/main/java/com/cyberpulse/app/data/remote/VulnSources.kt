package com.cyberpulse.app.data.remote

import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class CveRecord(
    val id: String,
    val description: String,
    val publishedAt: Long,
    val cvssScore: Double?,
    /** "vendor:product" pairs from CPE configurations and CNA "affected" data. */
    val products: List<String>,
    val inKev: Boolean,
)

/** NVD CVE API 2.0 — newly published CVEs. Unauthenticated limit is 5 requests / 30 s. */
class NvdClient(private val http: Http) {

    suspend fun fetchRecent(days: Long, now: Instant = Instant.now()): List<CveRecord> {
        val end = now.truncatedTo(ChronoUnit.SECONDS)
        val start = end.minus(days, ChronoUnit.DAYS)
        val results = mutableListOf<CveRecord>()
        var startIndex = 0
        for (page in 0 until MAX_PAGES) {
            if (page > 0) delay(6_500) // stay under the public rate limit
            val url = "$BASE?pubStartDate=${fmt(start)}&pubEndDate=${fmt(end)}" +
                "&resultsPerPage=$PAGE_SIZE&startIndex=$startIndex&noRejected"
            val json = JSONObject(http.get(url))
            val vulns = json.optJSONArray("vulnerabilities") ?: JSONArray()
            for (i in 0 until vulns.length()) {
                vulns.optJSONObject(i)?.optJSONObject("cve")?.let(::parseCve)?.let(results::add)
            }
            startIndex += vulns.length()
            if (vulns.length() == 0 || startIndex >= json.optInt("totalResults", 0)) break
        }
        return results
    }

    private fun parseCve(cve: JSONObject): CveRecord? {
        val id = cve.optString("id").takeIf { it.startsWith("CVE-") } ?: return null
        val description = cve.optJSONArray("descriptions").objects()
            .firstOrNull { it.optString("lang") == "en" }?.optString("value").orEmpty()
        val published = parseFeedDate(cve.optString("published")) ?: return null

        val products = linkedSetOf<String>()
        cve.optJSONArray("configurations").objects().forEach { config ->
            config.optJSONArray("nodes").objects().forEach { node ->
                node.optJSONArray("cpeMatch").objects().forEach { match ->
                    // cpe:2.3:part:vendor:product:version:...
                    val parts = match.optString("criteria").split(':')
                    if (parts.size > 4) products += "${parts[3]}:${parts[4]}"
                }
            }
        }
        cve.optJSONArray("affected").objects().forEach { affected ->
            affected.optJSONArray("affectedData").objects().forEach { data ->
                val vendor = data.optString("vendor")
                val product = data.optString("product")
                if (product.isNotBlank()) products += "$vendor:$product"
            }
        }

        return CveRecord(
            id = id,
            description = description,
            publishedAt = published,
            cvssScore = bestScore(cve.optJSONObject("metrics")),
            products = products.toList(),
            inKev = cve.has("cisaExploitAdd"),
        )
    }

    /** Prefers newest CVSS version, and NVD's own ("Primary") score over the CNA's. */
    private fun bestScore(metrics: JSONObject?): Double? {
        metrics ?: return null
        for (key in listOf("cvssMetricV40", "cvssMetricV31", "cvssMetricV30", "cvssMetricV2")) {
            val entries = metrics.optJSONArray(key).objects()
            if (entries.isEmpty()) continue
            val chosen = entries.firstOrNull { it.optString("type") == "Primary" } ?: entries.first()
            val score = chosen.optJSONObject("cvssData")?.optDouble("baseScore", Double.NaN)
            if (score != null && !score.isNaN()) return score
        }
        return null
    }

    private fun fmt(instant: Instant) = DATE_FMT.format(instant)

    private companion object {
        const val BASE = "https://services.nvd.nist.gov/rest/json/cves/2.0"
        const val PAGE_SIZE = 2000
        const val MAX_PAGES = 3
        val DATE_FMT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS").withZone(ZoneOffset.UTC)
    }
}

data class KevEntry(
    val cveId: String,
    val vendor: String,
    val product: String,
    val name: String,
    val description: String,
    val dateAdded: Long,
    val ransomware: Boolean,
)

/** CISA Known Exploited Vulnerabilities catalog. */
class KevClient(private val http: Http) {
    suspend fun fetch(): List<KevEntry> {
        val json = JSONObject(http.get(URL))
        return json.optJSONArray("vulnerabilities").objects().mapNotNull { v ->
            val id = v.optString("cveID").takeIf { it.startsWith("CVE-") } ?: return@mapNotNull null
            KevEntry(
                cveId = id,
                vendor = v.optString("vendorProject"),
                product = v.optString("product"),
                name = v.optString("vulnerabilityName"),
                description = v.optString("shortDescription"),
                dateAdded = parseFeedDate(v.optString("dateAdded")) ?: 0L,
                ransomware = v.optString("knownRansomwareCampaignUse").equals("Known", ignoreCase = true),
            )
        }
    }

    private companion object {
        const val URL = "https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json"
    }
}

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
