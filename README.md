<p align="center">
  <img src="frontend-ui/app-icon.png" width="128" alt="DevNest Logo" />
</p>

<h1 align="center">DevNest</h1>

<p align="center">
  <b>SSH 隧道 · 远程控制台 · 数据库 · Redis · 流水线</b><br/>
  可快速扩展的 <b>开发工作台基站</b>
</p>

<p align="center">
  <b>平台管治理，工具管业务 —— 新增一个工具模块，平台代码改动 = 0。</b>
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

- [定位](#定位)
- [架构](#架构)
- [功能模块](#功能模块)
- [项目结构](#项目结构)
- [技术栈](#技术栈)
- [本地开发](#本地开发)
- [打包发布](#打包发布)
- [常见问题](#常见问题)
- [文档](#文档)
- [License](#license)

---

## 定位

DevNest 不是把若干功能堆在一起的工具箱，而是一个**开发工作台基站**：

| 维度 | 含义 |
|---|---|
| **平台管治理** | 形态装配、资源登记、可观测、安全、隔离 —— 由平台统一提供。 |
| **工具管业务** | 隧道、控制台、数据源、Redis、流水线 —— 各自独立模块，互不依赖。 |
| **可快速扩展** | 新工具只需实现工具契约，**治理能力自动继承**，平台无需改动。 |

### 扩展判据（机器可验，非口号）

> **新增一个工具模块，平台改动行数 = 0；故意违反任一架构约束，CI 必须变红。**

前半句是 S3 的验收标准，后半句由 P0 质量门禁保证。**判据必须能被机器验，不能靠人评审。**

### 亮点

- 🧬 **可扩展基站**：平台管治理、工具管业务，新工具零改动接入（终态 fitness function C11 断言）。
- 🖥️ **桌面原生体验**：Tauri 2 + Vue 3，后端作为本地守护进程随 UI 启动/退出，零公网流量。
- 🔌 **内网穿透**：SSH 隧道断线自动重连、心跳保活，让本地也能连上内网 MySQL / DM / Redis。
- 🖲️ **Web 终端**：xterm.js 虚拟终端，WebSocket + 一次性 token 握手，保存常用 SSH 会话。
- 🗄️ **数据库工作台**：库表树、分页预览、SQL 执行与历史记录，支持 MySQL / 达梦（DM）。
- ⚡ **Redis 管理**：INFO / db / SCAN 键浏览、key 查看删除、命令白名单执行。
- 🧩 **流水线编排**：多步骤脚本编排、本机进程执行、跨步骤结果传递、多标签运行。
- 🛡️ **质量门禁内建**：JaCoCo 增量覆盖率、ArchUnit 架构约束、依赖收敛、OWASP 漏洞扫描。
- 📦 **单文件分发**：`jlink` 裁剪出约 60MB 的 `jre21` 内嵌打包，安装后无需 JDK。

---

## 架构

> 完整设计以 [目标架构 v2.0](docs/architecture/20260910_目标架构_v2.0.md) 为准；到达路径见 [架构演进路线](docs/architecture/20260910_架构演进路线.md) 与 [落地路线图](docs/architecture/20260910_目标架构落地路线图.md)。
>
> ⚠️ 本文档中标注为**现状**的图对应当前代码；标注为**终态**的图是设计蓝图，**尚未实现**。

### 现状架构（S1 · 单机自用）

单进程多模块，模块间**零横向依赖**，协作一律经 `core.spi` 接口；桌面壳以 sidecar 方式拉起后端。

```mermaid
flowchart TB
    subgraph Shell["桌面壳 · Tauri 2"]
        direction LR
        UI["Vue 3 UI<br/>Element Plus / CodeMirror / xterm.js"]
        RSH["Rust 壳<br/>窗口 + 后端进程生命周期"]
    end

    subgraph App["devnest-boot · Spring Boot 3 · 127.0.0.1:38080"]
        direction TB
        subgraph Biz["业务模块 · 彼此零横向依赖"]
            direction LR
            TN["devnest-tunnel<br/>SSH 隧道"]
            CS["devnest-console<br/>远程控制台"]
            DS["devnest-datasource<br/>数据源"]
            RD["devnest-redis<br/>Redis"]
            PL["devnest-pipeline<br/>流水线"]
        end
        CO["devnest-core<br/>BaseEntity · JPA · 缓存 · 线程池 · 连接池工厂 · SPI 契约"]
        CM["devnest-common<br/>响应体 · 错误码 · CORS · 加密 · SQL 校验"]
    end

    H2[("H2 文件库<br/>配置库（本地）")]
    SSH[("SSH 主机 / 跳板机")]
    DB[("MySQL / 达梦")]
    RDS[("Redis")]

    RSH -->|"拉起 / 优雅关闭"| App
    UI -->|"HTTP + WebSocket"| App
    Biz --> CO
    CO --> CM
    TN --> SSH
    CS --> SSH
    DS --> DB
    RD --> RDS
    App --> H2
```

**编译期强制**：`maven-enforcer` 锁定依赖方向 `common ← core ← {tunnel, console, datasource, redis, pipeline} ← boot`，业务模块横向依赖直接构建失败。

### 目标架构（S3 · 工具巢穴 · 终态）

平台提供治理能力，工具提供业务能力。四层结构 + 防劣化防线，**新工具对平台零改动**。

```mermaid
flowchart TB
    subgraph Access["① 接入层 · 同一份 jar，两种形态"]
        direction LR
        A1["桌面壳 Tauri<br/>devnest.mode=local"]
        A2["浏览器<br/>devnest.mode=server"]
    end

    subgraph Gov["② 治理层 · 平台提供（新工具零改动继承）"]
        direction LR
        G1["形态装配<br/>形态元注解 + 条件化装配"]
        G2["扩展点契约<br/>DevNestTool / ToolRegistry"]
        G3["资源统一登记<br/>ResourceRegistry · 启动自检"]
        G4["全维度可观测<br/>资源视图 / SLI / 健康 / trace"]
        G5["安全纵深<br/>鉴权 · 审计 · 密钥轮换"]
        G6["隔离与配额<br/>Bulkhead · 熔断 · 限流"]
    end

    subgraph Tool["③ 工具层 · 工具提供业务"]
        direction LR
        T1["SSH 隧道"]
        T2["远程控制台"]
        T3["数据源"]
        T4["Redis"]
        T5["流水线"]
        T6["未来工具<br/>HTTP 调试 / AI 辅助"]
    end

    subgraph Gate["④ 防劣化 · 约束由机器执行"]
        direction LR
        Q1["maven-enforcer<br/>依赖边界 · 版本收敛"]
        Q2["ArchUnit<br/>分层方向 · 形态语义 · 弃用标注"]
        Q3["JaCoCo<br/>增量行覆盖 ≥ 80%"]
        Q4["OWASP<br/>CVSS ≥ 7 即红"]
    end

    A1 --> Gov
    A2 --> Gov
    Gov -->|"治理能力自动继承"| Tool
    Gate -.->|"违规即红"| Gov
    Gate -.->|"违规即红"| Tool
```

**四根支柱**（互相支撑，不可偏科）：

| 支柱 | 本质 | 检验刻度 |
|---|---|---|
| **工业化** | 交付可重复、质量可拦截 | CI 不跳过测试；覆盖率/漏洞/架构有阈值；发布可回滚 |
| **可演进** | 变更成本不随时间上升 | 能安全地**加 / 改 / 删**：扩展点 + 契约版本 + 迁移范式 + ADR |
| **可治理** | 看得见 **且** 管得住 | 资源总账 + 指标健康日志；鉴权、审计、配额隔离 |
| **防劣化** | 违背约束时**机器拦截** | 约束 → 拦截矩阵全覆盖；每条约束都有负向验收用例 |

### 双形态：一份 jar，两种装配

形态由**启动参数**决定，不由代码分支决定。切换形态**不需要改任何业务代码**。

```mermaid
flowchart TB
    JAR["同一份 devnest-boot.jar"]
    JAR -->|"devnest.mode=local（默认）"| L
    JAR -->|"devnest.mode=server"| S

    subgraph L["LOCAL 形态 · 桌面壳"]
        direction TB
        L1["绑定 127.0.0.1"]
        L2["单用户 · 无鉴权"]
        L3["数据全量可见"]
        L4["H2 文件库 · 零配置"]
    end

    subgraph S["SERVER 形态 · 浏览器"]
        direction TB
        S1["绑定内网网卡"]
        S2["多用户 · 完整鉴权"]
        S3["按 owner / visibility 过滤"]
        S4["MySQL · 审计落库"]
    end
```

> **不变量**：引入服务端能力**不得**要求本地登录或配置（本地形态可用性不被牺牲）；前提被打破时必须**显式失败**而非静默运行。

### 演进路径与成熟度

```mermaid
flowchart LR
    S1["S1 · 单机自用<br/>模块化 + 编译期边界"] -->|"形态稳定"| S2["S2 · 双形态<br/>装配层 + 身份层 + 状态归属"]
    S2 -->|"出现第二个工具"| S3["S3 · 工具巢穴<br/>扩展点 + 治理层"]
```

| 成熟度 | 判据 | 状态 |
|---|---|---|
| **L1 可构建** | 一键构建 + 启动冒烟 | ✅ 已达成 |
| **L2 可拦截** | 测试被强制执行，质量有阈值 | ✅ P0 已落地 |
| **L3 可演进** | 契约与迁移受治理，变更可安全落地 | 📘 规划中（P4） |
| **L4 可治理** | 全维度可观测 + 闭环防劣化，新工具零改动接入 | 📘 终态（P2/P3/P5） |

**当前坐标**：`S1 单机形态 / L2 可拦截 / P0 已完成`。执行计划见 [落地路线图](docs/architecture/20260910_目标架构落地路线图.md)（P0 → P1 装配形态 → P2 治理纵深 → P3 安全隔离 → P4 演进纪律 → P5 扩展点闭环）。

### 约束 → 拦截矩阵

**没有执行者的约束不是约束，是愿望。** 每条架构约束都有唯一的机器执行者：

| 约束 | 拦截手段 | 时机 |
|---|---|---|
| 业务模块间零横向依赖 | `maven-enforcer` `bannedDependencies` | 构建 validate |
| 依赖版本收敛 | `maven-enforcer` `dependencyConvergence` | 构建 validate |
| 无已知高危依赖 | OWASP `dependency-check`（CVSS ≥ 7 即红） | CI |
| 分层依赖方向正确 | ArchUnit（`core` 不得依赖任何实现包） | CI |
| 新增运行时资源必须登记 | `ResourceRegistry` 启动自检 + 测试基座 | 启动 + CI |
| SERVER 形态不得装配本地实现 | `ServerModeConsistencyCheck`（启动即失败） | 启动 |
| 覆盖率不下降 | JaCoCo **增量行覆盖** ≥ 80% | CI |
| 新增代码行必须有测试 | 增量覆盖率门禁（只卡变更行） | CI |
| 形态语义不得散落业务代码 | ArchUnit：禁用裸 `@ConditionalOnProperty` | CI |
| 弃用标注完整 | ArchUnit：`@Deprecated` 必须带 `since` | CI |

> 完整矩阵（C1–C13 + 8 条负向验收用例 N1–N8）见 [目标架构 v2.0 §5](docs/architecture/20260910_目标架构_v2.0.md)。

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
│   ├── architecture/                #   架构文档、能力清单、演进路线、目标架构、落地路线图
│   ├── requirements/                #   需求规格说明
│   └── wiki/                        #   GitHub Wiki 页面源文件（scripts/sync-wiki.ps1 发布）
├── build-app.ps1                    # 本地一键打包（可选 -SkipBackend）
└── .github/workflows/build-app.yml  # CI：日常 push 只跑门禁，v* 标签 / 手动触发才打包
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
- **JaCoCo** · **ArchUnit** · **maven-enforcer** · **OWASP dependency-check** · **Testcontainers**

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

### 质量门禁

后端构建跑 `mvn verify`（而非 `package -DskipTests`），四道门禁：

| 门禁 | 工具 | 阈值 |
|---|---|---|
| 单元 / 集成测试 | Surefire + Failsafe + Testcontainers | 失败即红 |
| 覆盖率 | JaCoCo | **增量行覆盖 ≥ 80%** |
| 依赖收敛与漏洞 | enforcer + OWASP | CVSS ≥ 7 即红 |
| 架构约束 | ArchUnit | 违规即红 |

增量覆盖率门禁由 `scripts/check-incremental-coverage.ps1` 执行，本地与 CI 共用同一份实现。

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

`.github/workflows/build-app.yml` 把「门禁」和「打包」拆开，**日常提交只跑门禁，不产安装包**：

| 触发 | 执行 | 产物 |
|---|---|---|
| push 到 `main` / `dev`（命中 `paths`） | `quality-gate` | 无安装包（仅归档测试 / 覆盖率报告） |
| push `v*` 标签 | `quality-gate` + `build` | NSIS exe / MSI |
| 手动 `Run workflow`（勾选 `build_app`） | `quality-gate` + `build` | NSIS exe / MSI |

- `quality-gate`（Ubuntu）：`mvn verify` + JaCoCo + ArchUnit + Testcontainers + 增量覆盖率门禁 + OWASP 漏洞门禁（需 `NVD_API_KEY`，缺失时显式标注“未执行”）。
- `build`（Windows）：依赖 `quality-gate`，只做 Windows 打包（Tauri / jlink）。

> 安装包是**发布产物**，不是每次提交的副产物 —— 一次 Windows 打包要 10~20 分钟，而绝大多数提交并不改变发行版内容。需要安装包时推一个 `v*` 标签，或手动勾选 `build_app`。
>
> GitHub 的 `paths` 过滤对 tag 推送**不生效**（官方语义：*Path filters are not evaluated for pushes of tags*），因此标签发版不会被 `paths` 漏掉。日常 push 的门禁一刻没松：测试 / 覆盖率 / 架构 / 漏洞仍然每次都有执行者。

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

文档按"回答什么问题"分四层，**每层有唯一权威文档**：

| 层 | 回答的问题 | 权威文档 |
|---|---|---|
| **L0 需求** | 要做什么 | [docs/requirements/需求.md](docs/requirements/需求.md) |
| **L1 现状** | 现在是什么样 | [架构文档](docs/architecture/20260910_架构文档.md)（机制） / [能力清单](docs/architecture/20260910_当前实现能力清单.md)（可用性） |
| **L2 演进** | 怎么走 | [架构演进路线](docs/architecture/20260910_架构演进路线.md) / [落地路线图](docs/architecture/20260910_目标架构落地路线图.md) |
| **L3 目标** | 要去哪 | [目标架构 v2.0](docs/architecture/20260910_目标架构_v2.0.md) |

| 文档 | 说明 |
|---|---|
| [docs/API.md](docs/API.md) | 后端 HTTP / WebSocket 接口文档 |
| [docs/README.md](docs/README.md) | 文档目录与完整索引 |
| [Wiki](https://github.com/surperjie/devnest/wiki) | 入门导航 / 操作手册（快速开始、质量门禁、CI 与发布、FAQ）；权威内容仍以本仓库 `docs/` 为准 |

---

## License

[MIT](LICENSE) © 2026 Jie
