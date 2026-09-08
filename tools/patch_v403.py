from pathlib import Path

# SourceCatalog: add specialized media group and include it in all sources.
p = Path('app/src/main/java/br/com/monitordenoticias/android/SourceCatalog.kt')
s = p.read_text()
needle = '''    val byState = listOf(\n'''
specialized = '''    val specialized = listOf(\n        specialized("especializada-defesa-em-foco", "Defesa em Foco", "DefesaEmFoco", "defesaemfoco.com.br"),\n        specialized("especializada-defesa-aerea-naval", "Defesa Aérea & Naval", "Defesa Aerea e Naval", "Defesa Aérea Naval", "defesaaereanaval.com.br"),\n        specialized("especializada-defesanet", "DefesaNet", "Defesa Net", "defesanet.com.br"),\n        specialized("especializada-tecnodefesa", "Tecnologia & Defesa", "Tecnodefesa", "Tecnologia e Defesa", "tecnodefesa.com.br"),\n        specialized("especializada-zona-militar", "Zona Militar", "Zona-Militar", "zona-militar.com"),\n        specialized("especializada-click-petroleo-gas", "Click Petróleo e Gás", "Click Petroleo e Gas", "CPG", "clickpetroleoegas.com.br"),\n        specialized("especializada-poder-naval", "Poder Naval", "Naval.com.br", "naval.com.br"),\n        specialized("especializada-agencia-marinha", "Agência Marinha de Notícias", "Agencia Marinha de Noticias", "Agência Marinha", "agencia.marinha.mil.br"),\n        specialized("especializada-sociedade-militar", "Revista Sociedade Militar", "Sociedade Militar", "RSM", "sociedademilitar.com.br")\n    )\n\n'''
if 'val specialized = listOf(' not in s:
    s = s.replace(needle, specialized + needle)
s = s.replace('val all: List<MediaSource> = (national + byState).distinctBy { it.id }',
              'val all: List<MediaSource> = (national + specialized + byState).distinctBy { it.id }')
helper_needle = '''    private fun state(\n'''
helper = '''    private fun specialized(id: String, name: String, vararg aliases: String) = MediaSource(\n        id = id,\n        name = name,\n        region = NATIONAL_REGION,\n        state = "BR",\n        stateName = "Brasil",\n        group = "Mídia especializada",\n        aliases = aliases.toList()\n    )\n\n'''
if 'group = "Mídia especializada"' not in s:
    s = s.replace(helper_needle, helper + helper_needle)
p.write_text(s)

# Direct latest-news scanning for specialized sources.
p = Path('app/src/main/java/br/com/monitordenoticias/android/NewsLatestCollector.kt')
s = p.read_text()
s = s.replace('val scope = if (searchAllSources) SourceCatalog.national else selectedSources',
              'val scope = if (searchAllSources) SourceCatalog.national + SourceCatalog.specialized else selectedSources')
route_needle = '''            "nacional-o-globo" to Route("https://oglobo.globo.com/ultimas-noticias/", setOf("oglobo.globo.com"))\n'''
route_replacement = '''            "nacional-o-globo" to Route("https://oglobo.globo.com/ultimas-noticias/", setOf("oglobo.globo.com")),\n            "especializada-defesa-em-foco" to Route("https://www.defesaemfoco.com.br/", setOf("defesaemfoco.com.br")),\n            "especializada-defesa-aerea-naval" to Route("https://www.defesaaereanaval.com.br/", setOf("defesaaereanaval.com.br")),\n            "especializada-defesanet" to Route("https://www.defesanet.com.br/categoria/defesa/", setOf("defesanet.com.br")),\n            "especializada-tecnodefesa" to Route("https://tecnodefesa.com.br/", setOf("tecnodefesa.com.br")),\n            "especializada-zona-militar" to Route("https://www.zona-militar.com/pt/", setOf("zona-militar.com")),\n            "especializada-click-petroleo-gas" to Route("https://clickpetroleoegas.com.br/", setOf("clickpetroleoegas.com.br")),\n            "especializada-poder-naval" to Route("https://www.naval.com.br/", setOf("naval.com.br")),\n            "especializada-agencia-marinha" to Route("https://www.agencia.marinha.mil.br/portal", setOf("agencia.marinha.mil.br")),\n            "especializada-sociedade-militar" to Route("https://www.sociedademilitar.com.br/", setOf("sociedademilitar.com.br"))\n'''
if 'especializada-poder-naval' not in s:
    s = s.replace(route_needle, route_replacement)
p.write_text(s)

# Version bump.
p = Path('app/build.gradle.kts')
s = p.read_text().replace('versionCode = 402', 'versionCode = 403').replace('versionName = "4.0.2"', 'versionName = "4.0.3"')
p.write_text(s)
