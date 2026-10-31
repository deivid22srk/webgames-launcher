# Versões — fontes e justificativas

Pesquisadas via web + metadados oficiais (Maven Google, Maven Central, crates.io,
GitHub Releases) em **outubro de 2026**. Nenhuma versão foi escolhida de memória.

## Android / Gradle

| Componente | Versão | Fonte |
| --- | --- | --- |
| Android Gradle Plugin | 9.5.0 (out/2026) | developer.android.com/build/releases/agp-9-5-0-release-notes |
| Gradle | 9.8.2 | docs.gradle.org (9.8.0 de 2026-10-20 + patch) |
| Kotlin (KGP) | 2.4.10 — alinhado ao Kotlin **embutido** do AGP 9.5 | POM do KGP via resolução do buildscript; docs de Compose atuais usam 2.4.10 |
| compileSdk / targetSdk | 37 (`platforms;android-37.0`, Android 17) | `sdkmanager --list` |
| minSdk | 29 (Android 10) | justificativa em docs/DECISIONS.md (ADR-001) |
| NDK | r29 (`29.0.14206865`) | `sdkmanager --list` |
| JDK (CI e local) | 21 (Temurin) | AGP 9.5: JDK mínimo 17; 21 cobre o presente |

Observação AGP 9: **Kotlin embutido** — o plugin `org.jetbrains.kotlin.android`
NÃO é aplicado (proibido). KGP fixado em 2.4.10 via buildscript para casar com
os plugins `kotlin-compose`/`kotlin-serialization` e o pipeline `built_in_kotlinc`.

## Jetpack Compose / AndroidX

| Artefato | Versão | Fonte |
| --- | --- | --- |
| Compose BOM | 2026.09.00 | dl.google.com/android/maven2 (maven-metadata.xml) |
| material3 | **1.5.0-beta02** (override do BOM, que fixa 1.4.0) | metadados + inspeção do AAR (javap) — M3 Expressive completo (LoadingIndicator, FloatingToolbar, ButtonGroup, SplitButton, MaterialShapes, WideNavigationRail) só existe a partir de 1.5.0 |
| material3-window-size-class | 1.5.0-beta02 | metadados |
| activity-compose | 1.13.0 | metadados (última estável) |
| lifecycle-runtime/viewmodel-compose | 2.11.0 | metadados |
| navigation-compose | 2.10.2 | metadados |
| core-ktx | 1.19.1 | metadados |
| core-splashscreen | 1.2.0 | metadados |
| androidx.browser (Custom Tabs) | 1.10.0 | metadados |
| androidx.graphics:graphics-shapes | 1.1.0 | metadados |
| material-icons-core | 1.7.8 | metadados (linha 1.7 congelada) |
| kotlinx-serialization-json | 1.12.0 | Maven Central |
| androidx.test.ext:junit / test:core | 1.3.0 / 1.7.0 | metadados |

## Rust

| Crate | Versão | Fonte |
| --- | --- | --- |
| Toolchain | stable 1.99.1 (out/2026) | rustup |
| wgpu | 30.0.1 | crates.io API (`max_stable_version`) |
| jni | 0.22.4 | crates.io — API nova `EnvUnowned`/`Env`/`ErrorPolicy` |
| ureq | 3.5.0 | crates.io (rustls por padrão, zero C) |
| wasmi | 2.0.0 | crates.io |
| cargo-ndk | 4.1.2 | crates.io |
| raw-window-handle | 0.6.2 | crates.io |
| pollster | 1.0.1 | crates.io |
| android_logger | 0.15.1 | crates.io |
| bytemuck | 1.x | crates.io |

Notas de API da wgpu 30 confirmadas no código-fonte do crate:
`InstanceDescriptor::new_without_display_handle()`, `RequestAdapterOptions.apply_limit_buckets`,
`DeviceDescriptor { experimental_features, trace }`, `SurfaceConfiguration.color_space`,
`PipelineLayoutDescriptor { immediate_size }` (sem `push_constant_ranges`),
`RenderPassDescriptor.multiview_mask`, `RenderPassColorAttachment.depth_slice`,
`get_current_texture() -> CurrentSurfaceTexture` (enum) e `Queue::present(texture)`.

## GitHub Actions (majors mais recentes em out/2026)

| Action | Major | Fonte |
| --- | --- | --- |
| actions/checkout | v7 (7.0.1) | GitHub API releases/latest |
| actions/setup-java | v6 (6.1.0) | GitHub API |
| actions/upload-artifact | v7 (7.0.2) | GitHub API |
| gradle/actions | v6 (6.4.1) | GitHub API |
| dtolnay/rust-toolchain | @stable | branch |
| Swatinem/rust-cache | v2 (2.9.3) | GitHub API |
| taiki-e/install-action | v2 (2.88.1) | GitHub API |

## WebGPU em produção

- Chrome/Chromium Android: WebGPU habilitado por padrão desde **Chrome 121**
  (Android 12+, GPUs Qualcomm/ARM) — developer.chrome.com/blog/new-in-webgpu-121.
- WebView (System WebView): acompanha o Chromium; disponibilidade real varia por
  dispositivo — por isso o app tem uma **tela de diagnóstico** que sonda
  `navigator.gpu` em tempo de execução (nada é assumido).
- WebGPU Compatibility mode (GLES 3.1) amplia o alcance em dispositivos antigos.
