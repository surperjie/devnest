<script setup>
import { ref, onMounted, onUnmounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { tunnelApi } from "../../api/tunnel";
import BastionFormDialog from "./BastionFormDialog.vue";

const list = ref([]);
const loading = ref(false);
const dialogVisible = ref(false);
const editingRow = ref(null);
const importInput = ref(null);

const loadList = async () => {
  loading.value = true;
  try {
    list.value = await tunnelApi.listBastions();
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

const onStart = async (row) => {
  try {
    await tunnelApi.startTunnel(row.id);
    ElMessage.success("隧道已启动");
    loadList();
  } catch (e) {
    ElMessage.error(e.message);
  }
};
const onStop = async (row) => {
  try {
    await tunnelApi.stopTunnel(row.id);
    ElMessage.success("隧道已停止");
    loadList();
  } catch (e) {
    ElMessage.error(e.message);
  }
};
const onDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除跳板「${row.name}」?`, "提示", {
      type: "warning",
    });
    await tunnelApi.deleteBastion(row.id);
    ElMessage.success("已删除");
    loadList();
  } catch (e) {
    if (e !== "cancel" && e?.toString() !== "cancel") {
      ElMessage.error(e.message || "操作失败");
    }
  }
};

// 导出:调后端拿 payload,Blob 下载 JSON
const onExport = async () => {
  try {
    const payload = await tunnelApi.exportBastions();
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    const ts = new Date().toISOString().replace(/[:.]/g, "-").slice(0, 19);
    a.href = url;
    a.download = `devnest-tunnels-${ts}.json`;
    a.click();
    URL.revokeObjectURL(url);
    ElMessage.success(`已导出 ${payload.bastions?.length || 0} 个跳板配置`);
  } catch (e) {
    ElMessage.error(e.message || "导出失败");
  }
};

// 导入:选文件 → 读 JSON → 调后端 → 显示成功/跳过统计
const onImportClick = () => importInput.value?.click();
const onImportFile = async (e) => {
  const file = e.target.files?.[0];
  if (!file) return;
  try {
    const text = await file.text();
    const payload = JSON.parse(text);
    const result = await tunnelApi.importBastions(payload);
    ElMessage.success(`导入完成:成功 ${result.successCount},跳过 ${result.skipCount}`);
    loadList();
  } catch (err) {
    ElMessage.error(err.message || "导入失败:JSON 格式错误");
  } finally {
    e.target.value = "";
  }
};

// 5 秒轮询刷新(运行中状态会变)
let timer;
onMounted(() => {
  loadList();
  timer = setInterval(loadList, 5000);
});
onUnmounted(() => clearInterval(timer));
</script>

<template>
  <div class="bastion-list">
    <div class="toolbar">
      <h2>SSH 隧道</h2>
      <div class="actions">
        <el-button @click="onExport">导出配置</el-button>
        <el-button @click="onImportClick">导入配置</el-button>
        <el-button type="primary" @click="onAdd">+ 新增跳板</el-button>
        <input
          ref="importInput"
          type="file"
          accept=".json"
          style="display: none"
          @change="onImportFile"
        />
      </div>
    </div>

    <el-table
      :data="list"
      v-loading="loading"
      border
      stripe
      empty-text="暂无跳板,点击右上角新增"
    >
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="expand-content">
            <el-table :data="row.mappings || []" border size="small">
              <el-table-column label="远端地址" min-width="160">
                <template #default="{ row: m }">
                  {{ m.remoteHost }}:{{ m.remotePort }}
                </template>
              </el-table-column>
              <el-table-column label="本地端口" width="120" align="center">
                <template #default="{ row: m }">
                  {{ m.allocatedLocalPort ?? m.preferredLocalPort ?? "自动" }}
                </template>
              </el-table-column>
              <el-table-column prop="label" label="备注" min-width="120" show-overflow-tooltip />
            </el-table>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="120" />
      <el-table-column label="SSH 地址" min-width="180">
        <template #default="{ row }">
          {{ row.sshUser }}@{{ row.sshHost }}:{{ row.sshPort }}
        </template>
      </el-table-column>
      <el-table-column prop="mappingCount" label="映射数" width="80" align="center" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.running ? 'success' : 'info'" size="small">
            {{ row.running ? "运行中" : "未启动" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="!row.running"
            type="success"
            size="small"
            @click="onStart(row)"
          >启动</el-button>
          <el-button
            v-else
            type="warning"
            size="small"
            @click="onStop(row)"
          >停止</el-button>
          <el-button size="small" @click="onEdit(row)">编辑</el-button>
          <el-button type="danger" size="small" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <BastionFormDialog
      v-model="dialogVisible"
      :bastion="editingRow"
      @success="onDialogSuccess"
    />
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
.expand-content {
  padding: 12px 12px 12px 48px;
}
</style>
