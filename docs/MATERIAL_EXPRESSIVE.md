# Material 3 Expressive — diretrizes aplicadas

Fontes principais (lidas em out/2026):

- Material 3 Expressive (anúncio/diretrizes): https://m3.material.io/blog/material-3-expressive
- Compose Material 3 — release notes: https://developer.android.com/jetpack/androidx/releases/compose-material3
- Componentes expressivos (referência de API): https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary
- Motion (MotionScheme/springs): https://m3.material.io/styles/motion/overview
- Typography/type scale expressiva: https://m3.material.io/styles/typography/overview
- Shapes/MaterialShapes: https://m3.material.io/styles/shape/overview
- Dynamic color: https://m3.material.io/styles/color/dynamic-color/overview
- graphics-shapes (morphing): https://developer.android.com/develop/ui/compose/graphics/shapes

## O que o app usa (verificado por inspeção do AAR material3 1.5.0-beta02)

| Recurso | Onde |
| --- | --- |
| `MaterialExpressiveTheme` + `MotionScheme.expressive()` | `ui/theme/Theme.kt` — tema raiz do app |
| Dynamic Color (`dynamicLight/DarkColorScheme`) Android 12+ com fallback de paleta própria | `ui/theme/Theme.kt` |
| Tipografia expressiva (display/headline pesados) | `ui/theme/Type.kt` |
| `MaterialShapes` + morphing contínuo (`Morph.toPath(progress)`) | `ui/components/Common.kt` (`rememberMorphingShape`) — molduras decorativas dos cartões |
| `LoadingIndicator` / `ContainedLoadingIndicator` | Home durante carregamento do catálogo |
| `FloatingActionButtonMenu` + `ToggleFloatingActionButton` + `FloatingActionButtonMenuItem` | Home — "Adicionar jogo" |
| `HorizontalFloatingToolbar` | Tela de jogo — recarregar, FPS, controles, captura, sair |
| `ButtonGroup`/`SplitButton` (disponíveis na lib) | reservados para filtros/futuras ações |
| `WideNavigationRail` (rail adaptativo) | `ui/AppRoot.kt` em telas expandidas (tablets/foldables) |
| `NavigationBar` (compacto) | `ui/AppRoot.kt` |
| `WindowSizeClass` (`calculateWindowSizeClass`) | `MainActivity` + `AppRoot` |
| Edge-to-edge (`enableEdgeToEdge`) + imersão total no jogo | `MainActivity`, `GameScreen` |
| Predictive Back (`android:enableOnBackInvokedCallback`) | `AndroidManifest.xml` |
| Animações de mola | herdadas de `MotionScheme.expressive()` |
| Feedback háptico (`performHapticFeedback`) | controles virtuais |
| Acessibilidade: alvos ≥48dp, contentDescription em ícones, contraste do esquema | controles virtuais (raio mínimo 26px), ícones e labels |

## Princípios seguidos

1. **Motion com propósito** — molas expressivas (MotionScheme) para transições
   de UI; morphing de formas como identidade visual, não decoração solta.
2. **Tamanhos grandes e tipografia pesada** em títulos (display/headline),
   corpo confortável.
3. **Cor dinâmica como padrão** (Material You), com fallback consistente para
   versões antigas.
4. **Adaptatividade** — NavigationBar em telefones, WideNavigationRail em
   larguras médias/expandidas.
5. **Háptica e feedback** nos controles do jogo.
6. **Componentes expressivos onde agregam** (loading, FAB menu, floating
   toolbar), sem exageros.
