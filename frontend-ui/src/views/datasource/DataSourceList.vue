<script setup>
import { ref, onMounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { datasourceApi } from "../../api/datasource";
import { tunnelApi } from "../../api/tunnel";

const router = useRouter();
const list = ref([]);
const loading = ref(false);
const dialogVisible = ref(false);
const editingRow = ref(null);
const bastionList = ref([]);

const loadList = async () => {
  loading.value = true;
  try {
    list.value = await datasourceApi.list();
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    loading.value = false;
  }
};

const loadBastions = async () => {
  try {
    bastionList.value = await tunnelApi.listBastions();
  } catch {
    // ignore
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
  loadList();
};

const onExplore = (row) => {
  router.push(`/datasource/${row.id}/explorer`);
};

const onTest = async (row) => {
  try {
    const ok = await datasourceApi.testConnection(row.id);
    if (ok) ElMessage.success("连接成功");
    else ElMessage.warning("连接失败");
  } catch (e) {
    ElMessage.error(e.message);
  }
};

const onDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除数据源「${row.name}」?`, "提示", {
      type: "warning",
    });
    await datasourceApi.delete(row.id);
    ElMessage.success("已删除");
    loadList();
  } catch (e) {
    if (e !== "cancel" && e?.toString() !== "cancel") {
      ElMessage.error(e.message || "操作失败");
    }
  }
};

onMounted(() => {
  loadList();
  loadBastions();
});
</script>

<template>
  <div class="ds-list">
    <div class="toolbar">
      <h2>数据源管理</h2>
      <el-button type="primary" @click="onAdd">+ 新增数据源</el-button>
    </div>

    <el-table :data="list" v-loading="loading" border stripe
      empty-text="暂无数据源,点击右上角新增">
      <el-table-column prop="name" label="名称" min-width="120" />
      <el-table-column prop="dbType" label="类型" width="80" align="center">
        <template #default="{ row }">
          <el-tag size="small">{{ row.dbType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="连接地址" min-width="200">
        <template #default="{ row }">
          {{ row.host }}:{{ row.port }}/{{ row.databaseName }}
        </template>
      </el-table-column>
      <el-table-column prop="username" label="用户" width="100" />
      <el-table-column label="隧道" width="100" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.tunnelBastionId" type="warning" size="small">
            {{ bastionList.find(b => b.id === row.tunnelBastionId)?.name || '隧道#' + row.tunnelBastionId }}
          </el-tag>
          <span v-else style="color: #909399">直连</span>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="onExplore(row)">浏览</el-button>
          <el-button type="success" size="small" @click="onTest(row)">测试</el-button>
          <el-button size="small" @click="onEdit(row)">编辑</el-button>
          <el-button type="danger" size="small" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <DataSourceFormDialog
      v-model="dialogVisible"
      :ds="editingRow"
      :bastions="bastionList"
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
