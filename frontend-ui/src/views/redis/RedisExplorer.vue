<script setup>
import { ref, onMounted, computed } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { redisApi } from "../../api/redis";

const route = useRoute();
const redisId = Number(route.params.id);

// === 实例 INFO ===
const info = ref(null);
const infoLoading = ref(false);

// === db 列表 ===
const dbList = ref([]);
const selectedDb = ref(0); // 数字,直接传后端

// === key 扫描 ===
const keyCursor = ref("0");
const keyList = ref([]);
const keyPattern = ref("*");
const dbSize = ref(0);
const keysLoading = ref(false);
const hasMore = computed(() => keyCursor.value !== "0");

// === value 详情 ===
const selectedKey = ref(null);
const valueLoading = ref(false);
const valueData = ref(null); // RedisValueDto

// === 命令执行 ===
const cmdInput = ref("");
const cmdHistory = ref([]);
const cmdResult = ref(null);
const cmdLoading = ref(false);

// === 左侧面板可拖拽 ===
const leftWidth = ref(300);
const MIN_L = 180, MAX_L = 700;

const loadLayout = () => {
  try {
    const w = parseInt(localStorage.getItem(`devnest:redis:${redisId}:layout`));
    if (!isNaN(w) && w >= MIN_L && w <= MAX_L) leftWidth.value = w;
  } catch {}
};
const saveLayout = () => {
  try { localStorage.setItem(`devnest:redis:${redisId}:layout`, String(leftWidth.value)); } catch {}
};

// === 初始化 ===
const init = async () => {
  loadLayout();
  await Promise.all([loadInfo(), loadDbs()]);
  await scanKeys(true);
};

const loadInfo = async () => {
  infoLoading.value = true;
  try {
    info.value = await redisApi.info(redisId);
    // info 里也带 dbSize,同步一下
    if (info.value?.dbSize != null) dbSize.value = info.value.dbSize;
  } catch (e) { ElMessage.error("INFO 加载失败: " + e.message); }
  finally { infoLoading.value = false; }
};

const loadDbs = async () => {
  try {
    dbList.value = await redisApi.listDbs(redisId);
  } catch { /* ignore */ }
};

// === SCAN key ===
const scanKeys = async (reset = false) => {
  if (reset) keyCursor.value = "0";
  keysLoading.value = true;
  try {
    const r = await redisApi.scanKeys(
      redisId, String(selectedDb.value),
      keyCursor.value,
      keyPattern.value || "*",
      200
    );
    if (reset) {
      keyList.value = r.keys;
    } else {
      keyList.value.push(...r.keys);
    }
    keyCursor.value = r.cursor;
    if (r.dbSize != null) dbSize.value = r.dbSize;
  } catch (e) { ElMessage.error(e.message); }
  finally { keysLoading.value = false; }
};

const onRefreshKeys = () => scanKeys(true);
const onLoadMoreKeys = () => scanKeys(false);
const onDbChange = () => {
  selectedKey.value = null;
  valueData.value = null;
  scanKeys(true);
};

// === 选中 key 查看 value ===
const onKeyClick = async (key) => {
  selectedKey.value = key;
  valueLoading.value = true;
  try {
    valueData.value = await redisApi.getValue(redisId, String(selectedDb.value), key);
  } catch (e) { ElMessage.error(e.message); }
  finally { valueLoading.value = false; }
};

const onDeleteKey = async () => {
  if (!selectedKey.value) return;
  try {
    await ElMessageBox.confirm(`确认删除 key 「${selectedKey.value}」?`, "提示", { type: "warning" });
    const r = await redisApi.delKey(redisId, String(selectedDb.value), selectedKey.value);
    if (r.success) {
      ElMessage.success(r.output);
      selectedKey.value = null;
      valueData.value = null;
      scanKeys(true);
      loadInfo();
    } else {
      ElMessage.error(r.errorMsg || "删除失败");
    }
  } catch (e) {
    if (e !== "cancel" && e?.toString() !== "cancel") ElMessage.error(e.message || "操作失败");
  }
};

