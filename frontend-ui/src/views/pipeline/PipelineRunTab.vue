<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { pipelineApi } from "../../api/pipeline";
import PipelineRunParamsDialog from "./PipelineRunParamsDialog.vue";

const props = defineProps({
  pipeline: { type: Object, required: true },
  visible: { type: Boolean, default: true },
});
const emit = defineEmits(["close"]);

const RUN_LABEL = { WAITING: "排队中", RUNNING: "运行中", SUCCESS: "成功", FAILED: "失败", STOPPED: "已停止" };
const RUN_TAG = { WAITING: "warning", RUNNING: "warning", SUCCESS: "success", FAILED: "danger", STOPPED: "info" };
const STEP_LABEL = { RUNNING: "运行中", SUCCESS: "成功", FAILED: "失败", SKIPPED: "跳过" };
const STEP_TAG = { RUNNING: "warning", SUCCESS: "success", FAILED: "danger", SKIPPED: "info" };
const isActive = (r) => r && (r.status === "WAITING" || r.status === "RUNNING");

const runs = ref([]);
const selectedRunId = ref(null);
const detail = ref(null);
const steps = ref([]);
const manualStep = ref(false);
const selectedStepSeq = ref(null);
const lastLoadedRunId = ref(null);
const lastLoadedActive = ref(null);

const logText = ref("");
const logOffset = ref(0);
const logDone = ref(true);
const loadedLogKey = ref("");

const paramsVisible = ref(false);
const logEl = ref();

const fmtTime = (t) => (t ? String(t).replace("T", " ").slice(0, 19) : "-");
const fmtDur = (s) => (s === null || s === undefined ? "-" : s < 60 ? `${s}s` : `${Math.floor(s / 60)}m${s % 60}s`);

const activeRun = computed(() => runs.value.find(isActive) || null);

/* ---------- 数据加载 ---------- */

async function loadRuns() {
  try {
    const list = await pipelineApi.listRuns(props.pipeline.id);
    runs.value = list;
    if (!list.length) {
      selectedRunId.value = null;
      detail.value = null;
      steps.value = [];
      logText.value = "";
      return;
    }
    if (!selectedRunId.value || !list.some((r) => r.id === selectedRunId.value)) {
      selectedRunId.value = list[0].id;
    }
  } catch (e) {
    // 忽略瞬时错误,下次轮询自动恢复
  }
}

async function loadDetail(runId) {
  try {
    const d = await pipelineApi.getRun(runId);
    detail.value = d;
    steps.value = d.steps || [];
    // 默认定位:优先当前运行步骤,否则最后一个步骤
    if (!manualStep.value || !steps.value.some((s) => s.seq === selectedStepSeq.value)) {
      const runningStep = steps.value.find((s) => s.status === "RUNNING");
      selectedStepSeq.value = runningStep ? runningStep.seq : steps.value.length ? steps.value[steps.value.length - 1].seq : null;
    }
  } catch (e) {
    // ignore
  }
}

async function refreshLog() {
  const run = detail.value;
  if (!run || !steps.value.length || selectedStepSeq.value == null) return;
  const step = steps.value.find((s) => s.seq === selectedStepSeq.value);
  if (!step) return;
  const key = `${run.id}:${selectedStepSeq.value}:${isActive(run)}:${step.status}`;
  if (key !== loadedLogKey.value) {
    loadedLogKey.value = key;
    logText.value = "";
    logOffset.value = 0;
    logDone.value = false;
  }
  if (logDone.value) return;
  try {
    const chunk = await pipelineApi.getStepLog(run.id, selectedStepSeq.value, logOffset.value);
    if (chunk.text) logText.value += chunk.text;
    logOffset.value = chunk.nextOffset || 0;
    logDone.value = !!chunk.finished;
  } catch (e) {
    // ignore
  }
}

async function tick() {
  if (!props.visible) return;
  await loadRuns();
  if (activeRun.value) {
    if (lastLoadedRunId.value !== activeRun.value.id || lastLoadedActive.value !== true) {
      lastLoadedRunId.value = activeRun.value.id;
      lastLoadedActive.value = true;
      await loadDetail(activeRun.value.id);
    } else {
      await loadDetail(activeRun.value.id);
    }
  } else {
    if (lastLoadedActive.value === true) {
      // 刚结束的运行刷新终态
      lastLoadedActive.value = false;
      if (selectedRunId.value) await loadDetail(selectedRunId.value);
    }
    if (selectedRunId.value && (lastLoadedRunId.value !== selectedRunId.value || detail.value === null)) {
      lastLoadedRunId.value = selectedRunId.value;
      await loadDetail(selectedRunId.value);
    }
  }
  await refreshLog();
}

let timer = null;
onMounted(() => {
  tick();
  timer = setInterval(tick, 2000);
});
onUnmounted(() => {
  if (timer) clearInterval(timer);
});
watch(
  () => props.visible,
  (v) => {
    if (v) tick();
  }
);
watch(logText, async () => {
  await nextTick();
  if (logEl.value) logEl.value.scrollTop = logEl.value.scrollHeight;
});

/* ---------- 交互 ---------- */

const onStart = async (inputs) => {
  paramsVisible.value = false;
  try {
    await pipelineApi.startRun(props.pipeline.id, inputs);
    ElMessage.success("已启动运行");
    lastLoadedRunId.value = null;
    selectedStepSeq.value = null;
    await tick();
  } catch (e) {
    ElMessage.error(e.message);
  }
};

