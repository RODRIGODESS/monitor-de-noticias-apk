package br.com.monitordenoticias.android

data class News(
    val id: Long = 0,
    val title: String,
    val source: String,
    val date: Long,
    val link: String,
    val snippet: String = "",
    val important: Boolean = false,
    val demand: Boolean = false,
    val matchedTerm: String = "",
    val matchedDemand: String = "",
    val capturedAt: Long = System.currentTimeMillis()
)

data class Demand(val id: Long = 0, val vehicle: String, val subject: String, val active: Boolean = true)

data class SearchResult(
    val items: List<News>,
    val foundCount: Int,
    val newCount: Int,
    val newDemandCount: Int,
    val errors: Int = 0
)

data class AppState(
    val news: List<News> = emptyList(),
    val history: List<News> = emptyList(),
    val demands: List<Demand> = emptyList(),
    val terms: List<String> = emptyList(),
    val selectedTab: Int = 0,
    val busy: Boolean = false,
    val status: String = "Pronto",
    val intervalMinutes: Int = 30,
    val lastUpdatedAt: Long? = null,
    val showOnlyDemands: Boolean = false
)