// === 命令执行 ===
const onExec = async () => {
  const cmd = cmdInput.value?.trim();
  if (!cmd) return;
  cmdLoading.value = true;
  try {
    const r = await redisApi.execute(redisId, String(selectedDb.value), cmd);
    cmdResult.value = r;
    // 命令结果自动刷新 info 和 dbSize
    loadInfo();
    // 刷新 key 列表(仅对写命令或会改变结构的命令)
    const c = cmd.split(/\s+/)[0].toUpperCase();
    if (["SET", "DEL", "HSET", "HDEL", "LPUSH", "RPUSH", "SADD", "SREM",
         "ZADD", "ZREM", "EXPIRE", "RENAME", "UNLINK"].includes(c)) {
      scanKeys(true);
    }
    // 追加历史
    cmdHistory.value.unshift({ cmd, time: new Date().toLocaleTimeString() });
    cmdHistory.value = cmdHistory.value.slice(0, 50);
  } catch (e) {
    cmdResult.value = { success: false, errorMsg: e.message };
  } finally { cmdLoading.value = false; }
};

const onKeyDownCmd = (e) => {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    onExec();
  }
};

// === 左面板拖拽 ===
const dragging = ref(false);
const onDragStart = (e) => {
  dragging.value = true;
  document.addEventListener("mousemove", onDragMove);
  document.addEventListener("mouseup", onDragEnd);
  e.preventDefault();
};
const onDragMove = (e) => {
  if (!dragging.value) return;
  leftWidth.value = Math.max(MIN_L, Math.min(MAX_L, e.clientX));
};
const onDragEnd = () => {
  dragging.value = false;
  document.removeEventListener("mousemove", onDragMove);
  document.removeEventListener("mouseup", onDragEnd);
  saveLayout();
};

onMounted(init);
</script>

