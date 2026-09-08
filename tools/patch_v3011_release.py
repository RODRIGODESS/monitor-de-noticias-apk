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
workflow = Path(".github/workflows/release.yml")

# Jornal da Record: aceitar clipes e íntegras atuais.
replace_once(
    catalog,
    '            linkHints = listOf("/jr-na-tv/videos/"),',
    '            linkHints = listOf("/jr-na-tv/videos/", "/jr-na-tv/integras/video/"),'
)

# Fontes antigas da Band sem searchPrefix não devem cair numa busca genérica q=.
text = repo.read_text(encoding="utf-8")
start = text.index('        if (source.id in VideoSourceCatalog.portalProgramScanIds) {')
end = text.index('        return runCatching { fetchWebsite(source, capturedAt) }', start)
block = text[start:end]
old = '            if (source.searchUrlTemplate.isNotBlank()) {'
new = '            if (source.searchUrlTemplate.isNotBlank() && source.searchPrefix.isNotBlank()) {'
if block.count(old) != 1:
    raise SystemExit(f"VideoRepository portal fallback: expected one match, got {block.count(old)}")
block = block.replace(old, new, 1)
repo.write_text(text[:start] + block + text[end:], encoding="utf-8")

# Versionamento mantendo applicationId e assinatura.
replace_once(build, '        versionCode = 310\n        versionName = "3.0.10"', '        versionCode = 311\n        versionName = "3.0.11"')

# Workflow/release v3.0.11.
w = workflow.read_text(encoding="utf-8")
replacements = {
    'Prepare v3.0.10 release asset': 'Prepare v3.0.11 release asset',
    'monitor-de-noticias-v3.0.10.apk': 'monitor-de-noticias-v3.0.11.apk',
    'Point v3.0.10 tag to validated main commit': 'Point v3.0.11 tag to validated main commit',
    'refs/tags/v3.0.10': 'refs/tags/v3.0.11',
    'git tag -f v3.0.10': 'git tag -f v3.0.11',
    'tag_name: v3.0.10': 'tag_name: v3.0.11',
    'name: Monitor de Notícias Android v3.0.10': 'name: Monitor de Notícias Android v3.0.11',
}
for old, new in replacements.items():
    if old not in w:
        raise SystemExit(f"release.yml missing {old!r}")
    w = w.replace(old, new)

body_start = w.index('          body: |\n')
files_pos = w.index('          files: monitor-de-noticias-v3.0.11.apk', body_start)
new_body = '''          body: |\n            Monitor de Notícias Android v3.0.11 — ampliação de telejornais e redução de consultas instáveis de vídeo.\n\n            Novas fontes e programas:\n            - Jornal da Band e Brasil Urgente;\n            - Jornal da Record, incluindo clipes e íntegras do JR;\n            - Domingo Espetacular;\n            - Balanço Geral SP e Balanço Geral RJ;\n            - Jornal do Rio;\n            - Fantástico via Globoplay/Jarvis.\n\n            Estratégia de busca:\n            - páginas estáveis de programas passam a fazer uma única varredura recente por fonte e cruzamento local com todos os Termos e Demandas;\n            - Band, R7/Record e SBT deixam de multiplicar consultas quando existe uma listagem recente utilizável;\n            - fallback por busca só é usado quando existe prefixo explícito do programa, evitando consultas genéricas e falsos positivos;\n            - o fluxo Globoplay mantém Edições, Trechos e Jarvis, incluindo JH, JN e agora Fantástico.\n\n            Compatibilidade:\n            - versionCode 311 / versionName 3.0.11;\n            - mesmo applicationId br.com.monitordenoticias.android;\n            - mesma assinatura permanente validada pelo certificado SHA-256 esperado;\n            - instalação direta sobre v3.0.10 preservando Termos, Demandas, histórico, seleção de fontes e configurações;\n            - novas fontes nacionais são adicionadas uma única vez às instalações existentes sem remover escolhas atuais.\n'''
w = w[:body_start] + new_body + w[files_pos:]
workflow.write_text(w, encoding="utf-8")

print("v3.0.11 final release patch applied")
