<script setup>
import { ref, reactive, watch } from "vue";
import { ElMessage } from "element-plus";
import { datasourceApi } from "../../api/datasource";

const props = defineProps({
  modelValue: Boolean,
  ds: { type: Object, default: null },
  bastions: { type: Array, default: () => [] },
});
const emit = defineEmits(["update:modelValue", "success"]);

const formRef = ref();
const saving = ref(false);
const testing = ref(false);

const form = reactive({
  name: "",
  dbType: "MYSQL",
  host: "",
  port: 3306,
  databaseName: "",
  username: "",
  password: "",
  tunnelBastionId: null,
  remark: "",
});

const rules = {
  name: [{ required: true, message: "必填", trigger: "blur" }],
  dbType: [{ required: true, message: "必填", trigger: "change" }],
  host: [{ required: true, message: "必填", trigger: "blur" }],
  port: [{ required: true, message: "必填", trigger: "blur" }],
  databaseName: [{ required: true, message: "必填", trigger: "blur" }],
  username: [{ required: true, message: "必填", trigger: "blur" }],
};

const isEdit = () => props.ds !== null;

const reset = () => {
  Object.assign(form, {
    name: "", dbType: "MYSQL", host: "", port: 3306,
    databaseName: "", username: "", password: "",
    tunnelBastionId: null, remark: "",
  });
  formRef.value?.clearValidate();
};

const fillForm = () => {
  if (!isEdit()) { reset(); return; }
  const d = props.ds;
  Object.assign(form, {
    name: d.name, dbType: d.dbType, host: d.host, port: d.port,
    databaseName: d.databaseName, username: d.username, password: "",
    tunnelBastionId: d.tunnelBastionId, remark: d.remark || "",
  });
};

watch(() => props.modelValue, (v) => { if (v) fillForm(); });

const onTest = async () => {
  try {
    await formRef.value.validate();
  } catch { return; }
  testing.value = true;
  try {
    const ok = await datasourceApi.testConnectionDirect({ ...form });
    if (ok) ElMessage.success("连接成功");
    else ElMessage.warning("连接失败");
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    testing.value = false;
  }
};

const onSubmit = async () => {
  try {
    await formRef.value.validate();
  } catch { return; }
  if (!isEdit() && !form.password) {
    ElMessage.error("新增数据源必须填密码");
    return;
  }
  saving.value = true;
  try {
    if (isEdit()) {
      await datasourceApi.update(props.ds.id, { ...form });
      ElMessage.success("已保存");
    } else {
      await datasourceApi.create({ ...form });
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
    :title="isEdit() ? '编辑数据源' : '新增数据源'"
    width="640px"
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" placeholder="如:测试库" />
      </el-form-item>
      <el-form-item label="数据库类型" prop="dbType">
        <el-select v-model="form.dbType" style="width: 100%">
          <el-option label="MySQL" value="MYSQL" />
          <el-option label="达梦 DM" value="DM" />
        </el-select>
      </el-form-item>
      <el-form-item label="主机" prop="host">
        <el-input v-model="form.host" placeholder="如:127.0.0.1 或内网 IP" />
      </el-form-item>
      <el-form-item label="端口" prop="port">
        <el-input-number v-model="form.port" :min="1" :max="65535" />
      </el-form-item>
      <el-form-item label="库名" prop="databaseName">
        <el-input v-model="form.databaseName" placeholder="如:test_db" />
      </el-form-item>
      <el-form-item label="用户名" prop="username">
        <el-input v-model="form.username" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input v-model="form.password" type="password" show-password
          :placeholder="isEdit() ? '留空表示不修改' : '必填'" />
      </el-form-item>
      <el-form-item label="SSH 隧道">
        <el-select v-model="form.tunnelBastionId" clearable placeholder="直连(不选)" style="width: 100%">
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