<template>
  <div class="re-explorer">
    <!-- 左侧面板 -->
    <div class="left-panel" :style="{ width: leftWidth + 'px' }">
      <!-- db 选择 -->
      <div class="section">
        <div class="section-title">数据库 (db{{ selectedDb }})</div>
        <el-scrollbar height="140px">
          <div class="db-grid">
            <div
              v-for="db in dbList"
              :key="db"
              class="db-item"
              :class="{ active: selectedDb === Number(db.replace('db', '')) }"
              @click="selectedDb = Number(db.replace('db', '')); onDbChange()"
            >
              {{ db }}
            </div>
          </div>
        </el-scrollbar>
      </div>

      <!-- key 扫描 -->
      <div class="section flex1">
        <div class="section-title">
          Keys <span class="mini-info">共 {{ dbSize }}</span>
        </div>
        <div class="key-filter">
          <el-input v-model="keyPattern" size="small" placeholder="pattern 如 user:*" clearable
            @keyup.enter="scanKeys(true)">
            <template #append>
              <el-button size="small" :loading="keysLoading" @click="scanKeys(true)">🔍</el-button>
            </template>
          </el-input>
        </div>
        <el-scrollbar class="key-scroll">
          <div v-if="keyList.length === 0 && !keysLoading" class="empty-hint">
            暂无 key
          </div>
          <div
            v-for="k in keyList"
            :key="k"
            class="key-item"
            :class="{ active: selectedKey === k }"
            @click="onKeyClick(k)"
          >
            <span class="key-icon">🔑</span>
            <span class="key-name">{{ k }}</span>
          </div>
          <div v-if="hasMore" class="load-more" @click="onLoadMoreKeys">
            {{ keysLoading ? "加载中..." : "加载更多 (cursor=" + keyCursor + ")" }}
          </div>
          <div v-if="!hasMore && keyList.length > 0" class="scan-done">✓ 遍历完成</div>
        </el-scrollbar>
      </div>
    </div>

    <!-- 拖拽条 -->
    <div class="drag-bar" @mousedown="onDragStart"></div>

    <!-- 右侧面板 -->
    <div class="right-panel">
      <!-- Info 概览 -->
      <el-card v-loading="infoLoading" class="info-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>📊 服务器信息</span>
            <el-button size="small" @click="loadInfo">刷新</el-button>
          </div>
        </template>
        <div v-if="info" class="info-grid">
          <div class="info-cell"><div class="info-label">版本</div><div class="info-val">{{ info.version }}</div></div>
          <div class="info-cell"><div class="info-label">模式</div><div class="info-val">{{ info.mode }}</div></div>
          <div class="info-cell"><div class="info-label">客户端</div><div class="info-val">{{ info.connectedClients }}</div></div>
          <div class="info-cell"><div class="info-label">内存</div><div class="info-val">{{ info.memoryUsed }}</div></div>
          <div class="info-cell"><div class="info-label">命令累计</div><div class="info-val">{{ info.totalCommandsProcessed }}</div></div>
          <div class="info-cell"><div class="info-label">当前 DB Keys</div><div class="info-val">{{ info.dbSize }}</div></div>
        </div>
      </el-card>

      <!-- Key Value -->
      <el-card class="value-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>🔎 Key 详情</span>
            <div v-if="selectedKey" class="key-head">
              <el-tag size="small" :type="valueData?.type === 'NONE' ? 'info' : 'primary'">
                {{ valueData?.type || "-" }}
              </el-tag>
              <span class="key-name-head">{{ selectedKey }}</span>
              <el-tag v-if="valueData?.ttl != null" type="warning" size="small">
                TTL {{ valueData.ttl === -1 ? "永久" : valueData.ttl + "s" }}
              </el-tag>
              <el-button size="small" type="danger" plain @click="onDeleteKey">删除</el-button>
            </div>
            <span v-else class="empty-hint-inline">点击左侧 Key 查看详情</span>
          </div>
        </template>
        <div v-loading="valueLoading" class="value-body">
          <template v-if="valueData && valueData.type !== 'NONE'">
            <!-- STRING -->
            <pre v-if="valueData.type === 'STRING'" class="value-pre">{{ valueData.stringValue }}</pre>
            <!-- HASH -->
            <el-table v-else-if="valueData.type === 'HASH'" :data="Object.entries(valueData.hashValue || {}).map(([k,v])=>({k,v}))" border stripe size="small">
              <el-table-column prop="k" label="Field" min-width="160" />
              <el-table-column prop="v" label="Value" min-width="200" show-overflow-tooltip />
            </el-table>
            <!-- LIST / SET / ZSET -->
            <el-table v-else-if="['LIST','SET','ZSET'].includes(valueData.type)"
              :data="(valueData.listValue || []).map((v,i)=>({i: i+1, v}))" border stripe size="small">
              <el-table-column prop="i" label="#" width="50" align="center" />
              <el-table-column prop="v" label="Value" min-width="260" show-overflow-tooltip />
            </el-table>
            <!-- STREAM / 其他 -->
            <pre v-else class="value-pre">{{ valueData.stringValue || JSON.stringify(valueData, null, 2) }}</pre>
          </template>
          <div v-else-if="valueData?.type === 'NONE'" class="empty-hint-inline">
            Key 不存在或已过期 (TTL={{ valueData.ttl }})
          </div>
        </div>
      </el-card>

      <!-- 命令执行 -->
      <el-card class="cmd-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>⌨️ 命令执行 <el-tag size="small" type="success">白名单安全模式</el-tag></span>
          </div>
        </template>
        <div class="cmd-row">
          <el-input v-model="cmdInput" size="default" placeholder="输入 Redis 命令,回车执行 (如 GET mykey / HSET hash f v)"
            @keydown="onKeyDownCmd" clearable />
          <el-button type="primary" :loading="cmdLoading" @click="onExec">执行</el-button>
        </div>
        <div v-if="cmdResult" class="cmd-result" :class="{ error: !cmdResult.success }">
          <div class="cmd-result-head">
            <span v-if="cmdResult.success" class="success-tag">✓ {{ cmdResult.costMs }}ms</span>
            <span v-else class="error-tag">✗ {{ cmdResult.errorMsg }}</span>
          </div>
          <pre class="cmd-output">{{ cmdResult.output || "(nil)" }}</pre>
        </div>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.re-explorer {
  display: flex;
  height: calc(100vh - 120px);
  min-height: 600px;
}

