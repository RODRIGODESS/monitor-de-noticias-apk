from pathlib import Path

path = Path("app/src/main/java/br/com/monitordenoticias/android/VideoRepository.kt")
text = path.read_text()


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 occurrence, found {count}")
    text = text.replace(old, new, 1)


replace_once(
    r'''            val html = doc.html().replace("\\/", "/")''',
    '''            val html = normalizeEmbeddedScriptText(doc.html(), decodeQuotes = false)''',
    "Globoplay scan HTML normalization",
)

old_script_line = '''            val text = script.data().ifBlank { script.html() }'''
script_count = text.count(old_script_line)
if script_count != 4:
    raise SystemExit(f"script normalization: expected exactly 4 occurrences, found {script_count}")
text = text.replace(
    old_script_line,
    '''            val text = normalizeEmbeddedScriptText(script.data().ifBlank { script.html() })''',
)

helper_marker = '''    private fun cleanJsonText(value: String): String = cleanText(\n'''
helper = r'''    private fun normalizeEmbeddedScriptText(value: String, decodeQuotes: Boolean = true): String {
        var normalized = value
            .replace("\\/", "/")
            .replace("\\u002F", "/", ignoreCase = true)
            .replace("\\u003A", ":", ignoreCase = true)
            .replace("\\u0026", "&", ignoreCase = true)
            .replace("\\u003D", "=", ignoreCase = true)
        if (decodeQuotes) normalized = normalized.replace("\\\"", "\"")
        return normalized
    }

'''
replace_once(helper_marker, helper + helper_marker, "embedded script helper insertion")

replace_once(
    '''    private fun cleanJsonText(value: String): String = cleanText(\n        value\n''',
    '''    private fun cleanJsonText(value: String): String = cleanText(\n        normalizeEmbeddedScriptText(value)\n''',
    "cleanJsonText normalization",
)

replace_once(
    "topics?|categories?",
    "topics?|categor(?:y|ies)",
    "singular/plural category metadata regex",
)
replace_once(
    "description|headline|alternativeHeadline|caption|articleBody",
    "description|seoDescription|summary|headline|alternativeHeadline|caption|articleBody",
    "structured text metadata fields",
)
replace_once(
    "datePublished|uploadDate|dateCreated",
    "datePublished|uploadDate|dateCreated|publishedAt|publicationDate|publishedDate",
    "published date metadata fields",
)

path.write_text(text)
print("Globoplay embedded JSON parser patch applied successfully")
