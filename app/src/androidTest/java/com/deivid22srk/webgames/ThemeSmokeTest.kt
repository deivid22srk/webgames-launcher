package com.deivid22srk.webgames

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deivid22srk.webgames.ui.theme.WebGamesTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Teste de UI básico: tema M3 Expressive renderiza conteúdo. */
@RunWith(AndroidJUnit4::class)
class ThemeSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun themeRendersContent() {
        composeRule.setContent {
            WebGamesTheme(settings = null) {
                Text("webgames-ok")
            }
        }
        composeRule.onNodeWithText("webgames-ok").assertExists()
    }
}
