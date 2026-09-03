import http from "./http";

/**
 * Redis 可视化模块 API.
 * 后端路径前缀 /api/redis,由 http.js 的 baseURL 统一补齐.
 */
export const redisApi = {
  // 实例 CRUD
  list: () => http.get("/redis"),
  get: (id) => http.get(`/redis/${id}`),
  create: (data) => http.post("/redis", data),
  update: (id, data) => http.put(`/redis/${id}`, data),
  delete: (id) => http.delete(`/redis/${id}`),

  // 连接测试
  testConnection: (id) => http.post(`/redis/${id}/test`),
  testConnectionDirect: (data) => http.post("/redis/test", data),

  // ==== 可视化操作 ops ====
  // INFO 概览
  info: (id) => http.get(`/redis/${id}/ops/info`),
  // db 列表
  listDbs: (id) => http.get(`/redis/${id}/ops/dbs`),
  // SCAN key 扫描
  scanKeys: (id, db = "0", cursor = "0", pattern = null, count = 200) =>
    http.get(`/redis/${id}/ops/keys`, { params: { db, cursor, pattern, count } }),
  // 按 TYPE 获取完整 value
  getValue: (id, db = "0", key) =>
    http.get(`/redis/${id}/ops/key`, { params: { db, key } }),
  // 执行白名单命令
  execute: (id, db = "0", commandLine) =>
    http.post(`/redis/${id}/ops/exec`, commandLine, { params: { db } }),
  // 删除 key
  delKey: (id, db = "0", key) =>
    http.delete(`/redis/${id}/ops/key`, { params: { db, key } }),
};
