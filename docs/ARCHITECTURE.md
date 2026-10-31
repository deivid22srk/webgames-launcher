# Arquitetura — WebGames Launcher

```
┌──────────────────────────────────────────────────────────────────┐
│  app/ — Kotlin + Jetpack Compose (Material 3 Expressive)         │
│                                                                  │
│  MainActivity ─ AppRoot (NavHost, NavigationBar/WideRail)        │
│      Home        Editor        GameScreen       Settings/Diag    │
│        │            │               │                 │          │
│        │            │        WebView (Chromium)        │          │
│        │            │        + WgpuFxView (HUD)        │          │
│        ▼            ▼               ▼                 ▼          │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  NativeCore (JNI 0.22 — extern "system")                │    │
│  └───────────────────────────┬─────────────────────────────┘    │
└──────────────────────────────┼───────────────────────────────────┘
                               │ libwebgames_core.so (cdylib)
┌──────────────────────────────▼───────────────────────────────────┐
│  core/ — Rust                                                    │
│   catalog (JSON) · settings · stats · metadata (ureq/rustls)     │
│   compat scripts (JS) · renderctl (thread wgpu) · jni_bridge     │
│        │                          │                              │
│        ▼                          ▼                              │
│  renderer/ — Rust            experimental-engine/ (Fase B)       │
│   wgpu 30: fundo animado      wasmi: runner WASM real (PoC),     │
│   + HUD/controles (WGSL)      micro-benchmark, status            │
└──────────────────────────────────────────────────────────────────┘
```

## Camadas

### 1. UI (Kotlin/Compose, `app/`)
- **Home**: fundo animado wgpu (`WgpuFxView`), grade de cartões com formas
  expressivas em morphing, busca, filtros, favoritos, FAB menu, LoadingIndicator.
- **Editor**: CRUD de jogos com fetch de metadata (og:image/favicon) e teste de URL.
- **GameScreen**: WebView fullscreen imersiva com Safe Browsing,
  `setRendererPriorityPolicy`, bloqueio de HTTP (quando `https_only`), injeta
  scripts de compatibilidade do núcleo Rust; overlay `WgpuFxView` translúcido
  desenha controles virtuais/barra de FPS via wgpu; `HorizontalFloatingToolbar`;
  captura de tela via MediaStore; gamepad físico via `InputHub` (KeyEvent/
  MotionEvent → Gamepad API shim + teclado).
- **Settings**: tema, cor dinâmica, motor, FPS, qualidade wgpu, domínios
  confiáveis, cache, mapeamento de gamepad.
- **Diagnostics**: adaptador wgpu nativo (backend Vulkan/GLES, limites), WebGPU
  da WebView (`navigator.gpu`), status do motor experimental + benchmark WASM.

### 2. Núcleo Rust (`core/`, cdylib via JNI 0.22)
- Catálogo com persistência JSON atômica; estatísticas; configurações.
- Metadata com `ureq` (rustls, TLS sem C): título, og:image, favicon,
  disponibilidade (HEAD com fallback GET) e latência.
- `renderctl`: ciclo de vida do thread de render wgpu (start/stop/resize,
  qualidade, FPS, instâncias de HUD).
- `compat.rs`: scripts JS de compatibilidade (desbloqueio de áudio, zoom,
  fullscreen, gamepad virtual com override de `navigator.getGamepads`).

### 3. Renderer Rust (`renderer/`)
- wgpu 30 sobre Vulkan/GL a partir do `ANativeWindow*` (raw-window-handle).
- Shader de fundo: gradiente + blobs SDF animados (quantidade por qualidade).
- Shader de HUD: círculos instanciados (controles pressionados + barra de FPS).
- `probe_adapter()`: diagnóstico sem superfície.

### 4. Fase B (`experimental-engine/`)
- Runtime WASM real em Rust (**wasmi 2.0**) com módulo codificado à mão
  (`add`, `fib`, `sum_to`) — valida carregar/instanciar/executar WASM nativo,
  fundação do "motor experimental".
- Selecionável por jogo nas configurações; quando indisponível, o app degrada
  graciosamente para WebView (Fase A nunca quebra).

## Fluxo de renderização (fundo da home / HUD do jogo)

1. Kotlin cria `TextureView` → `Surface` → JNI `wgpuStart(surface, w, h, q)`.
2. Rust chama `ANativeWindow_fromSurface`, monta `SurfaceTargetUnsafe::RawHandle`,
   cria wgpu Instance/Adapter/Device/Surface e os 2 pipelines.
3. Thread `wgpu-render` desenha a cada frame com `PresentMode::Fifo`;
   parâmetros compartilhados por atomics/Mutex (qualidade, FPS, HUD).
4. `surfaceDestroyed` → `wgpuStop()` → join do thread → `ANativeWindow_release`.

## Projetos de referência estudados (resumo)

| Projeto | Papel avaliado | Conclusão |
| --- | --- | --- |
| wgpu (gfx-rs) | render WebGPU nativo | adotado — maduro, Android OK (Vulkan/GL) |
| winit + android-activity | janela/input nativo | não adotado: conflita com UI Compose; usamos Surface via JNI |
| cargo-ndk | cross-compile | adotado (4.1.2) |
| rust-android-gradle | integração Gradle | descartado: controle fino via cargo-ndk (ver ADR-004) |
| Servo | motor web em Rust | ativo e com suporte Android (servo.org, 2026), mas embedding de terceiros em Android ainda não é um "add dependency" — fora do escopo seguro; documentado como caminho futuro (ADR-005) |
| Dawn / wgpu-native | WebGPU alternativo | Dawn exige toolchain C++; wgpu-native é wrapper — wgpu direto é superior aqui |
| Bevy / egui / Slint / Vello | UI/render 2D | Bevy = engine de jogo completa (pesada p/ launcher); egui/Slint duplicariam Compose; Vello interessante p/ 2D vetorial futuro |
| WebView (Chromium) | execução dos jogos | adotado — único caminho confiável p/ JS/DOM/WASM/WebGPU; WebGPU no Chrome 121+ |
| GeckoView | 2º motor | WebGPU no Gecko Android ainda não GA; dependência pesada; mantido como opção futura |
| gilrs | gamepad | sem backend Android — usamos KeyEvent/MotionEvent do Android (ADR-006) |
| reqwest/ureq | rede HTTP | ureq 3: leve, rustls, sem tokio |
| rusqlite/sled | storage | JSON+serde: zero código C no cross-compile (ADR-003) |

## Segurança e legal

- HTTPS por padrão (`https_only`), lista de domínios confiáveis editável.
- Safe Browsing habilitado; `intent://` e esquemas não-http bloqueados.
- Permissões mínimas: INTERNET, ACCESS_NETWORK_STATE.
- O app **apenas abre sites**; nenhum jogo é copiado, redistribuído ou
  desprotegido.
