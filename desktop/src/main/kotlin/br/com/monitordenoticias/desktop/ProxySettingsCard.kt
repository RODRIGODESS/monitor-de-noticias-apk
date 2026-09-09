package br.com.monitordenoticias.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProxySettingsCard(c: DesktopController) {
    var loaded by remember { mutableStateOf(DesktopProxyManager.load(c.context)) }
    var enabled by remember(loaded) { mutableStateOf(loaded.enabled) }
    var host by remember(loaded) { mutableStateOf(loaded.host) }
    var port by remember(loaded) { mutableStateOf(loaded.port.toString()) }
    var username by remember(loaded) { mutableStateOf(loaded.username) }
    var domain by remember(loaded) { mutableStateOf(loaded.domain) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember {
        mutableStateOf(
            if (loaded.enabled && !DesktopProxyManager.isReady(c.context))
                "Configure usuário e senha para liberar as buscas pelo proxy."
            else if (loaded.enabled)
                "Proxy configurado: ${loaded.host}:${loaded.port}"
            else
                "Proxy desativado."
        )
    }
    var messageOk by remember { mutableStateOf(!loaded.enabled || loaded.hasSavedPassword) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Default.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Proxy autenticado", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Configuração dentro do aplicativo. A senha fica protegida pelo Windows DPAPI.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (enabled) "Ativo" else "Desativado", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }

            HorizontalDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Servidor") },
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { if (it.all(Char::isDigit) && it.length <= 5) port = it },
                    label = { Text("Porta") },
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.width(150.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Usuário") },
                    leadingIcon = { Icon(Icons.Default.Key, null) },
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("Domínio (opcional)") },
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(if (loaded.hasSavedPassword) "Senha — deixe em branco para manter a salva" else "Senha") },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }, enabled = enabled) {
                        Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )

            if (loaded.hasSavedPassword) {
                Text(
                    "✓ Senha protegida no perfil do usuário atual do Windows.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (messageOk) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (messageOk) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = if (messageOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(message, style = MaterialTheme.typography.labelLarge)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = !busy,
                    onClick = {
                        val portNumber = port.toIntOrNull() ?: -1
                        val result = DesktopProxyManager.save(c.context, enabled, host, portNumber, username, password, domain)
                        message = result.message
                        messageOk = result.ok
                        if (result.ok) {
                            password = ""
                            loaded = DesktopProxyManager.load(c.context)
                            c.refresh()
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Salvar e aplicar")
                }

                OutlinedButton(
                    enabled = !busy && enabled,
                    onClick = {
                        scope.launch {
                            val portNumber = port.toIntOrNull() ?: -1
                            val save = DesktopProxyManager.save(c.context, enabled, host, portNumber, username, password, domain)
                            if (!save.ok) {
                                message = save.message
                                messageOk = false
                                return@launch
                            }
                            password = ""
                            loaded = DesktopProxyManager.load(c.context)
                            c.refresh()
                            busy = true
                            message = "Testando conexão via ${loaded.host}:${loaded.port}..."
                            messageOk = true
                            val result = withContext(Dispatchers.IO) { DesktopProxyManager.test(c.context) }
                            busy = false
                            message = result.message
                            messageOk = result.ok
                            c.refresh()
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(7.dp))
                    }
                    Text(if (busy) "Testando..." else "Testar conexão")
                }

                if (loaded.hasSavedPassword) {
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            DesktopProxyManager.forgetPassword(c.context)
                            loaded = DesktopProxyManager.load(c.context)
                            password = ""
                            message = "Senha removida. Informe uma nova senha para usar o proxy."
                            messageOk = false
                            c.refresh()
                        }
                    ) {
                        Text("Remover senha salva")
                    }
                }

                Spacer(Modifier.weight(1f))
                Text(
                    "${host.ifBlank { "proxy" }}:${port.ifBlank { "—" }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}