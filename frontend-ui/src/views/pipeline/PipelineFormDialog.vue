<script setup>
import { ref, reactive, watch, nextTick } from "vue";
import { ElMessage } from "element-plus";
import { pipelineApi } from "../../api/pipeline";

const props = defineProps({
  modelValue: Boolean,
  pipeline: { type: Object, default: null },
});
const emit = defineEmits(["update:modelValue", "success"]);

const formRef = ref();
const loading = ref(false);
const saving = ref(false);

const emptyStep = () => ({
  name: "步骤",
  runType: "batch",
  script: "",
  workdir: "",
  argsText: "",
  timeoutSec: 300,
  onFail: "abort",
  outputParse: "none",
  enabled: true,
});

const emptyVarRow = () => ({ key: "", value: "" });

const form = reactive({
  name: "",
  remark: "",
  workdir: "",
  varsRows: [emptyVarRow()],
  steps: [],
});

const rules = {
  name: [{ required: true, message: "必填", trigger: "blur" }],
};

const isEdit = () => props.pipeline !== null;
const currentPipelineId = () => (props.pipeline ? props.pipeline.id : null);
/** 生成可展示的花括号变量示例(避免在模板里直接写 {{ 导致编译错乱) */
const varRef = (key) => "{{" + key + "}}";

const reset = () => {
  Object.assign(form, {
    name: "",
    remark: "",
    workdir: "",
    varsRows: [emptyVarRow()],
    steps: [emptyStep()],
  });
  formRef.value?.clearValidate();
};

const fillForm = async () => {
  if (!isEdit()) {
    reset();
    return;
  }
  loading.value = true;
  try {
    const detail = await pipelineApi.getPipeline(props.pipeline.id);
    form.name = detail.name;
    form.remark = detail.remark || "";
    form.workdir = detail.workdir || "";
    form.varsRows = Object.entries(detail.vars || {}).map(([k, v]) => ({ key: k, value: v || "" }));
    if (!form.varsRows.length) form.varsRows = [emptyVarRow()];
    form.steps = (detail.steps || []).map((s) => ({
      name: s.name,
      runType: s.runType || "batch",
      script: s.script || "",
      workdir: s.workdir || "",
      argsText: (s.args || []).join(", "),
      timeoutSec: s.timeoutSec || 300,
      onFail: s.onFail || "abort",
      outputParse: s.outputParse || "none",
      enabled: s.enabled !== false,
    }));
    if (!form.steps.length) form.steps.push(emptyStep());
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    loading.value = false;
  }
};

watch(
  () => props.modelValue,
  (v) => {
    if (v) fillForm();
  }
);

const addStep = () => {
  form.steps.push(emptyStep());
};
const removeStep = (idx) => {
  form.steps.splice(idx, 1);
};
const addVarRow = () => form.varsRows.push(emptyVarRow());
const removeVarRow = (idx) => form.varsRows.splice(idx, 1);
const buildVars = () => {
  const vars = {};
  form.varsRows.forEach((r) => {
    const k = (r.key || "").trim();
    const v = r.value ?? "";
    if (k && v !== "") vars[k] = v;
  });
  return vars;
};
const moveStep = (idx, dir) => {
  const target = idx + dir;
  if (target < 0 || target >= form.steps.length) return;
  const arr = form.steps;
  [arr[idx], arr[target]] = [arr[target], arr[idx]];
};

// 双击打开脚本编辑器
const scriptEditor = reactive({ visible: false, index: -1, title: "" });
const editScript = (idx) => {
  scriptEditor.visible = true;
  scriptEditor.index = idx;
  scriptEditor.title = `编辑脚本 - ${idx + 1}. ${form.steps[idx].name}`;
};
const onScriptSaved = () => {
  scriptEditor.visible = false;
};

const fmtRunType = (t) =>
  ({ batch: "BAT 批处理", cmdline: "命令行", powershell: "PowerShell", python: "Python" })[t] || t;

