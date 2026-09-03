<!--
  原生 textarea + 浮层 SQL 自动补全
  零第三方编辑器依赖,100% 兼容,保证输入框可见可用
  支持:关键字/库/表/列补全,Ctrl+Enter/F5 执行(含框选)
-->
<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from "vue";

const props = defineProps({
  modelValue: { type: String, default: "" },
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: "" },
  // 补全库[{ name, tables: [{name, columns: [{name, remark}]}] }]
  schemaTree: { type: Array, default: () => [] },
});
const emit = defineEmits(["update:modelValue", "execute", "blur"]);

const taRef = ref(null);

// MySQL 关键字(用于补全)
const SQL_KEYWORDS = [
  "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "LIKE", "BETWEEN", "IS", "NULL",
  "AS", "GROUP", "BY", "ORDER", "HAVING", "LIMIT", "OFFSET", "UNION", "ALL", "DISTINCT",
  "COUNT", "SUM", "AVG", "MAX", "MIN", "DESC", "ASC",
  "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
  "CREATE", "TABLE", "INDEX", "ALTER", "ADD", "DROP", "MODIFY", "CHANGE",
  "TRUNCATE", "RENAME", "USE", "SHOW", "DATABASES", "TABLES", "COLUMNS",
  "DESCRIBE", "DESC", "EXPLAIN", "PRIMARY", "KEY", "FOREIGN", "REFERENCES",
  "UNIQUE", "DEFAULT", "AUTO_INCREMENT", "ENGINE", "CHARSET", "COLLATE", "COMMENT",
  "CONSTRAINT", "CASE", "WHEN", "THEN", "END", "WITH", "EXISTS", "ANY", "SOME",
  "CROSS", "USING", "NATURAL", "INNER", "JOIN", "LEFT", "RIGHT", "OUTER", "FULL",
  "ON", "LATERAL", "WINDOW", "PARTITION", "ROWS", "RANGE", "ROLLUP", "CUBE",
  "GROUPING", "SETS", "OVER",
  "INT", "BIGINT", "VARCHAR", "CHAR", "TEXT", "LONGTEXT", "DATETIME", "DATE",
  "TIME", "TIMESTAMP", "DECIMAL", "DOUBLE", "FLOAT", "TINYINT", "SMALLINT",
  "MEDIUMINT", "BOOLEAN", "BIT", "BLOB", "JSON", "ENUM", "SET",
];

// 构建补全选项(扁平数组,每次 schema 变化重算)
const completionOptions = computed(() => {
  const items = [];
  for (const k of SQL_KEYWORDS) {
    items.push({ label: k, type: "关键字", detail: "SQL KEYWORD" });
  }
  for (const db of props.schemaTree || []) {
    items.push({ label: db.name, type: "库", detail: "DATABASE" });
    for (const t of db.tables || []) {
      items.push({ label: t.name, type: "表", detail: `表 · ${db.name}` });
      for (const c of t.columns || []) {
        items.push({
          label: c.name,
          type: "列",
          detail: `${db.name}.${t.name}`,
          remark: c.remark || "",
        });
      }
    }
  }
  return items;
});

// 弹层状态
const showPopup = ref(false);
const popupLeft = ref("0px");
const popupTop = ref("0px");
const popupWidth = ref("280px");
const activeIdx = ref(0);
const filterText = ref("");
const filterFrom = ref(0);
const filterTo = ref(0);

const filteredItems = computed(() => {
  if (!filterText.value) return completionOptions.value.slice(0, 50);
  const q = filterText.value.toLowerCase();
  const out = [];
  for (const it of completionOptions.value) {
    if (out.length >= 100) break;
    if (it.label.toLowerCase().startsWith(q)) out.unshift(it);
    else if (it.label.toLowerCase().includes(q)) out.push(it);
  }
  return out.slice(0, 80);
});

watch(filteredItems, () => { activeIdx.value = 0; });

// ==================== 文本处理 ====================
const localText = ref(props.modelValue || "");
watch(
  () => props.modelValue,
  (v) => { if (v !== localText.value) localText.value = v || ""; },
);
watch(localText, (v) => emit("update:modelValue", v));

