//! Configurações do app persistidas em settings.json.

use crate::storage;
use serde::{Deserialize, Serialize};
use std::path::Path;

/// Mapeamento de gamepad: botão padrão do Gamepad API -> tecla/símbolo.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct GamepadMapping {
    /// índice do botão Gamepad API -> nome do botão virtual
    pub buttons: std::collections::BTreeMap<String, String>,
}

impl Default for GamepadMapping {
    fn default() -> Self {
        Self {
            buttons: [
                ("0", "A"),
                ("1", "B"),
                ("2", "X"),
                ("3", "Y"),
                ("12", "D-pad Up"),
                ("13", "D-pad Down"),
                ("14", "D-pad Left"),
                ("15", "D-pad Right"),
                ("9", "Start"),
                ("8", "Select"),
            ]
            .into_iter()
            .map(|(k, v)| (k.to_string(), v.to_string()))
            .collect(),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct AppSettings {
    /// system | light | dark
    pub theme: String,
    pub dynamic_color: bool,
    pub fps_overlay: bool,
    /// 0 = baixo, 1 = médio, 2 = alto (qualidade do fundo wgpu)
    pub bg_quality: u32,
    /// webview | chrome | experimental
    pub default_engine: String,
    pub https_only: bool,
    pub haptics: bool,
    pub trusted_domains: Vec<String>,
    pub gamepad_mapping: GamepadMapping,
}

impl Default for AppSettings {
    fn default() -> Self {
        Self {
            theme: "system".into(),
            dynamic_color: true,
            fps_overlay: false,
            bg_quality: 1,
            default_engine: "webview".into(),
            https_only: true,
            haptics: true,
            trusted_domains: vec![
                "def-jam.online".into(),
                "openhaloce.com".into(),
                "slowroads.io".into(),
                "hexgl.bkcore.com".into(),
                "play2048.co".into(),
                "chromedino.com".into(),
                "crosscode-demo.web.app".into(),
            ],
            gamepad_mapping: GamepadMapping::default(),
        }
    }
}

impl AppSettings {
    pub fn load(dir: &Path) -> Result<Self, String> {
        Ok(storage::load(dir, "settings.json").unwrap_or_default())
    }

    pub fn save(&self, dir: &Path) -> Result<(), String> {
        storage::save(dir, "settings.json", self)
    }

    pub fn is_trusted(&self, host: &str) -> bool {
        if self.trusted_domains.is_empty() {
            return true;
        }
        self.trusted_domains
            .iter()
            .any(|d| host == d || host.ends_with(&format!(".{d}")))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn defaults_and_trust() {
        let s = AppSettings::default();
        assert_eq!(s.theme, "system");
        assert!(s.is_trusted("sub.play2048.co"));
        assert!(!s.is_trusted("evil.example.org"));
        assert!(s.haptics);
    }

    #[test]
    fn roundtrip() {
        let dir = std::env::temp_dir().join(format!("wgl-set-{}", std::process::id()));
        let _ = std::fs::create_dir_all(&dir);
        let s = AppSettings {
            theme: "dark".into(),
            bg_quality: 2,
            ..Default::default()
        };
        s.save(&dir).unwrap();
        let s2 = AppSettings::load(&dir).unwrap();
        assert_eq!(s2.theme, "dark");
        assert_eq!(s2.bg_quality, 2);
    }
}
