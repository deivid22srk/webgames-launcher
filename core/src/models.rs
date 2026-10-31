//! Tipos de domínio compartilhados (serializados como JSON pela ponte JNI).

use serde::{Deserialize, Serialize};

/// Um jogo web do catálogo.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Game {
    pub id: String,
    pub name: String,
    pub url: String,
    #[serde(default)]
    pub icon_url: Option<String>,
    #[serde(default)]
    pub category: String,
    /// portrait | landscape | auto
    #[serde(default = "default_orientation")]
    pub orientation: String,
    /// webview | chrome | experimental
    #[serde(default = "default_engine")]
    pub engine: String,
    #[serde(default)]
    pub favorite: bool,
    #[serde(default)]
    pub launch_count: u32,
    #[serde(default)]
    pub total_seconds: u64,
    #[serde(default)]
    pub last_played_ms: Option<u64>,
    #[serde(default)]
    pub added_ms: u64,
}

fn default_orientation() -> String {
    "auto".into()
}
fn default_engine() -> String {
    "webview".into()
}

impl Game {
    /// Domínio (host) da URL — usado na lista de domínios confiáveis.
    pub fn host(&self) -> Option<String> {
        url::Url::parse(&self.url)
            .ok()?
            .host_str()
            .map(|h| h.to_string())
    }

    /// Validação básica: HTTPS e URL parseável.
    pub fn validate(&self) -> Result<(), String> {
        let u = url::Url::parse(&self.url).map_err(|e| format!("URL inválida: {e}"))?;
        if u.scheme() != "https" {
            return Err("Apenas URLs HTTPS são permitidas por padrão".into());
        }
        if u.host_str().is_none() {
            return Err("URL sem host".into());
        }
        Ok(())
    }
}

/// Catálogo semeado na primeira execução.
/// O app apenas ABRE esses sites; nada é copiado ou redistribuído.
pub fn seed_games() -> Vec<Game> {
    let now = now_ms();
    let mk = |name: &str, url: &str, category: &str, orientation: &str| Game {
        id: format!("seed-{}", slug(name)),
        name: name.into(),
        url: url.into(),
        icon_url: None,
        category: category.into(),
        orientation: orientation.into(),
        engine: "webview".into(),
        favorite: false,
        launch_count: 0,
        total_seconds: 0,
        last_played_ms: None,
        added_ms: now,
    };
    vec![
        mk(
            "Def Jam Online",
            "https://def-jam.online/",
            "Luta",
            "landscape",
        ),
        mk("OpenHalo CE", "https://openhaloce.com/", "FPS", "landscape"),
        mk(
            "Slow Roads",
            "https://slowroads.io/",
            "Dirigir",
            "landscape",
        ),
        mk(
            "HexGL",
            "https://hexgl.bkcore.com/play/",
            "Corrida",
            "landscape",
        ),
        mk("2048", "https://play2048.co/", "Puzzle", "portrait"),
        mk(
            "Chrome Dino",
            "https://chromedino.com/",
            "Arcade",
            "landscape",
        ),
        mk(
            "CrossCode Demo",
            "https://crosscode-demo.web.app/",
            "RPG",
            "landscape",
        ),
    ]
}

pub fn slug(s: &str) -> String {
    s.chars()
        .map(|c| c.to_ascii_lowercase())
        .filter(|c| c.is_ascii_alphanumeric())
        .collect()
}

pub fn now_ms() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0)
}
