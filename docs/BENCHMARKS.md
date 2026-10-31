# Benchmarks — metodologia e resultados

## Micro-benchmark do motor experimental (Rust/wasmi)

Executado no app (Diagnóstico → "Rodar micro-benchmark WASM") e em testes
de unidade. Módulo WASM codificado à mão (ver
`experimental-engine/src/wasm_min.rs`), interpretado pelo wasmi 2.0.

- `fib(30)` recursivo (≈ 1,6 M chamadas) — valida execução CPU real via WASM.
- `sum_to(10_000_000)` com loop — valida throughput de interpretador.

| Ambiente | fib(30) | sum_to(10M) | Observação |
| --- | --- | --- | --- |
| Host x86_64 (release, cargo test) | < 100 ms | < 100 ms | total do teste: 0,11 s |
| Dispositivo Android (arm64) | medir via tela de Diagnóstico | idem | executado no mesmo pipeline wasmi |

> Os números do dispositivo devem ser coletados no hardware-alvo; a tela de
> diagnóstico executa exatamente o mesmo benchmark do CI.

## Tamanho dos binários (build local, out/2026)

| Artefato | Tamanho |
| --- | --- |
| `app-debug.apk` (arm64 + x86_64) | 53,7 MB |
| `app-release.apk` (R8 + 2 ABIs) | 21,2 MB |
| `libwebgames_core.so` (arm64, release, wgpu incl.) | ≈ 12,2 MB |

## Como medir FPS/frametime no dispositivo

1. Abrir um jogo → botão **FPS** na `HorizontalFloatingToolbar`.
2. A barra desenhada pelo wgpu (shader HUD) é proporcional ao frametime
   (33 ms = largura cheia); o texto "N FPS" usa o Choreographer.
3. Qualidade do fundo: Ajustes → "Qualidade do fundo (wgpu)" (0/1/2) —
   reduz/estende o número de blobs SDF do shader.

## Plano de comparação de motores (futuro)

Quando a Fase B evoluir para motor completo (Servo embedding ou
runner WASM+WebGPU dedicado), comparar por jogo: tempo até primeiro frame,
FPS médio (Choreographer vs. rAF hook), memória RSS (Debug.getMemoryInfo)
e uso de bateria (BatteryManager), 5 execuções por cenário.
