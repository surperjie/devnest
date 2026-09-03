<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick, computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { Plus } from "@element-plus/icons-vue";
import { datasourceApi } from "../../api/datasource";

const route = useRoute();
const router = useRouter();
const dsId = Number(route.params.id);

// localStorage 持久化 key
const STORAGE_KEY = `devnest:ds:${dsId}:sql-tabs`;
const LAYOUT_KEY = `devnest:ds:${dsId}:layout`;

// 库表树
const treeData = ref([]);
const treeLoading = ref(false);
const treeProps = { label: "name", children: "children" };
const treeRef = ref();
const expandedKeys = ref([]);

// 给 SQL 编辑器补全用的扁平结构(库/表/列)
const schemaCompletions = computed(() => {
  const out = [];
  for (const db of treeData.value || []) {
    const dbItem = { name: db.name, tables: [] };
    for (const t of db.children || []) {
      if (t.type === "TABLE" || t.type === "VIEW") {
        dbItem.tables.push({
          name: t.name,
          columns: (t.children || []).filter((c) => c.type === "COLUMN")
            .map((c) => ({ name: c.name, remark: c.remark || "" })),
        });
      }
    }
    out.push(dbItem);
  }
  return out;
});

const selectedDatabase = ref("");

// 左侧面板宽度(可拖拽调整)
const leftPanelWidth = ref(280);
const MIN_LEFT = 180;
const MAX_LEFT = 700;

const loadLayout = () => {
  try {
    const raw = localStorage.getItem(LAYOUT_KEY);
    if (raw) {
      const w = parseInt(raw);
      if (!isNaN(w) && w >= MIN_LEFT && w <= MAX_LEFT) leftPanelWidth.value = w;
    }
  } catch { /* ignore */ }
};
const saveLayout = () => {
  try { localStorage.setItem(LAYOUT_KEY, String(leftPanelWidth.value)); } catch { /* ignore */ }
};

// 拖拽调整宽度
const resizeDragging = ref(false);
let dragStartX = 0;
let dragStartWidth = 0;
const onResizeMouseDown = (e) => {
  resizeDragging.value = true;
  dragStartX = e.clientX;
  dragStartWidth = leftPanelWidth.value;
  document.addEventListener("mousemove", onResizeMouseMove);
  document.addEventListener("mouseup", onResizeMouseUp);
  document.body.style.cursor = "col-resize";
  document.body.style.userSelect = "none";
};
const onResizeMouseMove = (e) => {
  if (!resizeDragging.value) return;
  const delta = e.clientX - dragStartX;
  let w = dragStartWidth + delta;
  if (w < MIN_LEFT) w = MIN_LEFT;
  if (w > MAX_LEFT) w = MAX_LEFT;
  leftPanelWidth.value = w;
};
const onResizeMouseUp = () => {
  resizeDragging.value = false;
  document.removeEventListener("mousemove", onResizeMouseMove);
  document.removeEventListener("mouseup", onResizeMouseUp);
  document.body.style.cursor = "";
  document.body.style.userSelect = "";
  saveLayout();
};

// SQL 编辑器 Tab 页
const tabs = ref([]);
const activeTab = ref("0");
let tabSeq = 0;

// 加号按钮位置(紧贴最后一个 tab 右侧)
const addBtnLeft = ref("0px");
const tabsBarWrapRef = ref();
const updateAddBtnPosition = async () => {
  await nextTick();
  const wrap = tabsBarWrapRef.value;
  if (!wrap) return;
  const nav = wrap.querySelector(".el-tabs__nav");
  if (!nav) return;
  const navRect = nav.getBoundingClientRect();
  const wrapRect = wrap.getBoundingClientRect();
  addBtnLeft.value = (navRect.right - wrapRect.left) + "px";
};
const onResize = () => updateAddBtnPosition();

watch(() => tabs.value.map((t) => t.id + t.title), () => updateAddBtnPosition());
watch(activeTab, () => updateAddBtnPosition());

onBeforeUnmount(() => {
  document.removeEventListener("mousemove", onResizeMouseMove);
  document.removeEventListener("mouseup", onResizeMouseUp);
  window.removeEventListener("resize", onResize);
});

