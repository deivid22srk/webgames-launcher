//! WebGames Renderer — renderização wgpu/WebGPU do app.
//!
//! - fundo animado da home (gradiente + formas SDF em morphing);
//! - HUD e controles virtuais (círculos instanciados com estado de toque);
//! - diagnóstico do adaptador (backend Vulkan/GLES, limites).

pub mod android_surface;
mod hud;
mod shaders;

use serde::Serialize;
use std::time::Instant;

use crate::hud::MAX_INSTANCES;

pub use crate::hud::FrameParams;

#[derive(Debug, Clone, Serialize)]
pub struct AdapterSummary {
    pub available: bool,
    pub backend: String,
    pub name: String,
    pub vendor: String,
    pub device: String,
    pub device_type: String,
    pub driver: String,
    pub driver_info: String,
    pub max_texture_2d: u32,
    pub max_buffer_size: u64,
    pub error: Option<String>,
}

impl AdapterSummary {
    pub fn summary_line(&self) -> String {
        if self.available {
            format!("{} — {} ({})", self.backend, self.name, self.device_type)
        } else {
            format!("indisponível: {}", self.error.as_deref().unwrap_or("?"))
        }
    }
}

#[derive(Debug)]
pub struct RenderError(String);

impl std::fmt::Display for RenderError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.0)
    }
}

impl std::error::Error for RenderError {}

impl From<wgpu::RequestAdapterError> for RenderError {
    fn from(e: wgpu::RequestAdapterError) -> Self {
        Self(format!("request_adapter: {e}"))
    }
}

impl From<wgpu::CreateSurfaceError> for RenderError {
    fn from(e: wgpu::CreateSurfaceError) -> Self {
        Self(format!("create_surface: {e}"))
    }
}

pub struct GpuRenderer {
    device: wgpu::Device,
    queue: wgpu::Queue,
    surface: wgpu::Surface<'static>,
    surface_config: wgpu::SurfaceConfiguration,
    bg_pipeline: wgpu::RenderPipeline,
    bg_bind_group: wgpu::BindGroup,
    bg_uniform: wgpu::Buffer,
    hud_pipeline: wgpu::RenderPipeline,
    hud_bind_group: wgpu::BindGroup,
    hud_viewport: wgpu::Buffer,
    hud_instances: wgpu::Buffer,
    instance_count: usize,
    size: (u32, u32),
    start: Instant,
    summary: AdapterSummary,
}

