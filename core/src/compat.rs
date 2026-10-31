//! Scripts de compatibilidade injetados na WebView.
//! Ficam no core Rust para serem versionados/testáveis junto com o núcleo.

/// Converte toques dos controles virtuais / gamepad físico em:
/// 1) eventos de teclado (fallback para jogos de teclado);
/// 2) um gamepad virtual via Gamepad API (override de navigator.getGamepads).
pub const INPUT_BRIDGE_JS: &str = r#"(function(){
  if (window.__wgInput) return; window.__wgInput = true;
  var state = { buttons: new Array(17).fill(0), axes: [0,0,0,0], keys: {} };
  var KEYMAP = {
    'dpad_up':'ArrowUp','dpad_down':'ArrowDown','dpad_left':'ArrowLeft','dpad_right':'ArrowRight',
    'a':'KeyX','b':'KeyZ','x':'KeyS','y':'KeyA','start':'Enter','select':'ShiftLeft'
  };
  function fakeGamepad(){
    return { id:'WebGames Virtual Gamepad', index:0, connected:true, mapping:'standard',
             timestamp: performance.now(), buttons: state.buttons.map(function(v){
               return { pressed: v > 0.5, touched: v > 0, value: v }; }),
             axes: state.axes.slice(), vibrationActuator: null };
  }
  var origGetGamepads = null;
  try { origGetGamepads = navigator.getGamepads.bind(navigator); } catch(e) {}
  if (navigator.getGamepads) {
    Object.defineProperty(navigator, 'getGamepads', { value: function(){
      var real = [];
      try { real = origGetGamepads ? origGetGamepads() : []; } catch(e) { real = []; }
      var anyReal = false;
      for (var i=0;i<real.length;i++) if (real[i] && real[i].connected) anyReal = true;
      if (anyReal) return real;
      var out = [fakeGamepad()];
      for (var j=1;j<4;j++) out.push(null);
      return out;
    }, configurable: true });
  }
  function keyEvent(key, down){
    var code = KEYMAP[key] || key;
    if (!code) return;
    if (down && state.keys[code]) return;
    if (!down && !state.keys[code]) return;
    state.keys[code] = down;
    var type = down ? 'keydown' : 'keyup';
    var ev = new KeyboardEvent(type, { key: code.replace('Key','').length===1?code.replace('Key',''):code,
                                       code: code, bubbles: true, cancelable: true });
    document.activeElement && document.activeElement.dispatchEvent(ev);
    window.dispatchEvent(ev);
  }
  window.__wgButton = function(name, down){
    var map = { a:0, b:1, x:2, y:3, start:9, select:8,
                dpad_up:12, dpad_down:13, dpad_left:14, dpad_right:15 };
    var idx = map[name]; if (idx == null) return;
    state.buttons[idx] = down ? 1.0 : 0.0;
    keyEvent(name, down);
  };
  window.__wgStick = function(which, x, y){
    var base = which === 'left' ? 0 : 2;
    state.axes[base] = x; state.axes[base+1] = y;
  };
  window.dispatchEvent(new Event('gamepadconnected'));
})();
"#;

/// Ajustes gerais para jogos web: desbloqueio de áudio, fullscreen helper,
/// prevenção de zoom/gestos acidentais.
pub const COMPAT_JS: &str = r#"(function(){
  if (window.__wgCompat) return; window.__wgCompat = true;
  // impedir zoom por pinça / double-tap
  var meta = document.createElement('meta');
  meta.name = 'viewport';
  meta.content = 'width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no';
  if (!document.querySelector('meta[name="viewport"]')) document.head.appendChild(meta);
  document.addEventListener('gesturestart', function(e){ e.preventDefault(); });
  document.addEventListener('dblclick', function(e){ e.preventDefault(); });
  // desbloqueio de áudio no primeiro toque
  var unlocked = false;
  function unlock(){
    if (unlocked) return; unlocked = true;
    try {
      var AC = window.AudioContext || window.webkitAudioContext;
      if (AC) { var ctx = new AC(); var o = ctx.createOscillator(); var g = ctx.createGain();
        g.gain.value = 0.0001; o.connect(g); g.connect(ctx.destination); o.start(); o.stop(ctx.currentTime + 0.01);
        if (ctx.state === 'suspended') ctx.resume().catch(function(){}); }
    } catch (e) {}
  }
  window.addEventListener('touchstart', unlock, { once: true, passive: true });
  window.addEventListener('mousedown', unlock, { once: true });
  // helper de fullscreen
  window.__wgFullscreen = function(){
    var el = document.documentElement;
    if (!document.fullscreenElement) { (el.requestFullscreen || el.webkitRequestFullscreen || function(){}).call(el); }
    else { (document.exitFullscreen || document.webkitExitFullscreen || function(){}).call(document); }
  };
  // foco para jogos de teclado
  window.addEventListener('load', function(){ window.focus(); });
})();
"#;

pub fn compat_script() -> &'static str {
    COMPAT_JS
}

pub fn input_bridge_script() -> &'static str {
    INPUT_BRIDGE_JS
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn scripts_define_expected_globals() {
        assert!(COMPAT_JS.contains("__wgCompat"));
        assert!(COMPAT_JS.contains("__wgFullscreen"));
        assert!(INPUT_BRIDGE_JS.contains("__wgButton"));
        assert!(INPUT_BRIDGE_JS.contains("__wgStick"));
        assert!(INPUT_BRIDGE_JS.contains("getGamepads"));
    }
}
