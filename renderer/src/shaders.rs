//! Shaders WGSL embarcados.

/// Fundo animado da home: gradiente profundo + "blobs" SDF que respiram.
pub const BACKGROUND: &str = r#"
struct Uniforms {
    time: f32,
    width: f32,
    height: f32,
    quality: f32,
    fps: f32,
};
@group(0) @binding(0) var<uniform> u: Uniforms;

@vertex
fn vs(@builtin(vertex_index) vi: u32) -> @builtin(position) vec4<f32> {
    var p = array<vec2<f32>, 3>(
        vec2<f32>(-1.0, -1.0),
        vec2<f32>( 3.0, -1.0),
        vec2<f32>(-1.0,  3.0)
    );
    return vec4<f32>(p[vi], 0.0, 1.0);
}

fn sd_circle(p: vec2<f32>, c: vec2<f32>, r: f32) -> f32 {
    return length(p - c) - r;
}

@fragment
fn fs(@builtin(position) frag: vec4<f32>) -> @location(0) vec4<f32> {
    let res = vec2<f32>(u.width, u.height);
    let uv = frag.xy / res;
    let aspect = res.x / max(res.y, 1.0);
    let p = vec2<f32>((uv.x - 0.5) * aspect, 0.5 - uv.y);

    // gradiente base escuro (roxo/azul profundo)
    var col = mix(vec3<f32>(0.055, 0.045, 0.14), vec3<f32>(0.11, 0.09, 0.24), uv.y);

    // número de blobs por qualidade: 2 / 4 / 6
    let max_blobs = i32(u.quality) * 2 + 2;
    let t = u.time;

    var glow = 0.0;
    var tint = vec3<f32>(0.55, 0.35, 1.0);
    for (var i: i32 = 0; i < 6; i = i + 1) {
        if (i >= max_blobs) { break; }
        let fi = f32(i);
        let c = vec2<f32>(
            0.5 * aspect * sin(t * (0.21 + 0.075 * fi) + fi * 2.1),
            0.32 * cos(t * (0.17 + 0.06 * fi) + fi * 1.3)
        );
        let r = 0.10 + 0.055 * sin(t * 0.35 + fi);
        let d = sd_circle(p, c, r);
        let k = exp(-abs(d) * (7.0 - f32(min(max_blobs, 4))));
        if (i % 2 == 0) {
            tint = vec3<f32>(0.42, 0.62, 1.0);
        }
        glow = glow + k;
    }
    col = col + tint * glow * 0.16;

    // vinheta sutil
    let vig = smoothstep(1.25, 0.35, length(vec2<f32>(p.x / aspect, p.y)));
    col = col * (0.55 + 0.45 * vig);

    return vec4<f32>(col, 1.0);
}
"#;

/// HUD: círculos instanciados (controles virtuais + barra de FPS).
/// instância: @location(0) = [x, y, raio, pressed], @location(1) = [r, g, b, a]
pub const HUD: &str = r#"
struct Viewport {
    width: f32,
    height: f32,
    dpr: f32,
    time: f32,
};
@group(0) @binding(0) var<uniform> vp: Viewport;

struct VsOut {
    @builtin(position) pos: vec4<f32>,
    @location(0) center: vec2<f32>,
    @location(1) radius: f32,
    @location(2) pressed: f32,
    @location(3) color: vec4<f32>,
};

@vertex
fn vs(
    @builtin(vertex_index) vi: u32,
    @location(0) inst_a: vec4<f32>,
    @location(1) inst_b: vec4<f32>,
) -> VsOut {
    var corner = array<vec2<f32>, 6>(
        vec2<f32>(-1.0, -1.0), vec2<f32>(1.0, -1.0), vec2<f32>(-1.0, 1.0),
        vec2<f32>(-1.0,  1.0), vec2<f32>(1.0, -1.0), vec2<f32>( 1.0, 1.0)
    );
    let c = corner[vi];
    let center_px = inst_a.xy;
    let radius_px = inst_a.z;
    let pressed = inst_a.w;

    let pos_px = center_px + c * (radius_px + 3.0);
    // converte para NDC (origem no topo-esquerda)
    let ndc = vec2<f32>(
        pos_px.x / vp.width * 2.0 - 1.0,
        1.0 - pos_px.y / vp.height * 2.0
    );

    var out: VsOut;
    out.pos = vec4<f32>(ndc, 0.0, 1.0);
    out.center = center_px;
    out.radius = radius_px;
    out.pressed = pressed;
    out.color = inst_b;
    return out;
}

@fragment
fn fs(in: VsOut) -> @location(0) vec4<f32> {
    let d = distance(in.pos.xy, in.center) - in.radius;
    let alpha_edge = smoothstep(2.0, -1.0, d);
    if (alpha_edge <= 0.0) { discard; }

    // borda clara quando pressionado + anel externo
    let ring = smoothstep(4.0, 0.0, abs(d - 2.5)) * (0.35 + 0.4 * in.pressed);
    let fill = smoothstep(2.5, -2.0, d) * (0.28 + 0.45 * in.pressed);
    let a = clamp(fill + ring, 0.0, 1.0) * alpha_edge * in.color.a;
    let col = mix(in.color.rgb, vec3<f32>(1.0), in.pressed * 0.25);
    // saída premultiplicada (composição correta sobre o jogo)
    return vec4<f32>(col * a, a);
}
"#;
