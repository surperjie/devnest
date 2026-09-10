<script setup>
import { ref, onMounted } from "vue";
import { useRoute } from "vue-router";
import { pipelineApi } from "../../api/pipeline";
import PipelineRunTab from "./PipelineRunTab.vue";

const route = useRoute();
const tabs = ref([]); // { id, name }
const activeKey = ref("");
const addVisible = ref(false);
const candidates = ref([]);
const loadingCandidates = ref(false);
const addingId = ref(null);

const openPipeline = (p) => {
  if (!tabs.value.some((t) => t.id === p.id)) {
    tabs.value.push({ id: p.id, name: p.name, vars: p.vars || {} });
  }
  activeKey.value = String(p.id);
};

const openAdd = async () => {
  addVisible.value = true;
  loadingCandidates.value = true;
  try {
    candidates.value = await pipelineApi.listPipelines();
  } catch (e) {
    // ignore
  } finally {
    loadingCandidates.value = false;
  }
};

const closeTab = (tab) => {
  const idx = tabs.value.findIndex((t) => String(t.id) === String(tab));
  if (idx >= 0) {
    tabs.value.splice(idx, 1);
    if (activeKey.value === String(tab)) {
      const next = tabs.value[idx] || tabs.value[idx - 1];
      activeKey.value = next ? String(next.id) : "";
    }
  }
};

onMounted(async () => {
  const q = route.query.pipelineId;
  if (q) {
    try {
      const list = await pipelineApi.listPipelines();
      const p = list.find((x) => String(x.id) === String(q));
      if (p) {
        openPipeline(p);
        return;
      }
    } catch (e) {
      // fallthrough
    }
  }
  // 默认打开首个流水线
  try {
    const list = await pipelineApi.listPipelines();
    if (list.length) openPipeline(list[0]);
  } catch (e) {
    // ignore
  }
});
</script>

<template>
  <div class="ws">
    <div class="ws-head">
      <h2>流水线运行</h2>
      <div class="ws-actions">
        <el-button size="small" @click="openAdd">+ 打开流水线</el-button>
        <el-button size="small" @click="tabs.forEach(t => closeTab(String(t.id)))" :disabled="!tabs.length">全部关闭</el-button>
      </div>
    </div>

    <div class="ws-body">
      <template v-if="tabs.length">
        <div class="tab-strip">
          <div
            v-for="tab in tabs"
            :key="tab.id"
            class="pipeline-tab"
            :class="{ active: activeKey === String(tab.id) }"
            @click="activeKey = String(tab.id)"
          >
            <span class="tab-dot"></span>
            <span class="tab-name">{{ tab.name }}</span>
            <span class="tab-close" @click.stop="closeTab(tab)">✕</span>
          </div>
        </div>
        <div class="tab-body">
          <PipelineRunTab
            v-for="tab in tabs"
            v-show="activeKey === String(tab.id)"
            :key="tab.id"
            :pipeline="tab"
            :visible="activeKey === String(tab.id)"
            @close="closeTab(tab)"
          />
        </div>
      </template>
      <el-empty v-else description="尚未打开任何流水线,点右上角「+ 打开流水线」选择一条">
        <el-button type="primary" @click="openAdd">打开流水线</el-button>
      </el-empty>
    </div>

    <!-- 选择流水线 -->
    <el-dialog v-model="addVisible" title="打开流水线" width="640px">
      <el-table
        :data="candidates"
        v-loading="loadingCandidates"
        size="small"
        border
        highlight-current-row
        :current-row-key="addingId"
        @current-change="(row) => (addingId = row ? row.id : null)"
      >
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="stepCount" label="步骤" width="70" align="center" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.running" type="warning" size="small">运行中</el-tag>
            <el-tag v-else-if="row.lastStatus" size="small" :type="row.lastStatus === 'SUCCESS' ? 'success' : row.lastStatus === 'FAILED' ? 'danger' : 'info'">
              {{ row.lastStatus }}
            </el-tag>
            <span v-else class="never">从未运行</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      </el-table>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" @click="() => { const p = candidates.find(x => x.id === addingId); if (p) { openPipeline(p); addVisible = false; } }">
          打开
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ws {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.ws-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.ws-head h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}
.tab-strip {
  display: flex;
  gap: 4px;
  overflow-x: auto;
  padding-bottom: 2px;
}
.pipeline-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: #f4f4f5;
  border-radius: 6px 6px 0 0;
  cursor: pointer;
  border: 1px solid transparent;
  user-select: none;
  white-space: nowrap;
}
.pipeline-tab.active {
  background: #fff;
  border-color: #e4e7ed;
  border-bottom-color: #fff;
  color: #409eff;
  font-weight: 600;
}
.tab-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #c0c4cc;
}
.tab-close {
  cursor: pointer;
  color: #909399;
}
.tab-body {
  flex: 1;
  min-height: 0;
  border: 1px solid #e4e7ed;
  background: #fff;
  padding: 0 12px;
  overflow: hidden;
}
.never {
  color: #c0c4cc;
  font-size: 12px;
}
</style>
