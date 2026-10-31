package com.deivid22srk.webgames.ui.game

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Evento de input vindo de gamepad físico ou dos controles virtuais. */
sealed interface InputEvent {
    data class Button(val name: String, val pressed: Boolean) : InputEvent
    data class Stick(val left: Boolean, val x: Float, val y: Float) : InputEvent
}

/**
 * Hub de input: gamepad físico (MainActivity dispatch) publica aqui;
 * a tela de jogo coleta e injeta na WebView (Gamepad API / teclado).
 */
object InputHub {
    private val _events = MutableSharedFlow<InputEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<InputEvent> = _events

    fun emit(event: InputEvent) {
        _events.tryEmit(event)
    }

    /** Mapeia KeyEvents de gamepad para nomes de botão padrão. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val source = event.source
        val isGamepad = (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (source and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
        if (!isGamepad) return false
        val name = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> "a"
            KeyEvent.KEYCODE_BUTTON_B -> "b"
            KeyEvent.KEYCODE_BUTTON_X -> "x"
            KeyEvent.KEYCODE_BUTTON_Y -> "y"
            KeyEvent.KEYCODE_BUTTON_START -> "start"
            KeyEvent.KEYCODE_BUTTON_SELECT -> "select"
            KeyEvent.KEYCODE_DPAD_UP -> "dpad_up"
            KeyEvent.KEYCODE_DPAD_DOWN -> "dpad_down"
            KeyEvent.KEYCODE_DPAD_LEFT -> "dpad_left"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "dpad_right"
            else -> return false
        }
        val pressed = event.action == KeyEvent.ACTION_DOWN
        if (event.repeatCount > 0) return true
        emit(InputEvent.Button(name, pressed))
        return true
    }

    /** Sticks analógicos do gamepad físico. */
    fun onMotionEvent(event: MotionEvent): Boolean {
        val source = event.source
        val isJoystick = (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        if (!isJoystick) return false
        val lx = event.getAxisValue(MotionEvent.AXIS_X)
        val ly = event.getAxisValue(MotionEvent.AXIS_Y)
        val rx = event.getAxisValue(MotionEvent.AXIS_Z)
        val ry = event.getAxisValue(MotionEvent.AXIS_RZ)
        emit(InputEvent.Stick(left = true, x = lx, y = ly))
        emit(InputEvent.Stick(left = false, x = rx, y = ry))
        return true
    }
}
