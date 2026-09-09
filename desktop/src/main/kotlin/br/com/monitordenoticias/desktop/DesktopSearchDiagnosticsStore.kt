package br.com.monitordenoticias.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Estado observável com a auditoria da última busca de notícias no Windows. */
object DesktopSearchDiagnosticsStore {
    data class Snapshot(
        val active: Boolean = false,
        val startedAt: Long = 0L,
        val finishedAt: Long = 0L,
        val googleQueries: Int = 0,
        val googleRawItems: Int = 0,
        val googleInPeriod: Int = 0,
        val googleSourceAccepted: Int = 0,
        val googleErrors: Int = 0,
        val directSources: Int = 0,
        val directListingPages: Int = 0,
        val directCandidates: Int = 0,
        val directTermMatches: Int = 0,
        val directAccepted: Int = 0,
        val directErrors: Int = 0,
        val finalFound: Int = 0,
        val finalNew: Int = 0,
        val directSourceNames: List<String> = emptyList()
    )

    var snapshot: Snapshot by mutableStateOf(Snapshot())
        private set

    @Synchronized
    fun begin() {
        snapshot = Snapshot(active = true, startedAt = System.currentTimeMillis())
    }

    @Synchronized
    fun recordGoogle(raw: Int, inPeriod: Int, sourceAccepted: Int) {
        snapshot = snapshot.copy(
            googleQueries = snapshot.googleQueries + 1,
            googleRawItems = snapshot.googleRawItems + raw,
            googleInPeriod = snapshot.googleInPeriod + inPeriod,
            googleSourceAccepted = snapshot.googleSourceAccepted + sourceAccepted
        )
    }

    @Synchronized
    fun recordGoogleError() {
        snapshot = snapshot.copy(
            googleQueries = snapshot.googleQueries + 1,
            googleErrors = snapshot.googleErrors + 1
        )
    }

    @Synchronized
    fun recordDirect(d: DesktopDirectNewsCollector.Diagnostics) {
        snapshot = snapshot.copy(
            directSources = d.sourcesScanned,
            directListingPages = d.listingPages,
            directCandidates = d.candidateLinks,
            directTermMatches = d.candidateTermMatches,
            directAccepted = d.accepted,
            directErrors = d.errors,
            directSourceNames = d.sourceNames
        )
    }

    @Synchronized
    fun finish(found: Int, newCount: Int) {
        snapshot = snapshot.copy(
            active = false,
            finishedAt = System.currentTimeMillis(),
            finalFound = found,
            finalNew = newCount
        )
    }

    @Synchronized
    fun fail() {
        snapshot = snapshot.copy(active = false, finishedAt = System.currentTimeMillis())
    }
}
