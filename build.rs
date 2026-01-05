use std::env;
use std::path::PathBuf;

fn main() {
    if env::var_os("CARGO_FEATURE_CAPI").is_none() {
        return;
    }
    println!("cargo:rerun-if-changed=src/capi.rs");
    let version = env::var("CARGO_PKG_VERSION").expect("CARGO_PKG_VERSION not set");

    let mut iter = version.split('.');
    let major = iter.next().unwrap_or("0");
    let minor = iter.next().unwrap_or("0");
    let patch = iter.next().unwrap_or("0");

    let crate_dir = env::var("CARGO_MANIFEST_DIR").unwrap();
    let out_dir = PathBuf::from(env::var("OUT_DIR").unwrap());

    let header_path = out_dir.join("payload_dumper.hpp");

    let config = cbindgen::Config {
        language: cbindgen::Language::Cxx,
        include_guard: Some("PAYLOAD_DUMPER_HPP".to_string()),
        cpp_compat: true,
        pragma_once: true,

        header: Some(format!(
            "#define PAYLOAD_DUMPER_MAJOR {}\n\
             #define PAYLOAD_DUMPER_MINOR {}\n\
             #define PAYLOAD_DUMPER_PATCH {}\n",
            major, minor, patch
        )),

        ..Default::default()
    };

    cbindgen::Builder::new()
        .with_crate(crate_dir)
        .with_config(config)
        .generate()
        .expect("Failed to generate C++ bindings")
        .write_to_file(&header_path);
    println!(
        "cargo:warning=Generated C API header at {}",
        header_path.display()
    );
}
