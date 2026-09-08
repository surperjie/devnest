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

// 运行状态展示文案与标签色
const STATE_LABEL = {
  IDLE: "未启动",
  CONNECTING: "连接中",
  RECONNECTING: "重连中",
  RUNNING: "运行中",
  ERROR: "连接失败",
  CLOSED: "已停止",
};
const STATE_TAG = {
  IDLE: "info",
  CONNECTING: "warning",
  RECONNECTING: "warning",
  RUNNING: "success",
  ERROR: "danger",
  CLOSED: "info",
};
const stateText = (s) => STATE_LABEL[s] || "未启动";
const stateTagType = (s) => STATE_TAG[s] || "info";
const isBusy = (s) => s === "CONNECTING" || s === "RECONNECTING";

// 记录上次观察到的状态,状态变化时给出即时提示,让用户感知连接/重试/失败过程
const stateSeen = {};
const notifyStateTransition = (rows) => {
  const seenIds = new Set();
  for (const row of rows) {
    seenIds.add(row.id);
    const cur = row.state || "IDLE";
    const prev = stateSeen[row.id];
    if (prev && prev !== cur) {
      if (cur === "RUNNING" && prev === "CONNECTING") {
        ElMessage.success(`隧道「${row.name}」连接成功`);
      } else if (cur === "RUNNING" && prev === "RECONNECTING") {
        ElMessage.success(`隧道「${row.name}」已自动重连`);
      } else if (cur === "ERROR" && ["CONNECTING", "RECONNECTING", "RUNNING"].includes(prev)) {
        ElMessage.error(`「${row.name}」连接失败:${row.statusDetail || "网络异常"}`);
      }
    }
    stateSeen[row.id] = cur;
  }
  Object.keys(stateSeen).forEach((id) => {
    if (!seenIds.has(Number(id))) delete stateSeen[id];
  });
};

const loadList = async () => {
  if (!list.value.length) loading.value = true;
  try {
    list.value = await tunnelApi.listBastions();
    notifyStateTransition(list.value);
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    loading.value = false;
    schedulePoll();
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
    // 启动已改为异步:接口立即返回,连接与重试进展由轮询实时反馈
    await tunnelApi.startTunnel(row.id);
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
    const needPw = result.needPasswordNames || [];
    ElMessage.success(
      `导入完成:成功 ${result.successCount},跳过 ${result.skipCount}` +
        (needPw.length ? `,需重设密码 ${needPw.length} 项` : "")
    );
    showImportNotice(needPw, result.skippedNames || []);
    loadList();
  } catch (err) {
    ElMessage.error(err.message || "导入失败:JSON 格式错误");
  } finally {
    e.target.value = "";
  }
};

// 导入后提示:导出文件密码已脱敏 → 需重设密码;同名/信息缺失 → 已跳过
const showImportNotice = (needPw, skipped) => {
  const parts = [];
  if (needPw.length) {
    parts.push(
      `以下 ${needPw.length} 项未携带真实密码(导出文件已脱敏),已导入但暂不能连接:` +
        `\n\n${needPw.join("\n")}` +
        `\n\n请点击对应配置的「编辑」重新输入密码后即可正常使用。`
    );
  }
  if (skipped.length) {
    parts.push(`以下 ${skipped.length} 项被跳过:\n\n${skipped.join("\n")}`);
  }
  if (parts.length) {
    ElMessageBox.alert(parts.join("\n\n"), "导入完成,请注意", {
      type: "warning",
      confirmButtonText: "知道了",
    }).catch(() => {});
  }
};

// 轮询:有跳板处于连接/重连中时加速到 1.5s 让重试进展及时可见,空闲时 5s
let timer = null;
const schedulePoll = () => {
  if (timer) clearInterval(timer);
  const busy = list.value.some((r) => isBusy(r.state || "IDLE"));
  timer = setInterval(loadList, busy ? 1500 : 5000);
};
onMounted(() => {
  loadList();
});
onUnmounted(() => {
  if (timer) clearInterval(timer);
});
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
      <el-table-column label="状态" width="170" align="center">
        <template #default="{ row }">
          <el-tag :type="stateTagType(row.state)" size="small">
            {{ stateText(row.state) }}
          </el-tag>
          <div
            v-if="row.statusDetail && ['CONNECTING', 'RECONNECTING', 'ERROR'].includes(row.state)"
            class="state-detail"
            :title="row.statusDetail"
          >
            {{ row.statusDetail }}
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.state !== 'RUNNING' && !isBusy(row.state)"
            type="success"
            size="small"
            @click="onStart(row)"
          >启动</el-button>
          <el-button
            v-else
            type="warning"
            size="small"
            @click="onStop(row)"
          >{{ isBusy(row.state) ? "取消连接" : "停止" }}</el-button>
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
.state-detail {
  margin-top: 3px;
  font-size: 11px;
  line-height: 1.4;
  color: #909399;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