// 当前光标位置下的"待补全 token"起始/结束 + token 文字
function readTokenAtCursor(ta) {
  const pos = ta.selectionStart;
  const text = ta.value;
  let i = pos - 1;
  while (i >= 0 && /[\w$.]/.test(text[i])) i--;
  const from = i + 1;
  let j = pos;
  while (j < text.length && /[\w$.]/.test(text[j])) j++;
  const token = text.substring(from, j);
  return { from, to: j, token };
}

// 计算光标像素坐标(相对 textarea 容器,近似用 lineHeight*行+charWidth*列)
function caretCoords(ta) {
  const taRect = ta.getBoundingClientRect();
  const pos = ta.selectionStart;
  const before = ta.value.substring(0, pos);
  const lines = before.split(/\n/);
  const lineNo = lines.length - 1;
  const col = lines[lines.length - 1].length;
  const cs = getComputedStyle(ta);
  const lh = parseInt(cs.lineHeight) || parseInt(cs.fontSize) * 1.6 || 22;
  const fontSize = parseInt(cs.fontSize) || 14;
  const left = ta.offsetLeft + ta.clientLeft + col * (fontSize * 0.6) + 2 - ta.scrollLeft;
  const top = ta.offsetTop + ta.clientTop + (lineNo + 1) * lh + 2 - ta.scrollTop;
  return { left, top };
}

// 触发补全
const MIN_TRIGGER_LEN = 1;
function triggerCompletion(ta) {
  const { from, to, token } = readTokenAtCursor(ta);
  if (!token || token.length < MIN_TRIGGER_LEN) {
    hidePopup();
    return;
  }
  filterText.value = token;
  filterFrom.value = from;
  filterTo.value = to;
  if (!filteredItems.value.length) { hidePopup(); return; }
  const coords = caretCoords(ta);
  popupLeft.value = coords.left + "px";
  popupTop.value = coords.top + "px";
  showPopup.value = true;
  activeIdx.value = 0;
}

function hidePopup() {
  showPopup.value = false;
  filterText.value = "";
}

// 应用选中补全项到 textarea
function applyItem(idx) {
  const items = filteredItems.value;
  const item = items[idx];
  if (!item) return;
  const ta = taRef.value;
  if (!ta) return;
  const cur = localText.value;
  const from = filterFrom.value;
  const to = filterTo.value;
  const replaced = cur.substring(0, from) + item.label + cur.substring(to);
  localText.value = replaced;
  emit("update:modelValue", replaced);
  // 设置光标到插入位置后面
  nextTick(() => {
    const nextPos = from + item.label.length;
    ta.focus();
    ta.selectionStart = nextPos;
    ta.selectionEnd = nextPos;
  });
  hidePopup();
}

// 向上/向下选择
function moveActive(delta) {
  const n = filteredItems.value.length;
  if (!n) return;
  activeIdx.value = (activeIdx.value + delta + n) % n;
}

// 执行(Ctrl+Enter / F5):含框选语义
function doExecuteFromTa() {
  const ta = taRef.value;
  if (!ta) return;
  const isSel = ta.selectionStart !== ta.selectionEnd;
  const sql = isSel
    ? ta.value.substring(ta.selectionStart, ta.selectionEnd)
    : ta.value;
  emit("execute", sql, isSel);
}

// ==================== 事件 ====================
function onInput(e) {
  localText.value = e.target.value;
  emit("update:modelValue", e.target.value);
  triggerCompletion(e.target);
}
function onKeydown(e) {
  const ta = e.target;
  // 执行快捷键
  if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
    e.preventDefault();
    doExecuteFromTa();
    return;
  }
  if (e.key === "F5") {
    e.preventDefault();
    doExecuteFromTa();
    return;
  }
  if (showPopup.value) {
    if (e.key === "ArrowDown") { e.preventDefault(); moveActive(1); return; }
    if (e.key === "ArrowUp")   { e.preventDefault(); moveActive(-1); return; }
    if (e.key === "Enter" || e.key === "Tab") {
      e.preventDefault();
      applyItem(activeIdx.value);
      return;
    }
    if (e.key === "Escape") { e.preventDefault(); hidePopup(); return; }
  }
  // 字母/数字/点触发补全
  if (/^[\w$.]$/.test(e.key) || e.key === "Backspace") {
    // 等 input 事件后再算
    nextTick(() => triggerCompletion(ta));
  } else if ([" ", "(", ")", ";", ",", "\n", "=", "<", ">"].includes(e.key)) {
    hidePopup();
  }
}
function onBlur() {
  // 延迟:允许点击弹层
  setTimeout(() => { hidePopup(); emit("blur"); }, 150);
}
function onMouseUp(e) {
  // 选中文本后按执行按钮即可(这里不用处理,快捷键已经覆盖),但改变光标位置重算补全
  nextTick(() => triggerCompletion(e.target));
}