const newTab = (sql = "") => {
  const id = String(++tabSeq);
  tabs.value.push({
    id,
    title: `Query ${tabSeq}`,
    sql,
    results: [],
    executing: false,
  });
  activeTab.value = id;
  persistTabs();
};

const closeTab = (targetId) => {
  const idx = tabs.value.findIndex((t) => t.id === targetId);
  if (tabs.value.length === 1) {
    tabs.value[0].sql = "";
    tabs.value[0].results = [];
    persistTabs();
    return;
  }
  tabs.value.splice(idx, 1);
  if (activeTab.value === targetId) {
    activeTab.value = tabs.value[Math.max(0, idx - 1)].id;
  }
  persistTabs();
};

// localStorage 持久化
const persistTabs = () => {
  try {
    const data = tabs.value.map((t) => ({ title: t.title, sql: t.sql }));
    localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
  } catch { /* ignore */ }
};

const restoreTabs = () => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const saved = JSON.parse(raw);
      if (Array.isArray(saved) && saved.length) {
        saved.forEach((t) => {
          const id = String(++tabSeq);
          tabs.value.push({
            id,
            title: t.title || `Query ${tabSeq}`,
            sql: t.sql || "",
            results: [],
            executing: false,
          });
        });
        activeTab.value = tabs.value[0].id;
        return;
      }
    }
  } catch { /* ignore */ }
  newTab();
};

// sql 变化 debounce 保存
let saveTimer = null;
watch(
  () => tabs.value.map((t) => t.sql),
  () => {
    if (saveTimer) clearTimeout(saveTimer);
    saveTimer = setTimeout(persistTabs, 500);
  },
  { deep: true },
);

const loadSchema = async () => {
  treeLoading.value = true;
  try {
    treeData.value = await datasourceApi.getSchema(dsId);
    expandedKeys.value = treeData.value.map((n) => n.name);
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    treeLoading.value = false;
  }
};

const onNodeClick = (data, node) => {
  if (data.type === "DATABASE") {
    selectedDatabase.value = data.name;
  } else if (data.type === "TABLE" || data.type === "VIEW") {
    const dbName = node.parent?.data?.name || "";
    selectedDatabase.value = dbName;
    const tableRef = dbName
      ? `\`${dbName}\`.\`${data.name}\``
      : `\`${data.name}\``;
    const tab = currentTab();
    if (tab) {
      tab.sql = tab.sql
        ? `${tab.sql}\nSELECT * FROM ${tableRef} LIMIT 50;`
        : `SELECT * FROM ${tableRef} LIMIT 50;`;
    }
  }
};

const currentTab = () => tabs.value.find((t) => t.id === activeTab.value);

// 编辑器执行(Ctrl+Enter / F5):含框选
const onEditorExecute = (sql, isSelected) => doExecute(sql, isSelected);

// 点击「执行」按钮
const onExecute = () => doExecute(null, false);

// 公共执行
const doExecute = async (rawSql, isSelected) => {
  const tab = currentTab();
  if (!tab) return;
  const sql = (typeof rawSql === "string" && rawSql.trim()) ? rawSql : tab.sql;
  if (!sql || !sql.trim()) {
    ElMessage.warning("请输入 SQL");
    return;
  }
  tab.executing = true;
  try {
    const res = await datasourceApi.executeSql(dsId, sql, 200);
    tab.results = res.results || [];
    const ok = tab.results.filter((r) => r.status === "SUCCESS").length;
    const fail = tab.results.filter((r) => r.status === "FAILED").length;
    ElMessage.success(`${ok} 条成功${fail ? ", " + fail + " 条失败" : ""}, 耗时 ${res.totalCostMs}ms`);
    loadRecent();
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    tab.executing = false;
  }
};

// SQL 历史
const recentSqls = ref([]);
const loadRecent = async () => {
  try {
    recentSqls.value = await datasourceApi.getRecent(dsId);
  } catch { /* ignore */ }
};

const onUseRecent = (sql) => {
  const tab = currentTab();
  if (tab) tab.sql = sql;
};

