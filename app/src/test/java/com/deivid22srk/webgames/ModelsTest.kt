package com.deivid22srk.webgames

import com.deivid22srk.webgames.data.AppSettings
import com.deivid22srk.webgames.data.Game
import com.deivid22srk.webgames.ui.game.isolationSuspect
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun game_roundtrip() {
        val game = Game(
            id = "defjam",
            name = "Def Jam Online",
            url = "https://def-jam.online/",
            category = "Luta",
            orientation = "landscape",
            engine = "webview",
            favorite = true,
        )
        val raw = json.encodeToString(game)
        val back = json.decodeFromString(Game.serializer(), raw)
        assertEquals(game, back)
        assertTrue(raw.contains("def-jam.online"))
    }

    @Test
    fun settings_parses_core_json() {
        // JSON exatamente como o core Rust produz (snake_case)
        val raw = """
            {"theme":"dark","dynamic_color":true,"fps_overlay":false,"bg_quality":2,
             "default_engine":"webview","https_only":true,"haptics":true,
             "trusted_domains":["def-jam.online"],
             "gamepad_mapping":{"0":"A","1":"B"}}
        """.trimIndent()
        val s = json.decodeFromString(AppSettings.serializer(), raw)
        assertEquals("dark", s.theme)
        assertEquals(2, s.bgQuality)
        assertEquals(listOf("def-jam.online"), s.trustedDomains)
        assertEquals("A", s.gamepadMapping["0"])
    }

    @Test
    fun https_validation_helper() {
        val bad = Game(name = "x", url = "http://insecure.example/")
        val good = Game(name = "y", url = "https://secure.example/")
        assertEquals("insecure.example", bad.host)
        assertEquals("secure.example", good.host)
    }

    @Test
    fun settings_auto_browser_isolation_backcompat() {
        // JSON antigo do core, sem o campo novo -> default true
        val old = json.decodeFromString(AppSettings.serializer(), """{"theme":"dark"}""")
        assertTrue(old.autoBrowserIsolation)
        // com o campo novo presente
        val off = json.decodeFromString(
            AppSettings.serializer(),
            """{"theme":"dark","auto_browser_isolation":false}""",
        )
        assertFalse(off.autoBrowserIsolation)
    }

    @Test
    fun isolation_probe_detecta_mensagem_do_site() {
        // mensagem real do def-jam.online em PT-BR
        val ptBr = "\"0|Seu%20navegador%20n%C3%A3o%20consegue%20executar%20este%20jogo.%0A" +
            "Falta%3A%20isolamento%20entre%20origens.\""
        assertTrue(isolationSuspect(ptBr))
        // inglês
        val en = "\"0|Missing%3A%20cross-origin%20isolation.\""
        assertTrue(isolationSuspect(en))
        // menção direta ao SharedArrayBuffer
        val sab = "\"0|SharedArrayBuffer%20is%20not%20available%20in%20your%20browser.\""
        assertTrue(isolationSuspect(sab))
        // página já isolada -> nada a fazer
        assertFalse(isolationSuspect("\"1|consegue%20executar%20este%20jogo\""))
        // página comum -> falso
        assertFalse(isolationSuspect("\"0|Bem-vindo%20ao%20jogo!%20Toque%20para%20come%C3%A7ar.\""))
        assertFalse(isolationSuspect(null))
        assertFalse(isolationSuspect("null"))
        assertFalse(isolationSuspect(""))
    }
}
