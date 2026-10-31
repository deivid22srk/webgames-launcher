//! Controle do thread de renderização wgpu a partir do JNI.
//! O Kotlin passa a Surface (ANativeWindow) e este módulo gere o ciclo de vida.

use std::sync::atomic::{AtomicBool, AtomicU32, Ordering};
use std::sync::{Arc, Mutex};

/// Uma instância de controle desenhada pelo wgpu (botão virtual / indicador).
#[derive(Debug, Clone, Copy)]
pub struct HudInstance {
    /// posição em pixels da superfície
    pub x: f32,
    pub y: f32,
    pub raio: f32,
    pub color: [f32; 4],
    /// 0..1 (1 = pressionado)
    pub pressed: f32,
}

impl HudInstance {
    pub fn new(x: f32, y: f32, raio: f32, color: [f32; 4], pressed: f32) -> Self {
        Self {
            x,
            y,
            raio,
            color,
            pressed,
        }
    }

    /// 8 floats: [x, y, r, pressed, r, g, b, a]
    pub fn to_f32s(self) -> [f32; 8] {
        [
            self.x,
            self.y,
            self.raio,
            self.pressed,
            self.color[0],
            self.color[1],
            self.color[2],
            self.color[3],
        ]
    }
}

#[derive(Debug, Clone, Default)]
pub struct FrameParams {
    pub quality: u32,
    pub fps: f32,
    /// true = HUD de controles sobre o jogo; false = fundo decorativo da home
    pub hud_mode: bool,
    pub hud: Vec<HudInstance>,
}

pub struct RenderController {
    window_ptr: Arc<Mutex<usize>>,
    size: Arc<Mutex<(u32, u32)>>,
    running: Arc<AtomicBool>,
    visible: Arc<AtomicBool>,
    hud_mode: Arc<AtomicBool>,
    quality: Arc<AtomicU32>,
    fps: Arc<Mutex<f32>>,
    hud: Arc<Mutex<Vec<HudInstance>>>,
    handle: Arc<Mutex<Option<std::thread::JoinHandle<()>>>>,
}

impl Default for RenderController {
    fn default() -> Self {
        Self::new()
    }
}

impl RenderController {
    pub fn new() -> Self {
        Self {
            window_ptr: Arc::new(Mutex::new(0)),
            size: Arc::new(Mutex::new((0, 0))),
            running: Arc::new(AtomicBool::new(false)),
            visible: Arc::new(AtomicBool::new(true)),
            hud_mode: Arc::new(AtomicBool::new(false)),
            quality: Arc::new(AtomicU32::new(1)),
            fps: Arc::new(Mutex::new(0.0)),
            hud: Arc::new(Mutex::new(Vec::new())),
            handle: Arc::new(Mutex::new(None)),
        }
    }

    /// Inicia o loop de render em um thread dedicado (apenas Android).
    #[cfg(target_os = "android")]
    pub fn start(&self, window: usize, width: u32, height: u32) -> Result<(), String> {
        self.stop();
        if window == 0 {
            return Err("ANativeWindow nulo".into());
        }
        *self.window_ptr.lock().unwrap_or_else(|e| e.into_inner()) = window;
        *self.size.lock().unwrap_or_else(|e| e.into_inner()) = (width, height);
        self.running.store(true, Ordering::SeqCst);
        self.visible.store(true, Ordering::SeqCst);

        let running = Arc::clone(&self.running);
        let visible = Arc::clone(&self.visible);
        let hud_mode = Arc::clone(&self.hud_mode);
        let quality = Arc::clone(&self.quality);
        let fps = Arc::clone(&self.fps);
        let hud = Arc::clone(&self.hud);
        let size = Arc::clone(&self.size);

        let handle = std::thread::Builder::new()
            .name("wgpu-render".into())
            .spawn(move || {
                let win = window as *mut core::ffi::c_void;
                let result = (|| -> Result<(), String> {
                    let target = unsafe {
                        webgames_renderer::android_surface::surface_target_from_ptr(win)?
                    };
                    let mut renderer = pollster::block_on(webgames_renderer::GpuRenderer::new(
                        target, width, height,
                    ))
                    .map_err(|e| e.to_string())?;
                    log::info!("wgpu ativo: {}", renderer.adapter_summary().summary_line());
                    while running.load(Ordering::Relaxed) {
                        if !visible.load(Ordering::Relaxed) {
                            std::thread::sleep(std::time::Duration::from_millis(40));
                            continue;
                        }
                        let frame_start = std::time::Instant::now();
                        let (w, h) = *size.lock().unwrap_or_else(|e| e.into_inner());
                        let params = webgames_renderer::FrameParams {
                            quality: quality.load(Ordering::Relaxed),
                            fps: *fps.lock().unwrap_or_else(|e| e.into_inner()),
                            hud_mode: hud_mode.load(Ordering::Relaxed),
                            instances: hud
                                .lock()
                                .unwrap_or_else(|e| e.into_inner())
                                .iter()
                                .flat_map(|i| i.to_f32s())
                                .collect(),
                        };
                        if let Err(e) = renderer.render_if_resized(w, h, &params) {
                            log::warn!("render parou: {e}");
                            break;
                        }
                        // pacing ~66 fps: evita starvation do BufferQueue do
                        // TextureView (dequeueBuffer timed out em alguns SoCs)
                        let min_frame = std::time::Duration::from_millis(15);
                        let elapsed = frame_start.elapsed();
                        if elapsed < min_frame {
                            std::thread::sleep(min_frame - elapsed);
                        }
                    }
                    Ok(())
                })();
                if let Err(e) = result {
                    log::error!("renderer: {e}");
                }
                unsafe { webgames_renderer::android_surface::release_window(win) };
            })
            .map_err(|e| e.to_string())?;
        *self.handle.lock().unwrap_or_else(|e| e.into_inner()) = Some(handle);
        Ok(())
    }

    #[cfg(not(target_os = "android"))]
    pub fn start(&self, _window: usize, _width: u32, _height: u32) -> Result<(), String> {
        Err("superfície wgpu só está disponível no Android".into())
    }

    pub fn stop(&self) {
        self.running.store(false, Ordering::SeqCst);
        let handle = {
            let mut h = self.handle.lock().unwrap_or_else(|e| e.into_inner());
            h.take()
        };
        if let Some(h) = handle {
            let _ = h.join();
        }
        *self.window_ptr.lock().unwrap_or_else(|e| e.into_inner()) = 0;
    }

    pub fn resize(&self, w: u32, h: u32) {
        *self.size.lock().unwrap_or_else(|e| e.into_inner()) = (w.max(1), h.max(1));
    }

    pub fn set_quality(&self, q: u32) {
        self.quality.store(q.min(2), Ordering::Relaxed);
    }

    pub fn set_fps(&self, fps: f32) {
        *self.fps.lock().unwrap_or_else(|e| e.into_inner()) = fps;
    }

    pub fn set_hud(&self, items: Vec<HudInstance>) {
        *self.hud.lock().unwrap_or_else(|e| e.into_inner()) = items;
    }

    pub fn set_visible(&self, v: bool) {
        self.visible.store(v, Ordering::SeqCst);
    }

    pub fn set_hud_mode(&self, m: bool) {
        self.hud_mode.store(m, Ordering::SeqCst);
    }

    pub fn is_running(&self) -> bool {
        self.running.load(Ordering::SeqCst)
    }
}

impl Drop for RenderController {
    fn drop(&mut self) {
        self.stop();
    }
}
