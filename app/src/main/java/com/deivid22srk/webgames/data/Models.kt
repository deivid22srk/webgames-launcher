package com.deivid22srk.webgames.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Game(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    val iconUrl: String? = null,
    val category: String = "",
    val orientation: String = "auto",
    val engine: String = "webview",
    val favorite: Boolean = false,
    @SerialName("launch_count") val launchCount: Int = 0,
    @SerialName("total_seconds") val totalSeconds: Long = 0,
    @SerialName("last_played_ms") val lastPlayedMs: Long? = null,
    @SerialName("added_ms") val addedMs: Long = 0,
) {
    val host: String?
        get() = runCatching { java.net.URI(url).host }.getOrNull()
}

@Serializable
data class AppSettings(
    val theme: String = "system",
    val dynamicColor: Boolean = true,
    val fpsOverlay: Boolean = false,
    @SerialName("bg_quality") val bgQuality: Int = 1,
    @SerialName("default_engine") val defaultEngine: String = "webview",
    @SerialName("https_only") val httpsOnly: Boolean = true,
    val haptics: Boolean = true,
    /** Abrir no navegador completo quando o site exigir isolamento entre origens. */
    @SerialName("auto_browser_isolation") val autoBrowserIsolation: Boolean = true,
    @SerialName("trusted_domains") val trustedDomains: List<String> = emptyList(),
    @SerialName("gamepad_mapping") val gamepadMapping: Map<String, String> = emptyMap(),
)

@Serializable
data class WebMetadata(
    val url: String = "",
    @SerialName("final_url") val finalUrl: String = "",
    val title: String = "",
    val image: String? = null,
    val icon: String? = null,
    val reachable: Boolean = false,
    val status: Int = 0,
    val error: String? = null,
)

@Serializable
data class UrlCheck(
    val url: String = "",
    val reachable: Boolean = false,
    val status: Int = 0,
    @SerialName("latency_ms") val latencyMs: Long = 0,
    val error: String? = null,
)

@Serializable
data class Stats(
    @SerialName("total_sessions") val totalSessions: Long = 0,
    @SerialName("total_seconds") val totalSeconds: Long = 0,
    @SerialName("last_session_ms") val lastSessionMs: Long? = null,
)

@Serializable
data class AdapterInfo(
    val available: Boolean = false,
    val backend: String = "",
    val name: String = "",
    val vendor: String = "",
    val device: String = "",
    @SerialName("device_type") val deviceType: String = "",
    val driver: String = "",
    @SerialName("driver_info") val driverInfo: String = "",
    @SerialName("max_texture_2d") val maxTexture2d: Long = 0,
    @SerialName("max_buffer_size") val maxBufferSize: Long = 0,
    val error: String? = null,
)

@Serializable
data class EngineStatus(
    val available: Boolean = false,
    val runtime: String = "",
    @SerialName("runtime_version") val runtimeVersion: String = "",
    val detail: String = "",
)

@Serializable
data class BenchmarkResult(
    val engine: String = "",
    @SerialName("fib30_ms") val fib30Ms: Double = 0.0,
    @SerialName("sum_to_10m_ms") val sumTo10mMs: Double = 0.0,
    @SerialName("fib30_result") val fib30Result: Int = 0,
    val ok: Boolean = false,
    val error: String? = null,
)

@Serializable
data class OkResult(val ok: Boolean = false, val error: String? = null)
