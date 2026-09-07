from pathlib import Path

path = Path("app/src/main/java/br/com/monitordenoticias/android/VideoRepository.kt")
text = path.read_text(encoding="utf-8")

text = text.replace(
    'if (isYoutubeUrl(item.link)) return item.copy(link = canonicalizeUrl(item.link))',
    'if (isYoutubeUrl(item.link)) return if (isYoutubeVideoUrl(item.link)) item.copy(link = canonicalizeUrl(item.link)) else null'
)
text = text.replace(
    'if (isYoutubeUrl(url)) return true',
    'if (isYoutubeUrl(url)) return isYoutubeVideoUrl(url)'
)
text = text.replace(
    'if (isYoutubeUrl(item.link)) return true',
    'if (isYoutubeUrl(item.link)) return isYoutubeVideoUrl(item.link)'
)
text = text.replace('MonitorNoticias/2.8.2', 'MonitorNoticias/2.8.3')

needle = '''    private fun isYoutubeUrl(url: String): Boolean {\n        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")\n        return host == "youtu.be" || host.endsWith("youtube.com")\n    }\n'''
replacement = '''    private fun isYoutubeUrl(url: String): Boolean {\n        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")\n        return host == "youtu.be" || host.endsWith("youtube.com")\n    }\n\n    private fun isYoutubeVideoUrl(url: String): Boolean {\n        val uri = runCatching { URI(url) }.getOrNull() ?: return false\n        val host = uri.host.orEmpty().lowercase()\n        val path = uri.path.orEmpty().trim('/')\n        if (host == "youtu.be") return path.substringBefore('/').length >= 6\n        if (!host.endsWith("youtube.com")) return false\n\n        if (uri.path.equals("/watch", ignoreCase = true)) {\n            val videoId = uri.rawQuery.orEmpty()\n                .split('&')\n                .firstOrNull { it.startsWith("v=") }\n                ?.substringAfter("v=")\n                .orEmpty()\n            return videoId.length >= 6\n        }\n\n        val parts = path.split('/').filter { it.isNotBlank() }\n        return parts.size >= 2 && parts.first().lowercase() in setOf("shorts", "live") && parts[1].length >= 6\n    }\n'''

if needle not in text:
    raise SystemExit("YouTube helper insertion point not found")
text = text.replace(needle, replacement)
path.write_text(text, encoding="utf-8")
print("VideoRepository.kt patched for strict YouTube direct-video validation")
