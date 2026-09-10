<script setup>
import { ref, watch } from "vue";

const props = defineProps({
  modelValue: Boolean,
  pipelineName: { type: String, default: "" },
  /** 流水线默认变量(key/value),用于预填提示,留空行=直接用默认 */
  defaultVars: { type: Object, default: () => ({}) },
});
const emit = defineEmits(["update:modelValue", "start"]);

const emptyRow = () => ({ key: "", value: "" });
const rows = ref([emptyRow()]);
/** 花括号变量示例文本,避免模板中直接书写 {{ 破坏编译 */
const varRef = (key) => "{{" + key + "}}";

const defaultKeys = () => Object.keys(props.defaultVars || {});
const defaultsText = () => defaultKeys().join(", ");

watch(
  () => props.modelValue,
  (v) => {
    if (!v) return;
    // 预置默认变量的 key 且值留空:不改则提交时自动过滤,即用流水线默认值
    const keys = defaultKeys();
    rows.value = keys.length ? keys.map((k) => ({ key: k, value: "" })) : [emptyRow()];
  }
);

const addRow = () => rows.value.push(emptyRow());
const removeRow = (idx) => rows.value.splice(idx, 1);

const doStart = () => {
  const inputs = {};
  rows.value.forEach((r) => {
    const k = (r.key || "").trim();
    const v = r.value ?? "";
    // 值留空视为"使用默认变量",不提交覆盖
    if (k && v.trim() !== "") inputs[k] = v;
  });
  emit("start", inputs);
};
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="运行参数"
    width="640px"
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <div class="tip">
      启动「{{ pipelineName }}」。填写的参数会在步骤脚本/附加参数/工作目录中以
      <code>{{ varRef("key") }}</code> 引用;若某一步输出解析开启 KEY=VALUE,
      其键值会进入同一上下文供后续步骤使用。
    </div>
    <div v-if="defaultsText()" class="tip default-tip">
      流水线已配置默认变量: <code>{{ defaultsText() }}</code>。
      下面这些行不用填,直接「开始运行」就会套用默认值;要临时覆盖哪一项,就在对应行填新值即可。
    </div>
    <div v-for="(row, idx) in rows" :key="idx" class="row">
      <el-input v-model="row.key" placeholder="参数名,如 fileList / tag" style="width: 220px" />
      <el-input v-model="row.value" placeholder="参数值,如 wgt_o2o_web_file_list.txt" style="flex: 1" />
      <el-button type="danger" size="small" link @click="removeRow(idx)" :disabled="rows.length === 1">删</el-button>
    </div>
    <el-button type="primary" link @click="addRow">+ 添加参数</el-button>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" @click="doStart">开始运行</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.tip {
  font-size: 12px;
  color: #606266;
  margin-bottom: 12px;
  line-height: 1.6;
}
.tip code {
  background: #f4f4f5;
  padding: 0 4px;
  border-radius: 3px;
}
.default-tip {
  color: #67c23a;
}
.row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
</style>
