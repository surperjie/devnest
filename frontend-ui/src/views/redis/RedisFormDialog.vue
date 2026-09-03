<script setup>
import { ref, reactive, watch } from "vue";
import { ElMessage } from "element-plus";
import { redisApi } from "../../api/redis";

const props = defineProps({
  modelValue: Boolean,
  redis: { type: Object, default: null },
  bastions: { type: Array, default: () => [] },
});
const emit = defineEmits(["update:modelValue", "success"]);

const formRef = ref();
const saving = ref(false);
const testing = ref(false);

const form = reactive({
  name: "",
  host: "",
  port: 6379,
  password: "",
  dbIndex: 0,
  timeoutMs: 2000,
  maxConnections: 8,
  sshBastionId: null,
  remark: "",
});

const rules = {
  name: [{ required: true, message: "必填", trigger: "blur" }],
  host: [{ required: true, message: "必填", trigger: "blur" }],
  port: [{ required: true, message: "必填", trigger: "blur" }],
};

const isEdit = () => props.redis !== null;

const reset = () => {
  Object.assign(form, {
    name: "", host: "", port: 6379, password: "",
    dbIndex: 0, timeoutMs: 2000, maxConnections: 8,
    sshBastionId: null, remark: "",
  });
  formRef.value?.clearValidate();
};

const fillForm = () => {
  if (!isEdit()) { reset(); return; }
  const r = props.redis;
  Object.assign(form, {
    name: r.name, host: r.host, port: r.port,
    password: r.hasPassword ? "********" : "",
    dbIndex: r.dbIndex ?? 0,
    timeoutMs: r.timeoutMs ?? 2000,
    maxConnections: r.maxConnections ?? 8,
    sshBastionId: r.sshBastionId,
    remark: r.remark || "",
  });
};

watch(() => props.modelValue, (v) => { if (v) fillForm(); });

const onTest = async () => {
  try { await formRef.value.validate(); } catch { return; }
  testing.value = true;
  try {
    const ok = await redisApi.testConnectionDirect({ ...form });
    if (ok) ElMessage.success("连接成功");
    else ElMessage.warning("连接失败");
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    testing.value = false;
  }
};

const onSubmit = async () => {
  try { await formRef.value.validate(); } catch { return; }
  // 编辑时 ******** 掩码不更新密码
  const submitData = { ...form };
  if (isEdit() && form.password === "********") {
    submitData.password = null;
  }
  saving.value = true;
  try {
    if (isEdit()) {
      await redisApi.update(props.redis.id, submitData);
      ElMessage.success("已保存");
    } else {
      await redisApi.create(submitData);
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
    :title="isEdit() ? '编辑 Redis 实例' : '新增 Redis 实例'"
    width="680px"
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" placeholder="如:本地 Redis / 测试环境" />
      </el-form-item>
      <el-form-item label="主机" prop="host">
        <el-input v-model="form.host" placeholder="如:127.0.0.1" />
      </el-form-item>
      <el-form-item label="端口" prop="port">
        <el-input-number v-model="form.port" :min="1" :max="65535" />
      </el-form-item>
      <el-form-item label="密码">
        <el-input v-model="form.password" type="password" show-password
          :placeholder="isEdit() ? '留空表示不修改' : '无密码可留空'" />
      </el-form-item>
      <el-form-item label="默认 DB">
        <el-input-number v-model="form.dbIndex" :min="0" :max="15" />
      </el-form-item>
      <el-form-item label="超时(ms)">
        <el-input-number v-model="form.timeoutMs" :min="100" :max="30000" step="100" />
      </el-form-item>
      <el-form-item label="最大连接">
        <el-input-number v-model="form.maxConnections" :min="1" :max="100" />
      </el-form-item>
      <el-form-item label="SSH 隧道">
        <el-select v-model="form.sshBastionId" clearable placeholder="直连(不选)" style="width: 100%">
          <el-option v-for="b in bastions" :key="b.id" :label="b.name" :value="b.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="onCancel">取消</el-button>
      <el-button :loading="testing" @click="onTest">测试连接</el-button>
      <el-button type="primary" :loading="saving" @click="onSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>
