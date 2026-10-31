fn main() {
    // libandroid para ANativeWindow_* (usado em android_surface.rs no Android).
    let target = std::env::var("TARGET").unwrap_or_default();
    if target.contains("android") {
        println!("cargo:rustc-link-lib=dylib=android");
    }
}
