import http from "./http";

// 远程控制台 API 封装
export const consoleApi = {
  listConsoles: () => http.get("/console/consoles"),
  getConsole: (id) => http.get(`/console/consoles/${id}`),
  createConsole: (data) => http.post("/console/consoles", data),
  updateConsole: (id, data) => http.put(`/console/consoles/${id}`, data),
  deleteConsole: (id) => http.delete(`/console/consoles/${id}`),
  exportConsoles: () => http.get("/console/consoles/export"),
  importConsoles: (data) => http.post("/console/consoles/import", data),
  /** 申请一次性 WebSocket 握手 token (TOFU,30s TTL) */
  issueWsToken: (id) => http.post(`/console/consoles/${id}/ws-token`),
};
