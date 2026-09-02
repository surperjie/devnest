import http from "./http";

// SSH 隧道 API 封装
export const tunnelApi = {
  listBastions: () => http.get("/tunnel/bastions"),
  createBastion: (data) => http.post("/tunnel/bastions", data),
  updateBastion: (id, data) => http.put(`/tunnel/bastions/${id}`, data),
  deleteBastion: (id) => http.delete(`/tunnel/bastions/${id}`),
  startTunnel: (id) => http.post(`/tunnel/bastions/${id}/start`),
  stopTunnel: (id) => http.post(`/tunnel/bastions/${id}/stop`),
  listRunningStatus: () => http.get("/tunnel/status"),
  getStatus: (id) => http.get(`/tunnel/bastions/${id}/status`),
};
