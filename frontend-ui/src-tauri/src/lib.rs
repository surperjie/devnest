/// DevNest 桌面应用 Rust 入口.
///
/// 职责:
/// - 启动时拉起后端 Spring Boot jar(优先使用打包 JRE 21,回退系统 java)
/// - 为后端指定固定的可写工作目录(H2 数据、流水线工作区都落在该目录下)
/// - 窗口关闭时先请求后端优雅关闭(actuator/shutdown),超时才强杀,避免 H2 数据损坏
/// - 单实例保护:重复启动时直接退出,避免多开互抢后端与端口
/// - 全程写日志到 %TEMP%/devnest-app.log(每次启动清空)
///
/// @Author Ajiejiejie
/// @Date 2026/9/7 10:00
use std::fs::{self, File, OpenOptions};
use std::io::{Read, Write};
use std::net::{SocketAddr, TcpStream};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::Mutex;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

#[cfg(windows)]
use std::os::windows::fs::OpenOptionsExt;
#[cfg(windows)]
use std::os::windows::process::CommandExt;

use tauri::Manager;

/// 后端进程句柄,全局持有以便窗口关闭时结束后端
static BACKEND_PROCESS: Mutex<Option<Child>> = Mutex::new(None);
/// 单实例锁文件句柄:必须全程持有,句柄一关锁就释放
static INSTANCE_LOCK: Mutex<Option<File>> = Mutex::new(None);

