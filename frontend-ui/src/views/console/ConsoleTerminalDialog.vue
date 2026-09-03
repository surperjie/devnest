<script setup>
import { ref, onMounted, onUnmounted, nextTick, watch } from "vue";
import { Terminal } from "@xterm/xterm";
import { FitAddon } from "@xterm/addon-fit";
import "@xterm/xterm/css/xterm.css";
import { consoleApi } from "../../api/console";

const props = defineProps({ row: Object });
const emit = defineEmits(["close"]);

const termRef = ref(null);
const term = ref(null);
const fitAddon = ref(null);
const ws = ref(null);
const visible = ref(true);
const fullscreen = ref(false);
const quickCommands = ref([]);

// 解析控制台绑定的快捷命令 JSON
const parseQuickCommands = () => {
  const raw = props.row?.quickCommands;
  if (!raw) {
    quickCommands.value = [];
    return;
  }
  try {
    const arr = JSON.parse(raw);
    quickCommands.value = Array.isArray(arr) ? arr : [];
  } catch (e) {
    quickCommands.value = [];
  }
};

const onClose = () => {
  visible.value = false;
  cleanup();
  emit("close");
};

const cleanup = () => {
  if (ws.value) {
    ws.value.onclose = null;
    ws.value.onerror = null;
    ws.value.onmessage = null;
    ws.value.onopen = null;
    try {
      ws.value.close();
    } catch (e) {}
    ws.value = null;
  }
  if (term.value) {
    try {
      term.value.dispose();
    } catch (e) {}
    term.value = null;
  }
  window.removeEventListener("resize", onResize);
};

// 重新 fit 并把新行列数通知后端调整 PTY,修复输出超出界限
const sendResize = () => {
  if (!term.value || !fitAddon.value) return;
  try {
    fitAddon.value.fit();
    if (ws.value && ws.value.readyState === WebSocket.OPEN) {
      const msg = JSON.stringify({
        type: "resize",
        cols: term.value.cols,
        rows: term.value.rows,
      });
      ws.value.send(msg);
    }
  } catch (e) {}
};

const onResize = () => sendResize();

// 点击快捷命令:发送命令 + 回车到 shell
const sendQuickCommand = (cmd) => {
  if (ws.value && ws.value.readyState === WebSocket.OPEN) {
    ws.value.send(cmd + "\r");
  }
};

// 全屏切换后容器尺寸变化,等 DOM 更新再重新适配
watch(fullscreen, async () => {
  await nextTick();
  sendResize();
});

const init = async () => {
  await nextTick();
  parseQuickCommands();
  term.value = new Terminal({
    fontSize: 14,
    cursorBlink: true,
    theme: {
      background: "#1e1e1e",
      foreground: "#e0e0e0",
      cursor: "#ffffff",
    },
  });
  fitAddon.value = new FitAddon();
  term.value.loadAddon(fitAddon.value);
  term.value.open(termRef.value);
  fitAddon.value.fit();
  term.value.writeln(`\x1b[36m正在连接到 ${props.row?.name}...\x1b[0m`);

  // 先申请一次性 WS token (TOFU 30s TTL,后端架构升级后强制校验)
  let token;
  try {
    token = await consoleApi.issueWsToken(props.row.id);
    term.value.writeln("\x1b[90m[TOFU] 握手令牌已获取,建立 WebSocket...\x1b[0m");
  } catch (e) {
    term.value.writeln(`\r\n\x1b[31m[令牌申请失败] ${e.message || e}\x1b[0m`);
    term.value.writeln("\x1b[31m[连接已关闭]\x1b[0m");
    return;
  }

  // WebSocket base 选择:优先用环境变量显式指定的后端,否则默认直连本地 8080
  // (和昨天的版本保持一致的默认行为,避免走到 Vite dev server 5173 但 /ws 没代理的坑)
  let wsBase = "ws://127.0.0.1:8080";
  try {
    const apiBase = import.meta.env.VITE_API_BASE_URL;
    if (apiBase && (apiBase.startsWith("http://") || apiBase.startsWith("https://"))) {
      // 显式配置了远程后端:保持协议(host 对应用户部署的地址)
      const u = new URL(apiBase);
      wsBase = (u.protocol === "https:" ? "wss://" : "ws://") + u.host;
    }
  } catch (e) {
    wsBase = "ws://127.0.0.1:8080";
  }
  const url = `${wsBase}/ws/console/${props.row.id}?token=${encodeURIComponent(token)}`;
  term.value.writeln(`\x1b[90m[TOFU] 连接目标: ${url.replace(/token=[^&]+/, "token=***")}\x1b[0m`);
  ws.value = new WebSocket(url);

  ws.value.onopen = () => {
    term.value?.writeln("\x1b[32m[已连接,可输入命令]\x1b[0m");
    sendResize();
  };
  ws.value.onmessage = (e) => {
    if (term.value && e.data) {
      term.value.write(e.data);
    }
  };
  ws.value.onerror = () => {
    term.value?.writeln("\r\n\x1b[31m[连接错误] 请检查后端日志或令牌有效期\x1b[0m");
  };
  ws.value.onclose = (ev) => {
    const extra = (ev && (ev.code || ev.reason)) ? ` (code=${ev.code} ${ev.reason || ""})` : "";
    term.value?.writeln(`\r\n\x1b[31m[连接已关闭]${extra}\x1b[0m`);
  };

  term.value.onData((data) => {
    if (ws.value && ws.value.readyState === WebSocket.OPEN) {
      ws.value.send(data);
    }
  });

  window.addEventListener("resize", onResize);
};

onMounted(init);
onUnmounted(cleanup);
</script>

<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="(v) => { if (!v) onClose(); }"
    :fullscreen="fullscreen"
    :close-on-click-modal="false"
    :before-close="onClose"
    destroy-on-close
    top="5vh"
    :width="fullscreen ? '100%' : '85%'"
  >
    <template #header>
      <div class="dialog-header">
        <span class="title">{{ row?.name }} - SSH 终端</span>
        <el-button size="small" @click="fullscreen = !fullscreen">
          {{ fullscreen ? "退出全屏" : "全屏" }}
        </el-button>
      </div>
    </template>
    <div v-if="quickCommands.length" class="quick-bar">
      <span class="quick-label">快捷命令:</span>
      <el-button
        v-for="(c, i) in quickCommands"
        :key="i"
        size="small"
        type="primary"
        plain
        @click="sendQuickCommand(c.command)"
      >
        {{ c.name }}
      </el-button>
    </div>
    <div ref="termRef" class="terminal-container" :class="{ 'is-fullscreen': fullscreen }"></div>
    <template #footer>
      <el-button @click="onClose">关闭终端</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-right: 16px;
}
.dialog-header .title {
  font-size: 16px;
  font-weight: 600;
}
.quick-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 8px 12px;
  background: #252526;
  border-radius: 4px 4px 0 0;
}
.quick-label {
  color: #9cdcfe;
  font-size: 13px;
  margin-right: 4px;
}
.terminal-container {
  height: 60vh;
  background: #1e1e1e;
  padding: 8px;
  border-radius: 0 0 4px 4px;
  overflow: hidden;
}
.terminal-container.is-fullscreen {
  height: calc(100vh - 180px);
}
</style>
