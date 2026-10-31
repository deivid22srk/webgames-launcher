package com.deivid22srk.webgames.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Repositório: encapsula chamadas JNI no dispatcher de IO e parse JSON.
 */
class Repository(
    private val json: Json,
    private val filesDir: File,
) {
    private val iconCache: File = File(filesDir, "icons").apply { mkdirs() }

    private inline fun <reified T> parse(raw: String?): T? =
        raw?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

    // ---- catálogo ----
    suspend fun games(): List<Game> = withContext(Dispatchers.IO) {
        parse<List<Game>>(NativeCore.catalogList()) ?: emptyList()
    }

    suspend fun search(query: String): List<Game> = withContext(Dispatchers.IO) {
        parse<List<Game>>(NativeCore.catalogSearch(query)) ?: emptyList()
    }

    suspend fun categories(): List<String> = withContext(Dispatchers.IO) {
        parse<List<String>>(NativeCore.catalogCategories()) ?: emptyList()
    }

    suspend fun addGame(game: Game): Result<Game> = withContext(Dispatchers.IO) {
        val raw = NativeCore.catalogAdd(json.encodeToString(game)) ?: ""
        val res = parse<OkResult>(raw)
        if (res?.ok == true) {
            val added = runCatching {
                val obj = json.parseToJsonElement(raw).jsonObject
                json.decodeFromJsonElement(Game.serializer(), obj["game"]!!)
            }.getOrNull()
            Result.success(added ?: game)
        } else {
            Result.failure(IllegalStateException(res?.error ?: "falha ao salvar"))
        }
    }

    suspend fun updateGame(game: Game): Result<Unit> = withContext(Dispatchers.IO) {
        val res = parse<OkResult>(NativeCore.catalogUpdate(json.encodeToString(game)))
        if (res?.ok == true) Result.success(Unit)
        else Result.failure(IllegalStateException(res?.error ?: "falha ao atualizar"))
    }

    suspend fun removeGame(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val res = parse<OkResult>(NativeCore.catalogRemove(id))
        if (res?.ok == true) Result.success(Unit)
        else Result.failure(IllegalStateException(res?.error ?: "falha ao remover"))
    }

    suspend fun setFavorite(id: String, favorite: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            val res = parse<OkResult>(NativeCore.catalogSetFavorite(id, favorite))
            if (res?.ok == true) Result.success(Unit)
            else Result.failure(IllegalStateException(res?.error ?: "falha"))
        }

    suspend fun recordLaunch(id: String) = withContext(Dispatchers.IO) {
        NativeCore.catalogRecordLaunch(id)
    }

    suspend fun recordSession(id: String, seconds: Int) = withContext(Dispatchers.IO) {
        NativeCore.catalogRecordSession(id, seconds)
    }

    // ---- stats / settings ----
    suspend fun stats(): Stats? = withContext(Dispatchers.IO) { parse(NativeCore.stats()) }

    suspend fun settings(): AppSettings? = withContext(Dispatchers.IO) {
        parse(NativeCore.settingsGet())
    }

    suspend fun saveSettings(settings: AppSettings): Boolean = withContext(Dispatchers.IO) {
        parse<OkResult>(NativeCore.settingsSet(json.encodeToString(settings)))?.ok == true
    }

    // ---- rede ----
    suspend fun fetchMetadata(url: String): WebMetadata? = withContext(Dispatchers.IO) {
        parse(NativeCore.fetchMetadata(url))
    }

    suspend fun checkUrl(url: String): UrlCheck? = withContext(Dispatchers.IO) {
        parse(NativeCore.checkUrl(url))
    }

    // ---- ícones (cache em disco) ----
    suspend fun iconBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) return@withContext null
        val key = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)
        val f = File(iconCache, "$key.img")
        if (f.exists() && f.length() > 0) return@withContext f.readBytes()
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "WebGamesLauncher/0.1")
            conn.inputStream.use { input ->
                val data = input.readBytes()
                if (data.size <= 2_000_000) {
                    f.writeBytes(data)
                    data
                } else null
            }
        } catch (t: Throwable) {
            null
        }
    }

    suspend fun clearIconCache(): Int = withContext(Dispatchers.IO) {
        val files = iconCache.listFiles() ?: return@withContext 0
        var n = 0
        files.forEach { if (it.delete()) n++ }
        n
    }
}