// AI-SQL 对话框
const aiDialogVisible = ref(false);
const aiPrompt = ref("");
const aiResult = ref("");
const onAiOpen = () => {
  aiPrompt.value = "";
  aiResult.value = "";
  aiDialogVisible.value = true;
};
const onAiGenerate = () => {
  if (!aiPrompt.value.trim()) {
    ElMessage.warning("请描述要查询的数据");
    return;
  }
  ElMessage.info("AI 功能将在第六期统一配置后启用,当前请手动编写 SQL");
  aiResult.value = `-- AI 功能尚未启用\n-- 您的需求: ${aiPrompt.value}`;
};
const onApplyAiResult = () => {
  if (aiResult.value) {
    const tab = currentTab();
    if (tab) tab.sql = aiResult.value;
  }
  aiDialogVisible.value = false;
};

const onBack = () => router.push("/datasource");

onMounted(async () => {
  loadLayout();
  restoreTabs();
  loadSchema();
  loadRecent();
  window.addEventListener("resize", onResize);
  await nextTick();
  setTimeout(updateAddBtnPosition, 80);
});
</script>

<template>
  <div class="explorer">
    <!-- 顶部工具栏 -->
    <div class="topbar">
      <el-button size="small" @click="onBack">← 返回</el-button>
      <span class="title">数据库浏览器</span>
      <el-button size="small" @click="loadSchema" :loading="treeLoading">刷新结构</el-button>
    </div>

    <div class="body">
      <!-- 左侧:库表树 -->
      <div class="left-panel" :style="{ width: leftPanelWidth + 'px' }">
        <div class="panel-title">库表结构</div>
        <el-tree
          ref="treeRef"
          :data="treeData"
          :props="treeProps"
          node-key="name"
          @node-click="onNodeClick"
          :expand-on-click-node="false"
          :default-expanded-keys="expandedKeys"
          v-loading="treeLoading"
        >
          <template #default="{ data: node }">
            <span class="tree-node" :class="{ 'is-column': node.type === 'COLUMN' }">
              <el-icon v-if="node.type === 'DATABASE'" style="color: #67c23a">
                <component :is="FolderOpenedIcon" />
              </el-icon>
              <el-icon v-else-if="node.type === 'TABLE'" style="color: #409eff">
                <component :is="FilesIcon" />
              </el-icon>
              <el-icon v-else-if="node.type === 'VIEW'" style="color: #e6a23c">
                <component :is="FilesIcon" />
              </el-icon>
              <el-icon v-else style="color: #909399" />
              <span class="node-name">{{ node.name }}</span>
              <span v-if="node.type === 'COLUMN' && node.colType" class="col-type">{{ node.colType }}</span>
              <span v-if="node.type === 'COLUMN' && node.remark" class="col-remark">-- {{ node.remark }}</span>
              <el-tag v-if="node.isPk" type="danger" size="small" style="margin-left:4px">PK</el-tag>
            </span>
          </template>
        </el-tree>
      </div>

      <div
        class="resizer"
        :class="{ dragging: resizeDragging }"
        @mousedown="onResizeMouseDown"
        title="拖拽调整宽度"
      ></div>

      <!-- 右侧:多 Tab SQL 编辑器 + 结果 -->
      <div class="right-panel">
        <div class="tabs-bar-wrap" ref="tabsBarWrapRef">
          <el-tabs v-model="activeTab" type="card" @tab-remove="closeTab" @tab-change="persistTabs" class="tabs-no-content">
            <el-tab-pane
              v-for="tab in tabs"
              :key="'head_' + tab.id"
              :label="tab.title"
              :name="tab.id"
              :closable="true"
            />
          </el-tabs>
          <div class="tab-add-inner" :style="{ left: addBtnLeft }" @click="newTab()" title="新建查询">
            <el-icon><Plus /></el-icon>
          </div>
        </div>

        <!-- Tab 内容区 -->
        <div class="tab-content-area">
          <div v-for="tab in tabs" v-show="activeTab === tab.id" :key="'body_' + tab.id">
            <div class="tab-content">
              <!-- 工具栏 -->
              <div class="editor-toolbar">
                <span class="hint">Ctrl+Enter 执行 (框选部分执行选中)</span>
                <div class="gap"></div>
                <el-select
                  v-if="recentSqls.length"
                  placeholder="最近 SQL"
                  size="small"
                  style="width: 280px"
                  @change="onUseRecent"
                >
                  <el-option
                    v-for="s in recentSqls"
                    :key="s.id"
                    :label="s.sqlText.substring(0, 60)"
                    :value="s.sqlText"
                  />
                </el-select>
                <el-button size="small" @click="onAiOpen">AI 辅助</el-button>
                <el-button
                  type="primary"
                  size="small"
                  :loading="tab.executing"
                  @click="onExecute"
                >执行 SQL</el-button>
              </div>
              <!-- SQL 编辑器:原生 textarea(保证 100% 可用) + 轻量 SQL 自动补全 -->
              <div class="sql-edit-box">
                <SqlCompleteTextarea
                  v-model="tab.sql"
                  :schema-tree="schemaCompletions"
                  :disabled="tab.executing"
                  placeholder="输入 SQL,支持多条语句分号分割  Ctrl+Enter 或 F5 执行  框选部分可单独执行"
                  @execute="onEditorExecute"
                  @blur="persistTabs"
                />
              </div>
              <!-- 结果区 -->
              <div class="result-area">
                <div v-if="!tab.results || !tab.results.length" class="empty-hint">执行 SQL 后展示结果</div>
                <div v-for="(item, idx) in tab.results" :key="idx" class="result-item">
                  <div class="result-header">
                    <span class="sql-preview">{{ (item.sql || "").substring(0, 80) }}{{ (item.sql || "").length > 80 ? '...' : '' }}</span>
                    <el-tag v-if="item.status === 'SUCCESS'" type="success" size="small">成功</el-tag>
                    <el-tag v-else type="danger" size="small">失败</el-tag>
                    <span class="cost">{{ item.costMs }}ms</span>
                    <span v-if="item.affectedRows > 0" class="affected">{{ item.affectedRows }} 行受影响</span>
                    <span v-if="item.rows" class="row-count">{{ item.rows.length }} 行</span>
                  </div>
                  <div v-if="item.errorMsg" class="error-msg">{{ item.errorMsg }}</div>
                  <el-table
                    v-if="item.columns && item.columns.length && item.rows"
                    :data="item.rows"
                    border
                    stripe
                    size="small"
                    max-height="320"
                    style="width: 100%"
                  >
                    <el-table-column
                      v-for="(col, ci) in item.columns"
                      :key="col + '__' + ci"
                      :prop="col"
                      min-width="120"
                      show-overflow-tooltip
                    >
                      <template #header>
                        <span class="col-header-wrap">
                          <strong>{{ col }}</strong>
                          <el-tooltip
                            v-if="item.columnComments && item.columnComments[ci]"
                            :content="item.columnComments[ci]"
                            placement="top"
                          >
                            <span class="col-comment-icon" :title="item.columnComments[ci]">💬</span>
                          </el-tooltip>
                        </span>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- AI-SQL 对话框 -->
    <el-dialog v-model="aiDialogVisible" title="AI 辅助(即将接入)" width="520px">
      <el-input
        v-model="aiPrompt"
        type="textarea"
        :rows="4"
        placeholder="用自然语言描述您要查询的数据,例如:查出所有在 2024 年创建的用户并按创建时间倒序"
      />
      <div style="margin-top:12px">
        <el-button type="primary" @click="onAiGenerate">生成 SQL</el-button>
      </div>
      <el-input
        v-if="aiResult"
        v-model="aiResult"
        type="textarea"
        :rows="8"
        readonly
        style="margin-top: 12px"
      />
      <template #footer>
        <el-button @click="aiDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!aiResult" @click="onApplyAiResult">应用到当前编辑器</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
