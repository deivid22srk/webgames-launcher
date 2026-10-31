//! WebGames Experimental Engine (Fase B) — prova de conceito de runtime nativo.
//!
//! Roda módulos WASM reais em um runtime Rust puro (wasmi), sem navegador.
//! Isto valida a fundação do "motor experimental": carregar, instanciar e
//! executar código WASM compilado do lado nativo com o mesmo pipeline wgpu.
//!
//! Um motor de browser completo (DOM/JS/Canvas) continua fora de escopo —
//! ver docs/ARCHITECTURE.md e docs/DECISIONS.md (ADR-005).

mod wasm_min;

use serde::{Deserialize, Serialize};
use wasmi::{Engine, Linker, Module, Store};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EngineStatus {
    pub available: bool,
    pub runtime: String,
    pub runtime_version: String,
    pub detail: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BenchmarkResult {
    pub engine: String,
    pub fib30_ms: f64,
    pub sum_to_10m_ms: f64,
    pub fib30_result: i32,
    pub ok: bool,
    pub error: Option<String>,
}

pub struct EngineRunner;

impl EngineRunner {
    /// Verifica se o runtime WASM carrega e instancia um módulo real.
    pub fn status() -> EngineStatus {
        let runtime = format!("wasmi {}", env!("CARGO_PKG_VERSION"));
        match Self::instantiate_min_module() {
            Ok(detail) => EngineStatus {
                available: true,
                runtime: "wasmi".into(),
                runtime_version: runtime,
                detail,
            },
            Err(e) => EngineStatus {
                available: false,
                runtime: "wasmi".into(),
                runtime_version: runtime,
                detail: format!("falha: {e}"),
            },
        }
    }

    fn instantiate_min_module() -> Result<String, String> {
        let bytes = wasm_min::build_min_module();
        let engine = Engine::default();
        let module = Module::new(&engine, &bytes[..]).map_err(|e| e.to_string())?;
        let mut store = Store::new(&engine, ());
        let linker: Linker<()> = Linker::new(&engine);
        let instance = linker
            .instantiate_and_start(&mut store, &module)
            .map_err(|e| e.to_string())?;
        let add = instance
            .get_typed_func::<(i32, i32), i32>(&store, "add")
            .map_err(|e| e.to_string())?;
        let r = add.call(&mut store, (21, 21)).map_err(|e| e.to_string())?;
        if r != 42 {
            return Err(format!("add(21,21) = {r}, esperado 42"));
        }
        Ok("módulo mínimo instanciado e executado com sucesso".into())
    }

    /// Micro-benchmark honesto de CPU via WASM nativo (fib recursivo + loop).
    pub fn run_benchmark() -> BenchmarkResult {
        let mut res = BenchmarkResult {
            engine: "wasmi".into(),
            fib30_ms: 0.0,
            sum_to_10m_ms: 0.0,
            fib30_result: 0,
            ok: false,
            error: None,
        };
        let run = || -> Result<(f64, f64, i32), String> {
            let bytes = wasm_min::build_min_module();
            let engine = Engine::default();
            let module = Module::new(&engine, &bytes[..]).map_err(|e| e.to_string())?;
            let mut store = Store::new(&engine, ());
            let linker: Linker<()> = Linker::new(&engine);
            let instance = linker
                .instantiate_and_start(&mut store, &module)
                .map_err(|e| e.to_string())?;
            let fib = instance
                .get_typed_func::<i32, i32>(&store, "fib")
                .map_err(|e| e.to_string())?;
            let sum = instance
                .get_typed_func::<i32, i32>(&store, "sum_to")
                .map_err(|e| e.to_string())?;

            // aquecimento
            let _ = fib.call(&mut store, 10).map_err(|e| e.to_string())?;

            let t0 = std::time::Instant::now();
            let r = fib.call(&mut store, 30).map_err(|e| e.to_string())?;
            let fib30 = t0.elapsed().as_secs_f64() * 1000.0;

            let t1 = std::time::Instant::now();
            let _ = sum
                .call(&mut store, 10_000_000)
                .map_err(|e| e.to_string())?;
            let sum10m = t1.elapsed().as_secs_f64() * 1000.0;
            Ok((fib30, sum10m, r))
        };
        match run() {
            Ok((fib30, sum10m, r)) => {
                res.fib30_ms = fib30;
                res.sum_to_10m_ms = sum10m;
                res.fib30_result = r;
                res.ok = true;
            }
            Err(e) => res.error = Some(e),
        }
        res
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn module_instantiates_and_adds() {
        let s = EngineRunner::status();
        assert!(s.available, "status: {}", s.detail);
        assert!(s.detail.contains("sucesso"));
    }

    #[test]
    fn fib_and_sum_correct() {
        let bytes = wasm_min::build_min_module();
        let engine = Engine::default();
        let module = Module::new(&engine, &bytes[..]).unwrap();
        let mut store = Store::new(&engine, ());
        let linker: Linker<()> = Linker::new(&engine);
        let instance = linker.instantiate_and_start(&mut store, &module).unwrap();
        let fib = instance.get_typed_func::<i32, i32>(&store, "fib").unwrap();
        let sum = instance
            .get_typed_func::<i32, i32>(&store, "sum_to")
            .unwrap();
        assert_eq!(fib.call(&mut store, 10).unwrap(), 55);
        assert_eq!(fib.call(&mut store, 20).unwrap(), 6765);
        assert_eq!(sum.call(&mut store, 100).unwrap(), 5050);
    }

    #[test]
    fn benchmark_runs() {
        let b = EngineRunner::run_benchmark();
        assert!(b.ok, "bench: {:?}", b.error);
        assert_eq!(b.fib30_result, 832040);
        assert!(b.fib30_ms > 0.0);
    }
}
