from pathlib import Path

repo = Path('.')
controller_path = repo / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop/DesktopController.kt'
main_path = repo / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop/Main.kt'

controller = controller_path.read_text(encoding='utf-8')

old_interval = '''    private val newsIntervalMinutesState = mutableStateOf(prefs.getInt("desktop_news_interval", 30).coerceAtLeast(15))
    var newsIntervalMinutes: Int
        get() = newsIntervalMinutesState.value
        set(v) {
            val value = v.coerceAtLeast(15)
            newsIntervalMinutesState.value = value
            prefs.edit().putInt("desktop_news_interval", value).apply()
        }

'''
new_interval = old_interval + '''    private val demandIntervalMinutesState = mutableStateOf(prefs.getInt(KEY_DEMAND_INTERVAL, 60).coerceAtLeast(15))
    var demandIntervalMinutes: Int
        get() = demandIntervalMinutesState.value
        set(v) {
            val value = v.coerceAtLeast(15)
            demandIntervalMinutesState.value = value
            prefs.edit().putInt(KEY_DEMAND_INTERVAL, value).apply()
        }

    private val newsAutomaticEnabledState = mutableStateOf(prefs.getBoolean(KEY_NEWS_AUTO_ENABLED, true))
    var newsAutomaticEnabled: Boolean
        get() = newsAutomaticEnabledState.value
        set(v) {
            newsAutomaticEnabledState.value = v
            prefs.edit().putBoolean(KEY_NEWS_AUTO_ENABLED, v).apply()
        }

    private val demandAutomaticEnabledState = mutableStateOf(prefs.getBoolean(KEY_DEMAND_AUTO_ENABLED, true))
    var demandAutomaticEnabled: Boolean
        get() = demandAutomaticEnabledState.value
        set(v) {
            demandAutomaticEnabledState.value = v
            prefs.edit().putBoolean(KEY_DEMAND_AUTO_ENABLED, v).apply()
        }

    private val videoAutomaticEnabledState = mutableStateOf(prefs.getBoolean(KEY_VIDEO_AUTO_ENABLED, true))
    var videoAutomaticEnabled: Boolean
        get() = videoAutomaticEnabledState.value
        set(v) {
            videoAutomaticEnabledState.value = v
            prefs.edit().putBoolean(KEY_VIDEO_AUTO_ENABLED, v).apply()
        }

    private val videoAutoTimesState = mutableStateOf(loadVideoAutoTimes())
    var videoAutoTimes: Set<String>
        get() = videoAutoTimesState.value
        private set(v) {
            val clean = v.mapNotNull(::normalizeVideoAutoTime).toSortedSet()
            videoAutoTimesState.value = clean
            prefs.edit().putStringSet(KEY_VIDEO_AUTO_TIMES, clean).apply()
        }

    fun addVideoAutoTime(value: String): Boolean {
        val clean = normalizeVideoAutoTime(value) ?: return false
        videoAutoTimes = videoAutoTimes + clean
        return true
    }

    fun removeVideoAutoTime(value: String) {
        videoAutoTimes = videoAutoTimes - value
    }

    fun resetVideoAutoTimes() {
        videoAutoTimes = DEFAULT_VIDEO_AUTO_TIMES
    }

    private fun loadVideoAutoTimes(): Set<String> {
        val saved = prefs.getStringSet(KEY_VIDEO_AUTO_TIMES, DEFAULT_VIDEO_AUTO_TIMES).orEmpty()
        return saved.mapNotNull(::normalizeVideoAutoTime).toSortedSet()
    }

    private fun normalizeVideoAutoTime(value: String): String? = runCatching {
        LocalTime.parse(value.trim(), TIME).format(TIME)
    }.getOrNull()

    fun nextNewsAutoAt(now: Long = System.currentTimeMillis()): Long {
        if (!automaticMonitoring || !newsAutomaticEnabled) return 0L
        val last = prefs.getLong(KEY_NEWS_SCHEDULE_AT, 0L)
        return if (last <= 0L) now else (last + newsIntervalMinutes * 60_000L).coerceAtLeast(now)
    }

    fun nextDemandAutoAt(now: Long = System.currentTimeMillis()): Long {
        if (!automaticMonitoring || !demandAutomaticEnabled) return 0L
        val last = prefs.getLong(KEY_DEMAND_SCHEDULE_AT, 0L)
        return if (last <= 0L) now else (last + demandIntervalMinutes * 60_000L).coerceAtLeast(now)
    }

    fun nextVideoAutoAt(now: Long = System.currentTimeMillis()): Long {
        if (!automaticMonitoring || !videoAutomaticEnabled || videoAutoTimes.isEmpty()) return 0L
        val zone = ZoneId.systemDefault()
        val current = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDateTime()
        val times = videoAutoTimes.mapNotNull { runCatching { LocalTime.parse(it, TIME) }.getOrNull() }.sorted()
        if (times.isEmpty()) return 0L
        times.forEach { time ->
            val candidate = LocalDateTime.of(current.toLocalDate(), time)
            if (!candidate.isBefore(current)) return candidate.atZone(zone).toInstant().toEpochMilli()
        }
        return LocalDateTime.of(current.toLocalDate().plusDays(1), times.first()).atZone(zone).toInstant().toEpochMilli()
    }

'''
if old_interval not in controller:
    raise RuntimeError('news interval marker not found')