// 局部图标组件(避免 import 后模板不可用的问题)
import { FolderOpened, Files } from "@element-plus/icons-vue";
import SqlCompleteTextarea from "@/components/SqlCompleteTextarea.vue";
export default {
  components: { SqlCompleteTextarea },
  data() { return {}; },
  computed: {
    FolderOpenedIcon() { return FolderOpened; },
    FilesIcon() { return Files; },
  },
};
</script>

<style scoped>
.explorer { display: flex; flex-direction: column; height: 100%; }
.topbar { display: flex; align-items: center; gap: 12px; padding: 8px 16px; border-bottom: 1px solid #e4e7ed; background: #fff; flex-shrink: 0; }
.topbar .title { flex: 1; font-weight: 600; font-size: 15px; }
.body { flex: 1; min-height: 0; display: flex; overflow: hidden; }

/* 左:库表树 */
.left-panel {
  flex-shrink: 0;
  border-right: 1px solid #e4e7ed;
  background: #fff;
  overflow: auto;
  display: flex; flex-direction: column;
}
.panel-title {
  padding: 8px 12px;
  font-weight: 600;
  background: #f5f7fa;
  border-bottom: 1px solid #ebeef5;
  font-size: 13px;
  flex-shrink: 0;
}
.tree-node { display: inline-flex; align-items: center; gap: 4px; white-space: nowrap; }
.tree-node.is-column { padding-left: 4px; }
.node-name { flex-shrink: 0; }
.col-type { color: #909399; font-size: 12px; margin-left: 4px; flex-shrink: 0; }
.col-remark { color: #909399; font-size: 12px; margin-left: 8px; font-style: italic; }

/* 拖拽分隔条 */
.resizer { width: 6px; flex-shrink: 0; cursor: col-resize; background: transparent; transition: background .15s; }
.resizer:hover, .resizer.dragging { background: #409eff; width: 6px; }

/* 右:编辑器 */
.right-panel { flex: 1; min-width: 0; display: flex; flex-direction: column; background: #fff; }

/* Tab header 行 */
.tabs-bar-wrap { flex-shrink: 0; position: relative; border-bottom: 1px solid #e4e7ed; }
.tabs-bar-wrap :deep(.el-tabs) { margin: 0; }
.tabs-bar-wrap :deep(.el-tabs__header) { margin: 0; }
.tabs-bar-wrap :deep(.el-tabs__nav-wrap::after) { display: none; }
.tabs-bar-wrap :deep(.el-tabs__content) { display: none; }
.tabs-no-content :deep(.el-tabs__content) { display: none; }

/* 加号按钮 */
.tab-add-inner {
  position: absolute;
  bottom: 0;
  left: 0;
  z-index: 20;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  cursor: pointer;
  color: #409eff;
  padding: 4px;
  box-sizing: border-box;
}
.tab-add-inner:hover { background: #f5f7fa; }

/* Tab 内容 */
.tab-content-area { flex: 1; min-height: 0; overflow: hidden; }
.tab-content { display: flex; flex-direction: column; height: 100%; }
.editor-toolbar { display: flex; align-items: center; gap: 8px; padding: 6px 8px; border-bottom: 1px solid #e4e7ed; background: #f5f7fa; flex-shrink: 0; }
.editor-toolbar .hint { color: #909399; font-size: 12px; }
.editor-toolbar .gap { flex: 1; }

/* 编辑器容器 */
.sql-edit-box { width: 100%; height: 220px; box-sizing: border-box; flex-shrink: 0; }

/* 结果区 */
.result-area { flex: 1; overflow: auto; padding: 8px; }
.empty-hint { color: #909399; text-align: center; padding: 20px; }
.result-item { margin-bottom: 12px; border: 1px solid #ebeef5; border-radius: 4px; overflow: hidden; }
.result-header { display: flex; align-items: center; gap: 8px; padding: 4px 8px; background: #f5f7fa; border-bottom: 1px solid #ebeef5; font-size: 13px; }
.sql-preview { flex: 1; font-family: monospace; color: #606266; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.cost { color: #909399; }
.affected { color: #e6a23c; }
.row-count { color: #67c23a; }
.error-msg { padding: 8px 12px; color: #f56c6c; background: #fef0f0; font-size: 13px; font-family: monospace; white-space: pre-wrap; }

/* 结果列表头 */
.col-header-wrap {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
}
.col-comment-icon {
  font-size: 12px;
  color: #909399;
  cursor: help;
  padding: 0 2px;
}
</style>
