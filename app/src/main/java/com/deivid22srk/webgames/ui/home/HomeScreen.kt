package com.deivid22srk.webgames.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.deivid22srk.webgames.data.AppViewModel
import com.deivid22srk.webgames.data.Game
import com.deivid22srk.webgames.ui.components.WebGameImage
import com.deivid22srk.webgames.ui.components.rememberMorphingShape

/**
 * Home: grade de jogos com busca, filtros, favoritos e fundo wgpu animado.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    onOpenGame: (String) -> Unit,
    onEditGame: (String) -> Unit,
) {
    val games by vm.games.collectAsState()
    val categories by vm.categories.collectAsState()
    val loading by vm.loading.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }

    val filtered = remember(games, query, selectedCategory) {
        games.filter { g ->
            val matchesQuery = query.isBlank() ||
                g.name.contains(query, ignoreCase = true) ||
                g.category.contains(query, ignoreCase = true) ||
                (g.host?.contains(query, ignoreCase = true) == true)
            val matchesCategory = selectedCategory == null || g.category == selectedCategory
            matchesQuery && matchesCategory
        }
    }
    val favorites = filtered.filter { it.favorite }
    val others = filtered.filter { !it.favorite }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.padding(top = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val headerBlob = rememberMorphingShape(
                            MaterialShapes.Cookie9Sided,
                            MaterialShapes.Sunny,
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(MaterialTheme.colorScheme.primary, headerBlob),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "WebGames",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    val stats = vm.stats.collectAsState().value
                    Text(
                        text = stats?.let {
                            "${it.totalSessions} sessões · ${it.totalSeconds / 60} min jogados"
                        } ?: "Seu catálogo de jogos web",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Buscar jogos…") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { selectedCategory = null },
                                label = { Text("Todos") },
                            )
                        }
                        rowItems(categories) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = {
                                    selectedCategory = if (selectedCategory == cat) null else cat
                                },
                                label = { Text(cat) },
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            if (loading) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ContainedLoadingIndicator()
                    }
                }
            }

            if (favorites.isNotEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    SectionHeader("Favoritos")
                }
                items(favorites, key = { "fav-${it.id}" }) { game ->
                    GameCard(game, onOpenGame, onEditGame, vm)
                }
            }

            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                SectionHeader(if (favorites.isEmpty()) "Todos os jogos" else "Outros")
            }
            items(others, key = { it.id }) { game ->
                GameCard(game, onOpenGame, onEditGame, vm)
            }

            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(96.dp))
            }
        }

        // FAB menu expressivo
        var menuExpanded by remember { mutableStateOf(false) }
        FloatingActionButtonMenu(
            expanded = menuExpanded,
            button = {
                ToggleFloatingActionButton(
                    checked = menuExpanded,
                    onCheckedChange = { menuExpanded = it },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "adicionar")
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            FloatingActionButtonMenuItem(
                onClick = {
                    menuExpanded = false
                    onEditGame("")
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Adicionar jogo") },
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GameCard(
    game: Game,
    onOpenGame: (String) -> Unit,
    onEditGame: (String) -> Unit,
    vm: AppViewModel,
) {
    // forma estática por cartão: morphing por frame em N cartões derruba a UI
    val cardShape = MaterialShapes.Cookie9Sided.toShape()
    Card(
        onClick = { onOpenGame(game.id) },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f),
            ) {
                // moldura com forma expressiva em morphing atrás do ícone
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, cardShape),
                )
                WebGameImage(
                    url = game.iconUrl,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                        .background(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.shapes.medium,
                        ),
                    placeholderIcon = {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
                IconButton(
                    onClick = { vm.toggleFavorite(game) },
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(
                        if (game.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "favorito",
                        tint = if (game.favorite) Color(0xFFFF6B9D) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        game.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    Text(
                        game.category.ifBlank { game.host ?: "" },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onEditGame(game.id) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "editar",
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(onClick = { vm.deleteGame(game.id) {} }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "remover",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
