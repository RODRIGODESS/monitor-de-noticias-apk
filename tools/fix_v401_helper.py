from pathlib import Path

p = Path('app/src/main/java/br/com/monitordenoticias/android/MainActivityV28.kt')
text = p.read_text(encoding='utf-8')
old = 'v401InRun(it.capturedAt, newsAttempt, newsCompleted)'
new = 'v401SettingsInRun(it.capturedAt, newsAttempt, newsCompleted)'
if old not in text:
    raise SystemExit('news helper call not found')
text = text.replace(old, new, 1)
old = 'v401InRun(it.capturedAt, videoAttempt, videoCompleted)'
new = 'v401SettingsInRun(it.capturedAt, videoAttempt, videoCompleted)'
if old not in text:
    raise SystemExit('video helper call not found')
text = text.replace(old, new, 1)
helper = '''\nprivate fun v401SettingsInRun(capturedAt: Long, startedAt: Long, completedAt: Long): Boolean {\n    if (capturedAt <= 0L || startedAt <= 0L || completedAt < startedAt) return false\n    return capturedAt in startedAt..(completedAt + 5_000L)\n}\n\n'''
marker = 'private fun v28DateTime(ms: Long): String ='
if marker not in text:
    raise SystemExit('helper insertion marker not found')
text = text.replace(marker, helper + marker, 1)
p.write_text(text, encoding='utf-8')
print('v4.0.1 settings helper fixed')