/// 后端监听端口
const BACKEND_PORT: u16 = 38080;
/// 后端 jar 文件名
const BACKEND_JAR: &str = "devnest-boot.jar";
/// 打包 JRE 目录名
const BUNDLED_JRE_DIR: &str = "jre21";
/// 桌面打包运行时使用的 Spring profile(内嵌 H2 文件库,零配置)
const DESKTOP_PROFILE: &str = "desktop";
/// 等待后端就绪的最长时间(首次启动含 Flyway 迁移)
const READY_TIMEOUT_SECS: u64 = 60;
/// 优雅关闭等待上限,超时才强杀
const SHUTDOWN_WAIT_SECS: u64 = 10;
#[cfg(windows)]
/// 子进程不创建控制台窗口:避免 UI 拉起后端时弹出/闪一下的黑窗,也防止它随控制台关闭而被终止
const CREATE_NO_WINDOW: u32 = 0x0800_0000;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    // 启动前清空上次日志
    clear_log();

    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_dialog::init())
        .setup(|app| {
            // 后端工作目录:H2 数据/流水线工作区都落在它下面,先确定并保证可写
            let work_dir = resolve_work_dir();

            // 单实例:已有实例在跑时直接退出,避免多开互抢后端与端口
            match acquire_instance_lock(&work_dir) {
                Some(lock) => match INSTANCE_LOCK.lock() {
                    Ok(mut guard) => *guard = Some(lock),
                    Err(poisoned) => *poisoned.into_inner() = Some(lock),
                },
                None => {
                    log("Another DevNest instance is already running, exiting");
                    app.handle().exit(0);
                    return Ok(());
                }
            }

            let handle = app.handle().clone();
            std::thread::spawn(move || {
                start_backend(&handle, work_dir);
            });
            Ok(())
        })
        .on_window_event(|_window, event| {
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

/// 后端自身日志统一重定向到 %TEMP%\devnest-backend.log(UTF-8,每次启动重建).
/// 这样 UI 拉起的后端不再弹出/占用控制台(默认 GBK 控制台显示 UTF-8 中文会乱码),
/// 日志可读性好且便于排查后端崩溃。
fn open_backend_log() -> Option<File> {
    let path = std::env::temp_dir().join("devnest-backend.log");
    match OpenOptions::new().create(true).truncate(true).write(true).open(path) {
        Ok(mut f) => {
            let _ = writeln!(f, "=== DevNest backend log started at epoch {}", now_epoch());
            Some(f)
        }
        Err(_) => None,
    }
}

fn now_epoch() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

/// tauri 的 resource_dir()/canonicalize 会返回带 \\?\ 前缀的 verbatim 路径,
/// 而 `java -jar "\\?\E:\...\app.jar"` 会加载失败(报 "Could not find or load
/// main class JarLauncher"),因此传给 java 前必须还原成普通 Win32 路径;
/// UNC 前缀 \\?\UNC\server\share 需还原成 \\server\share。
fn to_win32_path(p: &std::path::Path) -> std::path::PathBuf {
    let s = p.as_os_str().to_string_lossy();
    let s = s
        .strip_prefix(r"\\?\UNC\")
        .map(|r| format!(r"\\{r}"))
        .or_else(|| s.strip_prefix(r"\\?\").map(|r| r.to_string()))
        .unwrap_or_else(|| s.to_string());
    std::path::PathBuf::from(s)
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

fn start_backend(app: &tauri::AppHandle, work_dir: PathBuf) {
    log("=== DevNest backend startup ===");

    // 端口已有服务:必须区分"自己的后端在跑"与"被其它程序占用",
    // 否则占用时会静默跳过启动,UI 只报 network error,用户完全无从判断。
    if let Some(is_devnest) = probe_backend(Duration::from_millis(800)) {
        if is_devnest {
            log(&format!("Port {BACKEND_PORT} already serving DevNest backend, skip starting"));
            return;
        }
        log(&format!("ERROR: port {BACKEND_PORT} is occupied by another program"));
        show_error_dialog(
            app,
            "DevNest 后端启动失败",
            &format!(
                "端口 {BACKEND_PORT} 已被其它程序占用,DevNest 后端无法启动。\n\
                 请关闭占用该端口的程序后重新打开 DevNest。"
            ),
        );
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
                "DevNest 后端启动失败",
                "未找到可用的 Java 运行环境。\n已尝试:内置 JRE、JAVA_HOME、PATH。\n请重新安装 DevNest(安装包自带 JRE)。",
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
                "DevNest 后端启动失败",
                "未找到 devnest-boot.jar,安装包可能不完整,请重新安装 DevNest。",
            );
            return;
        }
    };

    // 3. 启动后端
    // 关键点 1:tauri 的 resource_dir() 返回带 \\?\ 前缀的 verbatim 路径,
    //   java -jar 无法打开这种 jar,会立即退出("Could not find or load main class
    //   JarLauncher"),导致 UI 一直 network error。这里必须还原为普通 Win32 路径。
    // 关键点 2:显式指定工作目录。H2 配置库(./data)与流水线工作区(data/pipeline)
    //   都是相对路径,不指定 cwd 时双击启动的 cwd 可能是 System32 或安装目录,
    //   轻则数据落点漂移,重则因无写权限直接启动失败。
    let java_norm = to_win32_path(&java);
    let jar_norm = to_win32_path(&jar_path);
    let jar_str = jar_norm.to_string_lossy().to_string();
    log(&format!(
        "Starting: {} -jar {} --spring.profiles.active={DESKTOP_PROFILE} --server.port={BACKEND_PORT} (cwd={})",
        java_norm.display(),
        jar_str,
        work_dir.display()
    ));
    let mut cmd = Command::new(&java_norm);
    cmd.arg("-jar")
        .arg(&jar_str)
        .arg(format!("--spring.profiles.active={DESKTOP_PROFILE}"))
        .arg(format!("--server.port={BACKEND_PORT}"))
        .current_dir(&work_dir)
        .stdin(Stdio::null());
    // 不弹独立控制台窗口;后端 stdout/stderr 重定向到 UTF-8 日志文件
    #[cfg(windows)]
    {
        cmd.creation_flags(CREATE_NO_WINDOW);
    }
    if let Some(f) = open_backend_log() {
        match f.try_clone() {
            Ok(f2) => {
                cmd.stdout(Stdio::from(f)).stderr(Stdio::from(f2));
            }
            Err(_) => {
                cmd.stdout(Stdio::from(f));
                cmd.stderr(Stdio::null());
            }
        }
    }

    match cmd.spawn() {
        Ok(c) => {
            log(&format!("Backend process started, pid={}", c.id()));
            match BACKEND_PROCESS.lock() {
                Ok(mut guard) => *guard = Some(c),
                Err(poisoned) => *poisoned.into_inner() = Some(c),
            }
        }
        Err(e) => {
            log(&format!("ERROR: failed to spawn backend: {e}"));
            show_error_dialog(app, "DevNest 后端启动失败", &format!("后端进程启动失败:\n{e}"));
            return;
        }
    }

    // 4. 等待后端就绪(首次启动含 Flyway 迁移,最长 READY_TIMEOUT_SECS 秒)
    log(&format!("Waiting for backend to be ready (max {READY_TIMEOUT_SECS}s)..."));
    if let Err(msg) = wait_for_backend_ready(READY_TIMEOUT_SECS) {
        log(&format!("Backend not ready: {msg}"));
        // 清掉半死不活的后端进程,避免残留占端口
        stop_backend();
        show_error_dialog(app, "DevNest 后端启动失败", &msg);
    }
}

/// 结束后端:先请它自己优雅退出(释放 H2/SSH 隧道/连接池),超时才强杀.
/// 直接 kill 会让 H2 文件库来不及 flush,存在损坏后"下次打不开"的风险.
fn stop_backend() {
    log("=== DevNest backend shutdown ===");
    let mut guard = match BACKEND_PROCESS.lock() {
        Ok(g) => g,
        Err(poisoned) => poisoned.into_inner(),
    };
    let Some(mut child) = guard.take() else {
        log("No backend process to stop");
        return;
    };
    let pid = child.id();
    log(&format!("Requesting graceful shutdown, pid={pid}"));

    // 1) 请求优雅关闭(actuator/shutdown 仅放行本机请求,符合预期)
    match http_request("POST", "/actuator/shutdown", Duration::from_secs(3)) {
        Some(resp) => log(&format!("shutdown endpoint responded: {}", first_line(&resp))),
        None => log("shutdown endpoint not reachable, will kill process"),
    }

    // 2) 等待进程自行退出
    let deadline = Instant::now() + Duration::from_secs(SHUTDOWN_WAIT_SECS);
    while Instant::now() < deadline {
        match child.try_wait() {
            Ok(Some(status)) => {
                log(&format!("Backend exited gracefully: {status}"));
                return;
            }
            Ok(None) => std::thread::sleep(Duration::from_millis(200)),
            Err(e) => {
                log(&format!("try_wait failed: {e}"));
                break;
            }
        }
    }

    // 3) 超时兜底强杀
    log("Graceful shutdown timed out, killing process");
    let _ = child.kill();
    let _ = child.wait();
    log("Backend process terminated");
}

fn first_line(text: &str) -> String {
    text.lines().next().unwrap_or("").trim().to_string()
}

// ========================================================================
// 工具函数
// ========================================================================

// ========================================================================
// 运行环境:工作目录 / 单实例
// ========================================================================

/// 解析后端工作目录(H2 配置库与流水线工作区的落点).
///
/// 不用 exe 同目录:安装到 Program Files 时不可写,且卸载会连数据一起删.
/// 统一放 %LOCALAPPDATA%\DevNest:一定可写、与安装目录解耦、卸载不丢数据;
/// 极端情况下(取不到 LOCALAPPDATA)退回 %TEMP%\DevNest,保证总有可写目录.
fn resolve_work_dir() -> PathBuf {
    if let Some(local) = std::env::var_os("LOCALAPPDATA") {
        let dir = PathBuf::from(local).join("DevNest");
        if ensure_writable_dir(&dir) {
            log(&format!("Work dir: {dir:?}"));
            return dir;
        }
    }
    let dir = std::env::temp_dir().join("DevNest");
    let _ = ensure_writable_dir(&dir);
    log(&format!("Work dir (temp fallback): {dir:?}"));
    dir
}

/// 目录可写探测:能创建并写入临时文件才算可写
fn is_writable(dir: &Path) -> bool {
    let probe = dir.join(".devnest-write-test");
    match OpenOptions::new()
        .create(true)
        .write(true)
        .truncate(true)
        .open(&probe)
    {
        Ok(_) => {
            let _ = fs::remove_file(&probe);
            true
        }
        Err(_) => false,
    }
}

fn ensure_writable_dir(dir: &Path) -> bool {
    fs::create_dir_all(dir).is_ok() && is_writable(dir)
}

/// 单实例锁:独占打开 <work_dir>/app.lock(Windows 下 share_mode(0) 表示不共享).
/// 打开失败说明已有实例在运行 —— 此时直接退出,避免两个实例互抢后端,
/// 出现"关掉其中一个把后端也带走、另一个变孤儿"的情况.
fn acquire_instance_lock(work_dir: &Path) -> Option<File> {
    let path = work_dir.join("app.lock");
    let mut opts = OpenOptions::new();
    opts.create(true).write(true);
    #[cfg(windows)]
    opts.share_mode(0);
    opts.open(&path).ok()
}

// ========================================================================
// 后端探测:极简本机 HTTP 客户端(只为探活,避免引入额外依赖)
// ========================================================================

fn is_port_open(port: u16, timeout: Duration) -> bool {
    match format!("127.0.0.1:{port}").parse::<SocketAddr>() {
        Ok(addr) => TcpStream::connect_timeout(&addr, timeout).is_ok(),
        Err(_) => false,
    }
}

fn http_request(method: &str, path: &str, timeout: Duration) -> Option<String> {
    let addr: SocketAddr = format!("127.0.0.1:{BACKEND_PORT}").parse().ok()?;
    let mut stream = TcpStream::connect_timeout(&addr, timeout).ok()?;
    stream.set_read_timeout(Some(timeout)).ok()?;
    stream.set_write_timeout(Some(timeout)).ok()?;
    let req = format!(
        "{method} {path} HTTP/1.1\r\nHost: 127.0.0.1:{BACKEND_PORT}\r\nAccept: application/json\r\nConnection: close\r\nContent-Length: 0\r\n\r\n"
    );
    stream.write_all(req.as_bytes()).ok()?;
    let mut buf = String::new();
    stream.read_to_string(&mut buf).ok()?;
    Some(buf)
}

/// 探测 38080 上的服务:
/// - None        端口没有服务
/// - Some(true)  是 DevNest 后端(/actuator/health 返回 UP)
/// - Some(false) 端口被其它程序占用
fn probe_backend(timeout: Duration) -> Option<bool> {
    if !is_port_open(BACKEND_PORT, timeout) {
        return None;
    }
    Some(match http_request("GET", "/actuator/health", timeout) {
        Some(resp) => resp.contains("\"status\":\"UP\""),
        None => false,
    })
}

/// 等待后端就绪:轮询 health 端点;期间后端进程若已退出(常见于缺模块/配置错误),
/// 直接把日志尾部作为错误信息返回,让弹窗能显示真正的原因.
fn wait_for_backend_ready(timeout_secs: u64) -> Result<(), String> {
    let deadline = Instant::now() + Duration::from_secs(timeout_secs);
    let mut elapsed_ms = 0u64;
    while Instant::now() < deadline {
        if probe_backend(Duration::from_millis(800)) == Some(true) {
            log(&format!("Backend is ready (took about {elapsed_ms}ms)"));
            return Ok(());
        }
        if let Some(status) = backend_exited() {
            let tail = backend_log_tail(1500);
            log(&format!("ERROR: backend exited early: {status}"));
            return Err(format!(
                "后端进程意外退出({status})。\n\n后端日志尾部:\n{tail}"
            ));
        }
        std::thread::sleep(Duration::from_millis(500));
        elapsed_ms += 500;
    }
    let tail = backend_log_tail(1500);
    Err(format!(
        "后端在 {timeout_secs} 秒内未就绪。\n\n后端日志尾部:\n{tail}"
    ))
}

/// 后端进程是否已退出(仍在运行返回 None)
fn backend_exited() -> Option<std::process::ExitStatus> {
    let mut guard = match BACKEND_PROCESS.lock() {
        Ok(g) => g,
        Err(poisoned) => poisoned.into_inner(),
    };
    let child = guard.as_mut()?;
    match child.try_wait() {
        Ok(Some(status)) => Some(status),
        Ok(None) => None,
        Err(e) => {
            log(&format!("try_wait failed: {e}"));
            None
        }
    }
}

/// 读取后端日志尾部,把崩溃原因直接展示给用户,省去翻 %TEMP% 的功夫
fn backend_log_tail(max_chars: usize) -> String {
    let path = std::env::temp_dir().join("devnest-backend.log");
    let text = fs::read_to_string(&path).unwrap_or_else(|_| "(未找到后端日志)".to_string());
    let chars: Vec<char> = text.chars().collect();
    if chars.len() <= max_chars {
        text
    } else {
        chars[chars.len() - max_chars..].iter().collect()
    }
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

    // 4. 开发环境:仅 debug 构建查找,避免把构建机的绝对路径固化进 release 包
    #[cfg(debug_assertions)]
    {
        let dev_path = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
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
