from pathlib import Path
p = Path('app/src/main/java/br/com/monitordenoticias/android/VideoRepository.kt')
s = p.read_text(encoding='utf-8')
old = '            if (source.searchUrlTemplate.isNotBlank()) {'
new = '            if (source.searchUrlTemplate.isNotBlank() && source.searchPrefix.isNotBlank()) {'
# This exact branch appears once in collectRecentBySource after the portal-program landing attempt.
start = s.index('        if (source.id in VideoSourceCatalog.portalProgramScanIds) {')
end = s.index('        return runCatching { fetchWebsite(source, capturedAt) }', start)
block = s[start:end]
if block.count(old) != 1:
    raise SystemExit(f'expected one portal fallback condition, found {block.count(old)}')
block = block.replace(old, new, 1)
p.write_text(s[:start] + block + s[end:], encoding='utf-8')
