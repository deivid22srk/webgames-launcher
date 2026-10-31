//! Ponte JNI (crate `jni` 0.22) entre o app Kotlin e o núcleo Rust.
//!
//! Convenções:
//! - Kotlin `object NativeCore` (com.deivid22srk.webgames.data) declara `external fun`;
//! - segundo parâmetro é `this: JObject` (métodos de instância do singleton);
//! - retornos são JSON string (`JString`) com {"ok":...} quando mutação.

#![cfg(target_os = "android")]

use crate::models::Game;
use crate::{catalog, metadata, models, ok_json, renderctl, settings, state, stats};
use jni::errors::ThrowRuntimeExAndDefault;
use jni::objects::{JObject, JString};
use jni::{Env, EnvUnowned};

fn to_string(env: &mut Env<'_>, s: &JString<'_>) -> String {
    s.try_to_string(env).unwrap_or_default()
}

fn ret<'local>(env: &mut Env<'local>, s: String) -> jni::errors::Result<JString<'local>> {
    env.new_string(&s)
}

// ---------- inicialização ----------

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_deivid22srk_webgames_data_NativeCore_init<'local>(
    mut unowned: EnvUnowned<'local>,
    _this: JObject<'local>,
    data_dir: JString<'local>,
) {
    unowned
        .with_env(|env| -> jni::errors::Result<()> {
            let dir = to_string(env, &data_dir);
            if let Err(e) = crate::init(&dir) {
                log::error!("init falhou: {e}");
            }
            Ok(())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

// ---------- catálogo ----------

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_deivid22srk_webgames_data_NativeCore_catalogList<'local>(
    mut unowned: EnvUnowned<'local>,
    _this: JObject<'local>,
) -> JString<'local> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'local>> {
            let json = state().map(|s| {
                serde_json::to_string(&s.catalog.lock().unwrap_or_else(|e| e.into_inner()).list())
                    .unwrap_or_else(|_| "[]".into())
            });
            ret(env, json.unwrap_or_else(|e| ok_json(false, Some(e))))
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_deivid22srk_webgames_data_NativeCore_catalogSearch<'local>(
    mut unowned: EnvUnowned<'local>,
    _this: JObject<'local>,
    query: JString<'local>,
) -> JString<'local> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'local>> {
            let q = to_string(env, &query);
            let json = state().map(|s| {
                serde_json::to_string(
                    &s.catalog
                        .lock()
                        .unwrap_or_else(|e| e.into_inner())
                        .search(&q),
                )
                .unwrap_or_else(|_| "[]".into())
            });
            ret(env, json.unwrap_or_else(|e| ok_json(false, Some(e))))
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_deivid22srk_webgames_data_NativeCore_catalogCategories<'local>(
    mut unowned: EnvUnowned<'local>,
    _this: JObject<'local>,
) -> JString<'local> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'local>> {
            let json = state().map(|s| {
                serde_json::to_string(
                    &s.catalog
                        .lock()
                        .unwrap_or_else(|e| e.into_inner())
                        .categories(),
                )
                .unwrap_or_else(|_| "[]".into())
            });
            ret(env, json.unwrap_or_else(|e| ok_json(false, Some(e))))
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_deivid22srk_webgames_data_NativeCore_catalogAdd<'local>(
    mut unowned: EnvUnowned<'local>,
    _this: JObject<'local>,
    game_json: JString<'local>,
) -> JString<'local> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'local>> {
            let raw = to_string(env, &game_json);
            let parsed: Result<Game, _> = serde_json::from_str(&raw);
            let out = match (parsed, state()) {
                (Ok(g), Ok(s)) => match s
                    .catalog
                    .lock()
                    .unwrap_or_else(|e| e.into_inner())
                    .add(&s.data_dir, g)
                {
                    Ok(added) => serde_json::json!({ "ok": true, "game": added }).to_string(),
                    Err(e) => ok_json(false, Some(e)),
                },
                (Err(e), _) => ok_json(false, Some(format!("json inválido: {e}"))),
                (_, Err(e)) => ok_json(false, Some(e)),
            };
            ret(env, out)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_deivid22srk_webgames_data_NativeCore_catalogUpdate<'local>(
    mut unowned: EnvUnowned<'local>,
    _this: JObject<'local>,
    game_json: JString<'local>,
) -> JString<'local> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'local>> {
            let raw = to_string(env, &game_json);
            let out = match (serde_json::from_str::<Game>(&raw), state()) {
                (Ok(g), Ok(s)) => match s
                    .catalog
                    .lock()
                    .unwrap_or_else(|e| e.into_inner())
                    .update(&s.data_dir, g)
                {
                    Ok(_) => ok_json(true, None),
                    Err(e) => ok_json(false, Some(e)),
                },
                (Err(e), _) => ok_json(false, Some(format!("json inválido: {e}"))),
                (_, Err(e)) => ok_json(false, Some(e)),
            };
            ret(env, out)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_deivid22srk_webgames_data_NativeCore_catalogRemove<'local>(
    mut unowned: EnvUnowned<'local>,
    _this: JObject<'local>,
    id: JString<'local>,
) -> JString<'local> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'local>> {
            let id = to_string(env, &id);
            let out = match state() {
                Ok(s) => match s
                    .catalog
                    .lock()
                    .unwrap_or_else(|e| e.into_inner())
                    .remove(&s.data_dir, &id)
                {
                    Ok(_) => ok_json(true, None),
                    Err(e) => ok_json(false, Some(e)),
                },
                Err(e) => ok_json(false, Some(e)),
            };
            ret(env, out)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_deivid22srk_webgames_data_NativeCore_catalogSetFavorite<'local>(
    mut unowned: EnvUnowned<'local>,
    _this: JObject<'local>,
    id: JString<'local>,
    favorite: jni::sys::jboolean,
) -> JString<'local> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'local>> {
            let id = to_string(env, &id);
            let out = match state() {
                Ok(s) => match s
                    .catalog
                    .lock()
                    .unwrap_or_else(|e| e.into_inner())
                    .set_favorite(&s.data_dir, &id, favorite)
                {
                    Ok(_) => ok_json(true, None),
                    Err(e) => ok_json(false, Some(e)),
                },
                Err(e) => ok_json(false, Some(e)),
            };
            ret(env, out)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_deivid22srk_webgames_data_NativeCore_catalogRecordLaunch<'local>(
    mut unowned: EnvUnowned<'local>,
    _this: JObject<'local>,
    id: JString<'local>,
) -> JString<'local> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'local>> {
            let id = to_string(env, &id);
            let out = match state() {
                Ok(s) => match s
                    .catalog
                    .lock()
                    .unwrap_or_else(|e| e.into_inner())
                    .record_launch(&s.data_dir, &id)
                {
                    Ok(_) => ok_json(true, None),
                    Err(e) => ok_json(false, Some(e)),
                },
                Err(e) => ok_json(false, Some(e)),
            };
            ret(env, out)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_deivid22srk_webgames_data_NativeCore_catalogRecordSession<
    'local,
>(
    mut unowned: EnvUnowned<'local>,
    _this: JObject<'local>,
    id: JString<'local>,
    seconds: jni::sys::jint,
) -> JString<'local> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'local>> {
            let id = to_string(env, &id);
            let secs = seconds.max(0) as u64;
            let out = match state() {
                Ok(s) => {
                    let r1 = s
                        .catalog
                        .lock()
                        .unwrap_or_else(|e| e.into_inner())
                        .record_session(&s.data_dir, &id, secs);
                    let r2 = s
                        .stats
                        .lock()
                        .unwrap_or_else(|e| e.into_inner())
                        .record(&s.data_dir, secs);
                    match (r1, r2) {
                        (Ok(_), Ok(_)) => ok_json(true, None),
                        (Err(e), _) | (_, Err(e)) => ok_json(false, Some(e)),
                    }
                }
                Err(e) => ok_json(false, Some(e)),
            };
            ret(env, out)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

// ---------- stats / settings ----------

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_deivid22srk_webgames_data_NativeCore_stats<'local>(
    mut unowned: EnvUnowned<'local>,
    _this: JObject<'local>,
) -> JString<'local> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'local>> {
            let json = state().map(|s| {
                serde_json::to_string(&*s.stats.lock().unwrap_or_else(|e| e.into_inner()))
                    .unwrap_or_else(|_| "{}".into())
            });
            ret(env, json.unwrap_or_else(|e| ok_json(false, Some(e))))
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_deivid22srk_webgames_data_NativeCore_settingsGet<'local>(
    mut unowned: EnvUnowned<'local>,
    _this: JObject<'local>,
) -> JString<'local> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'local>> {
            let json = state().map(|s| {
                serde_json::to_string(&*s.settings.lock().unwrap_or_else(|e| e.into_inner()))
                    .unwrap_or_else(|_| "{}".into())
            });
            ret(env, json.unwrap_or_else(|e| ok_json(false, Some(e))))
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_deivid22srk_webgames_data_NativeCore_settingsSet<'local>(
    mut unowned: EnvUnowned<'local>,
    _this: JObject<'local>,
    settings_json: JString<'local>,
) -> JString<'local> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'local>> {
            let raw = to_string(env, &settings_json);
            let out = match state() {
                Ok(s) => match serde_json::from_str::<settings::AppSettings>(&raw) {
                    Ok(new) => {
                        let mut guard = s.settings.lock().unwrap_or_else(|e| e.into_inner());
                        *guard = new;
                        let r = guard.save(&s.data_dir);
                        match r {
                            Ok(_) => ok_json(true, None),
                            Err(e) => ok_json(false, Some(e)),
                        }
                    }
                    Err(e) => ok_json(false, Some(format!("json inválido: {e}"))),
                },
                Err(e) => ok_json(false, Some(e)),
            };
            ret(env, out)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

// ---------- rede ----------

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_deivid22srk_webgames_data_NativeCore_fetchMetadata<'local>(
    mut unowned: EnvUnowned<'local>,
    _this: JObject<'local>,
    url: JString<'local>,
) -> JString<'local> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'local>> {
            let url = to_string(env, &url);
            let json = state().map(|s| {
                serde_json::to_string(&metadata::fetch_metadata(&s.agent, &url))
                    .unwrap_or_else(|_| "{}".into())
            });
            ret(env, json.unwrap_or_else(|e| ok_json(false, Some(e))))
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_deivid22srk_webgames_data_NativeCore_checkUrl<'local>(
    mut unowned: EnvUnowned<'local>,
    _this: JObject<'local>,
    url: JString<'local>,
) -> JString<'local> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'local>> {
            let url = to_string(env, &url);
            let json = state().map(|s| {
                serde_json::to_string(&metadata::check_url(&s.agent, &url))
                    .unwrap_or_else(|_| "{}".into())
            });
            ret(env, json.unwrap_or_else(|e| ok_json(false, Some(e))))
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

// ---------- scripts de compatibilidade ----------

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_deivid22srk_webgames_data_NativeCore_compatScript<'local>(
    mut unowned: EnvUnowned<'local>,
    _this: JObject<'local>,
) -> JString<'local> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'local>> {
            ret(env, crate::compat::compat_script().to_string())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_deivid22srk_webgames_data_NativeCore_inputBridgeScript<'local>(
    mut unowned: EnvUnowned<'local>,
    _this: JObject<'local>,
) -> JString<'local> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'local>> {
            ret(env, crate::compat::input_bridge_script().to_string())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

// ---------- wgpu ----------

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_deivid22srk_webgames_data_NativeCore_wgpuAdapterInfo<'local>(
    mut unowned: EnvUnowned<'local>,
    _this: JObject<'local>,
) -> JString<'local> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'local>> {
            let json = serde_json::to_string(&webgames_renderer::GpuRenderer::probe_adapter())
                .unwrap_or_else(|_| "{}".into());
            ret(env, json)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_deivid22srk_webgames_data_NativeCore_wgpuStart<'local>(
    mut unowned: EnvUnowned<'local>,
    _this: JObject<'local>,
    surface: JObject<'local>,
    width: jni::sys::jint,
    height: jni::sys::jint,
    quality: jni::sys::jint,
) -> JString<'local> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'local>> {
            let out = match state() {
                Ok(s) => {
                    let env_raw = env.get_raw();
                    let obj_raw = surface.as_raw();
                    // Seguro: env/obj são válidos durante esta chamada JNI; a
                    // referência ANativeWindow devolvida tem refcount próprio.
                    let win = unsafe {
                        ANativeWindow_fromSurface(
                            env_raw as *mut core::ffi::c_void,
                            obj_raw as *mut core::ffi::c_void,
                        )
                    };
                    if win.is_null() {
                        ok_json(false, Some("ANativeWindow_fromSurface falhou".into()))
                    } else {
                        s.renderer.set_quality(quality.max(0) as u32);
                        match s.renderer.start(
                            win as usize,
                            width.max(1) as u32,
                            height.max(1) as u32,
                        ) {
                            Ok(_) => ok_json(true, None),
                            Err(e) => {
                                unsafe { ANativeWindow_release(win) };
                                ok_json(false, Some(e))
                            }
                        }
                    }
                }
                Err(e) => ok_json(false, Some(e)),
            };
            ret(env, out)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_deivid22srk_webgames_data_NativeCore_wgpuStop<'local>(
    mut unowned: EnvUnowned<'local>,
    _this: JObject<'local>,
) -> JString<'local> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'local>> {
            if let Ok(s) = state() {
                s.renderer.stop();
            }
            ret(env, ok_json(true, None))
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_deivid22srk_webgames_data_NativeCore_wgpuResize<'local>(
    mut unowned: EnvUnowned<'local>,
    _this: JObject<'local>,
    width: jni::sys::jint,
    height: jni::sys::jint,
) -> JString<'local> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'local>> {
            if let Ok(s) = state() {
                s.renderer.resize(width.max(1) as u32, height.max(1) as u32);
            }
            ret(env, ok_json(true, None))
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_deivid22srk_webgames_data_NativeCore_wgpuSetQuality<'local>(
    mut unowned: EnvUnowned<'local>,
    _this: JObject<'local>,
    quality: jni::sys::jint,
) -> JString<'local> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'local>> {
            if let Ok(s) = state() {
                s.renderer.set_quality(quality.max(0) as u32);
            }
            ret(env, ok_json(true, None))
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_deivid22srk_webgames_data_NativeCore_wgpuSetFps<'local>(
    mut unowned: EnvUnowned<'local>,
    _this: JObject<'local>,
    fps: jni::sys::jfloat,
) -> JString<'local> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'local>> {
            if let Ok(s) = state() {
                s.renderer.set_fps(fps);
            }
            ret(env, ok_json(true, None))
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_deivid22srk_webgames_data_NativeCore_wgpuSetVisible<'local>(
    mut unowned: EnvUnowned<'local>,
    _this: JObject<'local>,
    visible: jni::sys::jboolean,
) -> JString<'local> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'local>> {
            if let Ok(s) = state() {
                s.renderer.set_visible(visible);
            }
            ret(env, ok_json(true, None))
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_deivid22srk_webgames_data_NativeCore_wgpuSetHudMode<'local>(
    mut unowned: EnvUnowned<'local>,
    _this: JObject<'local>,
    hud_mode: jni::sys::jboolean,
) -> JString<'local> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'local>> {
            if let Ok(s) = state() {
                s.renderer.set_hud_mode(hud_mode);
            }
            ret(env, ok_json(true, None))
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

/// Recebe JSON: [{"x":..,"y":..,"r":..,"color":[r,g,b,a],"pressed":..}, ...]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_deivid22srk_webgames_data_NativeCore_wgpuSetHud<'local>(
    mut unowned: EnvUnowned<'local>,
    _this: JObject<'local>,
    hud_json: JString<'local>,
) -> JString<'local> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'local>> {
            let raw = to_string(env, &hud_json);
            let items: Vec<renderctl::HudInstance> = serde_json::from_str(&raw)
                .map(|v: Vec<serde_json::Value>| {
                    v.iter()
                        .filter_map(|j| {
                            Some(renderctl::HudInstance::new(
                                j.get("x")?.as_f64()? as f32,
                                j.get("y")?.as_f64()? as f32,
                                j.get("r")?.as_f64()? as f32,
                                [
                                    j.pointer("/color/0")?.as_f64()? as f32,
                                    j.pointer("/color/1")?.as_f64()? as f32,
                                    j.pointer("/color/2")?.as_f64()? as f32,
                                    j.pointer("/color/3")?.as_f64()? as f32,
                                ],
                                j.get("pressed")?.as_f64()? as f32,
                            ))
                        })
                        .collect()
                })
                .unwrap_or_default();
            if let Ok(s) = state() {
                s.renderer.set_hud(items);
            }
            ret(env, ok_json(true, None))
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

// ---------- Fase B: motor experimental ----------

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_deivid22srk_webgames_data_NativeCore_experimentalEngineStatus<
    'local,
>(
    mut unowned: EnvUnowned<'local>,
    _this: JObject<'local>,
) -> JString<'local> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'local>> {
            let json = serde_json::to_string(&webgames_experimental::EngineRunner::status())
                .unwrap_or_else(|_| "{}".into());
            ret(env, json)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_deivid22srk_webgames_data_NativeCore_runWasmBenchmark<'local>(
    mut unowned: EnvUnowned<'local>,
    _this: JObject<'local>,
) -> JString<'local> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'local>> {
            let json = serde_json::to_string(&webgames_experimental::EngineRunner::run_benchmark())
                .unwrap_or_else(|_| "{}".into());
            ret(env, json)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

// ---------- FFI NDK (libandroid) ----------

unsafe extern "C" {
    fn ANativeWindow_fromSurface(
        env: *mut core::ffi::c_void,
        surface: *mut core::ffi::c_void,
    ) -> *mut core::ffi::c_void;
    fn ANativeWindow_release(window: *mut core::ffi::c_void);
}
