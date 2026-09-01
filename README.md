# DevNest

> SSH 隧道 + 数据库 桌面工作台。

## 项目结构

```
devnest/
├── backend        # SpringBoot 3 Java 后端(SSH 隧道、数据库逻辑)
├── frontend-ui    # Tauri 2 + Vue 3 前端桌面项目
├── docs           # 需求文档、架构文档
└── README.md
```

## 快速开始

### 后端 (backend)

```bash
cd backend
mvn spring-boot:run
```

默认端口 `8080`,数据库配置见 [application.yml](backend/src/main/resources/application.yml)。

### 前端 (frontend-ui)

```bash
cd frontend-ui
npm install
npm run tauri:dev
```

首次运行会下载 Rust 依赖,耗时较长。

## 技术栈

- **后端**:Spring Boot 3.2.5 / Java 17 / JPA / MySQL / JSch(SSH 隧道)
- **前端**:Tauri 2 / Vue 3 / Vite 5

## 文档

需求与架构文档见 [docs/](docs/)。
