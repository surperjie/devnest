/// DevNest 桌面应用 Rust 入口.
///
/// @Author Ajiejiejie
/// @Date 2026/9/1 14:55
#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .run(tauri::generate_context!())
        .expect("error while running DevNest application");
}
