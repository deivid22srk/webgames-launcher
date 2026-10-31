fn main() {
    // Vincula libandroid para ANativeWindow_fromSurface / ANativeWindow_release.
    let target = std::env::var("TARGET").unwrap_or_default();
    if target.contains("android") {
        println!("cargo:rustc-link-lib=dylib=android");
        println!("cargo:rustc-link-lib=dylib=log");
    }
}
