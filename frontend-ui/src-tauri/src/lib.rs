/// DevNest 桌面应用 Rust 入口.
///
/// 职责:
/// - 启动时拉起后端 Spring Boot jar(优先使用打包 JRE 21,回退系统 java)
/// - 窗口关闭时自动结束后端进程,避免残留
/// - 全程写日志到 %TEMP%/devnest-app.log(每次启动清空)
///
/// @Author Ajiejiejie
/// @Date 2026/9/7 10:00
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::net::TcpStream;
use std::process::{Child, Command};
use std::sync::Mutex;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use tauri::Manager;

/// 后端进程句柄,全局持有以便窗口关闭时 kill
static BACKEND_PROCESS: Mutex<Option<Child>> = Mutex::new(None);

/// 后端监听端口
const BACKEND_PORT: u16 = 38080;
/// 后端 jar 文件名
const BACKEND_JAR: &str = "devnest-boot.jar";
/// 打包 JRE 目录名
const BUNDLED_JRE_DIR: &str = "jre21";

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    // 启动前清空上次日志
    clear_log();

    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_dialog::init())
        .setup(|app| {
            let handle = app.handle().clone();
            std::thread::spawn(move || {
                start_backend(&handle);
            });
            Ok(())
        })
        .on_window_event(|window, event| {
            if let tauri::WindowEvent::Destroyed = event {
                stop_backend();
            }
        })
        .run(tauri::generate_context!())
        .expect("error while running DevNest application");
}

// ========================================================================
// 日志:同时写入 stderr 和 %TEMP%/devnest-app.log
// ========================================================================

fn log(msg: &str) {
    eprintln!("[DevNest] {msg}");

    let log_path = log_file_path();
    if let Some(path) = &log_path {
        if let Ok(mut f) = OpenOptions::new().create(true).append(true).open(path) {
            let ts = SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .map(|d| d.as_secs())
                .unwrap_or(0);
            let _ = writeln!(f, "[{ts}] {msg}");
        }
    }
}

fn log_file_path() -> Option<std::path::PathBuf> {
    let dir = std::env::temp_dir();
    Some(dir.join("devnest-app.log"))
}

/// 每次启动时清空日志文件,避免无限增长
fn clear_log() {
    if let Some(path) = log_file_path() {
        let _ = fs::write(&path, "");
    }
}

// ========================================================================
// 错误弹窗
// ========================================================================

fn show_error_dialog(app: &tauri::AppHandle, title: &str, message: &str) {
    use tauri_plugin_dialog::DialogExt;
    log(&format!("ERROR DIALOG: {title} | {message}"));
    app.dialog()
        .message(format!(
            "{message}\n\nLog: {}",
            log_file_path()
                .map(|p| p.display().to_string())
                .unwrap_or_default()
        ))
        .title(title)
        .show(|_| {});
}

// ========================================================================
// 后端启动逻辑
// ========================================================================

fn start_backend(app: &tauri::AppHandle) {
    log("=== DevNest backend startup ===");

    if is_port_in_use(BACKEND_PORT) {
        log(&format!("Port {BACKEND_PORT} already in use, assuming backend is running"));
        return;
    }
    log(&format!("Port {BACKEND_PORT} is free, starting backend..."));

    // 1. 查找 java:优先打包 JRE,其次系统 java
    let java = match find_java(app) {
        Some(j) => {
            log(&format!("Using java: {j:?}"));
            j
        }
        None => {
            log("ERROR: java not found (checked bundled JRE, JAVA_HOME, PATH)");
            show_error_dialog(
                app,
                "Backend startup failed",
                "Java not found.\nThe app tried bundled JRE, JAVA_HOME, and PATH.\nPlease install JDK 21.",
            );
            return;
        }
    };

    // 2. 解析 jar 路径
    let jar_path = match resolve_jar_path(app) {
        Some(path) => {
            log(&format!("Backend jar: {path:?}"));
            path
        }
        None => {
            log("ERROR: jar not found in any location");
            log(&format!("  Checked resource_dir: {:?}", app.path().resource_dir().ok()));
            if let Ok(exe) = std::env::current_exe() {
                log(&format!("  exe dir: {:?}", exe.parent()));
            }
            show_error_dialog(
                app,
                "Backend startup failed",
                "devnest-boot.jar not found.\nThe application will start without backend.",
            );
            return;
        }
    };

    // 3. 启动后端
    log(&format!(
        "Starting: {java:?} -jar {:?} --spring.profiles.active=dev --server.port={BACKEND_PORT}",
        jar_path
    ));
    let child = Command::new(&java)
        .args([
            "-jar",
            jar_path.to_str().unwrap(),
            "--spring.profiles.active=dev",
            &format!("--server.port={BACKEND_PORT}"),
        ])
        .spawn();

    match child {
        Ok(c) => {
            log(&format!("Backend process started, pid={}", c.id()));
            *BACKEND_PROCESS.lock().unwrap() = Some(c);
        }
        Err(e) => {
            log(&format!("ERROR: failed to spawn backend: {e}"));
            show_error_dialog(app, "Backend startup failed", &format!("Failed to start backend:\n{e}"));
            return;
        }
    }

    // 4. 等待后端就绪(最多 30s)
    log("Waiting for backend to be ready (max 30s)...");
    wait_for_backend_ready(30);
}

