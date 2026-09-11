<p align="center">
  <img src="frontend-ui/app-icon.png" width="128" alt="DevNest Logo" />
</p>

<h1 align="center">DevNest</h1>

<p align="center">
  <b>SSH 隧道 · 远程控制台 · 数据库 · Redis · 流水线</b><br/>
  一体化桌面运维工作台
</p>

<p align="center">
  <a href="https://github.com/surperjie/devnest/actions/workflows/build-app.yml">
    <img src="https://github.com/surperjie/devnest/actions/workflows/build-app.yml/badge.svg?branch=main" alt="Build Status" />
  </a>
  <img src="https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2.5-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot 3.2.5" />
  <img src="https://img.shields.io/badge/Vue-3.4-4FC08D?logo=vuedotjs&logoColor=white" alt="Vue 3" />
  <img src="https://img.shields.io/badge/Tauri-2-24C8D8?logo=tauri&logoColor=white" alt="Tauri 2" />
  <img src="https://img.shields.io/badge/Vite-5.2-646CFF?logo=vite&logoColor=white" alt="Vite 5" />
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="MIT License" />
</p>

---

## 目录

- [亮点](#亮点)
- [功能模块](#功能模块)
- [项目结构](#项目结构)
- [技术栈](#技术栈)
- [本地开发](#本地开发)
- [打包发布](#打包发布)
- [常见问题](#常见问题)
- [文档](#文档)
- [License](#license)

---

## 亮点

- 🖥️ **桌面原生体验**：Tauri 2 + Vue 3，后端作为本地守护进程随 UI 启动/退出，零公网流量。
- 🔌 **内网穿透**：SSH 隧道断线自动重连、心跳保活，让本地也能连上内网 MySQL / DM / Redis。
- 🖲️ **Web 终端**：xterm.js 虚拟终端，WebSocket + 一次性 token 握手，保存常用 SSH 会话。
- 🗄️ **数据库工作台**：库表树、分页预览、SQL 执行与历史记录，支持 MySQL / 达梦(DM)。
- ⚡ **Redis 管理**：INFO / db / SCAN 键浏览、key 查看删除、命令白名单执行。
- 🧩 **流水线编排**：多步骤脚本编排、本机进程执行、跨步骤结果传递、多标签运行。
- 🛡️ **质量门禁内建**：JaCoCo 覆盖率、ArchUnit 架构约束、依赖收敛、maven-enforcer 模块边界。
- 📦 **单文件分发**：`jlink` 裁剪出约 60MB 的 `jre21` 内嵌打包，安装后无需 JDK。

---

## 功能模块

| 模块 | 说明 |
|---|---|
| 🖥️ **SSH 隧道** | 跳板机管理 + 本地端口转发（断线自动重连、心跳保活），支持内网数据库/Redis 接入。 |
| 🖲️ **远程控制台** | 保存 SSH 会话配置，WebSocket 打开虚拟终端（一次性 token 握手）。 |
| 🗄️ **数据源** | MySQL / 达梦（DM）等 JDBC 数据源，库表树、分页预览、SQL 执行与历史。 |
| 🔴 **Redis** | 实例管理 + INFO / db / SCAN 键浏览 / key 查看删除 / 命令白名单执行。 |
| 🧩 **流水线** | 多步骤脚本编排、本机进程执行、跨步骤结果传递、多标签运行。 |
| 🔄 **配置迁移** | 跳板、控制台均支持 JSON 导出/导入（跨环境迁移，导出含解密密码）。 |

---

## 项目结构

```text
devnest/
├── backend/                         # Spring Boot 3 多模块后端
│   ├── devnest-boot/                #   启动模块（打包 fat jar，含 Flyway 迁移脚本）
│   ├── devnest-common/              #   通用：响应体、错误码、CORS、配置加密等
│   ├── devnest-core/                #   领域基础层：BaseEntity、JPA、缓存、线程池、SPI 接口
│   ├── devnest-tunnel/              #   SSH 隧道模块
│   ├── devnest-console/             #   远程控制台 + WebSocket
│   ├── devnest-datasource/          #   数据源管理与 SQL 查询
│   ├── devnest-redis/               #   Redis 实例与 ops
│   └── devnest-pipeline/            #   流水线模块
├── frontend-ui/                     # Tauri 2 + Vue 3 桌面前端
│   └── src-tauri/                   #   Rust 壳：窗口 + 后端进程生命周期管理
│       └── resources/               #   运行产物：devnest-boot.jar + jre21（打包后）
├── scripts/                         # 构建/开发辅助脚本
│   ├── build-jre.ps1                #   jlink 裁剪 JRE（模块清单含 jdk.unsupported）
│   ├── check-incremental-coverage.ps1 # 增量覆盖率门禁脚本
│   └── start-backend.ps1            #   本地一键启动后端（自动切 UTF-8 + JDK 21）
├── docs/                            # 需求、架构、接口文档
│   ├── API.md                       #   后端 HTTP / WebSocket 接口文档
│   ├── README.md                    #   文档目录与索引
│   ├── architecture/                #   架构文档、路线图、能力清单
│   └── requirements/                #   需求规格说明
├── build-app.ps1                    # 本地一键打包（可选 -SkipBackend）
└── .github/workflows/build-app.yml  # CI 打包（与本地脚本同一套 jlink 逻辑）
```

---

## 技术栈

### 后端

![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-6DB33F?logo=springboot&logoColor=white)
![Hibernate](https://img.shields.io/badge/Hibernate-6.x-59666C?logo=hibernate&logoColor=white)
![Flyway](https://img.shields.io/badge/Flyway-✓-CC0200?logo=flyway&logoColor=white)

- **Java 21** · **Spring Boot 3.2.5** · 虚拟线程
- **JPA / Hibernate** + **Flyway** 数据库迁移
- **H2**（开发，`MODE=MySQL`）/ **MySQL**（生产）
- **JSch**（SSH）· **Jedis**（Redis）· **Caffeine** · **Resilience4j**
- **JaCoCo** · **ArchUnit** · **maven-enforcer** · **OWASP dependency-check**

### 前端

![Vue](https://img.shields.io/badge/Vue-3.4-4FC08D?logo=vuedotjs&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-5.2-646CFF?logo=vite&logoColor=white)
![Tauri](https://img.shields.io/badge/Tauri-2-24C8D8?logo=tauri&logoColor=white)
![Element Plus](https://img.shields.io/badge/Element%20Plus-2.7-409EFF?logo=element&logoColor=white)

- **Tauri 2** · **Vue 3.4** · **Vite 5.2** · **Element Plus**
- **CodeMirror 6**（SQL 编辑器）· **xterm.js**（终端）· **Axios**

### 打包运行时

- **GraalVM / OpenJDK 21** 通过 `jlink` 裁剪出内嵌 `jre21`（约 60MB）
- Windows 安装包：**NSIS exe** / **MSI**

---

## 本地开发

### 后端

```powershell
cd backend/devnest-boot
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

- 默认绑定 `127.0.0.1:38080`。
- H2 文件库落在 `<工作目录>/data/devnest-dev.mv.db`，重启数据不丢。
- 表结构由 Flyway 迁移（`backend/devnest-boot/src/main/resources/db/migration`）管理，JPA 仅 `validate`。
- 接口文档见 [docs/API.md](docs/API.md)。

> ⚠️ 后端要求 **JDK 21**。本机默认 `JAVA_HOME` 若为 1.8，请勿裸跑 `mvn`，用上面命令前先切到 JDK 21。

### 前端

```powershell
cd frontend-ui
npm install
npm run tauri:dev
```

- dev server 端口 `1420`，后端 CORS 已放行 `127.0.0.1:1420` / `localhost:1420` / `tauri://localhost` / `http://tauri.localhost`。
- 前端 axios 直连 `http://127.0.0.1:38080/api`（见 `src/api/http.js`）。

---

## 打包发布

### 本地一键打包（无需推送 GitHub）

```powershell
.\build-app.ps1            # Maven 打包 jar → 复制到 resources → jlink 生成 jre21 → tauri build
.\build-app.ps1 -SkipBackend   # 后端 jar 无变动时跳过 Maven 步骤
```

产物：`frontend-ui/src-tauri/target/release/bundle/`（NSIS exe / MSI）。
注意：本地产物未做代码签名，安装时可能触发 SmartScreen。

### 打包后的运行方式（零配置）

安装后双击即用，不需要装数据库、不需要改任何配置：

- 首次启动自动创建 `%LOCALAPPDATA%\DevNest\`，后端以 `desktop` profile 启动，使用内嵌 H2 文件库。
- 数据落点（固定，不随启动位置漂移）：
  - 配置库：`%LOCALAPPDATA%\DevNest\data\devnest.mv.db`
  - 流水线工作区：`%LOCALAPPDATA%\DevNest\data\pipeline\`
  - 加密主密钥：`%USERPROFILE%\.devnest\master.key`（首次启动自动生成）
- 关闭窗口时先请求后端优雅退出（`POST /actuator/shutdown`），超时才强杀，避免 H2 数据损坏。
- 重复启动会直接退出（单实例锁 `%LOCALAPPDATA%\DevNest\app.lock`），不会出现两个实例互抢后端。
- 若 `38080` 被其它程序占用，会弹窗明确提示（而不是静默失败）。
- 后端起不来时，弹窗会附带 `%TEMP%\devnest-backend.log` 的日志尾部，便于定位。

### CI

推送后由 `.github/workflows/build-app.yml` 执行同一套流程并产出安装包：

- `quality-gate`（Ubuntu）：`mvn verify` + JaCoCo + ArchUnit + Testcontainers + 增量覆盖率门禁 + OWASP 漏洞门禁（需 `NVD_API_KEY`，缺失时显式标注“未执行”）。
- `build`（Windows）：依赖 `quality-gate`，只做 Windows 打包（Tauri / jlink）。

### JRE 生成（`scripts/build-jre.ps1`）

`jlink` 模块清单**必须包含 `jdk.unsupported`**：缺它时后端启动会报 `ClassNotFoundException: sun.misc.Unsafe`（仅 Spring CGLIB 使用）。本地与 CI 共用此脚本，避免再次漏配。

---

## 常见问题

<details>
<summary><b>UI 拉起的后端控制台闪一下 / 一直 Network Error</b></summary>

历史根因：Tauri 打包路径带 `\\?\` verbatim 前缀，`java -jar` 无法打开该形式 jar，进程秒退。

已在 `frontend-ui/src-tauri/src/lib.rs` 修复：传给 java 前还原为普通 Win32 路径，并同时：

- 后端进程不再弹独立控制台窗口（`CREATE_NO_WINDOW`）。
- 后端自身日志重定向到 **`%TEMP%\devnest-backend.log`**（UTF-8）。
- UI 启动流程日志在 `%TEMP%\devnest-app.log`。

</details>

<details>
<summary><b>手动启动后端中文乱码</b></summary>

原因：后端日志按 **UTF-8** 输出（JDK 18+ 默认），而 Windows 简体控制台默认按 **GBK（代码页 936）** 解码，于是中文变成 `鏃犻渶...` / `锟斤拷` 一类乱码。

> UI 内部拉起的后端已由前端直接落盘 UTF-8 文件 `%TEMP%\devnest-backend.log`，不受此问题影响。

推荐用仓库自带的一键启动脚本，它会先把当前控制台切成 UTF-8 再启动（顺带自动定位 JDK 21）：

```powershell
.\scripts\start-backend.ps1
```

等效的手动姿势（二选一，先切代码页再跑 java）：

```powershell
chcp 65001
.\resources\jre21\bin\java.exe -jar .\resources\devnest-boot.jar --spring.profiles.active=dev
```

</details>

---

## 文档

| 文档 | 说明 |
|---|---|
| [docs/API.md](docs/API.md) | 后端 HTTP / WebSocket 接口文档 |
| [docs/README.md](docs/README.md) | 文档目录与索引 |
| [docs/architecture/](docs/architecture/) | 架构文档、当前能力清单、演进路线、落地路线图 |
| [docs/requirements/](docs/requirements/) | 需求规格说明 |

---

## License

[MIT](LICENSE) © 2026 Jie
