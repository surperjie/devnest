<script setup>
import { ref, onMounted, onUnmounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { tunnelApi } from "../../api/tunnel";
import BastionFormDialog from "./BastionFormDialog.vue";

const list = ref([]);
const loading = ref(false);
const dialogVisible = ref(false);
const editingRow = ref(null);

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
      <el-button type="primary" @click="onAdd">+ 新增跳板</el-button>
    </div>

    <el-table :data="list" v-loading="loading" border stripe empty-text="暂无跳板,点击右上角新增">
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
</style>