fn stop_backend() {
    log("=== DevNest backend shutdown ===");
    let mut guard = BACKEND_PROCESS.lock().unwrap();
    if let Some(mut child) = guard.take() {
        log(&format!("Killing backend process, pid={}", child.id()));
        let _ = child.kill();
        let _ = child.wait();
        log("Backend process terminated");
    } else {
        log("No backend process to kill");
    }
}

// ========================================================================
// 工具函数
// ========================================================================

fn is_port_in_use(port: u16) -> bool {
    TcpStream::connect(("127.0.0.1", port))
        .map(|s| drop(s))
        .is_ok()
}

fn wait_for_backend_ready(timeout_secs: u64) {
    for i in 0..timeout_secs * 2 {
        if is_port_in_use(BACKEND_PORT) {
            log(&format!("Backend ready! (took {}ms)", i * 500));
            return;
        }
        std::thread::sleep(Duration::from_millis(500));
    }
    log(&format!("WARNING: backend not ready after {timeout_secs}s"));
}

/// 解析后端 jar 路径(尝试多个可能的位置)
fn resolve_jar_path(app: &tauri::AppHandle) -> Option<std::path::PathBuf> {
    // 1. resource_dir/devnest-boot.jar
    if let Ok(dir) = app.path().resource_dir() {
        let p = dir.join(BACKEND_JAR);
        log(&format!("  Checking jar: {p:?} -> {}", p.exists()));
        if p.exists() {
            return Some(p);
        }
        // 2. resource_dir/resources/devnest-boot.jar
        let p2 = dir.join("resources").join(BACKEND_JAR);
        log(&format!("  Checking jar: {p2:?} -> {}", p2.exists()));
        if p2.exists() {
            return Some(p2);
        }
    }

    // 3. exe 同目录
    if let Ok(exe) = std::env::current_exe() {
        if let Some(exe_dir) = exe.parent() {
            let p = exe_dir.join("resources").join(BACKEND_JAR);
            log(&format!("  Checking jar: {p:?} -> {}", p.exists()));
            if p.exists() {
                return Some(p);
            }
            let p = exe_dir.join(BACKEND_JAR);
            log(&format!("  Checking jar: {p:?} -> {}", p.exists()));
            if p.exists() {
                return Some(p);
            }
        }
    }

    // 4. 开发环境
    let dev_path = std::path::PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("..")
        .join("..")
        .join("backend")
        .join("devnest-boot")
        .join("target")
        .join("devnest-boot-1.0.0.jar");
    log(&format!("  Checking jar: {dev_path:?} -> {}", dev_path.exists()));
    if dev_path.exists() {
        return Some(dev_path);
    }

    None
}

/// 查找 java 可执行文件:
/// 1. 打包 JRE (resource_dir/jre21/bin/java.exe)
/// 2. JAVA_HOME
/// 3. PATH
fn find_java(app: &tauri::AppHandle) -> Option<std::path::PathBuf> {
    let exe_name = if cfg!(windows) { "java.exe" } else { "java" };

    // 1. 打包 JRE
    if let Ok(dir) = app.path().resource_dir() {
        let p = dir.join(BUNDLED_JRE_DIR).join("bin").join(exe_name);
        log(&format!("  Checking bundled JRE: {p:?} -> {}", p.exists()));
        if p.exists() {
            return Some(p);
        }
        // 可能多一层 resources/
        let p2 = dir.join("resources").join(BUNDLED_JRE_DIR).join("bin").join(exe_name);
        log(&format!("  Checking bundled JRE: {p2:?} -> {}", p2.exists()));
        if p2.exists() {
            return Some(p2);
        }
    }

    // 2. exe 同目录下的 jre21
    if let Ok(exe) = std::env::current_exe() {
        if let Some(exe_dir) = exe.parent() {
            let p = exe_dir.join("resources").join(BUNDLED_JRE_DIR).join("bin").join(exe_name);
            log(&format!("  Checking bundled JRE: {p:?} -> {}", p.exists()));
            if p.exists() {
                return Some(p);
            }
        }
    }

    // 3. JAVA_HOME
    if let Ok(java_home) = std::env::var("JAVA_HOME") {
        log(&format!("  JAVA_HOME={java_home}"));
        let p = std::path::PathBuf::from(&java_home).join("bin").join(exe_name);
        if p.exists() {
            return Some(p);
        }
        log(&format!("  {p:?} does not exist"));
    } else {
        log("  JAVA_HOME not set");
    }

    // 4. PATH
    match which::which("java") {
        Ok(p) => {
            log(&format!("  Found java in PATH: {p:?}"));
            Some(p)
        }
        Err(_) => {
            log("  java not found in PATH");
            None
        }
    }
}
