<script setup>
import { ref, onMounted, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { pipelineApi } from "../../api/pipeline";
import PipelineFormDialog from "./PipelineFormDialog.vue";
import PipelineImportDialog from "./PipelineImportDialog.vue";

const router = useRouter();
const list = ref([]);
const loading = ref(false);
const dialogVisible = ref(false);
const editingRow = ref(null);
const importVisible = ref(false);

const RUN_LABEL = {
  WAITING: "排队中",
  RUNNING: "运行中",
  SUCCESS: "成功",
  FAILED: "失败",
  STOPPED: "已停止",
};
const RUN_TAG = {
  WAITING: "warning",
  RUNNING: "warning",
  SUCCESS: "success",
  FAILED: "danger",
  STOPPED: "info",
};
const fmtTime = (t) => (t ? String(t).replace("T", " ").slice(0, 19) : "-");

const loadList = async () => {
  if (!list.value.length) loading.value = true;
  try {
    list.value = await pipelineApi.listPipelines();
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    loading.value = false;
  }
};

const onAdd = () => {
  editingRow.value = null;
  dialogVisible.value = true;
};
const onEdit = (row) => {
  editingRow.value = row;
  dialogVisible.value = true;
};
const onDialogSuccess = () => {
  dialogVisible.value = false;
  editingRow.value = null;
  loadList();
};
const onImportSuccess = () => {
  importVisible.value = false;
  loadList();
};

const sanitizeFileName = (n) =>
  String(n || "pipeline").replace(/[\\/:*?"<>|\s]+/g, "_").slice(0, 50);
const onExport = async (row) => {
  try {
    const data = await pipelineApi.exportPipeline(row.id);
    const blob = new Blob([JSON.stringify(data, null, 2)], {
      type: "application/json;charset=utf-8",
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `pipeline_${sanitizeFileName(data.name)}.json`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
    ElMessage.success("已导出,可在其他环境/实例导入");
  } catch (e) {
    ElMessage.error(e.message);
  }
};

const onOpenWorkspace = (row) => {
  router.push({ path: "/pipeline/runs", query: { pipelineId: row.id } });
};

const onDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确认删除流水线「${row.name}」?该流水线的运行历史也会一并删除。`,
      "提示",
      { type: "warning" }
    );
    await pipelineApi.deletePipeline(row.id);
    ElMessage.success("已删除");
    loadList();
  } catch (e) {
    if (e !== "cancel" && e?.toString() !== "cancel") {
      ElMessage.error(e.message || "删除失败");
    }
  }
};

let timer = null;
const startPoll = () => {
  if (timer) clearInterval(timer);
  timer = setInterval(loadList, 5000);
};
onMounted(() => {
  loadList();
  startPoll();
});
onUnmounted(() => {
  if (timer) clearInterval(timer);
});
</script>

<template>
  <div class="pipeline-list">
    <div class="toolbar">
      <h2>流水线</h2>
      <div class="actions">
        <el-button @click="router.push('/pipeline/runs')">运行工作台</el-button>
        <el-button @click="importVisible = true">导入</el-button>
        <el-button type="primary" @click="onAdd">+ 新增流水线</el-button>
      </div>
    </div>

    <el-table
      :data="list"
      v-loading="loading"
      border
      stripe
      empty-text="暂无流水线,点击右上角新增"
    >
      <el-table-column prop="name" label="名称" min-width="150">
        <template #default="{ row }">
          <span class="name-cell" :title="row.remark || row.name">{{ row.name }}</span>
          <el-tag v-if="row.running" type="warning" size="small" style="margin-left: 6px">运行中</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="stepCount" label="步骤数" width="90" align="center" />
      <el-table-column label="最近运行" width="200">
        <template #default="{ row }">
          <template v-if="row.lastStatus">
            <el-tag :type="RUN_TAG[row.lastStatus] || 'info'" size="small">
              {{ RUN_LABEL[row.lastStatus] || row.lastStatus }}
            </el-tag>
            <div v-if="row.lastEndTime" class="last-time">{{ fmtTime(row.lastEndTime) }}</div>
          </template>
          <span v-else class="empty-text">从未运行</span>
        </template>
      </el-table-column>
      <el-table-column prop="workdir" label="默认工作目录" min-width="180" show-overflow-tooltip />
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="onOpenWorkspace(row)">运行工作台</el-button>
          <el-button size="small" @click="onExport(row)">导出</el-button>
          <el-button size="small" @click="onEdit(row)">编辑</el-button>
          <el-button type="danger" size="small" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <PipelineFormDialog
      v-model="dialogVisible"
      :pipeline="editingRow"
      @success="onDialogSuccess"
    />
    <PipelineImportDialog v-model="importVisible" @success="onImportSuccess" />
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.toolbar h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}
.actions {
  display: flex;
  gap: 8px;
}
.name-cell {
  font-weight: 600;
}
.last-time {
  margin-top: 2px;
  font-size: 11px;
  color: #909399;
}
.empty-text {
  color: #c0c4cc;
}
</style>
