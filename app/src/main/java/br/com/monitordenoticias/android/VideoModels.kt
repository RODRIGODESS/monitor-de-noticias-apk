package br.com.monitordenoticias.android

data class VideoItem(
    val id: Long = 0,
    val title: String,
    val sourceId: String,
    val sourceName: String,
    val publishedAt: Long,
    val link: String,
    val summary: String = "",
    val matchedTerm: String = "",
    val matchedDemand: String = "",
    val capturedAt: Long = System.currentTimeMillis()
) {
    val relevant: Boolean get() = matchedTerm.isNotBlank() || matchedDemand.isNotBlank()
    val demand: Boolean get() = matchedDemand.isNotBlank()
}

data class VideoSource(
    val id: String,
    val name: String,
    val group: String,
    val region: String = "Nacional",
    val state: String = "",
    val landingUrl: String,
    val linkHints: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val youtubeHandle: String = "",
    val searchUrlTemplate: String = ""
)

data class VideoSearchResult(
    val items: List<VideoItem>,
    val foundCount: Int,
    val newCount: Int,
    val relevantCount: Int,
    val newRelevantCount: Int,
    val errors: Int
)

data class VideoState(
    val items: List<VideoItem> = emptyList(),
    val selectedSourceIds: Set<String> = emptySet(),
    val busy: Boolean = false,
    val status: String = "Pronto",
    val filter: VideoFilter = VideoFilter.ALL,
    val lastManualAt: Long = 0L,
    val periodStartDate: String = "",
    val periodStartTime: String = "00:00",
    val periodEndDate: String = "",
    val periodEndTime: String = "23:59",
    val periodActive: Boolean = false
)

enum class VideoFilter { ALL, RELEVANT, DEMANDS }