impl GpuRenderer {
    /// Cria instância/adaptador/surface e os dois pipelines.
    pub async fn new(
        target: wgpu::SurfaceTargetUnsafe,
        width: u32,
        height: u32,
    ) -> Result<Self, RenderError> {
        let mut desc = wgpu::InstanceDescriptor::new_without_display_handle();
        desc.backends = wgpu::Backends::VULKAN | wgpu::Backends::GL;
        let instance = wgpu::Instance::new(desc);
        let surface = unsafe { instance.create_surface_unsafe(target)? };
        let adapter = instance
            .request_adapter(&wgpu::RequestAdapterOptions {
                power_preference: wgpu::PowerPreference::LowPower,
                compatible_surface: Some(&surface),
                force_fallback_adapter: false,
                apply_limit_buckets: false,
            })
            .await?;

        let (device, queue) = adapter
            .request_device(&wgpu::DeviceDescriptor {
                label: Some("webgames-device"),
                required_features: wgpu::Features::empty(),
                required_limits: wgpu::Limits::default(),
                experimental_features: wgpu::ExperimentalFeatures::disabled(),
                memory_hints: wgpu::MemoryHints::Performance,
                trace: wgpu::Trace::Off,
            })
            .await
            .map_err(|e| RenderError(format!("request_device: {e}")))?;

        let caps = surface.get_capabilities(&adapter);
        let format = caps
            .formats
            .first()
            .copied()
            .unwrap_or(wgpu::TextureFormat::Rgba8Unorm);
        let config = wgpu::SurfaceConfiguration {
            usage: wgpu::TextureUsages::RENDER_ATTACHMENT,
            format,
            width: width.max(1),
            height: height.max(1),
            present_mode: wgpu::PresentMode::Fifo,
            color_space: wgpu::SurfaceColorSpace::Auto,
            alpha_mode: caps.alpha_modes[0],
            view_formats: vec![],
            desired_maximum_frame_latency: 2,
        };
        surface.configure(&device, &config);

        // ---- pipeline do fundo ----
        let bg_module = device.create_shader_module(wgpu::ShaderModuleDescriptor {
            label: Some("bg-shader"),
            source: wgpu::ShaderSource::Wgsl(shaders::BACKGROUND.into()),
        });
        // struct Uniforms { time, width, height, quality, fps } -> 32 bytes alinhados
        let bg_uniform = device.create_buffer(&wgpu::BufferDescriptor {
            label: Some("bg-uniforms"),
            size: 32,
            usage: wgpu::BufferUsages::UNIFORM | wgpu::BufferUsages::COPY_DST,
            mapped_at_creation: false,
        });
        let bg_bgl = device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
            label: Some("bg-bgl"),
            entries: &[wgpu::BindGroupLayoutEntry {
                binding: 0,
                visibility: wgpu::ShaderStages::FRAGMENT,
                ty: wgpu::BindingType::Buffer {
                    ty: wgpu::BufferBindingType::Uniform,
                    has_dynamic_offset: false,
                    min_binding_size: None,
                },
                count: None,
            }],
        });
        let bg_bind_group = device.create_bind_group(&wgpu::BindGroupDescriptor {
            label: Some("bg-bg"),
            layout: &bg_bgl,
            entries: &[wgpu::BindGroupEntry {
                binding: 0,
                resource: bg_uniform.as_entire_binding(),
            }],
        });
        let bg_layout = device.create_pipeline_layout(&wgpu::PipelineLayoutDescriptor {
            label: Some("bg-pl"),
            bind_group_layouts: &[Some(&bg_bgl)],
            immediate_size: 0,
        });
        let bg_pipeline = device.create_render_pipeline(&wgpu::RenderPipelineDescriptor {
            label: Some("bg-pipeline"),
            layout: Some(&bg_layout),
            vertex: wgpu::VertexState {
                module: &bg_module,
                entry_point: Some("vs"),
                buffers: &[],
                compilation_options: Default::default(),
            },
            fragment: Some(wgpu::FragmentState {
                module: &bg_module,
                entry_point: Some("fs"),
                targets: &[Some(wgpu::ColorTargetState {
                    format,
                    blend: Some(wgpu::BlendState::REPLACE),
                    write_mask: wgpu::ColorWrites::ALL,
                })],
                compilation_options: Default::default(),
            }),
            primitive: wgpu::PrimitiveState::default(),
            depth_stencil: None,
            multisample: wgpu::MultisampleState::default(),
            multiview_mask: None,
            cache: None,
        });

        // ---- pipeline do HUD (instanciado) ----
        let hud_module = device.create_shader_module(wgpu::ShaderModuleDescriptor {
            label: Some("hud-shader"),
            source: wgpu::ShaderSource::Wgsl(shaders::HUD.into()),
        });
        let hud_viewport = device.create_buffer(&wgpu::BufferDescriptor {
            label: Some("hud-viewport"),
            size: 16,
            usage: wgpu::BufferUsages::UNIFORM | wgpu::BufferUsages::COPY_DST,
            mapped_at_creation: false,
        });
        let hud_bgl = device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
            label: Some("hud-bgl"),
            entries: &[wgpu::BindGroupLayoutEntry {
                binding: 0,
                visibility: wgpu::ShaderStages::VERTEX,
                ty: wgpu::BindingType::Buffer {
                    ty: wgpu::BufferBindingType::Uniform,
                    has_dynamic_offset: false,
                    min_binding_size: None,
                },
                count: None,
            }],
        });
        let hud_bind_group = device.create_bind_group(&wgpu::BindGroupDescriptor {
            label: Some("hud-bg"),
            layout: &hud_bgl,
            entries: &[wgpu::BindGroupEntry {
                binding: 0,
                resource: hud_viewport.as_entire_binding(),
            }],
        });
        let hud_vbl = crate::hud::vertex_buffer_layout();
        let hud_layout = device.create_pipeline_layout(&wgpu::PipelineLayoutDescriptor {
            label: Some("hud-pl"),
            bind_group_layouts: &[Some(&hud_bgl)],
            immediate_size: 0,
        });
        let hud_pipeline = device.create_render_pipeline(&wgpu::RenderPipelineDescriptor {
            label: Some("hud-pipeline"),
            layout: Some(&hud_layout),
            vertex: wgpu::VertexState {
                module: &hud_module,
                entry_point: Some("vs"),
                buffers: &[Some(hud_vbl)],
                compilation_options: Default::default(),
            },
            fragment: Some(wgpu::FragmentState {
                module: &hud_module,
                entry_point: Some("fs"),
                targets: &[Some(wgpu::ColorTargetState {
                    format,
                    blend: Some(wgpu::BlendState::ALPHA_BLENDING),
                    write_mask: wgpu::ColorWrites::ALL,
                })],
                compilation_options: Default::default(),
            }),
            primitive: wgpu::PrimitiveState {
                topology: wgpu::PrimitiveTopology::TriangleList,
                ..Default::default()
            },
            depth_stencil: None,
            multisample: wgpu::MultisampleState::default(),
            multiview_mask: None,
            cache: None,
        });
        let hud_instances = device.create_buffer(&wgpu::BufferDescriptor {
            label: Some("hud-instances"),
            size: (MAX_INSTANCES * 8 * std::mem::size_of::<f32>()) as u64,
            usage: wgpu::BufferUsages::VERTEX | wgpu::BufferUsages::COPY_DST,
            mapped_at_creation: false,
        });

        let summary = summarize(&adapter, &device);

        Ok(Self {
            device,
            queue,
            surface,
            surface_config: config,
            bg_pipeline,
            bg_bind_group,
            bg_uniform,
            hud_pipeline,
            hud_bind_group,
            hud_viewport,
            hud_instances,
            instance_count: 0,
            size: (width.max(1), height.max(1)),
            start: Instant::now(),
            summary,
        })
    }

    pub fn adapter_summary(&self) -> AdapterSummary {
        self.summary.clone()
    }

    /// Sonda o adaptador sem criar superfície (diagnóstico).
    pub fn probe_adapter() -> AdapterSummary {
        let mut desc = wgpu::InstanceDescriptor::new_without_display_handle();
        desc.backends = wgpu::Backends::VULKAN | wgpu::Backends::GL;
        let instance = wgpu::Instance::new(desc);
        let adapter =
            match pollster::block_on(instance.request_adapter(&wgpu::RequestAdapterOptions {
                power_preference: wgpu::PowerPreference::LowPower,
                compatible_surface: None,
                force_fallback_adapter: false,
                apply_limit_buckets: false,
            })) {
                Ok(a) => a,
                Err(e) => {
                    return AdapterSummary {
                        available: false,
                        backend: String::new(),
                        name: String::new(),
                        vendor: String::new(),
                        device: String::new(),
                        device_type: String::new(),
                        driver: String::new(),
                        driver_info: String::new(),
                        max_texture_2d: 0,
                        max_buffer_size: 0,
                        error: Some(e.to_string()),
                    }
                }
            };
        match pollster::block_on(adapter.request_device(&wgpu::DeviceDescriptor {
            label: Some("webgames-probe"),
            required_features: wgpu::Features::empty(),
            required_limits: wgpu::Limits::default(),
            experimental_features: wgpu::ExperimentalFeatures::disabled(),
            memory_hints: wgpu::MemoryHints::Performance,
            trace: wgpu::Trace::Off,
        })) {
            Ok((device, _)) => summarize(&adapter, &device),
            Err(e) => AdapterSummary {
                available: false,
                backend: String::new(),
                name: String::new(),
                vendor: String::new(),
                device: String::new(),
                device_type: String::new(),
                driver: String::new(),
                driver_info: String::new(),
                max_texture_2d: 0,
                max_buffer_size: 0,
                error: Some(format!("device: {e}")),
            },
        }
    }

    pub fn resize(&mut self, width: u32, height: u32) {
        let (w, h) = (width.max(1), height.max(1));
        if (w, h) == self.size {
            return;
        }
        self.size = (w, h);
        self.surface_config.width = w;
        self.surface_config.height = h;
        self.surface.configure(&self.device, &self.surface_config);
    }

    /// Renderiza um quadro; redimensiona se o tamanho mudou.
    pub fn render_if_resized(
        &mut self,
        width: u32,
        height: u32,
        params: &FrameParams,
    ) -> Result<(), RenderError> {
        if (width.max(1), height.max(1)) != self.size {
            self.resize(width.max(1), height.max(1));
        }
        self.render(params)
    }

    pub fn render(&mut self, params: &FrameParams) -> Result<(), RenderError> {
        let (w, h) = self.size;
        // uniformes do fundo: time, width, height, quality, fps
        let mut uni = [0f32; 8];
        uni[0] = self.start.elapsed().as_secs_f32();
        uni[1] = w as f32;
        uni[2] = h as f32;
        uni[3] = params.quality as f32;
        uni[4] = params.fps;
        self.queue
            .write_buffer(&self.bg_uniform, 0, bytemuck::cast_slice(&uni));
        // viewport do HUD: width, height, dpr(1), time
        let mut vp = [0f32; 4];
        vp[0] = w as f32;
        vp[1] = h as f32;
        vp[3] = uni[0];
        self.queue
            .write_buffer(&self.hud_viewport, 0, bytemuck::cast_slice(&vp));

        // instâncias (8 floats cada): barra de FPS + controles virtuais
        let mut flat: Vec<f32> = Vec::with_capacity((MAX_INSTANCES * 8).min(256));
        if params.fps > 0.0 {
            let ft_ms = 1000.0 / params.fps.max(1.0);
            let frac = (ft_ms / 33.33).clamp(0.05, 1.0);
            let color = if params.fps >= 55.0 {
                [0.2, 0.85, 0.45, 0.9]
            } else if params.fps >= 30.0 {
                [0.95, 0.75, 0.2, 0.9]
            } else {
                [0.95, 0.3, 0.3, 0.9]
            };
            flat.extend_from_slice(&[
                24.0,
                24.0,
                10.0 + 130.0 * frac,
                0.0,
                color[0],
                color[1],
                color[2],
                color[3],
            ]);
        }
        if params.hud_mode {
            flat.extend_from_slice(&params.instances);
        }
        flat.truncate(MAX_INSTANCES * 8);
        self.instance_count = flat.len() / 8;
        if self.instance_count > 0 {
            self.queue
                .write_buffer(&self.hud_instances, 0, bytemuck::cast_slice(&flat));
        }

        let frame = match self.surface.get_current_texture() {
            wgpu::CurrentSurfaceTexture::Success(t) => t,
            wgpu::CurrentSurfaceTexture::Suboptimal(t) => t,
            wgpu::CurrentSurfaceTexture::Lost | wgpu::CurrentSurfaceTexture::Outdated => {
                self.surface.configure(&self.device, &self.surface_config);
                return Ok(());
            }
            wgpu::CurrentSurfaceTexture::Timeout
            | wgpu::CurrentSurfaceTexture::Occluded
            | wgpu::CurrentSurfaceTexture::Validation => return Ok(()),
        };
        let view = frame
            .texture
            .create_view(&wgpu::TextureViewDescriptor::default());
        let mut encoder = self
            .device
            .create_command_encoder(&wgpu::CommandEncoderDescriptor {
                label: Some("frame-encoder"),
            });
        {
            let mut rpass = encoder.begin_render_pass(&wgpu::RenderPassDescriptor {
                label: Some("frame"),
                color_attachments: &[Some(wgpu::RenderPassColorAttachment {
                    view: &view,
                    depth_slice: None,
                    resolve_target: None,
                    ops: wgpu::Operations {
                        load: wgpu::LoadOp::Clear(wgpu::Color {
                            r: 0.05,
                            g: 0.05,
                            b: 0.09,
                            a: if params.hud_mode { 0.0 } else { 1.0 },
                        }),
                        store: wgpu::StoreOp::Store,
                    },
                })],
                depth_stencil_attachment: None,
                timestamp_writes: None,
                occlusion_query_set: None,
                multiview_mask: None,
            });
            if !params.hud_mode {
                rpass.set_pipeline(&self.bg_pipeline);
                rpass.set_bind_group(0, &self.bg_bind_group, &[]);
                rpass.draw(0..3, 0..1);
            }
            if self.instance_count > 0 {
                rpass.set_pipeline(&self.hud_pipeline);
                rpass.set_bind_group(0, &self.hud_bind_group, &[]);
                rpass.set_vertex_buffer(0, self.hud_instances.slice(..));
                rpass.draw(0..6, 0..self.instance_count as u32);
            }
        }
        self.queue.submit(Some(encoder.finish()));
        self.queue.present(frame);
        Ok(())
    }
}

fn summarize(adapter: &wgpu::Adapter, device: &wgpu::Device) -> AdapterSummary {
    let info = adapter.get_info();
    let limits = device.limits();
    AdapterSummary {
        available: true,
        backend: format!("{:?}", info.backend),
        name: info.name.clone(),
        vendor: info.vendor.to_string(),
        device: info.device.to_string(),
        device_type: format!("{:?}", info.device_type),
        driver: info.driver.clone(),
        driver_info: info.driver_info.clone(),
        max_texture_2d: limits.max_texture_dimension_2d,
        max_buffer_size: limits.max_buffer_size,
        error: None,
    }
}