controller = controller.replace(old_interval, new_interval, 1)

old_loop = '''    private suspend fun automationLoop() {
        while (currentCoroutineContext().isActive) {
            if (automaticMonitoring && DesktopProxyManager.isReady(context)) {
                val now = System.currentTimeMillis()
                val lastNews = prefs.getLong("desktop_auto_news_at", 0L)
                if (!newsBusy && now - lastNews >= newsIntervalMinutes * 60_000L) {
                    prefs.edit().putLong("desktop_auto_news_at", now).apply()
                    searchNews(automatic = true)
                }

                val lastDemand = prefs.getLong("desktop_auto_demands_at", 0L)
                if (!demandBusy && now - lastDemand >= 60L * 60L * 1000L) {
                    prefs.edit().putLong("desktop_auto_demands_at", now).apply()
                    searchAllDemands(automatic = true)
                }

                val dt = LocalDateTime.now()
                if (dt.minute < 2 && dt.hour in setOf(8, 12, 15, 19, 21) && !videoBusy) {
                    val slot = dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"))
                    if (prefs.getString("desktop_auto_video_slot", "") != slot) {
                        prefs.edit().putString("desktop_auto_video_slot", slot).apply()
                        searchVideos(automatic = true)
                    }
                }
            }
            delay(60_000L)
        }
    }
'''
new_loop = '''    private suspend fun automationLoop() {
        while (currentCoroutineContext().isActive) {
            if (automaticMonitoring && DesktopProxyManager.isReady(context)) {
                val now = System.currentTimeMillis()

                if (newsAutomaticEnabled) {
                    val lastNews = prefs.getLong(KEY_NEWS_SCHEDULE_AT, 0L)
                    if (!newsBusy && now - lastNews >= newsIntervalMinutes * 60_000L) {
                        prefs.edit().putLong(KEY_NEWS_SCHEDULE_AT, now).apply()
                        searchNews(automatic = true)
                    }
                }

                if (demandAutomaticEnabled) {
                    val lastDemand = prefs.getLong(KEY_DEMAND_SCHEDULE_AT, 0L)
                    if (!demandBusy && now - lastDemand >= demandIntervalMinutes * 60_000L) {
                        prefs.edit().putLong(KEY_DEMAND_SCHEDULE_AT, now).apply()
                        searchAllDemands(automatic = true)
                    }
                }

                if (videoAutomaticEnabled && videoAutoTimes.isNotEmpty() && !videoBusy) {
                    val dt = LocalDateTime.now()
                    val dueTime = videoAutoTimes.firstOrNull { raw ->
                        runCatching {
                            val scheduled = LocalDateTime.of(dt.toLocalDate(), LocalTime.parse(raw, TIME))
                            java.time.Duration.between(scheduled, dt).toMinutes() in 0L..2L
                        }.getOrDefault(false)
                    }
                    if (dueTime != null) {
                        val slot = "${dt.toLocalDate()}-$dueTime"
                        if (prefs.getString(KEY_VIDEO_SCHEDULE_SLOT, "") != slot) {
                            prefs.edit().putString(KEY_VIDEO_SCHEDULE_SLOT, slot).apply()
                            searchVideos(automatic = true)
                        }
                    }
                }
            }
            delay(60_000L)
        }
    }
'''
if old_loop not in controller:
    raise RuntimeError('automationLoop marker not found')
