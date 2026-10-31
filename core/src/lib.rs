//! WebGames Core — núcleo em Rust do WebGames Launcher.
//!
//! Responsabilidades:
//! - catálogo de jogos (CRUD, favoritos, categorias) com persistência JSON atômica;
//! - fetch de metadata (título, og:image, favicon) e checagem de disponibilidade;
//! - estatísticas de sessão;
//! - configurações do app;
//! - scripts de compatibilidade injetados na WebView;
//! - controle do renderer wgpu (fundo animado, HUD, controles virtuais);
//! - ponte JNI para o app Android (Kotlin).

pub mod catalog;
pub mod compat;
pub mod jni_bridge;
pub mod metadata;
pub mod models;
pub mod renderctl;
pub mod settings;
pub mod stats;
pub mod storage;

use std::path::PathBuf;
use std::sync::{Mutex, OnceLock};

pub use metadata::WebMetadata;
pub use models::Game;

/// Estado global do core, inicializado uma única vez via [`init`].
pub struct AppState {
    pub data_dir: PathBuf,
    pub agent: ureq::Agent,
    pub catalog: Mutex<catalog::Catalog>,
    pub settings: Mutex<settings::AppSettings>,
    pub stats: Mutex<stats::Stats>,
    pub renderer: renderctl::RenderController,
}

static STATE: OnceLock<AppState> = OnceLock::new();

/// Inicializa o core com o diretório de dados do app (filesDir do Android).
pub fn init(data_dir: &str) -> Result<(), String> {
    let dir = PathBuf::from(data_dir);
    std::fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    let icons_dir = dir.join("icons");
    std::fs::create_dir_all(&icons_dir).map_err(|e| e.to_string())?;

    let agent = ureq::Agent::config_builder()
        .timeout_global(Some(std::time::Duration::from_secs(8)))
        .user_agent("WebGamesLauncher/0.1 (+Android)")
        .build()
        .new_agent();

    let catalog = catalog::Catalog::load(&dir)?;
    let settings = settings::AppSettings::load(&dir)?;
    let stats = stats::Stats::load(&dir)?;

    let state = AppState {
        data_dir: dir,
        agent,
        catalog: Mutex::new(catalog),
        settings: Mutex::new(settings),
        stats: Mutex::new(stats),
        renderer: renderctl::RenderController::new(),
    };
    STATE
        .set(state)
        .map_err(|_| "core já inicializado".to_string())?;
    log::info!("WebGames core inicializado em {data_dir}");
    Ok(())
}

/// Acesso ao estado global ( erro se [`init`] não foi chamado).
pub fn state() -> Result<&'static AppState, String> {
    STATE
        .get()
        .ok_or_else(|| "core não inicializado".to_string())
}

/// Serializa um resultado em JSON de resposta padrão: {"ok":bool,"error":Option<String>}.
pub fn ok_json(ok: bool, error: Option<String>) -> String {
    serde_json::json!({ "ok": ok, "error": error }).to_string()
}
