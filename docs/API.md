# DevNest 后端接口文档

> 适用版本:2026-09 起 devnest 多模块后端。Desktop 端由 Tauri 拉起,绑定 `127.0.0.1:38080`。

## 1. 基础约定

### 1.1 地址与端口

| 项 | 值 |
|---|---|
| Base URL | `http://127.0.0.1:38080` |
| API 前缀 | `/api`(全部业务接口都在 `/api` 下) |
| 绑定地址 | 仅 `127.0.0.1`(`application.yml` 的 `server.address`),不对外网/局域网暴露 |
| Profile | Desktop 端固定 `--spring.profiles.active=dev`(H2 文件库 + SQL 日志) |
| Actuator | `/actuator/health`、`/actuator/info`、`/actuator/shutdown`(POST) |

### 1.2 统一响应体 `ApiResult<T>`

```json
{ "code": 0, "msg": "ok", "data": {} }
```

| 字段 | 说明 |
|---|---|
| `code` | `0`=成功;非 `0`=失败(见错误码表) |
| `msg` | 成功固定 `"ok"`,失败为可展示的中文原因 |
| `data` | 业务数据,失败为 `null` |

### 1.3 错误码(ErrorCode)

| 码段 | 归属 | 常见码 |
|---|---|---|
| 1001–1008 | SSH 隧道 | `1001` 跳板不存在 / `1002` 名称重复 / `1004` 无可用本地端口 / `1005` 隧道启动失败 / `1006` 未运行 / `1007` 已运行 |
| 2001–2003 | 远程控制台 | `2001` 配置不存在 / `2002` 名称重复 / `2003` WS token 无效 |
| 3001–3012 | 数据源 | `3001` 数据源不存在 / `3002` 名称重复 / `3003` 连接失败 / `3004` 驱动未安装 / `3010` SQL 被安全策略拦截(命中危险操作) / `3011` 执行失败 / `3012` 行数超上限 |
| 3501–3502 | AI SQL | `3501` 未启用 / `3502` 生成失败 |
| 4001–4011 | Redis | `4001` 实例不存在 / `4002` 名称重复 / `4003` 连接失败 / `4010` 命令被白名单拦截 / `4011` 命令执行失败 |
| 6000 | 参数校验失败(请求体标了 `@Valid`) | — |
| 6001 | 系统内部异常 | — |

额外:携带非白名单 `Origin` 的请求被兜底 Filter 拦截,返回 HTTP `403`、`code=4030`(`CORS_ORIGIN_BLOCKED`)。

### 1.4 CORS / WebSocket 白名单

来源必须属于以下之一,否则一律 403:

- `http://127.0.0.1:1420`(前端 vite dev)
- `http://localhost:1420`
- `tauri://localhost`(Tauri 2 Windows 桌面)
- `http://tauri.localhost`

### 1.5 请求头

无登录态/无鉴权(桌面本机工具)。`Content-Type: application/json`;WebSocket 走独立 token,见 §6。

### 1.6 密码字段约定

- **请求体**中的密码均为**明文**;服务端 AES 加密入库。
- **响应体**中的密码一律**脱敏**:
  - 跳板/控制台:返回占位串 `********`(未设置时为 `null`)
  - Redis 实例:返回布尔 `hasPassword`

---

## 2. SSH 隧道 `/api/tunnel`

### 2.1 跳板 CRUD

| 方法 | 路径 | 说明 | 请求体 |
|---|---|---|---|
| GET | `/api/tunnel/bastions` | 跳板列表(含映射明细) | — |
| POST | `/api/tunnel/bastions` | 新增跳板 | `SshBastionRequest` |
| PUT | `/api/tunnel/bastions/{id}` | 编辑跳板(密码留空=不改) | `SshBastionRequest` |
| DELETE | `/api/tunnel/bastions/{id}` | 删除(会先停隧道) | — |

**`SshBastionRequest`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | string | ✅ | 名称(唯一) |
| `sshHost` | string | ✅ | 跳板 SSH 主机 |
| `sshPort` | int | ✅ | SSH 端口 |
| `sshUser` | string | ✅ | SSH 用户 |
| `sshPassword` | string | 新增✅/编辑选 | 明文;编辑留空/null=不修改 |
| `remark` | string | - | 备注 |
| `mappings` | SshPortMappingRequest[] | - | 初始端口映射 |

**`SshPortMappingRequest`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `remoteHost` | string | ✅ | 目标内网主机 |
| `remotePort` | int(1–65535) | ✅ | 目标端口 |
| `preferredLocalPort` | int(1–65535) | - | 偏好本地端口,缺省自动分配 |
| `label` | string | - | 标签 |

