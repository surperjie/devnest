# DevNest Wiki

**SSH 隧道 · 远程控制台 · 数据库 · Redis · 流水线** —— 可快速扩展的**开发工作台基站**。

> **平台管治理，工具管业务 —— 新增一个工具模块，平台代码改动 = 0。**
>
> 判据是机器可验的：新增一个工具模块后平台改动行数必须为 0；故意违反任一架构约束，CI 必须变红。**判据不靠人评审。**

---

## 这个 Wiki 是什么

**入口与操作手册，不是第二份权威文档。**

| 内容类型 | 在哪 | 权威性 |
|---|---|---|
| 需求 / 架构 / 接口设计 | 仓库 [`docs/`](https://github.com/surperjie/devnest/tree/main/docs) | **唯一权威** |
| 入门导航 / 操作步骤 / 排障 | 本 Wiki | 导航与操作，不重述设计结论 |
| 接口行为 / 表结构 | 代码 / Flyway 脚本 | **最终以它们为准** |

同一问题两边说法不一致时，**以仓库 `docs/` 与代码为准**。本 Wiki 的源文件在 `docs/wiki/`，改动请走 PR。

---

## 常见入口

| 我想…… | 看这里 |
|---|---|
| 把项目跑起来 | [快速开始](快速开始) |
| 知道有哪些模块、代码放哪 | [项目结构](项目结构) |
| 搞清楚 CI 到底在拦什么 | [质量门禁](质量门禁) |
| 打一个安装包 / 发版 | [CI 与发布](CI与发布) |
| 找某份设计文档 | [文档地图](文档地图) |
| 排障（乱码 / Network Error / 覆盖率门禁红） | [常见问题](常见问题) |
| 提 PR / 改架构 / 加新工具模块 | [贡献指南](贡献指南) |

---

## 当前坐标

| 维度 | 现状 |
|---|---|
| 架构阶段 | **S1 单机自用** —— 单进程多模块，模块间零横向依赖，协作一律经 `core.spi` 接口 |
| 成熟度 | **L2 可拦截** —— 测试被强制执行、质量有阈值（P0 已完成） |
| 下一步 | P1 装配形态 → P2 治理纵深 → P3 安全隔离 → P4 演进纪律 → P5 扩展点闭环 |

> 阶段边界与阶段义务见 [架构演进路线](https://github.com/surperjie/devnest/blob/main/docs/architecture/20260910_架构演进路线.md)；
> 具体任务、落点、门禁、验收见 [落地路线图](https://github.com/surperjie/devnest/blob/main/docs/architecture/20260910_目标架构落地路线图.md)。

---

## 技术栈速查

**后端**

- Java 21 · Spring Boot 3.2.5 · 虚拟线程
- JPA / Hibernate + **Flyway** 迁移（JPA 仅 `validate`）
- H2（开发，`MODE=MySQL`）/ MySQL（生产）
- JSch（SSH）· Jedis（Redis）· Caffeine · Resilience4j

**前端**

- Tauri 2 · Vue 3.4 · Vite 5.2 · Element Plus
- CodeMirror 6（SQL 编辑器）· xterm.js（终端）· Axios

**打包运行时**

- `jlink` 裁剪内嵌 `jre21`（约 60MB），安装后**无需 JDK**
- Windows 安装包：NSIS exe / MSI

**质量**

- JaCoCo · ArchUnit · maven-enforcer · OWASP dependency-check · Testcontainers

---

## 一句话记住三个底线

1. **测试必须真的跑** —— 后端构建走 `mvn verify`，不存在 `package -DskipTests`。
2. **违规必须机器拦** —— 没有执行者的约束不是约束，是愿望（见[质量门禁](质量门禁)）。
3. **权威只在一处** —— 同一问题不在多处给答案（见[文档地图](文档地图)）。
