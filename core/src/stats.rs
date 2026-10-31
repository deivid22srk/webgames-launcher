//! Estatísticas agregadas de sessão.

use crate::storage;
use serde::{Deserialize, Serialize};
use std::path::Path;

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct Stats {
    pub total_sessions: u64,
    pub total_seconds: u64,
    pub last_session_ms: Option<u64>,
}

impl Stats {
    pub fn load(dir: &Path) -> Result<Self, String> {
        Ok(storage::load(dir, "stats.json").unwrap_or_default())
    }

    pub fn save(&self, dir: &Path) -> Result<(), String> {
        storage::save(dir, "stats.json", self)
    }

    pub fn record(&mut self, dir: &Path, seconds: u64) -> Result<(), String> {
        self.total_sessions = self.total_sessions.saturating_add(1);
        self.total_seconds = self.total_seconds.saturating_add(seconds);
        self.last_session_ms = Some(crate::models::now_ms());
        self.save(dir)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn records() {
        let dir = std::env::temp_dir().join(format!("wgl-stats-{}", std::process::id()));
        let _ = std::fs::create_dir_all(&dir);
        let mut s = Stats::default();
        s.record(&dir, 42).unwrap();
        s.record(&dir, 8).unwrap();
        assert_eq!(s.total_sessions, 2);
        assert_eq!(s.total_seconds, 50);
        let s2 = Stats::load(&dir).unwrap();
        assert_eq!(s2.total_seconds, 50);
    }
}
