package com.deivid22srk.webgames.data

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

class AppViewModel : ViewModel() {
    val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var repo: Repository

    private val _settings = MutableStateFlow<AppSettings?>(null)
    val settings: StateFlow<AppSettings?> = _settings.asStateFlow()

    private val _games = MutableStateFlow<List<Game>>(emptyList())
    val games: StateFlow<List<Game>> = _games.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _stats = MutableStateFlow<Stats?>(null)
    val stats: StateFlow<Stats?> = _stats.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun ensureInit(context: Context) {
        if (!this::repo.isInitialized) {
            NativeCore.init(context.filesDir.absolutePath)
            repo = Repository(json, context.filesDir)
            refreshAll()
        }
    }

    private fun repoOrThrow(): Repository = repo

    fun refreshAll() {
        viewModelScope.launch(Dispatchers.IO) {
            _games.value = repoOrThrow().games()
            _categories.value = repoOrThrow().categories()
            _stats.value = repoOrThrow().stats()
            if (_settings.value == null) {
                _settings.value = repoOrThrow().settings()
            }
            _loading.value = false
        }
    }

    fun saveSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        viewModelScope.launch(Dispatchers.IO) {
            repoOrThrow().saveSettings(newSettings)
        }
    }

    fun toggleFavorite(game: Game) {
        viewModelScope.launch(Dispatchers.IO) {
            repoOrThrow().setFavorite(game.id, !game.favorite).onSuccess { refreshAll() }
        }
    }

    fun addGame(game: Game, onDone: (Result<Game>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repoOrThrow().addGame(game)
            result.onSuccess { refreshAll() }
            onDone(result)
        }
    }

    fun updateGame(game: Game, onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repoOrThrow().updateGame(game)
            result.onSuccess { refreshAll() }
            onDone(result)
        }
    }

    fun deleteGame(id: String, onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repoOrThrow().removeGame(id)
            result.onSuccess { refreshAll() }
            onDone(result)
        }
    }

    fun recordLaunch(id: String) {
        viewModelScope.launch(Dispatchers.IO) { repoOrThrow().recordLaunch(id) }
    }

    fun recordSession(id: String, seconds: Int) {
        viewModelScope.launch(Dispatchers.IO) { repoOrThrow().recordSession(id, seconds) }
    }

    fun repository(): Repository = repoOrThrow()
}
