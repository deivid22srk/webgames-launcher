# WebGames Launcher

Launcher Android para **jogos web** (WebGL/WebGPU/WASM), construído com
**Material 3 Expressive** (Jetpack Compose) e um **núcleo em Rust** com
renderização **WebGPU (wgpu 30)** integrada via JNI.

> O app apenas **abre os sites** dos jogos (WebView/Chromium ou Chrome Custom
> Tabs). Nenhum conteúdo é copiado, redistribuído ou desprotegido.

## Destaques

- 🎨 **Material 3 Expressive**: `MaterialExpressiveTheme` + `MotionScheme.expressive()`,
  Dynamic Color (Material You), `MaterialShapes` com morphing contínuo,
  `LoadingIndicator`, `FloatingActionButtonMenu`, `HorizontalFloatingToolbar`,
  `WideNavigationRail`, tipografia expressiva, feedback háptico,
  edge-to-edge + Predictive Back, layouts adaptativos (WindowSizeClass).
- 🦀 **Rust de verdade no meio**: catálogo, rede (metadata/healthcheck), storage,
  configurações, estatísticas, scripts de compatibilidade e o **renderer wgpu**
  (fundo animado da home, HUD e controles virtuais) rodam em Rust
  (`libwebgames_core.so`, JNI 0.22).
- 🎮 **Jogos em fullscreen imersivo**: WebView otimizada (Safe Browsing,
  renderer priority, HTTPS-only), controles virtuais desenhados com wgpu,
  gamepad físico → Gamepad API, FPS overlay, captura de tela.
- 🔬 **Diagnóstico WebGPU**: adaptador nativo (backend Vulkan/GLES, limites) via
  wgpu + suporte WebGPU da WebView via `navigator.gpu`.
- 🧪 **Fase B (experimental)**: runner WASM real em Rust (wasmi 2.0) com
  benchmark visível no app — fundação do motor experimental, sem quebrar a
  Fase A.

## Catálogo inicial (editável pelo usuário)

[Def Jam Online](https://def-jam.online/), [OpenHalo CE](https://openhaloce.com/),
Slow Roads, HexGL, 2048, Chrome Dino, CrossCode Demo — adicione, edite,
favorite e categorize quantos quiser (ícone automático via og:image/favicon).

## Arquitetura (resumo — detalhes em `docs/ARCHITECTURE.md`)

```
Compose (M3 Expressive)  ──JNI 0.22──▶  core/ (Rust)  ──▶  renderer/ (wgpu 30, WGSL)
        │                                    │
        └── WebView/Chromium (jogos)         └── experimental-engine/ (wasmi, Fase B)
```

## Como compilar

Pré-requisitos: JDK 21, Android SDK (API 37) + **NDK r29** (`29.0.14206865`),
Rust stable com targets `aarch64-linux-android` e `x86_64-linux-android`,
`cargo install cargo-ndk`.

```bash
# tudo (Rust 2 ABIs + Kotlin + APKs)
./gradlew assembleDebug assembleRelease

# só Kotlin (rápido, para iterar UI)
SKIP_RUST=1 ./gradlew :app:assembleDebug

# só ABIs específicas
./gradlew -PrustTargets=arm64-v8a assembleDebug

# qualidade
cargo fmt --all && cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace
./gradlew :app:testDebugUnitTest :app:lintDebug
```

APKs saem em `app/build/outputs/apk/{debug,release}/`. O release usa keystore
de CI (ou `KEYSTORE_BASE64`+senhas nos secrets do GitHub — nunca versionados).

## CI

`.github/workflows/build.yml`: job Rust (fmt/clippy/test) + job Android
(NDK r29, cargo-ndk, Gradle, testes unitários, lint) com **artefatos de APK**
e **GitHub Release automático em tags `v*`**.

## Documentação

| Doc | Conteúdo |
| --- | --- |
| `docs/VERSIONS.md` | todas as versões e suas fontes |
| `docs/MATERIAL_EXPRESSIVE.md` | diretrizes M3E aplicadas + links |
| `docs/ARCHITECTURE.md` | camadas, fluxos, referências estudadas |
| `docs/DECISIONS.md` | ADRs (minSdk, M3 1.5.0-beta02, JSON vs rusqlite, cargo-ndk, Fase B, gilrs, TextureView, JNI) |
| `docs/BENCHMARKS.md` | metodologia e números medidos |

## Créditos

- [wgpu](https://github.com/gfx-rs/wgpu) · [winit](https://github.com/rust-windowing/winit) ·
  [cargo-ndk](https://github.com/bbqsrc/cargo-ndk) · [wasmi](https://github.com/wasmi-labs/wasmi) ·
  [ureq](https://github.com/algesten/ureq) · [Servo](https://servo.org) (referência)
- Jetpack Compose / Material 3 Expressive (Google)
- Jogos listados pertencem aos seus respectivos autores — o launcher apenas
  abre os sites deles.

## Licença

MIT — veja [LICENSE](LICENSE).