const onSubmit = async () => {
  try {
    await formRef.value.validate();
  } catch {
    return;
  }
  const validSteps = form.steps.filter((s) => s.name && s.script !== undefined);
  if (!validSteps.length) {
    ElMessage.error("请至少配置一个启用的步骤");
    return;
  }
  saving.value = true;
  const payload = {
    name: form.name,
    remark: form.remark,
    workdir: form.workdir,
    vars: buildVars(),
    steps: validSteps.map((s, i) => ({
      name: s.name || `步骤${i + 1}`,
      runType: s.runType,
      script: s.script,
      workdir: s.workdir || null,
      args: (s.argsText || "")
        .split(",")
        .map((a) => a.trim())
        .filter((a) => a !== ""),
      timeoutSec: s.timeoutSec || 300,
      onFail: s.onFail,
      outputParse: s.outputParse,
      enabled: s.enabled,
    })),
  };
  try {
    if (isEdit()) {
      await pipelineApi.updatePipeline(currentPipelineId(), payload);
      ElMessage.success("已保存");
    } else {
      await pipelineApi.createPipeline(payload);
      ElMessage.success("已创建");
    }
    emit("success");
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    saving.value = false;
  }
};

const onCancel = () => emit("update:modelValue", false);
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEdit() ? `编辑流水线 - ${props.pipeline.name}` : '新增流水线'"
    width="980px"
    top="4vh"
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="110px"
      v-loading="loading"
    >
      <div class="base-fields">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如:发布回退流水线" style="width: 300px" />
        </el-form-item>
        <el-form-item label="默认工作目录">
          <el-input v-model="form.workdir" placeholder="如:D:\work\game 或留空=每次运行自动创建工作区" style="width: 440px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="可选" style="width: 440px" />
        </el-form-item>
      </div>

      <el-form-item label="默认变量">
        <div class="vars-block">
          <div class="steps-tip">
            配置步骤脚本里
            <code>{{ varRef("key") }}</code> 的默认值(如 pdDate / tag / user / ver)。配置后直接点「运行...」、
            不填参数也会套用默认值;运行弹窗里对同名变量填值即可临时覆盖(只影响当次运行)。
          </div>
          <el-button type="primary" size="small" @click="addVarRow">+ 添加变量</el-button>
          <div v-for="(row, idx) in form.varsRows" :key="idx" class="var-row">
            <el-input v-model="row.key" placeholder="参数名,如 pdDate" style="width: 220px" size="small" />
            <el-input v-model="row.value" placeholder="默认值,如 2026/8/25" style="flex: 1" size="small" />
            <el-button type="danger" size="small" link @click="removeVarRow(idx)" :disabled="form.varsRows.length === 1">删</el-button>
          </div>
        </div>
      </el-form-item>

      <el-form-item label="步骤编排">
        <div class="steps-block">
          <div class="steps-tip">
            步骤按顺序执行(上一步结果可在下一步中使用)。
            点击「编辑脚本」或双击某行脚本单元格,即可就地修改脚本内容。
            变量用 {{ varRef("key") }} 引用:取值为 默认变量 -> 本次运行参数 -> 上一步输出的 KEY=VALUE,后者覆盖前者;
            附加参数依次对应批处理里的 %1/%2。
          </div>
          <el-button type="primary" size="small" @click="addStep">+ 添加步骤</el-button>
          <el-table :data="form.steps" border size="small" style="margin-top: 8px" max-height="420">
            <el-table-column label="顺序" width="50" align="center">
              <template #default="{ $index }">
                <span>{{ $index + 1 }}</span>
                <div class="step-move">
                  <el-button size="small" link :disabled="$index === 0" @click="moveStep($index, -1)">↑</el-button>
                  <el-button size="small" link :disabled="$index === form.steps.length - 1" @click="moveStep($index, 1)">↓</el-button>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="启用" width="60" align="center">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="名称" width="150">
              <template #default="{ row }">
                <el-input v-model="row.name" size="small" placeholder="步骤名称" />
              </template>
            </el-table-column>
            <el-table-column label="类型" width="130">
              <template #default="{ row }">
                <el-select v-model="row.runType" size="small">
                  <el-option label="BAT 批处理" value="batch" />
                  <el-option label="命令行 cmdline" value="cmdline" />
                  <el-option label="PowerShell" value="powershell" />
                  <el-option label="Python" value="python" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="脚本(双击编辑)" min-width="260">
              <template #default="{ row, $index }">
                <div
                  class="script-cell"
                  :title="row.script"
                  @dblclick="editScript($index)"
                >
                  <span class="script-preview">{{ row.script || "(空)" }}</span>
                  <el-button
                    size="small"
                    type="primary"
                    link
                    @click.stop="editScript($index)"
                  >编辑脚本</el-button>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="工作目录" width="170">
              <template #default="{ row }">
                <el-input v-model="row.workdir" size="small" placeholder="空=继承流水线目录" />
              </template>
            </el-table-column>
            <el-table-column label="附加参数" width="170">
              <template #default="{ row }">
                <el-input
                  v-model="row.argsText"
                  size="small"
                  :placeholder="'逗号分隔,如 ' + varRef('fileList') + ', ' + varRef('tag')"
                />
              </template>
            </el-table-column>
            <el-table-column label="超时(s)" width="90" align="center">
              <template #default="{ row }">
                <el-input-number v-model="row.timeoutSec" :min="10" :max="86400" size="small" controls-position="right" style="width: 80px" />
              </template>
            </el-table-column>
            <el-table-column label="失败策略" width="110">
              <template #default="{ row }">
                <el-select v-model="row.onFail" size="small">
                  <el-option label="终止流水线" value="abort" />
                  <el-option label="继续下一步" value="continue" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="输出解析" width="110">
              <template #default="{ row }">
                <el-select v-model="row.outputParse" size="small">
                  <el-option label="不解析" value="none" />
                  <el-option label="KEY=VALUE" value="keyvalue" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="60" align="center">
              <template #default="{ $index }">
                <el-button type="danger" size="small" link @click="removeStep($index)">删</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="onCancel">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSubmit">保存</el-button>
    </template>

    <!-- 脚本编辑器:双击脚本行弹出 -->
    <el-dialog
      v-model="scriptEditor.visible"
      :title="scriptEditor.title"
      width="820px"
      append-to-body
    >
      <div class="script-edit-tip">
        当前类型: {{ fmtRunType(form.steps[scriptEditor.index]?.runType) }} | 支持 {{ varRef("key") }} 引用上下文变量;
        批处理将按 GB18030 落盘,PowerShell/Python 脚本按 UTF-8 落盘
      </div>
      <el-input
        v-model="form.steps[scriptEditor.index].script"
        type="textarea"
        :rows="20"
        class="mono"
        spellcheck="false"
        placeholder="粘贴或编写脚本内容..."
      />
      <template #footer>
        <el-button @click="scriptEditor.visible = false">取消</el-button>
        <el-button type="primary" @click="onScriptSaved">保存脚本</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<style scoped>
.base-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 0 20px;
}
.steps-block {
  width: 100%;
}
.steps-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
  margin-bottom: 8px;
}
.vars-block {
  width: 100%;
}
.vars-block code {
  background: #f4f4f5;
  padding: 0 4px;
  border-radius: 3px;
}
.var-row {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
.step-move {
  display: inline-flex;
  flex-direction: column;
  line-height: 1;
  margin-left: 4px;
  vertical-align: middle;
}
.step-move .el-button {
  margin: 0;
  padding: 0 2px;
}
.script-cell {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  cursor: pointer;
}
.script-preview {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: monospace;
  font-size: 12px;
}
.script-edit-tip {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}
.mono :deep(textarea) {
  font-family: "Consolas", "Courier New", monospace;
  font-size: 13px;
}
</style>
