<script setup>
import { ref, computed } from "vue";
import { ElMessage } from "element-plus";
import { consoleApi } from "../../api/console";
import { tunnelApi } from "../../api/tunnel";

const props = defineProps({
  modelValue: Boolean,
  console: Object,
});
const emit = defineEmits(["update:modelValue", "success"]);

const form = ref({
  name: "",
  bastionId: null,
  remoteHost: "",
  remotePort: 22,
  sshUser: "",
  sshPassword: "",
  remark: "",
  quickCommands: [],
});
const bastions = ref([]);
const loading = ref(false);

const isEdit = computed(() => !!props.console);

const loadBastions = async () => {
  try {
    bastions.value = await tunnelApi.listBastions();
  } catch (e) {
    // 跳板列表加载失败不阻塞,用户可用直连模式
  }
};

const reset = () => {
  form.value = {
    name: "",
    bastionId: null,
    remoteHost: "",
    remotePort: 22,
    sshUser: "",
    sshPassword: "",
    remark: "",
    quickCommands: [],
  };
};

// 弹窗打开时加载跳板列表 + 回填(编辑)
import { watch } from "vue";
watch(
  () => props.modelValue,
  async (v) => {
    if (v) {
      await loadBastions();
      reset();
      if (props.console) {
        // 回填快捷命令 JSON
        let qc = [];
        try {
          const parsed = JSON.parse(props.console.quickCommands || "[]");
          qc = Array.isArray(parsed) ? parsed : [];
        } catch (e) {
          qc = [];
        }
        form.value = { ...props.console, sshPassword: "", quickCommands: qc };
      }
    }
  }
);

const onSubmit = async () => {
  if (!form.value.name || !form.value.remoteHost || !form.value.sshUser) {
    ElMessage.warning("请填写必填项");
    return;
  }
  if (!isEdit.value && !form.value.sshPassword) {
    ElMessage.warning("请填写密码");
    return;
  }
  loading.value = true;
  try {
    // 快捷命令序列化为 JSON 字符串入库,过滤空行
    const payload = {
      ...form.value,
      quickCommands: JSON.stringify(
        (form.value.quickCommands || []).filter((c) => c.name && c.command)
      ),
    };
    if (isEdit.value) {
      await consoleApi.updateConsole(props.console.id, payload);
      ElMessage.success("已更新");
    } else {
      await consoleApi.createConsole(payload);
      ElMessage.success("已创建");
    }
    emit("success");
    emit("update:modelValue", false);
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="(v) => emit('update:modelValue', v)"
    :title="isEdit ? '编辑控制台' : '新增控制台'"
    width="560px"
  >
    <el-form :model="form" label-width="100px">
      <el-form-item label="名称" required>
        <el-input v-model="form.name" placeholder="如:测试机控制台" />
      </el-form-item>
      <el-form-item label="连接模式">
        <el-select
          v-model="form.bastionId"
          clearable
          placeholder="直连模式(不选跳板)"
          style="width: 100%"
        >
          <el-option
            v-for="b in bastions"
            :key="b.id"
            :label="`隧道: ${b.name} (${b.sshHost})`"
            :value="b.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="目标主机" required>
        <el-input
          v-model="form.remoteHost"
          placeholder="直连=目标IP,隧道=远端内网IP"
        />
      </el-form-item>
      <el-form-item label="SSH 端口">
        <el-input-number v-model="form.remotePort" :min="1" :max="65535" />
      </el-form-item>
      <el-form-item label="用户名" required>
        <el-input v-model="form.sshUser" />
      </el-form-item>
      <el-form-item label="密码" :required="!isEdit">
        <el-input
          v-model="form.sshPassword"
          type="password"
          show-password
          :placeholder="isEdit ? '留空不修改' : ''"
        />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" />
      </el-form-item>
      <el-form-item label="快捷命令">
        <div class="quick-edit">
          <div
            v-for="(c, i) in form.quickCommands"
            :key="i"
            class="quick-row"
          >
            <el-input
              v-model="c.name"
              placeholder="命令名(如:查日志)"
              style="width: 160px"
            />
            <el-input
              v-model="c.command"
              placeholder="命令内容(如:tail -f app.log)"
              style="flex: 1"
            />
            <el-button
              type="danger"
              size="small"
              @click="form.quickCommands.splice(i, 1)"
            >删</el-button>
          </div>
          <el-button
            size="small"
            type="primary"
            plain
            @click="form.quickCommands.push({ name: '', command: '' })"
          >+ 添加</el-button>
          <span class="quick-tip">终端弹窗顶部显示为按钮,点击直接执行</span>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="onSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.quick-edit {
  width: 100%;
}
.quick-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
}
.quick-tip {
  display: block;
  color: #909399;
  font-size: 12px;
  margin-top: 6px;
}
</style>
