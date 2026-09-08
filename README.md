# DevNest

> SSH 隧道 · 远程控制台 · 数据库 · Redis —— 一体化桌面运维工作台。
>
> 前端 Tauri 2 + Vue 3 桌面应用;后端 Spring Boot 3 常驻 `127.0.0.1:38080`,由 UI 拉起并随 UI 退出,所有流量不经过公网。

## 功能模块

| 模块 | 说明 |
|---|---|
| SSH 隧道 | 跳板机管理 + 本地端口转发(断线自动重连、心跳保活),支持内网数据库/Redis 接入 |
| 远程控制台 | 保存 SSH 会话配置,WebSocket 打开虚拟终端(一次性 token 握手) |
| 数据源 | MySQL / 达梦(DM)等 JDBC 数据源,库表树、分页预览、SQL 执行与历史 |
| Redis | 实例管理 + INFO / db / SCAN 键浏览 / key 查看删除 / 命令白名单执行 |
| 配置迁移 | 跳板、控制台均支持 JSON 导出/导入(跨环境迁移,导出含解密密码) |

## 项目结构

```
devnest/
├── backend/                    # Spring Boot 3 多模块后端
│   ├── devnest-boot/           #   启动模块(打包 fat jar,含 Flyway 迁移脚本)
│   ├── devnest-common/         #   通用:响应体、错误码、CORS、配置加密等
│   ├── devnest-tunnel/         #   SSH 隧道模块
│   ├── devnest-console/        #   远程控制台 + WebSocket
│   ├── devnest-datasource/     #   数据源管理与 SQL 查询
│   └── devnest-redis/          #   Redis 实例与 ops
├── frontend-ui/                # Tauri 2 + Vue 3 桌面前端
│   └── src-tauri/              #   Rust 壳:窗口 + 后端进程生命周期管理
│       └── resources/          #   运行产物:devnest-boot.jar + jre21(打包后)
├── scripts/
│   └── build-jre.ps1           # jlink 裁剪 JRE(模块清单含 jdk.unsupported)
├── docs/                       # 需求、架构、接口文档
│   └── API.md                  # 后端 HTTP / WebSocket 接口文档
├── build-app.ps1               # 本地一键打包(可选 -SkipBackend)
└── .github/workflows/build-app.yml   # CI 打包(与本地脚本同一套 jlink 逻辑)
```

## 技术栈

- **后端**:Java 21 · Spring Boot 3 · JPA/Hibernate + Flyway · H2(dev,`MODE=MySQL`)/ MySQL(prod) · JSch(SSH)· 虚拟线程
- **前端**:Tauri 2 · Vue 3 · Vite 5 · Rust 1.7x+
- **打包运行时**:GraalVM/OpenJDK 21 `jlink` 裁剪出 `jre21`(约 60MB)内嵌分发

## 本地开发

### 后端

```powershell
cd backend/devnest-boot
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

- 默认绑定 `127.0.0.1:38080`,H2 文件库落在 `<工作目录>/data/devnest-dev.mv.db`,重启数据不丢;
- 表结构由 Flyway 迁移(`backend/devnest-boot/src/main/resources/db/migration`)管理,JPA 仅 `validate`;
- 接口文档见 [docs/API.md](docs/API.md)。

> 注意:后端要求 JDK 21。本机默认 `JAVA_HOME` 若为 1.8,请勿裸跑 `mvn`,用上面命令前先切到 JDK 21。

### 前端

```powershell
cd frontend-ui
npm install
npm run tauri:dev
```

- dev server 端口 `1420`,后端 CORS 已放行 `127.0.0.1:1420` / `localhost:1420` / `tauri://localhost` / `http://tauri.localhost`;
- 前端 axios 直连 `http://127.0.0.1:38080/api`(见 `src/api/http.js`)。

## 打包发布

### 本地一键打包(无需推送 GitHub)

```powershell
.\build-app.ps1            # Maven 打包 jar → 复制到 resources → jlink 生成 jre21 → tauri build
.\build-app.ps1 -SkipBackend   # 后端 jar 已有变动时跳过 Maven 步骤
```

产物:`frontend-ui/src-tauri/target/release/bundle/`(NSIS exe / MSI)。
注意:本地产物未做代码签名,安装时可能触发 SmartScreen。

### CI

推送后由 `.github/workflows/build-app.yml` 执行同一套流程并产出带签名/发布的安装包。

### JRE 生成(scripts/build-jre.ps1)

`jlink` 模块清单**必须包含 `jdk.unsupported`**:缺它时后端启动会报
`ClassNotFoundException: sun.misc.Unsafe`(仅 Spring CGLIB 使用)。本地与 CI 共用此脚本,避免再次漏配。

## 常见问题

### UI 拉起的后端控制台闪一下/一直 Network Error

历史根因(Tauri 打包路径带 `\\?\` verbatim 前缀,`java -jar` 无法打开该形式 jar,进程秒退)。
已在 `frontend-ui/src-tauri/src/lib.rs` 修复:传给 java 前还原为普通 Win32 路径,并同时:
- 后端进程不再弹独立控制台窗口(`CREATE_NO_WINDOW`);
- 后端自身日志重定向到 **`%TEMP%\devnest-backend.log`**(UTF-8);
- UI 启动流程日志在 `%TEMP%\devnest-app.log`。

### 手动启动后端中文乱码

原因:后端日志按 **UTF-8** 输出(JDK 18+ 默认),而 Windows 简体控制台默认按 **GBK(代码页 936)** 解码,于是中文变成 `鏃犻渶...`/`锟斤拷` 一类乱码(UI 内部拉起的后端已由前端直接落盘 UTF-8 文件 `%TEMP%\devnest-backend.log`,不受此问题影响)。

推荐用仓库自带的一键启动脚本,它会先把当前控制台切成 UTF-8 再启动(顺带自动定位 JDK 21):

```powershell
.\scripts\start-backend.ps1
```

等效的手动姿势(二选一,先切代码页再跑 java):

```powershell
chcp 65001
.\resources\jre21\bin\java.exe -jar .\resources\devnest-boot.jar --spring.profiles.active=dev
```

## 文档

- 接口文档:[docs/API.md](docs/API.md)
- 需求与架构:[docs/](docs/)