// 点击弹层外关闭
function onPopupMouseDown(e) {
  const ta = taRef.value;
  if (!e.target.closest(".ac-popup") && !e.target.closest(".sql-complete-ta")) return;
  if (ta) ta.focus();
}

onMounted(() => {
  document.addEventListener("mousedown", onPopupMouseDown);
});
onBeforeUnmount(() => {
  document.removeEventListener("mousedown", onPopupMouseDown);
});
</script>

<template>
  <div class="sql-complete-wrap" style="width:100%;height:100%;position:relative;">
    <textarea
      ref="taRef"
      :value="localText"
      :disabled="disabled"
      class="sql-complete-ta"
      :placeholder="placeholder"
      spellcheck="false"
      @input="onInput"
      @keydown="onKeydown"
      @blur="onBlur"
      @mouseup="onMouseUp"
      @click="($event) => nextTick(() => triggerCompletion($event.target))"
    ></textarea>
    <div
      v-if="showPopup && filteredItems.length"
      class="ac-popup"
      :style="{ left: popupLeft, top: popupTop, width: popupWidth }"
      @mousedown.prevent
    >
      <div
        v-for="(it, idx) in filteredItems.slice(0, 20)"
        :key="it.label + '|' + it.detail"
        :class="['ac-item', { active: idx === activeIdx }]"
        @mouseenter="activeIdx = idx"
        @click="applyItem(idx)"
      >
        <span class="ac-label">{{ it.label }}</span>
        <span class="ac-type" :class="'ac-type-' + it.type">{{ it.type }}</span>
        <span class="ac-detail">{{ it.detail }}</span>
      </div>
      <div v-if="filteredItems.length > 20" class="ac-more">还有 {{ filteredItems.length - 20 }} 个匹配...</div>
    </div>
  </div>
</template>

<style scoped>
.sql-complete-wrap { box-sizing: border-box; }
.sql-complete-ta {
  width: 100%;
  height: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 10px 12px;
  box-sizing: border-box;
  resize: none;
  outline: none;
  font-family: Consolas, Menlo, "Courier New", monospace;
  font-size: 14px;
  line-height: 1.6;
  background: #fff;
  tab-size: 2;
}
.sql-complete-ta:focus {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.18);
}
.sql-complete-ta:disabled {
  background: #f5f7fa;
  color: #909399;
  cursor: not-allowed;
}

/* 补全浮层 */
.ac-popup {
  position: absolute;
  z-index: 3000;
  max-height: 300px;
  overflow: auto;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.12);
  padding: 4px 0;
}
.ac-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 10px;
  font-size: 13px;
  cursor: pointer;
  user-select: none;
}
.ac-item.active {
  background: #ecf5ff;
}
.ac-label { flex: 0 0 auto; min-width: 140px; font-family: Consolas, Menlo, monospace; }
.ac-type {
  flex-shrink: 0;
  padding: 1px 6px;
  font-size: 11px;
  border-radius: 10px;
  color: #fff;
}
.ac-type-关键字 { background: #f56c6c; }
.ac-type-库     { background: #67c23a; }
.ac-type-表     { background: #409eff; }
.ac-type-列     { background: #909399; }
.ac-detail {
  flex: 1;
  color: #909399;
  font-size: 12px;
  text-align: right;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.ac-more {
  padding: 4px 10px;
  font-size: 12px;
  color: #909399;
  border-top: 1px solid #f2f6fc;
}
</style>
