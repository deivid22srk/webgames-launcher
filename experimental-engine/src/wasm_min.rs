//! Codificador minimalista de WASM binário (sem dependências).
//! Gera um módulo com três funções exportadas:
//! - `add(i32, i32) -> i32`
//! - `fib(i32) -> i32` (recursiva)
//! - `sum_to(i32) -> i32` (loop)

fn uleb(mut v: u32, out: &mut Vec<u8>) {
    loop {
        let mut b = (v & 0x7F) as u8;
        v >>= 7;
        if v != 0 {
            b |= 0x80;
        }
        out.push(b);
        if v == 0 {
            break;
        }
    }
}

fn section(id: u8, payload: &[u8], out: &mut Vec<u8>) {
    out.push(id);
    uleb(payload.len() as u32, out);
    out.extend_from_slice(payload);
}

fn name(s: &str, out: &mut Vec<u8>) {
    out.push(s.len() as u8);
    out.extend_from_slice(s.as_bytes());
}

/// Bytes de um módulo WASM válido com add/fib/sum_to.
pub fn build_min_module() -> Vec<u8> {
    let mut m = Vec::with_capacity(256);
    m.extend_from_slice(&[0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00]); // \0asm v1

    // Type section: 0:(i32,i32)->i32 ; 1:(i32)->i32
    let mut types = Vec::new();
    types.push(2);
    types.extend_from_slice(&[0x60, 2, 0x7F, 0x7F, 1, 0x7F]);
    types.extend_from_slice(&[0x60, 1, 0x7F, 1, 0x7F]);
    section(1, &types, &mut m);

    // Function section: fib=type1, sum_to=type1, add=type0
    section(3, &[3, 1, 1, 0], &mut m);

    // Export section
    let mut exp = Vec::new();
    exp.push(3);
    name("fib", &mut exp);
    exp.push(0x00);
    uleb(0, &mut exp);
    name("sum_to", &mut exp);
    exp.push(0x00);
    uleb(1, &mut exp);
    name("add", &mut exp);
    exp.push(0x00);
    uleb(2, &mut exp);
    section(7, &exp, &mut m);

    // Code section
    let mut code = Vec::new();
    code.push(3); // 3 corpos

    // fib(n): locals 0 — if com tipo de resultado [i32] (0x7F)
    let fib_body = [
        0x00, // 0 locals
        0x20, 0x00, // local.get 0
        0x41, 0x02, // i32.const 2
        0x48, // i32.lt_s
        0x04, 0x7F, // if (result i32)
        0x20, 0x00, //   local.get 0
        0x05, // else
        0x20, 0x00, //   local.get 0
        0x41, 0x01, //   i32.const 1
        0x6B, //   i32.sub
        0x10, 0x00, //   call 0 (fib)
        0x20, 0x00, //   local.get 0
        0x41, 0x02, //   i32.const 2
        0x6B, //   i32.sub
        0x10, 0x00, //   call 0
        0x6A, //   i32.add
        0x0B, // end if
        0x0B, // end fn
    ];
    uleb(fib_body.len() as u32, &mut code);
    code.extend_from_slice(&fib_body);

    // sum_to(n): 1 local i32 (acc)
    let sum_body = [
        0x01, 0x01, 0x7F, // 1 local i32
        0x41, 0x00, // i32.const 0
        0x21, 0x01, // local.set acc
        0x02, 0x40, // block
        0x03, 0x40, //   loop
        0x20, 0x00, //     local.get n
        0x45, //     i32.eqz
        0x0D, 0x01, //     br_if 1 (sai do block)
        0x20, 0x01, //     local.get acc
        0x20, 0x00, //     local.get n
        0x6A, //     i32.add
        0x21, 0x01, //     local.set acc
        0x20, 0x00, //     local.get n
        0x41, 0x01, //     i32.const 1
        0x6B, //     i32.sub
        0x21, 0x00, //     local.set n
        0x0C, 0x00, //     br 0 (loop)
        0x0B, //   end loop
        0x0B, // end block
    ];
    let mut sum_full = sum_body.to_vec();
    sum_full.extend_from_slice(&[0x20, 0x01, 0x0B]); // local.get acc; end fn
    uleb(sum_full.len() as u32, &mut code);
    code.extend_from_slice(&sum_full);

    // add(a,b): locals 0
    let add_body = [
        0x00, // 0 locals
        0x20, 0x00, // local.get 0
        0x20, 0x01, // local.get 1
        0x6A, // i32.add
        0x0B, // end fn
    ];
    uleb(add_body.len() as u32, &mut code);
    code.extend_from_slice(&add_body);

    section(10, &code, &mut m);
    m
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn module_has_valid_header_and_size() {
        let m = build_min_module();
        assert_eq!(&m[..4], b"\0asm");
        assert!(m.len() > 60);
        println!(
            "hex: {}",
            m.iter()
                .map(|b| format!("{:02x}", b))
                .collect::<Vec<_>>()
                .join(" ")
        );
    }
}

