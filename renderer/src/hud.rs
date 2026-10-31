//! HUD instanciado: cada instância é 8 floats [x, y, raio, pressed, r, g, b, a].

/// Máximo de instâncias por quadro (barra de FPS + botões virtuais).
pub const MAX_INSTANCES: usize = 64;

#[derive(Debug, Clone, Default)]
pub struct FrameParams {
    /// 0 = baixo, 1 = médio, 2 = alto
    pub quality: u32,
    pub fps: f32,
    /// true = HUD sobre o jogo (fundo transparente); false = fundo decorativo
    pub hud_mode: bool,
    /// instâncias já achatadas em f32 (8 por instância)
    pub instances: Vec<f32>,
}

/// Layout de vértice correspondente a 8 floats por instância (2 x Float32x4).
pub fn vertex_buffer_layout() -> wgpu::VertexBufferLayout<'static> {
    wgpu::VertexBufferLayout {
        array_stride: 8 * std::mem::size_of::<f32>() as u64,
        step_mode: wgpu::VertexStepMode::Instance,
        attributes: &[
            wgpu::VertexAttribute {
                format: wgpu::VertexFormat::Float32x4,
                offset: 0,
                shader_location: 0,
            },
            wgpu::VertexAttribute {
                format: wgpu::VertexFormat::Float32x4,
                offset: 4 * std::mem::size_of::<f32>() as u64,
                shader_location: 1,
            },
        ],
    }
}