### 2.2 隧道启停与状态

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/tunnel/bastions/{id}/start` | 启动隧道(后台重连+心跳) |
| POST | `/api/tunnel/bastions/{id}/stop` | 停止隧道 |
| GET | `/api/tunnel/status` | 全部运行状态 |
| GET | `/api/tunnel/bastions/{id}/status` | 单个状态 |

**`TunnelStatusDto`**:`{ bastionId, name, state, mappings: SshPortMappingDto[] }`

**`SshBastionDto`**(列表/详情项):`id, name, sshHost, sshPort, sshUser, sshPasswordMask, remark, running, mappingCount, mappings[], createTime, updateTime`

**`SshPortMappingDto`**:`id, bastionId, remoteHost, remotePort, preferredLocalPort, allocatedLocalPort(运行中填充), label, createTime, updateTime`

### 2.3 配置迁移(导出/导入)

| 方法 | 路径 | 请求/响应 |
|---|---|---|
| GET | `/api/tunnel/bastions/export` | → `BastionExportPayload` |
| POST | `/api/tunnel/bastions/import` | `BastionExportPayload` → `BastionImportResult` |

> 导出含解密后的真实密码,用于跨环境迁移;导入按 `name` 匹配查回 ID,同跳板名跳过。

**`BastionExportPayload`**:`{ version, exportTime, items: BastionExportItem[] }`
**`BastionExportItem`**:`{ name, sshHost, sshPort, sshUser, sshPassword, remark, mappings[] }`

---

## 3. 远程控制台 `/api/console`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/console/consoles` | 控制台配置列表 |
| GET | `/api/console/consoles/{id}` | 详情 |
| POST | `/api/console/consoles` | 新增 |
| PUT | `/api/console/consoles/{id}` | 编辑 |
| DELETE | `/api/console/consoles/{id}` | 删除 |
| GET | `/api/console/consoles/export` | 导出(含真实密码) |
| POST | `/api/console/consoles/import` | 导入 |
| POST | `/api/console/consoles/{id}/ws-token` | 申请 WebSocket 一次性握手 token,返回 `data=token` |

> 终端字节流走 WebSocket(`/ws/console/{id}`),**不**在本 REST 接口内,见 §6。

**`RemoteConsoleRequest`**:`name`(唯一),`bastionId`(可空,空=直连模式),`remoteHost`, `remotePort`(默认 22),`sshUser`,`sshPassword`(创建必填/编辑空=不改),`remark`,`quickCommands`(快捷命令 JSON 字符串)

**`RemoteConsoleDto`**(响应):`id, name, bastionId, remoteHost, remotePort, sshUser, sshPasswordMasked, remark, quickCommands, createTime, updateTime`

**`ConsoleExportItem`**:`{ name, bastionName, remoteHost, remotePort, sshUser, sshPassword, remark, quickCommands }`(bastionName 供导入按名称回查)

---

## 4. 数据源管理 `/api/datasource`

### 4.1 实例 CRUD 与连通性

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/datasource` | 数据源列表(不含密码) |
| GET | `/api/datasource/{id}` | 详情 |
| POST | `/api/datasource` | 新增 |
| PUT | `/api/datasource/{id}` | 编辑 |
| DELETE | `/api/datasource/{id}` | 删除 |
| POST | `/api/datasource/{id}/test` | 测试已保存实例连接 → `Boolean` |
| POST | `/api/datasource/test` | 测试未保存实例(表单直测)→ `Boolean` |

**`DataSourceRequest`**:`name`(唯一),`dbType`(如 `MYSQL`/`DM`),`host`,`port`,`databaseName`(可空=浏览整个服务器),`username`,`password`,`tunnelBastionId`(经 SSH 隧道连接时可空),`remark`

> `dbType` 需要对应 JDBC 驱动已装入后端;缺驱动时报 `3004`。

**`DataSourceDto`**(响应):`id, name, dbType, host, port, databaseName, username, tunnelBastionId, remark, createTime, updateTime`(无密码字段)

### 4.2 查询执行 `/api/datasource/{dsId}`

| 方法 | 路径 | 参数 | 说明 |
|---|---|---|---|
| GET | `.../schema` | — | 库/表/字段结构树 `SchemaNode[]` |
| GET | `.../preview` | `table`(必),`database`(选),`page`(默认0),`size`(默认50) | 分页预览表数据 |
| POST | `.../sql` | body `{ "sql": "...", "maxRows": 200 }` | 执行 SQL(多语句),返回多个结果集 |
| GET | `.../sql-history` | `page`(0),`size`(20) | 执行历史(分页) |
| GET | `.../sql-recent` | — | 最近 SQL(≤20 条,快捷复用) |

**`SchemaNode`**:`name, type(DATABASE/TABLE/VIEW/COLUMN), remark, children[], dataType, primaryKey`

**`TableDataResult`**:`columns[], columnComments[], rows[](Map), total, costMs`

**`SqlResultItem`**:`sql, columns[], columnComments[], rows[], affectedRows, costMs, status(SUCCESS/FAILED), errorMsg`

**`MultiSqlResult`**:`{ results: SqlResultItem[], totalCostMs }`

> 安全:采用**黑名单拦截**——放行 SELECT 与 DML/DDL,仅拦系统级危险操作(文件读写 `INTO OUTFILE`/`INTO DUMPFILE`/`LOAD_FILE`/`LOAD DATA`、盲注 `SLEEP`/`BENCHMARK`/`PG_SLEEP`/`WAITFOR DELAY`、动态执行 `EXEC`/`EXECUTE`/`PREPARE`/`DEALLOCATE`),命中返回 `3010`。关键字匹配对大小写、空白变形、注释拆分均不敏感;单条 SQL 上限 50000 字符,结果行数超过上限返回 `3012`(需加 LIMIT)。

---

## 5. Redis 运维 `/api/redis`

### 5.1 实例 CRUD 与连通性

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/redis` | 实例列表 |
| GET | `/api/redis/{id}` | 详情 |
| POST | `/api/redis` | 新增 |
| PUT | `/api/redis/{id}` | 编辑 |
| DELETE | `/api/redis/{id}` | 删除 |
| POST | `/api/redis/{id}/test` | 测试已保存实例 → `Boolean` |
| POST | `/api/redis/test` | 测试未保存实例 → `Boolean` |

