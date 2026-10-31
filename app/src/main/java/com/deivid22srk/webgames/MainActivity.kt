package com.deivid22srk.webgames

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.deivid22srk.webgames.data.AppViewModel
import com.deivid22srk.webgames.ui.AppRoot
import com.deivid22srk.webgames.ui.game.InputHub
import com.deivid22srk.webgames.ui.theme.WebGamesTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm.ensureInit(this)

        setContent {
            val windowSize = calculateWindowSizeClass(this)
            WebGamesTheme(vm.settings.value) {
                AppRoot(vm = vm, windowSize = windowSize)
            }
        }
    }

    /** Gamepad físico vira eventos do InputHub (Gamepad API na WebView). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (InputHub.onKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (InputHub.onMotionEvent(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }
}
