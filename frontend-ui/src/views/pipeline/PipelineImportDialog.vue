<script setup>
import { ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { pipelineApi } from "../../api/pipeline";

const props = defineProps({
  modelValue: Boolean,
});
const emit = defineEmits(["update:modelValue", "success"]);

const fileInput = ref();
const text = ref("");
const errorMsg = ref("");
const parsed = ref(null);
const importName = ref("");
const saving = ref(false);

const RUN_TYPE_LABEL = {
  batch: "BAT",
  cmdline: "命令行",
  powershell: "PowerShell",
  python: "Python",
};
const ON_FAIL_LABEL = { abort: "终止", continue: "继续" };
const PARSE_LABEL = { none: "不解析", keyvalue: "KEY=VALUE" };
const runTypeText = (t) => RUN_TYPE_LABEL[t] || t || "batch";

const reset = () => {
  text.value = "";
  errorMsg.value = "";
  parsed.value = null;
  importName.value = "";
  saving.value = false;
};
watch(
  () => props.modelValue,
  (v) => {
    if (v) reset();
  }
);

const tryParse = (raw) => {
  errorMsg.value = "";
  parsed.value = null;
  const content = (raw ?? "").trim();
  if (!content) return;
  let obj;
  try {
    obj = JSON.parse(content);
  } catch (e) {
    errorMsg.value = "JSON 解析失败: " + e.message;
    return;
  }
  if (!obj || typeof obj !== "object" || Array.isArray(obj)) {
    errorMsg.value = "文件内容需为 JSON 对象";
    return;
  }
  if (!obj.name || typeof obj.name !== "string") {
    errorMsg.value = "缺少字段 name(流水线名称)";
    return;
  }
  if (!Array.isArray(obj.steps) || obj.steps.length === 0) {
    errorMsg.value = "缺少字段 steps(至少需要一个步骤)";
    return;
  }
  const badIdx = obj.steps.findIndex(
    (s) => !s || typeof s !== "object" || !s.name || typeof s.script === "undefined"
  );
  if (badIdx >= 0) {
    errorMsg.value = `第 ${badIdx + 1} 个步骤缺少 name/script 字段`;
    return;
  }
  parsed.value = obj;
  importName.value = String(obj.name);
};
watch(text, (v) => tryParse(v));

const onPickFile = () => fileInput.value?.click();
const onFileChange = async (e) => {
  const file = e.target.files?.[0];
  e.target.value = "";
  if (!file) return;
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error("文件过大,请直接粘贴 JSON 内容");
    return;
  }
  text.value = await file.text();
};

const close = () => emit("update:modelValue", false);

const onSubmit = async () => {
  if (!parsed.value) {
    ElMessage.error("请先选择导出的 JSON 文件或粘贴配置内容");
    return;
  }
  const finalName = importName.value.trim();
  if (!finalName) {
    ElMessage.error("导入后的流水线名称不能为空");
    return;
  }
  saving.value = true;
  try {
    const payload = { ...parsed.value, name: finalName };
    await pipelineApi.importPipeline(payload);
    ElMessage.success(`已导入流水线「${finalName}」`);
    emit("success");
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    saving.value = false;
  }
};

