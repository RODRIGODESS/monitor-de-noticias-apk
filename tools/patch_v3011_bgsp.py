from pathlib import Path
p = Path('app/src/main/java/br/com/monitordenoticias/android/VideoSourceCatalog.kt')
s = p.read_text(encoding='utf-8')
old = '''            landingUrl = "https://record.r7.com/balanco-geral-sp/videos/",\n            linkHints = listOf("/balanco-geral-sp/videos/"),'''
new = '''            landingUrl = "https://record.r7.com/balanco-geral/",\n            linkHints = listOf("/balanco-geral/videos/"),'''
if s.count(old) != 1:
    raise SystemExit(f'expected one BG SP route, found {s.count(old)}')
p.write_text(s.replace(old, new, 1), encoding='utf-8')
