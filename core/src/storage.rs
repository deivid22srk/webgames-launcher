//! Persistência JSON atômica (tmp + rename) em arquivos dentro do filesDir.
//! Decisão: JSON puro-Rust em vez de rusqlite/sled — ver docs/DECISIONS.md (ADR-003).

use serde::de::DeserializeOwned;
use serde::Serialize;
use std::io::Write;
use std::path::Path;

pub fn load<T: DeserializeOwned>(dir: &Path, file: &str) -> Option<T> {
    let path = dir.join(file);
    let data = std::fs::read_to_string(&path).ok()?;
    match serde_json::from_str(&data) {
        Ok(v) => Some(v),
        Err(e) => {
            log::warn!("Falha ao parsear {path:?}: {e}; usando default");
            None
        }
    }
}

pub fn save<T: Serialize>(dir: &Path, file: &str, value: &T) -> Result<(), String> {
    let path = dir.join(file);
    let tmp = dir.join(format!("{file}.tmp"));
    let json = serde_json::to_string_pretty(value).map_err(|e| e.to_string())?;
    {
        let mut f = std::fs::File::create(&tmp).map_err(|e| e.to_string())?;
        f.write_all(json.as_bytes()).map_err(|e| e.to_string())?;
        f.sync_all().ok();
    }
    std::fs::rename(&tmp, &path).map_err(|e| e.to_string())?;
    Ok(())
}
