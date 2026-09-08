from pathlib import Path

repo = Path('app/src/main/java/br/com/monitordenoticias/android/NewsRepository.kt')
s = repo.read_text()

old = '''        val collected = linkedMapOf<String, News>()
        val newLinks = linkedSetOf<String>()
'''
new = '''        // Snapshot do histórico ANTES desta busca. NOVO significa que a matéria
        // não existia antes da varredura atual. A identidade editorial por veículo +
        // título também protege contra URLs diferentes do Google News para a mesma matéria.
        val historyBeforeRun = db.listNews(limit = 2000)
        val historyByLink = historyBeforeRun.associateBy { it.link }
        val historyByStory = historyBeforeRun.associateBy { storyKey(it) }

        fun reuseHistoricalIdentity(incoming: News): News {
            val previous = historyByLink[incoming.link] ?: historyByStory[storyKey(incoming)]
            return if (previous == null) incoming else mergeNews(
                previous,
                incoming.copy(link = previous.link, capturedAt = previous.capturedAt)
            )
        }

        val collected = linkedMapOf<String, News>()
        val newLinks = linkedSetOf<String>()
'''
assert old in s
s = s.replace(old, new, 1)

old = '''                    .distinctBy { it.link }
                    .toList()

                val mergedBatch = batch.map { incoming ->
'''
new = '''                    .distinctBy { it.link }
                    .map(::reuseHistoricalIdentity)
                    .distinctBy { it.link }
                    .toList()

                val mergedBatch = batch.map { incoming ->
'''
assert old in s
s = s.replace(old, new, 1)

old = '''                outcome.items.forEach { incoming ->
                    val duplicate = collected.values.firstOrNull { storyKey(it) == storyKey(incoming) }
'''
new = '''                outcome.items.forEach { rawIncoming ->
                    val incoming = reuseHistoricalIdentity(rawIncoming)
                    val duplicate = collected.values.firstOrNull { storyKey(it) == storyKey(incoming) }
'''
assert old in s
s = s.replace(old, new, 1)

old = '''            capturedAt = maxOf(previous.capturedAt, incoming.capturedAt)
'''
new = '''            // Primeira captura é imutável: reencontrar o conteúdo nunca o torna NOVO.
            capturedAt = previous.capturedAt.takeIf { it > 0L } ?: incoming.capturedAt
'''
assert old in s
s = s.replace(old, new, 1)

old = '''        val inserted = db.insertNews(matched)
        db.updateDemandStatus(demand.id, checkedAt, matched.size, inserted.size, "")
        return DemandSearchResult(demand, matched, matched.size, inserted.size)
'''
new = '''        val history = db.listNews(limit = 2000)
        val byLink = history.associateBy { it.link }
        val byStory = history.associateBy { storyKey(it) }
        val stableMatched = matched.map { incoming ->
            val previous = byLink[incoming.link] ?: byStory[storyKey(incoming)]
            if (previous == null) incoming else mergeNews(
                previous,
                incoming.copy(link = previous.link, capturedAt = previous.capturedAt)
            )
        }.distinctBy { it.link }

        val inserted = db.insertNews(stableMatched)
        db.updateDemandStatus(demand.id, checkedAt, stableMatched.size, inserted.size, "")
        return DemandSearchResult(demand, stableMatched, stableMatched.size, inserted.size)
'''
assert old in s
s = s.replace(old, new, 1)
repo.write_text(s)

vm = Path('app/src/main/java/br/com/monitordenoticias/android/MonitorViewModel.kt')
v = vm.read_text()
old = '''            _state.value = _state.value.copy(
                news = result.items,
                history = db.listNews(),
'''
new = '''            // Reconstrói a lista a partir do banco após a busca. Assim nenhum capturedAt
            // temporário vindo do RSS sobrevive quando a matéria já existia no histórico.
            val persistedNews = scopedRecent(_state.value)
            _state.value = _state.value.copy(
                news = persistedNews,
                history = db.listNews(),
'''
assert old in v
v = v.replace(old, new, 1)
vm.write_text(v)

p = Path('app/build.gradle.kts')
g = p.read_text().replace('versionCode = 401', 'versionCode = 402').replace('versionName = "4.0.1"', 'versionName = "4.0.2"')
p.write_text(g)
