# CI 与发布

流水线文件：[`.github/workflows/build-app.yml`](https://github.com/surperjie/devnest/blob/main/.github/workflows/build-app.yml)

---

## 1. 触发策略：日常提交只跑门禁，不产安装包

**安装包是发布产物，不是每次提交的副产物。** 一次 Windows 打包要 10~20 分钟，而绝大多数提交并不改变发行版内容。

| 触发 | 执行 | 产物 |
|---|---|---|
| push 到 `main` / `dev`（命中 `paths`） | `quality-gate` | **无安装包**（仅归档测试 / 覆盖率报告） |
| push `v*` 标签 | `quality-gate` + `build` | NSIS exe / MSI |
| 手动 *Run workflow*（勾选 `build_app`） | `quality-gate` + `build` | NSIS exe / MSI |

`paths` 过滤只对 `backend/**`、`frontend-ui/**`、`scripts/**`、`.github/workflows/build-app.yml` 生效。

> **注意**：GitHub 的 `paths` 过滤对 **tag 推送不生效**（官方语义：*Path filters are not evaluated for pushes of tags*），所以 `v*` 标签一定会触发流水线，不会被 `paths` 漏掉。

**门禁一刻没松**：日常 push 仍然每次都跑测试 / 覆盖率 / 架构 / 漏洞检查。省掉的只是"打包"这一步。

---

## 2. 两个 Job 分工

| Job | 运行环境 | 干什么 |
|---|---|---|
| `quality-gate` | ubuntu-latest | `mvn verify`（单测 + JaCoCo + enforcer + ArchUnit + Testcontainers）+ 增量覆盖率门禁 + OWASP 漏洞门禁；产出后端 jar 并归档报告 |
| `build` | windows-2022 | 依赖 `quality-gate`，下载它验证过的 jar，`jlink` 生成 `jre21` → 冒烟启动 → Tauri 打包成 exe / msi |

拆开的好处：

- **测试只在 Linux 跑一次**，不在 Windows 上重复（省约 10 分钟）；
- Testcontainers 需要 Linux Docker，`windows-2022` runner 上没有，放 `ubuntu` 才真跑得起来；
- `build` 用的是 `quality-gate` 交出**已经过门禁的那一份 jar**，全流程不存在 `-DskipTests`。

---

## 3. 怎么发一个版本

```powershell
# 1) 确认 main 是绿的、要发的提交都在
git checkout main
git pull

# 2) 打标签并推送（标签名以 v 开头才会触发 build job）
git tag -a v1.0.0 -m "DevNest v1.0.0"
git push origin v1.0.0
```

流水线跑完后，在该次 run 的 **Artifacts** 里取：

| 产物 | 内容 |
|---|---|
| `DevNest-Windows-Setup` | NSIS 安装包（`*_x64-setup.exe`），已内嵌 `jre21` |
| `DevNest-Windows-MSI` | MSI 安装包 |
| `quality-gate-reports` | surefire 报告、JaCoCo 报告、dependency-check 报告 |

也可以完全不动标签，手动触发：

> 仓库页面 → **Actions** → *Build Desktop App* → **Run workflow** → 勾选 `build_app` → Run

---

## 4. OWASP 漏洞门禁需要 `NVD_API_KEY`

漏洞门禁的运行前提是一个仓库 secret：

> **Settings → Secrets and variables → Actions → New repository secret** → 名称 `NVD_API_KEY`
> 免费申请：<https://nvd.nist.gov/developers/request-an-api-key>

**为什么必须要 Key**：NVD 的 CVE 2.0 接口对无凭据访问返回 403，且 dependency-check 插件 9.x 起已没有免 Key 的数据源。

**没配会怎样**：不"假装通过"，也不直接判红，而是显式标注「本次未执行」并写进 job summary —— 界面上看得见"这条门禁这次没跑"。**配好 secret 后无需改任何代码，门禁自动生效并真的会拦人。**

---

## 5. 常见疑问

**Q：只想让 main 上的推送也产出安装包，怎么办？**
把 `build` job 的 `if` 加上 `github.ref == 'refs/heads/main'` 即可；不建议再退回"每次提交都打包"。

**Q：改 workflow 文件本身会不会触发流水线？**
会 —— `.github/workflows/build-app.yml` 在 `paths` 里；但只跑门禁，不打安装包。

**Q：为什么 README 的构建徽章有时"跳过"？**
徽章取默认分支上最近一次运行。若那次运行被 `paths` 过滤掉，检查会停留在 Pending，PR 上看起来像卡住。改动 `paths` 覆盖范围时要留意这点。

**Q：CI 没被触发？**
按顺序检查：分支是不是 `main` / `dev`；改动是否命中 `paths`；是不是只改了 `docs/`、`README.md` 这类未纳入 `paths` 的路径（此时不触发是预期行为）。
