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
      {
        path: "datasource",
        name: "datasource",
        component: () => import("../views/datasource/DataSourceList.vue"),
        meta: { title: "数据源", icon: "Coin" },
      },
      {
        path: "datasource/:id/explorer",
        name: "datasource-explorer",
        component: () => import("../views/datasource/DatabaseExplorer.vue"),
        meta: { title: "数据库浏览器", icon: "Coin", hidden: true },
      },
      {
        path: "redis",
        name: "redis",
        component: () => import("../views/redis/RedisList.vue"),
        meta: { title: "Redis", icon: "Key" },
      },
      {
        path: "redis/:id/explorer",
        name: "redis-explorer",
        component: () => import("../views/redis/RedisExplorer.vue"),
        meta: { title: "Redis 浏览器", icon: "Key", hidden: true },
      },
    ],
  },
];

const router = createRouter({
  history: createWebHashHistory(),
  routes,
});

export default router;