const varEntries = (vars) => {
  if (!vars || typeof vars !== "object") return [];
  return Object.entries(vars);
};
const fmtArgs = (args) => (Array.isArray(args) ? args.join(", ") : "");
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="导入流水线"
    width="900px"
    top="4vh"
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <div class="import-tip">
      支持粘贴本系统「导出」生成的 JSON,或选择导出的
      <code>pipeline_*.json</code> 文件(请确保文件为 UTF-8 编码)。
      导入后为一条全新流水线;若名称与现有重复,改一下"导入名称"再导入即可。
    </div>

    <div class="source-row">
      <el-button size="small" @click="onPickFile">选择 JSON 文件</el-button>
      <el-button size="small" @click="text = ''">清空</el-button>
      <input
        ref="fileInput"
        type="file"
        accept=".json,application/json,text/plain"
        style="display: none"
        @change="onFileChange"
      />
      <span class="source-hint">或在下框粘贴 JSON 内容(粘贴即解析):</span>
    </div>
    <el-input
      v-model="text"
      type="textarea"
      :rows="8"
      class="mono"
      spellcheck="false"
      placeholder='{"name":"...","workdir":"...","vars":{},"steps":[{...}]}'
    />

    <div v-if="errorMsg" class="parse-error">解析失败:{{ errorMsg }}</div>

    <template v-if="parsed">
      <el-divider content-position="left">预览(可修改名称后导入)</el-divider>
      <div class="preview">
        <div class="preview-base">
          <label class="field-label">导入名称</label>
          <el-input v-model="importName" style="width: 360px" size="small" />
          <el-tag v-if="importName !== parsed.name" type="warning" size="small">已改名</el-tag>
        </div>
        <div class="preview-line"><span class="field-label">默认工作目录</span>{{ parsed.workdir || "(未配置,运行时可自动创建工作区)" }}</div>
        <div class="preview-line"><span class="field-label">备注</span>{{ parsed.remark || "-" }}</div>
        <div v-if="varEntries(parsed.vars).length" class="preview-line">
          <span class="field-label">默认变量</span>
          <el-tag v-for="([k, v], i) in varEntries(parsed.vars)" :key="i" size="small" class="var-tag">
            {{ k }}={{ v }}
          </el-tag>
        </div>
      </div>

      <el-table :data="parsed.steps" border size="small" max-height="260" style="margin-top: 6px">
        <el-table-column label="#" width="46" align="center">
          <template #default="{ $index }">{{ $index + 1 }}</template>
        </el-table-column>
        <el-table-column label="启用" width="60" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled === false ? 'info' : 'success'" size="small">
              {{ row.enabled === false ? "禁用" : "启用" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" width="150" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ runTypeText(row.runType) }}</template>
        </el-table-column>
        <el-table-column label="脚本" min-width="200">
          <template #default="{ row }">
            <span class="script-preview" :title="row.script">{{ row.script }}</span>
          </template>
        </el-table-column>
        <el-table-column label="工作目录" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.workdir || "(继承)" }}</template>
        </el-table-column>
        <el-table-column label="附加参数" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ fmtArgs(row.args) || "-" }}</template>
        </el-table-column>
        <el-table-column label="超时(s)" width="76" align="center">
          <template #default="{ row }">{{ row.timeoutSec || 300 }}</template>
        </el-table-column>
        <el-table-column label="失败" width="70" align="center">
          <template #default="{ row }">{{ ON_FAIL_LABEL[row.onFail] || row.onFail || "终止" }}</template>
        </el-table-column>
        <el-table-column label="输出解析" width="100" align="center">
          <template #default="{ row }">{{ PARSE_LABEL[row.outputParse] || row.outputParse || "不解析" }}</template>
        </el-table-column>
      </el-table>
    </template>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!parsed" @click="onSubmit">
        开始导入
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.import-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.7;
  margin-bottom: 10px;
}
.import-tip code {
  background: #f4f4f5;
  padding: 0 4px;
  border-radius: 3px;
}
.source-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.source-hint {
  font-size: 12px;
  color: #909399;
}
.parse-error {
  margin-top: 8px;
  color: #f56c6c;
  font-size: 13px;
}
.preview {
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 2;
}
.preview-base {
  display: flex;
  align-items: center;
  gap: 8px;
}
.preview-line {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.field-label {
  color: #606266;
  width: 110px;
  flex: none;
}
.var-tag {
  margin-right: 4px;
}
.script-preview {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: monospace;
  font-size: 12px;
}
.mono :deep(textarea) {
  font-family: "Consolas", "Courier New", monospace;
  font-size: 13px;
}
</style>
