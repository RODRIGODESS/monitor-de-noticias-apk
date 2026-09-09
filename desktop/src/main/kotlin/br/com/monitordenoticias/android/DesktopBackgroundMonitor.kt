package br.com.monitordenoticias.android

/**
 * Compatibilidade do núcleo compartilhado com a versão desktop.
 * O agendamento real no Windows fica no DesktopController.
 *
 * O proxy abaixo é exclusivo da edição Windows: como o núcleo compartilhado usa
 * java.net/HttpURLConnection e Jsoup, as propriedades padrão da JVM fazem com que
 * as novas conexões HTTP e HTTPS do aplicativo sejam encaminhadas pelo proxy.
 * Nenhum arquivo da versão Android é alterado por esta configuração.
 */
object BackgroundMonitor {
    const val PREFS = "monitor_prefs"

    const val WINDOWS_PROXY_HOST = "proxy-7dn.mb"
    const val WINDOWS_PROXY_PORT = 6060

    init {
        applyWindowsProxy()
    }

    private fun applyWindowsProxy() {
        if (!System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true)) return

        System.setProperty("java.net.useSystemProxies", "false")
        System.setProperty("http.proxyHost", WINDOWS_PROXY_HOST)
        System.setProperty("http.proxyPort", WINDOWS_PROXY_PORT.toString())
        System.setProperty("https.proxyHost", WINDOWS_PROXY_HOST)
        System.setProperty("https.proxyPort", WINDOWS_PROXY_PORT.toString())

        // Mantém acessos locais fora do proxy.
        System.setProperty("http.nonProxyHosts", "localhost|127.*|[::1]")
    }
}
