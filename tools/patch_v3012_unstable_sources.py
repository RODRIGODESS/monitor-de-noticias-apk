from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, got {count}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")

catalog = Path("app/src/main/java/br/com/monitordenoticias/android/VideoSourceCatalog.kt")
repo = Path("app/src/main/java/br/com/monitordenoticias/android/VideoRepository.kt")
build = Path("app/build.gradle.kts")

# Band: usar páginas atuais de programa e fallbacks específicos por praça.
replace_once(
    catalog,
    '            landingUrl = "https://www.band.com.br/noticias/jornal-da-band/videos",',
    '            landingUrl = "https://www.band.com.br/programas/jornal-da-band",'
)
replace_once(
    catalog,
    '            landingUrl = "https://www.band.com.br/noticias/brasil-urgente/videos",',
    '            landingUrl = "https://www.band.com.br/programas/brasil-urgente",'
)
replace_once(
    catalog,
    '            landingUrl = "https://record.r7.com/balanco-geral/",',
    '            landingUrl = "https://record.r7.com/balanco-geral/videos/",'
)
replace_once(
    catalog,
    '            linkHints = listOf("/domingo-espetacular/videos/"),',
    '            linkHints = listOf("/domingo-espetacular/videos/", "/domingo-espetacular/video/"),'
)
replace_once(
    catalog,
    '            landingUrl = "https://sbtnews.sbt.com.br/videos",',
    '            landingUrl = "https://sbtnews.sbt.com.br/videos/ao-vivo",'
)

regional_replacements = {
    '            landingUrl = "https://www.band.com.br/band-brasilia/videos", linkHints = listOf("/band-brasilia/videos/"), aliases = listOf("Band Brasília", "Band DF"),\n            searchUrlTemplate = "https://www.band.com.br/busca?q={query}"':
    '            landingUrl = "https://www.band.com.br/band-brasilia/videos", linkHints = listOf("/band-brasilia/videos/", "/videos/"), aliases = listOf("Band Brasília", "Band DF"),\n            searchUrlTemplate = "https://www.band.com.br/busca?q={query}", searchPrefix = "Band Brasília"',
    '            landingUrl = "https://www.band.com.br/band-minas", linkHints = listOf("/band-minas/videos/", "/videos/"), aliases = listOf("Band Minas", "Band Minas Gerais"),\n            searchUrlTemplate = "https://www.band.com.br/busca?q={query}"':
    '            landingUrl = "https://www.band.com.br/minas-gerais", linkHints = listOf("/band-minas/videos/", "/minas-gerais/videos/", "/videos/"), aliases = listOf("Band Minas", "Band Minas Gerais"),\n            searchUrlTemplate = "https://www.band.com.br/busca?q={query}", searchPrefix = "Band Minas"',
    '            landingUrl = "https://www.band.com.br/rio-de-janeiro/videos", linkHints = listOf("/rio-de-janeiro/videos/"), aliases = listOf("Band Rio", "Band Rio de Janeiro"),\n            searchUrlTemplate = "https://www.band.com.br/busca?q={query}"':
    '            landingUrl = "https://www.band.com.br/rio-de-janeiro/videos", linkHints = listOf("/rio-de-janeiro/videos/", "/videos/"), aliases = listOf("Band Rio", "Band Rio de Janeiro"),\n            searchUrlTemplate = "https://www.band.com.br/busca?q={query}", searchPrefix = "Band Rio"',
    '            landingUrl = "https://www.band.com.br/band-parana/videos", linkHints = listOf("/band-parana/videos/"), aliases = listOf("Band Paraná", "Band PR"),\n            searchUrlTemplate = "https://www.band.com.br/busca?q={query}"':
    '            landingUrl = "https://www.band.com.br/band-parana", linkHints = listOf("/band-parana/videos/", "/videos/"), aliases = listOf("Band Paraná", "Band PR"),\n            searchUrlTemplate = "https://www.band.com.br/busca?q={query}", searchPrefix = "Band Paraná"',
    '            landingUrl = "https://www.band.com.br/band-bahia", linkHints = listOf("/band-bahia/videos/", "/videos/"), aliases = listOf("Band Bahia", "Band BA"),\n            searchUrlTemplate = "https://www.band.com.br/busca?q={query}"':
    '            landingUrl = "https://www.band.com.br/ao-vivo/band-bahia", linkHints = listOf("/band-bahia/videos/", "/videos/"), aliases = listOf("Band Bahia", "Band BA"),\n            searchUrlTemplate = "https://www.band.com.br/busca?q={query}", searchPrefix = "Band Bahia"',
}
text = catalog.read_text(encoding="utf-8")
for old, new in regional_replacements.items():
    if text.count(old) != 1:
        raise SystemExit(f"catalog regional replacement expected one match, got {text.count(old)}: {old[:100]!r}")
    text = text.replace(old, new, 1)
catalog.write_text(text, encoding="utf-8")

# Fallback de portal só é útil quando há um prefixo explícito da fonte/programa.
text = repo.read_text(encoding="utf-8")
start = text.index('        if (source.id in VideoSourceCatalog.portalProgramScanIds) {')
end = text.index('        return runCatching { fetchWebsite(source, capturedAt) }', start)
block = text[start:end]
old = '            if (source.searchUrlTemplate.isNotBlank()) {'
new = '            if (source.searchUrlTemplate.isNotBlank() && source.searchPrefix.isNotBlank()) {'
if block.count(old) != 1:
    raise SystemExit(f"portal fallback expected one match, got {block.count(old)}")
block = block.replace(old, new, 1)
repo.write_text(text[:start] + block + text[end:], encoding="utf-8")

# Uma falha auxiliar isolada não torna toda a fonte instável. Para fontes de varredura,
# exigimos falha combinada de rotas; para portais com fallback específico, as duas rotas
# precisam falhar. Isso preserva falhas reais sem condenar Trechos/fallback isoladamente.
text = repo.read_text(encoding="utf-8")
old = '''                if (isSourceScanMode(source) && sourceScanCandidates.isEmpty() && sourceRequestFailures > 0) {\n                    markCurrentSourceUnstable()\n                }'''
new = '''                if (isSourceScanMode(source) && sourceScanCandidates.isEmpty() && sourceRequestFailures > 0) {\n                    val actionableScanFailure = when {\n                        source.youtubeHandle.isNotBlank() -> sourceRequestFailures >= 2\n                        isGloboplaySource(source) -> sourceRequestFailures >= 2\n                        source.id in VideoSourceCatalog.portalProgramScanIds && source.searchPrefix.isNotBlank() ->\n                            sourceFailureStages.containsKey("Portal • página do programa") &&\n                                sourceFailureStages.containsKey("Portal • busca fallback")\n                        else -> sourceFailureStages.containsKey("Portal • página do programa")\n                    }\n                    if (actionableScanFailure) markCurrentSourceUnstable()\n                }'''
if text.count(old) != 1:
    raise SystemExit(f"scan instability block expected one match, got {text.count(old)}")
repo.write_text(text.replace(old, new, 1), encoding="utf-8")

replace_once(build, '        versionCode = 311\n        versionName = "3.0.11"', '        versionCode = 312\n        versionName = "3.0.12"')

print("v3.0.12 unstable-source patch applied")