controller = controller.replace(old_loop, new_loop, 1)

companion_marker = '''        private val TIME = DateTimeFormatter.ofPattern("HH:mm")
'''
companion_insert = companion_marker + '''        private val DEFAULT_VIDEO_AUTO_TIMES = setOf("08:00", "12:00", "15:00", "19:00", "21:00")
        private const val KEY_NEWS_AUTO_ENABLED = "desktop_auto_news_enabled"
        private const val KEY_DEMAND_AUTO_ENABLED = "desktop_auto_demand_enabled"
        private const val KEY_VIDEO_AUTO_ENABLED = "desktop_auto_video_enabled"
        private const val KEY_DEMAND_INTERVAL = "desktop_demand_interval"
        private const val KEY_VIDEO_AUTO_TIMES = "desktop_video_auto_times"
        private const val KEY_NEWS_SCHEDULE_AT = "desktop_auto_news_at"
        private const val KEY_DEMAND_SCHEDULE_AT = "desktop_auto_demands_at"
        private const val KEY_VIDEO_SCHEDULE_SLOT = "desktop_auto_video_slot"
'''
if companion_marker not in controller:
    raise RuntimeError('companion TIME marker not found')
controller = controller.replace(companion_marker, companion_insert, 1)
controller_path.write_text(controller, encoding='utf-8')

main = main_path.read_text(encoding='utf-8')
old_settings_head = '''@Composable
private fun SettingsScreen(c: DesktopController) {
    var auto by remember { mutableStateOf(c.automaticMonitoring) }; var startup by remember { mutableStateOf(c.startWithWindows) }; var interval by remember { mutableIntStateOf(c.newsIntervalMinutes) }
    val nr = c.newsAutoReport(); val dr = c.demandAutoReport(); val vr = c.videoAutoReport()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ProxySettingsCard(c) }
        item { Panel { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Monitoramento automático", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); SettingSwitch(auto, { auto = it; c.automaticMonitoring = it }, "Executar buscas automáticas enquanto o aplicativo estiver ativo ou na bandeja"); SettingSwitch(startup, { startup = it; c.startWithWindows = it }, "Iniciar automaticamente após o login no Windows"); Text("Intervalo automático de notícias", fontWeight = FontWeight.SemiBold); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(15, 30, 45, 60).forEach { m -> FilterChip(selected = interval == m, onClick = { interval = m; c.newsIntervalMinutes = m }, label = { Text("$m min") }) } }; Text("Demandas: 1 hora • Vídeos: 08h, 12h, 15h, 19h e 21h.", color = AppMuted) } } }
'''
new_settings_head = '''@Composable
private fun SettingsScreen(c: DesktopController) {
    val nr = c.newsAutoReport(); val dr = c.demandAutoReport(); val vr = c.videoAutoReport()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ProxySettingsCard(c) }
        item { AutomationSettingsCard(c) }
'''
if old_settings_head not in main:
    raise RuntimeError('SettingsScreen automation panel marker not found')
main = main.replace(old_settings_head, new_settings_head, 1)

old_home_schedule = '''Text("Notícias: a cada ${c.newsIntervalMinutes} min • Demandas: 1 hora • Vídeos: 08h, 12h, 15h, 19h e 21h", color = AppMuted)'''
new_home_schedule = '''Text(
                            "Notícias: ${if (c.newsAutomaticEnabled) "a cada ${c.newsIntervalMinutes} min" else "pausadas"} • " +
                                "Demandas: ${if (c.demandAutomaticEnabled) "a cada ${c.demandIntervalMinutes} min" else "pausadas"} • " +
                                "Vídeos: ${if (c.videoAutomaticEnabled) c.videoAutoTimes.joinToString(", ") else "pausados"}",
                            color = AppMuted
                        )'''
if old_home_schedule not in main:
    raise RuntimeError('Home schedule marker not found')
main = main.replace(old_home_schedule, new_home_schedule, 1)
main_path.write_text(main, encoding='utf-8')
