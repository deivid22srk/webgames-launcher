//! Fetch de metadata (título, og:image, favicon) e checagem de disponibilidade.
//! Parsing HTML simples e sem dependências extras (busca por tags específicas).

use serde::{Deserialize, Serialize};
use url::Url;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WebMetadata {
    pub url: String,
    pub final_url: String,
    pub title: String,
    pub image: Option<String>,
    pub icon: Option<String>,
    pub reachable: bool,
    pub status: u16,
    pub error: Option<String>,
}

/// Extrai o valor de <meta property="..." content="..."> ou <meta name="..." content="...">.
fn extract_meta(html: &str, keys: &[&str]) -> Option<String> {
    let lower = html.to_lowercase();
    for key in keys {
        for pat in [format!("property=\"{key}\""), format!("name=\"{key}\"")] {
            if let Some(idx) = lower.find(&pat) {
                let rest = &html[idx..];
                if let Some(cpos) = rest.to_lowercase().find("content=\"") {
                    let after = &rest[cpos + 9..];
                    if let Some(end) = after.find('"') {
                        let v = &after[..end];
                        if !v.is_empty() {
                            return Some(v.to_string());
                        }
                    }
                }
            }
        }
    }
    None
}

/// Extrai <title>...</title>.
fn extract_title(html: &str) -> Option<String> {
    let lower = html.to_lowercase();
    let start = lower.find("<title")?;
    let after_gt = lower[start..].find('>')? + start + 1;
    let rest = &html[after_gt..];
    let end = rest.to_lowercase().find("</title>")?;
    let t = rest[..end].trim();
    if t.is_empty() {
        None
    } else {
        Some(t.to_string())
    }
}

/// Resolve um caminho relativo contra a URL base.
fn absolute(base: &Url, raw: &str) -> Option<String> {
    base.join(raw).ok().map(|u| u.to_string())
}

fn favicon_from_html(html: &str, base: &Url) -> Option<String> {
    let lower = html.to_lowercase();
    for needle in [
        "rel=\"icon\"",
        "rel=\"shortcut icon\"",
        "rel=\"apple-touch-icon\"",
        "rel='icon'",
        "rel='shortcut icon'",
    ] {
        if let Some(idx) = lower.find(needle) {
            let rest = &html[idx..];
            let limit = rest.len().min(600);
            let window = &rest[..limit];
            if let Some(hpos) = window.to_lowercase().find("href=\"") {
                let after = &window[hpos + 6..];
                if let Some(end) = after.find('"') {
                    return absolute(base, &after[..end]);
                }
            }
        }
    }
    None
}

fn favicon_fallback(base: &Url) -> String {
    // /favicon.ico na raiz do host
    format!(
        "{}://{}{}/favicon.ico",
        base.scheme(),
        base.host_str().unwrap_or_default(),
        base.port().map(|p| format!(":{p}")).unwrap_or_default()
    )
}

/// Limita leitura do corpo para evitar downloads gigantes.
const MAX_HTML: usize = 768 * 1024;

fn read_limited(resp: &mut ureq::http::Response<ureq::Body>) -> String {
    use std::io::Read;
    let mut buf: Vec<u8> = Vec::with_capacity(64 * 1024);
    let mut body = resp.body_mut().as_reader().take(MAX_HTML as u64);
    let _ = body.read_to_end(&mut buf);
    String::from_utf8_lossy(&buf).into_owned()
}

