package com.deivid22srk.webgames.ui.game

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.view.Choreographer
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.view.HapticFeedbackConstants
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.deivid22srk.webgames.data.AppViewModel
import com.deivid22srk.webgames.data.Game
import com.deivid22srk.webgames.data.NativeCore
import kotlinx.coroutines.launch
import android.net.Uri
import kotlin.math.roundToInt

/** Botão virtual: nome, posição em px e raio. */
private data class ControlDef(val name: String, val x: Float, val y: Float, val r: Float)

/**
 * Tela de jogo: WebView fullscreen imersiva + overlay wgpu (controles/FPS)
 * + FloatingToolbar expressiva. O jogo roda no site original — nada é baixado
 * ou redistribuído.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GameScreen(
    vm: AppViewModel,
    gameId: String,
    onExit: () -> Unit,
) {
    val game = vm.games.value.firstOrNull { it.id == gameId }
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val settings by vm.settings.collectAsState()

    if (game == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Jogo não encontrado", color = Color.White)
        }
        return
    }

    // Motor Chrome (Custom Tabs)
    if (game.engine == "chrome") {
        LaunchedEffect(game.id) {
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(game.url))
            onExit()
        }
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }

    var controlsEnabled by rememberSaveable { mutableStateOf(true) }
    var fpsVisible by rememberSaveable { mutableStateOf(settings?.fpsOverlay ?: false) }
    var webError by remember { mutableStateOf<String?>(null) }
    var loadingProgress by remember { mutableIntStateOf(0) }
    var fps by remember { mutableIntStateOf(0) }
    val sessionStart = remember { SystemClock.elapsedRealtime() }

    val webView = remember {
        buildWebView(context, settings?.httpsOnly ?: true) { err -> webError = err }
            .also { it.loadUrl(game.url) }
    }

    // ---- sessão + imersão ----
    DisposableEffect(gameId) {
        vm.recordLaunch(gameId)
        val window = activity?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            val secs = ((SystemClock.elapsedRealtime() - sessionStart) / 1000).toInt()
            vm.recordSession(gameId, secs)
        }
    }

    // ---- orientação configurável por jogo ----
    DisposableEffect(game.orientation) {
        activity?.requestedOrientation = when (game.orientation) {
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // ---- FPS via Choreographer ----
    DisposableEffect(fpsVisible, controlsEnabled) {
        val choreographer = Choreographer.getInstance()
        var frames = 0
        var last = SystemClock.uptimeMillis()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                val t = frameTimeNanos / 1_000_000
                frames++
                if (t - last >= 1000) {
                    val measured = frames * 1000f / (t - last)
                    // wgpu desenha a barra de FPS quando hud ativo ou overlay ligado
                    NativeCore.wgpuSetFps(if (fpsVisible || controlsEnabled) measured else 0f)
                    fps = measured.roundToInt()
                    frames = 0
                    last = t
                }
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(callback)
        onDispose {
            choreographer.removeFrameCallback(callback)
            NativeCore.wgpuSetFps(0f)
        }
    }

    // ---- gamepad físico -> bridge JS ----
    LaunchedEffect(Unit) {
        InputHub.events.collect { event ->
            val js = when (event) {
                is InputEvent.Button ->
                    "window.__wgButton && window.__wgButton('${event.name}', ${event.pressed})"
                is InputEvent.Stick ->
                    "window.__wgStick && window.__wgStick('${if (event.left) "left" else "right"}', ${event.x}, ${event.y})"
            }
            webView.evaluateJavascript(js, null)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize(),
        )

        // overlay wgpu + controles virtuais
        if (controlsEnabled && webError == null) {
            AndroidView(
                factory = { ctx ->
                    WgpuFxView(ctx, hudMode = true) { settings?.bgQuality ?: 1 }
                },
                modifier = Modifier.fillMaxSize(),
            )
            VirtualControls(
                haptics = settings?.haptics ?: true,
                onButton = { name, pressed ->
                    if (settings?.haptics == true) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    InputHub.emit(InputEvent.Button(name, pressed))
                },
            )
        }

        if (loadingProgress in 1..99 && webError == null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // banner offline / erro
        webError?.let { err ->
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Não foi possível abrir o jogo",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "O site parece estar fora do ar ou inacessível.\n$err",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.Button(onClick = {
                            webError = null
                            webView.reload()
                        }) { Text("Tentar novamente") }
                        androidx.compose.material3.OutlinedButton(onClick = onExit) {
                            Text("Voltar")
                        }
                    }
                }
            }
        }

        if (fpsVisible) {
            Text(
                "$fps FPS",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
            )
        }

        // barra flutuante expressiva
        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
        ) {
            IconButton(onClick = {
                webError = null
                webView.reload()
            }) { Icon(Icons.Filled.Refresh, contentDescription = "recarregar") }
            IconButton(onClick = {
                fpsVisible = !fpsVisible
                if (!fpsVisible) NativeCore.wgpuSetFps(0f)
            }) { Text("FPS", style = MaterialTheme.typography.labelLarge) }
            IconButton(onClick = {
                controlsEnabled = !controlsEnabled
                NativeCore.wgpuSetHudMode(controlsEnabled)
            }) { Text("HUD", style = MaterialTheme.typography.labelLarge) }
            IconButton(onClick = { takeScreenshot(webView, context) }) {
                Text("PNG", style = MaterialTheme.typography.labelLarge)
            }
            IconButton(onClick = onExit) {
                Icon(Icons.Filled.Close, contentDescription = "sair do jogo")
            }
        }
    }
}

/**
 * Controles virtuais: alvos de toque em Compose que espelham estado no wgpu
 * (círculos desenhados pelo renderer Rust) e disparam eventos no InputHub.
 */
