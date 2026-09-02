<script setup>
import { ref, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { consoleApi } from "../../api/console";
import { tunnelApi } from "../../api/tunnel";
import ConsoleFormDialog from "./ConsoleFormDialog.vue";
import ConsoleTerminalDialog from "./ConsoleTerminalDialog.vue";

const list = ref([]);
const loading = ref(false);
const formVisible = ref(false);
const editingRow = ref(null);
const terminalVisible = ref(false);
const terminalRow = ref(null);
const bastionMap = ref({});
const importInput = ref(null);

const loadList = async () => {
  loading.value = true;
  try {
    list.value = await consoleApi.listConsoles();
    // 拿跳板列表用于显示隧道模式对应的跳板名
    const bastions = await tunnelApi.listBastions();
    bastionMap.value = Object.fromEntries(bastions.map((b) => [b.id, b.name]));
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    loading.value = false;
  }
};

const onAdd = () => {
  editingRow.value = null;
  formVisible.value = true;
};
const onEdit = (row) => {
  editingRow.value = row;
  formVisible.value = true;
};
const onOpenTerminal = (row) => {
  terminalRow.value = row;
  terminalVisible.value = true;
};
const onFormSuccess = () => {
  formVisible.value = false;
  editingRow.value = null;
  loadList();
};
const onTerminalClose = () => {
  terminalVisible.value = false;
  terminalRow.value = null;
};

const onDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除控制台「${row.name}」?`, "提示", {
      type: "warning",
    });
    await consoleApi.deleteConsole(row.id);
    ElMessage.success("已删除");
    loadList();
  } catch (e) {
    if (e !== "cancel") ElMessage.error(e.message || "删除失败");
  }
};

const onExport = async () => {
  try {
    const payload = await consoleApi.exportConsoles();
    const blob = new Blob([JSON.stringify(payload, null, 2)], {
      type: "application/json",
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    const ts = new Date().toISOString().replace(/[:.]/g, "-").slice(0, 19);
    a.href = url;
    a.download = `devnest-consoles-${ts}.json`;
    a.click();
    URL.revokeObjectURL(url);
    ElMessage.success(
      `已导出 ${payload.consoles?.length || 0} 个控制台配置`
    );
  } catch (e) {
    ElMessage.error(e.message || "导出失败");
  }
};

const onImportClick = () => importInput.value?.click();
const onImportFile = async (e) => {
  const file = e.target.files?.[0];
  if (!file) return;
  try {
    const text = await file.text();
    const payload = JSON.parse(text);
    const result = await consoleApi.importConsoles(payload);
    ElMessage.success(
      `导入完成:成功 ${result.successCount},跳过 ${result.skipCount}`
    );
    loadList();
  } catch (err) {
    ElMessage.error(err.message || "导入失败:JSON 格式错误");
  } finally {
    e.target.value = "";
  }
};

onMounted(loadList);
</script>

<template>
  <div class="console-list">
    <div class="toolbar">
      <h2>远程控制台</h2>
      <div class="toolbar-right">
        <el-button @click="onImportClick">导入</el-button>
        <el-button type="success" @click="onExport">导出</el-button>
        <el-button type="primary" @click="onAdd">+ 新增控制台</el-button>
      </div>
    </div>
    <input
      ref="importInput"
      type="file"
      accept="application/json"
      style="display: none"
      @change="onImportFile"
    />

    <el-table
      :data="list"
      v-loading="loading"
      border
      stripe
      empty-text="暂无控制台,点击右上角新增"
    >
      <el-table-column prop="name" label="名称" min-width="120" />
      <el-table-column label="连接模式" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.bastionId ? 'warning' : 'success'" size="small">
            {{ row.bastionId ? "隧道" : "直连" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="目标" min-width="200">
        <template #default="{ row }">
          <span v-if="row.bastionId" class="bastion-tag">
            [{{ bastionMap[row.bastionId] || "未知跳板" }}]
          </span>
          {{ row.remoteHost }}:{{ row.remotePort }}
        </template>
      </el-table-column>
      <el-table-column prop="sshUser" label="用户" width="100" />
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="onOpenTerminal(row)">打开终端</el-button>
          <el-button size="small" @click="onEdit(row)">编辑</el-button>
          <el-button type="danger" size="small" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <ConsoleFormDialog
      v-model="formVisible"
      :console="editingRow"
      @success="onFormSuccess"
    />
    <ConsoleTerminalDialog
      v-if="terminalVisible"
      :row="terminalRow"
      @close="onTerminalClose"
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
.toolbar-right {
  display: flex;
  gap: 8px;
}
.bastion-tag {
  color: #e6a23c;
  font-weight: 600;
}
</style>
