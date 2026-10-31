package com.deivid22srk.webgames.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deivid22srk.webgames.data.AppViewModel
import com.deivid22srk.webgames.data.Game
import com.deivid22srk.webgames.data.UrlCheck
import com.deivid22srk.webgames.data.WebMetadata
import com.deivid22srk.webgames.ui.components.WebGameImage
import kotlinx.coroutines.launch

private val ORIENTATIONS = listOf("auto", "portrait", "landscape")
private val ENGINES = listOf("webview", "chrome", "experimental")

/**
 * Adicionar/editar jogo: nome, URL, ícone automático (og:image/favicon),
 * categoria, orientação e motor. Somente HTTPS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameEditorScreen(
    vm: AppViewModel,
    gameId: String?,
    onDone: () -> Unit,
) {
    val existing = remember(gameId) {
        vm.games.value.firstOrNull { it.id == gameId }
    }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var url by rememberSaveable { mutableStateOf(existing?.url ?: "https://") }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: "") }
    var orientation by rememberSaveable { mutableStateOf(existing?.orientation ?: "auto") }
    var engine by rememberSaveable { mutableStateOf(existing?.engine ?: "webview") }
    var iconUrl by rememberSaveable { mutableStateOf(existing?.iconUrl ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var metadata by remember { mutableStateOf<WebMetadata?>(null) }
    var check by remember { mutableStateOf<UrlCheck?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(metadata) {
        metadata?.let {
            if (name.isBlank()) name = it.title
            iconUrl = it.image ?: it.icon ?: iconUrl
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (existing == null) "Adicionar jogo" else "Editar jogo") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "voltar")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(72.dp)) {
                    WebGameImage(
                        url = iconUrl.ifBlank { null },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.size(12.dp))
                OutlinedButton(onClick = {
                    if (url.startsWith("https://")) {
                        scope.launch {
                            busy = true
                            metadata = vm.repository().fetchMetadata(url.trim())
                            busy = false
                        }
                    } else {
                        error = "Informe uma URL HTTPS antes de buscar dados"
                    }
                }) {
                    Text("Buscar dados")
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL (HTTPS)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Categoria") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Text("Orientação", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ORIENTATIONS.forEach { o ->
                    FilterChip(
                        selected = orientation == o,
                        onClick = { orientation = o },
                        label = { Text(o) },
                    )
                }
            }

            Text("Motor", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ENGINES.forEach { e ->
                    FilterChip(
                        selected = engine == e,
                        onClick = { engine = e },
                        label = { Text(e) },
                    )
                }
            }

            OutlinedButton(onClick = {
                scope.launch {
                    busy = true
                    check = vm.repository().checkUrl(url.trim())
                    busy = false
                }
            }) { Text("Testar URL") }
            check?.let {
                Text(
                    text = if (it.reachable) {
                        "Online (${it.status}, ${it.latencyMs} ms)"
                    } else {
                        "Indisponível: ${it.error ?: "sem resposta"}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (it.reachable) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        val game = (existing ?: Game()).copy(
                            name = name.trim(),
                            url = url.trim(),
                            category = category.trim(),
                            orientation = orientation,
                            engine = engine,
                            iconUrl = iconUrl.ifBlank { null },
                        )
                        if (existing == null) {
                            vm.addGame(game) { result ->
                                result.fold(
                                    onSuccess = { onDone() },
                                    onFailure = { error = it.message },
                                )
                            }
                        } else {
                            vm.updateGame(game) { result ->
                                result.fold(
                                    onSuccess = { onDone() },
                                    onFailure = { error = it.message },
                                )
                            }
                        }
                    },
                    enabled = !busy && name.isNotBlank() && url.isNotBlank(),
                ) { Text("Salvar") }

                if (existing != null) {
                    TextButton(onClick = {
                        vm.deleteGame(existing.id) { result ->
                            result.fold(onSuccess = { onDone() }, onFailure = { error = it.message })
                        }
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(Modifier.size(4.dp))
                        Text("Remover")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
