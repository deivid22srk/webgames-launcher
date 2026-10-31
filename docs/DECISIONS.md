# Decisões de engenharia (ADRs)

## ADR-001 — minSdk 29
**Data:** 2026-10-31. **Estado:** aceito.

- WebGPU (alvo do app) exige linha Chromium recente; Android 10 (API 29) é o
  piso realista para dispositivos que receberão WebView moderna.
- Recursos usados da plataforma: `setRendererPriorityPolicy` (API 29),
  `MediaStore.RELATIVE_PATH` (API 29), edge-to-edge/predictive back
  (targetSdk 33+), Dynamic Color (API 31+, com fallback).
- Custo: perde Android 9 ou mais antigo (≈ pequena fração em 2026) em troca de
  simplificação forte.

## ADR-002 — Material 3 1.5.0-beta02 em vez do BOM 1.4.0
**Estado:** aceito.

Os componentes M3 Expressive exigidos (`MaterialShapes`, `LoadingIndicator`,
`HorizontalFloatingToolbar`, `ButtonGroup`, `SplitButton`,
`FloatingActionButtonMenu`, `WideNavigationRail`) só existem na linha 1.5.
Inspecionamos os AARs: 1.4.0 não contém essas classes; 1.5.0-beta02 contém
todas. Riscos mitigados: APIs marcadas com `@OptIn`, BOM 2026.09.00 fixa o
restante em estáveis. Revisar ao sair 1.5.0 estável.

## ADR-003 — Persistência JSON (serde) em vez de rusqlite/sled
**Estado:** aceito.

Critérios: risco de cross-compile para Android, tamanho do binário, carga de
manutenção. `rusqlite` (bundled) exige `cc`/clang (ok com cargo-ndk, mas soma
superfície de falha) e `sled` está sem manutenção ativa. O modelo de dados
(catálogo/settings/stats) é pequeno e single-process; JSON atômico
(tmp+rename) com serde é suficiente e 100% Rust. Migração futura para
rusqlite é localizada em `storage.rs`.

## ADR-004 — cargo-ndk via tarefas Gradle em vez de rust-android-gradle
**Estado:** aceito.

`cargo-ndk` 4.1.2 configura CC/AR/linker por tripla e é mantido ativamente;
invocá-lo em tasks `Exec` do Gradle dá controle total (ABIs, output em
`app/build/rust/jniLibs`, skip via `-PSKIP_RUST=1`) sem depender de plugin
de terceiros no classpath do build.

## ADR-005 — Fase B: wasmi (PoC real de runtime) em vez de Servo/GeckoView agora
**Estado:** aceito.

- Servo (servo.org, Linux Foundation Europe) tem Android como plataforma e
  WebGPU, mas **embedding** por terceiros em Android ainda não é um caminho
  de "dependência" estável para um app de launcher em out/2026 (build via
  mach/AAR próprio, API de embedding em evolução).
- GeckoView: WebGPU no Firefox Android não é GA.
- Decisão: Fase B entrega um **runner WASM real** (wasmi 2.0) integrado ao
  wgpu, com status e benchmark visíveis no app, e documenta o caminho para
  incorporar Servo quando o embedding Android estiver maduro. A Fase A
  (WebView) nunca é quebrada pela Fase B.

## ADR-006 — Gamepad: eventos Android em vez de gilrs
**Estado:** aceito.

`gilrs` não tem backend Android (udev/XInput/IOKit). No Android, gamepads
chegam como `KeyEvent`/`MotionEvent` (SOURCE_GAMEPAD/JOYSTICK). O `InputHub`
mapeia para nomes padrão do Gamepad API; o núcleo Rust guarda o mapeamento e
os scripts de compat (shim de `navigator.getGamepads` + fallback de teclado).

## ADR-007 — TextureView em vez de SurfaceView para o wgpu
**Estado:** aceito (revisável).

TextureView participa da hierarquia normal de views (z-order previsível com
Compose e WebView) e aceita transparência — necessário para o overlay de
controles sobre o jogo. Custo: composição via GPU um pouco maior que
SurfaceView. Se medirmos custo relevante no fundo da home, mover para
SurfaceView com `setZOrderOnTop(false)`.

## ADR-008 — JNI manual (crate `jni` 0.22) em vez de UniFFI
**Estado:** aceito.

UniFFI gera bindings e build steps extras (KSP/native bridge); para ~30
funções JSON-in/JSON-out, JNI manual é direto, sem geração de código e com
menos risco de build. jni 0.22 (`EnvUnowned`/`Env`/`ErrorPolicy`) validado
com probe de compilação.

## ADR-009 — Alpha premultiplicado + frame pacing no overlay wgpu
**Data:** 2026-11-01 (após teste em Motorola edge 30 fusion / Android 14).
**Estado:** aceito.

Sintomas no primeiro APK: overlay de controles cobria o jogo com fundo roxo
(1º quadro com hud_mode errado) e depois ficava preto (alpha mode Opaque em
SurfaceTexture); `dequeueBuffer timed out` no BufferQueue com travadas de 2 s
no arranque. Correções:
- `CompositeAlphaMode::PreMultiplied` na surface + HUD shader emitindo cor
  premultiplicada com blend `One / OneMinusSrcAlpha`; clear premultiplicado.
- `wgpuSetHudMode` chamado **antes** do `wgpuStart` (1º quadro transparente).
- Frame pacing (~66 fps) no thread de render para não starvar o BufferQueue
  do TextureView.
- Cartões da home usam forma estática; morphing contínuo só em UMA decoração
  do cabeçalho (jank de 118 frames eliminado).

## ADR-010 — Sites com proteção anti-bot (Cloudflare): fallback Chrome
**Estado:** aceito.

Alguns jogos (ex.: Drift Hunters) retornam HTTP 403/503 para WebView
("You are not allowed to use this site"). O app **não contorna** proteções —
oferece: detecção de 403/406/503 com mensagem amigável, botão "Abrir no
Chrome" no card de erro e ação "Chrome" na toolbar (Custom Tabs). O Chrome
também é o caminho para jogos que exigem WebGPU quando a WebView do sistema
não o expõe (`navigator.gpu` ausente) — diagnosticável na tela de Diagnóstico.