#[cfg(test)]
mod debug_tests {
    use super::*;
    use wasmi::{Engine, Module};

    fn try_parse(m: Vec<u8>) -> Result<(), String> {
        let engine = Engine::default();
        Module::new(&engine, &m[..])
            .map(|_| ())
            .map_err(|e| e.to_string())
    }

    fn module_with(bodies: &[&[u8]], types: &[u8], funcs: &[u8]) -> Vec<u8> {
        let mut m = Vec::new();
        m.extend_from_slice(&[0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00]);
        let mut t = Vec::new();
        t.push(2); // 2 tipos fixos
        t.extend_from_slice(types);
        section(1, &t, &mut m);
        let mut f = Vec::new();
        f.push(funcs.len() as u8);
        f.extend_from_slice(funcs);
        section(3, &f, &mut m);
        let mut code = Vec::new();
        code.push(bodies.len() as u8);
        for b in bodies {
            uleb(b.len() as u32, &mut code);
            code.extend_from_slice(b);
        }
        section(10, &code, &mut m);
        m
    }

    #[test]
    fn debug_partial_modules() {
        let add = [0x00u8, 0x20, 0x00, 0x20, 0x01, 0x6A, 0x0B];
        let fib = [
            0x00u8, 0x20, 0x00, 0x41, 0x02, 0x48, 0x04, 0x7F, 0x20, 0x00, 0x05, 0x20, 0x00, 0x41,
            0x01, 0x6B, 0x10, 0x00, 0x20, 0x00, 0x41, 0x02, 0x6B, 0x10, 0x00, 0x6A, 0x0B, 0x0B,
        ];
        let sum = [
            0x01u8, 0x01, 0x7F, 0x41, 0x00, 0x21, 0x01, 0x02, 0x40, 0x03, 0x40, 0x20, 0x00, 0x45,
            0x0D, 0x01, 0x20, 0x01, 0x20, 0x00, 0x6A, 0x21, 0x01, 0x20, 0x00, 0x41, 0x01, 0x6B,
            0x21, 0x00, 0x0C, 0x00, 0x0B, 0x0B, 0x20, 0x01, 0x0B,
        ];
        // t0: (i32,i32)->i32 ; t1: (i32)->i32
        let types = [0x60u8, 2, 0x7F, 0x7F, 1, 0x7F, 0x60, 1, 0x7F, 1, 0x7F];

        let m_add = module_with(&[&add], &types, &[0]);
        println!("add only: {:?}", try_parse(m_add));

        let m_add_fib = module_with(&[&fib, &add], &types, &[1, 0]);
        println!("add+fib: {:?}", try_parse(m_add_fib));

        let m_add_fib_sum = module_with(&[&fib, &sum, &add], &types, &[1, 1, 0]);
        println!("add+fib+sum: {:?}", try_parse(m_add_fib_sum));
    }
}
