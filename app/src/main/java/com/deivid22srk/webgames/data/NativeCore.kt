package com.deivid22srk.webgames.data

import android.view.Surface

/**
 * Ponte JNI para o núcleo Rust (libwebgames_core.so).
 * Os métodos correspondem 1:1 aos exports em core/src/jni_bridge.rs.
 */
object NativeCore {
    init {
        System.loadLibrary("webgames_core")
    }

    // inicialização
    external fun init(dataDir: String)

    // catálogo
    external fun catalogList(): String?
    external fun catalogSearch(query: String): String?
    external fun catalogCategories(): String?
    external fun catalogAdd(gameJson: String): String?
    external fun catalogUpdate(gameJson: String): String?
    external fun catalogRemove(id: String): String?
    external fun catalogSetFavorite(id: String, favorite: Boolean): String?
    external fun catalogRecordLaunch(id: String): String?
    external fun catalogRecordSession(id: String, seconds: Int): String?

    // stats / settings
    external fun stats(): String?
    external fun settingsGet(): String?
    external fun settingsSet(settingsJson: String): String?

    // rede
    external fun fetchMetadata(url: String): String?
    external fun checkUrl(url: String): String?

    // scripts de compatibilidade
    external fun compatScript(): String?
    external fun inputBridgeScript(): String?

    // wgpu
    external fun wgpuAdapterInfo(): String?
    external fun wgpuStart(surface: Surface, width: Int, height: Int, quality: Int): String?
    external fun wgpuStop(): String?
    external fun wgpuResize(width: Int, height: Int): String?
    external fun wgpuSetQuality(quality: Int): String?
    external fun wgpuSetFps(fps: Float): String?
    external fun wgpuSetVisible(visible: Boolean): String?
    external fun wgpuSetHudMode(hudMode: Boolean): String?
    external fun wgpuSetHud(hudJson: String): String?

    // Fase B
    external fun experimentalEngineStatus(): String?
    external fun runWasmBenchmark(): String?
}
