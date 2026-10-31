package com.deivid22srk.webgames.ui.game

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import com.deivid22srk.webgames.data.NativeCore

/**
 * TextureView que delega a renderização ao wgpu (Rust).
 * - modo fundo (hudMode = false): fundo animado da home;
 * - modo HUD (hudMode = true): overlay translúcido com controles/FPS sobre o jogo.
 *
 * TextureView é escolhido em vez de SurfaceView porque participa da hierarquia
 * normal de views (z-order previsível dentro do Compose).
 */
class WgpuFxView(
    context: Context,
    private val hudMode: Boolean,
    private val qualityProvider: () -> Int,
) : TextureView(context), TextureView.SurfaceTextureListener {

    private var surface: Surface? = null

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
        val s = Surface(st)
        surface = s
        // hudMode ANTES do start: o primeiro quadro já nasce transparente
        // (sem isso, o fundo opaco aparece sobre o jogo por um instante)
        NativeCore.wgpuSetHudMode(hudMode)
        NativeCore.wgpuSetQuality(qualityProvider())
        NativeCore.wgpuStart(s, width, height, qualityProvider())
        NativeCore.wgpuSetVisible(true)
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
        NativeCore.wgpuResize(width, height)
    }

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        NativeCore.wgpuSetVisible(false)
        NativeCore.wgpuStop()
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit

    fun refreshQuality() {
        NativeCore.wgpuSetQuality(qualityProvider())
    }
}