const onStop = async (run) => {
  try {
    await ElMessageBox.confirm(`确认停止 run #${run.id}?正在运行的脚本会被强制终止。`, "停止运行", {
      type: "warning",
    });
    await pipelineApi.stopRun(run.id);
    ElMessage.success("停止请求已发送");
  } catch (e) {
    if (String(e) !== "cancel" && e?.message) ElMessage.error(e.message);
  }
};

const selectRun = async (run) => {
  selectedRunId.value = run.id;
  lastLoadedRunId.value = null;
  detail.value = null;
  await tick();
};

const selectStep = (step) => {
  manualStep.value = true;
  selectedStepSeq.value = step.seq;
  logText.value = "";
  loadedLogKey.value = "";
  refreshLog();
};
</script>

<template>
  <div class="run-tab">
    <div class="tab-head">
      <div class="head-left">
        <span class="pipe-name">{{ pipeline.name }}</span>
        <el-tag v-if="activeRun" type="warning" size="small" effect="dark">运行中 #{{ activeRun.id }}</el-tag>
      </div>
      <div class="head-right">
        <el-button size="small" type="primary" @click="paramsVisible = true">运行...</el-button>
        <el-button size="small" @click="lastLoadedRunId = null; tick()">刷新</el-button>
        <el-button size="small" link type="danger" @click="emit('close')">关闭标签</el-button>
      </div>
    </div>

    <div class="body">
      <!-- 左:运行记录 -->
      <div class="runs-panel">
        <div class="panel-title">运行记录</div>
        <el-table
          :data="runs"
          size="small"
          border
          max-height="240"
          highlight-current-row
          :current-row-key="selectedRunId"
          row-key="id"
          @row-click="selectRun"
        >
          <el-table-column label="ID" width="70">
            <template #default="{ row }">#{{ row.id }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="RUN_TAG[row.status] || 'info'" size="small">{{ RUN_LABEL[row.status] || row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="开始" width="150">
            <template #default="{ row }">{{ fmtTime(row.startTime) }}</template>
          </el-table-column>
          <el-table-column label="耗时" width="80">
            <template #default="{ row }">{{ fmtDur(row.durationSeconds) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button v-if="isActive(row)" type="danger" size="small" link @click.stop="onStop(row)">停止</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="detail?.errorMessage" class="run-error" :title="detail.errorMessage">
          错误: {{ detail.errorMessage }}
        </div>
      </div>

      <!-- 右:详情 + 日志 -->
      <div class="detail-panel" v-if="detail">
        <div class="panel-title">
          run #{{ detail.id }} 步骤
          <span class="run-meta">
            入参: <code>{{ Object.keys(detail.inputs || {}).join(", ") || "(无)" }}</code>
          </span>
        </div>
        <div class="steps">
          <div
            v-for="step in steps"
            :key="step.seq"
            class="step-item"
            :class="{ selected: selectedStepSeq === step.seq }"
            @click="selectStep(step)"
          >
            <span class="step-seq">{{ step.seq }}</span>
            <span class="step-name" :title="step.name">{{ step.name }}</span>
            <el-tag :type="STEP_TAG[step.status] || 'info'" size="small">{{ STEP_LABEL[step.status] || step.status }}</el-tag>
            <span v-if="step.exitCode != null" class="step-exit">退出码 {{ step.exitCode }}</span>
            <span class="step-time">{{ fmtDur(step.durationSeconds) }}</span>
          </div>
        </div>
        <div v-if="detail.errorMessage" class="run-error-line" :title="detail.errorMessage">
          <el-tag type="danger" size="small">运行错误</el-tag>
          <span class="err-msg">{{ detail.errorMessage }}</span>
        </div>

        <div class="panel-title log-title">步骤日志</div>
        <pre ref="logEl" class="log-panel"><code>{{ logText || "(暂无输出...)" }}</code></pre>
      </div>
      <el-empty v-else description="暂无运行,点击右上角「运行...」启动" style="flex: 1" />
    </div>

    <PipelineRunParamsDialog
      v-model="paramsVisible"
      :pipeline-name="pipeline.name"
      :default-vars="pipeline.vars"
      @start="onStart"
    />
  </div>
</template>

<style scoped>
.run-tab {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 12px 4px;
}
.tab-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.head-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.pipe-name {
  font-size: 16px;
  font-weight: 600;
}
.body {
  display: flex;
  gap: 12px;
  min-height: 0;
  flex: 1;
}
.runs-panel {
  width: 460px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.detail-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
}
.log-title {
  margin-top: 4px;
}
.run-meta {
  font-size: 12px;
  color: #909399;
  font-weight: 400;
  margin-left: 10px;
}
.steps {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  max-height: 96px;
  overflow-y: auto;
}
.step-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  cursor: pointer;
  background: #fff;
}
.step-item.selected {
  border-color: #409eff;
  background: #ecf5ff;
}
.step-seq {
  color: #909399;
  font-size: 12px;
}
.step-name {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.step-exit,
.step-time {
  color: #909399;
  font-size: 12px;
}
.run-error {
  color: #f56c6c;
  font-size: 12px;
  background: #fef0f0;
  padding: 6px 10px;
  border-radius: 4px;
  max-height: 40px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.run-error-line {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 100%;
}
.err-msg {
  font-size: 12px;
  color: #f56c6c;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.log-panel {
  flex: 1;
  min-height: 120px;
  background: #1e1e1e;
  color: #d4d4d4;
  font-family: Consolas, "Courier New", monospace;
  font-size: 12px;
  line-height: 1.5;
  padding: 10px;
  border-radius: 6px;
  overflow: auto;
  margin: 0;
}
</style>