.left-panel {
  display: flex;
  flex-direction: column;
  background: #fafafa;
  border-right: 1px solid #ebeef5;
  overflow: hidden;
}
.section {
  border-bottom: 1px solid #ebeef5;
}
.section.flex1 {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.section-title {
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  background: #f5f7fa;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.mini-info {
  font-size: 11px;
  font-weight: normal;
  color: #909399;
}

.db-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 4px;
  padding: 8px;
}
.db-item {
  text-align: center;
  padding: 4px 0;
  font-size: 12px;
  border-radius: 4px;
  background: #fff;
  border: 1px solid #ebeef5;
  cursor: pointer;
  transition: all 0.15s;
}
.db-item:hover { border-color: #409eff; color: #409eff; }
.db-item.active { background: #409eff; color: #fff; border-color: #409eff; }

.key-filter { padding: 8px; }
.key-scroll { flex: 1; min-height: 0; }
.key-item {
  padding: 4px 12px;
  font-size: 13px;
  cursor: pointer;
  display: flex;
  gap: 4px;
  align-items: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.key-item:hover { background: #ecf5ff; }
.key-item.active { background: #d9ecff; color: #409eff; }
.key-icon { font-size: 12px; }
.key-name { overflow: hidden; text-overflow: ellipsis; }

.load-more {
  padding: 6px 12px;
  text-align: center;
  color: #909399;
  cursor: pointer;
  font-size: 12px;
}
.load-more:hover { color: #409eff; }
.scan-done {
  padding: 6px 12px;
  text-align: center;
  color: #67c23a;
  font-size: 11px;
}
.empty-hint {
  padding: 20px;
  text-align: center;
  color: #909399;
  font-size: 12px;
}
.empty-hint-inline { color: #909399; font-size: 12px; }

.drag-bar {
  width: 6px;
  cursor: col-resize;
  background: transparent;
  transition: background 0.2s;
}
.drag-bar:hover, .drag-bar:active { background: #409eff; }

.right-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
  min-width: 0;
  overflow: hidden;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.info-card { flex-shrink: 0; }
.info-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px 16px;
}
.info-cell { text-align: center; }
.info-label { font-size: 11px; color: #909399; }
.info-val { font-size: 14px; font-weight: 600; color: #303133; }

.value-card { flex: 1; display: flex; flex-direction: column; min-height: 200px; }
.value-card :deep(.el-card__body) { flex: 1; overflow: auto; padding: 0; }
.key-head { display: flex; align-items: center; gap: 8px; }
.key-name-head { font-family: monospace; color: #409eff; font-size: 13px; }
.value-body { padding: 12px; }
.value-pre {
  background: #f5f7fa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 10px 12px;
  margin: 0;
  font-family: "Consolas", monospace;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 400px;
  overflow: auto;
}

.cmd-card { flex-shrink: 0; }
.cmd-row { display: flex; gap: 8px; }
.cmd-result {
  margin-top: 10px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 8px 12px;
  background: #fafafa;
}
.cmd-result.error { border-color: #fbc4c4; background: #fef0f0; }
.cmd-result-head { margin-bottom: 4px; }
.success-tag { color: #67c23a; font-size: 12px; font-weight: 600; }
.error-tag { color: #f56c6c; font-size: 12px; font-weight: 600; }
.cmd-output {
  margin: 0;
  font-family: "Consolas", monospace;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
  color: #303133;
  max-height: 240px;
  overflow: auto;
}
</style>
