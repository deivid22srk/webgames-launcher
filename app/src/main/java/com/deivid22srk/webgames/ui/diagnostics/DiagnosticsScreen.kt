package com.deivid22srk.webgames.ui.diagnostics

import android.content.Context
import android.os.Build
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.deivid22srk.webgames.data.AdapterInfo
import com.deivid22srk.webgames.data.NativeCore
import com.deivid22srk.webgames.data.AppViewModel
import com.deivid22srk.webgames.data.BenchmarkResult
import com.deivid22srk.webgames.data.EngineStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val WEBGPU_PROBE_JS = """
(async () => {
  if (!('gpu' in navigator)) return JSON.stringify({supported:false, reason:'navigator.gpu ausente nesta WebView'});
  try {
    const adapter = await navigator.gpu.requestAdapter();
    if (!adapter) return JSON.stringify({supported:false, reason:'requestAdapter retornou null'});
    let info = {};
    try { const i = adapter.info; info = {vendor:i.vendor||'', architecture:i.architecture||'', device:i.device||'', description:i.description||''}; } catch (e) { info = {}; }
    const l = adapter.limits;
    return JSON.stringify({supported:true, info:info, maxTextureDimension2d:l.maxTextureDimension2d, maxBufferSize:Number(l.maxBufferSize)});
  } catch (e) {
    return JSON.stringify({supported:false, reason:String(e)});
  }
})()
"""

/**
 * Diagnóstico WebGPU:
 * - adaptador NATIVO via wgpu (Rust): backend Vulkan/GLES, limites;
 * - WebGPU na WebView (Chromium): suporte, adapter.info, limites;
 * - motor experimental (Fase B): status + micro-benchmark WASM.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var nativeInfo by remember { mutableStateOf<AdapterInfo?>(null) }
    var webviewInfo by remember { mutableStateOf<String?>(null) }
    var engineStatus by remember { mutableStateOf<EngineStatus?>(null) }
    var benchmark by remember { mutableStateOf<BenchmarkResult?>(null) }
    var probing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        probing = true
        withContext(Dispatchers.IO) {
            nativeInfo = parse(NativeCore.wgpuAdapterInfo(), AdapterInfo.serializer())
            engineStatus = parse(NativeCore.experimentalEngineStatus(), EngineStatus.serializer())
        }
        probing = false
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Diagnóstico WebGPU") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            InfoCard(
                title = "Dispositivo",
                body = listOf(
                    "Modelo: ${Build.MANUFACTURER} ${Build.MODEL}",
                    "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    "WebView: ${
                        android.webkit.WebView.getCurrentWebViewPackage()?.versionName ?: "n/d"
                    }",
                ).joinToString("\n"),
            )

            InfoCard(
                title = "Adaptador nativo (wgpu / Rust)",
                body = nativeInfo?.let {
                    if (it.available) {
                        "Backend: ${it.backend}\nGPU: ${it.name}\nVendor: ${it.vendor}\n" +
                            "Tipo: ${it.deviceType}\nDriver: ${it.driver} ${it.driverInfo}\n" +
                            "Max textura 2D: ${it.maxTexture2d}\nMax buffer: ${it.maxBufferSize / (1024 * 1024)} MB"
                    } else {
                        "Indisponível: ${it.error ?: "?"}"
                    }
                } ?: "Consultando…",
            )

            InfoCard(
                title = "WebGPU na WebView (Chromium)",
                body = webviewInfo
                    ?: "Toque em “Testar WebView” para sondar navigator.gpu.",
            )
            Button(onClick = {
                probing = true
                val wv = WebView(context).apply { settings.javaScriptEnabled = true }
                wv.evaluateJavascript(WEBGPU_PROBE_JS) { result ->
                    webviewInfo = result
                        ?.trim('"')
                        ?.replace("\\\"", "\"")
                        ?.replace("\\n", "\n")
                    probing = false
                    wv.post { wv.destroy() }
                }
            }) { Text("Testar WebView") }

            InfoCard(
                title = "Motor experimental (Fase B)",
                body = engineStatus?.let {
                    "Runtime: ${it.runtime} (${it.runtimeVersion})\n" +
                        "Disponível: ${if (it.available) "sim" else "não"}\n${it.detail}"
                } ?: "Consultando…",
            )
            Button(onClick = {
                scope.launch(Dispatchers.IO) {
                    benchmark = parse(
                        NativeCore.runWasmBenchmark(),
                        BenchmarkResult.serializer(),
                    )
                }
            }) { Text("Rodar micro-benchmark WASM") }
            benchmark?.let {
                if (it.ok) {
                    InfoCard(
                        title = "Resultado (wasmi)",
                        body = "fib(30) = ${it.fib30Result} em ${"%.1f".format(it.fib30Ms)} ms\n" +
                            "sum_to(10M): ${"%.1f".format(it.sumTo10mMs)} ms",
                    )
                } else {
                    InfoCard(title = "Resultado", body = "Falha: ${it.error}")
                }
            }

            if (probing) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            }

            InfoCard(
                title = "Sobre WebGPU nos jogos",
                body = "Os jogos rodam na WebView do sistema (Chromium). Se o teste acima " +
                    "mostrar "navigator.gpu ausente" e o jogo exigir WebGPU, use o motor " +
                    "Chrome (Ajustes → Motor padrão, ou o botão Chrome na barra do jogo): " +
                    "o Chrome para Android tem WebGPU desde a versão 121.",
            )

            Text(
                "O diagnóstico não envia dados para a internet; tudo roda no dispositivo.",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private inline fun <reified T> parse(
    raw: String?,
    serializer: kotlinx.serialization.KSerializer<T>,
): T? {
    if (raw == null) return null
    return try {
        kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(serializer, raw)
    } catch (t: Throwable) {
        null
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
