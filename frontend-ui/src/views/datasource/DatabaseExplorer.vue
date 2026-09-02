<script setup>
import { ref, onMounted } from "vue";
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

// 当前选中表
const selectedTable = ref("");

// SQL 编辑器
const sqlText = ref("");
const executing = ref(false);

// 结果
const resultColumns = ref([]);
const resultRows = ref([]);
const resultTotal = ref(0);
const resultCost = ref(0);

// SQL 历史
const recentSqls = ref([]);

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

const loadRecent = async () => {
  try {
    recentSqls.value = await datasourceApi.getRecent(dsId);
  } catch {
    // ignore
  }
};

const onNodeClick = (node) => {
  if (node.type === "TABLE" || node.type === "VIEW") {
    selectedTable.value = node.name;
    sqlText.value = `SELECT * FROM \`${node.name}\` LIMIT 50;`;
  }
};

const onPreview = async (table) => {
  try {
    const res = await datasourceApi.preview(dsId, table, 0, 50);
    applyResult(res);
  } catch (e) {
    ElMessage.error(e.message);
  }
};

const onExecute = async () => {
  if (!sqlText.value.trim()) {
    ElMessage.warning("请输入 SQL");
    return;
  }
  executing.value = true;
  try {
    const res = await datasourceApi.executeSql(dsId, sqlText.value, 200);
    applyResult(res);
    ElMessage.success(`查询完成,${res.rows?.length || 0} 行,耗时 ${res.costMs}ms`);
    loadRecent();
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    executing.value = false;
  }
};

const applyResult = (res) => {
  resultColumns.value = res.columns || [];
  resultRows.value = res.rows || [];
  resultTotal.value = res.total || 0;
  resultCost.value = res.costMs || 0;
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

const onAiGenerate = async () => {
  if (!aiPrompt.value.trim()) {
    ElMessage.warning("请描述要查询的数据");
    return;
  }
  aiLoading.value = true;
  try {
    // AI-SQL 后端接口预留,当前返回降级提示
    // 第六期 AI 全局配置完成后接入实际调用
    ElMessage.info("AI 功能将在第六期统一配置后启用,当前请手动编写 SQL");
    aiResult.value = `-- AI 功能尚未启用
-- 您可以参考以下表结构手动编写 SQL:
-- 当前数据库的表结构已加载到左侧树视图
-- 您的需求: ${aiPrompt.value}`;
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    aiLoading.value = false;
  }
};

const onApplyAiResult = () => {
  if (aiResult.value) {
    sqlText.value = aiResult.value;
  }
  aiDialogVisible.value = false;
};

const onBack = () => router.push("/datasource");

// Ctrl+Enter 执行 SQL
const onKeydown = (e) => {
  if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
    e.preventDefault();
    onExecute();
  }
};

onMounted(() => {
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
              <el-icon v-if="node.type === 'TABLE'" style="color: #409eff">
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

      <!-- 右侧:SQL编辑器 + 结果 -->
      <div class="right-panel">
        <!-- SQL 编辑器区 -->
        <div class="editor-section">
          <div class="editor-toolbar">
            <span class="hint">Ctrl+Enter 执行</span>
            <div class="gap"></div>
            <el-select
              v-if="recentSqls.length"
              placeholder="最近 SQL"
              size="small"
              style="width: 300px"
              @change="(v) => sqlText = v"
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
              :loading="executing"
              @click="onExecute"
            >执行 SQL</el-button>
          </div>
          <textarea
            v-model="sqlText"
            class="sql-editor"
            placeholder="输入 SQL (仅支持 SELECT/SHOW/DESCRIBE/EXPLAIN)"
            @keydown="onKeydown"
            spellcheck="false"
          ></textarea>
        </div>

        <!-- 结果表格区 -->
        <div class="result-section">
          <div class="result-header">
            <span>结果 ({{ resultRows.length }} 行 / 共 {{ resultTotal }} 行)</span>
            <span v-if="resultCost" class="cost">耗时 {{ resultCost }}ms</span>
          </div>
          <el-table
            :data="resultRows"
            border
            stripe
            size="small"
            height="100%"
            empty-text="暂无数据,执行 SQL 后展示结果"
          >
            <el-table-column
              v-for="col in resultColumns"
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

    <!-- AI-SQL 对话框 -->
    <el-dialog v-model="aiDialogVisible" title="AI-SQL 辅助" width="640px">
      <el-input
        v-model="aiPrompt"
        type="textarea"
        :rows="3"
        placeholder="用自然语言描述要查询的数据,如:查询最近7天注册的用户数量"
      />
      <el-button
        type="primary"
        :loading="aiLoading"
        @click="onAiGenerate"
        style="margin-top: 12px"
      >生成 SQL</el-button>
      <el-input
        v-if="aiResult"
        v-model="aiResult"
        type="textarea"
        :rows="8"
        readonly
        style="margin-top: 12px"
      />
      <template #footer>
        <el-button @click="aiDialogVisible = false">关闭</el-button>
        <el-button v-if="aiResult" type="primary" @click="onApplyAiResult">填充到编辑器</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.explorer {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 32px);
}
.topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 8px;
}
.topbar .title {
  font-size: 16px;
  font-weight: 600;
  flex: 1;
}
.body {
  display: flex;
  gap: 8px;
  flex: 1;
  min-height: 0;
}
.left-panel {
  width: 260px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  background: #fff;
}
.panel-title {
  padding: 8px 12px;
  font-weight: 600;
  border-bottom: 1px solid #e4e7ed;
  background: #f5f7fa;
}
.left-panel .el-tree {
  flex: 1;
  overflow: auto;
}
.tree-node {
  display: flex;
  align-items: center;
  gap: 4px;
}
.col-type {
  color: #909399;
  font-size: 12px;
  margin-left: 4px;
}
.right-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}
.editor-section {
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  background: #fff;
}
.editor-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-bottom: 1px solid #e4e7ed;
  background: #f5f7fa;
}
.editor-toolbar .hint {
  color: #909399;
  font-size: 12px;
}
.editor-toolbar .gap {
  flex: 1;
}
.sql-editor {
  width: 100%;
  min-height: 120px;
  max-height: 240px;
  border: none;
  outline: none;
  resize: vertical;
  padding: 8px 12px;
  font-family: "JetBrains Mono", "Fira Code", "Consolas", monospace;
  font-size: 14px;
  line-height: 1.6;
  tab-size: 2;
}
.result-section {
  flex: 1;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  background: #fff;
  min-height: 0;
}
.result-header {
  display: flex;
  justify-content: space-between;
  padding: 6px 12px;
  border-bottom: 1px solid #e4e7ed;
  background: #f5f7fa;
  font-size: 13px;
}
.result-header .cost {
  color: #909399;
}
.result-section .el-table {
  flex: 1;
}
</style>
