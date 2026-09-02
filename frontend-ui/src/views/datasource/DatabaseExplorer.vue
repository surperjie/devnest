<script setup>
import { ref, onMounted, nextTick } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { datasourceApi } from "../../api/datasource";

const route = useRoute();
const router = useRouter();
const dsId = Number(route.params.id);

// 库表树
const treeData = ref([]);
const treeLoading = ref(false);
const treeProps = { label: "name", children: "children" };

const selectedDatabase = ref("");

// SQL 编辑器 Tab 页
const tabs = ref([]);
const activeTab = ref("0");
let tabSeq = 0;

// 每个 Tab 结构: { id, title, sql, results, executing }
const newTab = () => {
  const id = String(++tabSeq);
  tabs.value.push({
    id,
    title: `Query ${tabSeq}`,
    sql: "",
    results: [],
    executing: false,
  });
  activeTab.value = id;
};

const closeTab = (targetId) => {
  const idx = tabs.value.findIndex((t) => t.id === targetId);
  if (tabs.value.length === 1) return; // 至少保留一个
  tabs.value.splice(idx, 1);
  if (activeTab.value === targetId) {
    activeTab.value = tabs.value[Math.max(0, idx - 1)].id;
  }
};

const loadSchema = async () => {
  treeLoading.value = true;
  try {
    treeData.value = await datasourceApi.getSchema(dsId);
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
    // 插入到当前 Tab 的 SQL 末尾
    const tab = currentTab();
    if (tab) {
      tab.sql = tab.sql ? `${tab.sql}\nSELECT * FROM ${tableRef} LIMIT 50;` : `SELECT * FROM ${tableRef} LIMIT 50;`;
    }
  }
};

// 获取当前 Tab
const currentTab = () => tabs.value.find((t) => t.id === activeTab.value);

// 框选执行: 获取 textarea 选中文本,无选中则执行全部
const editorRefs = ref({});
const onExecute = async () => {
  const tab = currentTab();
  if (!tab) return;
  const ta = editorRefs.value[tab.id];
  let sql = tab.sql;
  if (ta && ta.selectionStart !== ta.selectionEnd) {
    sql = ta.value.substring(ta.selectionStart, ta.selectionEnd);
  }
  if (!sql.trim()) {
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
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    tab.executing = false;
  }
};

// Ctrl+Enter 执行
const onKeydown = (e) => {
  if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
    e.preventDefault();
    onExecute();
  }
};

// SQL 历史
const recentSqls = ref([]);
const loadRecent = async () => {
  try {
    recentSqls.value = await datasourceApi.getRecent(dsId);
  } catch {
    // ignore
  }
};

const onUseRecent = (sql) => {
  const tab = currentTab();
  if (tab) tab.sql = sql;
};

// AI-SQL 对话
const aiDialogVisible = ref(false);
const aiPrompt = ref("");
const aiLoading = ref(false);
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

