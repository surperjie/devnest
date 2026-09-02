import { createRouter, createWebHashHistory } from "vue-router";
import MainLayout from "../layouts/MainLayout.vue";

// 可扩展路由:后续新增模块在此追加子路由即可
const routes = [
  {
    path: "/",
    component: MainLayout,
    redirect: "/tunnel",
    children: [
      {
        path: "tunnel",
        name: "tunnel",
        component: () => import("../views/tunnel/BastionList.vue"),
        meta: { title: "SSH 隧道", icon: "Connection" },
      },
      {
        path: "console",
        name: "console",
        component: () => import("../views/console/ConsoleList.vue"),
        meta: { title: "远程控制台", icon: "Monitor" },
      },
      // 二期+ 模块路由占位:
      // { path: "datasource", name: "datasource", component: ..., meta: { title: "数据源", icon: "Coin" } },
      // { path: "redis", name: "redis", component: ..., meta: { title: "Redis", icon: "Key" } },
      // { path: "http", name: "http", component: ..., meta: { title: "HTTP 调试", icon: "Promotion" } },
      // { path: "ai", name: "ai", component: ..., meta: { title: "AI 配置", icon: "MagicStick" } },
    ],
  },
];

const router = createRouter({
  history: createWebHashHistory(),
  routes,
});

export default router;