@Composable
private fun VirtualControls(
    haptics: Boolean,
    onButton: (String, Boolean) -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val r = (h * 0.062f).coerceIn(26f, 64f)
        val d = r * 1.9f
        val buttons = remember(w, h) {
            val lx = w * 0.17f
            val ly = h * 0.74f
            val rx = w * 0.83f
            val ry = h * 0.74f
            listOf(
                ControlDef("dpad_up", lx, ly - d, r),
                ControlDef("dpad_left", lx - d, ly, r),
                ControlDef("dpad_right", lx + d, ly, r),
                ControlDef("dpad_down", lx, ly + d, r),
                ControlDef("y", rx, ry - d, r),
                ControlDef("x", rx - d, ry, r),
                ControlDef("a", rx + d, ry, r),
                ControlDef("b", rx, ry + d, r),
            )
        }
        var pressed by remember { mutableStateOf(setOf<String>()) }

        // espelha o estado dos botões no HUD wgpu
        LaunchedEffect(pressed) {
            val json = buttons.joinToString(",") { b ->
                val on = pressed.contains(b.name)
                """{"x":${b.x},"y":${b.y},"r":${b.r},"color":[0.72,0.66,1.0,${if (on) 0.95 else 0.55}],"pressed":${if (on) 1.0 else 0.0}}"""
            }
            NativeCore.wgpuSetHud("[$json]")
        }

        buttons.forEach { b ->
            val sizeDp = with(density) { (b.r * 2f).toDp() }
            Box(
                modifier = Modifier
                    .offset { IntOffset((b.x - b.r).roundToInt(), (b.y - b.r).roundToInt()) }
                    .size(sizeDp)
                    .pointerInput(b.name) {
                        detectTapGestures(
                            onPress = {
                                pressed = pressed + b.name
                                onButton(b.name, true)
                                try {
                                    awaitRelease()
                                } finally {
                                    onButton(b.name, false)
                                    pressed = pressed - b.name
                                }
                            },
                        )
                    },
            )
        }
    }
}

private fun buildWebView(
    context: Context,
    httpsOnly: Boolean,
    onError: (String?) -> Unit,
): WebView {
    val compatScript = NativeCore.compatScript() ?: ""
    val inputScript = NativeCore.inputBridgeScript() ?: ""
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.safeBrowsingEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        setRendererPriorityPolicy(1 /* RENDERER_PRIORITY_WHEN_VISIBLE */, false)
        setBackgroundColor(android.graphics.Color.BLACK)

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                view.evaluateJavascript(compatScript, null)
                view.evaluateJavascript(inputScript, null)
                onError(null)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                val u = request.url
                return when (u.scheme) {
                    "https" -> false
                    "http" -> {
                        if (httpsOnly) {
                            onError("Política do app: apenas URLs HTTPS são abertas.")
                            true
                        } else false
                    }
                    else -> true // bloqueia intent://, javascript:, etc.
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) {
                    onError(error.description?.toString() ?: "erro de rede")
                }
            }
        }

        webChromeClient = object : android.webkit.WebChromeClient() {
            private var customView: View? = null

            override fun onShowCustomView(view: View, callback: android.webkit.WebChromeClient.CustomViewCallback) {
                if (customView != null) return
                customView = view
                (context as? Activity)?.addContentView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }

            override fun onHideCustomView() {
                customView?.let { (it.parent as? ViewGroup)?.removeView(it) }
                customView = null
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                // progresso simples exposto via callback de erro nulo
            }
        }
    }
}

private fun takeScreenshot(view: View, context: Context) {
    try {
        val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        view.draw(canvas)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "webgames-${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WebGames")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 92, out)
            }
        }
        Toast.makeText(context, "Captura salva em Pictures/WebGames", Toast.LENGTH_SHORT).show()
    } catch (t: Throwable) {
        Toast.makeText(context, "Falha na captura: ${t.message}", Toast.LENGTH_SHORT).show()
    }
}
