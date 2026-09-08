from pathlib import Path
p = Path('app/src/main/java/br/com/monitordenoticias/android/VideoSourceCatalog.kt')
s = p.read_text(encoding='utf-8')
old = '            linkHints = listOf("/jr-na-tv/videos/"),'
new = '            linkHints = listOf("/jr-na-tv/videos/", "/jr-na-tv/integras/video/"),'
if s.count(old) != 1:
    raise SystemExit(f'expected one JR linkHints, found {s.count(old)}')
p.write_text(s.replace(old, new, 1), encoding='utf-8')
