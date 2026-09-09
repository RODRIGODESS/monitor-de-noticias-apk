package br.com.monitordenoticias.android

/**
 * Compatibilidade do núcleo compartilhado com a versão desktop.
 * O agendamento real no Windows fica no DesktopController.
 * A configuração/autenticação do proxy fica no DesktopProxyManager.
 */
object BackgroundMonitor {
    const val PREFS = "monitor_prefs"
    const val WINDOWS_PROXY_HOST = "proxy-7dn.mb"
    const val WINDOWS_PROXY_PORT = 6060
}
