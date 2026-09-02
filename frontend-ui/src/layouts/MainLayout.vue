<script setup>
import { useRouter, useRoute } from "vue-router";

const router = useRouter();
const route = useRoute();

// 菜单配置(可扩展):后续模块上线时把 disabled 改 false + 在 router/index.js 加路由
const menus = [
  { index: "/tunnel", title: "SSH 隧道", icon: "Connection", disabled: false },
  { index: "/datasource", title: "数据源", icon: "Coin", disabled: true },
  { index: "/redis", title: "Redis", icon: "Key", disabled: true },
  { index: "/http", title: "HTTP 调试", icon: "Promotion", disabled: true },
  { index: "/ai", title: "AI 配置", icon: "MagicStick", disabled: true },
];

const onSelect = (index) => router.push(index);
</script>

<template>
  <el-container class="layout">
    <el-aside width="200px" class="aside">
      <div class="logo">DevNest</div>
      <el-menu
        :default-active="route.path"
        class="menu"
        @select="onSelect"
      >
        <el-menu-item
          v-for="m in menus"
          :key="m.index"
          :index="m.index"
          :disabled="m.disabled"
        >
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-main class="main">
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
}
.aside {
  background: #ffffff;
  border-right: 1px solid #e4e7ed;
}
.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 18px;
  color: #409eff;
  border-bottom: 1px solid #e4e7ed;
}
.menu {
  border-right: none;
}
.main {
  padding: 16px;
  background: #f5f7fa;
  overflow: auto;
}
</style>
