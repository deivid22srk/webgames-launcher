package com.deivid22srk.webgames.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deivid22srk.webgames.data.AppSettings
import com.deivid22srk.webgames.data.AppViewModel
import kotlinx.coroutines.launch

private val THEMES = listOf("system", "light", "dark")
private val ENGINES = listOf("webview", "chrome", "experimental")

/** Ajustes: tema, cor dinâmica, motor, FPS, qualidade, domínios, cache, gamepad. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsState()
    val current = settings ?: AppSettings()
    val scope = rememberCoroutineScope()
    var newDomain by rememberSaveable { mutableStateOf("") }
    var cacheMsg by remember { mutableStateOf<String?>(null) }

    fun update(transform: (AppSettings) -> AppSettings) {
        vm.saveSettings(transform(current))
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Ajustes") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Tema", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                THEMES.forEach { t ->
                    FilterChip(
                        selected = current.theme == t,
                        onClick = { update { it.copy(theme = t) } },
                        label = { Text(t) },
                    )
                }
            }

            SettingSwitch(
                title = "Cor dinâmica (Material You)",
                checked = current.dynamicColor,
                onChecked = { v -> update { it.copy(dynamicColor = v) } },
            )
            SettingSwitch(
                title = "Overlay de FPS",
                checked = current.fpsOverlay,
                onChecked = { v -> update { it.copy(fpsOverlay = v) } },
            )
            SettingSwitch(
                title = "Feedback háptico",
                checked = current.haptics,
                onChecked = { v -> update { it.copy(haptics = v) } },
            )
            SettingSwitch(
                title = "Somente HTTPS",
                checked = current.httpsOnly,
                onChecked = { v -> update { it.copy(httpsOnly = v) } },
            )
            SettingSwitch(
                title = "Abrir no navegador quando o site exigir isolamento",
                checked = current.autoBrowserIsolation,
                onChecked = { v -> update { it.copy(autoBrowserIsolation = v) } },
            )
            Text(
                "Sites com emulador (ex.: Def Jam) pedem \"isolamento entre origens\" " +
                    "(SharedArrayBuffer), que o WebView do Android não oferece — o app abre " +
                    "esses jogos no navegador completo (Custom Tabs/Chrome).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                "Qualidade do fundo (wgpu): ${
                    listOf("baixa", "média", "alta")[current.bgQuality.coerceIn(0, 2)]
                }",
                style = MaterialTheme.typography.titleMedium,
            )
            Slider(
                value = current.bgQuality.coerceIn(0, 2).toFloat(),
                onValueChange = { v ->
                    update { it.copy(bgQuality = v.toInt().coerceIn(0, 2)) }
                },
                valueRange = 0f..2f,
                steps = 1,
            )

            HorizontalDivider()

            Text("Motor padrão", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ENGINES.forEach { e ->
                    FilterChip(
                        selected = current.defaultEngine == e,
                        onClick = { update { it.copy(defaultEngine = e) } },
                        label = { Text(e) },
                    )
                }
            }

            HorizontalDivider()

            Text("Domínios confiáveis", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newDomain,
                    onValueChange = { newDomain = it },
                    label = { Text("ex.: jogo.exemplo.com") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                IconButton(onClick = {
                    val d = newDomain.trim().removePrefix("https://").removeSuffix("/")
                    if (d.isNotBlank() && "." in d) {
                        update { it.copy(trustedDomains = it.trustedDomains + d) }
                        newDomain = ""
                    }
                }) { Icon(Icons.Filled.Add, contentDescription = "adicionar domínio") }
            }
            current.trustedDomains.forEach { d ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(d, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        update { it.copy(trustedDomains = it.trustedDomains - d) }
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "remover $d")
                    }
                }
            }

            HorizontalDivider()

            Text("Mapeamento de gamepad", style = MaterialTheme.typography.titleMedium)
            current.gamepadMapping.entries.sortedBy { it.key }.forEach { (btn, name) ->
                Text("Botão $btn → $name", style = MaterialTheme.typography.bodyMedium)
            }

            HorizontalDivider()

            OutlinedButton(onClick = {
                scope.launch {
                    val n = vm.repository().clearIconCache()
                    cacheMsg = "$n arquivos de ícone removidos"
                }
            }) { Text("Limpar cache de ícones") }
            cacheMsg?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