/// Busca metadata de uma URL (GET com timeout global do agent).
pub fn fetch_metadata(agent: &ureq::Agent, url: &str) -> WebMetadata {
    let mut out = WebMetadata {
        url: url.into(),
        final_url: url.into(),
        title: String::new(),
        image: None,
        icon: None,
        reachable: false,
        status: 0,
        error: None,
    };
    let parsed = match Url::parse(url) {
        Ok(u) if u.scheme() == "https" => u,
        Ok(u) => {
            out.error = Some("apenas https".into());
            let _ = u;
            return out;
        }
        Err(e) => {
            out.error = Some(format!("url inválida: {e}"));
            return out;
        }
    };

    match agent.get(parsed.as_str()).call() {
        Ok(mut resp) => {
            out.status = resp.status().as_u16();
            out.final_url = parsed.to_string();
            let html = read_limited(&mut resp);
            out.reachable = resp.status().is_success();
            out.title = extract_title(&html)
                .unwrap_or_else(|| parsed.host_str().unwrap_or_default().to_string());
            out.image = extract_meta(&html, &["og:image", "twitter:image", "og:image:url"])
                .and_then(|raw| absolute(&parsed, &raw));
            out.icon =
                favicon_from_html(&html, &parsed).or_else(|| Some(favicon_fallback(&parsed)));
        }
        Err(ureq::Error::StatusCode(code)) => {
            out.status = code;
            out.error = Some(format!("status {code}"));
        }
        Err(e) => {
            out.error = Some(e.to_string());
        }
    }
    out
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UrlCheck {
    pub url: String,
    pub reachable: bool,
    pub status: u16,
    pub latency_ms: u64,
    pub error: Option<String>,
}

/// HEAD com fallback GET parcial para checar disponibilidade.
pub fn check_url(agent: &ureq::Agent, url: &str) -> UrlCheck {
    let t0 = std::time::Instant::now();
    let parsed = match Url::parse(url) {
        Ok(u) => u,
        Err(e) => {
            return UrlCheck {
                url: url.into(),
                reachable: false,
                status: 0,
                latency_ms: 0,
                error: Some(format!("url inválida: {e}")),
            }
        }
    };
    let (reachable, status, error) = match agent.head(parsed.as_str()).call() {
        Ok(resp) => {
            let s = resp.status().as_u16();
            (resp.status().is_success(), s, None)
        }
        Err(ureq::Error::StatusCode(_code)) => {
            // HEAD pode não ser suportado: tenta GET
            match agent.get(parsed.as_str()).call() {
                Ok(resp) => {
                    let s = resp.status().as_u16();
                    (resp.status().is_success(), s, None)
                }
                Err(ureq::Error::StatusCode(c)) => (false, c, Some(format!("status {c}"))),
                Err(e) => (false, 0, Some(e.to_string())),
            }
        }
        Err(e) => (false, 0, Some(e.to_string())),
    };
    UrlCheck {
        url: url.into(),
        reachable,
        status,
        latency_ms: t0.elapsed().as_millis() as u64,
        error,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_title_og_image_favicon() {
        let html = r#"<html><head><title>Meu Jogo 2048</title>
            <meta property="og:image" content="/img/cover.png">
            <link rel="shortcut icon" href="/favicon2.ico">
        </head><body></body></html>"#;
        assert_eq!(extract_title(html).as_deref(), Some("Meu Jogo 2048"));
        assert_eq!(
            extract_meta(html, &["og:image"]).as_deref(),
            Some("/img/cover.png")
        );
        let base = Url::parse("https://play2048.co/").unwrap();
        assert_eq!(
            favicon_from_html(html, &base).as_deref(),
            Some("https://play2048.co/favicon2.ico")
        );
    }

    #[test]
    fn absolute_join() {
        let base = Url::parse("https://exemplo.com/dir/page.html").unwrap();
        assert_eq!(
            absolute(&base, "cover.png").as_deref(),
            Some("https://exemplo.com/dir/cover.png")
        );
    }

    #[test]
    fn validate_game_https() {
        let mut g = crate::models::Game {
            id: "x".into(),
            name: "x".into(),
            url: "ftp://x.com".into(),
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
        assert!(g.validate().is_err());
        g.url = "https://x.com".into();
        assert!(g.validate().is_ok());
        assert_eq!(g.host().as_deref(), Some("x.com"));
    }
}
