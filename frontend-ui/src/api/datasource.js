import http from "./http";

// 数据源 API 封装
export const datasourceApi = {
  // 数据源 CRUD
  list: () => http.get("/datasource"),
  get: (id) => http.get(`/datasource/${id}`),
  create: (data) => http.post("/datasource", data),
  update: (id, data) => http.put(`/datasource/${id}`, data),
  delete: (id) => http.delete(`/datasource/${id}`),
  // 连接测试
  testConnection: (id) => http.post(`/datasource/${id}/test`),
  testConnectionDirect: (data) => http.post("/datasource/test", data),
  // 库表结构
  getSchema: (id) => http.get(`/datasource/${id}/schema`),
  // 数据预览
  preview: (id, table, database = null, page = 0, size = 50) =>
    http.get(`/datasource/${id}/preview`, { params: { database, table, page, size } }),
  // 执行 SQL(支持多语句,返回多个结果集)
  executeSql: (id, sql, maxRows = 200) =>
    http.post(`/datasource/${id}/sql`, { sql, maxRows }),
  // SQL 历史
  getHistory: (id, page = 0, size = 20) =>
    http.get(`/datasource/${id}/sql-history`, { params: { page, size } }),
  getRecent: (id) => http.get(`/datasource/${id}/sql-recent`),
};
