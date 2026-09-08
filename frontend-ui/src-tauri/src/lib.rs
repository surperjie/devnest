/// DevNest 桌面应用 Rust 入口.
///
/// 职责:
/// - 启动时拉起后端 Spring Boot jar(通过 java -jar)
/// - 窗口关闭时自动结束后端进程,避免残留
///
/// @Author Ajiejiejie
/// @Date 2026/9/7 10:00
use std::net::TcpStream;
use std::process::{Child, Command};
use std::sync::Mutex;
use std::time::Duration;

use tauri::Manager;

/// 后端进程句柄,全局持有以便窗口关闭时 kill
static BACKEND_PROCESS: Mutex<Option<Child>> = Mutex::new(None);

/// 后端监听端口
const BACKEND_PORT: u16 = 8080;
/// 后端 jar 文件名
const BACKEND_JAR: &str = "devnest-boot.jar";

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .setup(|app| {
            // 启动后端进程(独立线程,避免阻塞 async runtime)
            let handle = app.handle().clone();
            std::thread::spawn(move || {
                start_backend(&handle);
            });
            Ok(())
        })
        .on_window_event(|window, event| {
            // 主窗口关闭时杀掉后端进程
            if let tauri::WindowEvent::Destroyed = event {
                stop_backend();
            }
        })
        .run(tauri::generate_context!())
        .expect("error while running DevNest application");
}

/// 启动后端:优先复用已运行实例(端口占用即视为已启动),否则拉起 java -jar
fn start_backend(app: &tauri::AppHandle) {
    // 1. 端口已被监听 → 后端已在运行,无需重复启动
    if is_port_in_use(BACKEND_PORT) {
        eprintln!("[DevNest] 端口 {BACKEND_PORT} 已被占用,假设后端已启动");
        return;
    }

    // 2. 解析 jar 路径:打包后从 resource_dir 取,开发时取 target 目录
    let jar_path = resolve_jar_path(app);
    eprintln!("[DevNest] 后端 jar 路径: {jar_path:?}");

    match jar_path {
        Some(path) => {
            // 查找 java 可执行文件
            let java = find_java();
            if java.is_none() {
                eprintln!("[DevNest] 未找到 java 命令,请确保已安装 JDK 21 并配置 PATH");
                return;
            }
            let java = java.unwrap();

            // 启动后端:stdout/stderr 继承,方便查看日志
            let child = Command::new(java)
                .args(["-jar", path.to_str().unwrap(), "--spring.profiles.active=dev"])
                .spawn();

            match child {
                Ok(c) => {
                    eprintln!("[DevNest] 后端进程已启动,pid={}", c.id());
                    *BACKEND_PROCESS.lock().unwrap() = Some(c);
                }
                Err(e) => eprintln!("[DevNest] 后端启动失败: {e}"),
            }

            // 3. 等待后端就绪(最多 30s)
            wait_for_backend_ready(30);
        }
        None => {
            eprintln!("[DevNest] 未找到后端 jar 文件,后端功能不可用");
        }
    }
}

/// 窗口关闭时杀掉后端进程
fn stop_backend() {
    let mut guard = BACKEND_PROCESS.lock().unwrap();
    if let Some(mut child) = guard.take() {
        eprintln!("[DevNest] 正在关闭后端进程,pid={}", child.id());
        // Windows 下 kill 是强制结束,可接受
        let _ = child.kill();
        let _ = child.wait();
        eprintln!("[DevNest] 后端进程已结束");
    }
}

/// 检测端口是否被占用
fn is_port_in_use(port: u16) -> bool {
    TcpStream::connect(("127.0.0.1", port))
        .map(|s| drop(s))
        .is_ok()
}

/// 等待后端 HTTP 端口就绪
fn wait_for_backend_ready(timeout_secs: u64) {
    for _ in 0..timeout_secs * 2 {
        if is_port_in_use(BACKEND_PORT) {
            eprintln!("[DevNest] 后端已就绪 (端口 {BACKEND_PORT})");
            return;
        }
        std::thread::sleep(Duration::from_millis(500));
    }
    eprintln!("[DevNest] 等待后端超时({timeout_secs}s),部分功能可能不可用");
}

/// 解析后端 jar 路径:
/// - 打包后:resource_dir/devnest-boot.jar
/// - 开发环境:../backend/devnest-boot/target/devnest-boot-1.0.0.jar
fn resolve_jar_path(app: &tauri::AppHandle) -> Option<std::path::PathBuf> {
    // 优先用 resource_dir(打包场景)
    if let Ok(dir) = app.path().resource_dir() {
        let p = dir.join(BACKEND_JAR);
        if p.exists() {
            return Some(p);
        }
    }
    // 开发环境:从项目相对路径找
    let dev_path = std::path::PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("..")
        .join("..")
        .join("backend")
        .join("devnest-boot")
        .join("target")
        .join("devnest-boot-1.0.0.jar");
    if dev_path.exists() {
        return Some(dev_path);
    }
    None
}

/// 查找 java 可执行文件:优先 JAVA_HOME,其次 PATH
fn find_java() -> Option<std::path::PathBuf> {
    if let Ok(java_home) = std::env::var("JAVA_HOME") {
        let p = std::path::PathBuf::from(java_home)
            .join("bin")
            .join(if cfg!(windows) { "java.exe" } else { "java" });
        if p.exists() {
            return Some(p);
        }
    }
    // PATH 查找
    if let Ok(path) = which::which("java") {
        return Some(path);
    }
    None
}
