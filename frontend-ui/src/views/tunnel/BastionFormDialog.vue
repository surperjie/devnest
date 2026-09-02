<script setup>
import { ref, reactive, watch } from "vue";
import { ElMessage } from "element-plus";
import { tunnelApi } from "../../api/tunnel";

const props = defineProps({
  modelValue: Boolean,
  bastion: { type: Object, default: null },
});
const emit = defineEmits(["update:modelValue", "success"]);

const formRef = ref();
const loading = ref(false);
const saving = ref(false);

const form = reactive({
  name: "",
  sshHost: "",
  sshPort: 22,
  sshUser: "",
  sshPassword: "",
  remark: "",
  mappings: [],
});

const rules = {
  name: [{ required: true, message: "必填", trigger: "blur" }],
  sshHost: [{ required: true, message: "必填", trigger: "blur" }],
  sshPort: [{ required: true, message: "必填", trigger: "blur" }],
  sshUser: [{ required: true, message: "必填", trigger: "blur" }],
};

const isEdit = () => props.bastion !== null;

const reset = () => {
  Object.assign(form, {
    name: "",
    sshHost: "",
    sshPort: 22,
    sshUser: "",
    sshPassword: "",
    remark: "",
    mappings: [],
  });
  formRef.value?.clearValidate();
};

const fillForm = async () => {
  if (!isEdit()) {
    reset();
    return;
  }
  const b = props.bastion;
  form.name = b.name;
  form.sshHost = b.sshHost;
  form.sshPort = b.sshPort;
  form.sshUser = b.sshUser;
  form.sshPassword = ""; // 编辑时密码留空表示不改
  form.remark = b.remark || "";
  loading.value = true;
  try {
    const status = await tunnelApi.getStatus(b.id);
    form.mappings = (status.mappings || []).map((m) => ({
      remoteHost: m.remoteHost,
      remotePort: m.remotePort,
      preferredLocalPort: m.preferredLocalPort,
      label: m.label || "",
    }));
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    loading.value = false;
  }
};

watch(() => props.modelValue, (v) => {
  if (v) fillForm();
});

const addMapping = () => {
  form.mappings.push({
    remoteHost: "",
    remotePort: 22,
    preferredLocalPort: null,
    label: "",
  });
};
const removeMapping = (idx) => {
  form.mappings.splice(idx, 1);
};

const onSubmit = async () => {
  try {
    await formRef.value.validate();
  } catch {
    return;
  }
  if (!isEdit() && !form.sshPassword) {
    ElMessage.error("新增跳板必须填密码");
    return;
  }
  saving.value = true;
  const payload = {
    name: form.name,
    sshHost: form.sshHost,
    sshPort: form.sshPort,
    sshUser: form.sshUser,
    sshPassword: form.sshPassword,
    remark: form.remark,
    mappings: form.mappings.map((m) => ({
      remoteHost: m.remoteHost,
      remotePort: m.remotePort,
      preferredLocalPort: m.preferredLocalPort || null,
      label: m.label,
    })),
  };
  try {
    if (isEdit()) {
      await tunnelApi.updateBastion(props.bastion.id, payload);
      ElMessage.success("已保存");
    } else {
      await tunnelApi.createBastion(payload);
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
    :title="isEdit() ? '编辑跳板' : '新增跳板'"
    width="760px"
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
      v-loading="loading"
    >
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" placeholder="如:测试跳板" />
      </el-form-item>
      <el-form-item label="SSH 主机" prop="sshHost">
        <el-input v-model="form.sshHost" placeholder="如:10.47.202.35" />
      </el-form-item>
      <el-form-item label="SSH 端口" prop="sshPort">
        <el-input-number v-model="form.sshPort" :min="1" :max="65535" />
      </el-form-item>
      <el-form-item label="用户名" prop="sshUser">
        <el-input v-model="form.sshUser" />
      </el-form-item>
      <el-form-item label="密码" prop="sshPassword">
        <el-input
          v-model="form.sshPassword"
          type="password"
          show-password
          :placeholder="isEdit() ? '留空表示不修改' : '必填'"
        />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" />
      </el-form-item>
      <el-form-item label="端口映射">
        <el-button type="primary" size="small" @click="addMapping">+ 添加映射</el-button>
        <el-table
          :data="form.mappings"
          border
          size="small"
          style="margin-top: 8px"
          empty-text="暂无映射,点击上方添加"
        >
          <el-table-column label="远端主机" min-width="150">
            <template #default="{ row }">
              <el-input v-model="row.remoteHost" size="small" placeholder="如:10.47.202.186" />
            </template>
          </el-table-column>
          <el-table-column label="远端端口" width="120">
            <template #default="{ row }">
              <el-input-number
                v-model="row.remotePort"
                :min="1"
                :max="65535"
                size="small"
                controls-position="right"
                style="width: 100px"
              />
            </template>
          </el-table-column>
          <el-table-column label="本地端口" width="130">
            <template #default="{ row }">
              <el-input-number
                v-model="row.preferredLocalPort"
                :min="1"
                :max="65535"
                size="small"
                controls-position="right"
                style="width: 110px"
                placeholder="自动"
              />
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="130">
            <template #default="{ row }">
              <el-input v-model="row.label" size="small" placeholder="如:测试库" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70">
            <template #default="{ $index }">
              <el-button type="danger" size="small" link @click="removeMapping($index)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="onCancel">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>