onMounted(() => {
  newTab();
  loadSchema();
  loadRecent();
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
      <div class="left-panel">
        <div class="panel-title">库表结构</div>
        <el-tree
          :data="treeData"
          :props="treeProps"
          @node-click="onNodeClick"
          :expand-on-click-node="false"
          default-expand-all
          v-loading="treeLoading"
        >
          <template #default="{ data: node }">
            <span class="tree-node">
              <el-icon v-if="node.type === 'DATABASE'" style="color: #67c23a">
                <component :is="'Coin'" />
              </el-icon>
              <el-icon v-else-if="node.type === 'TABLE'" style="color: #409eff">
                <component :is="'Grid'" />
              </el-icon>
              <el-icon v-else-if="node.type === 'VIEW'" style="color: #e6a23c">
                <component :is="'View'" />
              </el-icon>
              <el-icon v-else-if="node.type === 'COLUMN'" style="color: #909399">
                <component :is="'Tickets'" />
              </el-icon>
              <span>{{ node.name }}</span>
              <span v-if="node.dataType" class="col-type">{{ node.dataType }}</span>
              <el-tag v-if="node.primaryKey" size="small" type="danger" effect="plain" style="margin-left:4px">PK</el-tag>
            </span>
          </template>
        </el-tree>
      </div>

      <!-- 右侧:多 Tab SQL 编辑器 + 结果 -->
      <div class="right-panel">
        <el-tabs v-model="activeTab" type="card" @tab-remove="closeTab">
          <el-tab-pane
            v-for="tab in tabs"
            :key="tab.id"
            :label="tab.title"
            :name="tab.id"
            :closable="tabs.length > 1"
          >
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
              <!-- SQL 编辑器 -->
              <textarea
                :ref="(el) => { if (el) editorRefs[tab.id] = el }"
                v-model="tab.sql"
                class="sql-editor"
                placeholder="输入 SQL,支持多条语句(分号分割);框选部分可单独执行"
                @keydown="onKeydown"
                spellcheck="false"
              ></textarea>
              <!-- 结果区 -->
              <div class="result-area">
                <div v-if="!tab.results.length" class="empty-hint">执行 SQL 后展示结果</div>
                <div v-for="(item, idx) in tab.results" :key="idx" class="result-item">
                  <div class="result-header">
                    <span class="sql-preview">{{ item.sql.substring(0, 80) }}{{ item.sql.length > 80 ? '...' : '' }}</span>
                    <el-tag v-if="item.status === 'SUCCESS'" type="success" size="small">成功</el-tag>
                    <el-tag v-else type="danger" size="small">失败</el-tag>
                    <span class="cost">{{ item.costMs }}ms</span>
                    <span v-if="item.affectedRows > 0" class="affected">{{ item.affectedRows }} 行受影响</span>
                    <span v-if="item.rows" class="row-count">{{ item.rows.length }} 行</span>
                  </div>
                  <div v-if="item.errorMsg" class="error-msg">{{ item.errorMsg }}</div>
                  <el-table
                    v-if="item.columns && item.columns.length"
                    :data="item.rows"
                    border
                    stripe
                    size="small"
                    max-height="300"
                  >
                    <el-table-column
                      v-for="col in item.columns"
                      :key="col"
                      :prop="col"
                      :label="col"
                      min-width="120"
                      show-overflow-tooltip
                    />
                  </el-table>
                </div>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
        <!-- 新建 Tab 按钮 -->
        <div class="add-tab" @click="newTab">+ 新建查询</div>
      </div>
    </div>

    <!-- AI-SQL 对话框 -->
    <el-dialog v-model="aiDialogVisible" title="AI-SQL 辅助" width="640px">
      <el-input
        v-model="aiPrompt"
        type="textarea"
        :rows="3"
        placeholder="用自然语言描述要查询的数据,如:查询最近7天注册的用户数量"
      />
      <el-button type="primary" :loading="aiLoading" @click="onAiGenerate" style="margin-top: 12px">生成 SQL</el-button>
      <el-input v-if="aiResult" v-model="aiResult" type="textarea" :rows="8" readonly style="margin-top: 12px" />
      <template #footer>
        <el-button @click="aiDialogVisible = false">关闭</el-button>
        <el-button v-if="aiResult" type="primary" @click="onApplyAiResult">填充到当前 Tab</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.explorer { display: flex; flex-direction: column; height: calc(100vh - 32px); }
.topbar { display: flex; align-items: center; gap: 12px; padding-bottom: 8px; }
.topbar .title { font-size: 16px; font-weight: 600; flex: 1; }
.body { display: flex; gap: 8px; flex: 1; min-height: 0; }
.left-panel { width: 260px; border: 1px solid #e4e7ed; border-radius: 4px; display: flex; flex-direction: column; background: #fff; }
.panel-title { padding: 8px 12px; font-weight: 600; border-bottom: 1px solid #e4e7ed; background: #f5f7fa; }
.left-panel .el-tree { flex: 1; overflow: auto; }
.tree-node { display: flex; align-items: center; gap: 4px; }
.col-type { color: #909399; font-size: 12px; margin-left: 4px; }
.right-panel { flex: 1; display: flex; flex-direction: column; min-width: 0; background: #fff; border: 1px solid #e4e7ed; border-radius: 4px; }
.right-panel .el-tabs { flex: 1; display: flex; flex-direction: column; min-height: 0; }
.right-panel :deep(.el-tabs__content) { flex: 1; min-height: 0; overflow: hidden; }
.tab-content { display: flex; flex-direction: column; height: 100%; }
.editor-toolbar { display: flex; align-items: center; gap: 8px; padding: 6px 8px; border-bottom: 1px solid #e4e7ed; background: #f5f7fa; }
.editor-toolbar .hint { color: #909399; font-size: 12px; }
.editor-toolbar .gap { flex: 1; }
.sql-editor { width: 100%; min-height: 100px; max-height: 200px; border: none; outline: none; resize: vertical; padding: 8px 12px; font-family: "JetBrains Mono", "Fira Code", "Consolas", monospace; font-size: 14px; line-height: 1.6; tab-size: 2; }
.result-area { flex: 1; overflow: auto; padding: 8px; }
.empty-hint { color: #909399; text-align: center; padding: 20px; }
.result-item { margin-bottom: 12px; border: 1px solid #ebeef5; border-radius: 4px; overflow: hidden; }
.result-header { display: flex; align-items: center; gap: 8px; padding: 4px 8px; background: #f5f7fa; border-bottom: 1px solid #ebeef5; font-size: 13px; }
.sql-preview { flex: 1; font-family: monospace; color: #606266; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.cost { color: #909399; }
.affected { color: #e6a23c; }
.row-count { color: #409eff; }
.error-msg { padding: 8px; color: #f56c6c; background: #fef0f0; font-size: 13px; }
.add-tab { padding: 6px 12px; cursor: pointer; color: #409eff; font-size: 13px; border-top: 1px solid #e4e7ed; }
.add-tab:hover { background: #f5f7fa; }
</style>