**`RedisInstanceConfigRequest`**:`name`(≤64),`host`(≤128),`port`(默认 6379),`password`(可空;编辑传 null=不改),`dbIndex`(默认0),`timeoutMs`(100–30000,默认2000),`maxConnections`(1–100,默认8),`sshBastionId`, `remark`(≤255)

**`RedisInstanceConfigDto`**(响应):`id, name, host, port, hasPassword, dbIndex, timeoutMs, maxConnections, sshBastionId, remark, createTime, updateTime, reachable`

### 5.2 可视化操作 ops

| 方法 | 路径 | 参数 | 说明 |
|---|---|---|---|
| GET | `/api/redis/{id}/ops/info` | — | Redis INFO 概览 |
| GET | `/api/redis/{id}/ops/dbs` | — | 可用 db 列表 |
| GET | `/api/redis/{id}/ops/keys` | `db`(默认0),`cursor`(默认0),`pattern`(选),`count`(选) | SCAN 分页扫 key |
| GET | `/api/redis/{id}/ops/key` | `db`,`key` | 取 key 完整 value |
| POST | `/api/redis/{id}/ops/exec` | `db`,body=`commandLine` 纯文本 | 执行命令(白名单) |
| DELETE | `/api/redis/{id}/ops/key` | `db`,`key` | 删除 key |

> 危险/阻塞命令受白名单拦截,返回 `4010`。

---

## 6. WebSocket 远程终端

### 6.1 握手流程

1. `POST /api/console/consoles/{id}/ws-token` 获取**一次性** token;
2. `ws://127.0.0.1:38080/ws/console/{consoleId}?token=xxx` 建立连接;
3. 后端在握手阶段校验 token 与 `consoleId` 匹配且未过期,校验通过即销毁(TOFU);失败返回 `401/400`(错误码 `2003`)。

### 6.2 消息协议(UTF-8 文本帧)

| 方向 | 内容 | 说明 |
|---|---|---|
| 客户端→服务端 | 原始文本 | 普通按键/命令行输入,原样写入远程 shell |
| 客户端→服务端 | `{"type":"resize","cols":N,"rows":N}` | 终端窗口尺寸变更(以 `{` 开头才会尝试解析,不影响输入 `{` 开头内容) |
| 服务端→客户端 | 原始文本 | shell 输出(含 ANSI 转义序列)/错误提示(`[启动失败: ...]`/`[握手失败...]`) |

---

## 7. 附录

### 7.1 健康检查与优雅关闭

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/actuator/health` | `{"status":"UP"}`;外部来源只看 UP/DOWN,本机授权来源才显示详情 |
| GET | `/actuator/info` | 应用信息(敏感 key 自动脱敏) |
| POST | `/actuator/shutdown` | 优雅停机 |

### 7.2 本地调试提示

- 后端控制台日志(Desktop 拉起场景)落盘:`%TEMP%\devnest-backend.log`
- 应用自身启动流程日志:`%TEMP%\devnest-app.log`
- 手动启动后端(避免中文乱码,先切 UTF-8 代码页):

```powershell
chcp 65001
java -jar .\resources\devnest-boot.jar --spring.profiles.active=dev
```
