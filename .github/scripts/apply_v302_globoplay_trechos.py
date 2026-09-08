from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match, got {count}")
    return text.replace(old, new, 1)


def replace_between(text: str, start_marker: str, end_marker: str, replacement: str, label: str) -> str:
    start = text.find(start_marker)
    if start < 0:
        raise SystemExit(f"{label}: start marker not found")
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit(f"{label}: end marker not found")
    return text[:start] + replacement + text[end:]


repo_path = ROOT / "app/src/main/java/br/com/monitordenoticias/android/VideoRepository.kt"
text = repo_path.read_text(encoding="utf-8")

text = replace_once(
    text,
    ") {\n    private data class QuerySpec(",
    ") {\n    private val globoplayTrechosCollector = GloboplayTrechosCollector()\n\n    private data class QuerySpec(",
    "collector property",
)

resolve_start = "                fun resolve(item: VideoItem): VideoItem? {"
resolve_end = "\n\n                specs.forEach { spec ->"
new_resolve = '''                fun resolve(item: VideoItem): VideoItem? {
                    if (isYoutubeUrl(item.link)) {
                        return if (isYoutubeVideoUrl(item.link)) item.copy(link = canonicalizeUrl(item.link)) else null
                    }
                    val cacheKey = canonicalKey(item.link)
                    if (resolvedCache.containsKey(cacheKey)) return resolvedCache[cacheKey]

                    val globoplay = isGloboplaySource(source)
                    val resolved = runCatching { resolveDirectVideoPage(source, item, capturedAt) }
                        .onFailure {
                            // Em Trechos, abrir a página individual é enriquecimento opcional.
                            // O card /cenas/ já fornece título e link direto válidos.
                            if (!globoplay) errors++
                        }
                        .getOrNull()
                    val fallback = item.takeIf {
                        globoplay && usefulTitle(it.title) && isSpecificVideoUrl(source, it.link)
                    }?.copy(link = canonicalizeUrl(item.link))
                    val finalItem = resolved ?: fallback
                    resolvedCache[cacheKey] = finalItem
                    return finalItem
                }'''
text = replace_between(text, resolve_start, resolve_end, new_resolve, "resolve fallback")

raw_resolve_line = "                            val item = resolve(raw) ?: return@forEach\n"
raw_resolve_replacement = '''                            // A aba Trechos já entrega títulos jornalísticos úteis.
                            // Filtramos localmente antes de abrir /v/<id>, reduzindo requisições e falhas.
                            if (scanMode && isGloboplaySource(source)) {
                                val shallowBody = "${raw.title} ${raw.summary}"
                                val shallowTermMatch = terms.any { phraseMatches(shallowBody, it) }
                                val shallowDemandMatch = demands.any { demand ->
                                    sourceMatchesDemand(source, demand.vehicle) &&
                                        phraseMatches(shallowBody, demand.subject)
                                }
                                if (!shallowTermMatch && !shallowDemandMatch) return@forEach
                            }

                            val item = resolve(raw) ?: return@forEach
'''
text = replace_once(text, raw_resolve_line, raw_resolve_replacement, "shallow Globoplay filtering")

globo_start = "        if (isGloboplaySource(source)) {\n            val primary = when {"
globo_replacement = '''        if (isGloboplaySource(source)) {
            // Estratégia principal da v3.0.2: telejornal -> página do programa -> /cenas/ (Trechos).
            // A busca geral do Globoplay permanece somente como fallback.
            val trechos = globoplayTrechosCollector.collect(source, capturedAt, onError)
            if (trechos.isNotEmpty()) {
                return trechos.distinctBy { canonicalKey(it.link) }
            }

            val primary = when {'''
text = replace_once(text, globo_start, globo_replacement, "Globoplay Trechos primary strategy")

text = text.replace("MonitorNoticias/3.0.1", "MonitorNoticias/3.0.2")
text = replace_once(
    text,
    "private const val MAX_GLOBOPLAY_ITEMS_PER_SCAN = 10",
    "private const val MAX_GLOBOPLAY_ITEMS_PER_SCAN = 24",
    "Globoplay per-source limit",
)
text = replace_once(
    text,
    "private const val MAX_GLOBOPLAY_GENERAL_ITEMS_PER_SCAN = 18",
    "private const val MAX_GLOBOPLAY_GENERAL_ITEMS_PER_SCAN = 32",
    "Globoplay general limit",
)
repo_path.write_text(text, encoding="utf-8")

# Version bump. No change to applicationId or signing config.
gradle_path = ROOT / "app/build.gradle.kts"
gradle = gradle_path.read_text(encoding="utf-8")
gradle = replace_once(gradle, "versionCode = 301", "versionCode = 302", "versionCode")
gradle = replace_once(gradle, 'versionName = "3.0.1"', 'versionName = "3.0.2"', "versionName")
gradle_path.write_text(gradle, encoding="utf-8")

# Release workflow: keep permanent signing checks unchanged, only move release to v3.0.2.
release_path = ROOT / ".github/workflows/release.yml"
release = release_path.read_text(encoding="utf-8")
if "v3.0.1" not in release:
    raise SystemExit("Expected v3.0.1 release workflow before version bump")
release = release.replace("v3.0.1", "v3.0.2")
release = replace_once(
    release,
    "versionCode 301 / versionName 3.0.1",
    "versionCode 302 / versionName 3.0.2",
    "release version",
)
release = release.replace(
    "Monitor de Notícias Android v3.0.2 — Demandas silenciosas sem achados e varredura de Vídeos otimizada.",
    "Monitor de Notícias Android v3.0.2 — Trechos do Globoplay e varredura de Vídeos otimizada.",
)
release_marker = "            Monitor de Vídeos:\n"
if release_marker not in release:
    raise SystemExit("Release notes marker not found")
release = release.replace(
    release_marker,
    release_marker
    + "            - Globoplay passa a entrar na página de cada telejornal e varrer a aba Trechos (/cenas/);\n"
    + "            - os títulos dos cards são cruzados localmente com Termos e Demandas antes de abrir o vídeo;\n"
    + "            - falha no enriquecimento de um /v/<id> não descarta um card de Trechos já válido;\n",
    1,
)
release = release.replace(
    "instalação direta sobre v3.0.0 preservando dados e configurações.",
    "instalação direta sobre v3.0.1 preservando dados e configurações.",
)
release_path.write_text(release, encoding="utf-8")

# Guardrails after modifications.
repo_final = repo_path.read_text(encoding="utf-8")
gradle_final = gradle_path.read_text(encoding="utf-8")
release_final = release_path.read_text(encoding="utf-8")

assert "globoplayTrechosCollector.collect(source, capturedAt, onError)" in repo_final
assert "shallowTermMatch" in repo_final
assert "versionCode = 302" in gradle_final
assert 'versionName = "3.0.2"' in gradle_final
assert 'applicationId = "br.com.monitordenoticias.android"' in gradle_final
assert "tag_name: v3.0.2" in release_final
assert "EXPECTED_CERT=\"07164d3faf3ee09f241a547d403767c454b226faac26c4b058bc5011d773b1b0\"" in release_final

print("V3.0.2_GLOBOPLAY_TRECHOS_PATCH_OK=true")
