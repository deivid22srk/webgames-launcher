//! Catálogo de jogos: CRUD, favoritos, categorias, busca.

use crate::models::{seed_games, Game};
use crate::storage;
use std::path::Path;

#[derive(Debug, Default, serde::Serialize, serde::Deserialize)]
#[serde(transparent)]
pub struct Catalog {
    pub games: Vec<Game>,
}

impl Catalog {
    pub fn load(dir: &Path) -> Result<Self, String> {
        let mut cat: Catalog = storage::load(dir, "catalog.json").unwrap_or_default();
        if cat.games.is_empty() {
            cat.games = seed_games();
            storage::save(dir, "catalog.json", &cat.games)?;
            log::info!("Catálogo semeado com {} jogos", cat.games.len());
        }
        Ok(cat)
    }

    fn persist(&self, dir: &Path) -> Result<(), String> {
        storage::save(dir, "catalog.json", &self.games)
    }

    pub fn list(&self) -> Vec<Game> {
        self.games.clone()
    }

    pub fn get(&self, id: &str) -> Option<&Game> {
        self.games.iter().find(|g| g.id == id)
    }

    pub fn add(&mut self, dir: &Path, mut game: Game) -> Result<Game, String> {
        game.validate()?;
        if game.id.is_empty() {
            game.id = crate::models::slug(&game.name);
        }
        // garante unicidade do id
        let mut candidate = game.id.clone();
        let mut n = 1;
        while self.games.iter().any(|g| g.id == candidate) {
            n += 1;
            candidate = format!("{}-{n}", game.id);
        }
        game.id = candidate;
        if game.added_ms == 0 {
            game.added_ms = crate::models::now_ms();
        }
        self.games.push(game.clone());
        self.persist(dir)?;
        Ok(game)
    }

    pub fn update(&mut self, dir: &Path, game: Game) -> Result<bool, String> {
        game.validate()?;
        let Some(pos) = self.games.iter().position(|g| g.id == game.id) else {
            return Err("jogo não encontrado".into());
        };
        self.games[pos] = game;
        self.persist(dir)?;
        Ok(true)
    }

    pub fn remove(&mut self, dir: &Path, id: &str) -> Result<bool, String> {
        let before = self.games.len();
        self.games.retain(|g| g.id != id);
        let removed = self.games.len() != before;
        if removed {
            self.persist(dir)?;
        }
        Ok(removed)
    }

    pub fn set_favorite(&mut self, dir: &Path, id: &str, favorite: bool) -> Result<(), String> {
        let Some(g) = self.games.iter_mut().find(|g| g.id == id) else {
            return Err("jogo não encontrado".into());
        };
        g.favorite = favorite;
        self.persist(dir)?;
        Ok(())
    }

    pub fn record_launch(&mut self, dir: &Path, id: &str) -> Result<(), String> {
        let Some(g) = self.games.iter_mut().find(|g| g.id == id) else {
            return Err("jogo não encontrado".into());
        };
        g.launch_count = g.launch_count.saturating_add(1);
        g.last_played_ms = Some(crate::models::now_ms());
        self.persist(dir)?;
        Ok(())
    }

    pub fn record_session(&mut self, dir: &Path, id: &str, seconds: u64) -> Result<(), String> {
        let Some(g) = self.games.iter_mut().find(|g| g.id == id) else {
            return Err("jogo não encontrado".into());
        };
        g.total_seconds = g.total_seconds.saturating_add(seconds);
        self.persist(dir)?;
        Ok(())
    }

    /// Busca simples por nome/categoria/domínio (case-insensitive).
    pub fn search(&self, query: &str) -> Vec<Game> {
        let q = query.to_lowercase();
        self.games
            .iter()
            .filter(|g| {
                g.name.to_lowercase().contains(&q)
                    || g.category.to_lowercase().contains(&q)
                    || g.host().is_some_and(|h| h.contains(&q))
            })
            .cloned()
            .collect()
    }

    pub fn categories(&self) -> Vec<String> {
        let mut cats: Vec<String> = self
            .games
            .iter()
            .filter(|g| !g.category.is_empty())
            .map(|g| g.category.clone())
            .collect();
        cats.sort();
        cats.dedup();
        cats
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tmpdir() -> std::path::PathBuf {
        let d = std::env::temp_dir().join(format!("wgl-test-{}", std::process::id()));
        let _ = std::fs::create_dir_all(&d);
        d
    }

    #[test]
    fn seed_and_persist() {
        let dir = tmpdir();
        let mut cat = Catalog::load(&dir).unwrap();
        assert!(cat.games.len() >= 5);
        assert!(cat.games.iter().any(|g| g.url.contains("def-jam.online")));
        assert!(cat.games.iter().any(|g| g.url.contains("openhaloce.com")));
        let g = Game {
            id: String::new(),
            name: "Teste".into(),
            url: "https://exemplo.com/".into(),
            icon_url: None,
            category: "Teste".into(),
            orientation: "auto".into(),
            engine: "webview".into(),
            favorite: false,
            launch_count: 0,
            total_seconds: 0,
            last_played_ms: None,
            added_ms: 0,
        };
        let added = cat.add(&dir, g).unwrap();
        assert!(!added.id.is_empty());
        // recarregar do disco
        let mut cat2 = Catalog::load(&dir).unwrap();
        assert!(cat2.get(&added.id).is_some());
        assert!(cat2.set_favorite(&dir, &added.id, true).is_ok());
        let mut cat3 = Catalog::load(&dir).unwrap();
        assert!(cat3.get(&added.id).unwrap().favorite);
        assert!(cat3.remove(&dir, &added.id).unwrap());
    }

    #[test]
    fn rejects_http() {
        let mut cat = Catalog::default();
        let g = Game {
            id: String::new(),
            name: "Inseguro".into(),
            url: "http://exemplo.com/".into(),
            icon_url: None,
            category: String::new(),
            orientation: "auto".into(),
            engine: "webview".into(),
            favorite: false,
            launch_count: 0,
            total_seconds: 0,
            last_played_ms: None,
            added_ms: 0,
        };
        assert!(cat.add(&tmpdir(), g).is_err());
    }

    #[test]
    fn search_matches_host() {
        let cat = Catalog::load(&tmpdir()).unwrap();
        let r = cat.search("def-jam");
        assert!(r.iter().any(|g| g.name.contains("Def Jam")));
    }
}
