package com.deivid22srk.webgames.ui.game

import java.net.URLDecoder
import java.text.Normalizer

/**
 * Marcadores (minúsculos e sem acentos) que indicam que a página declara
 * exigir isolamento entre origens — COOP/COEP + SharedArrayBuffer — e por
 * isso não roda no WebView do Android (issues.chromium.org/40914606,
 * "Won't fix": um renderer por app, `crossOriginIsolated` sempre falso).
 */
internal val ISOLATION_MARKERS = listOf(
    "sharedarraybuffer",
    "isolamento entre origens",
    "isolamento de origem",
    "cross-origin isolation",
    "cross origin isolation",
    "consegue executar este jogo",
    "cannot run this game",
    "no puede ejecutar este juego",
)

/** Minúsculas + remoção de diacríticos (NFD), para casar PT/EN/ES sem esforço. */
internal fun foldAccents(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

/**
 * Decide, a partir da resposta do probe JS ("coi|texto-url-encoded"),
 * se o site pediu explicitamente um navegador com isolamento entre origens.
 *
 * O resultado do `evaluateJavascript` chega como JSON — ex.: `"0|Falta%3A%20..."`
 * — por isso as aspas externas são removidas antes do URL-decode.
 */
internal fun isolationSuspect(rawJsResult: String?): Boolean {
    if (rawJsResult.isNullOrBlank()) return false
    val res = rawJsResult.trim().removeSurrounding("\"")
    val parts = res.split('|', limit = 2)
    if (parts.getOrNull(0) == "1") return false // página já isolada: nada a fazer
    val encoded = parts.getOrNull(1).orEmpty()
    val text = runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(encoded)
    val folded = foldAccents(text.lowercase())
    return ISOLATION_MARKERS.any { it in folded }
}
