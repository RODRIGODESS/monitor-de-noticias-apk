package br.com.monitordenoticias.desktop

import android.content.Context
import br.com.monitordenoticias.android.BackgroundMonitor
import com.sun.jna.platform.win32.Crypt32Util
import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.PasswordAuthentication
import java.net.URL
import java.util.Base64

/** Configuração de proxy exclusiva da edição Windows. */
object DesktopProxyManager {
    private const val KEY_ENABLED = "desktop_proxy_enabled"
    private const val KEY_HOST = "desktop_proxy_host"
    private const val KEY_PORT = "desktop_proxy_port"
    private const val KEY_USER = "desktop_proxy_user"
    private const val KEY_DOMAIN = "desktop_proxy_domain"
    private const val KEY_PASSWORD_DPAPI = "desktop_proxy_password_dpapi"

    data class Settings(
        val enabled: Boolean,
        val host: String,
        val port: Int,
        val username: String,
        val domain: String,
        val hasSavedPassword: Boolean
    )

    data class SaveResult(val ok: Boolean, val message: String)
    data class TestResult(val ok: Boolean, val message: String)

    fun load(context: Context): Settings {
        val prefs = context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
        return Settings(
            enabled = prefs.getBoolean(KEY_ENABLED, true),
            host = prefs.getString(KEY_HOST, BackgroundMonitor.WINDOWS_PROXY_HOST).orEmpty()
                .ifBlank { BackgroundMonitor.WINDOWS_PROXY_HOST },
            port = prefs.getInt(KEY_PORT, BackgroundMonitor.WINDOWS_PROXY_PORT),
            username = prefs.getString(KEY_USER, "").orEmpty(),
            domain = prefs.getString(KEY_DOMAIN, System.getenv("USERDOMAIN") ?: "").orEmpty(),
            hasSavedPassword = prefs.getString(KEY_PASSWORD_DPAPI, "").orEmpty().isNotBlank()
        )
    }

    fun save(
        context: Context,
        enabled: Boolean,
        host: String,
        port: Int,
        username: String,
        password: String,
        domain: String
    ): SaveResult {
        val cleanHost = host.trim()
        val cleanUser = username.trim()
        val cleanDomain = domain.trim()
        val previous = load(context)

        if (enabled && cleanHost.isBlank()) return SaveResult(false, "Informe o servidor do proxy.")
        if (enabled && port !in 1..65535) return SaveResult(false, "Porta de proxy inválida.")
        if (enabled && cleanUser.isBlank()) return SaveResult(false, "Informe o usuário do proxy.")
        if (enabled && password.isBlank() && !previous.hasSavedPassword) {
            return SaveResult(false, "Informe a senha do proxy.")
        }

        val prefs = context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putString(KEY_HOST, cleanHost)
            .putInt(KEY_PORT, port)
            .putString(KEY_USER, cleanUser)
            .putString(KEY_DOMAIN, cleanDomain)

        if (password.isNotBlank()) {
            val protected = protectWithDpapi(password)
                ?: return SaveResult(false, "Não foi possível proteger a senha com a segurança nativa do Windows.")
            editor.putString(KEY_PASSWORD_DPAPI, protected)
        }

        editor.apply()
        apply(context)
        return SaveResult(true, if (enabled) "Proxy salvo e aplicado." else "Proxy desativado.")
    }

    fun forgetPassword(context: Context) {
        context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PASSWORD_DPAPI, "").apply()
        apply(context)
    }

    fun isReady(context: Context): Boolean {
        val settings = load(context)
        return !settings.enabled || (
            settings.host.isNotBlank() &&
                settings.port in 1..65535 &&
                settings.username.isNotBlank() &&
                settings.hasSavedPassword
            )
    }

    fun apply(context: Context) {
        if (!isWindows()) return
        val settings = load(context)
        if (!settings.enabled) {
            clearJvmProxy()
            return
        }

        System.setProperty("java.net.useSystemProxies", "false")
        System.setProperty("http.proxyHost", settings.host)
        System.setProperty("http.proxyPort", settings.port.toString())
        System.setProperty("https.proxyHost", settings.host)
        System.setProperty("https.proxyPort", settings.port.toString())
        System.setProperty("http.nonProxyHosts", "localhost|127.*|[::1]")
        System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "")
        System.setProperty("jdk.http.auth.proxying.disabledSchemes", "")

        if (settings.domain.isNotBlank()) {
            System.setProperty("http.auth.ntlm.domain", settings.domain)
        } else {
            System.clearProperty("http.auth.ntlm.domain")
        }

        val password = readSavedPassword(context)
        if (settings.username.isBlank() || password.isNullOrEmpty()) {
            Authenticator.setDefault(null)
            return
        }

        val login = if (settings.domain.isNotBlank() && !settings.username.contains('\\')) {
            "${settings.domain}\\${settings.username}"
        } else {
            settings.username
        }

        Authenticator.setDefault(object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? {
                if (requestorType != RequestorType.PROXY) return null
                if (!requestingHost.equals(settings.host, ignoreCase = true)) return null
                if (requestingPort > 0 && requestingPort != settings.port) return null
                return PasswordAuthentication(login, password.toCharArray())
            }
        })
    }

    fun test(context: Context): TestResult {
        if (!isWindows()) return TestResult(false, "Teste de proxy disponível apenas no Windows.")
        val settings = load(context)
        if (!settings.enabled) return TestResult(false, "Ative e salve o proxy antes de testar.")
        if (settings.host.isBlank() || settings.port !in 1..65535) {
            return TestResult(false, "Servidor ou porta do proxy inválidos. Salve a configuração novamente.")
        }
        if (!settings.hasSavedPassword || settings.username.isBlank()) {
            return TestResult(false, "Salve usuário e senha antes de testar.")
        }

        apply(context)
        return try {
            val connection = URL("https://news.google.com/robots.txt").openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 12_000
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 MonitorNoticiasWindows/4.0.3")
            val code = connection.responseCode
            connection.disconnect()
            when {
                code == 407 -> TestResult(false, "Proxy respondeu 407: usuário/senha não aceitos.")
                code in 200..399 -> TestResult(true, "Conexão via proxy OK (HTTP $code).")
                else -> TestResult(false, "Proxy respondeu HTTP $code.")
            }
        } catch (t: Throwable) {
            TestResult(false, "Falha no proxy: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun readSavedPassword(context: Context): String? {
        val protected = context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PASSWORD_DPAPI, "").orEmpty()
        if (protected.isBlank()) return null
        return unprotectWithDpapi(protected)
    }

    private fun clearJvmProxy() {
        listOf(
            "http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort",
            "http.auth.ntlm.domain"
        ).forEach(System::clearProperty)
        Authenticator.setDefault(null)
    }

    private fun protectWithDpapi(secret: String): String? {
        if (!isWindows()) return null
        return runCatching {
            val encrypted = Crypt32Util.cryptProtectData(secret.toByteArray(Charsets.UTF_8))
            Base64.getEncoder().encodeToString(encrypted)
        }.getOrNull()
    }

    private fun unprotectWithDpapi(value: String): String? {
        if (!isWindows()) return null
        return runCatching {
            val encrypted = Base64.getDecoder().decode(value)
            String(Crypt32Util.cryptUnprotectData(encrypted), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun isWindows() = System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true)
}
