import http from "./http";

// 流水线 API 封装
export const pipelineApi = {
  // 定义
  listPipelines: () => http.get("/pipeline/pipelines"),
  getPipeline: (id) => http.get(`/pipeline/pipelines/${id}`),
  createPipeline: (data) => http.post("/pipeline/pipelines", data),
  updatePipeline: (id, data) => http.put(`/pipeline/pipelines/${id}`, data),
  deletePipeline: (id) => http.delete(`/pipeline/pipelines/${id}`),
  exportPipeline: (id) => http.get(`/pipeline/pipelines/${id}/export`),
  importPipeline: (data) => http.post("/pipeline/pipelines/import", data),

  // 运行
  startRun: (pipelineId, inputs) =>
    http.post(`/pipeline/pipelines/${pipelineId}/runs`, { inputs: inputs || {} }),
  listRuns: (pipelineId) =>
    http.get("/pipeline/runs", { params: pipelineId ? { pipelineId } : {} }),
  getRun: (runId) => http.get(`/pipeline/runs/${runId}`),
  stopRun: (runId) => http.post(`/pipeline/runs/${runId}/stop`),
  getStepLog: (runId, seq, offset) =>
    http.get(`/pipeline/runs/${runId}/steps/${seq}/log`, { params: { offset: offset || 0 } }),
};
