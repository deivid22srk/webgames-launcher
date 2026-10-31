package com.deivid22srk.webgames

import com.deivid22srk.webgames.data.AppSettings
import com.deivid22srk.webgames.data.Game
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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
}
